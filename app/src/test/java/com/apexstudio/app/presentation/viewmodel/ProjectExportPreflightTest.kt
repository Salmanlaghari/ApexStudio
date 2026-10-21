package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.*
import org.junit.Test

class ProjectExportPreflightTest {
    private val video = MediaClip("v", "Video", "file:///video.mp4", 5000, trimEndMs = 5000)
    private val state = EditorState(project = Project("p", "Project", 5000, clips = listOf(video)))

    @Test fun `plain project passes global visual preflight`() {
        assertNull(projectVisualScopeUnsupportedReason(state))
    }

    @Test fun `missing project fails clearly`() {
        assertNotNull(projectVisualScopeUnsupportedReason(EditorState()))
    }

    @Test fun `legacy audio and sound effect clips are not filtered without a gate`() {
        for ((type, expected) in listOf(
            ClipType.AUDIO to "legacy audio clip",
            ClipType.SFX to "legacy sound-effect clip"
        )) {
            val legacy = video.copy(id = type.name, name = type.name, type = type)
            val edited = state.copy(project = state.project!!.copy(clips = listOf(video, legacy)))
            assertTrue(projectVisualScopeUnsupportedReason(edited)!!.contains(expected))
        }
    }

    @Test fun `global LUT FX and standard grading are supported`() {
        for (edited in listOf(
            state.copy(activeFilterId = "lut"),
            state.copy(activeFxId = "glitch"),
            state.copy(adjustments = VideoAdjustments(brightness = 0.2f))
        )) assertNull(projectVisualScopeUnsupportedReason(edited))
    }

    @Test fun `unsupported AR GPU grading and crop remain fail closed`() {
        for (edited in listOf(
            state.copy(activeArFilterId = "ar"),
            state.copy(activeGpuFilterConfig = state.activeGpuFilterConfig.copy(brightness = 0.1f)),
            state.copy(cropRect = state.cropRect.copy(left = 0.1f))
        )) assertNotNull(projectVisualScopeUnsupportedReason(edited))
    }

    @Test fun `project transitions and stickers cannot be silently dropped`() {
        assertNotNull(projectVisualScopeUnsupportedReason(state.copy(project = state.project!!.copy(
            lastTransitionType = "fade"))))
        assertNotNull(projectVisualScopeUnsupportedReason(state.copy(project = state.project!!.copy(
            stickers = listOf(StickerOverlay())))))
    }

    @Test fun `visible overlay blocks composition but hidden overlay stays hidden`() {
        val overlay = video.copy(id = "pip", type = ClipType.OVERLAY, trackIndex = 1)
        val withOverlay = state.copy(project = state.project!!.copy(clips = listOf(video, overlay)))
        assertNotNull(projectVisualScopeUnsupportedReason(withOverlay))
        assertNull(projectVisualScopeUnsupportedReason(withOverlay.copy(hiddenVideoLayers = setOf(1))))
    }
}
