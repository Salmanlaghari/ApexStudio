package com.apexstudio.app.ui.components

import kotlin.math.roundToInt

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
