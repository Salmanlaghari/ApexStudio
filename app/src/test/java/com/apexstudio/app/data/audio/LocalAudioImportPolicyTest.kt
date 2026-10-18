package com.apexstudio.app.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalAudioImportPolicyTest {
    @Test
    fun acceptsExplicitAudioStreamButRejectsVideoOnlyOrMissingFlag() {
        LocalAudioImportPolicy.requireAudioStream("yes")
        listOf(null, "no", "").forEach { metadata ->
            val failure = assertThrows(LocalAudioImportException::class.java) {
                LocalAudioImportPolicy.requireAudioStream(metadata)
            }
            assertEquals(LocalAudioImportException.Reason.INVALID_AUDIO, failure.reason)
        }
    }

    @Test
    fun acceptsOnlyRealPositiveMetadataDurations() {
        assertEquals(12_345L, LocalAudioImportPolicy.requireDurationMs("12345"))
        listOf(null, "", "unknown", "0", "-120").forEach { metadata ->
            val failure = assertThrows(LocalAudioImportException::class.java) {
                LocalAudioImportPolicy.requireDurationMs(metadata)
            }
            assertEquals(LocalAudioImportException.Reason.INVALID_AUDIO, failure.reason)
        }
    }

    @Test
    fun sanitizesProviderNamesAndAddsMimeExtensionWhenNeeded() {
        assertEquals("Voice note.m4a", LocalAudioImportPolicy.safeFileName("Voice note", "fallback", "audio/mp4"))
        assertEquals("recording.mp3", LocalAudioImportPolicy.safeFileName("recording.mp3", "fallback", "audio/mpeg"))
        assertEquals("outside.audio", LocalAudioImportPolicy.safeFileName("../../outside", "fallback", null))
    }
}
