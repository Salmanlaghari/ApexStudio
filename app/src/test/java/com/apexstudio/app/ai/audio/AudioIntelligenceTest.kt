package com.apexstudio.app.ai.audio

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [AudioIntelligence] — no Android, no model, no network.
 *
 * These exist because the module is deterministic DSP: given a known signal the
 * output is fully determined, so a real assertion is possible instead of a
 * smoke test. Tolerances are wide enough to survive float noise but tight
 * enough that a broken analysis fails.
 */
class AudioIntelligenceTest {

    private companion object {
        const val RATE = 16_000
    }

    /** Constant-amplitude sine over [samples]; cycles controls the pitch. */
    private fun sine(samples: Int, amplitude: Float, cycles: Int): FloatArray =
        FloatArray(samples) { i ->
            (amplitude * sin(2.0 * PI * cycles * i / samples)).toFloat()
        }

    /** Train of short clicks spaced [periodSamples] apart — a synthetic metronome. */
    private fun clickTrain(totalSamples: Int, periodSamples: Int, burst: Int = 200): FloatArray {
        val pcm = FloatArray(totalSamples)
        var at = 0
        while (at < totalSamples) {
            for (k in 0 until burst) {
                if (at + k < totalSamples) pcm[at + k] = 0.9f
            }
            at += periodSamples
        }
        return pcm
    }

    @Test
    fun loudnessOfKnownSineMatchesAnalyticRms() {
        val pcm = sine(samples = RATE, amplitude = 0.5f, cycles = 440)
        val result = AudioIntelligence.measureLoudness(pcm, RATE)

        // RMS of a 0.5-amplitude sine is 0.5/sqrt(2) = 0.3536 -> -9.03 dBFS.
        assertTrue(
            "rmsDbfs was ${result.rmsDbfs}, expected ~ -9.03",
            result.rmsDbfs in -9.6f..-8.4f
        )
        // Peak of a 0.5-amplitude sine is 0.5 -> -6.02 dBFS.
        assertTrue(
            "peakDbfs was ${result.peakDbfs}, expected ~ -6.02",
            result.peakDbfs in -6.4f..-5.6f
        )
        // Gated loudness sits close to RMS for a stationary signal.
        assertTrue(
            "gatedLufs was ${result.gatedLufs}",
            result.gatedLufs in -12f..-6f
        )
        assertTrue("gain must stay positive", result.suggestedGain > 0f)
    }

    @Test
    fun silenceIsReportedAsVeryQuiet() {
        val pcm = FloatArray(RATE)
        val result = AudioIntelligence.measureLoudness(pcm, RATE)
        assertEquals(-120f, result.rmsDbfs, 0.001f)
    }

    @Test
    fun detectBeatsFindsClickTrainTempo() {
        // 120 BPM at 16 kHz = one click every 8000 samples.
        val period = RATE * 60 / 120
        val pcm = clickTrain(totalSamples = RATE * 8, periodSamples = period)
        val result = AudioIntelligence.detectBeats(pcm, RATE)

        assertTrue(
            "expected several onsets in a 8 s click train, got ${result.beats.size}",
            result.beats.size >= 6
        )
        assertTrue(
            "bpm was ${result.bpm}, expected near 120",
            result.bpm in 114f..126f
        )
    }

    @Test
    fun detectBeatsOnSilenceReportsNoTempo() {
        val result = AudioIntelligence.detectBeats(FloatArray(RATE), RATE)
        assertEquals(0f, result.bpm, 0.001f)
        assertTrue(result.beats.isEmpty())
    }

    @Test
    fun findSilenceSeparatesLoudBlocksAroundAGap() {
        val loud = sine(samples = RATE, amplitude = 0.6f, cycles = 300)
        val gap = FloatArray(RATE) // one second of digital silence
        val pcm = loud + gap + loud

        val intervals = AudioIntelligence.findSilence(pcm, RATE)
        assertEquals(
            "expected two loud blocks around one silent second, got ${intervals.size}",
            2, intervals.size
        )
        assertTrue(
            "first block should start near 0, was ${intervals[0].startSample}",
            intervals[0].startSample < RATE / 10
        )
        assertTrue(
            "second block should start near 2 s, was ${intervals[1].startSample}",
            intervals[1].startSample in RATE..(RATE * 2 + RATE / 10)
        )
    }

    @Test
    fun autoDuckLowersGainWhereSpeechIsLoud() {
        val music = sine(samples = RATE * 4, amplitude = 0.5f, cycles = 200)
        // Speech present only in the middle two seconds.
        val speech = FloatArray(RATE * 4)
        for (i in RATE until RATE * 3) {
            speech[i] = (0.7f * sin(2.0 * PI * 150 * i / RATE)).toFloat()
        }

        val plan = AudioIntelligence.autoDuckPlan(music, speech, RATE)
        assertTrue("plan must not be empty", plan.isNotEmpty())
        assertTrue(
            "gain should dip below 0.9 somewhere, min was ${plan.minOf { it.gain }}",
            plan.any { it.gain < 0.9f }
        )
        assertTrue(
            "gain should return near unity outside speech, max was ${plan.maxOf { it.gain }}",
            plan.any { it.gain > 0.95f }
        )
        assertTrue(
            "times must be non-negative and ordered",
            plan.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs }
        )
    }

    @Test
    fun fitToDurationReturnsRequestedLength() {
        val music = sine(samples = RATE * 10, amplitude = 0.5f, cycles = 500)
        val targetSamples = RATE * 4

        val window = AudioIntelligence.fitToDuration(music, RATE, targetSamples)
        val length = window.endSampleExclusive - window.startSample
        assertEquals(
            "window length should match the target, got $length",
            targetSamples, length
        )
        assertTrue(
            "window must stay inside the source",
            window.startSample >= 0 && window.endSampleExclusive <= music.size
        )
    }

    @Test
    fun fitToDurationReturnsWholeTrackWhenTargetIsLonger() {
        val music = sine(samples = RATE * 2, amplitude = 0.5f, cycles = 100)
        val window = AudioIntelligence.fitToDuration(music, RATE, RATE * 10)
        assertEquals(0, window.startSample)
        assertEquals(music.size, window.endSampleExclusive)
    }
}
