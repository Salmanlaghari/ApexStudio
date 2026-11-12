package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.engine.AudioPlaybackMix
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.export.EightKExportAvailability
import com.apexstudio.app.data.export.Export8kCapability
import com.apexstudio.app.data.export.ExportVideoEncoderPlan
import com.apexstudio.app.data.export.ExportProgressStage
import com.apexstudio.app.data.export.eightKCapabilityRequestForSettings
import com.apexstudio.app.data.export.projectCompositionUnsupportedReason
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.filter.OneTapLooks
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.ExportResolution
import com.apexstudio.app.domain.model.ExportSettings
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.state.AudioStudioState
import com.apexstudio.app.presentation.state.EditorState
import kotlinx.coroutines.flow.update

import com.apexstudio.app.domain.model.timeline.sharedVideoClip
internal fun projectMainExportClips(state: EditorState): List<MediaClip> =
    state.project?.clips.orEmpty().filter {
        it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE)
    }

internal fun buildProjectExportConfig(
    state: EditorState,
    audio: AudioStudioState,
    settings: ExportSettings,
    filterPreset: FilterPreset? = null,
    includeSharedVideoClips: Boolean = true,
    encoderPlan: ExportVideoEncoderPlan? = null
): ExportEngine.ExportConfig = ExportEngine.ExportConfig(
    resolution = ExportResolution.canonical(settings.resolution),
    fps = settings.frameRate,
    quality = settings.quality.label.lowercase(),
    aspectRatio = settings.aspectRatio,
    bitrateMbps = settings.bitrateMbps,
    videoMimeType = encoderPlan?.mimeType ?: androidx.media3.common.MimeTypes.VIDEO_H264,
    videoEncoderName = encoderPlan?.codecName,
    filterPreset = filterPreset,
    filterIntensity = state.filterIntensity,
    signature = state.project?.signature,
    clipTransitions = state.project?.transitions.orEmpty(),
    videoDrawings = state.project?.videoDrawings.orEmpty(),
    sharedVideoClips = if (includeSharedVideoClips) {
        state.project?.let { project ->
            project.clips.mapNotNull { clip ->
                project.sharedVideoClip(clip.id)?.let { clip.id to it }
            }.toMap()
        }.orEmpty()
    } else emptyMap(),
    adjustments = state.adjustments,
    fxPreset = FxPreset.byId(state.activeFxId),
    downloadedFxEffect = com.apexstudio.app.data.fx.DownloadedEffectRepository.find(state.activeFxId),
    fxIntensity = state.fxIntensity,
    fxSpeed = state.fxSpeed,
    pitchSemitones = audio.pitchSemitones,
    volume = if (audio.isMuted) 0f else audio.volume,
    globalAudioVolume = if (audio.isMuted) 0f else audio.volume,
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
    settings: ExportSettings,
    filterPreset: FilterPreset? = null,
    eightKAvailability: EightKExportAvailability? = null
): String? {
    if (ExportResolution.canonical(settings.resolution) == "8K") {
        val settingsReason = videoExportSettingsUnsupportedReason(
            "8K", settings.frameRate, settings.quality.label.lowercase(),
            settings.aspectRatio, settings.bitrateMbps, eightKAvailability
        )
        if (settingsReason != null) return settingsReason
    }
    val activeFilterMirrorsViralHdr =
        OneTapLooks.isValidRenderConfig(state.activeGpuFilterConfig) &&
            state.activeFilterId == state.activeGpuFilterConfig.filterPresetId
    return projectVisualScopeUnsupportedReason(state)
    ?: if (state.activeFilterId != null && !activeFilterMirrorsViralHdr && filterPreset == null) {
        "The selected global filter is unavailable for export. Reapply the filter or use selected-clip export."
    } else if (state.activeFxId != null && FxPreset.byId(state.activeFxId) == null &&
        com.apexstudio.app.data.fx.DownloadedEffectRepository.find(state.activeFxId) == null
    ) {
        "The selected global FX is unavailable for export. Reapply the effect or use selected-clip export."
    } else {
        projectCompositionUnsupportedReason(
            projectMainExportClips(state),
            buildProjectExportConfig(
                state, audio, settings, filterPreset, includeSharedVideoClips = false,
                encoderPlan = (eightKAvailability as? EightKExportAvailability.Supported)?.plan
            )
        )
    }
}

/** Explicit opt-in; the selected-clip route and its visuals remain unchanged. */
fun EditorViewModel.startWholeProjectExport() {
    if (_export.value.isExporting) return
    val snapshot = _state.value
    val audioSnapshot = _audio.value
    val settings = _export.value.settings
    val eightKRequest = eightKCapabilityRequestForSettings(
        settings.aspectRatio, settings.frameRate, settings.quality.label.lowercase(), settings.bitrateMbps
    )
    val eightKAvailability = if (ExportResolution.canonical(settings.resolution) == "8K") {
        Export8kCapability.cached(eightKRequest)
    } else null
    val activeFilterMirrorsViralHdr =
        OneTapLooks.isValidRenderConfig(snapshot.activeGpuFilterConfig) &&
            snapshot.activeFilterId == snapshot.activeGpuFilterConfig.filterPresetId
    val filterPreset = if (!activeFilterMirrorsViralHdr && snapshot.activeFilterId != null && context != null) {
        try {
            com.apexstudio.app.data.filter.LutFilterEngine(context).manifest.filters
                .firstOrNull { it.id == snapshot.activeFilterId }
        } catch (_: Exception) {
            null
        }
    } else null
    val reason = wholeProjectExportUnsupportedReason(
        snapshot, audioSnapshot, settings, filterPreset, eightKAvailability
    )
    if (reason != null) {
        _export.update { it.copy(error = reason, outputUri = null, warning = null) }
        return
    }
    val encoderPlan = (eightKAvailability as? EightKExportAvailability.Supported)?.plan
    if (ExportResolution.canonical(settings.resolution) == "8K") {
        val memoryFailure = if (context == null) {
            "Device memory status could not be verified for 8K export."
        } else {
            Export8kCapability.currentMemoryFailure(context)
        }
        if (memoryFailure != null) {
            _export.update { it.copy(error = memoryFailure, outputUri = null, warning = null) }
            return
        }
    }
    val engine = exportEngine ?: run {
        _export.update { it.copy(error = "Export engine is not ready.", outputUri = null) }
        return
    }
    val clips = projectMainExportClips(snapshot)
    val config = buildProjectExportConfig(snapshot, audioSnapshot, settings, filterPreset, encoderPlan = encoderPlan)
    _export.update { it.copy(isExporting = true, progress = 0f, stage = ExportProgressStage.PREPARING, error = null, outputUri = null, warning = null) }
    engine.startProjectExport(clips, config)
}
