package com.apexstudio.app.data.background

import com.apexstudio.app.domain.model.BackgroundReplaceMode
import com.apexstudio.app.domain.model.BackgroundReplaceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for background-replace math + settings (PR-G).
 * Pure Kotlin — no Android dependencies needed.
 */
class BackgroundReplaceMathTest {

    @Test
    fun `smoothstepMask is 0 below low and 1 above high`() {
        assertEquals(0f, BackgroundReplaceMath.smoothstepMask(0f), 0.001f)
        assertEquals(0f, BackgroundReplaceMath.smoothstepMask(0.35f), 0.001f)
        assertEquals(1f, BackgroundReplaceMath.smoothstepMask(0.65f), 0.001f)
        assertEquals(1f, BackgroundReplaceMath.smoothstepMask(1f), 0.001f)
    }

    @Test
    fun `smoothstepMask is 0-5 at the midpoint (smooth, not a hard cut)`() {
        assertEquals(0.5f, BackgroundReplaceMath.smoothstepMask(0.5f), 0.001f)
    }

    @Test
    fun `smoothstepMask is monotonic`() {
        var prev = -1f
        var c = 0f
        while (c <= 1.001f) {
            val v = BackgroundReplaceMath.smoothstepMask(c)
            assertTrue("not monotonic at $c: $v < $prev", v >= prev)
            prev = v
            c += 0.05f
        }
    }

    @Test
    fun `blurDivisor grows with strength`() {
        assertEquals(4, BackgroundReplaceMath.blurDivisorFor(0f))
        assertEquals(24, BackgroundReplaceMath.blurDivisorFor(1f))
        val mid = BackgroundReplaceMath.blurDivisorFor(0.5f)
        assertTrue(mid in 5..23)
        assertTrue(BackgroundReplaceMath.blurDivisorFor(0.25f) < mid)
        assertTrue(mid < BackgroundReplaceMath.blurDivisorFor(0.75f))
    }

    @Test
    fun `blurDivisor clamps out-of-range strength`() {
        assertEquals(4, BackgroundReplaceMath.blurDivisorFor(-5f))
        assertEquals(24, BackgroundReplaceMath.blurDivisorFor(5f))
    }

    @Test
    fun `blurredSize never drops below 1`() {
        assertEquals(1, BackgroundReplaceMath.blurredSize(1, 24))
        assertEquals(1, BackgroundReplaceMath.blurredSize(0, 4))
        assertEquals(270, BackgroundReplaceMath.blurredSize(1080, 4))
    }

    @Test
    fun `coverCropRect crops sides for wide source`() {
        // 1920×1080 source into a 1080×1080 (square) frame: crop sides.
        val r = BackgroundReplaceMath.coverCropRect(1920, 1080, 1080, 1080)
        assertEquals(420f, r[0], 0.001f)
        assertEquals(0f, r[1], 0.001f)
        assertEquals(1500f, r[2], 0.001f)
        assertEquals(1080f, r[3], 0.001f)
    }

    @Test
    fun `coverCropRect crops top-bottom for tall source`() {
        // 1080×1920 source into a 1920×1080 frame: crop top/bottom.
        val r = BackgroundReplaceMath.coverCropRect(1080, 1920, 1920, 1080)
        assertEquals(0f, r[0], 0.001f)
        assertEquals(656.25f, r[1], 0.001f)
        assertEquals(1080f, r[2], 0.001f)
        assertEquals(1263.75f, r[3], 0.001f)
    }

    @Test
    fun `coverCropRect is identity for matching aspect`() {
        val r = BackgroundReplaceMath.coverCropRect(1920, 1080, 1280, 720)
        assertEquals(0f, r[0], 0.001f)
        assertEquals(0f, r[1], 0.001f)
        assertEquals(1920f, r[2], 0.001f)
        assertEquals(1080f, r[3], 0.001f)
    }

    @Test
    fun `coverCropRect throws for non-positive dimensions`() {
        try {
            BackgroundReplaceMath.coverCropRect(0, 1080, 1080, 1080)
            fail("expected IllegalArgumentException for zero width")
        } catch (_: IllegalArgumentException) {
        }
        try {
            BackgroundReplaceMath.coverCropRect(1920, 1080, 0, 720)
            fail("expected IllegalArgumentException for zero destination width")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `settings sanitized clamps blur and forces opaque colour`() {
        val s = BackgroundReplaceSettings(
            mode = BackgroundReplaceMode.COLOR,
            blurStrength = 3f,
            colorArgb = 0x00123456L
        ).sanitized()
        assertEquals(1f, s.blurStrength, 0.001f)
        assertEquals(0xFF123456L, s.colorArgb)
    }

    @Test
    fun `cacheKey is stable for identical settings and changes with any knob`() {
        val base = BackgroundReplaceSettings(
            mode = BackgroundReplaceMode.BLUR,
            blurStrength = 0.5f,
            sourceUri = "file:///a.mp4"
        )
        assertEquals(base.cacheKey(), base.copy().cacheKey())
        assertNotEquals(base.cacheKey(), base.copy(blurStrength = 0.6f).cacheKey())
        assertNotEquals(base.cacheKey(), base.copy(mode = BackgroundReplaceMode.COLOR).cacheKey())
        assertNotEquals(base.cacheKey(), base.copy(sourceUri = "file:///b.mp4").cacheKey())
        assertNotEquals(
            base.cacheKey(),
            base.copy(mode = BackgroundReplaceMode.IMAGE, imageUri = "file:///bg.jpg").cacheKey()
        )
    }
}
