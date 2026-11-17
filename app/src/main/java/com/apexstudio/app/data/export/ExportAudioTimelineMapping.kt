package com.apexstudio.app.data.export

/**
 * Pure mapping helpers for placing trimmed sources on the export PCM timeline.
 * Millisecond timestamps can come from persisted projects, so converting by
 * multiplying the full value by the sample rate must not wrap into an earlier
 * timeline position.
 */
internal object ExportAudioTimelineMapping {
    private const val SPEED_CHANGE_INTERVAL_US = 10_000L

    /** Converts a non-negative millisecond position to frames, saturating on overflow. */
    fun millisToFrames(millis: Long, sampleRate: Int): Long =
        com.apexstudio.app.data.audio.AudioTimelineMath.millisToFrames(millis, sampleRate)

    /** Converts a non-negative millisecond source position to microseconds without wrapping. */
    fun millisToMicros(millis: Long): Long {
        val ms = millis.coerceAtLeast(0L)
        return if (ms > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else ms * 1_000L
    }

    /** Timeline start of a source sequence, normalized and saturated for Media3 gaps. */
    fun timelinePositionUs(positionMs: Long): Long = millisToMicros(positionMs)

    /**
     * Source trim start and retained frame count, normalized to non-negative
     * source time. An invalid/empty interval maps to zero retained frames.
     */
    fun sourceTrim(trimStartMs: Long, trimEndMs: Long, sampleRate: Int): SourceTrim {
        require(sampleRate > 0) { "sampleRate must be positive" }
        val startMs = trimStartMs.coerceAtLeast(0L)
        if (trimEndMs <= startMs) return SourceTrim(startMs, 0L)
        // Both values are now non-negative and end > start, so subtraction is safe.
        return SourceTrim(startMs, millisToFrames(trimEndMs - startMs, sampleRate))
    }

    /** Positive trimmed source span in microseconds, saturated on overflow. */
    fun sourceSpanUs(trimStartMs: Long, trimEndMs: Long): Long? {
        val startMs = trimStartMs.coerceAtLeast(0L)
        val endMs = trimEndMs.coerceAtLeast(0L)
        if (endMs <= startMs) return null
        val spanMs = endMs - startMs
        return if (spanMs > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else spanMs * 1_000L
    }

    /** Half-open end of a placed PCM span, including saturated retained durations. */
    fun endFrame(startFrame: Long, frameCount: Long): Long =
        com.apexstudio.app.data.audio.AudioTimelineMath.endFrame(startFrame, frameCount)

    /**
     * Fraction in the original clip's speed curve for a segment-local source time.
     * [curveOffsetUs] is the segment's source offset from the clip's original trim start.
     */
    fun speedCurveFractionAtSegmentTime(
        timeUs: Long,
        curveOffsetUs: Long,
        curveSpanUs: Long
    ): Float {
        if (curveSpanUs <= 0L) return 0f
        val curveTimeUs = curveTimeUsAtSegmentOffset(timeUs, curveOffsetUs, curveSpanUs)
        return (curveTimeUs.toDouble() / curveSpanUs.toDouble()).toFloat()
    }

    /**
     * Next 10 ms source-time boundary for a variable-speed audio processor,
     * or null when the original curve has reached its end. With an offset, the
     * returned timestamp is translated back to the segment-local source clock.
     * Media3 providers must not return the current/past timestamp.
     */
    fun nextSpeedChangeTimeUs(timeUs: Long, spanUs: Long, curveOffsetUs: Long = 0L): Long? {
        if (spanUs <= 0L) return null
        val itemTimeUs = timeUs.coerceAtLeast(0L)
        val normalizedOffsetUs = curveOffsetUs.coerceIn(0L, spanUs)
        val curveTimeUs = curveTimeUsAtSegmentOffset(itemTimeUs, normalizedOffsetUs, spanUs)
        if (curveTimeUs >= spanUs) return null

        val remainderUs = curveTimeUs % SPEED_CHANGE_INTERVAL_US
        val deltaUs = SPEED_CHANGE_INTERVAL_US - remainderUs
        val nextCurveTimeUs = (if (curveTimeUs > Long.MAX_VALUE - deltaUs) {
            Long.MAX_VALUE
        } else {
            curveTimeUs + deltaUs
        }).coerceAtMost(spanUs)
        val nextItemTimeUs = nextCurveTimeUs - normalizedOffsetUs
        return nextItemTimeUs.takeIf { it > itemTimeUs }
    }

    private fun curveTimeUsAtSegmentOffset(timeUs: Long, curveOffsetUs: Long, curveSpanUs: Long): Long {
        if (curveSpanUs <= 0L) return 0L
        val offsetUs = curveOffsetUs.coerceIn(0L, curveSpanUs)
        val localTimeUs = timeUs.coerceAtLeast(0L)
        val absoluteUs = if (localTimeUs > Long.MAX_VALUE - offsetUs) {
            Long.MAX_VALUE
        } else {
            localTimeUs + offsetUs
        }
        return absoluteUs.coerceAtMost(curveSpanUs)
    }

    data class SourceTrim(val startMs: Long, val frameCount: Long)
}
