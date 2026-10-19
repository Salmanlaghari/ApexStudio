package com.apexstudio.app.data.captions

import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Log-mel spectrogram exactly as Whisper expects it (cf. whisper.cpp
 * `log_mel_spectrogram`, which the k2-fsa ONNX export was validated
 * against):
 *
 *  - 16 kHz mono float PCM in [-1, 1]
 *  - 25 ms Hann window (400 samples), 10 ms hop (160 samples)
 *  - 400-point DFT, bins 0..200 (magnitude spectrum)
 *  - 80 triangular mel filters, HTK scale, 0–8000 Hz
 *  - log10(max(e, 1e-10)), floored at (max − 8.0), then (x + 4.0) / 4.0
 *  - output is always [80 × 3000] (30 s), zero-padded / truncated
 *
 * Why a naive DFT instead of an FFT: N=400 is not a power of two, and a
 * mixed-radix/Bluestein FFT is subtle code that cannot be unit-tested on
 * this machine. The naive DFT with a precomputed twiddle table is
 * obviously correct; at ~80k complex MACs/frame it runs in ~1–3 s per
 * 30 s chunk on a mid-range phone — acceptable for a background caption
 * job, and dwarfed by the neural inference that follows.
 *
 * Note on filterbank variants: whisper.cpp and kaldi-native-fbank differ
 * slightly (Hann vs povey window, filter normalisation), but the
 * log + max-subtraction normalisation makes the features invariant to
 * any constant multiplicative scale, so both feed the model fine.
 */
object WhisperMelSpectrogram {

    const val SAMPLE_RATE = 16000
    const val N_FFT = 400
    const val HOP = 160
    const val N_MELS = 80
    const val N_FRAMES = 3000 // 30 s @ 10 ms hop
    private const val FMIN = 0f
    private const val FMAX = 8000f
    private const val N_BINS = N_FFT / 2 + 1 // 201

    // Twiddle table: [bin][n] -> cos/sin of 2*pi*bin*n/N_FFT.
    private val twiddleCos: Array<FloatArray> by lazy { buildTwiddle(true) }
    private val twiddleSin: Array<FloatArray> by lazy { buildTwiddle(false) }

    private fun buildTwiddle(isCos: Boolean): Array<FloatArray> {
        val t = Array(N_BINS) { FloatArray(N_FFT) }
        for (k in 0 until N_BINS) {
            for (n in 0 until N_FFT) {
                val a = (2.0 * Math.PI * k * n / N_FFT).toFloat()
                t[k][n] = if (isCos) cos(a) else sin(a)
            }
        }
        return t
    }

    // Hann window, precomputed.
    private val hann: FloatArray by lazy {
        FloatArray(N_FFT) { n ->
            (0.5 * (1.0 - cos(2.0 * Math.PI * n / (N_FFT - 1)))).toFloat()
        }
    }

    // Mel filterbank: [mel][bin], triangular, HTK scale.
    private val melFilters: Array<FloatArray> by lazy { buildMelFilters() }

    private fun hzToMel(hz: Float): Float = 2595f * log10(1f + hz / 700f)
    private fun melToHz(mel: Float): Float = 700f * (Math.pow(10.0, (mel / 2595f).toDouble()).toFloat() - 1f)

    private fun buildMelFilters(): Array<FloatArray> {
        val filters = Array(N_MELS) { FloatArray(N_BINS) }
        val melLow = hzToMel(FMIN)
        val melHigh = hzToMel(FMAX)
        // 80 filters need 82 edge points.
        val points = FloatArray(N_MELS + 2) { i ->
            melToHz(melLow + i * (melHigh - melLow) / (N_MELS + 1))
        }
        val bins = IntArray(N_MELS + 2) { i ->
            ((N_FFT + 1) * points[i] / SAMPLE_RATE).toInt().coerceIn(0, N_BINS - 1)
        }
        for (m in 0 until N_MELS) {
            val f0 = bins[m]
            val f1 = bins[m + 1]
            val f2 = bins[m + 2]
            if (f1 > f0) {
                for (k in f0 until f1) {
                    filters[m][k] = (k - f0).toFloat() / (f1 - f0).coerceAtLeast(1)
                }
            }
            if (f2 > f1) {
                for (k in f1 until f2) {
                    filters[m][k] = (f2 - k).toFloat() / (f2 - f1).coerceAtLeast(1)
                }
            }
        }
        return filters
    }

    /**
     * @param samples 16 kHz mono float PCM, any length.
     * @return FloatArray of size 80*3000, row-major [mel][frame].
     */
    fun compute(samples: FloatArray, checkCancelled: () -> Unit = {}): FloatArray {
        val nFrames = (samples.size + HOP - 1) / HOP
        val useFrames = minOf(nFrames, N_FRAMES)
        // Magnitude spectrogram -> mel energies, computed per frame.
        val melEnergies = FloatArray(N_MELS * useFrames)
        val frame = FloatArray(N_FFT)
        for (f in 0 until useFrames) {
            // Spectrogram calculation is CPU-bound and otherwise cannot observe coroutine cancellation.
            if (f % 32 == 0) checkCancelled()
            val start = f * HOP
            for (n in 0 until N_FFT) {
                val s = if (start + n < samples.size) samples[start + n] else 0f
                frame[n] = s * hann[n]
            }
            // DFT bins 0..200 via twiddle table.
            for (k in 0 until N_BINS) {
                var re = 0.0
                var im = 0.0
                val tc = twiddleCos[k]
                val ts = twiddleSin[k]
                for (n in 0 until N_FFT) {
                    val x = frame[n].toDouble()
                    re += x * tc[n]
                    im -= x * ts[n]
                }
                val mag = sqrt(re * re + im * im).toFloat()
                // Accumulate into mel bins directly (saves a 201-float temp).
                for (m in 0 until N_MELS) {
                    val w = melFilters[m][k]
                    if (w != 0f) melEnergies[f * N_MELS + m] += w * mag
                }
            }
        }
        // log10 + normalisation (matches k2-fsa test.py exactly).
        var maxLog = Float.NEGATIVE_INFINITY
        val logged = FloatArray(N_MELS * useFrames)
        for (i in logged.indices) {
            val v = log10(maxOf(melEnergies[i], 1e-10f))
            logged[i] = v
            if (v > maxLog) maxLog = v
        }
        val floor = maxLog - 8.0f
        // Output [80][3000], zero-padded.
        val out = FloatArray(N_MELS * N_FRAMES)
        for (f in 0 until useFrames) {
            for (m in 0 until N_MELS) {
                val v = maxOf(logged[f * N_MELS + m], floor)
                out[m * N_FRAMES + f] = (v + 4.0f) / 4.0f
            }
        }
        return out
    }
}
