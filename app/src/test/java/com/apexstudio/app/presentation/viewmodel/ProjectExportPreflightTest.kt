package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import com.apexstudio.app.data.filter.OneTapLooks
import com.apexstudio.app.presentation.state.AudioStudioState
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

    @Test fun `persisted Viral HDR is accepted without applying its mirrored LUT twice`() {
        val viral = OneTapLooks.VIRAL_HDR.applyWithIntensity(1f)
        val edited = state.copy(
            project = state.project!!.copy(clips = listOf(video.copy(gpuFilterConfig = viral))),
            activeGpuFilterConfig = viral,
            activeFilterId = "hdr_punch",
            filterIntensity = 0.85f
        )

        assertTrue(OneTapLooks.isValidRenderConfig(viral))
        assertEquals(viral, edited.project!!.clips.single().gpuFilterConfig)
        assertNull(wholeProjectExportUnsupportedReason(
            edited,
            AudioStudioState(),
            ExportSettings(),
            filterPreset = null
        ))
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

    @Test fun `persisted junction blocks export without legacy transition label or false fallback advice`() {
        val saved = state.copy(project = state.project!!.copy(
            transitions = listOf(ClipTransition(fromClipId = "v", toClipId = "next")),
            lastTransitionType = null
        ))
        val reason = projectVisualScopeUnsupportedReason(saved)!!
        assertTrue(reason.contains("Selected-clip export also does not render"))
        assertFalse(reason.contains("Use selected-clip export to keep"))
    }

    @Test fun `visible overlay blocks composition but hidden overlay stays hidden`() {
        val overlay = video.copy(id = "pip", type = ClipType.OVERLAY, trackIndex = 1)
        val withOverlay = state.copy(project = state.project!!.copy(clips = listOf(video, overlay)))
        assertNotNull(projectVisualScopeUnsupportedReason(withOverlay))
        assertNull(projectVisualScopeUnsupportedReason(withOverlay.copy(hiddenVideoLayers = setOf(1))))
    }

    @Test fun `restored non-main photo and video cannot be filtered out before export preflight`() {
        for (type in listOf(ClipType.IMAGE, ClipType.VIDEO)) {
            val other = video.copy(id = "other", type = type, trackIndex = 2)
            val saved = state.copy(project = state.project!!.copy(clips = listOf(video, other)))
            assertTrue(projectVisualScopeUnsupportedReason(saved)!!.contains("cannot be omitted"))
            assertNull(projectVisualScopeUnsupportedReason(saved.copy(hiddenVideoLayers = setOf(2))))
            assertEquals(listOf(video, other), saved.project!!.clips)
        }
    }
}
