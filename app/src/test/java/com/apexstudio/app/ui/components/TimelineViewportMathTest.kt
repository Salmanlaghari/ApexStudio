package com.apexstudio.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineViewportMathTest {
    @Test
    fun `two-finger translation pans without changing zoom`() {
        val scroll = timelineScrollForCentroid(
            currentScrollPx = 1_000,
            previousCentroidX = 200f,
            centroidX = 250f,
            oldZoom = 1f,
            newZoom = 1f,
            density = 1f
        )

        assertEquals(950, scroll)
    }

    @Test
    fun `pinch keeps the same timeline point beneath the moving centroid`() {
        val scroll = timelineScrollForCentroid(
            currentScrollPx = 200,
            previousCentroidX = 150f,
            centroidX = 180f,
            oldZoom = 1f,
            newZoom = 2f,
            density = 1f
        )

        assertEquals(520, scroll)
    }

    @Test
    fun `invalid scale does not produce a negative scroll`() {
        assertEquals(0, timelineScrollForCentroid(0, 50f, 90f, 0f, 1f, 1f))
    }
}
