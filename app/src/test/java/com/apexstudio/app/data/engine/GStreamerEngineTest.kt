package com.apexstudio.app.data.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GStreamerEngine].
 */
class GStreamerEngineTest {

    private val engine = GStreamerEngine()

    @Test
    fun testEngineTypeIsGStreamer() {
        assertEquals(EngineType.GSTREAMER, engine.engineType)
    }

    @Test
    fun testIsAvailableReturnsBooleanSafely() {
        // Must not throw even when native binaries are not loaded in JVM
        val available = engine.isAvailable()
        assertTrue(available is Boolean)
    }

    @Test
    fun testGetVersionReturnsNonEmptyString() {
        val version = engine.getVersion()
        assertTrue(version.isNotEmpty())
    }

    @Test
    fun testSupportsRealtimeAndStreaming() {
        assertTrue(engine.supports(MediaOperation(OperationType.REALTIME_PREVIEW)))
        assertTrue(engine.supports(MediaOperation(OperationType.STREAMING)))
        assertTrue(engine.supports(MediaOperation(OperationType.COMPLEX_FILTER, requiresRealtime = true)))
    }

    @Test
    fun testDoesNotSupportOfflineBatchOperations() {
        assertFalse(engine.supports(MediaOperation(OperationType.TRIM_CUT)))
        assertFalse(engine.supports(MediaOperation(OperationType.AUDIO_EXTRACT)))
        assertFalse(engine.supports(MediaOperation(OperationType.CONCAT_MERGE)))
    }

    @Test
    fun testExecutePipelineString() {
        val result = engine.execute(arrayOf("videotestsrc", "!", "autovideosink"))
        assertEquals(0, result)
    }
}
