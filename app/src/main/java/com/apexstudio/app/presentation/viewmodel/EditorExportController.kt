package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.photoedit.PhotoEditRenderer
import com.apexstudio.app.data.photoedit.PhotoVideoEncoder
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File


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
    // Photo clip (PR F): bake the edits into a bitmap, encode a still
    // video, then run the normal export on it.
    if (selected?.type == com.apexstudio.app.domain.model.ClipType.IMAGE) {
        startPhotoExport(selected, resolution, fps, quality)
        return
    }
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
            transitionDurationMs = s.project?.lastTransitionDurationMs ?: 500L
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
    quality: String
) {
    val engine = exportEngine ?: return
    val ctx = context ?: return
    _export.update { it.copy(isExporting = true, progress = 0f, error = null, outputUri = null) }

    viewModelScope.launch(Dispatchers.IO) {
        var tempVideo: File? = null
        try {
            val bitmap = PhotoEditRenderer.renderEdited(ctx, clip.uri, clip.photoEdit, maxDim = 2048)
                ?: throw IllegalStateException("Could not decode photo: ${clip.name}")
            val trimmedMs = if (clip.trimEndMs > clip.trimStartMs) {
                clip.trimEndMs - clip.trimStartMs
            } else {
                clip.durationMs
            }
            tempVideo = PhotoVideoEncoder.encodeStillImage(ctx, bitmap, trimmedMs)
                ?: throw IllegalStateException("Could not encode photo video")
            try {
                bitmap.recycle()
            } catch (_: Exception) {
            }

            val s = _state.value
            val stickers = (s.project?.stickers ?: emptyList()) + clip.stickers
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
            val transitionType = s.project?.lastTransitionType?.let { typeStr ->
                com.apexstudio.app.data.gl.TransitionEngine.Companion.TransitionType.fromId(typeStr)
            }
            engine.startExport(
                android.net.Uri.fromFile(tempVideo).toString(),
                ExportEngine.ExportConfig(
                    resolution = resolution,
                    fps = fps,
                    quality = quality,
                    // Photo edits are baked into the frames already.
                    filterPreset = null,
                    filterIntensity = 0f,
                    adjustments = VideoAdjustments(),
                    cropRect = null,
                    clipSpeed = clip.speedMultiplier,
                    keyframes = clip.keyframes,
                    fxPreset = com.apexstudio.app.data.fx.FxPreset.byId(s.activeFxId),
                    fxIntensity = s.fxIntensity,
                    textOverlays = clip.textOverlays,
                    stickers = stickers,
                    // Trim is baked into the still-video duration.
                    trimStartMs = 0L,
                    trimEndMs = 0L,
                    // Stills carry no audio.
                    pitchSemitones = 0f,
                    volume = 1f,
                    pipOverlays = pipOverlays,
                    transitionType = transitionType,
                    transitionDurationMs = s.project?.lastTransitionDurationMs ?: 500L
                )
            )
            // Wait for the export to terminate, then clean up the temp
            // video (30 min cap so a stuck export can't leak the file).
            val deadline = android.os.SystemClock.elapsedRealtime() + 30L * 60L * 1000L
            var sawActive = false
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                val st = engine.exportState.value
                if (st.isExporting) sawActive = true
                else if (sawActive) break
                delay(500)
            }
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
