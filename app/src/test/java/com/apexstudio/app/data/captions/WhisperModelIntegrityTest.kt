package com.apexstudio.app.data.captions

import com.apexstudio.app.ai.AiModelUnavailableException
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperModelIntegrityTest {
    @Test fun rawModelLoadRejectsBeforeMissingFileOrNativeAccess() {
        assertRawLoadBlocked(File("/not-installed/whisper-model.onnx"))
    }

    @Test fun rawModelLoadCannotTreatExistingUnreviewedFilesAsApproval() {
        val file = File.createTempFile("unreviewed-whisper", ".onnx")
        try {
            file.writeText("not an ONNX model")
            assertRawLoadBlocked(file)
        } finally {
            file.delete()
        }
    }

    private fun assertRawLoadBlocked(file: File) {
        try {
            WhisperModel.load(file, file, file)
            throw AssertionError("Raw model loading must fail before native processing")
        } catch (failure: AiModelUnavailableException) {
            assertTrue(failure.message.orEmpty().contains("Whisper caption generation"))
            assertTrue(failure.message.orEmpty().contains("No protected AI processing was run"))
        }
    }

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
