package com.apexstudio.app.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBeatDetectorTest {
    @Test
    fun detectsSyntheticClickTrackWithinOneAnalysisFrame() {
        val expectedMs = listOf(250L, 750L, 1_250L, 1_750L, 2_250L, 2_750L, 3_250L)
        val pcm = syntheticClickTrack(
            durationMs = 3_600,
            beatTimesMs = expectedMs,
            sampleRateHz = 16_000
        )

        val detected = AudioBeatDetector.detect(pcm, sampleRateHz = 16_000)

        assertEquals("one onset should be found for each synthetic click", expectedMs.size, detected.size)
        expectedMs.zip(detected).forEach { (expected, actual) ->
            assertTrue("expected $expected ms, got $actual ms", kotlin.math.abs(expected - actual) <= 5L)
        }
    }

    @Test
    fun streamingAccumulatorMatchesPcmHelper() {
        val pcm = syntheticClickTrack(1_600, listOf(100L, 600L, 1_100L), 16_000)
        val expected = AudioBeatDetector.detect(pcm, 16_000)
        val accumulator = AudioBeatDetector.Accumulator(16_000)
        pcm.forEach(accumulator::addSample)

        assertEquals(expected, accumulator.finish())
    }

    @Test
    fun silenceProducesNoBeatMarkers() {
        assertTrue(AudioBeatDetector.detect(FloatArray(16_000), 16_000).isEmpty())
    }

    private fun syntheticClickTrack(
        durationMs: Int,
        beatTimesMs: List<Long>,
        sampleRateHz: Int
    ): FloatArray {
        val pcm = FloatArray(durationMs * sampleRateHz / 1_000)
        // Low deterministic background noise keeps the fixture representative
        // of decoded audio while remaining well below each transient.
        for (index in pcm.indices) {
            pcm[index] = (((index * 31) % 101) - 50) / 10_000f
        }
        beatTimesMs.forEachIndexed { beatIndex, timeMs ->
            val start = (timeMs * sampleRateHz / 1_000).toInt()
            val amplitude = if (beatIndex % 2 == 0) 0.8f else 0.55f
            val pulseLength = sampleRateHz * 4 / 1_000
            for (offset in 0 until pulseLength) {
                if (start + offset in pcm.indices) pcm[start + offset] += amplitude
            }
        }
        return pcm
    }
}
