package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.OverlayBlend
import kotlinx.coroutines.flow.update

/**
 * PiP / image overlay canvas editing (Prince round-4 feedback item 2a:
 * "Overlay picture lag gaya lekin EDIT nahi ho raha").
 *
 * Overlay clips (MediaClip with type == ClipType.OVERLAY) carry their own
 * transform (pipX / pipY / pipScale / pipRotationDeg / pipOpacity) on the
 * clip itself — not in transient UI state — so the live preview canvas
 * and the export GL effect always render the same thing.
 *
 * Gesture flow mirrors the sticker canvas: drag / pinch handlers call the
 * move/scale/rotate variants with persist = false for live preview, then
 * [persistOverlayClipGesture] once the gesture ends to write the project.
 *
 * Undo: every overlay mutation is undoable. Continuous gestures /
 * sliders arm ONE undo entry per interaction (see [armOverlayUndo]):
 * the first live update snapshots the pre-gesture state; the rest of
 * the interaction does not push more entries, so one Undo restores the
 * state from before the drag. Discrete actions (add / delete / keyframe
 * add / remove / track move) push their own entry via the generic
 * functions they call.
 */

/**
 * Snapshot the current clips for undo, at most once per interaction.
 * Call on the first live update of a gesture / slider drag; call
 * [disarmOverlayUndo] when the interaction ends.
 */
fun EditorViewModel.armOverlayUndo() {
    if (!overlayEditUndoArmed) {
        pushUndo()
        overlayEditUndoArmed = true
    }
}

/** Allow the next overlay interaction to push its own undo entry. */
fun EditorViewModel.disarmOverlayUndo() {
    overlayEditUndoArmed = false
}

fun EditorViewModel.selectOverlayClip(id: String?) =
    _state.update { state ->
        // Single selection: picking an overlay on the canvas also makes
        // it the timeline selection (so the clip bar + properties panel
        // target it); deselecting it clears the timeline selection only
        // if the timeline selection IS an overlay.
        val selectedClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
        state.copy(
            selectedOverlayClipId = id,
            selectedClipId = when {
                id != null -> id
                selectedClip?.type == ClipType.OVERLAY -> null
                else -> state.selectedClipId
            }
        )
    }

fun EditorViewModel.moveOverlayClip(
    clipId: String,
    dx: Float,
    dy: Float,
    persist: Boolean = true
) {
    if (!persist) armOverlayUndo()
    updateClip(clipId, persist) { clip ->
        clip.copy(
            pipX = (clip.pipX + dx).coerceIn(0f, 1f),
            pipY = (clip.pipY + dy).coerceIn(0f, 1f)
        )
    }
}

fun EditorViewModel.scaleOverlayClip(
    clipId: String,
    scale: Float,
    persist: Boolean = true
) {
    if (!persist) armOverlayUndo()
    updateClip(clipId, persist) { clip ->
        clip.copy(pipScale = scale.coerceIn(MediaClipPipScale.MIN, MediaClipPipScale.MAX))
    }
}

fun EditorViewModel.rotateOverlayClip(
    clipId: String,
    deltaDeg: Float,
    persist: Boolean = true
) {
    if (!persist) armOverlayUndo()
    updateClip(clipId, persist) { clip ->
        clip.copy(pipRotationDeg = (clip.pipRotationDeg + deltaDeg) % 360f)
    }
}

fun EditorViewModel.setOverlayClipOpacity(clipId: String, opacity: Float) {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId }
    if (clip != null && clip.keyframes.keyframes.isNotEmpty()) {
        // CapCut-style auto-keyframe: once the overlay has keyframes,
        // the Opacity slider pins opacity at the playhead (snapping to
        // the nearest keyframe within 1s, like canvas gestures) instead
        // of moving the base value. With no keyframes the slider keeps
        // its existing meaning: edit the base pipOpacity.
        val t = clip.keyframes.interpolateAt(livePlayheadMs())
        upsertOverlayKeyframe(
            clipId,
            livePlayheadMs(),
            t.translateX,
            t.translateY,
            t.scale,
            t.rotationDeg,
            opacity
        )
        return
    }
    armOverlayUndo()
    updateClip(clipId) { clip ->
        clip.copy(pipOpacity = opacity.coerceIn(0f, 1f))
    }
}

/** Flush the project after a gesture that streamed persist = false updates. */
fun EditorViewModel.persistOverlayClipGesture(clipId: String) {
    disarmOverlayUndo()
    updateClip(clipId, persist = true) { it }
}

fun EditorViewModel.removeOverlayClip(clipId: String) {
    deleteClip(clipId)
    if (_state.value.selectedOverlayClipId == clipId) {
        _state.update { it.copy(selectedOverlayClipId = null) }
    }
}

// ---- Properties panel setters (all undoable, one entry per drag) ----

fun EditorViewModel.setOverlayBlend(clipId: String, blend: OverlayBlend) {
    armOverlayUndo()
    updateClip(clipId) { it.copy(overlayBlend = blend) }
}

fun EditorViewModel.setOverlayBorder(
    clipId: String,
    widthFraction: Float,
    colorArgb: Int
) {
    armOverlayUndo()
    updateClip(clipId) {
        it.copy(
            overlayBorderWidth = widthFraction.coerceIn(0f, 0.05f),
            overlayBorderColor = colorArgb
        )
    }
}

fun EditorViewModel.setOverlayShadow(clipId: String, strength: Float) {
    armOverlayUndo()
    updateClip(clipId) { it.copy(overlayShadow = strength.coerceIn(0f, 1f)) }
}

fun EditorViewModel.setOverlayFlip(
    clipId: String,
    flipH: Boolean,
    flipV: Boolean
) {
    armOverlayUndo()
    updateClip(clipId) { it.copy(overlayFlipH = flipH, overlayFlipV = flipV) }
}

fun EditorViewModel.setOverlayVolume(clipId: String, volume: Float) {
    armOverlayUndo()
    updateClip(clipId) { it.copy(overlayVolume = volume.coerceIn(0f, 2f)) }
}

// ---- Overlay keyframes ----

/**
 * Insert or replace a keyframe at [timeMs] with the given ABSOLUTE
 * overlay transform (translateX = pipX, translateY = pipY,
 * scale = pipScale, rotationDeg, opacity). Called by canvas gestures
 * while the clip has keyframes, and by the properties panel's
 * "Add keyframe" button.
 *
 * The upsert snaps to the nearest existing keyframe within 1s (so a
 * drag during playback edits that keyframe instead of spraying one
 * per frame); otherwise it creates a new keyframe at [timeMs].
 */
fun EditorViewModel.upsertOverlayKeyframe(
    clipId: String,
    timeMs: Long,
    x: Float,
    y: Float,
    scale: Float,
    rotationDeg: Float,
    opacity: Float,
    persist: Boolean = true
) {
    armOverlayUndo()
    updateClip(clipId, persist = persist) { clip ->
        val kfs = clip.keyframes.keyframes
        val nearest = kfs.minByOrNull { kotlin.math.abs(it.timeMs - timeMs) }
        val newKf = if (nearest != null && kotlin.math.abs(nearest.timeMs - timeMs) <= 1000L) {
            nearest.copy(
                translateX = x.coerceIn(0f, 1f),
                translateY = y.coerceIn(0f, 1f),
                scale = scale.coerceIn(MediaClipPipScale.MIN, MediaClipPipScale.MAX),
                rotationDeg = rotationDeg,
                opacity = opacity.coerceIn(0f, 1f)
            )
        } else {
            Keyframe(
                id = java.util.UUID.randomUUID().toString(),
                timeMs = timeMs,
                translateX = x.coerceIn(0f, 1f),
                translateY = y.coerceIn(0f, 1f),
                scale = scale.coerceIn(MediaClipPipScale.MIN, MediaClipPipScale.MAX),
                rotationDeg = rotationDeg,
                opacity = opacity.coerceIn(0f, 1f)
            )
        }
        val updated = if (nearest != null && kotlin.math.abs(nearest.timeMs - timeMs) <= 1000L) {
            kfs.map { if (it.id == nearest.id) newKf else it }
        } else {
            (kfs + newKf).sortedBy { it.timeMs }
        }
        clip.copy(keyframes = KeyframeTrack(updated))
    }
}

/**
 * Capture the overlay's CURRENT effective transform (keyframe-aware) as
 * a keyframe at [timeMs]. "Add keyframe" in the properties panel.
 */
fun EditorViewModel.captureOverlayKeyframe(clipId: String, timeMs: Long) {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    // Capture the current transform as an AnimatedTransform (NOT a
    // Keyframe — upsertOverlayKeyframe owns id assignment, so no
    // placeholder/empty id is ever created here).
    val at = if (clip.keyframes.keyframes.isEmpty()) {
        // No keyframes yet: the current transform is the pip base values.
        AnimatedTransform(
            translateX = clip.pipX,
            translateY = clip.pipY,
            scale = clip.pipScale,
            rotationDeg = clip.pipRotationDeg,
            opacity = clip.pipOpacity
        )
    } else {
        clip.keyframes.interpolateAt(timeMs)
    }
    upsertOverlayKeyframe(
        clipId, timeMs, at.translateX, at.translateY, at.scale, at.rotationDeg, at.opacity
    )
}

/** Delete one overlay keyframe. */
fun EditorViewModel.removeOverlayKeyframe(clipId: String, keyframeId: String) {
    pushUndo()
    updateClip(clipId) { clip ->
        clip.copy(
            keyframes = KeyframeTrack(
                clip.keyframes.keyframes.filter { it.id != keyframeId }
            )
        )
    }
}

// ---- Properties panel open/close ----

fun EditorViewModel.openOverlayPropsPanel() {
    _state.update { it.copy(overlayPropsOpen = true) }
}

fun EditorViewModel.closeOverlayPropsPanel() {
    disarmOverlayUndo()
    _state.update { it.copy(overlayPropsOpen = false) }
}

/** PiP overlay scale limits shared by the canvas and the export config. */
object MediaClipPipScale {
    const val MIN: Float = 0.05f
    const val MAX: Float = 0.9f
}
