package com.apexstudio.app.presentation.viewmodel

import kotlin.math.abs

/** One clip's capacity on the output timeline, after its existing trim and speed settings. */
internal data class AutoCutClipCapacity(
    val clipId: String,
    val availableDurationMs: Long
)

/** A contiguous, beat-bounded output segment assigned to one selected source clip. */
internal data class AutoCutSegment(
    val clipId: String,
    val timelineStartMs: Long,
    val timelineEndMs: Long
) {
    val durationMs: Long get() = timelineEndMs - timelineStartMs
}

/** Pure beat-grid planner. It never invents cut points between detector timestamps. */
internal object AutoCutPlanner {
    /**
     * Starts at [sequenceStartMs] and divides consecutive beat intervals among [clips].
     * Each selected clip is used exactly once, in input order, and its output span is
     * bounded by its available duration. The dynamic search avoids rejecting a feasible
     * plan just because an early locally-balanced choice leaves too little room later.
     */
    fun plan(
        beatTimestampsMs: List<Long>,
        sequenceStartMs: Long,
        clips: List<AutoCutClipCapacity>
    ): List<AutoCutSegment> {
        if (sequenceStartMs < 0L || clips.size < 2 || clips.any { it.availableDurationMs <= 0L }) {
            return emptyList()
        }

        val boundaries = buildList {
            add(sequenceStartMs)
            beatTimestampsMs.asSequence()
                .filter { it > sequenceStartMs }
                .distinct()
                .sorted()
                .forEach(::add)
        }
        if (boundaries.lastIndex < clips.size) return emptyList()

        data class Choice(val score: Double, val endBoundaryIndex: Int)
        val memo = HashMap<Pair<Int, Int>, Choice?>()

        fun solve(clipIndex: Int, startBoundaryIndex: Int): Choice? {
            if (clipIndex == clips.size) return Choice(score = 0.0, endBoundaryIndex = startBoundaryIndex)
            val key = clipIndex to startBoundaryIndex
            if (memo.containsKey(key)) return memo[key]

            val remainingClips = clips.size - clipIndex
            val lastAllowedBoundary = boundaries.lastIndex - (remainingClips - 1)
            val targetDurationMs =
                (boundaries.last() - boundaries[startBoundaryIndex]).toDouble() / remainingClips
            var best: Choice? = null
            var bestDurationMs = Long.MAX_VALUE

            for (endBoundaryIndex in (startBoundaryIndex + 1)..lastAllowedBoundary) {
                val durationMs = boundaries[endBoundaryIndex] - boundaries[startBoundaryIndex]
                if (durationMs <= 0L || durationMs > clips[clipIndex].availableDurationMs) continue
                val remainder = solve(clipIndex + 1, endBoundaryIndex) ?: continue
                val score = abs(durationMs.toDouble() - targetDurationMs) + remainder.score
                val currentBest = best
                if (currentBest == null || score < currentBest.score ||
                    (score == currentBest.score && durationMs < bestDurationMs)
                ) {
                    best = Choice(score, endBoundaryIndex)
                    bestDurationMs = durationMs
                }
            }
            memo[key] = best
            return best
        }

        if (solve(0, 0) == null) return emptyList()
        val result = ArrayList<AutoCutSegment>(clips.size)
        var startBoundaryIndex = 0
        for (clipIndex in clips.indices) {
            val choice = memo[clipIndex to startBoundaryIndex] ?: return emptyList()
            result += AutoCutSegment(
                clipId = clips[clipIndex].clipId,
                timelineStartMs = boundaries[startBoundaryIndex],
                timelineEndMs = boundaries[choice.endBoundaryIndex]
            )
            startBoundaryIndex = choice.endBoundaryIndex
        }
        return result
    }
}
