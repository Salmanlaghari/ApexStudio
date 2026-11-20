package com.apexstudio.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `successive pinch samples keep the same timeline time under the centroid`() {
        val firstScroll = timelineScrollForCentroid(
            currentScrollPx = 500,
            previousCentroidX = 200f,
            centroidX = 210f,
            oldZoom = 1f,
            newZoom = 1.2f,
            density = 1f
        )
        val secondScroll = timelineScrollForCentroid(
            currentScrollPx = firstScroll,
            previousCentroidX = 210f,
            centroidX = 230f,
            oldZoom = 1.2f,
            newZoom = 1.5f,
            density = 1f
        )

        assertEquals(820, secondScroll)
        assertEquals(
            (500f + 200f) / 54f,
            (secondScroll + 230f) / (54f * 1.5f),
            0.02f
        )
    }

    @Test
    fun `minimum zoom expands ruler label interval enough to avoid overlap`() {
        val secondWidthDp = 54f * 0.01f
        val labelWidthDp = 27f
        val stepSeconds = timelineRulerLabelStepSeconds(secondWidthDp, labelWidthDp)

        assertEquals(60, stepSeconds)
        assertTrue(stepSeconds * secondWidthDp >= labelWidthDp + 2f)
    }

    @Test
    fun `ruler keeps compact cadence at normal and frame-level zoom`() {
        assertEquals(2, timelineRulerLabelStepSeconds(secondWidthDp = 54f, labelWidthDp = 27f))
        assertEquals(1, timelineRulerLabelStepSeconds(secondWidthDp = 180f, labelWidthDp = 27f))
    }

    @Test
    fun `invalid scale does not produce a negative scroll`() {
        assertEquals(0, timelineScrollForCentroid(0, 50f, 90f, 0f, 1f, 1f))
    }
}
