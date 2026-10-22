package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.presentation.state.EditorState

/**
 * The basic sequence route must not silently discard editor-wide visuals.
 * Per-source capability checks live beside the composition engine.
 */
internal fun projectVisualScopeUnsupportedReason(state: EditorState): String? {
    val project = state.project ?: return "No project is open."
    if (0 in state.hiddenVideoLayers) return "The main video track is hidden. Show it before whole-project export."
    val unsupportedAudioClip = project.clips.firstOrNull { it.type == ClipType.AUDIO || it.type == ClipType.SFX }
    if (unsupportedAudioClip != null) {
        val kind = if (unsupportedAudioClip.type == ClipType.SFX) "sound-effect" else "audio"
        return "Whole-project export does not yet include legacy $kind clip '${unsupportedAudioClip.name}'. Use the supported project audio-track controls or remove that clip."
    }
    if (state.activeArFilterId != null) {
        return "Whole-project export does not yet render AR filters. Use selected-clip export to keep that edit."
    }
    if (!state.cropRect.isFullFrame() || !state.activeGpuFilterConfig.isDefault) {
        return "Whole-project export does not yet render GPU filters or crop. Use selected-clip export to keep those edits."
    }
    if (project.stickers.isNotEmpty()) {
        return "Whole-project export does not yet render project stickers. Use selected-clip export to keep those edits."
    }
    if (project.transitions.isNotEmpty() || project.lastTransitionType != null || state.lastTransitionType != null) {
        return "Whole-project export uses hard cuts, not boundary transitions. Selected-clip export also does not render clip-junction transitions. Remove the saved transition choice before whole-project export."
    }
    if (project.clips.any {
            it.type == ClipType.OVERLAY && it.trackIndex !in state.hiddenVideoLayers
        }) {
        return "Whole-project export cannot render visible PiP layers, so it is blocked; nothing is skipped. " +
            "To export the main track only, return to the editor and explicitly hide/remove PiP. " +
            "Selected source compares/exports one original source, not the PiP composition."
    }
    if (project.clips.any {
            (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE) &&
                it.trackIndex != 0 && it.trackIndex !in state.hiddenVideoLayers
        }) {
        return "Whole-project export supports only the main video track. Visible video or photo clips on other tracks cannot be omitted."
    }
    return null
}
