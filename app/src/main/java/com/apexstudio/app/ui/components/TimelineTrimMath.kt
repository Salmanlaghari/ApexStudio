package com.apexstudio.app.ui.components

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurveMath
import kotlin.math.roundToLong

/**
 * Map a horizontal timeline/output-time drag to a source head boundary.
 *
 * The timeline is laid out using a clip's effective (speed-ramped) output
 * duration, so treating pointer milliseconds as source milliseconds makes
 * trim handles drift under a speed curve. Instead, find the source boundary
 * whose newly-normalized curve produces the requested output-duration change.
 * Positive deltas trim more from the head; negative deltas expand it.
 */
internal fun timelineTrimStartForOutputDeltaMs(
    clip: MediaClip,
    outputDeltaMs: Long,
    minimumSpanMs: Long = 100L
): Long {
    val duration = clip.durationMs.coerceAtLeast(0L)
    val end = clip.trimEndMs.coerceIn(0L, duration)
    val minSpan = minimumSpanMs.coerceAtLeast(1L).coerceAtMost(end.coerceAtLeast(1L))
    val maxStart = (end - minSpan).coerceAtLeast(0L)
    val currentStart = clip.trimStartMs.coerceIn(0L, maxStart)
    if (outputDeltaMs == 0L) return currentStart
    constantMainSourceDelta(clip, outputDeltaMs)?.let { sourceDelta ->
        return addSaturated(currentStart, sourceDelta).coerceIn(0L, maxStart)
    }
    val targetDuration = subtractSaturated(
        clip.copy(trimStartMs = currentStart, trimEndMs = end).effectiveDurationMs(),
        outputDeltaMs
    )

    fun outputDuration(start: Long) = clip.copy(trimStartMs = start, trimEndMs = end).effectiveDurationMs()
    val minDuration = outputDuration(maxStart)
    val maxDuration = outputDuration(0L)
    val target = targetDuration.coerceIn(minDuration, maxDuration)

    // Output duration decreases monotonically as the source head advances.
    var low = 0L
    var high = maxStart
    while (low < high) {
        val mid = low + (high - low) / 2L
        if (outputDuration(mid) > target) low = mid + 1L else high = mid
    }
    return closestBoundary(low, if (low > 0L) low - 1L else low, target, ::outputDuration)
}

/** Positive deltas extend the source tail; negative deltas trim it earlier. */
internal fun timelineTrimEndForOutputDeltaMs(
    clip: MediaClip,
    outputDeltaMs: Long,
    minimumSpanMs: Long = 100L
): Long {
    val duration = clip.durationMs.coerceAtLeast(0L)
    val start = clip.trimStartMs.coerceIn(0L, duration)
    val minSpan = minimumSpanMs.coerceAtLeast(1L)
    val minEnd = if (start > duration - minSpan) duration else start + minSpan
    val maxEnd = duration.coerceAtLeast(minEnd)
    val currentEnd = clip.trimEndMs.coerceIn(minEnd, maxEnd)
    if (outputDeltaMs == 0L) return currentEnd
    constantMainSourceDelta(clip, outputDeltaMs)?.let { sourceDelta ->
        return addSaturated(currentEnd, sourceDelta).coerceIn(minEnd, maxEnd)
    }
    val targetDuration = addSaturated(
        clip.copy(trimStartMs = start, trimEndMs = currentEnd).effectiveDurationMs(),
        outputDeltaMs
    )

    fun outputDuration(end: Long) = clip.copy(trimStartMs = start, trimEndMs = end).effectiveDurationMs()
    val minDuration = outputDuration(minEnd)
    val maxDuration = outputDuration(maxEnd)
    val target = targetDuration.coerceIn(minDuration, maxDuration)

    // Output duration increases monotonically as the source tail advances.
    var low = minEnd
    var high = maxEnd
    while (low < high) {
        val mid = low + (high - low) / 2L
        if (outputDuration(mid) < target) low = mid + 1L else high = mid
    }
    return closestBoundary(low, if (low > minEnd) low - 1L else low, target, ::outputDuration)
}

/** Avoid choosing an arbitrary edge of a rounded output-duration plateau at constant speed. */
private fun constantMainSourceDelta(clip: MediaClip, outputDeltaMs: Long): Long? {
    if (clip.type != ClipType.VIDEO || clip.trackIndex != 0 ||
        SpeedCurveMath.sanitize(clip.speedPoints).size >= 2) return null
    return (outputDeltaMs.toDouble() * SpeedCurveMath.sanitizeConstantSpeed(clip.speedMultiplier))
        .roundToLong()
}

private inline fun closestBoundary(
    first: Long,
    second: Long,
    targetDuration: Long,
    outputDuration: (Long) -> Long
): Long = if (
    absoluteDifference(outputDuration(first), targetDuration) <=
    absoluteDifference(outputDuration(second), targetDuration)
) first else second

private fun absoluteDifference(a: Long, b: Long): Long =
    if (a >= b) a - b else b - a

private fun addSaturated(a: Long, b: Long): Long = when {
    b > 0L && a > Long.MAX_VALUE - b -> Long.MAX_VALUE
    b < 0L && a < Long.MIN_VALUE - b -> Long.MIN_VALUE
    else -> a + b
}

private fun subtractSaturated(a: Long, b: Long): Long = when {
    b < 0L && a > Long.MAX_VALUE + b -> Long.MAX_VALUE
    b > 0L && a < Long.MIN_VALUE + b -> Long.MIN_VALUE
    else -> a - b
}
