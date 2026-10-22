package com.apexstudio.app.presentation.viewmodel

/**
 * Integer-FPS timeline frame arithmetic. Frame timestamps are rounded from the
 * frame index rather than repeatedly adding a rounded frame duration, so 24,
 * 30, and 60fps grids do not accumulate millisecond drift.
 */
internal object TimelineFrameMath {
    /** Timestamp in milliseconds for a zero-based frame index. */
    fun frameTimeMs(frameIndex: Long, fps: Int): Long {
        require(fps > 0) { "fps must be positive" }
        if (frameIndex <= 0L) return 0L
        return kotlin.math.round(frameIndex.toDouble() * 1_000.0 / fps).toLong()
    }

    /**
     * Move to the adjacent frame-grid timestamp in [forward]'s direction.
     * An off-grid position moves to the next/previous grid point; an exact
     * frame timestamp moves by one full frame. The result is bounded by the
     * inclusive timeline interval [0, durationMs].
     */
    fun step(positionMs: Long, forward: Boolean, fps: Int, durationMs: Long): Long {
        require(fps > 0) { "fps must be positive" }
        val duration = durationMs.coerceAtLeast(0L)
        val position = positionMs.coerceIn(0L, duration)
        if (duration == 0L) return 0L

        val framePosition = position.toDouble() * fps / 1_000.0
        val nearestFrame = kotlin.math.round(framePosition).toLong()
        val onFrameGrid = frameTimeMs(nearestFrame, fps) == position
        val targetFrame = when {
            onFrameGrid && forward -> nearestFrame + 1L
            onFrameGrid -> nearestFrame - 1L
            forward -> kotlin.math.ceil(framePosition).toLong()
            else -> kotlin.math.floor(framePosition).toLong()
        }.coerceAtLeast(0L)

        val targetMs = frameTimeMs(targetFrame, fps)
        return targetMs.coerceIn(0L, duration)
    }
}
