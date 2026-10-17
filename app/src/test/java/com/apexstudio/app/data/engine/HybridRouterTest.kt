package com.apexstudio.app.data.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HybridRouter].
 */
class HybridRouterTest {

    private val router = HybridRouter()

    @Test
    fun testRoutesSimpleTrimToMedia3() {
        val op = MediaOperation(OperationType.TRIM_CUT, isComplex = false)
        val decision = router.planRoute(op)
        assertEquals(EngineType.MEDIA3, decision.selectedEngine)
        assertTrue(decision.fallbackEngines.contains(EngineType.FFMPEG))
    }

    @Test
    fun testRoutesPlaybackToMedia3() {
        val op = MediaOperation(OperationType.PLAYBACK)
        val decision = router.planRoute(op)
        assertEquals(EngineType.MEDIA3, decision.selectedEngine)
    }

    @Test
    fun testRoutesSimpleExportToMedia3() {
        val op = MediaOperation(OperationType.EXPORT, isComplex = false)
        val decision = router.planRoute(op)
        assertEquals(EngineType.MEDIA3, decision.selectedEngine)
    }

    @Test
    fun testRoutesComplexFilterToFFmpeg() {
        val op = MediaOperation(OperationType.COMPLEX_FILTER)
        val decision = router.planRoute(op)
        assertEquals(EngineType.FFMPEG, decision.selectedEngine)
        assertTrue(decision.fallbackEngines.contains(EngineType.GSTREAMER))
    }

    @Test
    fun testRoutesComplexExportToFFmpeg() {
        val op = MediaOperation(OperationType.EXPORT, isComplex = true)
        val decision = router.planRoute(op)
        assertEquals(EngineType.FFMPEG, decision.selectedEngine)
        assertTrue(decision.fallbackEngines.contains(EngineType.MEDIA3))
    }

    @Test
    fun testRoutesAudioExtractAndConcatToFFmpeg() {
        val opAudio = MediaOperation(OperationType.AUDIO_EXTRACT)
        val opConcat = MediaOperation(OperationType.CONCAT_MERGE)
        val opConvert = MediaOperation(OperationType.FORMAT_CONVERT)

        assertEquals(EngineType.FFMPEG, router.planRoute(opAudio).selectedEngine)
        assertEquals(EngineType.FFMPEG, router.planRoute(opConcat).selectedEngine)
        assertEquals(EngineType.FFMPEG, router.planRoute(opConvert).selectedEngine)
    }

    @Test
    fun testRoutesRealtimePreviewAndStreamingToGStreamer() {
        val opPreview = MediaOperation(OperationType.REALTIME_PREVIEW)
        val opStreaming = MediaOperation(OperationType.STREAMING)
        val opRealtimeFilter = MediaOperation(OperationType.COMPLEX_FILTER, requiresRealtime = true)

        assertEquals(EngineType.GSTREAMER, router.planRoute(opPreview).selectedEngine)
        assertEquals(EngineType.GSTREAMER, router.planRoute(opStreaming).selectedEngine)
        assertEquals(EngineType.GSTREAMER, router.planRoute(opRealtimeFilter).selectedEngine)
    }

    @Test
    fun testRoutingHistoryIsLogged() {
        val initialCount = router.getRoutingHistory().size
        val op = MediaOperation(OperationType.TRIM_CUT)
        router.planRoute(op)

        val updated = router.getRoutingHistory()
        assertEquals(initialCount + 1, updated.size)
        val last = updated.last()
        assertEquals(op, last.operation)
        assertNotNull(last.rationale)
        assertTrue(last.timestamp > 0)
    }

    @Test
    fun testSuccessfulExecutionWithoutFallback() {
        val mockMedia3 = object : Engine {
            override val engineType = EngineType.MEDIA3
            override fun isAvailable() = true
            override fun getVersion() = "1.0"
            override fun supports(operation: MediaOperation) = true
            override fun execute(command: Array<String>) = 0
        }

        val testRouter = HybridRouter(
            mapOf(
                EngineType.MEDIA3 to mockMedia3,
                EngineType.FFMPEG to FFmpegEngine(),
                EngineType.GSTREAMER to GStreamerEngine()
            )
        )

        val op = MediaOperation(OperationType.TRIM_CUT)
        val result = testRouter.execute(op, arrayOf("trim", "/in.mp4", "/out.mp4"))

        assertTrue(result.isSuccess)
        assertEquals(EngineType.MEDIA3, result.engineUsed)
        assertFalse(result.fallbackOccurred)
        assertEquals(listOf(EngineType.MEDIA3), result.attemptedEngines)
    }

    @Test
    fun testAutomaticFallbackWhenPrimaryEngineFails() {
        val failingMedia3 = object : Engine {
            override val engineType = EngineType.MEDIA3
            override fun isAvailable() = true
            override fun getVersion() = "1.0"
            override fun supports(operation: MediaOperation) = true
            override fun execute(command: Array<String>) = throw RuntimeException("Media3 hardware codec failed")
        }

        val fallbackFFmpeg = object : Engine {
            override val engineType = EngineType.FFMPEG
            override fun isAvailable() = true
            override fun getVersion() = "8.1"
            override fun supports(operation: MediaOperation) = true
            override fun execute(command: Array<String>) = 0
        }

        val testRouter = HybridRouter(
            mapOf(
                EngineType.MEDIA3 to failingMedia3,
                EngineType.FFMPEG to fallbackFFmpeg,
                EngineType.GSTREAMER to GStreamerEngine()
            )
        )

        val op = MediaOperation(OperationType.TRIM_CUT)
        val result = testRouter.execute(op, arrayOf("-ss", "0", "-to", "10"))

        assertTrue(result.isSuccess)
        assertEquals(EngineType.FFMPEG, result.engineUsed)
        assertTrue(result.fallbackOccurred)
        assertEquals(listOf(EngineType.MEDIA3, EngineType.FFMPEG), result.attemptedEngines)
    }
}
