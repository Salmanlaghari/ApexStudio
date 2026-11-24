package com.apexstudio.app.ui.screens.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipOverlayCanvasBoundsTest {
    @Test
    fun landscapeOverlayCenterIsInsetByHalfBox() {
        val bounds = overlayCenterBounds(1000f, 600f, 400f, 200f, 0f)
        assertEquals(0.2f, bounds.first.first, 0.001f)
        assertEquals(0.8f, bounds.first.second, 0.001f)
        assertEquals(1f / 6f, bounds.second.first, 0.001f)
        assertEquals(5f / 6f, bounds.second.second, 0.001f)
    }

    @Test
    fun rotatedOverlayUsesRotatedBoundingBox() {
        val bounds = overlayCenterBounds(1000f, 1000f, 400f, 200f, 45f)
        assertTrue(bounds.first.first > 0.20f)
        assertTrue(bounds.second.first > 0.20f)
        val clamped = clampOverlayCenter(0f, 1f, 1000f, 1000f, 400f, 200f, 45f)
        assertEquals(bounds.first.first, clamped.first, 0.001f)
        assertEquals(bounds.second.second, clamped.second, 0.001f)
    }

    @Test
    fun oversizedPortraitOverlayCollapsesCenterToFrameMiddle() {
        val bounds = overlayCenterBounds(400f, 800f, 360f, 900f, 0f)
        assertEquals(0.5f, bounds.first.first, 0.001f)
        assertEquals(0.5f, bounds.first.second, 0.001f)
        assertEquals(0.5f, bounds.second.first, 0.001f)
        assertEquals(0.5f, bounds.second.second, 0.001f)
    }
}
