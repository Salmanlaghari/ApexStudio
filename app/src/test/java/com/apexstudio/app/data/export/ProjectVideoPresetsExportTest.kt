package com.apexstudio.app.data.export

import com.apexstudio.app.data.template.ProjectVideoPresets
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.*
import org.junit.Test

class ProjectVideoPresetsExportTest {
    private val clip = MediaClip("video", "Video", "file:///video.mp4", 10_000L, trimEndMs = 10_000L)

    @Test fun everyBuiltInEffectIsAcceptedPerClipByProjectExport() {
        ProjectVideoPresets.effects.forEach { preset ->
            val clips = listOf(clip.copy(projectVideoEffectId = preset.id), clip.copy(id = "other"))
            assertNull(projectCompositionUnsupportedReason(clips, ExportEngine.ExportConfig()))
        }
    }

    @Test fun unknownEffectCannotBeSilentlyDroppedDuringExport() {
        val reason = projectCompositionUnsupportedReason(listOf(clip.copy(projectVideoEffectId = "unknown")), ExportEngine.ExportConfig())
        assertTrue(reason.orEmpty().contains("unknown project-video effect"))
    }

    @Test fun existingTextCompositionRestrictionIsStillHonest() {
        val titled = clip.copy(textOverlays = listOf(ProjectVideoPresets.textTemplates.first().createTextOverlay()))
        val reason = projectCompositionUnsupportedReason(listOf(titled), ExportEngine.ExportConfig())
        assertTrue(reason.orEmpty().contains("captions or text overlays"))
    }
}
