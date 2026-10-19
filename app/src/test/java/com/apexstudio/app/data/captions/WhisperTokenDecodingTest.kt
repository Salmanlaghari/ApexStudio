package com.apexstudio.app.data.captions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class WhisperTokenDecodingTest {
    @Test fun splitUtf8CodePointsRemainIntactAcrossByteBpeTokens() {
        for (phrase in listOf("السلام علیکم دنیا", "नमस्ते दुनिया", "hello 🌍 café", "こんにちは世界")) {
            val bytes = phrase.toByteArray(Charsets.UTF_8)
            val table = bytes.indices.associateWith { byteArrayOf(bytes[it]) }
            val decoded = decodeWhisperTokenBytes(bytes.indices.toList(), table)
            assertEquals(phrase, decoded)
            assertFalse(decoded.contains('\uFFFD'))
        }
    }

    @Test fun mixedByteTokenLengthsAreJoinedBeforeDecoding() {
        val table = mapOf(
            0 to "Hello ".toByteArray(),
            1 to byteArrayOf(0xF0.toByte(), 0x9F.toByte()),
            2 to byteArrayOf(0x8C.toByte(), 0x8D.toByte()),
            3 to "!".toByteArray(),
        )
        assertEquals("Hello 🌍!", decodeWhisperTokenBytes(listOf(0, 1, 2, 3), table))
        assertEquals("Hello !", decodeWhisperTokenBytes(listOf(0, 99, 3), table))
        assertEquals("", decodeWhisperTokenBytes(emptyList(), table))
    }

    @Test fun promptTokensAreIncludedInDecoderContextLimit() {
        assertEquals(445, whisperDecodeBudget(448, 3))
        assertEquals(1, whisperDecodeBudget(4, 3))
        assertThrows(IllegalArgumentException::class.java) { whisperDecodeBudget(3, 3) }
        assertThrows(IllegalArgumentException::class.java) { whisperDecodeBudget(448, 0) }
        assertThrows(IllegalArgumentException::class.java) { whisperDecodeBudget(-1, 3) }
    }
}
