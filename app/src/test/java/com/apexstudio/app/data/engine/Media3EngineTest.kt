package com.apexstudio.app.data.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Media3Engine].
 */
class Media3EngineTest {

    private val engine = Media3Engine()

    @Test
    fun testEngineTypeIsMedia3() {
        assertEquals(EngineType.MEDIA3, engine.engineType)
    }

    @Test
    fun testIsAvailableReturnsTrue() {
        assertTrue(engine.isAvailable())
    }

    @Test
    fun testGetVersionReturnsMedia3Version() {
        assertEquals("1.11.1", engine.getVersion())
    }

    @Test
    fun testSupportsSimpleOperations() {
        assertTrue(engine.supports(MediaOperation(OperationType.TRIM_CUT, isComplex = false)))
        assertTrue(engine.supports(MediaOperation(OperationType.PLAYBACK)))
        assertTrue(engine.supports(MediaOperation(OperationType.EXPORT, isComplex = false)))
    }

    @Test
    fun testDoesNotSupportComplexOrRealtimeOperations() {
        assertFalse(engine.supports(MediaOperation(OperationType.TRIM_CUT, isComplex = true)))
        assertFalse(engine.supports(MediaOperation(OperationType.COMPLEX_FILTER)))
        assertFalse(engine.supports(MediaOperation(OperationType.REALTIME_PREVIEW)))
        assertFalse(engine.supports(MediaOperation(OperationType.STREAMING)))
        assertFalse(engine.supports(MediaOperation(OperationType.EXPORT, isComplex = true)))
    }

    @Test
    fun testExecuteTrimCommand() {
        val result = engine.execute(arrayOf("trim", "/in.mp4", "/out.mp4", "1000", "5000"))
        assertEquals(0, result)
    }
}
