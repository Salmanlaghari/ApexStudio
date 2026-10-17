package com.apexstudio.editor.thumbnail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ThumbnailStripLru] — the bounded strip cache behind
 * the Bug 1 fix (an unbounded `Map` that kept every decoded strip, and
 * every deleted clip's bitmaps, alive for the life of the process).
 *
 * Pure JVM: the store is plain Kotlin + `LinkedHashMap`, no Android.
 */
class ThumbnailStripLruTest {

    private fun store(max: Int) = ThumbnailStripLru<String, String>(max)

    // === construction ===

    @Test
    fun `starting store is empty`() {
        val lru = store(4)
        assertEquals(0, lru.size)
        assertTrue(lru.isEmpty)
        assertNull(lru.peek("a"))
    }

    @Test
    fun `non positive max size is rejected`() {
        try {
            store(0)
            throw AssertionError("expected IllegalArgumentException for maxSize = 0")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("maxSize"))
        }
    }

    // === the actual cap ===

    @Test
    fun `put beyond capacity evicts the eldest entry`() {
        val lru = store(2)
        lru.put("a", "A")
        lru.put("b", "B")
        val returned = lru.put("c", "C")

        assertEquals("A", returned)
        assertEquals(2, lru.size)
        assertNull(lru.peek("a"))
        assertEquals("B", lru.peek("b"))
        assertEquals("C", lru.peek("c"))
        assertEquals(1, lru.evictions)
    }

    @Test
    fun `capacity of one holds only the newest listing`() {
        val lru = store(1)
        lru.put("a", "A")
        lru.put("b", "B")
        assertEquals(1, lru.size)
        assertNull(lru.peek("a"))
        assertEquals("B", lru.peek("b"))
    }

    @Test
    fun `replacing an existing key does not evict anything`() {
        val lru = store(2)
        lru.put("a", "A1")
        lru.put("b", "B")
        val returned = lru.put("a", "A2")

        assertEquals("A1", returned)
        assertEquals(2, lru.size)
        assertEquals(0, lru.evictions)
        assertEquals("A2", lru.peek("a"))
    }

    // === recency semantics ===

    @Test
    fun `get promotes an entry to most recently used`() {
        val lru = store(2)
        lru.put("a", "A")
        lru.put("b", "B")
        assertEquals("A", lru.get("a"))   // promotes "a"

        lru.put("c", "C")                  // must now evict "b"
        assertEquals("A", lru.peek("a"))
        assertNull(lru.peek("b"))
        assertEquals("C", lru.peek("c"))
    }

    @Test
    fun `peek does not promote an entry`() {
        val lru = store(2)
        lru.put("a", "A")
        lru.put("b", "B")
        assertEquals("A", lru.peek("a"))   // no promotion

        lru.put("c", "C")                  // "a" is still eldest
        assertNull(lru.peek("a"))
        assertEquals("B", lru.peek("b"))
    }

    // === counters ===

    @Test
    fun `counters track hits and misses separately`() {
        val lru = store(4)
        lru.put("a", "A")

        assertEquals("A", lru.get("a"))
        lru.get("missing")

        assertEquals(1, lru.hits)
        assertEquals(1, lru.misses)
    }

    @Test
    fun `reset counters leaves entries alone`() {
        val lru = store(4)
        lru.put("a", "A")
        lru.get("a")
        lru.get("nope")

        lru.resetCounters()

        assertEquals(0, lru.hits)
        assertEquals(0, lru.misses)
        assertEquals("A", lru.peek("a"))
    }

    // === removal ===

    @Test
    fun `remove drops only that key`() {
        val lru = store(4)
        lru.put("a", "A")
        lru.put("b", "B")

        assertEquals("A", lru.remove("a"))
        assertNull(lru.remove("a"))
        assertEquals(1, lru.size)
        assertFalse(lru.contains("a"))
        assertTrue(lru.contains("b"))
        assertEquals(listOf("b"), lru.keys())
    }

    @Test
    fun `clear empties the store and resets counters`() {
        val lru = store(4)
        lru.put("a", "A")
        lru.get("a")
        lru.get("b")

        lru.clear()

        assertEquals(0, lru.size)
        assertTrue(lru.isEmpty)
        assertEquals(0, lru.hits)
        assertEquals(0, lru.misses)
        assertEquals(0, lru.evictions)
        assertEquals(emptyList<String>(), lru.keys())
    }

    // === snapshot isolation (what the UI reads) ===

    @Test
    fun `snapshot is an independent copy`() {
        val lru = store(4)
        lru.put("a", "A")
        lru.put("b", "B")

        val snap = lru.snapshot()
        lru.put("c", "C")          // mutate the live store
        lru.remove("a")

        assertEquals(setOf("a", "b"), snap.keys)
        assertEquals("A", snap["a"])
        assertEquals("B", snap["b"])
        assertEquals(setOf("b", "c"), lru.snapshot().keys)
    }

    @Test
    fun `keys come back eldest first`() {
        val lru = store(4)
        lru.put("first", "1")
        lru.put("second", "2")
        lru.put("third", "3")

        assertEquals(listOf("first", "second", "third"), lru.keys())
    }
}
