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
    if (project.lastTransitionType != null || state.lastTransitionType != null) {
        return "Whole-project export currently uses hard cuts, not transitions. Use selected-clip export to keep transition effects."
    }
    if (project.clips.any {
            it.type == ClipType.OVERLAY && it.trackIndex !in state.hiddenVideoLayers
        }) {
        return "Whole-project export does not yet composite visible PiP layers. Use selected-clip export to keep those layers."
    }
    if (project.clips.any { it.type == ClipType.VIDEO && it.trackIndex != 0 && it.trackIndex !in state.hiddenVideoLayers }) {
        return "Whole-project export supports only the main video track."
    }
    return null
}
