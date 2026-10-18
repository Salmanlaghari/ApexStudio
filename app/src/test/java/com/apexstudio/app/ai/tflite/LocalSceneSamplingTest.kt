package com.apexstudio.app.ai.tflite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LocalSceneSamplingTest {
    @Test fun exactIntervalNeverSchedulesTheExclusiveOutPoint() {
        assertEquals(listOf(0L, 1_000L, 2_000L, 2_999L), LocalSceneSampling.timestamps(0L, 3_000L, 1_000L, 360))
    }

    @Test fun sourceOffsetAndLastRetainedMillisecondArePreserved() {
        assertEquals(listOf(4_500L, 5_500L, 6_500L, 7_499L), LocalSceneSampling.timestamps(4_500L, 7_500L, 1_000L, 360))
        assertEquals(listOf(4_500L, 5_500L, 6_500L), LocalSceneSampling.timestamps(4_500L, 6_501L, 1_000L, 360))
    }

    @Test fun capSpansEntireTrimInsteadOfSilentlyDroppingItsTail() {
        assertEquals(listOf(0L, 3_333L, 6_666L, 9_999L), LocalSceneSampling.timestamps(0L, 10_000L, 1_000L, 4))
        // The interval grid fits the cap, but retaining the tail needs one more sample.
        assertEquals(listOf(0L, 1_499L, 2_999L), LocalSceneSampling.timestamps(0L, 3_000L, 1_000L, 3))
    }

    @Test fun oneAndTwoMillisecondRangesContainNoDuplicateOrOutsideSamples() {
        assertEquals(listOf(7L), LocalSceneSampling.timestamps(7L, 8L, 1_000L, 360))
        assertEquals(listOf(7L, 8L), LocalSceneSampling.timestamps(7L, 9L, 1_000L, 360))
    }

    @Test fun hugeIntervalCannotOverflowOrAllocateBySourceDuration() {
        assertEquals(listOf(10L, 99L), LocalSceneSampling.timestamps(10L, 100L, Long.MAX_VALUE, 360))
        val times = LocalSceneSampling.timestamps(0L, Long.MAX_VALUE, 1L, 10_000)
        assertEquals(10_000, times.size)
        assertEquals(0L, times.first())
        assertEquals(Long.MAX_VALUE - 1L, times.last())
        assertTrue(times.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun largeSourceOffsetStaysLongSafe() {
        val times = LocalSceneSampling.timestamps(Long.MAX_VALUE - 101L, Long.MAX_VALUE, 1L, 4)
        assertEquals(Long.MAX_VALUE - 101L, times.first())
        assertEquals(Long.MAX_VALUE - 1L, times.last())
        assertTrue(times.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun randomizedRangesAreOrderedBoundedAndCoverBothEdges() {
        val random = Random(165)
        repeat(2_000) {
            val start = random.nextLong(0L, Long.MAX_VALUE - 1_000_000L)
            val end = start + random.nextLong(1L, 1_000_000L)
            val cap = random.nextInt(2, 361)
            val times = LocalSceneSampling.timestamps(start, end, random.nextLong(1L, 50_000L), cap)
            assertTrue(times.size in 1..cap)
            assertEquals(start, times.first())
            assertEquals(end - 1L, times.last())
            assertTrue(times.all { it >= start && it < end })
            assertTrue(times.zipWithNext().all { (a, b) -> a < b })
        }
    }

    @Test fun invalidConfigurationIsRejectedBeforeSampling() {
        listOf<() -> Unit>(
            { LocalSceneSampling.timestamps(-1L, 5L, 1L, 2) },
            { LocalSceneSampling.timestamps(5L, 5L, 1L, 2) },
            { LocalSceneSampling.timestamps(6L, 5L, 1L, 2) },
            { LocalSceneSampling.timestamps(0L, 5L, 0L, 2) },
            { LocalSceneSampling.timestamps(0L, 5L, 1L, 1) },
            { LocalSceneSampling.timestamps(0L, 5L, 1L, 10_001) },
        ).forEach { invalid ->
            try {
                invalid()
                throw AssertionError("Invalid sampling parameters accepted")
            } catch (_: IllegalArgumentException) { /* expected */ }
        }
    }
}
