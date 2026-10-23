package com.apexstudio.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class ClipTimeMappingTest {
    private fun clip(id: String, duration: Long = 10_000L) = MediaClip(
        id = id, name = id, uri = "file:///$id.mp4", durationMs = duration,
        trimEndMs = duration, type = ClipType.VIDEO
    )

    @Test fun captionOutputTimeIsClipRelativeWithoutApplyingSpeedTwiceOrClampingFutureClips() {
        val first = clip("first")
        val second = clip("second").copy(speedPoints = SpeedCurveMath.flat(2f))
        val clips = listOf(first, second)
        assertEquals(300L, ClipTimeMapping.outputAtProjectTime(clips, second, 10_300L))
        assertEquals(-1L, ClipTimeMapping.outputAtProjectTime(clips, second, 9_999L))
        val overlay = clip("pip").copy(type = ClipType.OVERLAY, trackIndex = 2, timelineOffsetMs = 20_000L)
        assertEquals(300L, ClipTimeMapping.outputAtProjectTime(listOf(overlay), overlay, 20_300L))
        assertEquals(-20_000L, ClipTimeMapping.outputAtProjectTime(listOf(overlay), overlay, 0L))
    }

    @Test fun secondClipUsesItsOwnSourceNotAbsoluteProjectPlayhead() {
        val first = clip("first")
        val second = clip("second", 20_000L).copy(trimStartMs = 4_000L)
        val clips = listOf(first, second)
        assertEquals(7_000L, ClipTimeMapping.sourceAtProjectTime(clips, second, 13_000L))
        assertEquals(13_000L, ClipTimeMapping.projectAtSourceTime(clips, second, 7_000L))
    }

    @Test fun overlayUsesExplicitTimelineOffsetAndSourceHead() {
        val overlay = clip("pip", 20_000L).copy(type = ClipType.OVERLAY, trackIndex = 2,
            timelineOffsetMs = 30_000L, trimStartMs = 5_000L)
        assertEquals(7_000L, ClipTimeMapping.sourceAtProjectTime(listOf(overlay), overlay, 32_000L))
        assertEquals(32_000L, ClipTimeMapping.projectAtSourceTime(listOf(overlay), overlay, 7_000L))
    }

    @Test fun speedCurveAndSourceTrimAreMappedTogether() {
        val source = clip("slow").copy(trimStartMs = 2_000L, speedPoints = SpeedCurveMath.flat(0.5f))
        val sourceAtFourSeconds = ClipTimeMapping.sourceAtProjectTime(listOf(source), source, 4_000L)
        assertTrue(kotlin.math.abs(sourceAtFourSeconds - 4_000L) <= 2L)
        assertTrue(kotlin.math.abs(ClipTimeMapping.projectAtSourceTime(listOf(source), source, sourceAtFourSeconds) - 4_000L) <= 4L)
    }

    @Test fun constantMainClipSpeedMapsTimelineDurationAndPreviewSeekInOutputTime() {
        val fast = clip("fast", 12_000L).copy(
            trimStartMs = 2_000L,
            trimEndMs = 10_000L,
            speedMultiplier = 2f
        )
        assertEquals(4_000L, fast.effectiveDurationMs())
        assertEquals(6_000L, ClipTimeMapping.sourceAtProjectTime(listOf(fast), fast, 2_000L))
        assertEquals(2_000L, ClipTimeMapping.projectAtSourceTime(listOf(fast), fast, 6_000L))
        assertEquals(10_000L, ClipTimeMapping.sourceAtProjectTime(listOf(fast), fast, 99_000L))
        assertEquals(4_000L, ClipTimeMapping.projectAtSourceTime(listOf(fast), fast, 12_000L))
    }

    @Test fun slowMainClipDurationMovesFollowingClipAndUsesMatchingSourceMapping() {
        val slow = clip("slow", 12_000L).copy(
            trimStartMs = 2_000L,
            trimEndMs = 10_000L,
            speedMultiplier = 0.5f
        )
        val next = clip("next")
        assertEquals(16_000L, slow.effectiveDurationMs())
        assertEquals(16_000L, SpeedCurveMath.timelineStartMsForClip(listOf(slow, next), next))
        assertEquals(4_000L, ClipTimeMapping.sourceAtProjectTime(listOf(slow, next), slow, 4_000L))
        assertEquals(4_000L, ClipTimeMapping.projectAtSourceTime(listOf(slow, next), slow, 4_000L))
    }

    @Test fun constantMainSpeedMapsSourceIntervalsToOutputDuration() {
        val fast = clip("fast").copy(trimStartMs = 2_000L, speedMultiplier = 2f)
        val slow = clip("slow").copy(trimStartMs = 2_000L, speedMultiplier = 0.5f)

        assertEquals(2_000L, ClipTimeMapping.outputDurationBetweenSourceTimes(fast, 4_000L, 8_000L))
        assertEquals(8_000L, ClipTimeMapping.outputDurationBetweenSourceTimes(slow, 4_000L, 8_000L))
        assertEquals(1_000L, ClipTimeMapping.outputOffsetAtSourceTime(fast, 4_000L))
        assertEquals(4_000L, ClipTimeMapping.outputOffsetAtSourceTime(slow, 4_000L))
    }

    @Test fun constantSpeedSanitizerMatchesSupportedEditorRange() {
        assertEquals(2f, SpeedCurveMath.sanitizeConstantSpeed(2f), 0f)
        assertEquals(10f, SpeedCurveMath.sanitizeConstantSpeed(20f), 0f)
        assertEquals(0.1f, SpeedCurveMath.sanitizeConstantSpeed(0.01f), 0f)
        assertEquals(1f, SpeedCurveMath.sanitizeConstantSpeed(Float.NaN), 0f)
        assertEquals(1f, SpeedCurveMath.sanitizeConstantSpeed(Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun outsideClipTimeClampsToSourceTrimBounds() {
        val overlay = clip("bounded").copy(type = ClipType.OVERLAY, trackIndex = 1,
            timelineOffsetMs = 20_000L, trimStartMs = 2_000L, trimEndMs = 8_000L)
        assertEquals(2_000L, ClipTimeMapping.sourceAtProjectTime(listOf(overlay), overlay, 0L))
        assertEquals(8_000L, ClipTimeMapping.sourceAtProjectTime(listOf(overlay), overlay, 99_000L))
    }

    @Test fun overlayHeadTrimDurationUsesTheOriginalRampCoordinates() {
        val overlay = clip("ramped", 10_000L).copy(
            type = ClipType.OVERLAY,
            trackIndex = 2,
            speedPoints = listOf(
                SpeedPoint(0f, 0.5f),
                SpeedPoint(0.5f, 0.5f),
                SpeedPoint(1f, 2f),
            ),
        )

        val mapped = ClipTimeMapping.outputDurationBetweenSourceTimes(overlay, 0L, 2_500L)
        assertEquals("first quarter is at 0.5x in the original curve", 5_000L, mapped)
        assertTrue(
            "mapping a shortened interval as a new curve would restart the ramp",
            mapped != SpeedCurveMath.effectiveDurationMs(2_500L, overlay.speedPoints)
        )
    }

    @Test fun speedRampedOverlayUsesOutputDurationForVisibilityAndMapsPreviewSourceTime() {
        val overlay = clip("slow-pip", 12_000L).copy(
            type = ClipType.OVERLAY,
            trackIndex = 2,
            timelineOffsetMs = 5_000L,
            trimStartMs = 2_000L,
            trimEndMs = 10_000L,
            speedPoints = SpeedCurveMath.flat(0.5f)
        )

        assertEquals(16_000L, overlay.overlayTimelineDurationMs())
        assertTrue(overlay.isOverlayActiveAt(20_999L))
        assertFalse(overlay.isOverlayActiveAt(21_000L))
        assertTrue(kotlin.math.abs(2_000L - overlay.overlaySourceOffsetMs(4_000L)) <= 2L)
        assertEquals(0.5f, overlay.overlaySpeedAtOutputMs(4_000L), 0.0001f)
        assertEquals(
            overlay.overlaySourceOffsetMs(4_000L),
            OverlayTimeMapping.sourceOffsetMs(4_000L, 8_000L, overlay.speedPoints)
        )
    }

    @Test fun rampedOverlayOutputToSourceMappingRoundTripsForExportTimestamps() {
        val points = listOf(
            SpeedPoint(0f, 0.5f),
            SpeedPoint(0.5f, 1f),
            SpeedPoint(1f, 2f)
        )
        val sourceSpanMs = 8_000L
        val durationMs = OverlayTimeMapping.timelineDurationMs(sourceSpanMs, points)

        for (outputMs in listOf(0L, durationMs / 4L, durationMs / 2L, durationMs * 3L / 4L, durationMs)) {
            val sourceMs = OverlayTimeMapping.sourceOffsetMs(outputMs, sourceSpanMs, points)
            val mappedBack = SpeedCurveMath.sourceToOutputMs(sourceMs, sourceSpanMs, points)
            assertTrue("output=$outputMs source=$sourceMs mappedBack=$mappedBack", kotlin.math.abs(mappedBack - outputMs) <= 5L)
        }
    }

    @Test fun nonFiniteOverlaySpeedPointsFallBackToSafeNormalPlayback() {
        val points = listOf(SpeedPoint(0f, Float.NaN), SpeedPoint(Float.POSITIVE_INFINITY, 2f))
        assertTrue(SpeedCurveMath.sanitize(points).isEmpty())
        assertEquals(8_000L, OverlayTimeMapping.timelineDurationMs(8_000L, points))
        assertEquals(2_000L, OverlayTimeMapping.sourceOffsetMs(2_000L, 8_000L, points))
        assertEquals(1f, OverlayTimeMapping.speedAtOutputMs(2_000L, 8_000L, points), 0f)
    }

    @Test fun validOverlaySpeedPointsSurviveInvalidNeighbors() {
        val points = listOf(SpeedPoint(0f, 2f), SpeedPoint(0.5f, Float.NEGATIVE_INFINITY),
            SpeedPoint(Float.NaN, 1f), SpeedPoint(1f, 2f))
        assertEquals(SpeedCurveMath.flat(2f), SpeedCurveMath.sanitize(points))
        assertEquals(4_000L, OverlayTimeMapping.timelineDurationMs(8_000L, points))
        assertEquals(2f, OverlayTimeMapping.speedAtOutputMs(2_000L, 8_000L, points), 0.0001f)
    }
}
