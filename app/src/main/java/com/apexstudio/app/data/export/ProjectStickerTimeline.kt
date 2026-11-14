package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.StickerOverlay

/**
 * Projects legacy per-clip sticker windows onto the sequential V1 timeline for
 * display and editing. Project stickers already use project time; stickers on
 * [MediaClip.stickers] use clip-output-local time, so only those are shifted.
 */
internal object ProjectStickerTimeline {
    data class Segment(
        val sticker: StickerOverlay,
        /** Null for a project sticker; otherwise the clip whose output clock owns the sticker. */
        val ownerClipId: String?,
    )

    data class Range(val startMs: Long, val endMs: Long)

    /**
     * Returns project-clock sticker segments. An unbounded per-clip sticker is
     * displayed through its owning clip's output end; an unbounded project
     * sticker keeps its legacy behavior and is shortened by the UI to a preview
     * segment.
     */
    fun segments(clips: List<MediaClip>, projectStickers: List<StickerOverlay>): List<Segment> {
        val projectSegments = projectStickers.map { Segment(sticker = it, ownerClipId = null) }
        val plan = ProjectCompositionTimeline.build(clips)
        val clipSegments = plan.entries.flatMap { entry ->
            val duration = entry.durationMs
            if (duration <= 0L) return@flatMap emptyList()
            entry.clip.stickers.mapNotNull { source ->
                val start = source.startMs.coerceIn(0L, duration - 1L)
                val end = if (source.endMs == Long.MAX_VALUE) {
                    duration
                } else {
                    source.endMs.coerceIn(start + 1L, duration)
                }
                if (end <= start) return@mapNotNull null
                val displayKeyframes = source.keyframes.copy(
                    keyframes = source.keyframes.keyframes.map { keyframe ->
                        keyframe.copy(timeMs = saturatingAdd(entry.timelineStartMs, keyframe.timeMs))
                    }.sortedBy { it.timeMs }
                )
                Segment(
                    sticker = source.copy(
                        startMs = saturatingAdd(entry.timelineStartMs, start),
                        endMs = saturatingAdd(entry.timelineStartMs, end),
                        keyframes = displayKeyframes,
                    ),
                    ownerClipId = entry.clip.id,
                )
            }
        }
        return projectSegments + clipSegments
    }

    /** Maps a project-clock sticker range back to the owning clip's output clock. */
    fun clipOutputRange(
        clips: List<MediaClip>,
        ownerClipId: String,
        projectStartMs: Long,
        projectEndMs: Long,
    ): Range? {
        val entry = ProjectCompositionTimeline.build(clips).entries.firstOrNull { it.clip.id == ownerClipId }
            ?: return null
        if (entry.durationMs <= 0L) return null
        val start = (projectStartMs - entry.timelineStartMs).coerceIn(0L, entry.durationMs - 1L)
        val end = (projectEndMs - entry.timelineStartMs).coerceIn(start + 1L, entry.durationMs)
        return Range(start, end)
    }

    /** Returns clip-output-local playhead time only while the project playhead is inside that clip. */
    fun clipOutputTimeAtProjectTime(
        clips: List<MediaClip>,
        ownerClipId: String,
        projectTimeMs: Long,
        allowEndBoundary: Boolean = false,
    ): Long? {
        val entry = ProjectCompositionTimeline.build(clips).entries.firstOrNull { it.clip.id == ownerClipId }
            ?: return null
        if (projectTimeMs < entry.timelineStartMs || projectTimeMs > entry.timelineEndMs) return null
        if (!allowEndBoundary && projectTimeMs == entry.timelineEndMs) return null
        return projectTimeMs - entry.timelineStartMs
    }

    /** Maps a clip-output-local time into the project clock, including the clip end boundary. */
    fun projectTimeAtClipOutputTime(
        clips: List<MediaClip>,
        ownerClipId: String,
        clipOutputTimeMs: Long,
    ): Long? {
        val entry = ProjectCompositionTimeline.build(clips).entries.firstOrNull { it.clip.id == ownerClipId }
            ?: return null
        return saturatingAdd(entry.timelineStartMs, clipOutputTimeMs.coerceIn(0L, entry.durationMs))
    }

    private fun saturatingAdd(a: Long, b: Long): Long =
        if (b > 0L && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}
