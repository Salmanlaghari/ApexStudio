package com.apexstudio.app.data.captions

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperModelIntegrityTest {
    @Test fun readinessRejectsSameSizeAssetCorruption() {
        val file = File.createTempFile("whisper-asset", ".bin")
        try {
            val original = "model".toByteArray()
            val digest = MessageDigest.getInstance("SHA-256").digest(original)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            file.writeBytes(original)
            assertTrue(isWhisperAssetVerified(file, original.size.toLong(), digest))
            file.writeBytes("other".toByteArray()) // identical length, different bytes
            assertFalse(isWhisperAssetVerified(file, original.size.toLong(), digest))
        } finally {
            file.delete()
        }
    }
}
