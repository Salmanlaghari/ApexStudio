package com.apexstudio.app.ui.screens.colortools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTimelineComparisonRendererTest {
    @Test fun limitsComparisonTo1080pGeometry() {
        assertTrue(ColorTimelineComparisonRenderer.isSupportedComparisonOutput(1920, 1080))
        assertTrue(ColorTimelineComparisonRenderer.isSupportedComparisonOutput(1080, 1920))
        assertTrue(ColorTimelineComparisonRenderer.isSupportedComparisonOutput(1280, 720))
        assertFalse(ColorTimelineComparisonRenderer.isSupportedComparisonOutput(3840, 2160))
        assertFalse(ColorTimelineComparisonRenderer.isSupportedComparisonOutput(1920, 1200))
        assertFalse(ColorTimelineComparisonRenderer.isSupportedComparisonOutput(0, 720))
    }

    @Test fun boundsComparisonTimelineDurationAndPotentialFrameSamples() {
        assertTrue(ColorTimelineComparisonRenderer.isSupportedComparisonTimeline(30 * 60 * 1_000L, 60))
        assertTrue(ColorTimelineComparisonRenderer.isSupportedComparisonTimeline(30 * 60 * 1_000L, 24))
        assertFalse(ColorTimelineComparisonRenderer.isSupportedComparisonTimeline(30 * 60 * 1_000L + 1L, 30))
        assertFalse(ColorTimelineComparisonRenderer.isSupportedComparisonTimeline(30 * 60 * 1_000L, 61))
        assertFalse(ColorTimelineComparisonRenderer.isSupportedComparisonTimeline(0L, 30))
    }

    @Test fun pairPolicyRequiresMatchingVerifiedEncodedProperties() {
        val baseline = ColorTimelineComparisonRenderer.EncodedVideoMetadata(1920, 1080, 29.97, 900, 30_030)
        assertTrue(ColorTimelineComparisonRenderer.compatibleEncodedPair(
            baseline, baseline.copy(durationMs = 30_040)))
        assertFalse(ColorTimelineComparisonRenderer.compatibleEncodedPair(baseline, baseline.copy(frameCount = 899)))
        assertFalse(ColorTimelineComparisonRenderer.compatibleEncodedPair(baseline, baseline.copy(width = 1280)))
        assertFalse(ColorTimelineComparisonRenderer.compatibleEncodedPair(baseline, baseline.copy(frameRate = 25.0)))
        assertFalse(ColorTimelineComparisonRenderer.compatibleEncodedPair(baseline, baseline.copy(durationMs = 31_000)))
        assertFalse(ColorTimelineComparisonRenderer.compatibleEncodedPair(baseline, baseline.copy(frameCount = 0)))
    }

    @Test fun parsesOnlyFinitePositiveFrameRates() {
        assertEquals(29.97, ColorTimelineComparisonRenderer.parseFrameRate("30000/1001")!!, 0.001)
        assertEquals(30.0, ColorTimelineComparisonRenderer.parseFrameRate("30")!!, 0.0)
        assertNull(ColorTimelineComparisonRenderer.parseFrameRate("0/0"))
        assertNull(ColorTimelineComparisonRenderer.parseFrameRate("-30/1"))
        assertNull(ColorTimelineComparisonRenderer.parseFrameRate("30/0"))
        assertNull(ColorTimelineComparisonRenderer.parseFrameRate("unknown"))
    }

    @Test fun timestampPolicyRejectsDifferentCadenceEvenWhenNominalMetadataCouldMatch() {
        val ordered = listOf(0L, 33_367L, 66_733L, 100_100L)
        assertTrue(ColorTimelineComparisonRenderer.compatiblePresentationTimestamps(ordered, ordered.toList()))
        assertFalse(ColorTimelineComparisonRenderer.compatiblePresentationTimestamps(
            ordered, listOf(0L, 33_000L, 67_000L, 100_100L)))
        // Duplicate or invalid PTS cannot be used as an exact output clock.
        assertFalse(ColorTimelineComparisonRenderer.compatiblePresentationTimestamps(
            listOf(0L, 33_000L, 33_000L), listOf(0L, 33_000L, 33_000L)))
        assertFalse(ColorTimelineComparisonRenderer.compatiblePresentationTimestamps(
            listOf(0L, -1L), listOf(0L, -1L)))
    }

    @Test fun requestedPreviewTimeMapsToNearestActualPresentationTimestamp() {
        val irregular = listOf(0L, 40_000L, 83_000L, 125_000L)
        assertEquals(0L, ColorTimelineComparisonRenderer.nearestPresentationFrameIndex(irregular, 0L))
        assertEquals(1L, ColorTimelineComparisonRenderer.nearestPresentationFrameIndex(irregular, 50_000L))
        assertEquals(2L, ColorTimelineComparisonRenderer.nearestPresentationFrameIndex(irregular, 70_000L))
        assertEquals(3L, ColorTimelineComparisonRenderer.nearestPresentationFrameIndex(irregular, 1_000_000L))
    }
}
