package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.ClipTimeMapping
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip

/**
 * Pure timeline plan for the sequential V1 (main video/photo) track.
 *
 * The editor's V1 clips are composed in project-list order; their
 * [MediaClip.timelineOffsetMs] is not used to place them. This planner only
 * models that main-track sequence. It does not render clips, still images,
 * transitions, global overlays, or audio. In particular, callers must not
 * treat this mapping as evidence that those layers are exported.
 */
internal object ProjectCompositionTimeline {
    data class Entry(
        val clip: MediaClip,
        /** Normalized source trim passed to a future media item builder. */
        val sourceStartMs: Long,
        val sourceEndMs: Long,
        val timelineStartMs: Long,
        val timelineEndMs: Long
    ) {
        val durationMs: Long get() = timelineEndMs - timelineStartMs

        /** Maps elapsed V1 output time to the source timestamp for this clip. */
        fun sourceTimeAtOutputOffset(outputOffsetMs: Long): Long {
            val offset = outputOffsetMs.coerceIn(0L, durationMs)
            if (clip.type == ClipType.IMAGE) return sourceStartMs
            if (offset == 0L) return sourceStartMs
            if (offset == durationMs) return sourceEndMs
            val normalized = clip.copy(trimStartMs = sourceStartMs, trimEndMs = sourceEndMs)
            val sourceOffset = ClipTimeMapping.mainSourceOffsetAtOutputMs(normalized, offset)
            return saturatingAdd(sourceStartMs, sourceOffset).coerceIn(sourceStartMs, sourceEndMs)
        }
    }

    data class Plan(
        val entries: List<Entry>,
        /** Main-track media excluded because its source interval was unusable. */
        val excludedClipIds: List<String>,
        val durationMs: Long
    ) {
        /** Half-open timeline lookup: a boundary belongs to the following clip. */
        fun entryAt(projectTimeMs: Long): Entry? {
            if (projectTimeMs < 0L || projectTimeMs >= durationMs) return null
            return entries.firstOrNull {
                projectTimeMs >= it.timelineStartMs && projectTimeMs < it.timelineEndMs
            }
        }

        fun sourceTimeAt(projectTimeMs: Long): Long? {
            val entry = entryAt(projectTimeMs) ?: return null
            return entry.sourceTimeAtOutputOffset(projectTimeMs - entry.timelineStartMs)
        }
    }

    fun build(clips: List<MediaClip>): Plan {
        val mainTrack = clips.filter {
            it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE)
        }
        val entries = mutableListOf<Entry>()
        val excluded = mutableListOf<String>()
        var cursor = 0L

        for (clip in mainTrack) {
            val start = clip.trimStartMs.coerceAtLeast(0L)
            val declaredEnd = when {
                clip.trimEndMs == Long.MAX_VALUE && clip.durationMs > 0L -> clip.durationMs
                clip.trimEndMs > 0L -> clip.trimEndMs
                clip.durationMs > 0L -> clip.durationMs
                else -> clip.trimEndMs
            }
            val end = if (clip.durationMs > 0L) minOf(declaredEnd, clip.durationMs) else declaredEnd
            if (end <= start) {
                excluded += clip.id
                continue
            }

            val outputDuration = if (clip.type == ClipType.IMAGE) {
                com.apexstudio.app.domain.model.SpeedCurveMath.effectiveDurationMs(
                    end - start,
                    clip.speedPoints
                )
            } else {
                ClipTimeMapping.mainClipDurationMs(clip.copy(trimStartMs = start, trimEndMs = end))
            }
            if (outputDuration <= 0L) {
                excluded += clip.id
                continue
            }
            val next = saturatingAdd(cursor, outputDuration)
            entries += Entry(clip, start, end, cursor, next)
            cursor = next
        }
        return Plan(entries, excluded, cursor)
    }

    private fun saturatingAdd(a: Long, b: Long): Long =
        if (b > 0L && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}
