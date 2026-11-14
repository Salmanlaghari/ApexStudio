package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.data.aireport.AiContentReportTarget
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolsTargetTest {
    private fun clip(id: String, type: ClipType = ClipType.VIDEO, trackIndex: Int = 0) = MediaClip(
        id = id,
        name = "$id.mp4",
        uri = "file:///$id.mp4",
        durationMs = 5_000L,
        trimEndMs = 5_000L,
        type = type,
        trackIndex = trackIndex
    )

    @Test
    fun explicitMainTrackVideoWinsOverThePreviewClip() {
        val selected = clip("selected")
        val preview = clip("preview")
        assertEquals(selected, resolveAiTargetClip(selected, selected.id, preview))
    }

    @Test
    fun explicitPhotoOrOverlayNeverFallsBackToAnotherPreviewVideo() {
        val preview = clip("preview")
        val photo = clip("photo", type = ClipType.IMAGE)
        val overlay = clip("overlay", type = ClipType.OVERLAY, trackIndex = 1)
        assertNull(resolveAiTargetClip(photo, photo.id, preview))
        assertNull(resolveAiTargetClip(overlay, overlay.id, preview))
    }

    @Test
    fun withoutAnExplicitSelectionOnlyTheMainPreviewVideoCanBeTheTarget() {
        val previewVideo = clip("preview")
        val previewPhoto = clip("photo", type = ClipType.IMAGE)
        assertEquals(previewVideo, resolveAiTargetClip(null, null, previewVideo))
        assertNull(resolveAiTargetClip(null, null, previewPhoto))
        assertNull(resolveAiTargetClip(null, "stale-id", previewVideo))
    }

    @Test
    fun secondaryTrackVideoAndOtherProjectCannotPassTheActionGuard() {
        val mainVideo = clip("main")
        val secondaryVideo = clip("secondary", trackIndex = 1)
        assertTrue(isCurrentProjectAiVideoTarget("project-a", "project-a", mainVideo))
        assertFalse(isCurrentProjectAiVideoTarget("project-a", "project-b", mainVideo))
        assertFalse(isCurrentProjectAiVideoTarget("project-a", "project-a", secondaryVideo))
        assertFalse(isCurrentProjectAiVideoTarget("project-a", "project-a", null))
    }

    @Test
    fun reportTargetMustStillMatchItsProjectClipTypeAndOverlay() {
        val video = clip("reported")
        val project = Project(id = "project-a", name = "Edit", durationMs = 5_000L, clips = listOf(video))
        val report = AiContentReportTarget("project-a", "reported", "VIDEO")

        assertTrue(isAiReportTargetCurrent("project-a", project, report))
        assertFalse(isAiReportTargetCurrent("project-b", project, report))
        assertFalse(isAiReportTargetCurrent("project-a", project, report.copy(clipId = "removed")))
        assertFalse(isAiReportTargetCurrent("project-a", project, report.copy(clipType = "IMAGE")))
        assertFalse(isAiReportTargetCurrent("project-a", project, report.copy(textOverlayId = "removed")))
        assertFalse(isAiReportTargetCurrent("project-a", null, report))
        assertFalse(isAiReportTargetCurrent("project-a", project.copy(clips = listOf(clip("reported", type = ClipType.IMAGE))), report.copy(clipType = "IMAGE")))
        assertFalse(isAiReportTargetCurrent("project-a", project.copy(clips = listOf(clip("reported", trackIndex = 1))), report))
    }

    @Test
    fun reportTargetConstructionRejectsNonVideoAndStaleTextOverlaySelections() {
        val video = clip("main-video")
        val project = Project(id = "project-a", name = "Edit", durationMs = 5_000L, clips = listOf(video))

        assertTrue(resolveAiVideoReportTarget("project-a", project, video, null) != null)
        assertNull(resolveAiVideoReportTarget("project-a", project, video, "removed-overlay"))
        assertNull(resolveAiVideoReportTarget("project-a", project, clip("photo", type = ClipType.IMAGE), null))
        assertNull(resolveAiVideoReportTarget("project-b", project, video, null))
    }
}
