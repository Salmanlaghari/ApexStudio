package com.apexstudio.app.ui.components

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip

/** Timeline interval used to keep overlapping items legible within a layer lane. */
internal data class TimelineLaneSpan(val startMs: Long, val endMs: Long)

/**
 * Assigns each interval to the first available visual subrow. Items retain
 * input order, while non-overlapping items share a row and overlapping ones
 * are independently tappable instead of obscuring one another.
 */
internal fun timelineLaneRowIndices(spans: List<TimelineLaneSpan>): List<Int> {
    val rowEndTimes = mutableListOf<Long>()
    val placements = IntArray(spans.size)
    spans.indices.sortedWith(compareBy<Int> { spans[it].startMs }.thenBy { spans[it].endMs }).forEach { index ->
        val span = spans[index]
        val start = span.startMs.coerceAtLeast(0L)
        val minimumEnd = if (start == Long.MAX_VALUE) Long.MAX_VALUE else start + 1L
        val end = span.endMs.coerceAtLeast(minimumEnd)
        val row = rowEndTimes.indexOfFirst { it <= start }.let { if (it < 0) rowEndTimes.size else it }
        if (row == rowEndTimes.size) rowEndTimes += end else rowEndTimes[row] = end
        placements[index] = row
    }
    return placements.toList()
}

internal fun timelineLaneRowCount(rowIndices: List<Int>): Int =
    (rowIndices.maxOrNull() ?: -1) + 1

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
