package com.apexstudio.app.data.text

import com.apexstudio.app.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TextAnimEngine] — pure Kotlin math shared by the
 * live preview and the export bake, so no Android dependencies here.
 */
class TextAnimEngineTest {

    private fun overlay(
        animationType: String = "NONE",
        animationDurationMs: Long = 800L,
        startMs: Long = 0L,
        endMs: Long = 5000L,
        outroAnimationType: String = "NONE",
        outroAnimationDurationMs: Long = 600L,
        text: String = "Hello"
    ) = TextOverlay(
        id = "t1",
        text = text,
        animationType = animationType,
        animationDurationMs = animationDurationMs,
        startMs = startMs,
        endMs = endMs,
        outroAnimationType = outroAnimationType,
        outroAnimationDurationMs = outroAnimationDurationMs
    )

    @Test
    fun `NONE intro and outro produce identity`() {
        val s = TextAnimEngine.compute(overlay(), 2500L)
        assertEquals(1f, s.alpha, 0.001f)
        assertEquals(1f, s.scaleX, 0.001f)
        assertEquals(0f, s.transYFrac, 0.001f)
        assertEquals(null, s.charsToShow)
    }

    @Test
    fun `FADE_IN ramps alpha from 0 to 1`() {
        val o = overlay(animationType = "FADE_IN")
        assertEquals(0f, TextAnimEngine.compute(o, 0L).alpha, 0.01f)
        val mid = TextAnimEngine.compute(o, 400L).alpha
        assertTrue(mid > 0.3f && mid < 1f)
        assertEquals(1f, TextAnimEngine.compute(o, 800L).alpha, 0.001f)
        assertEquals(1f, TextAnimEngine.compute(o, 5000L).alpha, 0.001f)
    }

    @Test
    fun `POP_SPRING starts small and settles at 1`() {
        val o = overlay(animationType = "POP_SPRING")
        val start = TextAnimEngine.compute(o, 0L)
        assertEquals(0.3f, start.scaleX, 0.01f)
        val end = TextAnimEngine.compute(o, 800L)
        assertEquals(1f, end.scaleX, 0.01f)
        assertEquals(1f, end.scaleY, 0.01f)
        // Spring overshoots past 1 mid-flight (buttery CapCut feel) —
        // the damped spring peaks around t≈0.29.
        val mid = TextAnimEngine.compute(o, 230L)
        assertTrue(mid.scaleX > 1f)
    }

    @Test
    fun `TYPEWRITER reveals characters progressively`() {
        val o = overlay(animationType = "TYPEWRITER", text = "Hello")
        assertEquals(0, TextAnimEngine.compute(o, 0L).charsToShow)
        val mid = TextAnimEngine.compute(o, 400L).charsToShow!!
        assertTrue(mid in 1..4)
        assertEquals(5, TextAnimEngine.compute(o, 800L).charsToShow)
    }

    @Test
    fun `SLIDE_UP starts offset and lands at rest`() {
        val o = overlay(animationType = "SLIDE_UP")
        assertTrue(TextAnimEngine.compute(o, 0L).transYFrac > 0f)
        assertEquals(0f, TextAnimEngine.compute(o, 800L).transYFrac, 0.001f)
    }

    @Test
    fun `BLUR_IN starts blurred and sharp`() {
        val o = overlay(animationType = "BLUR_IN")
        assertTrue(TextAnimEngine.compute(o, 0L).blurFrac > 0f)
        assertEquals(0f, TextAnimEngine.compute(o, 800L).blurFrac, 0.0001f)
    }

    @Test
    fun `SLIDE_BOUNCE overshoots past rest`() {
        val o = overlay(animationType = "SLIDE_BOUNCE")
        // easeOutBack overshoot → transY goes slightly negative mid-flight.
        val mid = TextAnimEngine.compute(o, 500L).transYFrac
        assertTrue(mid < 0f)
        assertEquals(0f, TextAnimEngine.compute(o, 800L).transYFrac, 0.02f)
    }

    @Test
    fun `FADE_OUT only affects the end window`() {
        val o = overlay(outroAnimationType = "FADE_OUT", endMs = 5000L, outroAnimationDurationMs = 600L)
        assertEquals(1f, TextAnimEngine.compute(o, 4000L).alpha, 0.001f)
        val mid = TextAnimEngine.compute(o, 4700L).alpha
        assertTrue(mid > 0f && mid < 1f)
        assertEquals(0f, TextAnimEngine.compute(o, 5000L).alpha, 0.01f)
    }

    @Test
    fun `SLIDE_DOWN sinks while fading`() {
        val o = overlay(outroAnimationType = "SLIDE_DOWN", endMs = 5000L, outroAnimationDurationMs = 600L)
        val s = TextAnimEngine.compute(o, 5000L)
        assertTrue(s.transYFrac > 0f)
        assertEquals(0f, s.alpha, 0.01f)
    }

    @Test
    fun `SHRINK collapses scale at the end`() {
        val o = overlay(outroAnimationType = "SHRINK", endMs = 5000L, outroAnimationDurationMs = 600L)
        val s = TextAnimEngine.compute(o, 5000L)
        assertTrue(s.scaleX < 0.2f)
        assertEquals(0f, s.alpha, 0.01f)
    }

    @Test
    fun `outro ignored when endMs is unbounded`() {
        val o = overlay(outroAnimationType = "FADE_OUT", endMs = Long.MAX_VALUE)
        assertEquals(1f, TextAnimEngine.compute(o, 99999L).alpha, 0.001f)
    }

    @Test
    fun `isAnimated distinguishes static from animated overlays`() {
        assertFalse(TextAnimEngine.isAnimated(overlay()))
        assertTrue(TextAnimEngine.isAnimated(overlay(animationType = "FADE_IN")))
        assertTrue(TextAnimEngine.isAnimated(overlay(outroAnimationType = "SHRINK")))
        assertTrue(TextAnimEngine.isAnimated(overlay(animationType = "PULSE")))
    }

    @Test
    fun `legacy animation ids still resolve`() {
        // Older projects stored FADE / POP.
        val fade = overlay(animationType = "FADE")
        assertEquals(0f, TextAnimEngine.compute(fade, 0L).alpha, 0.01f)
        val pop = overlay(animationType = "POP")
        assertEquals(0.3f, TextAnimEngine.compute(pop, 0L).scaleX, 0.01f)
    }

    @Test
    fun `stateKey is stable for static overlays and moves for animated`() {
        val static = overlay()
        assertEquals(
            TextAnimEngine.stateKey(static, 1000L),
            TextAnimEngine.stateKey(static, 2000L)
        )
        val animated = overlay(animationType = "FADE_IN")
        assert(
            TextAnimEngine.stateKey(animated, 100L) !=
                    TextAnimEngine.stateKey(animated, 400L)
        )
    }

    @Test
    fun `TextOverlay carries new pro fields with safe defaults`() {
        val o = TextOverlay(id = "x", text = "Hi")
        assertEquals("CENTER", o.textAlign)
        assertEquals(0f, o.letterSpacingEm, 0.0001f)
        assertEquals(null, o.gradientStartArgb)
        assertEquals("NONE", o.outroAnimationType)
        assertEquals(600L, o.outroAnimationDurationMs)
    }
}
