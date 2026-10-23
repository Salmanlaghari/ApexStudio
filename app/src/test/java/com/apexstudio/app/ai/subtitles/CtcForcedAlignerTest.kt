package com.apexstudio.app.ai.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CtcForcedAlignerTest {
    @Test fun wordBoundariesComeFromAcousticTokenFrames() {
        val low = kotlin.math.ln(0.01f)
        val high = kotlin.math.ln(0.97f)
        val logits = Array(4) { FloatArray(3) { low } }
        logits[0][0] = high
        logits[1][1] = high
        logits[2][0] = high
        logits[3][2] = high
        val aligned = CtcForcedAligner.align(logits, blankId = 0,
            targetTokenIds = intArrayOf(1, 2), wordIndexForToken = intArrayOf(0, 1),
            wordTexts = listOf("hello", "world"), frameDurationMs = 20f, startOffsetMs = 100L)
        assertEquals(listOf("hello", "world"), aligned.words.map { it.text })
        assertEquals(120L, aligned.words[0].startMs)
        assertEquals(140L, aligned.words[0].endMs)
        assertEquals(160L, aligned.words[1].startMs)
        assertEquals(180L, aligned.words[1].endMs)
        assertTrue(aligned.words.all { it.precision == TimingPrecision.CTC_FORCED_WORD_ALIGNMENT })
        assertTrue(aligned.words.all { it.confidence!! > 0.9f })
    }

    @Test fun invalidVocabularyOrMissingFramesFailsInsteadOfGuessing() {
        try {
            CtcForcedAligner.align(arrayOf(floatArrayOf(0f)), 0, intArrayOf(1), intArrayOf(0), listOf("x"), 10f)
            throw AssertionError("Expected bad-token validation")
        } catch (_: IllegalArgumentException) { }
    }
}
