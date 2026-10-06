package com.apexstudio.app.data.photoedit

import com.apexstudio.app.data.photoedit.PhotoEditRenderer.CropCorner
import com.apexstudio.app.domain.model.PhotoCropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure crop-rect math in [PhotoEditRenderer].
 * No Android dependencies — plain JUnit.
 */
class PhotoCropMathTest {

    @Test
    fun `clampRect keeps rect inside unit square`() {
        val clamped = PhotoEditRenderer.clampRect(PhotoCropRect(-0.5f, -0.2f, 1.4f, 2f))
        assertEquals(0f, clamped.left, 0.001f)
        assertEquals(0f, clamped.top, 0.001f)
        assertEquals(1f, clamped.right, 0.001f)
        assertEquals(1f, clamped.bottom, 0.001f)
    }

    @Test
    fun `clampRect enforces minimum size`() {
        val clamped = PhotoEditRenderer.clampRect(PhotoCropRect(0.5f, 0.5f, 0.51f, 0.51f))
        assertTrue(clamped.width() >= 0.049f)
        assertTrue(clamped.height() >= 0.049f)
    }

    @Test
    fun `moveCorner free drag moves one corner`() {
        val rect = PhotoCropRect.full()
        val moved = PhotoEditRenderer.moveCorner(rect, CropCorner.BOTTOM_RIGHT, 0.7f, 0.6f, null)
        assertEquals(0f, moved.left, 0.001f)
        assertEquals(0f, moved.top, 0.001f)
        assertEquals(0.7f, moved.right, 0.001f)
        assertEquals(0.6f, moved.bottom, 0.001f)
    }

    @Test
    fun `moveCorner free drag never inverts`() {
        val rect = PhotoCropRect.full()
        // Drag bottom-right past the top-left: rect normalises instead.
        val moved = PhotoEditRenderer.moveCorner(rect, CropCorner.BOTTOM_RIGHT, -0.2f, -0.2f, null)
        assertTrue(moved.left < moved.right)
        assertTrue(moved.top < moved.bottom)
    }

    @Test
    fun `moveCorner with locked aspect keeps exact ratio`() {
        val rect = PhotoCropRect.full()
        val moved = PhotoEditRenderer.moveCorner(rect, CropCorner.BOTTOM_RIGHT, 0.7f, 0.9f, 1f)
        assertEquals(1f, moved.aspect(), 0.01f)
        // Anchored at top-left (0,0).
        assertEquals(0f, moved.left, 0.01f)
        assertEquals(0f, moved.top, 0.01f)
    }

    @Test
    fun `moveCorner with 16-9 lock keeps ratio from any corner`() {
        val rect = PhotoCropRect.full()
        val aspect = 16f / 9f
        val moved = PhotoEditRenderer.moveCorner(rect, CropCorner.TOP_LEFT, 0.2f, 0.3f, aspect)
        assertEquals(aspect, moved.aspect(), 0.02f)
        // Opposite corner (bottom-right) stays anchored at (1,1).
        assertEquals(1f, moved.right, 0.01f)
        assertEquals(1f, moved.bottom, 0.01f)
    }

    @Test
    fun `moveCorner locked aspect stays inside unit square`() {
        val rect = PhotoCropRect(0.2f, 0.2f, 0.8f, 0.8f)
        val moved = PhotoEditRenderer.moveCorner(rect, CropCorner.TOP_RIGHT, 1.5f, -0.5f, 1f)
        assertTrue(moved.left >= 0f)
        assertTrue(moved.top >= 0f)
        assertTrue(moved.right <= 1f)
        assertTrue(moved.bottom <= 1f)
        assertEquals(1f, moved.aspect(), 0.05f)
    }
}
