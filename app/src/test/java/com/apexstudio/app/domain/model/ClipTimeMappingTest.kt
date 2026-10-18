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
}
