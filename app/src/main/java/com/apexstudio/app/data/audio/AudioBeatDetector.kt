package com.apexstudio.app.data.audio

import kotlin.math.sqrt

/**
 * Small on-device transient detector for decoded mono PCM. It reduces the PCM
 * stream to 5 ms RMS-energy frames, then picks strong positive energy changes
 * as onsets. No model, network call, or third-party DSP dependency is used.
 */
object AudioBeatDetector {
    private const val FRAME_MS = 5
    private const val MIN_ONSET_SPACING_MS = 80
    private const val RELATIVE_THRESHOLD = 0.08f
    private const val MAD_MULTIPLIER = 4f

    /** Incremental accumulator so long imported tracks need not be retained in memory. */
    class Accumulator(
        private val sampleRateHz: Int = PcmAudioExtractor.TARGET_RATE,
        frameMs: Int = FRAME_MS
    ) {
        private val frameSamples: Int
        private val frames = ArrayList<Float>()
        private var sumSquares = 0.0
        private var samplesInFrame = 0
        private var finished = false

        init {
            require(sampleRateHz > 0) { "sampleRateHz must be positive" }
            require(frameMs > 0) { "frameMs must be positive" }
            frameSamples = (sampleRateHz.toLong() * frameMs / 1_000L).toInt().coerceAtLeast(1)
        }

        fun addSample(sample: Float) {
            check(!finished) { "Cannot add samples after finish()" }
            val value = if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
            sumSquares += value.toDouble() * value
            samplesInFrame++
            if (samplesInFrame == frameSamples) flushFrame()
        }

        /** Returns source-relative millisecond timestamps for detected onsets. */
        fun finish(): List<Long> {
            check(!finished) { "finish() may only be called once" }
            finished = true
            if (samplesInFrame > 0) flushFrame()
            return detectFrames(frames)
        }

        private fun flushFrame() {
            frames += sqrt(sumSquares / samplesInFrame).toFloat()
            sumSquares = 0.0
            samplesInFrame = 0
        }
    }

    /** Pure helper used by unit tests and callers that already have PCM in memory. */
    fun detect(pcm: FloatArray, sampleRateHz: Int = PcmAudioExtractor.TARGET_RATE): List<Long> {
        if (sampleRateHz <= 0 || pcm.isEmpty()) return emptyList()
        val accumulator = Accumulator(sampleRateHz)
        pcm.forEach(accumulator::addSample)
        return accumulator.finish()
    }

    private fun detectFrames(energy: List<Float>): List<Long> {
        if (energy.size < 2) return emptyList()
        val novelty = FloatArray(energy.size)
        for (index in energy.indices) {
            val previous = if (index == 0) 0f else energy[index - 1]
            novelty[index] = (energy[index] - previous).coerceAtLeast(0f)
        }

        val sorted = novelty.sorted()
        val median = sorted[sorted.size / 2]
        val deviations = novelty.map { kotlin.math.abs(it - median) }.sorted()
        val mad = deviations[deviations.size / 2]
        val maximum = novelty.maxOrNull() ?: 0f
        if (maximum <= 1e-6f) return emptyList()
        val threshold = maxOf(maximum * RELATIVE_THRESHOLD, median + MAD_MULTIPLIER * mad)
        val minSpacingFrames = (MIN_ONSET_SPACING_MS / FRAME_MS).coerceAtLeast(1)
        val candidates = mutableListOf<Int>()

        for (index in novelty.indices) {
            val value = novelty[index]
            if (value < threshold) continue
            val isLocalMaximum = value >= (novelty.getOrNull(index - 1) ?: Float.NEGATIVE_INFINITY) &&
                value > (novelty.getOrNull(index + 1) ?: Float.NEGATIVE_INFINITY)
            if (!isLocalMaximum) continue

            val previousIndex = candidates.lastOrNull()
            if (previousIndex == null || index - previousIndex >= minSpacingFrames) {
                candidates += index
            } else if (value > novelty[previousIndex]) {
                candidates[candidates.lastIndex] = index
            }
        }
        return candidates.map { it.toLong() * FRAME_MS }.distinct()
    }
}
