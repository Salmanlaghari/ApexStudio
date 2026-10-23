package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MaskKeyframe
import com.apexstudio.app.domain.model.VideoMask
import com.apexstudio.app.domain.model.resolveMaskAt
import kotlinx.coroutines.flow.update
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Mask tool (CapCut-style shape masks on video clips).
// ---------------------------------------------------------------------------

/** Attach [mask] to a clip (replaces any existing mask). */
fun EditorViewModel.setClipMask(clipId: String, mask: VideoMask?) {
    updateClip(clipId) { it.copy(mask = mask) }
}

/** Update the clip's mask, or no-op when the clip has none. */
fun EditorViewModel.updateClipMask(
    clipId: String,
    persist: Boolean = true,
    transform: (VideoMask) -> VideoMask
) {
    updateClip(clipId, persist = persist) { clip ->
        val m = clip.mask ?: return@updateClip clip
        clip.copy(mask = transform(m))
    }
}

/** Remove the clip's mask entirely. */
fun EditorViewModel.clearClipMask(clipId: String) = setClipMask(clipId, null)

/** Drag the mask centre (persist=false during the gesture, true on release). */
fun EditorViewModel.setMaskCenter(
    clipId: String,
    x: Float,
    y: Float,
    persist: Boolean = true,
    atMs: Long? = null
) =
    updateClipMask(clipId, persist = persist) {
        it.updateMaskGeometryAt(atMs ?: livePlayheadMs()) { mask -> mask.withCenter(x, y) }
    }

/** Resize the mask (persist=false during the gesture, true on release). */
fun EditorViewModel.setMaskSize(
    clipId: String,
    sx: Float,
    sy: Float,
    persist: Boolean = true,
    atMs: Long? = null
) =
    updateClipMask(clipId, persist = persist) {
        it.updateMaskGeometryAt(atMs ?: livePlayheadMs()) { mask -> mask.withSize(sx, sy) }
    }

/**
 * A static mask edits its base geometry. An animated mask edits the value at
 * the playhead instead, preserving the surrounding animation keys and pinning
 * the currently resolved non-geometry properties into a newly inserted key.
 */
private fun VideoMask.updateMaskGeometryAt(
    playheadMs: Long,
    transform: (VideoMask) -> VideoMask
): VideoMask {
    if (maskKeyframes.isEmpty()) return transform(this).copy(presetId = null)

    val timeMs = playheadMs.coerceAtLeast(0L)
    val current = resolveMaskAt(timeMs)
    val changed = transform(current)
    val existing = maskKeyframes.firstOrNull { it.timeMs == timeMs }
    val key = MaskKeyframe(
        id = existing?.id ?: "mask-$timeMs",
        timeMs = timeMs,
        centerX = changed.centerX,
        centerY = changed.centerY,
        sizeX = changed.sizeX,
        sizeY = changed.sizeY,
        rotationDeg = changed.rotationDeg,
        feather = changed.feather,
        opacity = changed.opacity,
        invert = changed.invert
    )
    return copy(
        maskKeyframes = (maskKeyframes.filterNot { it.timeMs == timeMs } + key).sortedBy { it.timeMs },
        presetId = null
    )
}

// ---------------------------------------------------------------------------
// Text keyframes — CapCut behaviour.
// ---------------------------------------------------------------------------

/**
 * Move a text overlay to an absolute position, honouring keyframes
 * CapCut-style: when the overlay already has keyframes, the drag
 * writes the new position into a keyframe at the playhead (updating
 * the nearest one within 150 ms, else adding a new one) instead of
 * moving the base position — so the text animates between keyframes.
 * With no keyframes, the base position moves as before.
 */
fun EditorViewModel.moveTextOverlayAtPlayhead(
    clipId: String,
    overlayId: String,
    newX: Float,
    newY: Float
) {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val overlay = clip.textOverlays.firstOrNull { it.id == overlayId } ?: return
    if (overlay.keyframes.keyframes.isEmpty()) {
        // Classic behaviour: move the base anchor.
        updateTextOverlay(clipId, overlayId) {
            it.copy(
                x = newX.coerceIn(0.04f, 0.96f),
                y = newY.coerceIn(0.06f, 0.94f)
            )
        }
        return
    }
    val playheadMs = livePlayheadMs()
    val cx = newX.coerceIn(0.04f, 0.96f)
    val cy = newY.coerceIn(0.06f, 0.94f)
    updateTextOverlay(clipId, overlayId) { ov ->
        val kfs = ov.keyframes.keyframes
        val near = kfs.minByOrNull { abs(it.timeMs - playheadMs) }
        val updated = if (near != null && abs(near.timeMs - playheadMs) <= 150L) {
            // Rewrite the nearby keyframe's offset so the text sits
            // exactly where the user dropped it.
            kfs.map {
                if (it.id == near.id) it.copy(
                    translateX = cx - ov.x,
                    translateY = cy - ov.y
                ) else it
            }
        } else {
            // New keyframe at the playhead: a drag is a POSITION gesture,
            // so pin only the position. Scale/rotation/opacity stay neutral
            // (the Keyframe defaults) — capturing the interpolated values
            // here would bake in transient states the user never asked to
            // keyframe (e.g. a mid-flight preset scale pop), and subsequent
            // interpolation would animate toward that unintended pin.
            // This mirrors the nearby-keyframe branch above, which also
            // rewrites position only.
            kfs + Keyframe(
                id = java.util.UUID.randomUUID().toString(),
                timeMs = playheadMs,
                translateX = cx - ov.x,
                translateY = cy - ov.y,
                scale = 1f,
                rotationDeg = 0f,
                opacity = 1f
            )
        }
        ov.copy(keyframes = KeyframeTrack(updated).sorted())
    }
    // Note: updateTextOverlay persists via updateClip already.
}
