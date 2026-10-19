package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.engine.AudioPlaybackMix
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.export.projectCompositionUnsupportedReason
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.ExportSettings
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.state.AudioStudioState
import com.apexstudio.app.presentation.state.EditorState
import kotlinx.coroutines.flow.update

internal fun projectMainExportClips(state: EditorState): List<MediaClip> =
    state.project?.clips.orEmpty().filter {
        it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE)
    }

internal fun buildProjectExportConfig(
    state: EditorState,
    audio: AudioStudioState,
    settings: ExportSettings
): ExportEngine.ExportConfig = ExportEngine.ExportConfig(
    resolution = settings.resolution,
    fps = settings.frameRate,
    quality = settings.quality.label.lowercase(),
    aspectRatio = settings.aspectRatio,
    bitrateMbps = settings.bitrateMbps,
    pitchSemitones = audio.pitchSemitones,
    volume = if (audio.isMuted) 0f else audio.volume,
    reverbEnabled = audio.reverbEnabled,
    reverbPreset = audio.reverbPreset,
    echoEnabled = audio.echoEnabled,
    bassBoostEnabled = audio.bassBoostEnabled,
    bassBoostStrength = audio.bassBoostStrength,
    musicMixTracks = state.project?.audioTracks.orEmpty().let { tracks ->
        tracks.filter { AudioPlaybackMix.isEnabled(it, tracks) }
    }
)

internal fun wholeProjectExportUnsupportedReason(
    state: EditorState,
    audio: AudioStudioState,
    settings: ExportSettings
): String? = projectVisualScopeUnsupportedReason(state)
    ?: projectCompositionUnsupportedReason(projectMainExportClips(state), buildProjectExportConfig(state, audio, settings))

/** Explicit opt-in; the selected-clip route and its visuals remain unchanged. */
fun EditorViewModel.startWholeProjectExport() {
    if (_export.value.isExporting) return
    val snapshot = _state.value
    val audioSnapshot = _audio.value
    val settings = _export.value.settings
    val reason = wholeProjectExportUnsupportedReason(snapshot, audioSnapshot, settings)
    if (reason != null) {
        _export.update { it.copy(error = reason, outputUri = null, warning = null) }
        return
    }
    val engine = exportEngine ?: return
    val clips = projectMainExportClips(snapshot)
    val config = buildProjectExportConfig(snapshot, audioSnapshot, settings)
    _export.update { it.copy(isExporting = true, progress = 0f, error = null, outputUri = null, warning = null) }
    engine.startProjectExport(clips, config)
}
