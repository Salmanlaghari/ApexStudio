package com.apexstudio.app.ai.subtitles

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishWordAlignmentTest {
    @Test fun tokenizerMapsLettersToPinnedWav2vecVocabularyAndKeepsDisplayedPunctuation() {
        val tokens = tokenizeEnglishTranscript("Hello, can't stop!")

        assertEquals(listOf("Hello,", "can't", "stop!"), tokens.displayedWords)
        assertArrayEquals(
            intArrayOf(11, 5, 15, 15, 8, 4, 19, 7, 9, 27, 6, 4, 12, 6, 8, 23),
            tokens.tokenIds,
        )
        assertArrayEquals(
            intArrayOf(0, 0, 0, 0, 0, -1, 1, 1, 1, 1, 1, -1, 2, 2, 2, 2),
            tokens.wordIndices,
        )
        assertEquals(BundledEnglishAlignmentModel.SHA256,
            "1d9a366c27b2966625cd5035ac3db8847c53bba617169912bb251b42975a3a22")
        assertEquals(95_212_816L, BundledEnglishAlignmentModel.SIZE_BYTES)
    }

    @Test fun tokenizerAllowsAsciiPunctuationAroundWordsButRejectsDigitsAndUnicode() {
        assertEquals(listOf("(Well-", "done.)"), tokenizeEnglishTranscript("(Well-done.)").displayedWords)
        assertThrows(IllegalArgumentException::class.java) { tokenizeEnglishTranscript("hello 42") }
        assertThrows(IllegalArgumentException::class.java) { tokenizeEnglishTranscript("café") }
        assertThrows(IllegalArgumentException::class.java) { tokenizeEnglishTranscript("hello\u00a0world") }
        assertThrows(IllegalArgumentException::class.java) { tokenizeEnglishTranscript("!!!") }
    }

    @Test fun apostropheOnlyJoinsWordsInternallyAndNeverCreatesEmptyTargets() {
        val tokens = tokenizeEnglishTranscript("'tis dogs'")
        assertEquals(listOf("'tis", "dogs'"), tokens.displayedWords)
        assertTrue(tokens.tokenIds.isNotEmpty())
        assertEquals(tokens.tokenIds.size, tokens.wordIndices.size)
        assertEquals(-1, tokens.wordIndices[3]) // separator after the first three letters
    }

    @Test fun uriPolicyAllowsLocalInputsButRejectsNetworkAndRemoteFileUris() {
        assertTrue(isAllowedLocalAudioUri(null, null)) // plain local path URI
        assertTrue(isAllowedLocalAudioUri("file", null))
        assertTrue(isAllowedLocalAudioUri("content", "com.example.provider"))
        assertTrue(isAllowedLocalAudioUri("android.resource", "com.example.app"))
        assertFalse(isAllowedLocalAudioUri("file", "remote-host"))
        assertFalse(isAllowedLocalAudioUri("https", "example.com"))
        assertFalse(isAllowedLocalAudioUri("http", null))
    }
}
