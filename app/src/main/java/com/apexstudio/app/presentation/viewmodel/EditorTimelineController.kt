package com.apexstudio.app.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


fun EditorViewModel.togglePlay() = _state.update { it.copy(isPlaying = !it.isPlaying) }

fun EditorViewModel.setPlaying(v: Boolean) = _state.update { it.copy(isPlaying = v) }

fun EditorViewModel.seekTo(ms: Long) = _state.update {
    val clamped = ms.coerceIn(0, it.durationMs)
    it.copy(currentTimeMs = clamped, playerPositionMs = clamped)
}

fun EditorViewModel.stepFrame(forward: Boolean) = _state.update {
    val step = 33L
    val next = if (forward) it.currentTimeMs + step else (it.currentTimeMs - step).coerceAtLeast(0)
    it.copy(currentTimeMs = next, playerPositionMs = next)
}

fun EditorViewModel.setZoom(z: Float) = _state.update { it.copy(zoomLevel = z.coerceIn(0.2f, 4f)) }

fun EditorViewModel.multiplyZoom(factor: Float) = _state.update {
    val current = it.zoomLevel
    val next = (current * factor).coerceIn(0.2f, 4f)
    it.copy(zoomLevel = next)
}

fun EditorViewModel.fitTimelineToScreen(viewportPx: Float = 1000f) = _state.update {
    val totalMs = it.durationMs.coerceAtLeast(5000L)
    val target = (viewportPx / (totalMs * 0.12f)).coerceIn(0.2f, 4f)
    it.copy(zoomLevel = target)
}

fun EditorViewModel.selectTool(t: EditorTool) = _state.update { it.copy(selectedTool = t) }

fun EditorViewModel.selectClip(id: String?) = _state.update { state ->
    val clip = id?.let { clipId -> state.project?.clips?.firstOrNull { it.id == clipId } }
    state.copy(
        selectedClipId = id,
        // CapCut-style single selection: picking a clip clears the audio-lane selection.
        selectedAudioTrackId = if (id != null) null else state.selectedAudioTrackId,
        activeGpuFilterConfig = clip?.gpuFilterConfig ?: com.apexstudio.app.data.filter.GpuFilterConfig()
    )
}

fun EditorViewModel.setPlayerPosition(ms: Long) = _state.update { it.copy(playerPositionMs = ms) }

fun EditorViewModel.setPlayerDuration(ms: Long) = _state.update { it.copy(playerDurationMs = ms) }

fun EditorViewModel.setPlayerReady(ready: Boolean) = _state.update { it.copy(isPlayerReady = ready) }

// Phase A: separate "buffering" signal from "ready". The Player.Listener
// calls this with (playbackState == Player.STATE_BUFFERING). We keep
// isPlayerReady semantically unchanged — STATE_READY is the source of
// truth for "first frame painted" so the rest of the UI (filters,
// transforms, overlays) keeps gating on isPlayerReady as before.
fun EditorViewModel.setBuffering(buffering: Boolean) = _state.update { it.copy(isBuffering = buffering) }

fun EditorViewModel.setVideoSize(width: Int, height: Int) = _state.update { it.copy(videoWidth = width, videoHeight = height) }


fun EditorViewModel.setCropMode(enabled: Boolean) = _state.update { it.copy(cropMode = enabled) }

fun EditorViewModel.setCropRect(rect: CropRect) = _state.update { it.copy(cropRect = rect) }

fun EditorViewModel.applyCropAspect(aspect: CropAspect) {
    _state.update { s ->
        val target = aspect.ratio ?: return@update s.copy(cropAspect = aspect)
        val current = s.cropRect
        val currentAspect = current.width / current.height
        val (newW, newH) = if (currentAspect > target) {
            val w = (current.height * target).coerceAtMost(1f)
            w to current.height
        } else {
            val h = (current.width / target).coerceAtMost(1f)
            current.width to h
        }
        val cx = (current.left + current.right) / 2f
        val cy = (current.top + current.bottom) / 2f
        val l = (cx - newW / 2f).coerceIn(0f, 1f - newW)
        val t = (cy - newH / 2f).coerceIn(0f, 1f - newH)
        s.copy(
            cropAspect = aspect,
            cropRect = CropRect(l, t, l + newW, t + newH)
        )
    }
}

fun EditorViewModel.resetCrop() = _state.update { it.copy(cropRect = CropRect.Full, cropAspect = CropAspect.FREE) }

fun EditorViewModel.openSpeedPanel() = _state.update { it.copy(speedPanelOpen = true) }

fun EditorViewModel.closeSpeedPanel() = _state.update { it.copy(speedPanelOpen = false) }

fun EditorViewModel.setPlaybackSpeed(speed: Float) = _state.update { it.copy(playbackSpeed = speed.coerceIn(0.1f, 10f)) }


fun EditorViewModel.setOpticalFlowMotionBlur(enabled: Boolean, intensity: Float = _state.value.opticalFlowBlurIntensity) {
    _state.update { it.copy(opticalFlowMotionBlur = enabled, opticalFlowBlurIntensity = intensity.coerceIn(0f, 1f)) }
}


fun EditorViewModel.setSpeedRampCurvePreset(preset: String) {
    _state.update { it.copy(speedRampCurvePreset = preset) }
}


fun EditorViewModel.setClipSpeed(clipId: String, multiplier: Float) {
    val clamped = multiplier.coerceIn(0.1f, 10f)
    updateClip(clipId) { it.copy(speedMultiplier = clamped) }
    val selectedSpeed = clamped
    _state.update { it.copy(playbackSpeed = selectedSpeed) }
}


fun EditorViewModel.setClipSpeedCurve(clipId: String, curve: SpeedCurve) =
    updateClip(clipId) { it.copy(speedCurve = curve) }


fun EditorViewModel.setClipSpeedRamp(clipId: String, start: Float, end: Float) {
    val s = start.coerceIn(0.1f, 10f)
    val e = end.coerceIn(0.1f, 10f)
    updateClip(clipId) { it.copy(rampStartSpeed = s, rampEndSpeed = e, speedCurve = SpeedCurve.RAMP) }
}


fun EditorViewModel.applySpeedPreset(clipId: String, preset: SpeedPreset) {
    setClipSpeed(clipId, preset.multiplier)
}


internal fun EditorViewModel.updateClip(
    clipId: String,
    persist: Boolean = true,
    transform: (MediaClip) -> MediaClip
) {
    _state.update { s ->
        val proj = s.project ?: return@update s
        val newClips = proj.clips.map { if (it.id == clipId) transform(it) else it }
        s.copy(project = proj.copy(clips = newClips))
    }
    if (persist) persistProject()
}


fun EditorViewModel.addKeyframe(clipId: String, timeMs: Long, transform: AnimatedTransform = AnimatedTransform.Identity) {
    updateClip(clipId) { clip ->
        val kf = Keyframe(
            id = java.util.UUID.randomUUID().toString(),
            timeMs = timeMs,
            translateX = transform.translateX,
            translateY = transform.translateY,
            scale = transform.scale,
            rotationDeg = transform.rotationDeg,
            opacity = transform.opacity
        )
        val without = clip.keyframes.keyframes.filter { it.timeMs != timeMs }
        clip.copy(keyframes = KeyframeTrack(without + kf).sorted())
    }
}


fun EditorViewModel.updateKeyframe(clipId: String, keyframeId: String, transform: (Keyframe) -> Keyframe) {
    updateClip(clipId) { clip ->
        clip.copy(
            keyframes = KeyframeTrack(
                clip.keyframes.keyframes.map { if (it.id == keyframeId) transform(it) else it }
            ).sorted()
        )
    }
}


fun EditorViewModel.removeKeyframe(clipId: String, keyframeId: String) {
    updateClip(clipId) { clip ->
        clip.copy(keyframes = KeyframeTrack(clip.keyframes.keyframes.filter { it.id != keyframeId }))
    }
}


fun EditorViewModel.clearKeyframes(clipId: String) {
    updateClip(clipId) { it.copy(keyframes = KeyframeTrack()) }
}


fun EditorViewModel.setKeyframePanelOpen(open: Boolean) =
    _state.update { it.copy(keyframePanelOpen = open) }


fun EditorViewModel.toggleKeyframeAtPlayhead() {
    val clipId = _state.value.selectedClipId ?: _state.value.project?.clips?.firstOrNull()?.id ?: return
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val playheadMs = _state.value.playerPositionMs
    val existingKf = clip.keyframes.keyframes.firstOrNull { kotlin.math.abs(it.timeMs - playheadMs) <= 150L }
    if (existingKf != null) {
        removeKeyframe(clipId, existingKf.id)
    } else {
        val currentTransform = clip.keyframes.interpolateAt(playheadMs)
        addKeyframe(clipId, playheadMs, currentTransform)
    }
}


fun EditorViewModel.jumpToNextKeyframe() {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == _state.value.selectedClipId }
        ?: _state.value.project?.clips?.firstOrNull() ?: return
    val currentMs = _state.value.playerPositionMs
    val next = clip.keyframes.keyframes.filter { it.timeMs > currentMs + 50L }.minByOrNull { it.timeMs }
    if (next != null) {
        seekTo(next.timeMs)
    }
}


fun EditorViewModel.jumpToPrevKeyframe() {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == _state.value.selectedClipId }
        ?: _state.value.project?.clips?.firstOrNull() ?: return
    val currentMs = _state.value.playerPositionMs
    val prev = clip.keyframes.keyframes.filter { it.timeMs < currentMs - 50L }.maxByOrNull { it.timeMs }
    if (prev != null) {
        seekTo(prev.timeMs)
    }
}


fun EditorViewModel.setTimelineZoom(zoom: Float) {
    _state.update { it.copy(timelineZoom = zoom.coerceIn(0.5f, 10.0f)) }
}


fun EditorViewModel.zoomInTimeline() {
    val current = _state.value.timelineZoom
    setTimelineZoom((current + 0.5f).coerceAtMost(10.0f))
}


fun EditorViewModel.zoomOutTimeline() {
    val current = _state.value.timelineZoom
    setTimelineZoom((current - 0.5f).coerceAtLeast(0.5f))
}


fun EditorViewModel.moveClipLeft(clipId: String) {
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val index = p.clips.indexOfFirst { it.id == clipId }
        if (index <= 0) return@update s
        val updated = p.clips.toMutableList()
        val item = updated.removeAt(index)
        updated.add(index - 1, item)
        s.copy(project = p.copy(clips = updated))
    }
    persistProject()
}


fun EditorViewModel.moveClipRight(clipId: String) {
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val index = p.clips.indexOfFirst { it.id == clipId }
        if (index < 0 || index >= p.clips.size - 1) return@update s
        val updated = p.clips.toMutableList()
        val item = updated.removeAt(index)
        updated.add(index + 1, item)
        s.copy(project = p.copy(clips = updated))
    }
    persistProject()
}


fun EditorViewModel.reorderClips(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        if (fromIndex !in p.clips.indices || toIndex !in p.clips.indices) return@update s
        val updated = p.clips.toMutableList()
        val item = updated.removeAt(fromIndex)
        updated.add(toIndex, item)
        s.copy(project = p.copy(clips = updated))
    }
    persistProject()
}


fun EditorViewModel.applyAnimationPreset(preset: com.apexstudio.app.data.animation.AnimationPresetType) {
    val clipId = _state.value.selectedClipId ?: _state.value.project?.clips?.firstOrNull()?.id ?: return
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    pushUndo()
    val playhead = _state.value.playerPositionMs
    val clipDuration = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(600L)
    val animDuration = minOf(1200L, clipDuration)
    val startMs = if (playhead >= clip.timelineOffsetMs && playhead < clip.timelineOffsetMs + clipDuration) {
        playhead
    } else {
        clip.timelineOffsetMs
    }
    val track = com.apexstudio.app.data.animation.AnimationPresets.createTrack(preset, startMs, animDuration)
    updateClip(clipId) { it.copy(keyframes = track) }
}


fun EditorViewModel.undo() = viewModelScope.launch {
    val current = _state.value.project?.clips ?: return@launch
    if (undoStack.isEmpty()) return@launch
    redoStack.addLast(current)
    val prev = undoStack.removeLast()
    _state.update {
        it.copy(project = it.project?.copy(clips = prev),
            canUndo = undoStack.isNotEmpty(), canRedo = true)
    }
}


fun EditorViewModel.redo() = viewModelScope.launch {
    val current = _state.value.project?.clips ?: return@launch
    if (redoStack.isEmpty()) return@launch
    undoStack.addLast(current)
    val next = redoStack.removeLast()
    _state.update {
        it.copy(project = it.project?.copy(clips = next),
            canUndo = true, canRedo = redoStack.isNotEmpty())
    }
}


fun EditorViewModel.trimClip(clipId: String, startMs: Long, endMs: Long) {
    pushUndo()
    _state.update { s ->
        val proj = s.project ?: return@update s
        val updatedClips = proj.clips.map { c ->
            if (c.id == clipId) {
                val safeStart = startMs.coerceIn(0L, (c.durationMs - 100L).coerceAtLeast(0L))
                val safeEnd = endMs.coerceIn(safeStart + 100L, c.durationMs)
                c.copy(
                    trimStartMs = safeStart,
                    trimEndMs = safeEnd
                )
            } else c
        }
        val newDuration = updatedClips.sumOf { (it.trimEndMs - it.trimStartMs).coerceAtLeast(1000L) }
        val newPlayhead = s.playerPositionMs.coerceIn(0L, newDuration)
        s.copy(
            project = proj.copy(clips = updatedClips, durationMs = newDuration),
            durationMs = newDuration,
            playerPositionMs = newPlayhead,
            currentTimeMs = newPlayhead,
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
    }
    persistProject()
}


fun EditorViewModel.openTrimPanel() = _state.update { it.copy(trimPanelOpen = true) }

fun EditorViewModel.closeTrimPanel() = _state.update { it.copy(trimPanelOpen = false) }


fun EditorViewModel.setTrimStartAtPlayhead(clipId: String) {
    val playhead = _state.value.playerPositionMs
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val newStart = playhead.coerceIn(0L, (clip.trimEndMs - 100L).coerceAtLeast(0L))
    trimClip(clipId, newStart, clip.trimEndMs)
}


fun EditorViewModel.setTrimEndAtPlayhead(clipId: String) {
    val playhead = _state.value.playerPositionMs
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val newEnd = playhead.coerceIn(clip.trimStartMs + 100L, clip.durationMs)
    trimClip(clipId, clip.trimStartMs, newEnd)
}


fun EditorViewModel.resetTrim(clipId: String) {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    trimClip(clipId, 0L, clip.durationMs)
}


/**
 * Executes real hardware-accelerated video trimming on the selected clip using AndroidX Media3 Transformer.
 * Uses MediaItem.ClippingConfiguration with the requested start and end timestamps.
 */
fun EditorViewModel.trimClipWithMedia3Transformer(
    clipId: String,
    startMs: Long,
    endMs: Long,
    replaceInTimeline: Boolean = true,
    onComplete: ((String) -> Unit)? = null
) {
    val trimmer = media3VideoTrimmer ?: return
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return

    _state.update {
        it.copy(
            isTransformerTrimming = true,
            transformerTrimProgress = 0f,
            transformerTrimMessage = "Preparing Media3 Transformer cut...",
            transformerTrimError = null
        )
    }

    viewModelScope.launch {
        val request = com.apexstudio.app.data.trim.Media3VideoTrimmer.TrimRequest(
            inputUri = clip.uri,
            startMs = startMs,
            endMs = endMs,
            clipId = clipId
        )
        trimmer.trimVideo(request).collect { result ->
            when (result) {
                is com.apexstudio.app.data.trim.Media3VideoTrimmer.TrimResult.Progress -> {
                    _state.update {
                        it.copy(
                            isTransformerTrimming = true,
                            transformerTrimProgress = result.progress,
                            transformerTrimMessage = "Trimming clip via Media3 Transformer (${(result.progress * 100).toInt()}%)..."
                        )
                    }
                }
                is com.apexstudio.app.data.trim.Media3VideoTrimmer.TrimResult.Success -> {
                    pushUndo()
                    if (replaceInTimeline) {
                        // Update the existing clip with the newly trimmed file and reset trim offsets to 0..duration
                        updateClip(clipId) { c ->
                            c.copy(
                                uri = result.outputUri,
                                durationMs = result.durationMs,
                                trimStartMs = 0L,
                                trimEndMs = result.durationMs
                            )
                        }
                    } else {
                        // Add as new trimmed clip to timeline
                        val newClip = clip.copy(
                            id = "clip_${System.currentTimeMillis()}_trimmed",
                            uri = result.outputUri,
                            durationMs = result.durationMs,
                            trimStartMs = 0L,
                            trimEndMs = result.durationMs
                        )
                        _state.update { s ->
                            val p = s.project ?: return@update s
                            val updatedClips = p.clips + newClip
                            val newDuration = updatedClips.sumOf { (it.trimEndMs - it.trimStartMs).coerceAtLeast(1000L) }
                            s.copy(
                                project = p.copy(clips = updatedClips, durationMs = newDuration),
                                durationMs = newDuration
                            )
                        }
                        persistProject()
                    }
                    _state.update {
                        it.copy(
                            isTransformerTrimming = false,
                            transformerTrimProgress = 1f,
                            transformerTrimMessage = "Clip trimmed successfully with Media3 Transformer!"
                        )
                    }
                    onComplete?.invoke(result.outputUri)
                }
                is com.apexstudio.app.data.trim.Media3VideoTrimmer.TrimResult.Error -> {
                    _state.update {
                        it.copy(
                            isTransformerTrimming = false,
                            transformerTrimError = result.message
                        )
                    }
                }
            }
        }
    }
}


fun EditorViewModel.cancelMedia3Trim() {
    media3VideoTrimmer?.cancelActiveTrim()
    _state.update {
        it.copy(
            isTransformerTrimming = false,
            transformerTrimProgress = 0f,
            transformerTrimMessage = null
        )
    }
}


fun EditorViewModel.clearTransformerTrimStatus() {
    _state.update {
        it.copy(
            transformerTrimMessage = null,
            transformerTrimError = null
        )
    }
}


fun EditorViewModel.splitClip(clipId: String, atMs: Long) {
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val c = p.clips.firstOrNull { it.id == clipId } ?: return@update s
        val safeSplit = atMs.coerceIn(c.trimStartMs + 100L, (c.trimEndMs - 100L).coerceAtLeast(c.trimStartMs + 100L))

        val originalEnd = c.trimEndMs
        val newClip = c.copy(
            id = "clip_${System.currentTimeMillis()}_split",
            trimStartMs = safeSplit,
            trimEndMs = originalEnd,
            durationMs = originalEnd - safeSplit
        )
        val updatedClips = mutableListOf<com.apexstudio.app.domain.model.MediaClip>()
        for (clip in p.clips) {
            if (clip.id == clipId) {
                updatedClips.add(clip.copy(trimEndMs = safeSplit, durationMs = safeSplit - clip.trimStartMs))
                updatedClips.add(newClip)
            } else {
                updatedClips.add(clip)
            }
        }
        val newDur = updatedClips.sumOf { (it.trimEndMs - it.trimStartMs).coerceAtLeast(100L) }
        s.copy(
            project = p.copy(clips = updatedClips, durationMs = newDur),
            durationMs = newDur,
            selectedClipId = newClip.id,
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
    }
    persistProject()
}


fun EditorViewModel.duplicateClip(clipId: String) {
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val target = p.clips.firstOrNull { it.id == clipId } ?: return@update s
        val newClip = target.copy(
            id = "clip_${System.currentTimeMillis()}_dup"
        )
        val updated = p.clips + newClip
        val newDur = updated.sumOf { (it.trimEndMs - it.trimStartMs).coerceAtLeast(100L) }
        s.copy(
            project = p.copy(clips = updated, durationMs = newDur),
            durationMs = newDur,
            selectedClipId = newClip.id,
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
    }
    persistProject()
}


fun EditorViewModel.cutClipAtPlayhead() {
    val clipId = _state.value.selectedClipId ?: return
    val atMs = _state.value.currentTimeMs
    pushUndo()
    _state.update { s ->
        s.copy(project = s.project?.copy(
            clips = s.project.clips.map {
                if (it.id == clipId) {
                    val end = atMs.coerceIn(it.trimStartMs, it.trimEndMs)
                    it.copy(trimEndMs = end, durationMs = end - it.trimStartMs)
                } else it
            }
        ))
    }
}


fun EditorViewModel.deleteClip(clipId: String) {
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val updated = p.clips.filterNot { it.id == clipId }
        val newSelected = if (s.selectedClipId == clipId) updated.firstOrNull()?.id else s.selectedClipId
        val newDur = updated.maxOfOrNull { it.trimEndMs - it.trimStartMs } ?: 0L
        s.copy(
            project = p.copy(clips = updated, durationMs = newDur),
            durationMs = newDur,
            selectedClipId = newSelected,
            // Phase D: deleting the active overlay clip clears the
            // transform too, otherwise the preview layer would keep
            // trying to render a non-existent overlay's PlayerView.
            overlayClipId = if (s.overlayClipId == clipId) null else s.overlayClipId,
            overlayTransform = if (s.overlayClipId == clipId) com.apexstudio.app.presentation.state.OverlayTransform.Identity else s.overlayTransform,
            // PiP canvas selection must follow the clip: a deleted
            // overlay cannot stay selected.
            selectedOverlayClipId = if (s.selectedOverlayClipId == clipId) null else s.selectedOverlayClipId
        )
    }
    persistProject()
}


/**
 * Deletes multiple clips in a single operation: one undo entry, one state
 * update, one project persist. Prefer this over calling [deleteClip] in a
 * loop for multi-select bulk delete.
 */
fun EditorViewModel.deleteClips(clipIds: Set<String>) {
    if (clipIds.isEmpty()) return
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val updated = p.clips.filterNot { it.id in clipIds }
        val newSelected = if (s.selectedClipId in clipIds) updated.firstOrNull()?.id else s.selectedClipId
        val newDur = updated.maxOfOrNull { it.trimEndMs - it.trimStartMs } ?: 0L
        s.copy(
            project = p.copy(clips = updated, durationMs = newDur),
            durationMs = newDur,
            selectedClipId = newSelected,
            // Deleting the active overlay clip clears the transform too,
            // otherwise the preview layer would keep trying to render a
            // non-existent overlay's PlayerView.
            overlayClipId = if (s.overlayClipId in clipIds) null else s.overlayClipId,
            overlayTransform = if (s.overlayClipId in clipIds) com.apexstudio.app.presentation.state.OverlayTransform.Identity else s.overlayTransform,
            // PiP canvas selection must follow the clip: a deleted
            // overlay cannot stay selected.
            selectedOverlayClipId = if (s.selectedOverlayClipId in clipIds) null else s.selectedOverlayClipId
        )
    }
    persistProject()
}


fun EditorViewModel.moveClipToTrack(clipId: String, newType: ClipType, newTrackIndex: Int) {
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val updated = p.clips.map {
            if (it.id == clipId) it.copy(type = newType, trackIndex = newTrackIndex)
            else it
        }
        s.copy(project = p.copy(clips = updated))
    }
    persistProject()
}


fun EditorViewModel.addClipToTrack(type: ClipType, trackIndex: Int) {
    val ctx = context ?: return
    viewModelScope.launch {
        val sampleUri = try {
            com.apexstudio.app.data.media.SampleVideoGenerator.getOrCreateSampleVideo(ctx)
        } catch (e: Exception) { "" }
        val newClip = MediaClip(
            id = java.util.UUID.randomUUID().toString(),
            name = when(type) {
                ClipType.VIDEO -> "Clip_V1.mp4"
                ClipType.OVERLAY -> "Overlay_V2.mp4"
                ClipType.AUDIO -> "Track_A1.mp3"
                ClipType.SFX -> "Sfx_FX.wav"
                ClipType.IMAGE -> "Photo_P1.jpg"
            },
            uri = sampleUri,
            durationMs = 10_000L,
            trimStartMs = 0L,
            trimEndMs = 10_000L,
            trackIndex = trackIndex,
            type = type
        )
        pushUndo()
        _state.update { s ->
            val p = s.project ?: return@update s
            val updated = p.clips + newClip
            val newDur = maxOf(s.durationMs, 10_000L)
            s.copy(
                project = p.copy(clips = updated, durationMs = newDur),
                durationMs = newDur,
                selectedClipId = newClip.id
            )
        }
        persistProject()
    }
}


fun EditorViewModel.setClipTimelineOffset(clipId: String, offsetMs: Long) {
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val updated = p.clips.map {
            if (it.id == clipId) it.copy(timelineOffsetMs = offsetMs.coerceAtLeast(0L))
            else it
        }
        s.copy(project = p.copy(clips = updated))
    }
    persistProject()
}


fun EditorViewModel.shiftClipTimelineOffset(clipId: String, deltaMs: Long) {
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val updated = p.clips.map {
            if (it.id == clipId) {
                val newOffset = (it.timelineOffsetMs + deltaMs).coerceAtLeast(0L)
                it.copy(timelineOffsetMs = newOffset)
            } else it
        }
        s.copy(project = p.copy(clips = updated))
    }
    persistProject()
}


internal fun EditorViewModel.pushUndo() {
    val current = _state.value.project?.clips ?: return
    undoStack.addLast(current)
    if (undoStack.size > 50) undoStack.removeFirst()
    redoStack.clear()
    _state.update { it.copy(canUndo = true, canRedo = false) }
}


/**
 * Toggles a keyframe at the current playhead position for the given clip.
 * If a keyframe exists within 150ms of the playhead it is removed, otherwise
 * one is added using the interpolated transform at the playhead.
 */
fun EditorViewModel.toggleKeyframeAtPlayheadFor(clipId: String) {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val playheadMs = _state.value.playerPositionMs
    val existingKf = clip.keyframes.keyframes.firstOrNull { kotlin.math.abs(it.timeMs - playheadMs) <= 150L }
    if (existingKf != null) {
        removeKeyframe(clipId, existingKf.id)
    } else {
        addKeyframe(clipId, playheadMs, clip.keyframes.interpolateAt(playheadMs))
    }
}


/**
 * Moves a clip keyframe to a new timeline position (from diamond drag on the timeline).
 */
fun EditorViewModel.moveKeyframe(clipId: String, keyframeId: String, newTimeMs: Long) {
    updateKeyframe(clipId, keyframeId) { it.copy(timeMs = newTimeMs.coerceAtLeast(0L)) }
}


/**
 * Toggles a text-overlay keyframe at the current playhead position.
 */
fun EditorViewModel.toggleTextKeyframeAtPlayhead(clipId: String, overlayId: String) {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val overlay = clip.textOverlays.firstOrNull { it.id == overlayId } ?: return
    val playheadMs = _state.value.playerPositionMs
    val existingKf = overlay.keyframes.keyframes.firstOrNull { kotlin.math.abs(it.timeMs - playheadMs) <= 150L }
    if (existingKf != null) {
        updateTextOverlay(clipId, overlayId) { ov ->
            ov.copy(keyframes = KeyframeTrack(ov.keyframes.keyframes.filter { it.id != existingKf.id }))
        }
    } else {
        val t = overlay.keyframes.interpolateAt(playheadMs)
        val kf = Keyframe(
            id = java.util.UUID.randomUUID().toString(),
            timeMs = playheadMs,
            translateX = t.translateX,
            translateY = t.translateY,
            scale = t.scale,
            rotationDeg = t.rotationDeg,
            opacity = t.opacity
        )
        updateTextOverlay(clipId, overlayId) { ov ->
            val without = ov.keyframes.keyframes.filter { it.timeMs != playheadMs }
            ov.copy(keyframes = KeyframeTrack(without + kf).sorted())
        }
    }
}


/**
 * Moves a text-overlay keyframe to a new timeline position (from diamond drag).
 */
fun EditorViewModel.moveTextKeyframe(clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) {
    updateTextOverlay(clipId, overlayId) { ov ->
        ov.copy(
            keyframes = KeyframeTrack(
                ov.keyframes.keyframes.map {
                    if (it.id == keyframeId) it.copy(timeMs = newTimeMs.coerceAtLeast(0L)) else it
                }
            ).sorted()
        )
    }
}


// ---------------------------------------------------------------------------
// Multi-layer video tracks (CapCut-style, up to 10 layers).
// Layer 0 = main video (V1). Layers 1..9 = overlay layers (V2..V10),
// composited picture-in-picture above the main layer.
// A clip's layer is its MediaClip.trackIndex (0..9).
// ---------------------------------------------------------------------------

/** Maximum number of video layers (V1..V10). */
const val MAX_VIDEO_LAYERS = 10

/** Toggle a video layer's visibility (eye). Hidden layers are skipped in preview, export and timeline. */
fun EditorViewModel.toggleVideoLayerVisibility(layer: Int) {
    val l = layer.coerceIn(0, MAX_VIDEO_LAYERS - 1)
    _state.update { s ->
        val hidden = s.hiddenVideoLayers.toMutableSet()
        if (l in hidden) hidden.remove(l) else hidden.add(l)
        s.copy(hiddenVideoLayers = hidden)
    }
    persistProject()
}

/** Toggle a video layer's lock. Locked layers can't be edited until unlocked. */
fun EditorViewModel.toggleVideoLayerLock(layer: Int) {
    val l = layer.coerceIn(0, MAX_VIDEO_LAYERS - 1)
    _state.update { s ->
        val locked = s.lockedVideoLayers.toMutableSet()
        if (l in locked) locked.remove(l) else locked.add(l)
        s.copy(lockedVideoLayers = locked)
    }
    persistProject()
}

/**
 * Delete a video layer: removes every clip on it. Layer 0 (main) can never
 * be deleted. Returns true when a layer was actually removed.
 */
fun EditorViewModel.deleteVideoLayer(layer: Int): Boolean {
    val l = layer.coerceIn(0, MAX_VIDEO_LAYERS - 1)
    if (l == 0) return false
    pushUndo()
    var removed = false
    _state.update { s ->
        val p = s.project ?: return@update s
        val remaining = p.clips.filter { clip ->
            val onLayer = clip.trackIndex == l &&
                (clip.type == com.apexstudio.app.domain.model.ClipType.VIDEO ||
                 clip.type == com.apexstudio.app.domain.model.ClipType.IMAGE ||
                 clip.type == com.apexstudio.app.domain.model.ClipType.OVERLAY)
            if (onLayer) removed = true
            !onLayer
        }
        val hidden = s.hiddenVideoLayers - l
        val locked = s.lockedVideoLayers - l
        val extras = s.extraVideoLayers - l
        s.copy(
            project = p.copy(clips = remaining),
            hiddenVideoLayers = hidden,
            lockedVideoLayers = locked,
            extraVideoLayers = extras,
            selectedClipId = if (s.selectedClipId?.let { id -> remaining.none { it.id == id } } == true) null else s.selectedClipId
        )
    }
    persistProject()
    return removed
}

/**
 * Find the next free overlay layer (1..9). A layer is free when no video
 * clip uses that trackIndex. Returns -1 when all 10 layers are occupied.
 */
fun EditorViewModel.nextFreeVideoLayer(): Int {
    val used = _state.value.project?.clips
        ?.filter {
            it.type == com.apexstudio.app.domain.model.ClipType.VIDEO ||
            it.type == com.apexstudio.app.domain.model.ClipType.IMAGE ||
            it.type == com.apexstudio.app.domain.model.ClipType.OVERLAY
        }
        ?.map { it.trackIndex.coerceIn(0, MAX_VIDEO_LAYERS - 1) }
        ?.toSet() ?: setOf(0)
    return (1 until MAX_VIDEO_LAYERS).firstOrNull { it !in used } ?: -1
}

/**
 * Move a clip to a video layer. Layer 0 keeps VIDEO/IMAGE types (full-frame
 * main). Layers 1..9 force OVERLAY so preview + export composite the clip
 * picture-in-picture above the main layer — matching the timeline's V2..V10
 * rows and the PiP canvas hit-testing.
 */
fun EditorViewModel.moveClipToVideoLayer(clipId: String, layer: Int) {
    val l = layer.coerceIn(0, MAX_VIDEO_LAYERS - 1)
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val updated = p.clips.map { clip ->
            if (clip.id != clipId) return@map clip
            val newType = when {
                l == 0 && clip.type == com.apexstudio.app.domain.model.ClipType.OVERLAY ->
                    com.apexstudio.app.domain.model.ClipType.VIDEO
                l > 0 && (clip.type == com.apexstudio.app.domain.model.ClipType.VIDEO ||
                          clip.type == com.apexstudio.app.domain.model.ClipType.IMAGE) ->
                    com.apexstudio.app.domain.model.ClipType.OVERLAY
                else -> clip.type
            }
            clip.copy(trackIndex = l, type = newType)
        }
        s.copy(project = p.copy(clips = updated))
    }
    persistProject()
}

/**
 * Explicitly add an empty overlay layer (V2..V10). The layer row appears
 * immediately so the user can drop clips onto it; the layer is forgotten
 * again once it holds clips (trackIndex becomes the source of truth).
 * Returns the new layer index, or -1 when all 10 layers exist.
 */
fun EditorViewModel.addEmptyVideoLayer(): Int {
    val used = _state.value.project?.clips
        ?.filter {
            it.type == com.apexstudio.app.domain.model.ClipType.VIDEO ||
            it.type == com.apexstudio.app.domain.model.ClipType.IMAGE ||
            it.type == com.apexstudio.app.domain.model.ClipType.OVERLAY
        }
        ?.map { it.trackIndex.coerceIn(0, MAX_VIDEO_LAYERS - 1) }
        ?.toSet() ?: emptySet()
    val extras = _state.value.extraVideoLayers
    val layer = (1 until MAX_VIDEO_LAYERS).firstOrNull { it !in used && it !in extras } ?: return -1
    _state.update { it.copy(extraVideoLayers = it.extraVideoLayers + layer) }
    return layer
}
