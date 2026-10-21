package com.apexstudio.app.data.animation

import com.apexstudio.app.domain.model.SpeedCurveMath
import com.apexstudio.app.domain.model.SpeedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KeyframeAnimationClockTest {
    @Test
    fun `trimmed source timestamp maps through constant speed into placed project time`() {
        val clock = KeyframeAnimationClock(
            trimStartMs = 1_200L,
            trimEndMs = 5_200L,
            timelineStartMs = 8_000L,
            clipSpeed = 2f,
            speedPoints = emptyList()
        )

        // Media3 gives the clipped item a zero-based source PTS. At 1s into
        // the retained source, the 2x clip has advanced only 500ms in project time.
        assertEquals(8_500L, clock.projectTimeMs(1_000_000L))
    }

    @Test
    fun `variable speed curve is converted against the retained source span`() {
        val points = listOf(SpeedPoint(0f, 0.5f), SpeedPoint(0.5f, 2f), SpeedPoint(1f, 1f))
        val clock = KeyframeAnimationClock(
            trimStartMs = 2_000L,
            trimEndMs = 8_000L,
            timelineStartMs = 12_500L,
            clipSpeed = 3f, // A valid curve takes precedence over this constant fallback.
            speedPoints = points
        )
        val expectedOffset = SpeedCurveMath.sourceToOutputMs(2_000L, 6_000L, points)

        assertEquals(12_500L + expectedOffset, clock.projectTimeMs(2_000_000L))
        assertNotEquals(14_500L, clock.projectTimeMs(2_000_000L))
    }

    @Test
    fun `PTS beyond clipped source end clamps to clip project end`() {
        val clock = KeyframeAnimationClock(
            trimStartMs = 500L,
            trimEndMs = 4_500L,
            timelineStartMs = 3_000L,
            clipSpeed = 2f,
            speedPoints = emptyList()
        )

        assertEquals(5_000L, clock.projectTimeMs(9_000_000L))
    }
}
