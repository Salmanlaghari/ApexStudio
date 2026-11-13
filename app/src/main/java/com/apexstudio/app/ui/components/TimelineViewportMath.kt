package com.apexstudio.app.ui.components

import kotlin.math.ceil
import kotlin.math.roundToInt

private val TIMELINE_RULER_LABEL_STEPS_SECONDS = intArrayOf(
    1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1_800, 3_600, 7_200,
    14_400, 28_800, 43_200, 86_400
)

/** Pick a readable ruler interval while preserving the current cadence at normal/high zoom. */
internal fun timelineRulerLabelStepSeconds(
    secondWidthDp: Float,
    labelWidthDp: Float,
    labelGapDp: Float = 2f
): Int {
    val baselineStep = if (secondWidthDp >= 180f) 1 else 2
    if (!secondWidthDp.isFinite() || secondWidthDp <= 0f || !labelWidthDp.isFinite()) {
        return baselineStep
    }

    val gap = labelGapDp.takeIf { it.isFinite() && it > 0f } ?: 0f
    val requiredStep = ceil((labelWidthDp.coerceAtLeast(0f) + gap) / secondWidthDp)
        .toInt()
        .coerceAtLeast(baselineStep)
    return TIMELINE_RULER_LABEL_STEPS_SECONDS.firstOrNull { it >= requiredStep } ?: requiredStep
}

/**
 * Return the horizontal scroll that keeps the timeline time under a moving pinch centroid fixed.
 * If [oldZoom] and [newZoom] are equal, this is pure two-finger panning; otherwise it anchors zoom.
 */
internal fun timelineScrollForCentroid(
    currentScrollPx: Int,
    previousCentroidX: Float,
    centroidX: Float,
    oldZoom: Float,
    newZoom: Float,
    density: Float
): Int {
    if (!oldZoom.isFinite() || !newZoom.isFinite() || !density.isFinite() ||
        oldZoom <= 0f || newZoom <= 0f || density <= 0f
    ) return currentScrollPx.coerceAtLeast(0)

    val oldMsToPx = (54f * oldZoom / 1000f) * density
    val newMsToPx = (54f * newZoom / 1000f) * density
    if (oldMsToPx <= 0f || newMsToPx <= 0f) return currentScrollPx.coerceAtLeast(0)
    val timeAtPreviousCentroidMs = (currentScrollPx + previousCentroidX) / oldMsToPx
    return (timeAtPreviousCentroidMs * newMsToPx - centroidX).roundToInt().coerceAtLeast(0)
}
