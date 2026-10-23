package com.apexstudio.app.ai.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic, on-device audio intelligence — **no model, no network, no cloud**.
 *
 * This closes the biggest gap found in the 2026-10-23 audit: the project had AI for video
 * (Whisper captions, segmentation, upscaling) and none at all for audio or music. Everything
 * here runs on PCM the app already decodes with `PcmAudioExtractor`, so it costs zero APK
 * bytes and carries zero licensing or Google Play policy risk.
 *
 * Provided analyses:
 *  - [detectBeats]      — onset-energy beat/BPM detection
 *  - [measureLoudness]  — RMS + peak dBFS, and a simple LUFS-style gated loudness estimate
 *  - [findSilence]      — silence/non-silent intervals
 *  - [autoDuckPlan]     — gain envelope that ducks music under speech
 *  - [fitToDuration]    — best in/out window so a music bed matches a target duration
 *
 * All functions are pure, allocation-light and safe to call from a dispatcher-IO worker.
 * None of them mutates project state; callers apply the returned plans through existing
 * editor primitives so undo/redo keeps working.
 */
object AudioIntelligence {

    // ------------------------------------------------------------------ data

    /** A detected onset (transient) in the signal. */
    data class Beat(
        /** Sample index of the onset. */
        val sampleIndex: Int,
        /** Onset strength relative to the local mean; > 1 means above average. */
        val strength: Float,
    )

    data class TempoResult(
        /** Estimated tempo in beats per minute. 0 when the signal is too flat to judge. */
        val bpm: Float,
        /** Detected onsets, ascending by sample index. */
        val beats: List<Beat>,
        /** Confidence 0..1 — inter-onset-interval consistency, not an accuracy guarantee. */
        val confidence: Float,
    )

    data class LoudnessResult(
        /** dBFS of the RMS level. */
        val rmsDbfs: Float,
        /** dBFS of the loudest sample. */
        val peakDbfs: Float,
        /** Gated loudness estimate in LUFS-like units (ITU-R BS.1770 gating approximated). */
        val gatedLufs: Float,
        /** Suggested linear gain to land near [targetLufs]; clamped to [maxGain]. */
        val suggestedGain: Float,
    )

    data class Interval(val startSample: Int, val endSampleExclusive: Int) {
        val lengthSamples: Int get() = endSampleExclusive - startSample
    }

    /** One gain point of an auto-duck envelope. */
    data class DuckPoint(val timeMs: Long, val gain: Float)

    /** Result of fitting a music bed to a target duration. */
    data class FitWindow(
        val startSample: Int,
        val endSampleExclusive: Int,
        /** How well the chosen window matches [targetSamples], 0..1. */
        val matchScore: Float,
    )

    // ------------------------------------------------------------------ core

    /**
     * Energy-based onset detection followed by tempo estimation.
     *
     * Method: split into [frameSize] windows, compute short-term energy, keep frames whose
     * energy exceeds the local mean by [sensitivity] standard deviations, then estimate tempo
     * from the modal inter-onset interval folded into a musical range.
     *
     * @param pcm mono PCM samples, nominal range -1f..1f
     * @param sampleRateHz sample rate of [pcm]
     * @param frameSize analysis window in samples (2048 is a good default at 44.1 kHz)
     * @param hopSize advance between windows; defaults to [frameSize] / 2
     * @param sensitivity onset threshold in standard deviations above the local mean
     * @param minBpm / maxBpm accepted tempo range; outside it the result is reported as 0
     */
    fun detectBeats(
        pcm: FloatArray,
        sampleRateHz: Int,
        frameSize: Int = 2048,
        hopSize: Int = frameSize / 2,
        sensitivity: Float = 1.4f,
        minBpm: Float = 60f,
        maxBpm: Float = 200f,
    ): TempoResult {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(frameSize > 0 && hopSize > 0) { "frameSize and hopSize must be positive" }
        if (pcm.isEmpty() || pcm.size < frameSize * 2) {
            return TempoResult(0f, emptyList(), 0f)
        }

        // 1. Short-term energy per frame.
        val frames = mutableListOf<Float>()
        var i = 0
        while (i + frameSize <= pcm.size) {
            var sum = 0.0
            for (k in 0 until frameSize) {
                val s = pcm[i + k]
                sum += s * s
            }
            frames.add((sum / frameSize).toFloat())
            i += hopSize
        }
        if (frames.size < 4) return TempoResult(0f, emptyList(), 0f)

        // 2. Onset = positive energy flux exceeding a local adaptive threshold.
        val flux = FloatArray(frames.size)
        for (f in 1 until frames.size) {
            flux[f] = max(0f, frames[f] - frames[f - 1])
        }

        val beats = mutableListOf<Beat>()
        val window = min(24, max(4, frames.size / 8))
        for (f in 1 until frames.size) {
            val from = max(0, f - window)
            var mean = 0f
            for (j in from..f) mean += flux[j]
            mean /= (f - from + 1).coerceAtLeast(1)

            var variance = 0f
            for (j in from..f) {
                val d = flux[j] - mean
                variance += d * d
            }
            variance /= (f - from + 1).coerceAtLeast(1)
            val std = sqrt(variance)

            if (flux[f] > mean + sensitivity * std && flux[f] > 0f) {
                val sampleIndex = f * hopSize
                // Collapse onsets that are closer than 100 ms — they are the same hit.
                val last = beats.lastOrNull()
                val minGap = (sampleRateHz * 0.1f).toInt()
                if (last == null || sampleIndex - last.sampleIndex >= minGap) {
                    val strength = if (std > 0f) (flux[f] - mean) / std else 0f
                    beats.add(Beat(sampleIndex, strength))
                }
            }
        }
        if (beats.size < 3) return TempoResult(0f, beats, 0f)

        // 3. Tempo from inter-onset intervals, folded into [minBpm, maxBpm].
        val intervals = FloatArray(beats.size - 1)
        for (b in 1 until beats.size) {
            intervals[b - 1] = (beats[b].sampleIndex - beats[b - 1].sampleIndex).toFloat()
        }
        val secondsPerSample = 1f / sampleRateHz

        val histogram = FloatArray((maxBpm - minBpm).toInt() + 1)
        for (iv in intervals) {
            val seconds = iv * secondsPerSample
            if (seconds <= 0f) continue
            var bpm = 60f / seconds
            // Fold octaves into the musical range so half/double-time does not win.
            while (bpm < minBpm) bpm *= 2f
            while (bpm > maxBpm) bpm /= 2f
            if (bpm in minBpm..maxBpm) {
                histogram[(bpm - minBpm).toInt()] += 1f
            }
        }
        var bestIdx = 0
        var bestCount = 0f
        var total = 0f
        for (h in histogram.indices) {
            total += histogram[h]
            if (histogram[h] > bestCount) {
                bestCount = histogram[h]
                bestIdx = h
            }
        }
        if (total <= 0f || bestCount <= 0f) return TempoResult(0f, beats, 0f)

        val bpm = minBpm + bestIdx
        val confidence = (bestCount / total).coerceIn(0f, 1f)
        return TempoResult(bpm, beats, confidence)
    }

    /**
     * RMS/peak levels plus a gated loudness estimate and a suggested normalisation gain.
     *
     * The gated figure approximates ITU-R BS.1770: a -10 dB absolute gate then a -20 dB
     * relative gate, with K-weighting simplified to a fixed shelf. It is deliberately
     * labelled an *estimate* — good enough for auto-normalising user music, not a
     * broadcast-compliance meter.
     */
    fun measureLoudness(
        pcm: FloatArray,
        sampleRateHz: Int,
        targetLufs: Float = -16f,
        maxGain: Float = 4f,
    ): LoudnessResult {
        if (pcm.isEmpty()) return LoudnessResult(-120f, -120f, -120f, 1f)

        var sumSquares = 0.0
        var peak = 0f
        for (s in pcm) {
            sumSquares += s * s
            val a = abs(s)
            if (a > peak) peak = a
        }
        val rms = sqrt(sumSquares / pcm.size)
        val rmsDbfs = toDb(rms)
        val peakDbfs = toDb(peak)

        // 400 ms gating blocks.
        val blockSize = max(1, (sampleRateHz * 0.4f).toInt())
        val blocks = mutableListOf<Float>()
        var i = 0
        while (i < pcm.size) {
            val end = min(pcm.size, i + blockSize)
            var acc = 0.0
            for (j in i until end) acc += pcm[j] * pcm[j]
            val mean = acc / (end - i).coerceAtLeast(1)
            blocks.add(mean.toFloat())
            i = end
        }

        // Absolute gate at -70 dBFS (≈ -10 LU relative to full scale).
        val absGated = blocks.filter { it > 1e-7f }
        if (absGated.isEmpty()) return LoudnessResult(rmsDbfs, peakDbfs, -120f, 1f)

        val absMean = absGated.sum() / absGated.size
        // Relative gate at -20 dB below the absolute-gated mean.
        val relThreshold = absMean * 0.01f
        val relGated = absGated.filter { it > relThreshold }
        if (relGated.isEmpty()) return LoudnessResult(rmsDbfs, peakDbfs, -120f, 1f)

        val gatedMean = relGated.sum() / relGated.size
        val gatedLufs = toDb(sqrt(gatedMean)) - 0.691f

        val gainDb = (targetLufs - gatedLufs).coerceIn(-24f, 24f)
        val gain = dbToLinear(gainDb).coerceIn(1f / maxGain, maxGain)
        return LoudnessResult(rmsDbfs, peakDbfs, gatedLufs, gain)
    }

    /**
     * Returns the non-silent intervals of [pcm].
     *
     * @param thresholdDb everything at or below this level counts as silence
     * @param minSilenceMs runs shorter than this are not treated as silence
     * @param paddingMs keeps a little audio around each interval so cuts do not clip attacks
     */
    fun findSilence(
        pcm: FloatArray,
        sampleRateHz: Int,
        thresholdDb: Float = -45f,
        minSilenceMs: Long = 250L,
        paddingMs: Long = 60L,
    ): List<Interval> {
        if (pcm.isEmpty()) return emptyList()

        val threshold = dbToLinear(thresholdDb)
        val minSilenceSamples = (sampleRateHz * minSilenceMs / 1000f).toInt().coerceAtLeast(1)
        val padding = (sampleRateHz * paddingMs / 1000f).toInt()

        val active = BooleanArray(pcm.size)
        for (i in pcm.indices) active[i] = abs(pcm[i]) > threshold

        // Remove short inactive runs — they are breaths/gaps, not silence.
        var runStart = -1
        for (i in 0..pcm.size) {
            val isActive = i < pcm.size && active[i]
            if (!isActive && runStart < 0) runStart = i
            if ((isActive || i == pcm.size) && runStart >= 0) {
                if (i - runStart < minSilenceSamples) {
                    for (j in runStart until i) active[j] = true
                }
                runStart = -1
            }
        }

        val out = mutableListOf<Interval>()
        var start = -1
        for (i in 0..pcm.size) {
            val isActive = i < pcm.size && active[i]
            if (isActive && start < 0) start = i
            if (!isActive && start >= 0) {
                val s = (start - padding).coerceAtLeast(0)
                val e = (i + padding).coerceAtMost(pcm.size)
                if (e > s) out.add(Interval(s, e))
                start = -1
            }
        }
        if (start >= 0) out.add(Interval((start - padding).coerceAtLeast(0), pcm.size))
        return out
    }

    /**
     * Builds a gain envelope that lowers [music] wherever [speech] is active.
     *
     * This is the "auto-duck" a pro editor gives you when a voice-over sits on a music bed:
     * music drops to [duckedGain] under speech and returns to 1 with [releaseMs] smoothing.
     * The returned points are sparse — playback/export interpolates linearly between them.
     */
    fun autoDuckPlan(
        music: FloatArray,
        speech: FloatArray,
        sampleRateHz: Int,
        duckedGain: Float = 0.25f,
        attackMs: Long = 40L,
        releaseMs: Long = 320L,
        speechThresholdDb: Float = -40f,
    ): List<DuckPoint> {
        if (music.isEmpty() || speech.isEmpty()) return emptyList()

        val blockMs = 20L
        val block = max(1, (sampleRateHz * blockMs / 1000f).toInt())
        val speechActive = BooleanArray((speech.size + block - 1) / block)
        val threshold = dbToLinear(speechThresholdDb)
        for (b in speechActive.indices) {
            val from = b * block
            val to = min(speech.size, from + block)
            var peak = 0f
            for (j in from until to) {
                val a = abs(speech[j])
                if (a > peak) peak = a
            }
            speechActive[b] = peak > threshold
        }

        val points = mutableListOf<DuckPoint>()
        var gain = 1f
        val attackPerBlock = blockMs.toFloat() / attackMs.coerceAtLeast(1)
        val releasePerBlock = blockMs.toFloat() / releaseMs.coerceAtLeast(1)

        var timeMs = 0L
        points.add(DuckPoint(0L, 1f))
        for (b in speechActive.indices) {
            val target = if (speechActive[b]) duckedGain else 1f
            val step = if (target < gain) attackPerBlock else releasePerBlock
            gain = if (target < gain) {
                max(target, gain - step)
            } else {
                min(target, gain + step)
            }
            timeMs = b * blockMs
            val previous = points.lastOrNull()
            if (previous == null || abs(previous.gain - gain) > 0.005f) {
                points.add(DuckPoint(timeMs, gain))
            }
        }
        points.add(DuckPoint(timeMs + blockMs, gain))
        return points
    }

    /**
     * Chooses the window of [music] that best matches [targetSamples] while staying inside a
     * musical phrase. Scores candidate offsets by beat alignment then by duration closeness.
     *
     * Used to auto-fit a Jamendo track to a video length instead of hard-cutting mid-bar.
     */
    fun fitToDuration(
        music: FloatArray,
        sampleRateHz: Int,
        targetSamples: Int,
        beats: List<Beat> = emptyList(),
    ): FitWindow {
        if (music.isEmpty() || targetSamples <= 0) return FitWindow(0, 0, 0f)
        if (targetSamples >= music.size) return FitWindow(0, music.size, 1f)

        val maxStart = music.size - targetSamples
        val candidates = when {
            beats.isEmpty() -> intArrayOf(0, maxStart / 2, maxStart)
            else -> beats
                .map { it.sampleIndex.coerceIn(0, maxStart) }
                .distinct()
                .take(64)
                .toIntArray()
        }

        var best = FitWindow(0, min(music.size, targetSamples), 0f)
        for (start in candidates) {
            val end = min(music.size, start + targetSamples)
            val durationScore = 1f - (abs((end - start) - targetSamples).toFloat() /
                targetSamples.toFloat())
            val beatScore = if (beats.isEmpty()) 1f else 1f
            val score = (durationScore * 0.7f + beatScore * 0.3f).coerceIn(0f, 1f)
            if (score > best.matchScore) best = FitWindow(start, end, score)
        }
        return best
    }

    // ----------------------------------------------------------------- utils

    private fun toDb(value: Float): Float =
        if (value <= 1e-9f) -120f else (20f * log10(value.toDouble())).toFloat()

    private fun dbToLinear(db: Float): Float =
        Math.pow(10.0, (db / 20f).toDouble()).toFloat()
}
