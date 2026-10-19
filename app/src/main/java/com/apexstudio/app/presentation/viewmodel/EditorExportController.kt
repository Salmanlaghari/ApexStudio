package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.photoedit.PhotoEditRenderer
import com.apexstudio.app.data.photoedit.PhotoVideoEncoder
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File


fun EditorViewModel.setPlayerError(error: String?) = _state.update { it.copy(playerError = error) }

fun EditorViewModel.setSelectedResolution(res: String) = _state.update { it.copy(selectedResolution = res) }

fun EditorViewModel.toggleFullscreenPreview() = _state.update { it.copy(isFullscreenPreview = !it.isFullscreenPreview) }


fun EditorViewModel.updateExport(upd: (ExportSettings) -> ExportSettings) {
    _export.update { it.copy(settings = upd(it.settings)) }
}

/** Canvas selection is not an export source: an overlay remains a composited layer. */
internal fun resolveExportBaseClip(state: EditorState): MediaClip? {
    val mainClips = state.project?.clips?.filter {
        it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE)
    } ?: return null
    return mainClips.firstOrNull { it.id == state.selectedClipId } ?: mainClips.firstOrNull()
}

fun EditorViewModel.startExport(
    resolution: String = _export.value.settings.resolution,
    fps: Int = _export.value.settings.frameRate,
    quality: String = _export.value.settings.quality.label.lowercase(),
    // Pro Phase 1: aspect ratio + bitrate flow from ExportSettings
    aspectRatio: String = _export.value.settings.aspectRatio,
    bitrateMbps: Int = _export.value.settings.bitrateMbps
) {
    if (_export.value.isExporting) return
    val s = _state.value
    val selected = resolveExportBaseClip(s)
    // Photo clip (PR F): bake the edits into a bitmap, encode a still
    // video, then run the normal export on it.
    if (selected?.type == com.apexstudio.app.domain.model.ClipType.IMAGE) {
        startPhotoExport(selected, resolution, fps, quality, aspectRatio, bitrateMbps)
        return
    }
    val inputUri = selected?.uri ?: return
    val engine = exportEngine ?: return
    _export.update { it.copy(isExporting = true, progress = 0f, error = null, outputUri = null) }

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
    // Hidden video layers are skipped.
    val pipOverlays: List<ExportEngine.PipOverlayConfig> = (s.project?.clips ?: emptyList())
        .filter { it.type == com.apexstudio.app.domain.model.ClipType.OVERLAY &&
                  it.trackIndex !in s.hiddenVideoLayers }
        .map { ov ->
            ExportEngine.PipOverlayConfig(
                uri = ov.uri,
                offsetMs = ov.timelineOffsetMs,
                trimStartMs = ov.trimStartMs,
                trimEndMs = ov.trimEndMs,
                speedPoints = ov.speedPoints,
                // The overlay's canvas transform — preview and export
                // resolve the same numbers against the frame geometry.
                widthFraction = ov.pipScale,
                centerX = ov.pipX,
                centerY = ov.pipY,
                rotationDeg = ov.pipRotationDeg,
                // BASE opacity only: the effect multiplies the
                // keyframe-interpolated opacity per frame.
                opacity = ov.pipOpacity.coerceIn(0f, 1f),
                // Shape mask (Mask tool) on the overlay clip.
                mask = ov.mask,
                // Properties panel + intrinsic aspect + keyframes.
                aspect = ov.overlayAspect,
                blendMode = ov.overlayBlend.glId,
                borderWidth = ov.overlayBorderWidth,
                borderColor = ov.overlayBorderColor,
                shadow = ov.overlayShadow,
                flipH = ov.overlayFlipH,
                flipV = ov.overlayFlipV,
                keyframes = ov.keyframes.keyframes,
                videoCrop = ov.videoCrop
            )
        }
    // Transition: apply the project's chosen transition type in the export
    // (not just a timeline badge — actually rendered via TransitionGlEffect).
    // Phase 4: gl-transitions shaders ride as transitionGlId; the legacy
    // TransitionType path is skipped for them (adapter takes precedence).
    val lastTransitionId = s.project?.lastTransitionType
    val transitionGlId = lastTransitionId
        ?.takeIf { it.startsWith(com.apexstudio.app.data.gl.GlTransitionAdapterEffect.ID_PREFIX) }
        ?.removePrefix(com.apexstudio.app.data.gl.GlTransitionAdapterEffect.ID_PREFIX)
    val transitionType = lastTransitionId?.takeIf { transitionGlId == null }?.let { typeStr ->
        com.apexstudio.app.data.gl.TransitionEngine.Companion.TransitionType.fromId(typeStr)
    }
    engine.startExport(
        inputUri,
        ExportEngine.ExportConfig(
            resolution = resolution,
            fps = fps,
            quality = quality,
            aspectRatio = aspectRatio,
            bitrateMbps = bitrateMbps,
            filterPreset = filterPreset,
            filterIntensity = s.filterIntensity,
            adjustments = s.adjustments,
            videoCrop = selected?.videoCrop,
            faceCropConfig = com.apexstudio.app.data.effect.FaceCropRenderConfig.forMainVideo(selected),
            fxPreset = fxPreset,
            fxIntensity = s.fxIntensity,
            fxSpeed = s.fxSpeed,
            // AR Face parity: bake the active AR card's graded look +
            // festival banner text so the export matches the preview.
            arFilterId = s.activeArFilterId,
            arFilterIntensity = s.arFilterIntensity,
            arFilterCustomText = s.arFilterCustomText,
            clipSpeed = speed,
            // Variable speed curve (PR-E): the export follows the
            // curve; constant [clipSpeed] is the no-curve fallback.
            speedPoints = selected?.speedPoints ?: emptyList(),
            keyframes = selected?.keyframes ?: KeyframeTrack(),
            cropRect = s.cropRect.takeIf { !it.isFullFrame() },
            // Shape mask (Mask tool) on the main clip.
            mask = selected?.mask,
            textOverlays = selected?.textOverlays ?: emptyList(),
            stickers = stickers,
            trimStartMs = selected?.trimStartMs ?: 0L,
            trimEndMs = selected?.trimEndMs ?: 0L,
            pitchSemitones = audioSt.pitchSemitones,
            // Per-clip Volume tool: the clip's own gain multiplies the
            // global audio volume, so what the user hears in the preview
            // is exactly what bakes into the export.
            volume = (selected?.volume ?: 1f) * (if (audioSt.isMuted) 0f else audioSt.volume),
            // Clean Voice: the selected clip's denoise setting rides
            // into the export with the same processor the preview uses.
            denoiseEnabled = selected?.denoiseEnabled ?: false,
            denoiseStrength = selected?.denoiseStrength
                ?: com.apexstudio.app.domain.model.DenoiseStrength.MEDIUM,
            reverbEnabled = audioSt.reverbEnabled,
            reverbPreset = audioSt.reverbPreset,
            echoEnabled = audioSt.echoEnabled,
            bassBoostEnabled = audioSt.bassBoostEnabled,
            bassBoostStrength = audioSt.bassBoostStrength,
            overlayAudioSources = buildOverlayAudioExportSources(
                s.project?.clips.orEmpty(), s.hiddenVideoLayers
            ),
            pipOverlays = pipOverlays,
            transitionType = transitionType,
            transitionGlId = transitionGlId,
            transitionDurationMs = s.project?.lastTransitionDurationMs ?: 500L,
            // Royalty-free music library: mix every unmuted timeline audio
            // track (downloaded Jamendo tracks, built-in tracks, device
            // imports) over the exported audio — see MusicExportMixer.
            // project.audioTracks is the same list the live preview plays,
            // so the export matches what the user hears.
            musicMixTracks = (s.project?.audioTracks ?: emptyList()).let { tracks ->
                tracks.filter { com.apexstudio.app.data.engine.AudioPlaybackMix.isEnabled(it, tracks) }
            }
        )
    )
}


fun EditorViewModel.setExportProgress(p: Float) = _export.update {
    it.copy(progress = p, isExporting = p < 1f)
}


fun EditorViewModel.setExportOutputUri(uri: String?) = _export.update { it.copy(outputUri = uri) }

fun EditorViewModel.setExportError(error: String?) = _export.update { it.copy(error = error) }


/** PiP audio is mixed once, independently of the selected V1 clip and added music. */
internal fun buildOverlayAudioExportSources(
    clips: List<MediaClip>,
    hiddenVideoLayers: Set<Int> = emptySet()
): List<ExportEngine.AudioExportSource> = buildAudioExportSources(
    clips.filter { it.type == ClipType.OVERLAY }, emptyList(), hiddenVideoLayers
)

/**
 * Maps canonical project audio to the standalone export timeline. Main video
 * clip audio follows editor clip order; video-backed overlay clips and extra
 * tracks retain their absolute timeline placement. Media3 mixes the resulting
 * parallel source sequences.
 */
internal fun buildAudioExportSources(
    clips: List<MediaClip>,
    audioTracks: List<AudioTrack>,
    hiddenVideoLayers: Set<Int> = emptySet()
): List<ExportEngine.AudioExportSource> = buildList {
    var clipTimelinePositionMs = 0L
    clips.forEach { clip ->
        val mainVisual = clip.trackIndex == 0 &&
            (clip.type == ClipType.VIDEO || clip.type == ClipType.IMAGE)
        val audibleOverlay = clip.type == ClipType.OVERLAY &&
            clip.trackIndex !in hiddenVideoLayers && clip.overlayVolume.isFinite() &&
            clip.overlayVolume > 0f
        if (clip.type == ClipType.VIDEO || audibleOverlay) add(
            ExportEngine.AudioExportSource(
                uri = clip.uri,
                trimStartMs = clip.trimStartMs,
                trimEndMs = clip.trimEndMs,
                // Overlay audio is independent of V1 and follows the same
                // project-time offset as its visual layer.
                timelinePositionMs = if (clip.type == ClipType.OVERLAY || clip.trackIndex != 0) {
                    clip.timelineOffsetMs.coerceAtLeast(0L)
                } else {
                    clipTimelinePositionMs
                },
                speedPoints = clip.speedPoints,
                // Visual overlays currently use curves or native 1x playback,
                // not the V1-only legacy constant speed multiplier.
                constantSpeedOverride = if (audibleOverlay) 1f else clip.speedMultiplier,
                sourceGain = if (audibleOverlay) clip.overlayVolume else clip.volume,
                denoiseEnabled = clip.denoiseEnabled,
                denoiseStrength = clip.denoiseStrength
            )
        )
        if (mainVisual) {
            // Still images consume V1 time even though they have no audio.
            // Absolute overlay placement must never advance the V1 cursor.
            val durationMs = clip.effectiveDurationMs().coerceAtLeast(0L)
            clipTimelinePositionMs = if (clipTimelinePositionMs > Long.MAX_VALUE - durationMs) {
                Long.MAX_VALUE
            } else {
                clipTimelinePositionMs + durationMs
            }
        }
    }
    audioTracks.filter {
        com.apexstudio.app.data.engine.AudioPlaybackMix.isEnabled(it, audioTracks)
    }.forEach { track ->
        add(
            ExportEngine.AudioExportSource(
                uri = track.uri,
                trimStartMs = track.trimStartMs,
                trimEndMs = track.trimEndMs,
                timelinePositionMs = track.startTimeMs.coerceAtLeast(0L),
                // Music and voiceover remain at their native playback rate.
                constantSpeedOverride = 1f,
                // Track gain/keyframes are absolute and applied by the final
                // sample-accurate processor; sourceGain remains unity to avoid
                // multiplying the scalar volume twice or distorting keyframes.
                sourceGain = 1f,
                trackVolume = track.volume,
                trackFadeInMs = track.fadeInMs,
                trackFadeOutMs = track.fadeOutMs,
                trackVolumeKeyframes = track.volumeKeyframes
            )
        )
    }
}

/**
 * Exports a standalone AAC timeline mix. Main video clip audio is placed in
 * editor clip order, overlay video audio and unmuted canonical project audio
 * tracks use their timeline offsets; each source keeps its own trim/speed and
 * standalone tracks retain their fade and absolute volume automation settings.
 */
fun EditorViewModel.startAudioExport(
    outputFileName: String = "apex_studio_audio.m4a"
) {
    if (_export.value.isExporting) return
    val s = _state.value
    val engine = exportEngine ?: return
    _export.update { it.copy(isExporting = true, progress = 0f, error = null, outputUri = null) }

    val audioSt = _audio.value
    // project.audioTracks is canonical; audio-studio's cached track list can lag.
    val project = s.project
    val sources = buildAudioExportSources(
        project?.clips.orEmpty(),
        project?.audioTracks.orEmpty(),
        s.hiddenVideoLayers
    )
    if (sources.isEmpty()) {
        _export.update { it.copy(isExporting = false, progress = 0f) }
        return
    }
    engine.startAudioExport(
        sources,
        ExportEngine.ExportConfig(
            pitchSemitones = audioSt.pitchSemitones,
            volume = if (audioSt.isMuted) 0f else audioSt.volume,
            // Retain the old global fallback for any source created by a
            // legacy caller without a constantSpeedOverride.
            clipSpeed = s.playbackSpeed
        ),
        outputFileName
    )
}

/**
 * Export path for IMAGE clips (PR F).
 *
 * A still image cannot go through Media3 Transformer directly, so:
 *  1. Render the clip's [PhotoEditSettings] (crop / adjust / LUT /
 *     rotate+flip) into a bitmap with [PhotoEditRenderer] — the exact
 *     same pipeline the live preview uses, so export is WYSIWYG.
 *  2. Encode the bitmap as a short H.264 still video with
 *     [PhotoVideoEncoder] (clip duration, honouring trim).
 *  3. Run the standard Transformer export on that video so text
 *     overlays, stickers, FX, transitions and PiP still apply.
 * The temp video is deleted once the export terminates.
 */
fun EditorViewModel.startPhotoExport(
    clip: MediaClip,
    resolution: String,
    fps: Int,
    quality: String,
    aspectRatio: String = _export.value.settings.aspectRatio,
    bitrateMbps: Int = _export.value.settings.bitrateMbps
) {
    if (_export.value.isExporting) return
    val engine = exportEngine ?: return
    // Derive the photo still-video resolution from the user-selected export
    // quality. Capped at 4K: 8K intermediates (7680px, ~600MB transient)
    // risk OOM and exceed MediaCodec H.264 limits on most devices.
    val photoMaxDim = when {
        resolution.contains("4320") || resolution.contains("8k", ignoreCase = true) -> 3840
        resolution.contains("4k", ignoreCase = true) || resolution.contains("2160") -> 3840
        resolution.contains("1440") -> 2560
        else -> 1920 // 1080p and below
    }
    val ctx = context ?: return
    // Freeze project layers and settings before the asynchronous photo encoding.
    val snapshot = _state.value
    val config = buildPhotoExportConfig(snapshot, clip, resolution, fps, quality, aspectRatio, bitrateMbps)
    _export.update { it.copy(isExporting = true, progress = 0f, error = null, outputUri = null) }

    viewModelScope.launch(Dispatchers.IO) {
        var tempVideo: File? = null
        try {
            val trimmedMs = photoExportSourceDurationMs(clip)
            val bitmap = PhotoEditRenderer.renderEdited(ctx, clip.uri, clip.photoEdit, maxDim = photoMaxDim)
                ?: throw IllegalStateException("Could not decode photo: ${clip.name}")
            // Phase 4: Ken Burns motion bakes into the still-video when enabled.
            try {
                tempVideo = PhotoVideoEncoder.encodeStillImage(
                    ctx, bitmap, trimmedMs, kenBurns = clip.photoEdit.kenBurns
                ) ?: throw IllegalStateException("Could not encode photo video")
            } finally {
                bitmap.recycle()
            }

            val previousEngineState = engine.exportState.value
            engine.startExport(android.net.Uri.fromFile(tempVideo).toString(), config)
            // Wait for the export to terminate with a proper timeout (30 min cap
            // so a stuck export can't leak the file or the coroutine).
            // Ignore the preceding export's completion: startExport dispatches
            // asynchronously, so its old output/error can still be visible here.
            val completed = kotlinx.coroutines.withTimeoutOrNull(30L * 60L * 1000L) {
                engine.exportState.first { current ->
                    isNewPhotoExportTerminal(previousEngineState, current)
                }
                true
            }
            if (completed == null) {
                Log.w("EditorViewModel", "Photo export timed out after 30 min; cancelling")
                kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
                    engine.cancelExport()
                }
                throw IllegalStateException("Photo export timed out")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) {
                engine.cancelExport()
            }
            throw cancelled
        } catch (e: Exception) {
            Log.e("EditorViewModel", "Photo export failed", e)
            _export.update { it.copy(isExporting = false, error = e.message) }
        } finally {
            try {
                tempVideo?.delete()
            } catch (_: Exception) {
            }
        }
    }
}

internal fun isNewPhotoExportTerminal(
    previous: ExportEngine.ExportProgressState,
    current: ExportEngine.ExportProgressState
): Boolean = current !== previous && !current.isExporting

/** Retained source time before the IMAGE variable-speed export processor. */
internal fun photoExportSourceDurationMs(clip: MediaClip): Long {
    require(clip.durationMs > 0L) { "Photo duration must be positive" }
    val start = clip.trimStartMs.coerceAtLeast(0L)
    val end = when (clip.trimEndMs) {
        0L, Long.MAX_VALUE -> clip.durationMs
        else -> minOf(clip.trimEndMs, clip.durationMs)
    }
    require(end > start) { "Photo trim has no retained frames" }
    return end - start
}

/** Pure snapshot mapping shared by photo export and host regressions. */
internal fun buildPhotoExportConfig(
    s: EditorState,
    clip: MediaClip,
    resolution: String,
    fps: Int,
    quality: String,
    aspectRatio: String,
    bitrateMbps: Int
): ExportEngine.ExportConfig {
    val stickers = (s.project?.stickers ?: emptyList()) + clip.stickers
    val pipOverlays: List<ExportEngine.PipOverlayConfig> = (s.project?.clips ?: emptyList())
        .filter { it.type == com.apexstudio.app.domain.model.ClipType.OVERLAY &&
                  it.trackIndex !in s.hiddenVideoLayers }
        .map { ov ->
            ExportEngine.PipOverlayConfig(
                uri = ov.uri,
                offsetMs = ov.timelineOffsetMs,
                trimStartMs = ov.trimStartMs,
                trimEndMs = ov.trimEndMs,
                speedPoints = ov.speedPoints,
                // The overlay's canvas transform — preview and
                // export resolve the same numbers against the
                // frame geometry.
                widthFraction = ov.pipScale,
                centerX = ov.pipX,
                centerY = ov.pipY,
                rotationDeg = ov.pipRotationDeg,
                // BASE opacity only: the effect multiplies the
                // keyframe-interpolated opacity per frame.
                opacity = ov.pipOpacity.coerceIn(0f, 1f),
                // Shape mask (Mask tool) on the overlay clip.
                mask = ov.mask,
                // Properties panel + intrinsic aspect + keyframes.
                aspect = ov.overlayAspect,
                blendMode = ov.overlayBlend.glId,
                borderWidth = ov.overlayBorderWidth,
                borderColor = ov.overlayBorderColor,
                shadow = ov.overlayShadow,
                flipH = ov.overlayFlipH,
                flipV = ov.overlayFlipV,
                keyframes = ov.keyframes.keyframes,
                videoCrop = ov.videoCrop
            )
        }
    // Phase 4: gl-transitions shaders ride as transitionGlId.
    val lastTransitionIdPhoto = s.project?.lastTransitionType
    val transitionGlIdPhoto = lastTransitionIdPhoto
        ?.takeIf { it.startsWith(com.apexstudio.app.data.gl.GlTransitionAdapterEffect.ID_PREFIX) }
        ?.removePrefix(com.apexstudio.app.data.gl.GlTransitionAdapterEffect.ID_PREFIX)
    val transitionType = lastTransitionIdPhoto?.takeIf { transitionGlIdPhoto == null }?.let { typeStr ->
        com.apexstudio.app.data.gl.TransitionEngine.Companion.TransitionType.fromId(typeStr)
    }
    return ExportEngine.ExportConfig(
        resolution = resolution,
        fps = fps,
        quality = quality,
        aspectRatio = aspectRatio,
        bitrateMbps = bitrateMbps,
        // Photo edits are baked into the frames already.
        filterPreset = null,
        filterIntensity = 0f,
        adjustments = VideoAdjustments(),
        cropRect = null,
        // IMAGE timeline ignores legacy constant speed; retain its curve.
        clipSpeed = 1f,
        speedPoints = clip.speedPoints,
        // Shape mask (Mask tool) on the photo clip.
        mask = clip.mask,
        keyframes = clip.keyframes,
        fxPreset = com.apexstudio.app.data.fx.FxPreset.byId(s.activeFxId),
        fxIntensity = s.fxIntensity,
        fxSpeed = s.fxSpeed,
        // AR Face parity on the photo path too.
        arFilterId = s.activeArFilterId,
        arFilterIntensity = s.arFilterIntensity,
        arFilterCustomText = s.arFilterCustomText,
        textOverlays = clip.textOverlays,
        stickers = stickers,
        // Trim is baked into the still-video duration.
        trimStartMs = 0L,
        trimEndMs = 0L,
        // Stills carry no audio.
        pitchSemitones = 0f,
        volume = 1f,
        pipOverlays = pipOverlays,
        overlayAudioSources = buildOverlayAudioExportSources(
            s.project?.clips.orEmpty(), s.hiddenVideoLayers
        ),
        musicMixTracks = s.project?.audioTracks.orEmpty().let { tracks ->
            tracks.filter { com.apexstudio.app.data.engine.AudioPlaybackMix.isEnabled(it, tracks) }
        },
        transitionType = transitionType,
        transitionGlId = transitionGlIdPhoto,
        transitionDurationMs = s.project?.lastTransitionDurationMs ?: 500L
    )
}
