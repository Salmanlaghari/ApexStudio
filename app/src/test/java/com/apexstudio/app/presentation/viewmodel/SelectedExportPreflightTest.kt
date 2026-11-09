package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.EditorState
import org.junit.Assert.*
import org.junit.Test

class SelectedExportPreflightTest {
    private fun video(id: String, type: ClipType = ClipType.VIDEO, track: Int = 0) = MediaClip(
        id = id, name = id, uri = "file:///$id.mp4", durationMs = 4000L, trimEndMs = 4000L, type = type, trackIndex = track)
    private fun state(extra: List<MediaClip> = emptyList()) = EditorState(project = Project("p", "P", 8000L,
        clips = listOf(video("first"), video("second")) + extra), selectedClipId = "second")
    @Test fun plainLaterClipCanExportWithoutProjectTimeLayers() {
        assertNull(selectedVideoExportUnsupportedReason(state()))
    }
    @Test fun laterClipWithVisiblePipIsBlockedRatherThanMispositioned() {
        val s = state(listOf(video("pip", ClipType.OVERLAY, 1)))
        assertTrue(selectedVideoExportUnsupportedReason(s)!!.contains("rebase"))
        assertNull(selectedVideoExportUnsupportedReason(s.copy(hiddenVideoLayers = setOf(1))))
        assertNull(selectedVideoExportUnsupportedReason(s.copy(selectedClipId = "first")))
    }
    @Test fun hiddenMainTrackAndLegacyAudioCannotBeSilentlyExported() {
        assertTrue(selectedVideoExportUnsupportedReason(state().copy(hiddenVideoLayers = setOf(0)))!!.contains("hidden"))
        for (type in listOf(ClipType.AUDIO, ClipType.SFX)) {
            assertTrue(selectedVideoExportUnsupportedReason(state(listOf(video("audio", type))))!!.contains("Legacy audio"))
        }
    }
    @Test fun enabledMusicIsBlockedForLaterClipButMutedMusicIsNot() {
        val track = AudioTrack(id = "music", name = "Music", uri = "file:///music.m4a", trimEndMs = 8000L)
        val s = state().let { it.copy(project = it.project!!.copy(audioTracks = listOf(track))) }
        assertTrue(selectedVideoExportUnsupportedReason(s)!!.contains("rebase"))
        assertNull(selectedVideoExportUnsupportedReason(s.copy(project = s.project!!.copy(audioTracks = listOf(track.copy(isMuted = true))))))
    }
    @Test fun unsupportedVideoTracksAreNotSilentlyOmitted() {
        assertTrue(selectedVideoExportUnsupportedReason(state(listOf(video("extra", track = 2))))!!.contains("outside V1"))
    }

    @Test fun activeFilterUnavailableToExportIsBlockedInsteadOfSilentlyDropped() {
        val reason = selectedVisualEffectUnavailableReason(
            activeFilterId = "missing-lut",
            filterIntensity = 0.7f,
            filterPresetAvailable = false,
            activeFxId = null,
            fxIntensity = 0f
        )

        assertTrue(reason!!.contains("filter is unavailable"))
    }

    @Test fun activeUnknownFxIsBlockedButDisabledOrResolvedLooksRemainAllowed() {
        val missingFx = selectedVisualEffectUnavailableReason(
            activeFilterId = null,
            filterIntensity = 0f,
            filterPresetAvailable = false,
            activeFxId = "missing-fx",
            fxIntensity = 0.6f
        )
        assertTrue(missingFx!!.contains("FX is unavailable"))

        assertNull(selectedVisualEffectUnavailableReason(
            activeFilterId = "missing-lut",
            filterIntensity = 0f,
            filterPresetAvailable = false,
            activeFxId = "missing-fx",
            fxIntensity = 0f
        ))
        assertNull(selectedVisualEffectUnavailableReason(
            activeFilterId = "resolved-lut",
            filterIntensity = 0.6f,
            filterPresetAvailable = true,
            activeFxId = com.apexstudio.app.data.fx.FxPreset.GLITCH.id,
            fxIntensity = 0.4f
        ))
    }
}
