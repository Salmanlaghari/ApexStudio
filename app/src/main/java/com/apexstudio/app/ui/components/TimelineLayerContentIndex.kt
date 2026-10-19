package com.apexstudio.app.ui.components

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip

/**
 * Project-level indexes used by timeline lanes. Building these once keeps
 * layer rendering proportional to project size rather than rescanning all
 * clips for every lane/text overlay during composition.
 */
internal data class TimelineLayerContentIndex(
    val videoByLayer: Map<Int, List<MediaClip>>,
    val textOwnerByOverlayId: Map<String, String>,
) {
    companion object {
        fun from(clips: List<MediaClip>): TimelineLayerContentIndex {
            val layers = linkedMapOf<Int, MutableList<MediaClip>>()
            val textOwners = linkedMapOf<String, String>()
            clips.forEach { clip ->
                if (clip.type == ClipType.VIDEO || clip.type == ClipType.IMAGE || clip.type == ClipType.OVERLAY) {
                    layers.getOrPut(clip.trackIndex.coerceIn(0, 9)) { mutableListOf() }.add(clip)
                }
                // Preserve the previous first-match behavior if malformed or
                // legacy project data contains duplicate overlay IDs.
                clip.textOverlays.forEach { overlay -> textOwners.putIfAbsent(overlay.id, clip.id) }
            }
            return TimelineLayerContentIndex(
                videoByLayer = layers.mapValues { (_, layerClips) -> layerClips.toList() },
                textOwnerByOverlayId = textOwners,
            )
        }
    }
}
