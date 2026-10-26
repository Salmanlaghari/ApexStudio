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

/** Convert an 8 dp hit radius into milliseconds, with safe bounds for invalid or extreme scales. */
internal fun beatSnapThresholdMs(msToDp: Float, density: Float): Long {
    val pixelsPerMs = msToDp * density
    if (!msToDp.isFinite() || !density.isFinite() || !pixelsPerMs.isFinite() || pixelsPerMs <= 0f) {
        return 1L
    }
    return (8f / pixelsPerMs).coerceIn(1f, MAX_BEAT_SNAP_THRESHOLD_MS.toFloat()).roundToLong()
}

internal fun snapToBeatTime(
    timeMs: Long,
    beatsMs: List<Long>,
    enabled: Boolean,
    thresholdMs: Long
): Long {
    if (!enabled || thresholdMs < 0L) return timeMs
    var closest = timeMs
    var closestDistance = thresholdMs
    var found = false
    for (beatMs in beatsMs) {
        if (beatMs < 0L) continue
        val distance = absoluteTimeDistance(beatMs, timeMs)
        if (distance <= closestDistance && (!found || distance < closestDistance || beatMs < closest)) {
            closest = beatMs
            closestDistance = distance
            found = true
        }
    }
    return if (found) closest else timeMs
}

/**
 * Snap a video tail in project time, then invert the speed curve to the source
 * trim boundary using the same output-duration math as a manual trim drag.
 */
internal fun snapVideoTrimEndToBeat(
    clip: MediaClip,
    trimStartMs: Long,
    requestedEndMs: Long,
    timelineStartMs: Long,
    beatMarkersMs: List<Long>,
    enabled: Boolean,
    thresholdMs: Long
): Long {
    if (!enabled || beatMarkersMs.isEmpty()) return requestedEndMs
    val baseClip = clip.copy(trimStartMs = trimStartMs)
    val requestedClip = baseClip.copy(trimEndMs = requestedEndMs)
    val rawTimelineEnd = addSaturated(timelineStartMs, requestedClip.effectiveDurationMs())
    val snappedTimelineEnd = snapToBeatTime(rawTimelineEnd, beatMarkersMs, enabled, thresholdMs)
    if (snappedTimelineEnd == rawTimelineEnd) return requestedEndMs

    val targetDurationMs = subtractSaturated(snappedTimelineEnd, timelineStartMs)
    val outputDeltaMs = subtractSaturated(targetDurationMs, baseClip.effectiveDurationMs())
    return timelineTrimEndForOutputDeltaMs(baseClip, outputDeltaMs)
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

private fun absoluteTimeDistance(a: Long, b: Long): Long {
    val distance = if (a >= b) a - b else b - a
    return if (distance < 0L) Long.MAX_VALUE else distance
}

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

private const val MAX_BEAT_SNAP_THRESHOLD_MS = 5_000L
