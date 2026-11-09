package com.apexstudio.app.presentation.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCutPlannerTest {
    @Test
    fun `distributes beat intervals evenly and places every cut on a beat`() {
        val plan = AutoCutPlanner.plan(
            beatTimestampsMs = listOf(0L, 500L, 1_000L, 1_500L, 2_000L, 2_500L, 3_000L),
            sequenceStartMs = 0L,
            clips = listOf(
                AutoCutClipCapacity("a", 2_000L),
                AutoCutClipCapacity("b", 1_500L),
                AutoCutClipCapacity("c", 3_000L)
            )
        )

        assertEquals(
            listOf(
                AutoCutSegment("a", 0L, 1_000L),
                AutoCutSegment("b", 1_000L, 2_000L),
                AutoCutSegment("c", 2_000L, 3_000L)
            ),
            plan
        )
        assertTrue(plan.drop(1).all { segment -> segment.timelineStartMs in listOf(1_000L, 2_000L) })
        assertTrue(plan.all { segment -> segment.timelineEndMs in listOf(500L, 1_000L, 1_500L, 2_000L, 2_500L, 3_000L) })
    }

    @Test
    fun `short source clips consume only complete beat intervals`() {
        val plan = AutoCutPlanner.plan(
            beatTimestampsMs = listOf(0L, 500L, 1_000L, 1_600L, 2_100L),
            sequenceStartMs = 0L,
            clips = listOf(
                AutoCutClipCapacity("short", 500L),
                AutoCutClipCapacity("medium", 700L),
                AutoCutClipCapacity("long", 1_500L)
            )
        )

        assertEquals(
            listOf(
                AutoCutSegment("short", 0L, 500L),
                AutoCutSegment("medium", 500L, 1_000L),
                AutoCutSegment("long", 1_000L, 2_100L)
            ),
            plan
        )
        assertTrue(plan.all { it.durationMs <= listOf(500L, 700L, 1_500L)[plan.indexOf(it)] })
    }

    @Test
    fun `finds a feasible allocation when the locally balanced first interval would strand the last clip`() {
        val plan = AutoCutPlanner.plan(
            beatTimestampsMs = listOf(0L, 1L, 2L, 3L),
            sequenceStartMs = 0L,
            clips = listOf(AutoCutClipCapacity("long", 2L), AutoCutClipCapacity("short", 1L))
        )

        assertEquals(
            listOf(AutoCutSegment("long", 0L, 2L), AutoCutSegment("short", 2L, 3L)),
            plan
        )
    }

    @Test
    fun `starts after the existing sequence and ignores earlier or duplicate beats`() {
        val plan = AutoCutPlanner.plan(
            beatTimestampsMs = listOf(100L, 500L, 1_000L, 1_000L, 1_500L, 2_000L, 2_500L),
            sequenceStartMs = 1_000L,
            clips = listOf(
                AutoCutClipCapacity("a", 1_000L),
                AutoCutClipCapacity("b", 1_000L)
            )
        )

        assertEquals(
            listOf(AutoCutSegment("a", 1_000L, 1_500L), AutoCutSegment("b", 1_500L, 2_500L)),
            plan
        )
    }

    @Test
    fun `rejects incomplete plans instead of silently dropping selected clips`() {
        assertTrue(
            AutoCutPlanner.plan(
                beatTimestampsMs = listOf(0L, 400L, 900L),
                sequenceStartMs = 0L,
                clips = listOf(AutoCutClipCapacity("a", 500L), AutoCutClipCapacity("b", 500L), AutoCutClipCapacity("c", 500L))
            ).isEmpty()
        )
        assertTrue(
            AutoCutPlanner.plan(
                beatTimestampsMs = listOf(0L, 500L, 1_000L),
                sequenceStartMs = 0L,
                clips = listOf(AutoCutClipCapacity("too-short", 100L), AutoCutClipCapacity("other", 1_000L))
            ).isEmpty()
        )
    }
}
