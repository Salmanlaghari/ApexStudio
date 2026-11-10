package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.ClipTransition
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.viewmodel.computeTimelineDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTransitionTimelineTest {
    private fun video(
        id: String,
        trimStart: Long = 0L,
        trimEnd: Long = 1_000L,
        duration: Long = 10_000L
    ) = MediaClip(
        id = id,
        name = id,
        uri = "file:///$id.mp4",
        durationMs = duration,
        trimStartMs = trimStart,
        trimEndMs = trimEnd
    )

    private fun transition(from: String, to: String, duration: Long) = ClipTransition(
        id = "$from-$to",
        fromClipId = from,
        toClipId = to,
        durationMs = duration
    )

    @Test
    fun `hard cut baseline concatenates video clips`() {
        val plan = ProjectTransitionTimeline.build(listOf(video("a"), video("b")))

        assertEquals(2_000L, plan.durationMs)
        assertEquals(0L, plan.entries[0].timelineStartMs)
        assertEquals(1_000L, plan.entries[1].timelineStartMs)
        assertTrue(plan.transitions.isEmpty())
        assertEquals(listOf(0L to 1_000L, 1_000L to 2_000L),
            plan.audioSegments.map { it.timelineStartMs to it.timelineEndMs })
        assertSame(plan.entries[0], plan.entryAt(999L))
        assertSame(plan.entries[1], plan.entryAt(1_000L))
        assertSame(plan.audioSegments[1], plan.audioSegmentAt(1_000L))
        assertNull(plan.entryAt(2_000L))
    }

    @Test
    fun `single transition overlaps and maps both source clocks`() {
        val a = video("a", trimStart = 2_000L, trimEnd = 6_000L)
        val b = video("b", trimStart = 10_000L, trimEnd = 14_000L, duration = 20_000L)
        val plan = ProjectTransitionTimeline.build(listOf(a, b), listOf(transition("a", "b", 1_000L)))

        assertEquals(7_000L, plan.durationMs)
        assertEquals(4_000L, plan.entries[0].timelineEndMs)
        assertEquals(3_000L, plan.entries[1].timelineStartMs)
        assertEquals(1_000L, plan.transitions.single().durationMs)
        assertEquals(3_000L, plan.transitions.single().startMs)
        assertEquals(4_000L, plan.transitions.single().endMs)
        assertSame(plan.entries[0], plan.entryAt(3_000L))
        assertSame(plan.entries[1], plan.entryAt(4_000L))
        assertEquals(5_500L, plan.sourceTimeAt(3_500L))
        assertEquals(10_000L, plan.entries[1].sourceTimeAtOutputOffset(0L))
        assertEquals(11_000L, plan.sourceTimeAt(4_000L))
        assertEquals(listOf(0L to 3_500L, 3_500L to 7_000L),
            plan.audioSegments.map { it.timelineStartMs to it.timelineEndMs })
        assertEquals(10_500L, plan.audioSegments[1].sourceStartMs)
        assertSame(plan.audioSegments[1], plan.audioSegmentAt(3_500L))
    }

    @Test
    fun `editor duration subtracts the transition overlap`() {
        val clips = listOf(video("a"), video("b"))

        assertEquals(
            1_800L,
            computeTimelineDurationMs(
                clips = clips,
                transitions = listOf(transition("a", "b", 200L))
            )
        )
    }

    @Test
    fun `incoming source clock maps to project time after the overlap is removed`() {
        val clips = listOf(
            video("a", trimStart = 2_000L, trimEnd = 6_000L),
            video("b", trimStart = 10_000L, trimEnd = 14_000L, duration = 20_000L)
        )
        val plan = ProjectTransitionTimeline.build(clips, listOf(transition("a", "b", 1_000L)))

        assertEquals(4_000L, plan.projectTimeAtSourceTime(clips, clips[1], 11_000L))
    }

    @Test
    fun `photo clips keep their timeline space and do not receive video transitions`() {
        val photo = video("photo", trimEnd = 1_000L).copy(type = ClipType.IMAGE)
        val clips = listOf(video("a"), photo, video("b"))
        val plan = ProjectTransitionTimeline.build(
            clips,
            listOf(transition("a", "photo", 300L), transition("photo", "b", 300L))
        )

        assertEquals(3_000L, plan.durationMs)
        assertSame(photo, plan.entryAt(1_000L)?.clip)
        assertTrue(plan.transitions.isEmpty())
    }

    @Test
    fun `transition duration clamps to half of both short adjacent clips`() {
        val plan = ProjectTransitionTimeline.build(
            listOf(video("a", trimEnd = 300L), video("b", trimEnd = 100L)),
            listOf(transition("a", "b", 1_000L))
        )

        assertEquals(50L, plan.transitions.single().durationMs)
        assertEquals(350L, plan.durationMs)
        assertEquals(250L, plan.transitions.single().startMs)
    }

    @Test
    fun `adjacent transitions leave middle clip windows non-overlapping`() {
        val plan = ProjectTransitionTimeline.build(
            listOf(video("a"), video("b"), video("c")),
            listOf(
                transition("a", "b", 900L),
                transition("b", "c", 900L)
            )
        )

        assertEquals(listOf(500L, 500L), plan.transitions.map { it.durationMs })
        assertEquals(1_000L, plan.entries[1].durationMs)
        assertEquals(1_000L, plan.transitions[0].endMs)
        assertEquals(1_000L, plan.transitions[1].startMs)
        assertEquals(plan.transitions[1].startMs, plan.transitions[0].endMs)
        assertEquals(2_000L, plan.durationMs)
        assertEquals(listOf(0L to 750L, 750L to 1_250L, 1_250L to 2_000L),
            plan.audioSegments.map { it.timelineStartMs to it.timelineEndMs })
        assertTrue(plan.audioSegments.zipWithNext().all { (a, b) -> a.timelineEndMs == b.timelineStartMs })
    }

    @Test
    fun `unknown and non adjacent transitions are ignored`() {
        val plan = ProjectTransitionTimeline.build(
            listOf(video("a"), video("b"), video("c")),
            listOf(
                transition("missing", "b", 100L),
                transition("a", "c", 100L),
                transition("b", "missing", 100L)
            )
        )

        assertTrue(plan.transitions.isEmpty())
        assertEquals(3_000L, plan.durationMs)
    }

    @Test
    fun `normalization drops transitions no longer joining adjacent main videos`() {
        val a = video("a")
        val photo = video("photo").copy(type = ClipType.IMAGE)
        val b = video("b")
        val c = video("c")
        val stale = listOf(transition("a", "b", 200L), transition("b", "c", 200L))

        assertEquals(listOf(transition("b", "c", 200L)),
            ProjectTransitionTimeline.normalizeTransitions(listOf(a, photo, b, c), stale))
    }

    @Test
    fun `transition and clip ranges use exact half open endpoints`() {
        val plan = ProjectTransitionTimeline.build(
            listOf(video("a"), video("b")),
            listOf(transition("a", "b", 200L))
        )
        val t = plan.transitions.single()

        assertNull(plan.transitionAt(t.startMs - 1L))
        assertSame(t, plan.transitionAt(t.startMs))
        assertNull(plan.transitionAt(t.endMs))
        assertSame(plan.entries[0], plan.entryAt(t.startMs))
        assertSame(plan.entries[1], plan.entryAt(t.endMs))
        assertNull(plan.sourceTimeAt(plan.durationMs))
    }
}
