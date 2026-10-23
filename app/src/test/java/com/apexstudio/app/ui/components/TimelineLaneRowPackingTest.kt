package com.apexstudio.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineLaneRowPackingTest {
    @Test
    fun `overlapping timeline items receive separate independently visible subrows`() {
        val rows = timelineLaneRowIndices(
            listOf(
                TimelineLaneSpan(0L, 1_000L),
                TimelineLaneSpan(250L, 750L),
                TimelineLaneSpan(500L, 1_250L),
            )
        )

        assertEquals(listOf(0, 1, 2), rows)
        assertEquals(3, timelineLaneRowCount(rows))
    }

    @Test
    fun `out of order and touching segments reuse rows without unnecessary lane growth`() {
        val rows = timelineLaneRowIndices(
            listOf(
                TimelineLaneSpan(1_000L, 2_000L),
                TimelineLaneSpan(0L, 1_000L),
                TimelineLaneSpan(500L, 750L),
            )
        )

        assertEquals(listOf(0, 0, 1), rows)
        assertEquals(2, timelineLaneRowCount(rows))
    }

    @Test
    fun `empty lane has no rows and zero length spans remain placeable`() {
        assertEquals(emptyList<Int>(), timelineLaneRowIndices(emptyList()))
        val rows = timelineLaneRowIndices(listOf(TimelineLaneSpan(-10L, -10L)))
        assertEquals(listOf(0), rows)
        assertEquals(1, timelineLaneRowCount(rows))
    }
}
