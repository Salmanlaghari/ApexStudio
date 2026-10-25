package com.apexstudio.app.data.audio

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Small on-device transient detector for decoded mono PCM. It reduces the PCM
 * stream to short RMS-energy frames, then picks strong positive energy changes
 * as onsets. No model, network call, or third-party DSP dependency is used.
 */
object AudioBeatDetector {
    private const val FRAME_MS = 5
    private const val MIN_ONSET_SPACING_MS = 80
    private const val RELATIVE_THRESHOLD = 0.08f
    private const val MAD_MULTIPLIER = 4f

    /**
     * Streams PCM into compact per-frame energy values for robust whole-track
     * thresholding; decoded PCM samples themselves are never retained.
     */
    class Accumulator(
        private val sampleRateHz: Int = PcmAudioExtractor.TARGET_RATE,
        frameMs: Int = FRAME_MS
    ) {
        private val frameMs = frameMs
        private val frameSamples: Int
        private val frames = FloatFrameBuffer()
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
            return detectFrames(frames.takeArray(), frameSamples, sampleRateHz, frameMs)
        }

        private fun flushFrame() {
            frames.add(sqrt(sumSquares / samplesInFrame).toFloat())
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

    /** Median for an ascending, non-empty array; averages both middle values for even lengths. */
    internal fun medianOfSorted(values: FloatArray): Float {
        require(values.isNotEmpty()) { "values must not be empty" }
        val middle = values.size / 2
        return if (values.size % 2 == 0) {
            (values[middle - 1] + values[middle]) / 2f
        } else {
            values[middle]
        }
    }

    private fun detectFrames(
        energy: FloatArray,
        frameSamples: Int,
        sampleRateHz: Int,
        frameMs: Int
    ): List<Long> {
        if (energy.size < 2) return emptyList()
        val novelty = FloatArray(energy.size)
        for (index in energy.indices) {
            val previous = if (index == 0) 0f else energy[index - 1]
            novelty[index] = (energy[index] - previous).coerceAtLeast(0f)
        }

        val sortedNovelty = novelty.copyOf().also { it.sort() }
        val median = medianOfSorted(sortedNovelty)
        val deviations = FloatArray(novelty.size) { index -> abs(novelty[index] - median) }
        deviations.sort()
        val mad = medianOfSorted(deviations)
        val maximum = novelty.maxOrNull() ?: 0f
        if (maximum <= 1e-6f) return emptyList()
        val threshold = maxOf(maximum * RELATIVE_THRESHOLD, median + MAD_MULTIPLIER * mad)
        val frameDurationMs = frameSamples.toDouble() * 1_000.0 / sampleRateHz
        val minSpacingFrames = (MIN_ONSET_SPACING_MS / frameDurationMs).roundToInt().coerceAtLeast(1)
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
        return candidates.map { index ->
            (index.toDouble() * frameSamples * 1_000.0 / sampleRateHz).roundToLong()
        }
    }

    private class FloatFrameBuffer {
        private var values = FloatArray(INITIAL_CAPACITY)
        private var size = 0

        fun add(value: Float) {
            if (size == values.size) {
                values = values.copyOf((values.size * 2).coerceAtLeast(INITIAL_CAPACITY))
            }
            values[size++] = value
        }

        fun takeArray(): FloatArray {
            val result = values.copyOf(size)
            values = FloatArray(0)
            size = 0
            return result
        }

        private companion object {
            const val INITIAL_CAPACITY = 1_024
        }
    }
}
