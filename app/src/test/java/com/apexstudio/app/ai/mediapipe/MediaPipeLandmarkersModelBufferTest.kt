package com.apexstudio.app.ai.mediapipe

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPipeLandmarkersModelBufferTest {
    @Test
    fun readsModelBytesExactlyIntoDirectBuffer() {
        val file = Files.createTempFile("mediapipe-model", ".task").toFile()
        try {
            val bytes = byteArrayOf(0, 1, 2, 127, -1)
            file.writeBytes(bytes)

            val buffer = MediaPipeLandmarkers.modelBuffer(file)

            assertTrue(buffer.isDirect)
            assertEquals(bytes.size, buffer.remaining())
            for (expected in bytes) assertEquals(expected.toInt(), buffer.get().toInt())
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsEmptyAndOversizedModelsBeforeAllocatingBuffer() {
        val file = Files.createTempFile("mediapipe-model", ".task").toFile()
        try {
            assertRejected(file)
            RandomAccessFile(file, "rw").use {
                it.setLength(MediaPipeLandmarkers.MAX_TASK_MODEL_BYTES.toLong() + 1L)
            }
            assertRejected(file)
        } finally {
            file.delete()
        }
    }

    private fun assertRejected(file: File) {
        try {
            MediaPipeLandmarkers.modelBuffer(file)
            throw AssertionError("Unsafe model size was accepted")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("size"))
        }
    }
}
