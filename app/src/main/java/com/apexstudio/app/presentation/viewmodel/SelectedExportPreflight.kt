package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.engine.AudioPlaybackMix
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.presentation.state.EditorState

/** A selected clip has a zero-based output clock, unlike project-positioned layers. */
internal fun selectedVideoExportUnsupportedReason(state: EditorState): String? {
    val project = state.project ?: return "No project is open."
    val base = resolveExportBaseClip(state) ?: return "Select a main video or photo before exporting."
    if (0 in state.hiddenVideoLayers) return "The main video track is hidden. Show it before exporting."
    if (project.clips.any { it.type == ClipType.AUDIO || it.type == ClipType.SFX }) {
        return "Legacy audio/sound-effect clips are not supported by video export. Move them to the Music timeline before exporting."
    }
    if (project.clips.any { it.type == ClipType.VIDEO && it.trackIndex != 0 && it.trackIndex !in state.hiddenVideoLayers }) {
        return "Video outside V1 must use a PiP overlay layer before it can be included in video export."
    }
    val main = project.clips.filter { it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE) }
    val isLaterClip = main.takeWhile { it.id != base.id }.any { it.effectiveDurationMs() > 0L }
    val placedAudio = project.audioTracks.any { AudioPlaybackMix.isEnabled(it, project.audioTracks) }
    val placedOverlays = project.clips.any { it.type == ClipType.OVERLAY && it.trackIndex !in state.hiddenVideoLayers }
    if (isLaterClip && (placedAudio || placedOverlays)) {
        return "Selected-clip export cannot yet rebase project-positioned music or PiP for a later V1 clip. Use Whole project if available, or keep this edit in a single-clip project. Nothing has been removed."
    }
    return null
}
