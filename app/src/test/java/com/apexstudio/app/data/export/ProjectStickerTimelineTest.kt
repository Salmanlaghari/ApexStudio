package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.StickerOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectStickerTimelineTest {
    @Test
    fun `clip stickers use sequential speed adjusted output time while project stickers stay global`() {
        val first = MediaClip(
            id = "first",
            name = "First",
            uri = "file:///first.mp4",
            durationMs = 12_000L,
            trimStartMs = 1_000L,
            trimEndMs = 11_000L,
            type = ClipType.VIDEO,
            trackIndex = 0,
            speedMultiplier = 2f,
            timelineOffsetMs = 99_000L,
        )
        val clipSticker = StickerOverlay(
            id = "clip-sticker",
            name = "Clip star",
            startMs = 2_500L,
            endMs = 6_500L,
            keyframes = KeyframeTrack(listOf(Keyframe.identity(3_000L, id = "clip-key"))),
        )
        val second = MediaClip(
            id = "second",
            name = "Second",
            uri = "file:///second.mp4",
            durationMs = 8_000L,
            trimEndMs = 8_000L,
            type = ClipType.VIDEO,
            trackIndex = 0,
            stickers = listOf(clipSticker),
        )
        val projectSticker = StickerOverlay(id = "project-sticker", name = "Global", startMs = 200L, endMs = 600L)

        val segments = ProjectStickerTimeline.segments(listOf(first, second), listOf(projectSticker))
        val global = segments.single { it.sticker.id == "project-sticker" }
        val attached = segments.single { it.sticker.id == "clip-sticker" }

        assertNull(global.ownerClipId)
        assertEquals(200L, global.sticker.startMs)
        assertEquals(600L, global.sticker.endMs)
        assertEquals("second", attached.ownerClipId)
        assertEquals(7_500L, attached.sticker.startMs)
        assertEquals(11_500L, attached.sticker.endMs)
        assertEquals(8_000L, attached.sticker.keyframes.keyframes.single().timeMs)
    }

    @Test
    fun `unbounded clip sticker runs through its output end and ranges map back to local time`() {
        val first = MediaClip(
            id = "first",
            name = "First",
            uri = "file:///first.mp4",
            durationMs = 10_000L,
            trimEndMs = 10_000L,
            type = ClipType.VIDEO,
            trackIndex = 0,
            speedMultiplier = 2f,
        )
        val sticker = StickerOverlay(id = "unbounded", startMs = 500L, endMs = Long.MAX_VALUE)
        val second = MediaClip(
            id = "second",
            name = "Second",
            uri = "file:///second.mp4",
            durationMs = 6_000L,
            trimEndMs = 6_000L,
            type = ClipType.VIDEO,
            trackIndex = 0,
            stickers = listOf(sticker),
        )
        val clips = listOf(first, second)

        val segment = ProjectStickerTimeline.segments(clips, emptyList()).single()
        assertEquals(5_500L, segment.sticker.startMs)
        assertEquals(11_000L, segment.sticker.endMs)
        assertEquals(ProjectStickerTimeline.Range(1_500L, 3_000L),
            ProjectStickerTimeline.clipOutputRange(clips, "second", 6_500L, 8_000L))
        assertEquals(2_000L, ProjectStickerTimeline.clipOutputTimeAtProjectTime(clips, "second", 7_000L))
        assertNull(ProjectStickerTimeline.clipOutputTimeAtProjectTime(clips, "second", 11_000L))
        assertEquals(6_000L, ProjectStickerTimeline.clipOutputTimeAtProjectTime(
            clips, "second", 11_000L, allowEndBoundary = true,
        ))
        assertEquals(11_000L, ProjectStickerTimeline.projectTimeAtClipOutputTime(clips, "second", 6_000L))
    }
}
