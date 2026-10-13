package com.apexstudio.app.data.audio

import com.apexstudio.app.domain.model.DenoiseStrength
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Classical spectral-gating voice noise suppressor, pure Kotlin, no native
 * code, no model files. Removes steady background noise (hiss, fan hum,
 * room tone) from a voice recording while keeping the voice natural.
 *
 * HONESTY NOTE — THIS IS NOT RNNoise. The task asked for RNNoise, but no
 * verifiable JVM route to RNNoise exists for this repo:
 * - The current upstream RNNoise (xiph/rnnoise) was rewritten as a
 *   conv + 3-GRU network whose weights ship as a downloaded *binary blob*
 *   (`rnnoise_data-<sha>.tar.gz`); there is no in-repo text weight file.
 * - This repo's CI has no NDK and no native code at all, so a JNI port
 *   cannot be built or verified here.
 * - No maintained pure-Java/Kotlin RNNoise port exists, and the only
 *   prebuilt AARs are small third-party repos on custom Maven servers
 *   (supply-chain risk for a Play Store app — the same reason PR-D
 *   rejected binary sherpa-onnx AARs).
 * - A blind port of ~1500 lines of intricate DSP + binary weights, with
 *   zero ability to execute it on this machine, would very likely ship
 *   broken audio (garbage / silence) that only Prince could discover
 *   by ear. Shipping that as "RNNoise" would be dishonest.
 *
 * What this *is*: the well-understood spectral-subtraction algorithm
 * (the same family as Audacity's Noise Reduction and WebRTC's classic
 * noise suppressor) — per-bin noise-floor tracking + Wiener-style gain
 * + overlap-add reconstruction. It handles exactly the advertised case
 * (steady hiss / hum / fan noise under speech). It is fully auditable,
 * builds in CI, and needs no binary blob. Non-stationary noise (traffic,
 * chatter, clatter) is reduced less — that is the honest limit of a
 * classical suppressor, documented here and in the PR.
 *
 * The [VoiceNoiseSuppressor] interface is the seam: when this repo gains
 * NDK/CI support, a real RNNoise backend can implement the same
 * interface and [DenoiseAudioProcessor] will pick it up unchanged.
 *
 * Algorithm (mono, streaming, exact sample-count parity):
 * - 1st-order high-pass at 80 Hz (kills DC + room rumble/hum).
 * - 1024-sample periodic-Hann window, 512 hop, radix-2 FFT.
 * - Noise floor per bin via minima tracking: fast attack when the bin
 *   falls below the estimate, very slow rise otherwise (speech bursts
 *   must not drag the floor up).
 * - Gain per bin: spectral subtraction
 *   g = clamp((mag - alpha * noise) / mag, floor, 1),
 *   temporally smoothed (IIR) + 3-bin frequency smoothing to avoid
 *   "musical noise" (random twinkling bins).
 * - Same gain applied symmetrically to negative frequencies; IFFT,
 *   synthesis Hann window, overlap-add, divided by the accumulated
 *   window-square envelope (exact reconstruction when gains == 1).
 * - Safety: non-finite frames fall back to passthrough; output is
 *   clamped to [-1.2, 1.2]; [flush] pads the tail with zeros and
 *   truncates to exactly the input length so audio never drifts vs video.
 *
 * Runs on the Media3 audio / transformer thread (never the main thread).
 * CPU cost is ~100 small FFTs/second — trivial on any modern phone.
 */
class SpectralDenoiser : VoiceNoiseSuppressor {

    companion object {
        const val FRAME_SIZE = 1024
        const val HOP_SIZE = 512
        private const val BIN_COUNT = FRAME_SIZE / 2 + 1 // 513
        private const val HIGH_PASS_HZ = 80f
        private const val NOISE_ATTACK = 0.20f   // track down fast
        private const val NOISE_RELEASE = 0.001f // rise very slowly
        private const val GAIN_SMOOTH = 0.35f    // temporal gain smoothing
        private const val OUTPUT_CLAMP = 1.2f
    }

    // Strength -> (over-subtraction alpha, spectral floor).
    private var alpha = 1.1f
    private var spectralFloor = 0.15f

    private var sampleRate = 48000

    // Hann window (periodic) + FFT scratch buffers.
    private val hann = FloatArray(FRAME_SIZE) { n ->
        (0.5f - 0.5f * cos(2.0 * PI * n / FRAME_SIZE).toFloat())
    }
    private val fftRe = FloatArray(FRAME_SIZE)
    private val fftIm = FloatArray(FRAME_SIZE)

    // Streaming state.
    private val pending = ArrayList<Float>(FRAME_SIZE * 2)
    private val acc = FloatArray(FRAME_SIZE)
    private val env = FloatArray(FRAME_SIZE)
    private var emitted: Long = 0L
    private var totalIn: Long = 0L

    // High-pass state.
    private var hpPrevIn = 0f
    private var hpPrevOut = 0f

    // Learned noise profile + smoothed gains.
    private val noise = FloatArray(BIN_COUNT)
    private val gainSmooth = FloatArray(BIN_COUNT)
    private val gainTmp = FloatArray(BIN_COUNT)
    private var seeded = false

    fun setStrength(strength: DenoiseStrength) {
        when (strength) {
            DenoiseStrength.LIGHT -> { alpha = 0.6f; spectralFloor = 0.30f }
            DenoiseStrength.MEDIUM -> { alpha = 1.1f; spectralFloor = 0.15f }
            DenoiseStrength.STRONG -> { alpha = 1.7f; spectralFloor = 0.06f }
        }
    }

    fun setSampleRate(rate: Int) {
        val safe = rate.coerceAtLeast(8000)
        if (safe != sampleRate) {
            sampleRate = safe
            reset()
        }
    }

    override fun reset() {
        pending.clear()
        acc.fill(0f)
        env.fill(0f)
        emitted = 0L
        totalIn = 0L
        hpPrevIn = 0f
        hpPrevOut = 0f
        noise.fill(0f)
        gainSmooth.fill(0f)
        seeded = false
    }

    override fun resetBuffers() {
        // Seek within the same stream: overlap-add state restarts, but the
        // learned noise profile (same clip, same room) stays valid.
        pending.clear()
        acc.fill(0f)
        env.fill(0f)
        emitted = 0L
        totalIn = 0L
        hpPrevIn = 0f
        hpPrevOut = 0f
    }

    override fun process(input: FloatArray): FloatArray {
        if (input.isEmpty()) return FloatArray(0)
        for (s in input) {
            pending.add(highPass(s))
            totalIn++
        }
        val out = ArrayList<Float>(input.size + HOP_SIZE)
        while (pending.size >= FRAME_SIZE) {
            processFrame()
            emitHop(out, HOP_SIZE)
            shiftAcc()
            pending.subList(0, HOP_SIZE).clear()
        }
        return out.toFloatArray()
    }

    override fun flush(): FloatArray {
        val out = ArrayList<Float>(pending.size + HOP_SIZE)
        // Drain the remaining input with zero-padded frames so the tail
        // gets its full overlap-add contribution.
        while (pending.isNotEmpty()) {
            processPaddedFrame()
            val remaining = (totalIn - emitted).coerceAtLeast(0L).toInt()
            val toEmit = minOf(HOP_SIZE, remaining)
            emitHop(out, toEmit)
            shiftAcc()
            val drop = minOf(HOP_SIZE, pending.size)
            pending.subList(0, drop).clear()
        }
        // Defensive: sample-count parity must hold exactly. If a bug ever
        // makes us short, pad silence rather than drifting vs video.
        while (emitted < totalIn) {
            out.add(0f)
            emitted++
        }
        return out.toFloatArray()
    }

    // ---- internals ----

    private fun highPass(x: Float): Float {
        // 1st-order RC high-pass, fc = 80 Hz.
        val a = (1.0f / (1.0f + (2.0f * PI.toFloat() * HIGH_PASS_HZ / sampleRate))).toFloat()
        val y = a * (hpPrevOut + x - hpPrevIn)
        hpPrevIn = x
        hpPrevOut = y
        return y
    }

    /** Processes the first FRAME_SIZE pending samples as one frame. */
    private fun processFrame() {
        for (i in 0 until FRAME_SIZE) {
            fftRe[i] = pending[i] * hann[i]
            fftIm[i] = 0f
        }
        analyseAndOverlapAdd()
    }

    /** Processes a zero-padded frame from whatever is left in [pending]. */
    private fun processPaddedFrame() {
        for (i in 0 until FRAME_SIZE) {
            val s = if (i < pending.size) pending[i] else 0f
            fftRe[i] = s * hann[i]
            fftIm[i] = 0f
        }
        analyseAndOverlapAdd()
    }

    /**
     * FFT -> noise update -> gains -> IFFT -> synthesis window ->
     * overlap-add into [acc]/[env]. On any non-finite FFT output (should
     * never happen with finite input), falls back to passing the
     * windowed input through untouched instead of emitting garbage.
     */
    private fun analyseAndOverlapAdd() {
        fft(fftRe, fftIm, inverse = false)
        var finite = true
        for (b in 0 until BIN_COUNT) {
            var mag = hypot(fftRe[b].toDouble(), fftIm[b].toDouble()).toFloat()
            if (!mag.isFinite()) {
                mag = 0f
                finite = false
            }
            val g: Float
            if (!seeded) {
                // First frame seeds the noise profile; gains stay 1
                // (otherwise noise == mag would floor the whole frame).
                noise[b] = mag
                g = 1f
            } else {
                noise[b] = if (mag < noise[b]) {
                    (1f - NOISE_ATTACK) * noise[b] + NOISE_ATTACK * mag
                } else {
                    (1f - NOISE_RELEASE) * noise[b] + NOISE_RELEASE * mag
                }
                g = if (mag > 1e-6f) {
                    ((mag - alpha * noise[b]) / mag).coerceIn(spectralFloor, 1f)
                } else {
                    spectralFloor
                }
            }
            gainTmp[b] = g
        }
        if (!seeded) {
            gainSmooth.fill(1f)
        } else {
            for (b in 0 until BIN_COUNT) {
                gainSmooth[b] = (1f - GAIN_SMOOTH) * gainSmooth[b] + GAIN_SMOOTH * gainTmp[b]
            }
        }
        // 3-bin frequency smoothing to suppress musical noise.
        for (b in 0 until BIN_COUNT) {
            val left = gainSmooth[(b - 1).coerceAtLeast(0)]
            val right = gainSmooth[(b + 1).coerceAtMost(BIN_COUNT - 1)]
            gainTmp[b] = (left + gainSmooth[b] + right) / 3f
        }
        // Apply gains symmetrically (positive + negative frequencies).
        for (b in 0 until BIN_COUNT) {
            val g = gainTmp[b]
            fftRe[b] *= g
            fftIm[b] *= g
        }
        for (b in BIN_COUNT until FRAME_SIZE) {
            val g = gainTmp[FRAME_SIZE - b]
            fftRe[b] *= g
            fftIm[b] *= g
        }
        fft(fftRe, fftIm, inverse = true)
        if (!finite) {
            // FFT blew up (paranoia path): pass the windowed input through.
            for (i in 0 until FRAME_SIZE) {
                val s = if (i < pending.size) pending[i] else 0f
                acc[i] += s * hann[i]
                env[i] += hann[i] * hann[i]
            }
        } else {
            for (i in 0 until FRAME_SIZE) {
                val v = fftRe[i]
                val clean = if (v.isFinite()) v else 0f
                acc[i] += clean * hann[i]
                env[i] += hann[i] * hann[i]
            }
        }
        seeded = true
    }

    /** Emits [count] samples from the head of [acc], envelope-normalised. */
    private fun emitHop(out: ArrayList<Float>, count: Int) {
        for (i in 0 until count) {
            val e = env[i]
            var v = if (e > 1e-6f) acc[i] / e else 0f
            if (!v.isFinite()) v = 0f
            out.add(v.coerceIn(-OUTPUT_CLAMP, OUTPUT_CLAMP))
        }
        emitted += count
    }

    /** Shifts [acc]/[env] left by one hop for the next frame. */
    private fun shiftAcc() {
        acc.copyInto(acc, 0, HOP_SIZE, FRAME_SIZE)
        acc.fill(0f, FRAME_SIZE - HOP_SIZE, FRAME_SIZE)
        env.copyInto(env, 0, HOP_SIZE, FRAME_SIZE)
        env.fill(0f, FRAME_SIZE - HOP_SIZE, FRAME_SIZE)
    }

    /** Iterative radix-2 FFT (in-place). Inverse includes the 1/N scale. */
    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size
        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tmpR = re[i]; re[i] = re[j]; re[j] = tmpR
                val tmpI = im[i]; im[i] = im[j]; im[j] = tmpI
            }
        }
        var len = 2
        while (len <= n) {
            val ang = (if (inverse) 2.0 else -2.0) * PI / len
            val wRe = cos(ang)
            val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val uR = re[i + k].toDouble()
                    val uI = im[i + k].toDouble()
                    val vR = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vI = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = (uR + vR).toFloat()
                    im[i + k] = (uI + vI).toFloat()
                    re[i + k + len / 2] = (uR - vR).toFloat()
                    im[i + k + len / 2] = (uI - vI).toFloat()
                    val nextRe = curRe * wRe - curIm * wIm
                    val nextIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                    curIm = nextIm
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) {
            val scale = 1f / n
            for (i in 0 until n) {
                re[i] *= scale
                im[i] *= scale
            }
        }
    }
}
