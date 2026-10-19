package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.video.VideoUpscaleEngine
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoUpscaleControllerPolicyTest {
    @Test fun acceptsOnlyBoundedLandscapeOrPortraitX3Sources() {
        val landscape = VideoUpscaleEngine.validateSource(640, 360, 30_000)
        assertEquals(1920, landscape.outputWidth)
        assertEquals(1080, landscape.outputHeight)

        val portrait = VideoUpscaleEngine.validateSource(360, 640, 30_000)
        assertEquals(1080, portrait.outputWidth)
        assertEquals(1920, portrait.outputHeight)

        assertInvalid { VideoUpscaleEngine.validateSource(642, 360, 1_000) }
        assertInvalid { VideoUpscaleEngine.validateSource(640, 362, 1_000) }
        assertInvalid { VideoUpscaleEngine.validateSource(640, 360, 30_001) }
        assertInvalid { VideoUpscaleEngine.validateSource(640, 360, 1_000, frameRate = 31) }
        assertInvalid { VideoUpscaleEngine.validateSource(640, 360, 30_000, frameRate = 31) }
        assertInvalid { VideoUpscaleEngine.validateSource(641, 361, 1_000) }
    }

    @Test fun selectedMainVideoCommitRequiresExactProjectAndClipSnapshot() {
        val clip = MediaClip(
            id = "video-1", name = "source.mp4", uri = "content://media/source/1",
            durationMs = 10_000, trimStartMs = 1_000, trimEndMs = 8_000,
            type = ClipType.VIDEO, trackIndex = 0
        )
        assertTrue(videoUpscaleCanCommit("project-1", clip, "project-1", clip.id, clip))
        assertFalse(videoUpscaleCanCommit("project-1", clip, "project-2", clip.id, clip))
        assertFalse(videoUpscaleCanCommit("project-1", clip, "project-1", "another-clip", clip))
        assertFalse(videoUpscaleCanCommit("project-1", clip, "project-1", clip.id, clip.copy(uri = "content://media/replaced")))
        assertFalse(videoUpscaleCanCommit("project-1", clip, "project-1", clip.id, clip.copy(trimStartMs = 0)))
        assertFalse(videoUpscaleCanCommit("project-1", clip, "project-1", clip.id, clip.copy(type = ClipType.IMAGE)))
        assertFalse(videoUpscaleCanCommit("project-1", clip, "project-1", clip.id, clip.copy(trackIndex = 1)))
        assertFalse(videoUpscaleCanCommit("project-1", clip, "project-1", clip.id, null))
    }

    private fun assertInvalid(block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return }
        throw AssertionError("Expected input to exceed the upscale processing policy")
    }
}
