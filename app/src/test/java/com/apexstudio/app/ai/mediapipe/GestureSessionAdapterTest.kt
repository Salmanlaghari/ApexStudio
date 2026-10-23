package com.apexstudio.app.ai.mediapipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSessionAdapterTest {
    @Test
    fun `commands require explicit session arm and disarm clears debounce`() {
        val adapter = GestureSessionAdapter()
        assertNull(adapter.update(0, listOf(hand(0.2f))))
        adapter.arm("editor-context-7")
        assertTrue(adapter.isArmed())
        assertNull(adapter.update(0, listOf(hand(0.2f))))
        assertNull(adapter.update(60, listOf(hand(0.31f))))
        val command = adapter.update(120, listOf(hand(0.45f)))
        assertTrue(command is GestureCommand.TrimByFraction)
        assertNull(adapter.update(180, listOf(hand(0.9f)))) // Existing controller latch debounces repeats.

        adapter.disarm()
        assertFalse(adapter.isArmed())
        assertNull(adapter.update(240, listOf(hand(0.1f))))
        adapter.arm("editor-context-8")
        assertNull(adapter.update(300, listOf(hand(0.9f)))) // New context starts with a fresh baseline.
    }

    @Test
    fun `mapper requires exactly one valid hand and mirrors preview x only`() {
        val hand = hand(0.25f)
        val normal = GestureSessionAdapter.handSample(123, listOf(hand))
        assertTrue(normal.tracked)
        assertEquals(0.25f, normal.handCenterX!!, 0.0001f)
        assertEquals(0.8f, normal.pinchRatio!!, 0.0001f)

        val mirrored = GestureSessionAdapter.handSample(123, listOf(hand), mirrorHorizontal = true)
        assertEquals(0.75f, mirrored.handCenterX!!, 0.0001f)
        assertEquals(normal.pinchRatio, mirrored.pinchRatio)
        assertFalse(GestureSessionAdapter.handSample(123, emptyList()).tracked)
        assertFalse(GestureSessionAdapter.handSample(123, listOf(hand, hand)).tracked)
        assertFalse(GestureSessionAdapter.handSample(123, listOf(HandDetection(emptyList()))).tracked)
    }

    @Test
    fun `tracking loss cannot complete a partial swipe`() {
        val adapter = GestureSessionAdapter()
        adapter.arm("editor-context")
        assertNull(adapter.update(0, listOf(hand(0.2f))))
        assertNull(adapter.update(60, listOf(hand(0.32f))))
        assertNull(adapter.update(80, emptyList()))
        assertNull(adapter.update(100, listOf(hand(0.8f))))
        assertNull(adapter.update(160, listOf(hand(0.7f))))
        val fresh = adapter.update(220, listOf(hand(0.6f)))
        assertTrue(fresh is GestureCommand.TrimByFraction)
        assertTrue((fresh as GestureCommand.TrimByFraction).delta < 0f)
    }

    private fun hand(centerX: Float): HandDetection {
        val points = MutableList(21) { NormalizedPoint(centerX, 0.5f) }
        points[0] = NormalizedPoint(centerX, 0.8f)
        points[9] = NormalizedPoint(centerX, 0.4f)
        points[4] = NormalizedPoint(0.2f, 0.4f)
        points[8] = NormalizedPoint(0.52f, 0.4f)
        return HandDetection(points)
    }
}
