package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.flow.update


fun EditorViewModel.setPlayerError(error: String?) = _state.update { it.copy(playerError = error) }

fun EditorViewModel.setSelectedResolution(res: String) = _state.update { it.copy(selectedResolution = res) }

fun EditorViewModel.toggleFullscreenPreview() = _state.update { it.copy(isFullscreenPreview = !it.isFullscreenPreview) }


fun EditorViewModel.updateExport(upd: (ExportSettings) -> ExportSettings) {
    _export.update { it.copy(settings = upd(it.settings)) }
}


fun EditorViewModel.startExport(
    resolution: String = _export.value.settings.resolution,
    fps: Int = _export.value.settings.frameRate,
    quality: String = _export.value.settings.quality.label.lowercase()
) {
    val s = _state.value
    val selected = s.project?.clips?.firstOrNull { it.id == s.selectedClipId }
        ?: s.project?.clips?.firstOrNull()
    val inputUri = selected?.uri ?: return
    val engine = exportEngine ?: return
    _export.update { it.copy(isExporting = true, progress = 0f) }

    var filterPreset: com.apexstudio.app.data.filter.FilterPreset? = null
    if (s.activeFilterId != null && context != null) {
        try {
            filterPreset = com.apexstudio.app.data.filter.LutFilterEngine(context!!).manifest.filters
                .firstOrNull { it.id == s.activeFilterId }
        } catch (e: Exception) {
            Log.w("EditorViewModel", "Failed to load filter manifest for export", e)
        }
    }
    val fxPreset = com.apexstudio.app.data.fx.FxPreset.byId(s.activeFxId)
    val speed = selected?.speedMultiplier ?: s.playbackSpeed
    val stickers = (s.project?.stickers ?: emptyList()) + (selected?.stickers ?: emptyList())
    val audioSt = _audio.value
    // Picture-in-Picture overlays: composite overlay clips in the export.
    val pipOverlays: List<ExportEngine.PipOverlayConfig> = (s.project?.clips ?: emptyList())
        .filter { it.type == com.apexstudio.app.domain.model.ClipType.OVERLAY }
        .map { ov ->
            ExportEngine.PipOverlayConfig(
                uri = ov.uri,
                offsetMs = ov.timelineOffsetMs,
                trimStartMs = ov.trimStartMs,
                trimEndMs = ov.trimEndMs,
                opacity = ov.keyframes.interpolateAt(ov.timelineOffsetMs).opacity
            )
        }
    // Transition: apply the project's chosen transition type in the export
    // (not just a timeline badge — actually rendered via TransitionGlEffect).
    val transitionType = s.project?.lastTransitionType?.let { typeStr ->
        com.apexstudio.app.data.gl.TransitionEngine.Companion.TransitionType.fromId(typeStr)
    }
    engine.startExport(
        inputUri,
        ExportEngine.ExportConfig(
            resolution = resolution,
            fps = fps,
            quality = quality,
            filterPreset = filterPreset,
            filterIntensity = s.filterIntensity,
            adjustments = s.adjustments,
            fxPreset = fxPreset,
            fxIntensity = s.fxIntensity,
            clipSpeed = speed,
            keyframes = selected?.keyframes ?: KeyframeTrack(),
            cropRect = s.cropRect.takeIf { !it.isFullFrame() },
            textOverlays = selected?.textOverlays ?: emptyList(),
            stickers = stickers,
            trimStartMs = selected?.trimStartMs ?: 0L,
            trimEndMs = selected?.trimEndMs ?: 0L,
            pitchSemitones = audioSt.pitchSemitones,
            // Per-clip Volume tool: the clip's own gain multiplies the
            // global audio volume, so what the user hears in the preview
            // is exactly what bakes into the export.
            volume = (selected?.volume ?: 1f) * (if (audioSt.isMuted) 0f else audioSt.volume),
            reverbEnabled = audioSt.reverbEnabled,
            reverbPreset = audioSt.reverbPreset,
            echoEnabled = audioSt.echoEnabled,
            bassBoostEnabled = audioSt.bassBoostEnabled,
            bassBoostStrength = audioSt.bassBoostStrength,
            pipOverlays = pipOverlays,
            transitionType = transitionType,
            transitionDurationMs = s.project?.lastTransitionDurationMs ?: 500L,
            // Royalty-free music library: mix every unmuted timeline audio
            // track (downloaded Jamendo tracks, built-in tracks, device
            // imports) over the exported audio — see MusicExportMixer.
            // project.audioTracks is the same list the live preview plays,
            // so the export matches what the user hears.
            musicMixTracks = (s.project?.audioTracks ?: emptyList()).filter { !it.isMuted }
        )
    )
}


fun EditorViewModel.setExportProgress(p: Float) = _export.update {
    it.copy(progress = p, isExporting = p < 1f)
}


fun EditorViewModel.setExportOutputUri(uri: String?) = _export.update { it.copy(outputUri = uri) }

fun EditorViewModel.setExportError(error: String?) = _export.update { it.copy(error = error) }


/**
 * Exports the project's audio as a standalone AAC (.m4a) file: every
 * timeline clip's audio plus all unmuted extra audio tracks (music /
 * voiceover), concatenated in timeline order. Reuses the audio pipeline
 * (pitch, volume, speed) from the video export.
 */
fun EditorViewModel.startAudioExport(
    outputFileName: String = "apex_studio_audio.m4a"
) {
    val s = _state.value
    val engine = exportEngine ?: return
    _export.update { it.copy(isExporting = true, progress = 0f) }

    val audioSt = _audio.value
    val clips = s.project?.clips?.filter { it.type == ClipType.VIDEO } ?: emptyList()
    // Every audio source in the project: all video clips in timeline order,
    // then any unmuted extra audio tracks (music / voiceover).
    val sources = buildList {
        clips.forEach { clip ->
            add(
                ExportEngine.AudioExportSource(
                    uri = clip.uri,
                    trimStartMs = clip.trimStartMs,
                    trimEndMs = clip.trimEndMs
                )
            )
        }
        audioSt.tracks.filter { !it.isMuted }.forEach { track ->
            add(
                ExportEngine.AudioExportSource(
                    uri = track.uri,
                    trimStartMs = track.trimStartMs,
                    trimEndMs = track.trimEndMs
                )
            )
        }
    }
    if (sources.isEmpty()) {
        _export.update { it.copy(isExporting = false, progress = 0f) }
        return
    }
    val speed = clips.firstOrNull()?.speedMultiplier ?: s.playbackSpeed
    engine.startAudioExport(
        sources,
        ExportEngine.ExportConfig(
            pitchSemitones = audioSt.pitchSemitones,
            volume = if (audioSt.isMuted) 0f else audioSt.volume,
            clipSpeed = speed
        ),
        outputFileName
    )
}
