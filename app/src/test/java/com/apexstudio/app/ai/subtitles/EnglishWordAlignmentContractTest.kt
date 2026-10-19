package com.apexstudio.app.ai.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishWordAlignmentContractTest {
    @Test fun everyCharacterTokenIsMappedAndSeparatorsAreNotPartOfAnyWord() {
        val encoded = tokenizeEnglishTranscript("Don't stop.")
        assertEquals(encoded.tokenIds.size, encoded.wordIndices.size)
        assertEquals(-1, encoded.wordIndices[5])
        assertTrue(encoded.wordIndices.filter { it >= 0 }.distinct() == listOf(0, 1))
    }

    @Test fun oversizedTranscriptIsRejectedBeforeTokenExpansion() {
        try {
            tokenizeEnglishTranscript("A".repeat(MAX_ENGLISH_TRANSCRIPT_CHARACTERS + 1))
            throw AssertionError("Expected oversized transcript to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("character alignment limit"))
        }
    }
}
