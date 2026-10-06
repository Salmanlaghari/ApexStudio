package com.apexstudio.app.data.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Snapchat-style animated colour filter presets.
 *
 * The four animated presets (Hue Cycle, Pulse Beat, Gradient Sweep,
 * Light Leak Sweep) are time-driven via the `uTime`/`uSpeed` uniforms
 * in [FxGlEffect]. These tests lock in their identity, flags, and
 * category wiring so a refactor can't silently drop them from the
 * "Animated" gallery or break the speed-slider plumbing.
 */
class AnimatedFilterTest {

    private val animatedIds = listOf(
        "hue_cycle",
        "pulse_beat",
        "gradient_sweep",
        "light_leak_sweep"
    )

    @Test
    fun `byId resolves all four animated presets`() {
        for (id in animatedIds) {
            assertNotNull("byId($id) should resolve", FxPreset.byId(id))
        }
    }

    @Test
    fun `animated presets are flagged isAnimated with Animated category`() {
        for (id in animatedIds) {
            val preset = FxPreset.byId(id)!!
            assertTrue("$id should be isAnimated", preset.isAnimated)
            assertEquals("$id category", "Animated", preset.category)
        }
    }

    @Test
    fun `Animated category is listed`() {
        assertTrue(
            "\"Animated\" should be a filter category",
            FxPreset.categories().contains("Animated")
        )
    }

    @Test
    fun `exactly four animated presets exist`() {
        val animated = FxPreset.values().filter { it.isAnimated }
        assertEquals(
            "expected 4 animated presets, found: ${animated.map { it.id }}",
            4,
            animated.size
        )
    }

    @Test
    fun `non-animated presets are not flagged`() {
        // Spot-check a few long-standing presets.
        for (id in listOf("vignette", "vhs", "glitch", "bloom")) {
            val preset = FxPreset.byId(id)!!
            assertFalse("$id should not be isAnimated", preset.isAnimated)
        }
    }

    @Test
    fun `byId returns null for unknown id`() {
        assertNull(FxPreset.byId("no_such_filter"))
        assertNull(FxPreset.byId(null))
    }

    @Test
    fun `animated preset ids are unique`() {
        val ids = FxPreset.values().map { it.id }
        assertEquals("duplicate preset ids found", ids.size, ids.toSet().size)
    }
}
