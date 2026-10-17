package com.apexstudio.app.presentation.state

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip

/**
 * The clip the Keyframe / Animation panel is allowed to edit — Bug 5.
 *
 * Before this, `KeyframePanelOverlay` resolved the target as:
 *
 *     project.clips.firstOrNull { it.id == selectedClipId }
 *         ?: project.clips.firstOrNull()      // <- silent fallback
 *
 * so with the video layer deselected (or a stale id) the panel quietly
 * opened on the FIRST clip in the project and CapCut-style
 * `onUpsertParam` writes then landed on a clip the user never touched —
 * an animation applied to the wrong layer with no visible error.
 *
 * One clip; never a fallback. Null means "no layer is selected", which
 * the panel renders as "nothing to animate".
 *
 * A clip only qualifies when it is a layer the editor animates:
 * [ClipType.VIDEO] (the requirement for this fix — position, scale,
 * rotation, opacity, volume, effect, filter and saturation keyframes),
 * [ClipType.OVERLAY] (PiP has its own transform track) and
 * [ClipType.IMAGE] (a photo layer still takes position/scale/opacity).
 * [ClipType.AUDIO] / [ClipType.SFX] are keyed through the audio-mixer
 * volume lane, and the panel never targets them here.
 *
 * Short: [isKeyframeTarget] is the "should the Animation entry point be
 * live for this selection?" predicate shared by the toolbar and the
 * panel.
 */
fun EditorState.keyframeAnchor(): MediaClip? {
    val id = selectedClipId ?: return null
    val clips = project?.clips ?: return null
    return clips.firstOrNull { it.id == id }?.takeIf { it.isKeyframeTarget() }
}

/** True for clip types the video-layer keyframe controls animate. */
fun MediaClip.isKeyframeTarget(): Boolean = when (type) {
    ClipType.VIDEO,
    ClipType.OVERLAY,
    ClipType.IMAGE -> true
    ClipType.AUDIO,
    ClipType.SFX -> false
}
