package com.apexstudio.editor.thumbnail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineThumbnailRequestPassTest {
    private val key = "clip" to 0L

    @Test
    fun `cached visible frames do not request fast polling or consume capacity`() {
        val inFlight = mutableSetOf<Pair<String, Long>>()
        val pass = TimelineThumbnailRequestPass(inFlight)
        assertFalse(pass.claim(key, cached = true, visible = true))
        assertFalse(pass.visibleWorkDispatched)
        assertEquals(0, pass.launched)
        assertEquals(400L, pass.pollDelayMs)
        assertTrue(inFlight.isEmpty())
    }

    @Test
    fun `already in-flight visible frames do not request fast polling`() {
        val pass = TimelineThumbnailRequestPass(mutableSetOf(key))
        assertFalse(pass.claim(key, cached = false, visible = true))
        assertEquals(0, pass.launched)
        assertEquals(400L, pass.pollDelayMs)
    }

    @Test
    fun `full decoder capacity does not request fast polling for undispatched visible work`() {
        val inFlight = (1L..4L).map { "clip" to it }.toMutableSet()
        val pass = TimelineThumbnailRequestPass(inFlight)
        assertFalse(pass.claim(key, cached = false, visible = true))
        assertEquals(4, inFlight.size)
        assertEquals(0, pass.launched)
        assertEquals(400L, pass.pollDelayMs)
    }

    @Test
    fun `new visible decode claims its key and requests fast polling`() {
        val inFlight = mutableSetOf<Pair<String, Long>>()
        val pass = TimelineThumbnailRequestPass(inFlight)
        assertTrue(pass.claim(key, cached = false, visible = true))
        assertTrue(key in inFlight)
        assertTrue(pass.visibleWorkDispatched)
        assertEquals(1, pass.launched)
        assertEquals(40L, pass.pollDelayMs)
        assertFalse(pass.claim(key, cached = false, visible = true))
        assertEquals(1, pass.launched)
    }

    @Test
    fun `background decodes stay paced and per-pass capacity survives completed jobs`() {
        val inFlight = mutableSetOf<Pair<String, Long>>()
        val pass = TimelineThumbnailRequestPass(inFlight)
        repeat(4) {
            val next = "clip" to it.toLong()
            assertTrue(pass.claim(next, cached = false, visible = false))
            inFlight.remove(next)
        }
        assertFalse(pass.claim("clip" to 4L, cached = false, visible = true))
        assertEquals(4, pass.launched)
        assertEquals(400L, pass.pollDelayMs)
    }
}
