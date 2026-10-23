package com.apexstudio.app.data.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LutFilterGlEffectCompareTest {
    @Test
    fun `before and after modes select source and graded pixels respectively`() {
        assertTrue(isOriginalLutComparisonPixel(LutComparisonMode.BEFORE, 0.25f, 0.5f))
        assertFalse(isOriginalLutComparisonPixel(LutComparisonMode.AFTER, 0.25f, 0.5f))
    }

    @Test
    fun `split leaves left side original and grades right side including split boundary`() {
        assertTrue(isOriginalLutComparisonPixel(LutComparisonMode.SPLIT, 0.49f, 0.5f))
        assertFalse(isOriginalLutComparisonPixel(LutComparisonMode.SPLIT, 0.5f, 0.5f))
        assertFalse(isOriginalLutComparisonPixel(LutComparisonMode.SPLIT, 0.75f, 0.5f))
    }

    @Test
    fun `split bounds are clamped and default after mode has legacy export uniform`() {
        assertFalse(isOriginalLutComparisonPixel(LutComparisonMode.SPLIT, 0f, -1f))
        assertFalse(isOriginalLutComparisonPixel(LutComparisonMode.SPLIT, 1f, 2f))
        assertEquals(0f, LutComparisonMode.AFTER.shaderValue, 0f)
    }
}
