package com.apexstudio.app.ai.onnx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModNetBackgroundRemovalAdapterTest {
    @Test
    fun `input geometry preserves aspect and aligns axes within bound`() {
        val (width, height) = ModNetBackgroundRemovalAdapter.inputDimensions(1920, 1080)
        assertEquals(896, width)
        assertEquals(512, height)
        assertEquals(0, width % ModNetBackgroundRemovalAdapter.DIMENSION_ALIGNMENT)
        assertEquals(0, height % ModNetBackgroundRemovalAdapter.DIMENSION_ALIGNMENT)
        assertTrue(width.toLong() * height <= ModNetBackgroundRemovalAdapter.MAX_MODEL_PIXELS)
    }

    @Test
    fun `input geometry bounds large panoramas`() {
        val (width, height) = ModNetBackgroundRemovalAdapter.inputDimensions(8192, 1024)
        assertTrue(width <= ModNetBackgroundRemovalAdapter.MAX_MODEL_SIDE)
        assertTrue(height <= ModNetBackgroundRemovalAdapter.MAX_MODEL_SIDE)
        assertTrue(width.toLong() * height <= ModNetBackgroundRemovalAdapter.MAX_MODEL_PIXELS)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `input rejects too many source pixels`() {
        ModNetBackgroundRemovalAdapter.inputDimensions(5000, 2000)
    }

    @Test
    fun `matte probabilities are clamped and rounded to byte alpha`() {
        assertEquals(0, ModNetBackgroundRemovalAdapter.alphaByte(-0.5f))
        assertEquals(0, ModNetBackgroundRemovalAdapter.alphaByte(0f))
        assertEquals(128, ModNetBackgroundRemovalAdapter.alphaByte(0.5f))
        assertEquals(255, ModNetBackgroundRemovalAdapter.alphaByte(1f))
        assertEquals(255, ModNetBackgroundRemovalAdapter.alphaByte(1.5f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `matte conversion rejects non-finite model values`() {
        ModNetBackgroundRemovalAdapter.alphaByte(Float.NaN)
    }
}
