package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.TextOverlay

/**
 * Maps a project-clock text interval onto the existing per-clip overlay schema.
 * Each returned overlay keeps the source overlay's full style/animation data and
 * stores clip-output-local [TextOverlay.startMs]/[TextOverlay.endMs]. The
 * animation-window fields retain the full logical range in each clip's clock,
 * so intro/loop/outro timing continues across segment boundaries. A generated
 * projectTextLayerId explicitly links pieces; legacy same-ID overlays stay separate.
 */
internal object ProjectTextOverlayTimeline {
    data class Range(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = endMs - startMs
    }

    data class Segment(val clipId: String, val overlay: TextOverlay)

    /**
     * Clips [rangeStartMs, rangeEndMs) against the V1 output sequence. Invalid or
     * empty ranges are rejected; no timestamp beyond the existing project timeline
     * is invented. Timeline offsets use the same trim/speed math as export.
     */
    fun segments(
        clips: List<MediaClip>,
        overlay: TextOverlay,
        rangeStartMs: Long,
        rangeEndMs: Long
    ): List<Segment> {
        if (rangeStartMs < 0L || rangeEndMs <= rangeStartMs) return emptyList()
        val plan = ProjectCompositionTimeline.build(clips)
        if (plan.durationMs <= 0L) return emptyList()
        val start = rangeStartMs.coerceAtMost(plan.durationMs)
        val end = rangeEndMs.coerceAtMost(plan.durationMs)
        if (end <= start) return emptyList()
        val logicalLayerId = overlay.projectTextLayerId ?: java.util.UUID.randomUUID().toString()

        return plan.entries.mapNotNull { entry ->
            val overlapStart = maxOf(start, entry.timelineStartMs)
            val overlapEnd = minOf(end, entry.timelineEndMs)
            if (overlapEnd <= overlapStart) return@mapNotNull null
            Segment(
                clipId = entry.clip.id,
                overlay = overlay.copy(
                    startMs = overlapStart - entry.timelineStartMs,
                    endMs = overlapEnd - entry.timelineStartMs,
                    animationWindowStartMs = start - entry.timelineStartMs,
                    animationWindowEndMs = end - entry.timelineStartMs,
                    projectTextLayerId = logicalLayerId,
                    rangeEndExclusive = true
                )
            )
        }
    }

    /**
     * Reconstructs one absolute project-time range from a legacy single-clip
     * overlay or from multiple same-id segments. Legacy Long.MAX_VALUE ends are
     * clipped to that clip's actual output duration before returning.
     */
    fun range(clips: List<MediaClip>, overlayId: String, ownerClipId: String? = null): Range? {
        if (overlayId.isBlank()) return null
        val plan = ProjectCompositionTimeline.build(clips)
        val ownerClip = ownerClipId?.let { id -> clips.firstOrNull { clip -> clip.id == id && clip.textOverlays.any { it.id == overlayId } } }
        val ownerEntry = ownerClip?.let { owner -> plan.entries.firstOrNull { it.clip.id == owner.id } }
        // A selected non-V1 owner has no project-clock range. Do not accidentally
        // display or edit another V1 clip's legacy overlay with the same ID.
        if (ownerClip != null && ownerEntry == null) return null
        // An explicitly requested owner wins, including legacy overlays that
        // happen to share an ID with a different project's logical text layer.
        val selected = ownerClip?.textOverlays?.firstOrNull { it.id == overlayId }
            ?: plan.entries.asSequence().flatMap { it.clip.textOverlays.asSequence() }
                .firstOrNull { it.id == overlayId && it.projectTextLayerId != null }
            ?: plan.entries.asSequence().flatMap { it.clip.textOverlays.asSequence() }
                .firstOrNull { it.id == overlayId }
            ?: return null
        val matching = if (selected.projectTextLayerId != null) {
            plan.entries.mapNotNull { entry ->
                entry.clip.textOverlays.firstOrNull { it.projectTextLayerId == selected.projectTextLayerId }
                    ?.let { entry to it }
            }
        } else {
            val entry = ownerEntry ?: plan.entries.firstOrNull { e -> e.clip.textOverlays.any { it === selected } }
                ?: return null
            listOf(entry to selected)
        }
        val ranges = matching.mapNotNull { (entry, overlay) ->
            val localStart = overlay.startMs.coerceIn(0L, entry.durationMs)
            val localEnd = overlay.endMs.coerceIn(0L, entry.durationMs)
            if (localEnd <= localStart) null else Range(
                startMs = safeAdd(entry.timelineStartMs, localStart),
                endMs = safeAdd(entry.timelineStartMs, localEnd)
            )
        }
        if (ranges.isEmpty()) return null
        return Range(ranges.minOf { it.startMs }, ranges.maxOf { it.endMs })
    }

    private fun safeAdd(a: Long, b: Long): Long =
        if (b > 0L && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}
