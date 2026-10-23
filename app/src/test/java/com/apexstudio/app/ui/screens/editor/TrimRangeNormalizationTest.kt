package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrimRangeNormalizationTest {
    @Test
    fun largeTimesAreClampedWithoutOverflow() {
        val parsedHugeEnd = TimeFormat.parseTimeToMs("1e300")
        assertEquals(Long.MAX_VALUE, parsedHugeEnd)
        assertEquals(
            NormalizedTrimRange(500L, 1_000L),
            normalizeTrimRange(500L, parsedHugeEnd!!, 1_000L)
        )
        assertEquals(
            NormalizedTrimRange(900L, 1_000L),
            normalizeTrimRange(Long.MAX_VALUE - 1L, Long.MAX_VALUE, 1_000L)
        )
        assertEquals(
            NormalizedTrimRange(0L, 1_000L),
            normalizeTrimRange(Long.MIN_VALUE, Long.MAX_VALUE, 1_000L)
        )
    }

    @Test
    fun timesBeyondClipEndSelectTheFinalMinimumSpan() {
        assertEquals(
            NormalizedTrimRange(900L, 1_000L),
            normalizeTrimRange(5_000L, 10_000L, 1_000L)
        )
    }

    @Test
    fun sub100MillisecondClipUsesItsAvailableDurationAsMinimumSpan() {
        assertEquals(
            NormalizedTrimRange(0L, 40L),
            normalizeTrimRange(10L, 20L, 40L)
        )
    }

    @Test
    fun zeroDurationHasNoInventedTrimRange() {
        assertNull(normalizeTrimRange(0L, Long.MAX_VALUE, 0L))
        assertNull(normalizeTrimRange(0L, 100L, -1L))
    }
}
