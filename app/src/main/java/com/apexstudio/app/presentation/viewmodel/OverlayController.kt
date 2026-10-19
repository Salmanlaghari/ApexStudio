package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.OverlayClip
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.ClipType
import kotlinx.coroutines.flow.update

/** Signed project-time shift that keeps an overlay's right edge anchored as its source head moves. */
internal fun overlayTimelineStartShift(
    clip: MediaClip,
    newStartMs: Long,
    newEndMs: Long = clip.trimEndMs
): Long {
    if (clip.type != ClipType.OVERLAY || newStartMs == clip.trimStartMs) return 0L
    // Speed points are interpreted over the CURRENT trimmed span. After a trim,
    // that span changes and effectiveDurationMs() can differ from the removed
    // source segment's duration measured on the old curve. Shifting by the old
    // curve's segment duration therefore moves the right edge (often visibly
    // on a ramp). Compare the actual output durations at the SAME tail position:
    // a properties edit may move both handles, and the removed tail must not be
    // counted again as a head shift. This applies the tail edit before the head.
    val endMs = newEndMs.coerceIn(newStartMs, clip.durationMs)
    val oldOutputDuration = if (endMs > clip.trimStartMs)
        clip.copy(trimEndMs = endMs).effectiveDurationMs() else 0L
    val newOutputDuration = clip.copy(
        trimStartMs = newStartMs,
        trimEndMs = endMs
    ).effectiveDurationMs()
    return oldOutputDuration - newOutputDuration
}

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
    if (current.type != ClipType.OVERLAY) return
    val target = OverlayClip.from(current).copy(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        scale = scale.coerceIn(OverlayClip.MIN_SCALE, OverlayClip.MAX_SCALE),
        rotationDeg = rotationDeg.coerceIn(-180f, 180f)
    )
    val updated = target.writeTo(current)
    if (updated == current) return
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
    if (current.type != ClipType.OVERLAY) return
    val window = OverlayClip.from(current).trimmed(
        requestedStartMs,
        requestedEndMs
    )
    // A no-op drag still armed undo above? - no: arm only when this
    // actually changes something, so scrubbing the slider to the same
    // value can't fill the undo stack.
    if (window.startMs == current.trimStartMs && window.endMs == current.trimEndMs) return
    armOverlayUndo()
    var updatedDuration: Long? = null
    _state.update { state ->
        val project = state.project ?: return@update state
        val clips = project.clips.map { clip ->
            if (clip.id != clipId) clip
            else window.writeTo(clip).copy(
                timelineOffsetMs = (clip.timelineOffsetMs + overlayTimelineStartShift(clip, window.startMs, window.endMs))
                    .coerceAtLeast(0L)
            )
        }
        val duration = computeTimelineDurationMs(clips, project.audioTracks)
        updatedDuration = duration
        state.copy(
            project = project.copy(clips = clips, durationMs = duration),
            durationMs = duration,
            playerPositionMs = state.playerPositionMs.coerceAtMost(duration),
            currentTimeMs = state.currentTimeMs.coerceAtMost(duration)
        )
    }
    updatedDuration?.let { duration -> _playerPosition.update { it.coerceAtMost(duration) } }
    persistProject()
}
