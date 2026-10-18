package com.apexstudio.app.ai.tflite

import com.apexstudio.app.ai.AiCancellationToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class LocalVideoAnalysisTest {
    @Test fun detectsLargeHistogramChangeButNotIdenticalFrames() {
        val dark = FloatArray(64) { 0.1f }
        val bright = FloatArray(64) { 0.9f }
        assertEquals(0f, LocalVideoAnalysis.frameChange(dark, dark), 0.0001f)
        assertTrue(LocalVideoAnalysis.frameChange(dark, bright) > 0.8f)
        val frames = listOf(
            LumaFrame(0, 8, 8, dark), LumaFrame(500, 8, 8, dark),
            LumaFrame(1_000, 8, 8, bright), LumaFrame(1_500, 8, 8, bright)
        )
        val result = LocalVideoAnalysis.analyze(frames, highlightWindowMs = 1_000)
        assertEquals(1_000L, result.boundaries.single().timestampMs)
        assertTrue(result.highlights.isNotEmpty())
        assertTrue(result.highlights.all { it.endMs > it.startMs && it.score in 0f..1f })
    }

    @Test fun ranksWindowsWithMeasuredAudioEnergyHigherWhenOtherInputsMatch() {
        val quiet = listOf(LumaFrame(0, 2, 2, FloatArray(4) { .5f }), LumaFrame(1_000, 2, 2, FloatArray(4) { .5f }))
        val loud = listOf(LumaFrame(3_000, 2, 2, FloatArray(4) { .5f }, .9f), LumaFrame(4_000, 2, 2, FloatArray(4) { .5f }, .9f))
        val result = LocalVideoAnalysis.analyze(quiet + loud, histogramDistanceThreshold = 1f, highlightWindowMs = 2_000)
        assertEquals(3_000L, result.highlights.first().startMs)
        assertTrue(result.highlights.first().score > result.highlights.last().score)
    }

    @Test fun cancellationIsPropagatedAndStyleIsExplicitlyRuleBased() {
        val token = AiCancellationToken().apply { cancel() }
        val frames = listOf(LumaFrame(0, 2, 2, FloatArray(4)), LumaFrame(1, 2, 2, FloatArray(4) { 1f }))
        try {
            LocalVideoAnalysis.analyze(frames, cancellation = token)
            throw AssertionError("expected cancellation")
        } catch (_: CancellationException) { }
        val (_, style) = LocalStyleAnalysis.analyze(intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt()))
        assertEquals("vivid", style.label)
        assertTrue(style.explanation.contains("no learned style model"))
    }
}
