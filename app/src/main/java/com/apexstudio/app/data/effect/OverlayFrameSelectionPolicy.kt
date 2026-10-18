package com.apexstudio.app.data.effect

/** Pure policy used by the PiP decoder; timestamps are source-media microseconds. */
internal object OverlayFrameSelectionPolicy {
    const val FUTURE_TOLERANCE_US = 33_000L

    enum class OutputAction {
        /** This frame is within the target window and may be rendered. */
        RENDER_AND_CONTINUE,
        /** Keep this codec output buffer until a later target reaches it. */
        HOLD_AND_STOP
    }

    /** A backwards timeline request requires a decoder seek; a future decoded PTS does not. */
    fun mustSeek(targetTimeUs: Long, previousTargetTimeUs: Long?): Boolean =
        previousTargetTimeUs != null && targetTimeUs < previousTargetTimeUs

    /** Reuse the current SurfaceTexture image while the target has not passed its PTS. */
    fun canReuseFrame(targetTimeUs: Long, frameTimeUs: Long?, hasFrame: Boolean): Boolean =
        hasFrame && frameTimeUs != null && targetTimeUs <= frameTimeUs

    fun actionForOutput(
        outputTimeUs: Long,
        targetTimeUs: Long,
        futureToleranceUs: Long = FUTURE_TOLERANCE_US
    ): OutputAction = if (outputTimeUs <= saturatedAdd(targetTimeUs, futureToleranceUs.coerceAtLeast(0L))) {
        OutputAction.RENDER_AND_CONTINUE
    } else {
        OutputAction.HOLD_AND_STOP
    }

    /** Convert milliseconds without wrapping near Long limits. */
    fun millisecondsToMicroseconds(milliseconds: Long): Long = when {
        milliseconds > Long.MAX_VALUE / 1_000L -> Long.MAX_VALUE
        milliseconds < Long.MIN_VALUE / 1_000L -> Long.MIN_VALUE
        else -> milliseconds * 1_000L
    }

    /** Convert a source-relative offset and trim origin without overflowing either operation. */
    fun sourceTimeUs(sourceOffsetMs: Long, trimStartMs: Long): Long = saturatedAdd(
        millisecondsToMicroseconds(sourceOffsetMs),
        millisecondsToMicroseconds(trimStartMs)
    )

    private fun saturatedAdd(left: Long, right: Long): Long = when {
        right > 0L && left > Long.MAX_VALUE - right -> Long.MAX_VALUE
        right < 0L && left < Long.MIN_VALUE - right -> Long.MIN_VALUE
        else -> left + right
    }
}
