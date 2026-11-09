package com.apexstudio.app.data.audio.sfx

import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SfxSynthTest {
    @Test
    fun `same effect parameters render the same sample hash`() {
        val first = SfxSynth.render("whoosh", 650L, 0.73f)
        val second = SfxSynth.render("whoosh", 650L, 0.73f)

        assertEquals(first.size, second.size)
        assertEquals(first.contentHashCode(), second.contentHashCode())
        assertNotEquals(first.contentHashCode(), SfxSynth.render("whoosh", 650L, 0.6f).contentHashCode())
    }

    @Test
    fun `all fourteen registry effects are unique valid and audible within their default duration`() {
        assertEquals(14, SfxLibrary.effects.size)
        assertEquals(SfxLibrary.effects.size, SfxLibrary.effects.map { it.id }.toSet().size)
        assertTrue(SfxLibrary.effects.all { it.id.isNotBlank() && it.category in SfxCategory.entries })

        for (effect in SfxLibrary.effects) {
            val samples = SfxSynth.render(effect.id, effect.defaultDurationMs)
            val expectedFrames = (effect.defaultDurationMs.toDouble() * SfxSynth.SAMPLE_RATE / 1_000.0).roundToInt()
            assertEquals("${effect.id} sample count", expectedFrames, samples.size)
            assertTrue("${effect.id} should not be silent", samples.any { it.toInt() != 0 })
            val durationMs = samples.size.toDouble() * 1_000.0 / SfxSynth.SAMPLE_RATE
            assertTrue("${effect.id} duration $durationMs", durationMs <= effect.defaultDurationMs + 1.0)
            val peak = samples.maxOf { abs(it.toInt()) / Short.MAX_VALUE.toFloat() }
            assertTrue("${effect.id} peak $peak exceeded -1 dBFS", peak <= SfxSynth.PEAK_CEILING + 1f / Short.MAX_VALUE)
        }
    }
}
