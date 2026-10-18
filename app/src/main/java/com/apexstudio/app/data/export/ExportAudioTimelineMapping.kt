package com.apexstudio.app.data.export

/**
 * Pure mapping helpers for placing trimmed sources on the export PCM timeline.
 * Millisecond timestamps can come from persisted projects, so converting by
 * multiplying the full value by the sample rate must not wrap into an earlier
 * timeline position.
 */
internal object ExportAudioTimelineMapping {
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

    /** Half-open end of a placed PCM span, including saturated retained durations. */
    fun endFrame(startFrame: Long, frameCount: Long): Long {
        val start = startFrame.coerceAtLeast(0L)
        val count = frameCount.coerceAtLeast(0L)
        return if (start > Long.MAX_VALUE - count) Long.MAX_VALUE else start + count
    }

    data class SourceTrim(val startMs: Long, val frameCount: Long)
}
