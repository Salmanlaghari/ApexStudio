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
    fun millisToFrames(millis: Long, sampleRate: Int): Long {
        require(sampleRate > 0) { "sampleRate must be positive" }
        val ms = millis.coerceAtLeast(0L)
        val wholeSeconds = ms / 1_000L
        val remainingMs = ms % 1_000L
        if (wholeSeconds > Long.MAX_VALUE / sampleRate) return Long.MAX_VALUE
        val wholeFrames = wholeSeconds * sampleRate
        val fractionalFrames = (remainingMs * sampleRate) / 1_000L
        return if (wholeFrames > Long.MAX_VALUE - fractionalFrames) {
            Long.MAX_VALUE
        } else {
            wholeFrames + fractionalFrames
        }
    }

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
    fun endFrame(startFrame: Long, frameCount: Long): Long {
        val start = startFrame.coerceAtLeast(0L)
        val count = frameCount.coerceAtLeast(0L)
        return if (start > Long.MAX_VALUE - count) Long.MAX_VALUE else start + count
    }

    /**
     * Next 10 ms source-time boundary for a variable-speed audio processor,
     * or null when the curve has reached its end. Media3 providers must not
     * return the current/past timestamp: doing so at the clip endpoint can
     * leave the processor repeatedly querying a speed change that cannot occur.
     */
    fun nextSpeedChangeTimeUs(timeUs: Long, spanUs: Long): Long? {
        if (spanUs <= 0L) return null
        val currentUs = timeUs.coerceAtLeast(0L)
        if (currentUs >= spanUs) return null

        val remainderUs = currentUs % SPEED_CHANGE_INTERVAL_US
        val deltaUs = SPEED_CHANGE_INTERVAL_US - remainderUs
        val nextUs = if (currentUs > Long.MAX_VALUE - deltaUs) {
            Long.MAX_VALUE
        } else {
            currentUs + deltaUs
        }
        return minOf(nextUs, spanUs)
    }

    data class SourceTrim(val startMs: Long, val frameCount: Long)
}
