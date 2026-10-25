package com.apexstudio.app.data.captions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionStyleTimingTest {
    private val words = listOf(
        CaptionWordTiming("Hello", 0L, 500L, 0, 5),
        CaptionWordTiming("world", 500L, 1_000L, 6, 11),
        CaptionWordTiming("again", 1_000L, 1_500L, 12, 17)
    )

    @Test
    fun exposesExactlyTheFiveRequestedPresetsInOrder() {
        assertEquals(
            listOf("Classic", "Karaoke Fill", "Word Pop", "Typewriter", "Bounce"),
            CaptionStyles.presets.map { it.name }
        )
        assertEquals(5, CaptionStyles.presets.size)
    }

    @Test
    fun classicShowsTheLineAndFadesAtItsStartWithoutHighlightingAWord() {
        val frame = CaptionStyleTiming.evaluate(words, style(CaptionStyles.CLASSIC_ID), 80L)

        assertEquals(listOf(0, 1, 2), frame.visibleWordIndices)
        assertEquals(0, frame.activeWordIndex)
        assertEquals(null, frame.highlightedWordIndex)
        assertTrue(frame.alpha in 0f..1f)
        assertEquals(1f, frame.scale)
    }

    @Test
    fun karaokeHighlightsTheWordWhoseMeasuredIntervalContainsTheTimestamp() {
        val frame = CaptionStyleTiming.evaluate(words, style(CaptionStyles.KARAOKE_FILL_ID), 750L)

        assertEquals(listOf(0, 1, 2), frame.visibleWordIndices)
        assertEquals(1, frame.activeWordIndex)
        assertEquals(1, frame.highlightedWordIndex)
    }

    @Test
    fun wordPopScalesOnlyTheCurrentTimedWord() {
        val frame = CaptionStyleTiming.evaluate(words, style(CaptionStyles.WORD_POP_ID), 1_100L)

        assertEquals(2, frame.activeWordIndex)
        assertEquals(2, frame.highlightedWordIndex)
        assertTrue("active word should be above resting scale", frame.scale > 1f)
        val gapWords = listOf(
            CaptionWordTiming("Hello", 0L, 400L, 0, 5),
            CaptionWordTiming("world", 500L, 1_000L, 6, 11)
        )
        val gap = CaptionStyleTiming.evaluate(gapWords, style(CaptionStyles.WORD_POP_ID), 450L)
        assertEquals(null, gap.activeWordIndex)
        assertEquals(1f, gap.scale)
    }

    @Test
    fun typewriterRevealsOnlyWordsThatHaveStarted() {
        val frame = CaptionStyleTiming.evaluate(words, style(CaptionStyles.TYPEWRITER_ID), 750L)

        assertEquals(listOf(0, 1), frame.visibleWordIndices)
        assertEquals(1, frame.activeWordIndex)
        assertEquals(null, frame.highlightedWordIndex)
    }

    @Test
    fun bounceHighlightsAndOffsetsTheActiveWordDuringItsMeasuredInterval() {
        val frame = CaptionStyleTiming.evaluate(words, style(CaptionStyles.BOUNCE_ID), 1_100L)

        assertEquals(2, frame.activeWordIndex)
        assertEquals(2, frame.highlightedWordIndex)
        assertTrue("active word should move upward during the bounce", frame.verticalOffsetFraction < 0f)
        assertFalse(frame.visibleWordIndices.isEmpty())
    }

    private fun style(id: String) = requireNotNull(CaptionStyles.find(id))
}
