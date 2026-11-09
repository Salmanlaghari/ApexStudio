package com.apexstudio.app.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.presentation.state.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.apexstudio.app.domain.model.timeline.Curve
import com.apexstudio.app.domain.model.timeline.VideoAnimationProperty
import com.apexstudio.app.domain.model.timeline.addAnimationKeyframe
import com.apexstudio.app.domain.model.timeline.animationKeyframeTimes
import com.apexstudio.app.domain.model.timeline.addAnimationKeyframeAt
import com.apexstudio.app.domain.model.timeline.copyAnimationKeyframe
import com.apexstudio.app.domain.model.timeline.moveAnimationKeyframe
import com.apexstudio.app.domain.model.timeline.removeAnimationKeyframeAt
import com.apexstudio.app.domain.model.timeline.setAnimationCurveAt
import com.apexstudio.app.domain.model.timeline.upsertAnimationValue

import com.apexstudio.app.domain.model.timeline.clearAnimationKeyframes
import com.apexstudio.app.domain.model.timeline.replaceLegacyKeyframeAt
import com.apexstudio.app.domain.model.timeline.sharedKeyframeTrackFor
import com.apexstudio.app.domain.model.timeline.sharedTextItem
import com.apexstudio.app.domain.model.timeline.sharedVideoClip
import com.apexstudio.app.domain.model.timeline.syncSharedTimeline
import com.apexstudio.app.domain.model.timeline.updateSharedKeyframeTrack
import com.apexstudio.app.domain.model.timeline.updateSharedTextItem
import com.apexstudio.app.domain.model.timeline.animationTransformAt
/**
 * Minimum interval between [EditorState] position mirrors. The live
 * playhead ([EditorViewModel.playerPositionFlow]) still ticks at ~30Hz,
 * but the state copy is capped at ~10Hz so playback doesn't recompose
 * the whole editor 30x/s.
 */
private const val POSITION_STATE_SYNC_MIN_INTERVAL_MS = 100L

/** Persist the selected main-track clip's composition placement choice. */
fun EditorViewModel.setVideoFitMode(clipId: String, mode: VideoFitMode) {
    val project = _state.value.project ?: return
    val clip = project.clips.firstOrNull { it.id == clipId } ?: return
    if (clip.type != ClipType.VIDEO || clip.videoFitMode == mode) return
    pushUndo()
    _state.update { state ->
        val current = state.project ?: return@update state
        state.copy(project = current.copy(clips = current.clips.map {
            if (it.id == clipId) it.copy(videoFitMode = mode) else it
        }))
    }
    persistProject(refreshLegacyClipFields = true)
}


fun EditorViewModel.togglePlay() = setPlaying(!_state.value.isPlaying)

fun EditorViewModel.setPlaying(v: Boolean) {
    // Replay is a project seek, not a seek to the last selected source's trim head.
    // Reset both clocks before observers start the video and placed-audio players.
    if (v && !_state.value.isPlaying && _state.value.durationMs > 0L && livePlayheadMs() >= _state.value.durationMs) {
        seekTo(0L)
    }
    _state.update { it.copy(isPlaying = v) }
    // Pausing: flush the live position into the state mirror so every
    // state reader (timecode, trim panel, split) sees the exact frame
    // playback stopped on, not a up-to-100ms-stale mirror.
    if (!v) {
        val live = livePlayheadMs().coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        lastPositionStateSyncAtMs = android.os.SystemClock.uptimeMillis()
        _playerPosition.value = live
        _state.update { it.copy(playerPositionMs = live, currentTimeMs = live) }
    }
}

fun EditorViewModel.seekTo(ms: Long) {
    // Seek: the live value AND the state mirror move together, so the
    // playhead, timecode and every action read the same position.
    val clamped = ms.coerceIn(0, _state.value.durationMs)
    _playerPosition.value = clamped
    lastPositionStateSyncAtMs = android.os.SystemClock.uptimeMillis()
    _state.update {
        it.copy(currentTimeMs = clamped, playerPositionMs = clamped)
    }
}

fun EditorViewModel.stepFrame(forward: Boolean) {
    // Stop playback before moving the playhead: otherwise the player loop
    // can immediately replace the frame we just stepped to.
    setPlaying(false)
    // Step from the LIVE playhead: the state mirror can lag it by up to
    // 100ms, and stepping from the stale mirror would jump backwards.
    val base = livePlayheadMs()
    val state = _state.value
    val duration = state.durationMs.coerceAtLeast(0L)
    val fps = state.project?.fps?.takeIf { it > 0 } ?: 60
    val next = TimelineFrameMath.step(base, forward, fps, duration)
    _playerPosition.value = next
    lastPositionStateSyncAtMs = android.os.SystemClock.uptimeMillis()
    _state.update { it.copy(currentTimeMs = next, playerPositionMs = next) }
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
        // Single selection: picking an OVERLAY clip on the timeline
        // also selects it on the preview canvas (so the transform box
        // shows); picking anything else clears the canvas selection.
        selectedOverlayClipId = if (clip?.type == ClipType.OVERLAY) id else null,
        // CapCut-style single selection: picking a clip clears the audio-lane
        // selection, and also any text/sticker selection — otherwise the
        // TEXT/STICKER bar (higher precedence in resolveToolbarSelection)
        // would keep showing and the clip actions would stay unreachable.
        selectedAudioTrackId = if (id != null) null else state.selectedAudioTrackId,
        selectedTextOverlayId = if (id != null) null else state.selectedTextOverlayId,
        selectedStickerId = if (id != null) null else state.selectedStickerId,
        activeGpuFilterConfig = clip?.gpuFilterConfig ?: com.apexstudio.app.data.filter.GpuFilterConfig(),
        chromaKeySettings = clip?.chromaKeySettings
            ?: com.apexstudio.app.domain.model.ChromaKeySettings()
    )
}

/**
 * Single source of truth for the playhead: the LIVE flow
 * ([EditorViewModel.playerPositionFlow]) IS the position (driven by
 * player.currentPosition in the EditorScreen loop). The [EditorState]
 * copy ([playerPositionMs] / [currentTimeMs]) is a ~10Hz mirror so
 * playback ticks don't recompose the whole editor — see
 * [EditorViewModel._playerPosition]. Seeks / pauses / user actions
 * force a mirror sync ([forceStateSync]).
 */
fun EditorViewModel.setPlayerPosition(ms: Long, forceStateSync: Boolean = false) {
    _playerPosition.value = ms
    val now = android.os.SystemClock.uptimeMillis()
    if (forceStateSync || now - lastPositionStateSyncAtMs >= POSITION_STATE_SYNC_MIN_INTERVAL_MS) {
        lastPositionStateSyncAtMs = now
        _state.update { it.copy(playerPositionMs = ms, currentTimeMs = ms) }
    }
}

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
    // A constant speed supersedes any variable curve: the curve is
    // cleared so the two can never disagree about the clip's rate.
    // Recompute the timeline duration because clearing a curve
    // restores the clip's plain trimmed span.
    updateClip(clipId) { it.copy(speedMultiplier = clamped, speedPoints = emptyList()) }
    _state.update { it.copy(playbackSpeed = clamped) }
    refreshTimelineDuration()
}

/**
 * Apply a variable speed curve to a clip. [points] is sanitised
 * (sorted, clamped, endpoints pinned); an empty/sanitised-to-empty
 * list clears the curve. Also recomputes the timeline duration —
 * a curve changes the clip's output length.
 */
fun EditorViewModel.setClipSpeedPoints(clipId: String, points: List<SpeedPoint>) {
    val clean = SpeedCurveMath.sanitize(points)
    updateClip(clipId) {
        it.copy(
            speedPoints = clean,
            speedCurve = if (clean.isNotEmpty()) SpeedCurve.RAMP else it.speedCurve
        )
    }
    if (clean.isEmpty()) {
        _state.update { it.copy(speedRampCurvePreset = "CUSTOM") }
    }
    refreshTimelineDuration()
}

/** One-tap curve presets (Slow-mo / Fast / Ramp In / Ramp Out / Bullet Time). */
fun EditorViewModel.applySpeedCurvePreset(clipId: String, preset: SpeedCurveMath.CurvePreset) {
    setClipSpeedPoints(clipId, preset.points)
    _state.update { it.copy(speedRampCurvePreset = preset.name) }
}

/**
 * Recompute [EditorState.durationMs] from the current clips after a
 * change that can alter a clip's output length (curve edit / clear,
 * trim, add/remove). Mirrors [trimClip]: the playhead is clamped
 * into the new total so it can never strand past the end.
 */
fun EditorViewModel.refreshTimelineDuration() {
    // Clamp from the LIVE playhead (the state mirror can lag it by up
    // to 100ms) for the STATE mirror. The LIVE flow is clamped
    // separately below from its CURRENT value (atomic update) — NOT
    // from the stale liveBefore snapshot: the 33ms playback loop keeps
    // advancing live while _state.update runs, so writing liveBefore
    // back would clobber the loop's newer tick and snap the playhead
    // backward during playback.
    val liveBefore = livePlayheadMs()
    var newDurationMs: Long? = null
    _state.update { s ->
        val clips = s.project?.clips ?: return@update s
        val newDuration = computeTimelineDurationMs(clips, s.project?.audioTracks ?: emptyList())
        newDurationMs = newDuration
        val newPlayhead = liveBefore.coerceIn(0L, newDuration)
        s.copy(
            project = s.project?.copy(clips = clips, durationMs = newDuration),
            durationMs = newDuration,
            playerPositionMs = newPlayhead,
            currentTimeMs = newPlayhead
        )
    }
    // Live clamp: only ever pulls live DOWN to the new duration bound
    // (when the duration shrank below the current position); if live
    // is already within bounds this is a no-op, never a backward snap.
    newDurationMs?.let { d -> _playerPosition.update { it.coerceIn(0L, d) } }
    // updateClip() already persisted once with the pre-refresh
    // duration; persist again so disk state matches.
    persistProject()
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
        val newClips = proj.clips.map { original ->
            if (original.id != clipId) original else {
                val changed = transform(original)
                if (changed.uri != original.uri) changed.copy(faceCropTrack = null) else changed
            }
        }
        val legacyAnimationChanged = newClips.any { changed ->
            proj.clips.firstOrNull { it.id == changed.id }?.keyframes != changed.keyframes
        }
        s.copy(project = proj.copy(clips = newClips).syncSharedTimeline(
            refreshLegacyAnimation = legacyAnimationChanged,
            refreshLegacyClipFields = true
        ))
    }
    if (persist) persistProject()
}


fun EditorViewModel.addKeyframe(clipId: String, timeMs: Long, transform: AnimatedTransform = AnimatedTransform.Identity) {
    val project = _state.value.project ?: return
    if (project.clips.none { it.id == clipId }) return
    // Add samples all typed properties first, then records the preview values at this time.
    pushUndo()
    val targetTime = timeMs.coerceIn(0L, project.durationMs.coerceAtLeast(0L))
    val keyframe = Keyframe(
        id = java.util.UUID.randomUUID().toString(),
        timeMs = targetTime,
        translateX = transform.translateX,
        translateY = transform.translateY,
        scale = transform.scale,
        rotationDeg = transform.rotationDeg,
        opacity = transform.opacity,
        volume = transform.volume,
        effectIntensity = transform.effectIntensity,
        filterIntensity = transform.filterIntensity,
        saturation = transform.saturation ?: 1f,
        curve = KeyframeCurve.BEZIER
    )
    val synced = project.syncSharedTimeline()
    val updated = synced.updateSharedKeyframeTrack(clipId) { clip ->
        clip.addAnimationKeyframe(targetTime, Curve.SMOOTH).replaceLegacyKeyframeAt(keyframe)
    }
    _state.update { current -> if (current.project?.id == project.id) current.copy(project = updated) else current }
    persistProject()
}

/**
 * CapCut-style auto-keyframe: change one keyframeable parameter at
 * [timeMs] (Key-frame Studio slider, V1 pinch-to-zoom, ...).
 *
 * - A keyframe within 150ms of [timeMs] is UPDATED in place — only
 *   the changed parameter moves (all other fields, including the
 *   saturation pin, are preserved). No new keyframe, no undo entry
 *   (drag streams call this repeatedly). This is an intentional gesture
 *   sampling window (not a frame-duration estimate): pinch-while-playing
 *   relies on it to avoid creating a keyframe on every playback frame.
 *   Keep it separate from frame stepping unless the gesture sampling policy
 *   is changed as well.
 * - Otherwise, if the track is non-empty, a NEW keyframe is inserted
 *   at [timeMs] (other fields = interpolated values at [timeMs],
 *   saturation pins the current effective factor) with one undo entry.
 * - If the track is EMPTY this is a no-op returning false: the first
 *   keyframe stays manual (the "+" button), exactly like CapCut.
 */
/**
 * Apply a single parameter change to a Keyframe.
 * Maps KeyframeParam enum to the corresponding Keyframe field.
 */
private fun Keyframe.withParam(param: KeyframeParam, value: Float): Keyframe = when (param) {
    KeyframeParam.TRANSLATE_X -> copy(translateX = value)
    KeyframeParam.TRANSLATE_Y -> copy(translateY = value)
    KeyframeParam.SCALE -> copy(scale = value)
    KeyframeParam.ROTATION -> copy(rotationDeg = value)
    KeyframeParam.OPACITY -> copy(opacity = value)
    KeyframeParam.VOLUME -> copy(volume = value)
    KeyframeParam.EFFECT_INTENSITY -> copy(effectIntensity = value)
    KeyframeParam.FILTER_INTENSITY -> copy(filterIntensity = value)
    KeyframeParam.SATURATION -> copy(saturation = value)
}

private fun KeyframeCurve.toTimelineCurve(): Curve = when (this) {
    KeyframeCurve.LINEAR -> Curve.LINEAR
    KeyframeCurve.EASE_IN -> Curve.EASE_IN
    KeyframeCurve.EASE_OUT -> Curve.EASE_OUT
    KeyframeCurve.BEZIER, KeyframeCurve.EASE_IN_OUT, KeyframeCurve.SPRING, KeyframeCurve.OVERSHOOT -> Curve.SMOOTH
    KeyframeCurve.HOLD -> Curve.LINEAR
}

fun EditorViewModel.upsertKeyframeParam(
    clipId: String,
    timeMs: Long,
    param: KeyframeParam,
    value: Float
): Boolean {
    if (!value.isFinite()) return false
    val original = _state.value.project ?: return false
    val project = original.syncSharedTimeline()
    val clip = project.sharedVideoClip(clipId) ?: return false
    val times = clip.animationKeyframeTimes()
    if (times.isEmpty()) return false
    val property = when (param) {
        KeyframeParam.TRANSLATE_X -> VideoAnimationProperty.POSITION_X
        KeyframeParam.TRANSLATE_Y -> VideoAnimationProperty.POSITION_Y
        KeyframeParam.SCALE -> VideoAnimationProperty.SCALE
        KeyframeParam.ROTATION -> VideoAnimationProperty.ROTATION
        KeyframeParam.OPACITY -> VideoAnimationProperty.OPACITY
        KeyframeParam.VOLUME -> VideoAnimationProperty.VOLUME
        KeyframeParam.EFFECT_INTENSITY -> VideoAnimationProperty.EFFECT_INTENSITY
        KeyframeParam.FILTER_INTENSITY -> VideoAnimationProperty.FILTER_INTENSITY
        KeyframeParam.SATURATION -> VideoAnimationProperty.SATURATION
    }
    val near = times.minByOrNull { kotlin.math.abs(it - timeMs) }
    val nearEnough = near != null && kotlin.math.abs(near - timeMs) <= 150L
    val targetTime = if (nearEnough) near!! else timeMs.coerceIn(0L, project.durationMs.coerceAtLeast(0L))
    if (!nearEnough) pushUndo()
    val updated = project.updateSharedKeyframeTrack(clipId) { current ->
        val seeded = if (nearEnough) current else current.addAnimationKeyframe(targetTime, Curve.SMOOTH)
        seeded.upsertAnimationValue(property, targetTime, value)
    }
    _state.update { current -> if (current.project?.id == original.id) current.copy(project = updated) else current }
    persistProject()
    return true
}

/**
 * V1 pinch-to-zoom (CapCut "Smooth Zoom In"): a two-finger pinch on
 * the video preview multiplies the selected VIDEO/IMAGE clip's scale
 * at the live playhead. No-op when no clip is selected, the clip is
 * an overlay (overlays have their own canvas gestures), or the clip
 * has no keyframes yet (first keyframe stays manual — use "+").
 * During playback the playhead moves between zoom events; the
 * 150ms snap window collapses near-duplicates into one keyframe,
 * so a long pinch-while-playing samples roughly one keyframe per
 * 150ms instead of spraying one per frame.
 */
fun EditorViewModel.pinchZoomSelectedClip(zoomFactor: Float) {
    if (zoomFactor == 1f || !zoomFactor.isFinite()) return
    // No fallback to the first clip: pinching with no explicit
    // selection must be a no-op, never a surprise keyframe on an
    // unselected clip.
    val clipId = _state.value.selectedClipId ?: return
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    if (clip.type == ClipType.OVERLAY) return
    if (clip.keyframes.keyframes.isEmpty()) return
    val playheadMs = livePlayheadMs()
    val current = clip.keyframes.interpolateAt(playheadMs).scale
    val next = (current * zoomFactor).coerceIn(0.1f, 4f)
    upsertKeyframeParam(clip.id, playheadMs, KeyframeParam.SCALE, next)
}

/** Copy a keyframe with a single [KeyframeParam] replaced. */
private fun Keyframe.copy(param: KeyframeParam, value: Float): Keyframe = when (param) {
    KeyframeParam.TRANSLATE_X -> copy(translateX = value)
    KeyframeParam.TRANSLATE_Y -> copy(translateY = value)
    KeyframeParam.SCALE -> copy(scale = value)
    KeyframeParam.ROTATION -> copy(rotationDeg = value)
    KeyframeParam.OPACITY -> copy(opacity = value)
    KeyframeParam.VOLUME -> copy(volume = value)
    KeyframeParam.EFFECT_INTENSITY -> copy(effectIntensity = value)
    KeyframeParam.FILTER_INTENSITY -> copy(filterIntensity = value)
    KeyframeParam.SATURATION -> copy(saturation = value)
}


fun EditorViewModel.updateKeyframe(clipId: String, keyframeId: String, transform: (Keyframe) -> Keyframe) {
    val original = _state.value.project ?: return
    val project = original.syncSharedTimeline()
    val legacy = project.clips.firstOrNull { it.id == clipId } ?: return
    val prior = project.sharedKeyframeTrackFor(clipId, legacy.keyframes).keyframes.firstOrNull { it.id == keyframeId } ?: return
    val changed = transform(prior)
    val targetTime = changed.timeMs.coerceIn(0L, project.durationMs.coerceAtLeast(0L))
    if (targetTime == prior.timeMs && changed == prior) return
    // Time drags are continuous gestures; changing values or the curve is a discrete edit.
    if (targetTime == prior.timeMs && changed != prior) pushUndo()
    val sharedUpdated = project.updateSharedKeyframeTrack(clipId) { current ->
        val moved = if (targetTime == prior.timeMs) current else current.moveAnimationKeyframe(prior.timeMs, targetTime)
        moved.setAnimationCurveAt(targetTime, changed.curve.toTimelineCurve())
            .replaceLegacyKeyframeAt(changed.copy(timeMs = targetTime))
    }
    val updated = sharedUpdated.withExactLegacyKeyframe(clipId, changed.copy(timeMs = targetTime))
    _state.update { state -> if (state.project?.id == original.id) state.copy(project = updated) else state }
    persistProject()
}


/** Apply transient curve-editor state to the live preview without saving or creating undo entries. */
fun EditorViewModel.previewKeyframeCurve(clipId: String, keyframe: Keyframe) {
    val original = _state.value.project ?: return
    val updated = original.updateSharedKeyframeTrack(clipId) { current ->
        current.setAnimationCurveAt(keyframe.timeMs, keyframe.curve.toTimelineCurve())
            .replaceLegacyKeyframeAt(keyframe)
    }.withExactLegacyKeyframe(clipId, keyframe)
    if (updated == original) return
    _state.update { state -> if (state.project?.id == original.id) state.copy(project = updated) else state }
}

/** Commit a curve-editor gesture as one undoable edit, using its pre-drag model as the undo snapshot. */
fun EditorViewModel.commitKeyframeCurve(clipId: String, originalKeyframe: Keyframe, editedKeyframe: Keyframe) {
    if (originalKeyframe == editedKeyframe) return
    val currentProject = _state.value.project ?: return
    val restoredProject = currentProject.updateSharedKeyframeTrack(clipId) { current ->
        current.setAnimationCurveAt(originalKeyframe.timeMs, originalKeyframe.curve.toTimelineCurve())
            .replaceLegacyKeyframeAt(originalKeyframe)
    }.withExactLegacyKeyframe(clipId, originalKeyframe)
    _state.update { state ->
        if (state.project?.id == currentProject.id) state.copy(project = restoredProject) else state
    }
    updateKeyframe(clipId, originalKeyframe.id) { editedKeyframe }
}

/** Keep the legacy UI/render adapter's exact curve identity and Bezier controls across a shared update. */
private fun Project.withExactLegacyKeyframe(clipId: String, keyframe: Keyframe): Project {
    val clipIndex = clips.indexOfFirst { it.id == clipId }
    if (clipIndex < 0) return this
    val clip = clips[clipIndex]
    val priorKeys = clip.keyframes.keyframes
    val targetIndex = priorKeys.indexOfFirst { it.id == keyframe.id }
        .takeIf { it >= 0 }
        ?: priorKeys.indexOfFirst { it.timeMs == keyframe.timeMs }
    val updatedKeys = if (targetIndex < 0) {
        priorKeys + keyframe
    } else {
        priorKeys.mapIndexed { index, prior -> if (index == targetIndex) keyframe else prior }
    }
    val updatedClips = clips.toMutableList()
    updatedClips[clipIndex] = clip.copy(keyframes = KeyframeTrack(updatedKeys))
    return copy(clips = updatedClips)
}


fun EditorViewModel.removeKeyframe(clipId: String, keyframeId: String) {
    val original = _state.value.project ?: return
    val project = original.syncSharedTimeline()
    val legacy = project.clips.firstOrNull { it.id == clipId } ?: return
    val selected = project.sharedKeyframeTrackFor(clipId, legacy.keyframes).keyframes.firstOrNull { it.id == keyframeId } ?: return
    pushUndo()
    val updated = project.updateSharedKeyframeTrack(clipId) { it.removeAnimationKeyframeAt(selected.timeMs) }
    _state.update { state -> if (state.project?.id == original.id) state.copy(project = updated) else state }
    persistProject()
}


fun EditorViewModel.clearKeyframes(clipId: String) {
    val original = _state.value.project ?: return
    val project = original.syncSharedTimeline()
    if (project.sharedVideoClip(clipId)?.animationKeyframeTimes().isNullOrEmpty()) return
    pushUndo()
    val updated = project.updateSharedKeyframeTrack(clipId) { it.clearAnimationKeyframes() }
    _state.update { state -> if (state.project?.id == original.id) state.copy(project = updated) else state }
    persistProject()
}

/** Paste a pin as a single undoable edit, replacing only an exact-time collision. */
fun EditorViewModel.pasteKeyframe(clipId: String, source: Keyframe, timeMs: Long) {
    val original = _state.value.project ?: return
    val project = original.syncSharedTimeline()
    if (project.clips.none { it.id == clipId }) return
    val targetTime = timeMs.coerceIn(0L, project.durationMs.coerceAtLeast(0L))
    pushUndo()
    val updated = project.updateSharedKeyframeTrack(clipId) { clip ->
        clip.copyAnimationKeyframe(source.timeMs, targetTime)
            .setAnimationCurveAt(targetTime, source.curve.toTimelineCurve())
            .replaceLegacyKeyframeAt(source.copy(timeMs = targetTime))
    }
    _state.update { state -> if (state.project?.id == original.id) state.copy(project = updated) else state }
    persistProject()
}


fun EditorViewModel.setKeyframePanelOpen(open: Boolean) =
    _state.update {
        it.copy(
            keyframePanelOpen = open,
            keyframePropertyFilter = if (open) it.keyframePropertyFilter else null,
            keyframeSelectedTimeMs = if (open) it.keyframeSelectedTimeMs else null,
            keyframeCurveEditorOpen = if (open) it.keyframeCurveEditorOpen else false
        )
    }

/** Opens the actual horizontally-scrolling manual keyframe editor on one property. */
fun EditorViewModel.openKeyframePropertyPanel(property: String) {
    require(property in setOf("POSITION", "SCALE", "ROTATION", "OPACITY"))
    _state.update { it.copy(keyframePanelOpen = true, keyframePropertyFilter = property) }
}

fun EditorViewModel.openKeyframeAnimationPanel() =
    _state.update { it.copy(keyframePanelOpen = true, keyframePropertyFilter = null, keyframeSelectedTimeMs = null, keyframeCurveEditorOpen = false) }

fun EditorViewModel.openKeyframeCurveEditor(clipId: String, timeMs: Long) {
    if (_state.value.project?.clips?.none { it.id == clipId } != false) return
    _state.update {
        it.copy(
            selectedClipId = clipId,
            keyframePanelOpen = true,
            keyframePropertyFilter = "ALL",
            keyframeSelectedTimeMs = timeMs,
            keyframeCurveEditorOpen = true
        )
    }
}

fun EditorViewModel.dismissKeyframeCurveEditor() =
    _state.update { it.copy(keyframeCurveEditorOpen = false) }


fun EditorViewModel.toggleKeyframeAtPlayhead() {
    val clipId = _state.value.selectedClipId ?: _state.value.project?.clips?.firstOrNull()?.id ?: return
    toggleKeyframeAtPlayheadFor(clipId)
}

/** Project-frame identity for APIs used by the frame-precise inspector. */
private fun EditorViewModel.projectFrameIndex(timeMs: Long): Long {
    val fps = _state.value.project?.fps?.takeIf { it > 0 } ?: 60
    return kotlin.math.round(timeMs.toDouble() * fps / 1_000.0).toLong()
}

private fun EditorViewModel.projectFrameTimeMs(frameIndex: Long): Long {
    val state = _state.value
    val fps = state.project?.fps?.takeIf { it > 0 } ?: 60
    return TimelineFrameMath.frameTimeMs(frameIndex.coerceAtLeast(0L), fps)
        .coerceAtMost(state.durationMs.coerceAtLeast(0L))
}

/**
 * Toggle a keyframe at the live playhead using project-frame identity.
 * Unlike [toggleKeyframeAtPlayhead], this deliberately does not use the
 * legacy 150ms touch-gesture tolerance.
 */
fun EditorViewModel.toggleFrameKeyframeAtPlayheadFor(clipId: String): Boolean {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return false
    val playheadMs = livePlayheadMs()
    val frame = projectFrameIndex(playheadMs)
    val existing = clip.keyframes.keyframes.firstOrNull { projectFrameIndex(it.timeMs) == frame }
    if (existing != null) {
        removeKeyframe(clipId, existing.id)
    } else {
        val frameTimeMs = projectFrameTimeMs(frame)
        addKeyframe(clipId, frameTimeMs, clip.keyframes.interpolateAt(frameTimeMs))
    }
    return true
}

/**
 * Set one parameter on the keyframe for [timeMs]'s project frame, or add
 * one sampled from the current transform when that frame has no keyframe.
 * This frame-exact path is separate from the legacy 150ms gesture upsert.
 */
fun EditorViewModel.upsertFrameKeyframeParam(
    clipId: String,
    timeMs: Long,
    param: KeyframeParam,
    value: Float
): Boolean {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return false
    val frame = projectFrameIndex(timeMs)
    val existing = clip.keyframes.keyframes.firstOrNull { projectFrameIndex(it.timeMs) == frame }
    if (existing != null) {
        if (existing.withParam(param, value) != existing) {
            pushUndo()
            updateKeyframe(clipId, existing.id) { it.withParam(param, value) }
        }
        return true
    }

    // addKeyframe owns the discrete undo entry and samples/pins every other
    // transform value; updateKeyframe then changes only the requested field.
    val frameTimeMs = projectFrameTimeMs(frame)
    val sampled = clip.keyframes.interpolateAt(frameTimeMs)
    addKeyframe(clipId, frameTimeMs, sampled)
    val inserted = _state.value.project?.clips?.firstOrNull { it.id == clipId }
        ?.keyframes?.keyframes?.firstOrNull { it.timeMs == frameTimeMs }
        ?: return false
    updateKeyframe(clipId, inserted.id) { it.withParam(param, value) }
    return true
}


fun EditorViewModel.jumpToNextKeyframe() {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == _state.value.selectedClipId }
        ?: _state.value.project?.clips?.firstOrNull() ?: return
    val currentMs = livePlayheadMs()
    val next = clip.keyframes.keyframes.filter { it.timeMs > currentMs + 50L }.minByOrNull { it.timeMs }
    if (next != null) {
        seekTo(next.timeMs)
    }
}


fun EditorViewModel.jumpToPrevKeyframe() {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == _state.value.selectedClipId }
        ?: _state.value.project?.clips?.firstOrNull() ?: return
    val currentMs = livePlayheadMs()
    val prev = clip.keyframes.keyframes.filter { it.timeMs < currentMs - 50L }.maxByOrNull { it.timeMs }
    if (prev != null) {
        seekTo(prev.timeMs)
    }
}


fun EditorViewModel.setTimelineZoom(zoom: Float) {
    _state.update { it.copy(timelineZoom = zoom.coerceIn(TIMELINE_MIN_ZOOM, TIMELINE_MAX_ZOOM)) }
}


fun EditorViewModel.zoomInTimeline() {
    val current = _state.value.timelineZoom
    setTimelineZoom((current + 0.5f).coerceAtMost(TIMELINE_MAX_ZOOM))
}


fun EditorViewModel.zoomOutTimeline() {
    val current = _state.value.timelineZoom
    setTimelineZoom((current - 0.5f).coerceAtLeast(TIMELINE_MIN_ZOOM))
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
    persistProject(refreshLegacyClipFields = true)
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
    persistProject(refreshLegacyClipFields = true)
}


fun EditorViewModel.reorderMainVideoClips(orderedIds: List<String>) {
    val main = _state.value.project?.clips?.filter(::isMainTrack).orEmpty()
    if (orderedIds.size != main.size || orderedIds.toSet() != main.map { it.id }.toSet()) return
    if (orderedIds == main.map { it.id }) return
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val mainClips = p.clips.filter(::isMainTrack)
        val byId = mainClips.associateBy { it.id }
        val requested = orderedIds.map { byId.getValue(it) }
        val grouped = requested.groupBy { it.groupId }.filter { (key, value) -> key != null && value.size > 1 }
        val normalized = requested.toMutableList()
        grouped.forEach { (groupId, members) ->
            val positions = normalized.mapIndexedNotNull { i, clip -> if (clip.groupId == groupId) i else null }
            if (positions.last() - positions.first() + 1 != positions.size) {
                normalized.removeAll { it.groupId == groupId }
                normalized.addAll(positions.last().coerceAtMost(normalized.size), members)
            }
        }
        var index = 0
        s.copy(project = p.copy(clips = p.clips.map { if (isMainTrack(it)) normalized[index++] else it }))
    }
    persistProject(refreshLegacyClipFields = true)
}

fun EditorViewModel.reorderClips(fromIndex: Int, toIndex: Int) {
    val main = _state.value.project?.clips?.filter(::isMainTrack).orEmpty()
    if (fromIndex !in main.indices || toIndex !in main.indices || fromIndex == toIndex) return
    val source = main[fromIndex]
    val group = source.groupId?.let { gid -> main.filter { it.groupId == gid } } ?: listOf(source)
    val remaining = main.filterNot { it.id in group.map { member -> member.id }.toSet() }.toMutableList()
    val insertion = toIndex.coerceIn(0, remaining.size)
    remaining.addAll(insertion, group)
    reorderMainVideoClips(remaining.map { it.id })
}
fun EditorViewModel.applyConfiguredAnimationPreset(
    preset: com.apexstudio.app.data.animation.AnimationPresetType,
    mode: com.apexstudio.app.data.animation.AnimationPresetMode,
    durationMs: Long,
    intensity: Float
): Boolean {
    val state = _state.value
    val project = state.project ?: return false
    val clip = state.keyframeAnchor() ?: return false
    val span = clip.effectiveDurationMs().coerceAtLeast(1L)
    val requested = durationMs.coerceIn(
        com.apexstudio.app.data.animation.AnimationPresetOptions.MIN_DURATION_MS,
        com.apexstudio.app.data.animation.AnimationPresetOptions.MAX_DURATION_MS)
    val actualDuration = minOf(requested, span)
    val clipStart = SpeedCurveMath.timelineStartMsForClip(project.clips, clip)
    val start = if (mode == com.apexstudio.app.data.animation.AnimationPresetMode.OUT)
        clipStart + span - actualDuration else clipStart
    val generated = com.apexstudio.app.data.animation.AnimationPresetOptions.createTrack(
        preset, mode, requested, intensity.takeIf { it.isFinite() } ?: 1f)
    val track = KeyframeTrack(generated.keyframes.map { pin ->
        pin.copy(timeMs = start + (pin.timeMs.toDouble() / requested * actualDuration).toLong())
    }).sorted()
    pushUndo()
    updateClip(clip.id) { it.copy(keyframes = track) }
    return true
}

fun EditorViewModel.applyAnimationPreset(preset: com.apexstudio.app.data.animation.AnimationPresetType) {
    val clipId = _state.value.selectedClipId ?: _state.value.project?.clips?.firstOrNull()?.id ?: return
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    pushUndo()
    val playhead = livePlayheadMs()
    val clipDuration = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(600L)
    val animDuration = minOf(1200L, clipDuration)
    val startMs = if (playhead >= clip.timelineOffsetMs && playhead < clip.timelineOffsetMs + clipDuration) {
        playhead
    } else {
        clip.timelineOffsetMs
    }
    val track = com.apexstudio.app.data.animation.AnimationPresets.createTrack(preset, startMs, animDuration)
    updateClip(clipId) { it.copy(keyframes = track) }
    // The clip the preset landed on STAYS selected: Edit Pack (and every
    // other tool) says "applies to the selected clip", so an Animation
    // apply must never leave the user with no selection.
    _state.update { it.copy(selectedClipId = clipId) }
}


private fun EditorViewModel.undoSnapshot(): EditorUndoSnapshot? = _state.value.let { s ->
    s.project?.let { EditorUndoSnapshot(it, s.hiddenVideoLayers, s.lockedVideoLayers, s.extraVideoLayers) }
}

private fun EditorViewModel.restoreUndoSnapshot(snapshot: EditorUndoSnapshot) {
    val duration = snapshot.project.durationMs.coerceAtLeast(0L)
    val position = livePlayheadMs().coerceIn(0L, duration)
    _playerPosition.value = position
    _audio.update { it.copy(tracks = snapshot.project.audioTracks) }
    _state.update { state ->
        val activeClip = snapshot.project.clips.firstOrNull { it.id == state.selectedClipId }
            ?: snapshot.project.clips.firstOrNull {
                it.trackIndex == 0 && it.type == ClipType.VIDEO
            }
        val restoredFilter = activeClip?.gpuFilterConfig ?: com.apexstudio.app.data.filter.GpuFilterConfig()
        state.copy(
            project = snapshot.project,
            activeGpuFilterConfig = restoredFilter,
            activeFilterId = restoredFilter.filterPresetId,
            filterIntensity = restoredFilter.filterIntensity,
            durationMs = duration,
            playerPositionMs = position,
            currentTimeMs = position,
            hiddenVideoLayers = snapshot.hiddenVideoLayers,
            lockedVideoLayers = snapshot.lockedVideoLayers,
            extraVideoLayers = snapshot.extraVideoLayers,
            selectedClipId = state.selectedClipId?.takeIf { id ->
                snapshot.project.clips.any { it.id == id }
            },
            selectedOverlayClipId = state.selectedOverlayClipId?.takeIf { id ->
                snapshot.project.clips.any { it.id == id && it.type == ClipType.OVERLAY }
            },
            selectedTextOverlayId = state.selectedTextOverlayId?.takeIf { id ->
                snapshot.project.clips.any { clip -> clip.textOverlays.any { it.id == id } }
            },
            selectedStickerId = state.selectedStickerId?.takeIf { id ->
                snapshot.project.stickers.any { it.id == id }
            },
            selectedAudioTrackId = state.selectedAudioTrackId?.takeIf { id ->
                snapshot.project.audioTracks.any { it.id == id }
            },
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
    }
    persistProject()
}

fun EditorViewModel.undo() = viewModelScope.launch {
    val current = undoSnapshot() ?: return@launch
    if (undoStack.isEmpty()) return@launch
    redoStack.addLast(current)
    val prev = undoStack.removeLast()
    restoreUndoSnapshot(prev)
}


fun EditorViewModel.redo() = viewModelScope.launch {
    val current = undoSnapshot() ?: return@launch
    if (redoStack.isEmpty()) return@launch
    undoStack.addLast(current)
    val next = redoStack.removeLast()
    restoreUndoSnapshot(next)
}


/**
 * CapCut magnet / ripple rule for the main track.
 *
 * Any edit that CHANGES V1's length (trim, duplicate — and delete below)
 * must carry every overlay layer that starts at or after the edit
 * boundary along by the same delta, otherwise the layers drift away from
 * the picture they were placed against (the layer-sync complaint).
 * Anchor is the timeline boundary where the change begins; anything
 * starting earlier is untouched. Track 0 itself never moves here — its
 * own layout is derived cumulatively.
 */
internal fun isMainTrack(c: MediaClip): Boolean =
    c.trackIndex == 0 &&
        (c.type == ClipType.VIDEO || c.type == ClipType.IMAGE)

/** Timeline ms where [clipId] starts on the main track (cumulative V1). */
internal fun v1StartBefore(clips: List<MediaClip>, clipId: String): Long {
    var acc = 0L
    for (c in clips) {
        if (c.id == clipId) return acc
        if (isMainTrack(c)) acc += c.effectiveDurationMs()
    }
    return acc
}

/** Shift every non-main-track item at/after [anchorMs] by [deltaMs]. */
internal fun rippleOverlays(
    clips: List<MediaClip>,
    anchorMs: Long,
    deltaMs: Long
): List<MediaClip> {
    if (deltaMs == 0L) return clips
    return clips.map { c ->
        if (c.trackIndex == 0 || c.timelineOffsetMs < anchorMs) c
        else c.copy(
            timelineOffsetMs = (c.timelineOffsetMs + deltaMs).coerceAtLeast(0L)
        )
    }
}

fun EditorViewModel.trimClip(clipId: String, startMs: Long, endMs: Long) {
    val source = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    if (source.durationMs <= 0L) return
    val requestedStart = startMs.coerceIn(0L, (source.durationMs - 1L).coerceAtLeast(0L))
    val requestedEnd = endMs.coerceIn(
        requestedStart + minOf(100L, source.durationMs - requestedStart),
        source.durationMs
    )
    if (requestedStart == source.trimStartMs && requestedEnd == source.trimEndMs) return
    pushUndo()
    // Clamp from the LIVE playhead (the state mirror can lag it by up
    // to 100ms) for the STATE mirror. The LIVE flow is clamped
    // separately below from its CURRENT value (atomic update) — NOT
    // from the stale liveBefore snapshot: the 33ms playback loop keeps
    // advancing live while _state.update runs, so writing liveBefore
    // back would clobber the loop's newer tick and snap the playhead
    // backward during playback.
    val liveBefore = livePlayheadMs()
    var newDurationMs: Long? = null
    _state.update { s ->
        val proj = s.project ?: return@update s
        // Ripple: a main-track trim changes V1's length, so overlays
        // at/after the edit boundary travel with it (CapCut magnet).
        val trimTarget = proj.clips.firstOrNull { it.id == clipId }
        var rippled = proj.clips
        if (trimTarget != null && isMainTrack(trimTarget)) {
            val safeStart = startMs.coerceIn(0L, (trimTarget.durationMs - 1L).coerceAtLeast(0L))
            // Guard the clamp: minOf keeps coerceIn's min <= max when
            // the clip is under 100ms (Kilo CRITICAL: IllegalArgumentException).
            val safeEnd = endMs.coerceIn(
                safeStart + minOf(100L, trimTarget.durationMs - safeStart),
                trimTarget.durationMs
            )
            val oldDur = trimTarget.effectiveDurationMs()
            val newDur = trimTarget.copy(trimStartMs = safeStart, trimEndMs = safeEnd).effectiveDurationMs()
            val anchor = v1StartBefore(proj.clips, clipId) + minOf(oldDur, newDur)
            rippled = rippleOverlays(proj.clips, anchor, newDur - oldDur)
        }
        val updatedClips = rippled.map { c ->
            if (c.id == clipId) {
                // Preserve a valid requested source in-point even when less than 100 ms
                // remains. Pulling the opposite handle backward makes a tail trim jump.
                val safeStart = startMs.coerceIn(0L, (c.durationMs - 1L).coerceAtLeast(0L))
                val safeEnd = endMs.coerceIn(
                    safeStart + minOf(100L, c.durationMs - safeStart),
                    c.durationMs
                )
                // Overlays use absolute timeline placement, so moving the
                // source head either way also moves the left edge. V1 is
                // laid out cumulatively and does not use this adjustment.
                val overlayStartShift = overlayTimelineStartShift(c, safeStart, safeEnd)
                c.copy(
                    trimStartMs = safeStart,
                    trimEndMs = safeEnd,
                    timelineOffsetMs = if (c.type == ClipType.OVERLAY) {
                        (c.timelineOffsetMs + overlayStartShift).coerceAtLeast(0L)
                    } else c.timelineOffsetMs
                )
            } else c
        }
        val newDuration = computeTimelineDurationMs(updatedClips, s.project?.audioTracks ?: emptyList())
        newDurationMs = newDuration
        val newPlayhead = liveBefore.coerceIn(0L, newDuration)
        s.copy(
            project = proj.copy(clips = updatedClips, durationMs = newDuration),
            durationMs = newDuration,
            playerPositionMs = newPlayhead,
            currentTimeMs = newPlayhead,
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
    }
    // Live clamp: only ever pulls live DOWN to the new duration bound
    // (when the trim shrank the timeline below the current position);
    // otherwise a no-op, never a backward snap.
    newDurationMs?.let { d -> _playerPosition.update { it.coerceIn(0L, d) } }
    persistProject(refreshLegacyClipFields = true)
}


fun EditorViewModel.openTrimPanel() = _state.update { it.copy(trimPanelOpen = true) }

fun EditorViewModel.closeTrimPanel() = _state.update { it.copy(trimPanelOpen = false) }


fun EditorViewModel.setTrimStartAtPlayhead(clipId: String) {
    val clips = _state.value.project?.clips ?: return
    val clip = clips.firstOrNull { it.id == clipId } ?: return
    if (clip.durationMs <= 0L) return
    val playhead = ClipTimeMapping.sourceAtProjectTime(clips, clip, livePlayheadMs())
    val newStart = playhead.coerceIn(0L, (clip.trimEndMs - 100L).coerceAtLeast(0L))
    trimClip(clipId, newStart, clip.trimEndMs)
}


fun EditorViewModel.setTrimEndAtPlayhead(clipId: String) {
    val clips = _state.value.project?.clips ?: return
    val clip = clips.firstOrNull { it.id == clipId } ?: return
    if (clip.durationMs <= 0L) return
    val playhead = ClipTimeMapping.sourceAtProjectTime(clips, clip, livePlayheadMs())
    // A valid short clip (or a restored in-point close to the source end) may have
    // less than 100 ms remaining. Never construct a reversed coerceIn range.
    val start = clip.trimStartMs.coerceIn(0L, clip.durationMs)
    val minimumEnd = start + minOf(100L, clip.durationMs - start)
    val newEnd = playhead.coerceIn(minimumEnd, clip.durationMs)
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
                            trimEndMs = result.durationMs,
                            faceCropTrack = null,
                        )
                        _state.update { s ->
                            val p = s.project ?: return@update s
                            val updatedClips = p.clips + newClip
                            val newDuration = computeTimelineDurationMs(updatedClips, s.project?.audioTracks ?: emptyList())
                            s.copy(
                            project = p.copy(clips = updatedClips, durationMs = newDuration),
                            durationMs = newDuration
                        )
                    }
                    persistProject(importLegacyAnimation = true, refreshLegacyClipFields = true)
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
    // Overlay no-op guard BEFORE pushUndo: splitting with the playhead
    // outside the overlay's span must not burn an undo entry.
    val preClip = _state.value.project?.clips?.firstOrNull { it.id == clipId }
    if (preClip != null && preClip.type == com.apexstudio.app.domain.model.ClipType.OVERLAY) {
        val span = preClip.trimEndMs - preClip.trimStartMs
        if (atMs < preClip.timelineOffsetMs || atMs > preClip.timelineOffsetMs + span) return
    }
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val c = p.clips.firstOrNull { it.id == clipId } ?: return@update s
        // OVERLAY clips are positioned by timelineOffsetMs, so the
        // playhead (timeline time) must be converted to a SOURCE time:
        // sourceSplit = trimStart + (playhead - offset). The old code
        // fed the timeline time straight into trimStartMs/trimEndMs,
        // which splits at the wrong source frame whenever the overlay
        // does not start at 00:00. The second half also starts at
        // offset + (sourceSplit - trimStart) — the old code kept the
        // original offset, so both halves overlapped exactly.
        val isOverlay = c.type == com.apexstudio.app.domain.model.ClipType.OVERLAY
        val isMainVideo = c.trackIndex == 0 && c.type == ClipType.VIDEO
        val sourceSplit = if (isOverlay) {
            c.trimStartMs + (atMs - c.timelineOffsetMs)
        } else if (isMainVideo) {
            ClipTimeMapping.sourceAtProjectTime(p.clips, c, atMs)
        } else atMs
        val safeSplit = sourceSplit.coerceIn(c.trimStartMs + 100L, (c.trimEndMs - 100L).coerceAtLeast(c.trimStartMs + 100L))

        val originalEnd = c.trimEndMs
        val newClip = c.copy(
            id = "clip_${System.currentTimeMillis()}_split",
            trimStartMs = safeSplit,
            trimEndMs = originalEnd,
            // Overlay and V1 trim bounds remain in source-time coordinates;
            // retain the original source-duration ceiling for later trim edits.
            durationMs = if (isOverlay || isMainVideo) c.durationMs else originalEnd - safeSplit,
            // Second half starts where the first half ends.
            timelineOffsetMs = if (isOverlay) {
                c.timelineOffsetMs + (safeSplit - c.trimStartMs)
            } else c.timelineOffsetMs
        )
        val updatedClips = mutableListOf<com.apexstudio.app.domain.model.MediaClip>()
        for (clip in p.clips) {
            if (clip.id == clipId) {
                updatedClips.add(
                    clip.copy(
                        trimEndMs = safeSplit,
                        durationMs = if (isOverlay || isMainVideo) clip.durationMs else safeSplit - clip.trimStartMs
                    )
                )
                updatedClips.add(newClip)
            } else {
                updatedClips.add(clip)
            }
        }
        val newDur = computeTimelineDurationMs(updatedClips, s.project?.audioTracks ?: emptyList())
        s.copy(
            project = p.copy(clips = updatedClips, durationMs = newDur),
            durationMs = newDur,
            selectedClipId = newClip.id,
            selectedOverlayClipId = if (isOverlay) newClip.id else s.selectedOverlayClipId,
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
    }
    persistProject(
        importLegacyAnimation = true,
        refreshLegacyClipFields = true,
        refreshLegacyTextStickerFields = true
    )
}


fun EditorViewModel.duplicateClip(clipId: String) {
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val target = p.clips.firstOrNull { it.id == clipId } ?: return@update s
        val isOverlay = target.type == com.apexstudio.app.domain.model.ClipType.OVERLAY
        // Overlays are positioned by timelineOffsetMs: an exact copy
        // would land precisely on top of the original (invisible,
        // unselectable underneath). CapCut-style: place the duplicate
        // right after the original on the same track.
        val newClip = target.copy(
            id = "clip_${System.currentTimeMillis()}_dup",
            timelineOffsetMs = if (isOverlay) {
                target.timelineOffsetMs + (target.trimEndMs - target.trimStartMs)
            } else target.timelineOffsetMs
        )
        // Ripple: duplicating a main-track clip lengthens V1 after the
        // original's end, so overlays downstream shift right by that
        // length — layers stay glued to their scene (CapCut magnet).
        val rippled = if (isMainTrack(target)) {
            val extra = target.effectiveDurationMs()
            rippleOverlays(p.clips, p.clips.sumOf { if (isMainTrack(it)) it.effectiveDurationMs() else 0L }, extra)
        } else p.clips
        val updated = rippled + newClip
        val newDur = computeTimelineDurationMs(updated, s.project?.audioTracks ?: emptyList())
        s.copy(
            project = p.copy(clips = updated, durationMs = newDur),
            durationMs = newDur,
            selectedClipId = newClip.id,
            selectedOverlayClipId = if (isOverlay) newClip.id else s.selectedOverlayClipId,
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
    }
    persistProject(
        importLegacyAnimation = true,
        refreshLegacyClipFields = true,
        refreshLegacyTextStickerFields = true
    )
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
        // Ripple: deleting a main-track clip pulls everything after its
        // start (other V1 clips by cumulative layout, overlays here) left.
        val gone = p.clips.firstOrNull { it.id == clipId }
        val rippled = if (gone != null && isMainTrack(gone)) {
            rippleOverlays(p.clips, v1StartBefore(p.clips, clipId), -gone.effectiveDurationMs())
        } else p.clips
        val updated = rippled.filterNot { it.id == clipId }
        val newSelected = if (s.selectedClipId == clipId) updated.firstOrNull()?.id else s.selectedClipId
        val newDur = computeTimelineDurationMs(updated, s.project?.audioTracks ?: emptyList())
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
    persistProject(refreshLegacyClipFields = true, refreshLegacyTextStickerFields = true)
}


/**
 * Deletes multiple clips in a single operation: one undo entry, one state
 * update, one project persist. Prefer this over calling [deleteClip] in a
 * loop for multi-select bulk delete.
 */
fun EditorViewModel.deleteClips(clipIds: Set<String>) {
    // Keep this legacy UI route on the same ripple-aware implementation as
    // the multi-select action. Selection setup is transient; the action still
    // records exactly one undo snapshot and performs one persistence.
    val valid = _state.value.project?.clips.orEmpty().map { it.id }.toSet().intersect(clipIds)
    if (valid.isEmpty()) return
    _state.update { it.copy(selectedClipIds = valid, multiSelectMode = true) }
    deleteSelectedClips()
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
    persistProject(importLegacyAnimation = true, refreshLegacyClipFields = true)
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
            val newDur = computeTimelineDurationMs(updated, s.project?.audioTracks ?: emptyList())
            s.copy(
                project = p.copy(clips = updated, durationMs = newDur),
                durationMs = newDur,
                selectedClipId = newClip.id
            )
        }
        persistProject(importLegacyAnimation = true, refreshLegacyClipFields = true)
    }
}


fun EditorViewModel.setClipTimelineOffset(clipId: String, offsetMs: Long) {
    val before = _state.value
    val source = before.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val zoom = before.timelineZoom.coerceIn(TIMELINE_MIN_ZOOM, TIMELINE_MAX_ZOOM)
    val thresholdMs = (8_000f / (54f * zoom)).toLong().coerceAtLeast(1L)
    val snappedOffset = findMagneticSnapPoint(
        offsetMs.coerceAtLeast(0L), thresholdMs, excludeClipId = clipId
    ).coerceAtLeast(0L)
    if (source.timelineOffsetMs == snappedOffset) return
    val delta = snappedOffset - source.timelineOffsetMs
    val groupId = source.groupId
    val members = before.project?.clips.orEmpty().filter { groupId != null && it.groupId == groupId }
    val effectiveDelta = if (members.isEmpty()) delta else delta.coerceAtLeast(-members.minOf { it.timelineOffsetMs })
    if (effectiveDelta == 0L) return
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val updated = p.clips.map {
            if (it.id == clipId || (groupId != null && it.groupId == groupId))
                it.copy(timelineOffsetMs = (it.timelineOffsetMs + effectiveDelta).coerceAtLeast(0L))
            else it
        }
        s.copy(project = p.copy(clips = updated))
    }
    persistProject(refreshLegacyClipFields = true)
}


fun EditorViewModel.shiftClipTimelineOffset(clipId: String, deltaMs: Long) {
    if (deltaMs == 0L) return
    val before = _state.value.project ?: return
    val source = before.clips.firstOrNull { it.id == clipId } ?: return
    val members = before.clips.filter { source.groupId != null && it.groupId == source.groupId }
    val effectiveDelta = if (members.isEmpty()) deltaMs else deltaMs.coerceAtLeast(-members.minOf { it.timelineOffsetMs })
    if (effectiveDelta == 0L) return
    pushUndo()
    _state.update { s ->
        val p = s.project ?: return@update s
        val updated = p.clips.map {
            if (it.id == clipId || (source.groupId != null && it.groupId == source.groupId))
                it.copy(timelineOffsetMs = (it.timelineOffsetMs + effectiveDelta).coerceAtLeast(0L))
            else it
        }
        s.copy(project = p.copy(clips = updated))
    }
    persistProject(refreshLegacyClipFields = true)
}
internal fun EditorViewModel.pushUndo() {
    val state = _state.value
    val project = state.project ?: return
    val current = EditorUndoSnapshot(project, state.hiddenVideoLayers, state.lockedVideoLayers, state.extraVideoLayers)
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
    val original = _state.value.project ?: return
    val project = original.syncSharedTimeline()
    val legacy = project.clips.firstOrNull { it.id == clipId } ?: return
    val shared = project.sharedVideoClip(clipId) ?: return
    val playheadMs = livePlayheadMs()
    val existingTime = shared.animationKeyframeTimes()
        .minByOrNull { kotlin.math.abs(it - playheadMs) }
        ?.takeIf { kotlin.math.abs(it - playheadMs) <= 150L }
    if (existingTime != null) {
        val marker = project.sharedKeyframeTrackFor(clipId, legacy.keyframes).keyframes
            .firstOrNull { it.timeMs == existingTime } ?: return
        removeKeyframe(clipId, marker.id)
    } else {
        addKeyframe(clipId, playheadMs, shared.animationTransformAt(playheadMs))
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
    val original = _state.value.project ?: return
    val project = original.syncSharedTimeline(importLegacyAnimation = true)
    val item = project.sharedTextItem(clipId, overlayId) ?: return
    val playheadMs = livePlayheadMs()
    val existingTime = item.animationKeyframeTimes().firstOrNull { time ->
        val delta = time - playheadMs
        delta in -150L..150L
    }
    val updatedItem = if (existingTime != null) item.removeAnimationKeyframeAt(existingTime)
        else item.addAnimationKeyframeAt(playheadMs)
    pushUndo()
    val updatedProject = project.updateSharedTextItem(clipId, overlayId) { updatedItem }
    _state.update { state -> if (state.project?.id == original.id) state.copy(project = updatedProject) else state }
    persistProject()
}


/**
 * Moves a text-overlay keyframe to a new timeline position (from diamond drag).
 */
fun EditorViewModel.moveTextKeyframe(clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) {
    val original = _state.value.project ?: return
    val project = original.syncSharedTimeline(importLegacyAnimation = true)
    val oldTime = project.clips.firstOrNull { it.id == clipId }?.textOverlays
        ?.firstOrNull { it.id == overlayId }?.keyframes?.keyframes
        ?.firstOrNull { it.id == keyframeId }?.timeMs ?: return
    pushUndo()
    val updatedProject = project.updateSharedTextItem(clipId, overlayId) {
        it.moveAnimationKeyframe(oldTime, newTimeMs.coerceAtLeast(0L))
    }
    _state.update { state -> if (state.project?.id == original.id) state.copy(project = updatedProject) else state }
    persistProject()
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
    pushUndo()
    _state.update { s ->
        val hidden = s.hiddenVideoLayers.toMutableSet()
        if (l in hidden) hidden.remove(l) else hidden.add(l)
        s.copy(
            project = s.project?.copy(hiddenVideoLayers = hidden),
            hiddenVideoLayers = hidden
        )
    }
    persistProject()
}

/** Toggle a video layer's lock. Locked layers can't be edited until unlocked. */
fun EditorViewModel.toggleVideoLayerLock(layer: Int) {
    val l = layer.coerceIn(0, MAX_VIDEO_LAYERS - 1)
    pushUndo()
    _state.update { s ->
        val locked = s.lockedVideoLayers.toMutableSet()
        if (l in locked) locked.remove(l) else locked.add(l)
        s.copy(
            project = s.project?.copy(lockedVideoLayers = locked),
            lockedVideoLayers = locked
        )
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
            project = p.copy(
                clips = remaining,
                hiddenVideoLayers = hidden,
                lockedVideoLayers = locked
            ),
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

/**
 * Single source of truth for the project timeline's total duration.
 *
 * ROOT-CAUSE FIX (Prince: "video add karne ke baad timeline bahut fast ho jati hai"):
 * V1 (trackIndex 0) lays clips SEQUENTIALLY, so its span is the SUM of trimmed
 * durations — but onMediaPicked/deleteClip used MAX. With 2+ clips the ruler,
 * playhead and loop point only covered MAX while the clips visually spanned
 * SUM, so the playhead raced across the timeline (2x fast with 2 clips, and
 * the track content even overflowed the timeline width). Overlay layers
 * (1..9) are absolutely positioned by timelineOffsetMs, so their contribution
 * is MAX(offset + trimmed). Audio tracks may legitimately extend the timeline
 * (music longer than the video), so their max end is included too.
 */
fun computeTimelineDurationMs(
    clips: List<com.apexstudio.app.domain.model.MediaClip>,
    audioTracks: List<com.apexstudio.app.domain.model.AudioTrack> = emptyList()
): Long {
    val v1SequentialMs = clips
        .filter {
            it.trackIndex == 0 &&
                (it.type == com.apexstudio.app.domain.model.ClipType.VIDEO ||
                 it.type == com.apexstudio.app.domain.model.ClipType.IMAGE)
        }
        // A speed-ramped clip occupies its OUTPUT length on the timeline.
        .sumOf { it.effectiveDurationMs() }
    val overlayMaxEndMs = clips
        .filter { it.trackIndex != 0 }
        .maxOfOrNull { it.timelineOffsetMs + it.effectiveDurationMs() }
        ?: 0L
    // Audio trim bounds are source coordinates. Its project end is the
    // persisted placement plus the playable source span.
    val audioMaxEndMs = audioTracks.maxOfOrNull {
        it.startTimeMs.coerceAtLeast(0L) + (it.trimEndMs - it.trimStartMs).coerceAtLeast(0L)
    } ?: 0L
    return maxOf(v1SequentialMs, overlayMaxEndMs, audioMaxEndMs).coerceAtLeast(1000L)
}
