package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.VideoDrawingLayer
import com.apexstudio.app.domain.model.VideoDrawingPoint
import com.apexstudio.app.domain.model.VideoDrawingStroke
import com.apexstudio.app.domain.model.timeline.sharedVideoClip
import com.apexstudio.app.domain.model.timeline.syncSharedTimeline
import com.apexstudio.app.domain.model.timeline.updateSharedVideoClip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectDrawingTimelineSyncTest {
    @Test
    fun `legacy drawing deletion refresh clears typed owner layer`() {
        val project = projectWithClipDrawing().syncSharedTimeline()
        assertTrue(project.sharedVideoClip("clip")?.drawingLayer != null)

        val cleared = project.copy(videoDrawings = emptyList())
            .syncSharedTimeline(refreshLegacyDrawingClipIds = setOf("clip"))

        assertTrue(cleared.sharedVideoClip("clip")?.drawingLayer == null)
        assertTrue(cleared.videoDrawings.none { it.clipId == "clip" })
    }

    @Test
    fun `typed drawing deletion removes legacy mirror and remains deleted`() {
        val project = projectWithClipDrawing().syncSharedTimeline()

        val cleared = project.updateSharedVideoClip("clip") { it.copy(drawingLayer = null) }
            .syncSharedTimeline()

        assertTrue(cleared.videoDrawings.none { it.clipId == "clip" })
        assertTrue(cleared.sharedVideoClip("clip")?.drawingLayer == null)
    }

    @Test
    fun `deleting a clip removes its legacy drawing instead of persisting an orphan`() {
        val project = projectWithClipDrawing().syncSharedTimeline()

        val deleted = project.copy(clips = emptyList()).syncSharedTimeline()

        assertTrue(deleted.videoDrawings.isEmpty())
        assertFalse(deleted.timeline.tracks.any { track ->
            track is com.apexstudio.app.domain.model.timeline.VideoTrack && track.clips.any { it.id == "clip" }
        })
    }

    private fun projectWithClipDrawing() = Project(
        id = "drawing-project",
        name = "Drawing project",
        durationMs = 1_000L,
        clips = listOf(MediaClip("clip", "Clip", "file:///clip.mp4", 1_000L, trimEndMs = 1_000L)),
        videoDrawings = listOf(
            VideoDrawingLayer(
                id = "clip-drawing",
                clipId = "clip",
                strokes = listOf(
                    VideoDrawingStroke(points = listOf(VideoDrawingPoint(.2f, .3f)))
                )
            )
        )
    )
}
