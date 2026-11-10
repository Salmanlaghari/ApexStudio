package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.ClipTransition
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip

/**
 * Pure plan for sequential V1 video clips and valid adjacent transitions.
 *
 * Transition windows are half-open. During a window [startMs, endMs), both
 * clips are active; [entryAt] returns the outgoing clip because entries are
 * ordered in track order. At the window's end the incoming clip owns the
 * boundary. Unknown, non-adjacent, and non-positive transitions are ignored.
 */
internal object ProjectTransitionTimeline {
    data class Entry(
        val clip: MediaClip,
        val sourceStartMs: Long,
        val sourceEndMs: Long,
        /** Start of this clip in the legacy hard-cut timeline, before overlaps. */
        val baseTimelineStartMs: Long,
        val timelineStartMs: Long,
        val timelineEndMs: Long
    ) {
        val durationMs: Long get() = timelineEndMs - timelineStartMs

        fun sourceTimeAtOutputOffset(outputOffsetMs: Long): Long {
            val offset = outputOffsetMs.coerceIn(0L, durationMs)
            if (clip.type == ClipType.IMAGE) return sourceStartMs
            if (offset == 0L) return sourceStartMs
            if (offset == durationMs) return sourceEndMs
            val normalized = clip.copy(trimStartMs = sourceStartMs, trimEndMs = sourceEndMs)
            return saturatingAdd(
                sourceStartMs,
                com.apexstudio.app.domain.model.ClipTimeMapping.mainSourceOffsetAtOutputMs(
                    normalized,
                    offset
                )
            ).coerceIn(sourceStartMs, sourceEndMs)
        }
    }

    data class Transition(
        val transition: ClipTransition,
        val from: Entry,
        val to: Entry,
        val startMs: Long,
        val endMs: Long
    ) {
        val durationMs: Long get() = endMs - startMs
    }

    /** Main-video audio uses a hard cut at the visual transition midpoint. */
    data class AudioSegment(
        val entry: Entry,
        val timelineStartMs: Long,
        val timelineEndMs: Long,
        val sourceStartMs: Long,
        val sourceEndMs: Long
    ) {
        val clip: MediaClip get() = entry.clip
        val durationMs: Long get() = timelineEndMs - timelineStartMs
    }

    data class Plan(
        val entries: List<Entry>,
        val transitions: List<Transition>,
        val audioSegments: List<AudioSegment>,
        val durationMs: Long
    ) {
        /** Half-open lookup; an overlap resolves to the outgoing entry. */
        fun entryAt(projectTimeMs: Long): Entry? {
            if (projectTimeMs < 0L || projectTimeMs >= durationMs) return null
            return entries.firstOrNull {
                projectTimeMs >= it.timelineStartMs && projectTimeMs < it.timelineEndMs
            }
        }

        /** Returns the transition whose half-open overlap contains the time. */
        fun transitionAt(projectTimeMs: Long): Transition? {
            if (projectTimeMs < 0L || projectTimeMs >= durationMs) return null
            return transitions.firstOrNull {
                projectTimeMs >= it.startMs && projectTimeMs < it.endMs
            }
        }

        /** Main-track clip audio has one owner at every in-range project time. */
        fun audioSegmentAt(projectTimeMs: Long): AudioSegment? {
            if (projectTimeMs < 0L || projectTimeMs >= durationMs) return null
            return audioSegments.firstOrNull {
                projectTimeMs >= it.timelineStartMs && projectTimeMs < it.timelineEndMs
            }
        }

        /** Maps project time to the selected active clip's source clock. */
        fun sourceTimeAt(projectTimeMs: Long): Long? {
            val entry = entryAt(projectTimeMs) ?: return null
            return entry.sourceTimeAtOutputOffset(projectTimeMs - entry.timelineStartMs)
        }

        /** Maps a decoded main-video source timestamp back into the overlap-aware project clock. */
        fun projectTimeAtSourceTime(clips: List<MediaClip>, clip: MediaClip, sourceTimeMs: Long): Long? {
            val entry = entries.firstOrNull { it.clip.id == clip.id } ?: return null
            if (clip.type == ClipType.IMAGE) return entry.timelineStartMs
            val hardCutTime = com.apexstudio.app.domain.model.ClipTimeMapping.projectAtSourceTime(
                clips, clip, sourceTimeMs
            )
            val clipOutputOffset = (hardCutTime - entry.baseTimelineStartMs).coerceIn(0L, entry.durationMs)
            return (entry.timelineStartMs + clipOutputOffset).coerceIn(entry.timelineStartMs, entry.timelineEndMs)
        }

        /** Both clips active in a transition, in outgoing/incoming order. */
        fun activeEntriesAt(projectTimeMs: Long): List<Entry> {
            if (projectTimeMs < 0L || projectTimeMs >= durationMs) return emptyList()
            return entries.filter {
                projectTimeMs >= it.timelineStartMs && projectTimeMs < it.timelineEndMs
            }
        }
    }

    fun build(
        clips: List<MediaClip>,
        transitions: List<ClipTransition> = emptyList()
    ): Plan {
        // Build from the canonical planner so trims, speed curves, and source
        // durations cannot diverge between hard-cut and transition plans.
        val base = ProjectCompositionTimeline.build(
            clips.filter { it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE) }
        )
        if (base.entries.isEmpty()) return Plan(emptyList(), emptyList(), emptyList(), 0L)

        val requestedByPair = transitions
            .groupBy { it.fromClipId to it.toClipId }
            .mapValues { (_, values) -> values.first() }
        val durations = LongArray(base.entries.size - 1)
        for (index in durations.indices) {
            val from = base.entries[index]
            val to = base.entries[index + 1]
            if (from.clip.type != ClipType.VIDEO || to.clip.type != ClipType.VIDEO) continue
            val requested = requestedByPair[from.clip.id to to.clip.id]?.durationMs ?: 0L
            if (requested <= 0L) continue
            val halfFrom = from.durationMs / 2L
            val halfTo = to.durationMs / 2L
            durations[index] = minOf(requested, halfFrom, halfTo)
        }

        val entries = ArrayList<Entry>(base.entries.size)
        var cursor = 0L
        base.entries.forEachIndexed { index, baseEntry ->
            val start = cursor
            val end = saturatingAdd(start, baseEntry.durationMs)
            entries += Entry(
                clip = baseEntry.clip,
                sourceStartMs = baseEntry.sourceStartMs,
                sourceEndMs = baseEntry.sourceEndMs,
                baseTimelineStartMs = baseEntry.timelineStartMs,
                timelineStartMs = start,
                timelineEndMs = end
            )
            if (index < durations.size) {
                cursor = subtractNonNegative(end, durations[index])
            }
        }

        val plannedTransitions = buildList {
            for (index in durations.indices) {
                val duration = durations[index]
                if (duration <= 0L) continue
                val from = entries[index]
                val to = entries[index + 1]
                val transition = requestedByPair[from.clip.id to to.clip.id] ?: continue
                add(Transition(
                    transition = transition,
                    from = from,
                    to = to,
                    startMs = subtractNonNegative(from.timelineEndMs, duration),
                    endMs = from.timelineEndMs
                ))
            }
        }
        val duration = entries.last().timelineEndMs
        val transitionsByPair = plannedTransitions.associateBy { it.from.clip.id to it.to.clip.id }
        val audioSegments = entries.mapIndexedNotNull { index, entry ->
            val previous = entries.getOrNull(index - 1)
            val next = entries.getOrNull(index + 1)
            val incoming = previous?.let { transitionsByPair[it.clip.id to entry.clip.id] }
            val outgoing = next?.let { transitionsByPair[entry.clip.id to it.clip.id] }
            val startMs = incoming?.let { midpoint(it.startMs, it.endMs) } ?: entry.timelineStartMs
            val endMs = outgoing?.let { midpoint(it.startMs, it.endMs) } ?: entry.timelineEndMs
            if (endMs <= startMs) return@mapIndexedNotNull null
            val sourceStartMs = entry.sourceTimeAtOutputOffset(startMs - entry.timelineStartMs)
            val sourceEndMs = entry.sourceTimeAtOutputOffset(endMs - entry.timelineStartMs)
            if (sourceEndMs <= sourceStartMs) return@mapIndexedNotNull null
            AudioSegment(entry, startMs, endMs, sourceStartMs, sourceEndMs)
        }
        return Plan(entries, plannedTransitions, audioSegments, duration)
    }

    /** Returns the rendered overlap duration, or zero when the requested boundary cannot overlap. */
    fun plannedDurationMs(clips: List<MediaClip>, transition: ClipTransition): Long =
        build(clips, listOf(transition)).transitions.firstOrNull {
            it.transition.fromClipId == transition.fromClipId &&
                it.transition.toClipId == transition.toClipId
        }?.durationMs ?: 0L

    /** Drops only transition references made stale by clip-order/type edits. */
    fun normalizeTransitions(clips: List<MediaClip>, transitions: List<ClipTransition>): List<ClipTransition> {
        val main = clips.filter {
            it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE)
        }
        val adjacentVideoPairs = main.zipWithNext()
            .filter { (from, to) -> from.type == ClipType.VIDEO && to.type == ClipType.VIDEO }
            .map { (from, to) -> from.id to to.id }
            .toSet()
        val seen = mutableSetOf<Pair<String, String>>()
        return transitions.filter { transition ->
            val pair = transition.fromClipId to transition.toClipId
            pair in adjacentVideoPairs && seen.add(pair)
        }
    }

    private fun midpoint(startMs: Long, endMs: Long): Long =
        startMs + (endMs - startMs) / 2L

    private fun saturatingAdd(a: Long, b: Long): Long =
        if (b > 0L && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    private fun subtractNonNegative(a: Long, b: Long): Long =
        if (b <= 0L) a else if (a < b) 0L else a - b
}
