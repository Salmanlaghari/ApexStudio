package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MaskModel
import com.apexstudio.app.domain.model.MaskPoint
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask
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
fun EditorViewModel.setMaskCenter(clipId: String, x: Float, y: Float, persist: Boolean = true) =
    updateClipMask(clipId, persist = persist) { it.withCenter(x, y) }

/** Resize the mask (persist=false during the gesture, true on release). */
fun EditorViewModel.setMaskSize(clipId: String, sx: Float, sy: Float, persist: Boolean = true) =
    updateClipMask(clipId, persist = persist) { it.withSize(sx, sy) }

/**
 * Capture a pen region from frame-normalized points. [MaskModel.capturePen]
 * validates and normalizes the path first, so an incomplete gesture fails
 * loudly instead of becoming an empty export polygon.
 */
fun EditorViewModel.captureMaskPen(
    clipId: String,
    framePoints: List<MaskPoint>,
    frameWidth: Float,
    frameHeight: Float,
    persist: Boolean = true
) {
    val capture = MaskModel.capturePen(framePoints, frameWidth, frameHeight)
    updateClip(clipId, persist = persist) { clip ->
        val current = clip.mask ?: MaskModel.defaultForShape(MaskShape.DRAW)
        clip.copy(mask = current.copy(
            shape = MaskShape.DRAW,
            centerX = capture.centerX,
            centerY = capture.centerY,
            sizeX = capture.sizeX,
            sizeY = capture.sizeY,
            drawPoints = capture.points,
            presetId = null
        ))
    }
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
