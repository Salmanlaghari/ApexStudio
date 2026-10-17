package com.apexstudio.app.presentation.state

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip

/**
 * The clip a CLIP-SCOPED tool is allowed to edit — shared by the Mask
 * tool (Bug 4) and the Keyframe / Animation panel (Bug 5).
 *
 * Both overlays used to resolve their target as:
 *
 *     project.clips.firstOrNull { it.id == selectedClipId }
 *         ?: project.clips.firstOrNull()      // <- silent fallback
 *
 * so with the layer deselected (or a stale id) the panel quietly opened
 * on the FIRST clip in the project and its writes — `setClipMask`,
 * `upsertKeyframeParam` — landed on a clip the user never touched, with
 * no visible error.
 *
 * One clip; never a fallback. Null means "no layer is selected",
 * which the overlay renders as "nothing to edit".
 *
 * A clip only qualifies when it is a layer the editor animates:
 * [ClipType.VIDEO] (the Bug 5 requirement — position, scale, rotation,
 * opacity, volume, effect, filter and saturation keyframes), plus
 * [ClipType.OVERLAY] (PiP has its own transform track) and
 * [ClipType.IMAGE] (a photo layer still takes position/scale/opacity).
 * [ClipType.AUDIO] / [ClipType.SFX] are excluded: their volume is keyed
 * through the audio-mixer lane, and neither takes a scene mask.
 *
 * [isKeyframeTarget] is the shared "is this selection editable?"
 * predicate, one call for both the toolbar and the overlay.
 */
fun EditorState.clipLayerAnchor(): MediaClip? {
    val id = selectedClipId ?: return null
    val clips = project?.clips ?: return null
    return clips.firstOrNull { it.id == id }?.takeIf { it.isKeyframeTarget() }
}

/** Clip target for the Keyframe / Animation panel — see [clipLayerAnchor]. */
fun EditorState.keyframeAnchor(): MediaClip? = clipLayerAnchor()

/** True for clip types the video-layer keyframe controls and masks animate. */
fun MediaClip.isKeyframeTarget(): Boolean = when (type) {
    ClipType.VIDEO,
    ClipType.OVERLAY,
    ClipType.IMAGE -> true
    ClipType.AUDIO,
    ClipType.SFX -> false
}
