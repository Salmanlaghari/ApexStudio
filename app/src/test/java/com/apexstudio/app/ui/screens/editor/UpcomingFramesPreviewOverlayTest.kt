package com.apexstudio.app.ui.screens.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingFramesPreviewOverlayTest {
    @Test
    fun targetsAreOrderedAtQuarterSecondLookaheadIntervals() {
        val targets = upcomingFrameTargets(currentTimeMs = 1_000L, durationMs = 10_000L)

        assertEquals(listOf(750L, 1_500L, 2_250L, 3_000L), targets.map { it.first })
        assertEquals(listOf(1_750L, 2_500L, 3_250L, 4_000L), targets.map { it.second })
        assertEquals(listOf("+0.8s", "+1.5s", "+2.3s", "+3.0s"), targets.map { it.third })
    }

    @Test
    fun targetsClampToProjectDurationInsteadOfSeekingPastTheEnd() {
        val targets = upcomingFrameTargets(currentTimeMs = 4_800L, durationMs = 5_000L)

        assertTrue(targets.all { it.second in 0L..5_000L })
        assertEquals(5_000L, targets.last().second)
    }

    @Test
    fun veryShortProjectsStillExposeSafeTargets() {
        val targets = upcomingFrameTargets(currentTimeMs = 0L, durationMs = 0L)

        assertEquals(4, targets.size)
        assertEquals(1_000L, targets.last().second)
    }
}
