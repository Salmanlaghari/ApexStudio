package com.apexstudio.app.data.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FFmpegEngine] and [FFmpegCommands].
 *
 * These are pure JVM tests — they verify command construction only,
 * not actual FFmpeg execution (which requires the native library).
 */
class FFmpegEngineTest {

    private val engine = FFmpegEngine()

    @Test
    fun testEngineTypeIsFFmpeg() {
        assertEquals(EngineType.FFMPEG, engine.engineType)
    }

    @Test
    fun testIsAvailableReturnsBoolean() {
        val result = engine.isAvailable()
        // isAvailable must not throw; result is a Boolean by type.
        assertTrue(result is Boolean)
    }

    @Test
    fun testSupportsOperations() {
        assertTrue(engine.supports(MediaOperation(OperationType.COMPLEX_FILTER)))
        assertTrue(engine.supports(MediaOperation(OperationType.AUDIO_EXTRACT)))
        assertTrue(engine.supports(MediaOperation(OperationType.CONCAT_MERGE)))
        assertTrue(engine.supports(MediaOperation(OperationType.FORMAT_CONVERT)))
        assertTrue(engine.supports(MediaOperation(OperationType.EXPORT, isComplex = true)))
        assertFalse(engine.supports(MediaOperation(OperationType.PLAYBACK)))
    }

    @Test
    fun testTrimVideoGeneratesCorrectCommand() {
        val command = FFmpegCommands.trimVideo("/input.mp4", "/output.mp4", 1000, 5000)
        assertTrue(command.contains("-ss"))
        assertTrue(command.contains("-to"))
        assertTrue(command.contains("-i"))
        assertTrue(command.contains("-c"))
        assertTrue(command.contains("copy"))
    }

    @Test
    fun testConvertFormatGeneratesCorrectCommand() {
        val command = FFmpegCommands.convertFormat("/input.mp4", "/output.mp4")
        assertTrue(command.contains("-i"))
        assertTrue(command.contains("-c:v"))
        assertTrue(command.contains("libx264"))
        assertTrue(command.contains("-c:a"))
        assertTrue(command.contains("aac"))
    }

    @Test
    fun testExtractAudioGeneratesCorrectCommand() {
        val command = FFmpegCommands.extractAudio("/input.mp4", "/output.mp3")
        assertTrue(command.contains("-vn"))
    }

    @Test
    fun testMergeVideosSingleInput() {
        val command = FFmpegCommands.mergeVideos(listOf("/input1.mp4"), "/output.mp4")
        // mergeVideos always uses the concat protocol, even for a single input.
        assertTrue(command.contains("-i"))
        assertTrue(command.contains("copy"))
        assertTrue(command.any { it.startsWith("concat:") })
    }

    @Test
    fun testMergeVideosMultipleInputsUsesConcat() {
        val command = FFmpegCommands.mergeVideos(
            listOf("/input1.mp4", "/input2.mp4"),
            "/output.mp4"
        )
        assertTrue(command.any { it.startsWith("concat:") })
    }
}
