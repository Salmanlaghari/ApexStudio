package com.apexstudio.app.presentation.viewmodel

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
 */

fun EditorViewModel.selectOverlayClip(id: String?) =
    _state.update { it.copy(selectedOverlayClipId = id) }

fun EditorViewModel.moveOverlayClip(
    clipId: String,
    dx: Float,
    dy: Float,
    persist: Boolean = true
) {
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
    updateClip(clipId, persist) { clip ->
        clip.copy(pipScale = scale.coerceIn(MediaClipPipScale.MIN, MediaClipPipScale.MAX))
    }
}

fun EditorViewModel.rotateOverlayClip(
    clipId: String,
    deltaDeg: Float,
    persist: Boolean = true
) {
    updateClip(clipId, persist) { clip ->
        clip.copy(pipRotationDeg = (clip.pipRotationDeg + deltaDeg) % 360f)
    }
}

fun EditorViewModel.setOverlayClipOpacity(clipId: String, opacity: Float) {
    updateClip(clipId) { clip ->
        clip.copy(pipOpacity = opacity.coerceIn(0f, 1f))
    }
}

/** Flush the project after a gesture that streamed persist = false updates. */
fun EditorViewModel.persistOverlayClipGesture(clipId: String) {
    updateClip(clipId, persist = true) { it }
}

fun EditorViewModel.removeOverlayClip(clipId: String) {
    deleteClip(clipId)
    if (_state.value.selectedOverlayClipId == clipId) {
        _state.update { it.copy(selectedOverlayClipId = null) }
    }
}

/** PiP overlay scale limits shared by the canvas and the export config. */
object MediaClipPipScale {
    const val MIN: Float = 0.05f
    const val MAX: Float = 0.9f
}
