package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.OverlayClip

/**
 * Layout + trim operations for overlay clips — Bug 3.
 *
 * Overlay editing already lived here for gestures (move / scale / rotate
 * live on the preview canvas) and for compositing (blend, border, shadow,
 * flip, volume, keyframes). Two of the writes the task asks for did not:
 *
 * - **[setOverlayLayout] the explicit Position / Scale / Rotation
 *   mirrors of those gestures**, so an overlay can be placed precisely
 *   with sliders - the canvas is fine for a rough drop and unusable for
 *   exact placement at 10% zoom. Validation is delegated to the
 *   closed-form bounds of [OverlayClip] rather than re-litigated here,
 *   so the panel cannot accept a value the preview pinch would reject.
 *
 * - **[trimOverlayClip] the source window**: an overlay is its own media
 *   and had no start/end editing at all. The video trim sheet trims the
 *   timeline placement of V1 clips, so trimming an overlay there edited
 *   the wrong thing; this writes the overlay's own [MediaClip]
 *   `trimStartMs`/`trimEndMs`, clamped inside its source.
 *
 * Both go through the same undo arm as every other overlay edit (one
 * entry per user interaction, not one per slider tick) and share
 * [armOverlayUndo] with [EditorPipOverlayController], so a jittery drag
 * followed by Ctrl+Z restores exactly one logical step.
 *
 * Note on keyframes: a keyframed transform keeps being driven by
 * [upsertOverlayKeyframe] (the gesture path), while these two write the
 * BASE values underneath the track. That split is deliberate — the
 * sliders serve placement (base) and the gestures serve animation, and
 * silently re-targeting a placement slider onto a playhead keyframe
 * would move the animation instead of the element.
 *
 * @see OverlayClip.writeTo for the exact fields a write touches.
 */
fun EditorViewModel.setOverlayLayout(
    clipId: String,
    x: Float,
    y: Float,
    scale: Float,
    rotationDeg: Float
) {
    val current = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val target = OverlayClip.from(current).copy(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        scale = scale.coerceIn(OverlayClip.MIN_SCALE, OverlayClip.MAX_SCALE),
        rotationDeg = rotationDeg.coerceIn(-180f, 180f)
    )
    armOverlayUndo()
    updateClip(clipId) { clip -> target.writeTo(clip) }
}

/**
 * Set the overlay's own source window (`trimStartMs`..`trimEndMs`) —
 * the little faded window over its media, not its timeline placement.
 *
 * Arguments are accepted in any order (a RangeSlider can hand you a
 * reversed pair while dragging) and are clamped to a minimum 1 ms span,
 * because a zero-length overlay has no visible frames and would still
 * render a hole in every export. Anything outside the source collapses
 * inward rather than throwing.
 */
fun EditorViewModel.trimOverlayClip(
    clipId: String,
    requestedStartMs: Long,
    requestedEndMs: Long
) {
    val current = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val window = OverlayClip.from(current).trimmed(
        requestedStartMs,
        requestedEndMs
    )
    // A no-op drag still armed undo above? - no: arm only when this
    // actually changes something, so scrubbing the slider to the same
    // value can't fill the undo stack.
    if (window.startMs == current.trimStartMs && window.endMs == current.trimEndMs) return
    armOverlayUndo()
    updateClip(clipId) { clip -> window.writeTo(clip) }
}
