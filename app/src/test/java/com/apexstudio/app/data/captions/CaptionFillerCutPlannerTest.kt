package com.apexstudio.app.data.captions

import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionFillerCutPlannerTest {
    private fun clip(id: String, type: ClipType = ClipType.VIDEO, track: Int = 0) = MediaClip(
        id = id,
        name = id,
        uri = "file:///$id.mp4",
        durationMs = 1_000L,
        trimEndMs = 1_000L,
        type = type,
        trackIndex = track
    )

    @Test
    fun `cut crossing a main clip boundary is split into safe per-clip source pieces`() {
        val project = Project(
            id = "p", name = "p", durationMs = 2_000L,
            clips = listOf(clip("first"), clip("second"), clip("overlay", ClipType.AUDIO, track = 2))
        )
        val plan = CaptionFillerCutPlanner.plan(project.clips, listOf(TimelineCutRange(900L, 1_100L)))

        assertEquals(200L, plan.removedDurationMs)
        assertEquals(setOf("first", "second"), plan.keptSegmentsByClipId.keys)
        assertEquals(listOf(0L to 900L), plan.keptSegmentsByClipId.getValue("first").map { it.sourceStartMs to it.sourceEndMs })
        assertEquals(listOf(100L to 1_000L), plan.keptSegmentsByClipId.getValue("second").map { it.sourceStartMs to it.sourceEndMs })
        assertFalse("audio/overlay clips are never source-cut", "overlay" in plan.keptSegmentsByClipId)
    }

    @Test
    fun `disjoint word ranges create multiple surviving pieces without changing their exact cut windows`() {
        val source = clip("source")
        val plan = CaptionFillerCutPlanner.plan(
            listOf(source),
            listOf(TimelineCutRange(100L, 150L), TimelineCutRange(700L, 800L))
        )

        assertEquals(150L, plan.removedDurationMs)
        assertEquals(
            listOf(0L to 100L, 150L to 700L, 800L to 1_000L),
            plan.keptSegmentsByClipId.getValue("source").map { it.sourceStartMs to it.sourceEndMs }
        )
    }

    @Test
    fun `speed-adjusted output cut maps back to source boundaries without restarting clip speed`() {
        val fast = MediaClip(
            id = "fast", name = "fast", uri = "file:///fast.mp4", durationMs = 10_000L,
            trimEndMs = 10_000L, speedMultiplier = 2f
        )
        val plan = CaptionFillerCutPlanner.plan(listOf(fast), listOf(TimelineCutRange(2_500L, 3_000L)))

        assertEquals(500L, plan.removedDurationMs)
        assertEquals(listOf(0L to 5_000L, 6_000L to 10_000L),
            plan.keptSegmentsByClipId.getValue("fast").map { it.sourceStartMs to it.sourceEndMs })
        assertEquals(listOf(2_500L, 2_000L), plan.keptSegmentsByClipId.getValue("fast").map {
            it.sourceClip.copy(trimStartMs = it.sourceStartMs, trimEndMs = it.sourceEndMs).effectiveDurationMs()
        })
    }

    @Test
    fun `overlapping music is not included in main video cuts`() {
        val music = AudioTrack(
            id = "music", name = "music", uri = "file:///music.mp3",
            startTimeMs = 900L, trimStartMs = 0L, trimEndMs = 800L, sourceDurationMs = 800L
        )
        val project = Project(
            id = "p", name = "p", durationMs = 1_000L,
            clips = listOf(clip("video")), audioTracks = listOf(music)
        )
        val plan = CaptionFillerCutPlanner.plan(project, listOf(TimelineCutRange(950L, 1_050L)))

        assertEquals(50L, plan.removedDurationMs)
        assertEquals(listOf(music), project.audioTracks)
        assertTrue(plan.keptSegmentsByClipId.getValue("video").all { it.sourceClip.type == ClipType.VIDEO })
    }
}
