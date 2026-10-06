package com.apexstudio.app.ui.screens.editor

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the sticker rect-crop editor's corner math.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StickerCropEditorTest {

    @Test
    fun `dragging top-left corner shrinks the rect`() {
        val out = moveCropCorner(floatArrayOf(0f, 0f, 1f, 1f), 0, 0.2f, 0.2f)
        assertEquals(0.2f, out[0], 0.001f)
        assertEquals(0.2f, out[1], 0.001f)
        assertEquals(1f, out[2], 0.001f)
        assertEquals(1f, out[3], 0.001f)
    }

    @Test
    fun `corners cannot cross the opposite edge (min size kept)`() {
        val out = moveCropCorner(floatArrayOf(0f, 0f, 1f, 1f), 0, 0.99f, 0.99f)
        assertEquals(0.95f, out[0], 0.001f)
        assertEquals(0.95f, out[1], 0.001f)
    }

    @Test
    fun `dragging bottom-right corner is clamped to the box`() {
        val out = moveCropCorner(floatArrayOf(0.1f, 0.1f, 0.9f, 0.9f), 3, 1.5f, 1.5f)
        assertEquals(1f, out[2], 0.001f)
        assertEquals(1f, out[3], 0.001f)
        assertEquals(0.1f, out[0], 0.001f)
        assertEquals(0.1f, out[1], 0.001f)
    }

    @Test
    fun `top-right corner only moves right and top edges`() {
        val out = moveCropCorner(floatArrayOf(0.2f, 0.2f, 0.8f, 0.8f), 1, 0.9f, 0.1f)
        assertEquals(0.2f, out[0], 0.001f)
        assertEquals(0.1f, out[1], 0.001f)
        assertEquals(0.9f, out[2], 0.001f)
        assertEquals(0.8f, out[3], 0.001f)
    }
}
