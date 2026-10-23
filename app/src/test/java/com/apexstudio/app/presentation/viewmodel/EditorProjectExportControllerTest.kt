package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.presentation.state.AudioStudioState
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.*
import org.junit.Test

class EditorProjectExportControllerTest {
    private val first = MediaClip("first", "First", "file:///first.mp4", 5000, trimEndMs = 5000)
    private val second = first.copy(id = "second", uri = "file:///second.mp4", speedMultiplier = 2f)
    private val state = EditorState(project = Project("p", "Project", 7500, clips = listOf(first, second)))
    private val settings = ExportSettings(resolution = "1080p", frameRate = 30, aspectRatio = "9:16", bitrateMbps = 12)

    @Test fun `project mode is independent of selected clip and preserves timeline order`() {
        assertEquals(listOf(first, second), projectMainExportClips(state.copy(selectedClipId = second.id)))
        assertNull(wholeProjectExportUnsupportedReason(state, AudioStudioState(), settings))
    }

    @Test fun `global audio and output settings reach project config`() {
        val config = buildProjectExportConfig(state, AudioStudioState(volume = 0.4f, pitchSemitones = 3f), settings)
        assertEquals("1080P", config.resolution)
        assertEquals("9:16", config.aspectRatio)
        assertEquals(30, config.fps)
        assertEquals(12, config.bitrateMbps)
        assertEquals(0.4f, config.volume, 0f)
        assertEquals(3f, config.pitchSemitones, 0f)
        assertEquals(0f, buildProjectExportConfig(state, AudioStudioState(isMuted = true), settings).volume, 0f)
    }

    @Test fun `historical 8K export settings are mapped to supported 4K output`() {
        val config = buildProjectExportConfig(
            state,
            AudioStudioState(),
            settings.copy(resolution = "8K Ultra HD")
        )
        assertEquals("4K", config.resolution)
        assertEquals(30, config.fps)
        assertEquals("9:16", config.aspectRatio)
    }

    @Test fun `project music respects mute and solo before engine postmix`() {
        val music = AudioTrack("music", "Music", "file:///music.aac")
        val voice = music.copy(id = "voice", isSolo = true)
        val muted = music.copy(id = "muted", isMuted = true, isSolo = true)
        val edited = state.copy(project = state.project!!.copy(audioTracks = listOf(music, voice, muted)))
        assertEquals(listOf(voice), buildProjectExportConfig(edited, AudioStudioState(), settings).musicMixTracks)
    }

    @Test fun `global filter FX and grading are carried into project config`() {
        val lut = FilterPreset("cinema", "Cinema", "Cinematic", "luts/cinema.cube")
        val edited = state.copy(
            activeFilterId = lut.id,
            filterIntensity = 0.65f,
            activeFxId = FxPreset.GLITCH.id,
            fxIntensity = 0.4f,
            fxSpeed = 1.5f,
            adjustments = VideoAdjustments(brightness = 0.12f, saturation = 1.1f)
        )
        val config = buildProjectExportConfig(edited, AudioStudioState(), settings, lut)
        assertEquals(lut, config.filterPreset)
        assertEquals(0.65f, config.filterIntensity, 0f)
        assertEquals(FxPreset.GLITCH, config.fxPreset)
        assertEquals(0.4f, config.fxIntensity, 0f)
        assertEquals(1.5f, config.fxSpeed, 0f)
        assertEquals(edited.adjustments, config.adjustments)
        assertNull(wholeProjectExportUnsupportedReason(edited, AudioStudioState(), settings, lut))
    }

    @Test fun `unsupported edits block project mode rather than dropping from output`() {
        assertNotNull(wholeProjectExportUnsupportedReason(state.copy(activeFilterId = "cinema"), AudioStudioState(), settings))
        assertNotNull(wholeProjectExportUnsupportedReason(state.copy(activeFxId = "not-a-real-fx"), AudioStudioState(), settings))
        assertNotNull(wholeProjectExportUnsupportedReason(state.copy(activeArFilterId = "festival"), AudioStudioState(), settings))
        assertNotNull(wholeProjectExportUnsupportedReason(state.copy(project = state.project!!.copy(
            clips = listOf(first.copy(type = ClipType.IMAGE), second))), AudioStudioState(), settings))
        assertNotNull(wholeProjectExportUnsupportedReason(state, AudioStudioState(reverbEnabled = true), settings))
    }
}
