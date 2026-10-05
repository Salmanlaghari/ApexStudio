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
            volume = if (audioSt.isMuted) 0f else audioSt.volume,
            reverbEnabled = audioSt.reverbEnabled,
            reverbPreset = audioSt.reverbPreset,
            echoEnabled = audioSt.echoEnabled,
            bassBoostEnabled = audioSt.bassBoostEnabled,
            bassBoostStrength = audioSt.bassBoostStrength
        )
    )
}


fun EditorViewModel.setExportProgress(p: Float) = _export.update {
    it.copy(progress = p, isExporting = p < 1f)
}


fun EditorViewModel.setExportOutputUri(uri: String?) = _export.update { it.copy(outputUri = uri) }

fun EditorViewModel.setExportError(error: String?) = _export.update { it.copy(error = error) }
