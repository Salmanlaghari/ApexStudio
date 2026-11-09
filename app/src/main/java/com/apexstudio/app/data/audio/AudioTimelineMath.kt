package com.apexstudio.app.data.audio

import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.VolumeKeyframe
import com.apexstudio.app.domain.model.timeline.Keyframe

/**
 * Pure timeline math shared by the real-time preview and PCM export mixers.
 *
 * Project placement and keyframes use absolute milliseconds. Export uses
 * sample frames, so conversions are explicit, non-negative, floor-based, and
 * saturating. Source windows are half-open and honor both the user trim and
 * known source duration.
 */
internal object AudioTimelineMath {
    data class SourceWindow(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = endMs - startMs
    }

    data class Placement(
        val sourceWindow: SourceWindow,
        val startFrame: Long,
        val playFrames: Long
    ) {
        /** Half-open timeline end; saturates instead of wrapping. */
        val endFrame: Long get() = AudioTimelineMath.endFrame(startFrame, playFrames)
    }

    /** Normalized retained source interval, or null for an empty/invalid trim. */
    fun sourceWindow(track: AudioTrack): SourceWindow? {
        val startMs = track.trimStartMs.coerceAtLeast(0L)
        val endMs = minOf(track.trimEndMs, track.sourceEndMs).coerceAtLeast(0L)
        return if (endMs > startMs) SourceWindow(startMs, endMs) else null
    }

    /** Map an absolute project playhead position to a source millisecond. */
    fun sourcePositionMs(track: AudioTrack, timelinePositionMs: Long): Long? {
        val window = sourceWindow(track) ?: return null
        val timelineStartMs = track.startTimeMs.coerceAtLeast(0L)
        if (timelinePositionMs < timelineStartMs) return null
        val elapsedMs = timelinePositionMs - timelineStartMs
        if (elapsedMs >= window.durationMs) return null
        return window.startMs + elapsedMs
    }

    /** Convert a track's shared source window and placement to export frames. */
    fun placement(track: AudioTrack, sampleRate: Int): Placement? {
        require(sampleRate > 0) { "sampleRate must be positive" }
        val window = sourceWindow(track) ?: return null
        return Placement(
            sourceWindow = window,
            startFrame = millisToFrames(track.startTimeMs, sampleRate),
            playFrames = millisToFrames(window.durationMs, sampleRate)
        )
    }

    /** Convert non-negative milliseconds to frames using floor and saturation. */
    fun millisToFrames(millis: Long, sampleRate: Int): Long {
        require(sampleRate > 0) { "sampleRate must be positive" }
        val ms = millis.coerceAtLeast(0L)
        val rate = sampleRate.toLong()
        val wholeSeconds = ms / 1_000L
        val remainingMs = ms % 1_000L
        if (wholeSeconds > Long.MAX_VALUE / rate) return Long.MAX_VALUE
        val wholeFrames = wholeSeconds * rate
        val fractionalFrames = (remainingMs * rate) / 1_000L
        return if (wholeFrames > Long.MAX_VALUE - fractionalFrames) {
            Long.MAX_VALUE
        } else {
            wholeFrames + fractionalFrames
        }
    }

    /** Convert a non-negative global frame to its floor-based timeline millisecond. */
    fun frameTimeMs(frame: Long, sampleRate: Int): Long {
        require(sampleRate > 0) { "sampleRate must be positive" }
        val normalizedFrame = frame.coerceAtLeast(0L)
        val rate = sampleRate.toLong()
        val wholeSeconds = normalizedFrame / rate
        val remainingFrames = normalizedFrame % rate
        if (wholeSeconds > Long.MAX_VALUE / 1_000L) return Long.MAX_VALUE
        val wholeMs = wholeSeconds * 1_000L
        val fractionalMs = (remainingFrames * 1_000L) / rate
        return if (wholeMs > Long.MAX_VALUE - fractionalMs) {
            Long.MAX_VALUE
        } else {
            wholeMs + fractionalMs
        }
    }

    /** Half-open frame end with normalized inputs and saturating addition. */
    fun endFrame(startFrame: Long, frameCount: Long): Long {
        val start = startFrame.coerceAtLeast(0L)
        val count = frameCount.coerceAtLeast(0L)
        return if (start > Long.MAX_VALUE - count) Long.MAX_VALUE else start + count
    }

    /** Shared mute/solo policy; a muted solo still suppresses non-solo tracks. */
    fun isEnabled(track: AudioTrack, tracks: List<AudioTrack>): Boolean =
        !track.isMuted && (tracks.none { it.isSolo } || track.isSolo)

    /** Preview gain at an absolute timeline millisecond, including edge fades. */
    fun playbackVolumeAt(
        track: AudioTrack,
        timelinePositionMs: Long,
        tracks: List<AudioTrack>
    ): Float {
        if (!isEnabled(track, tracks)) return 0f
        val window = sourceWindow(track) ?: return 0f
        val timelineStartMs = track.startTimeMs.coerceAtLeast(0L)
        if (timelinePositionMs < timelineStartMs) return 0f
        val elapsedMs = timelinePositionMs - timelineStartMs
        if (elapsedMs >= window.durationMs) return 0f
        val keyedVolume = volumeAt(track, timelinePositionMs)
        val envelope = fadeEnvelopeAtMs(
            elapsedMs = elapsedMs,
            playDurationMs = window.durationMs,
            fadeInMs = track.fadeInMs,
            fadeOutMs = track.fadeOutMs
        )
        return (keyedVolume * envelope).coerceIn(0f, 1f)
    }

    /** Absolute timeline volume; export may pass a list sorted once per track. */
    fun volumeAt(track: AudioTrack, timelinePositionMs: Long): Float =
        volumeAt(track, timelinePositionMs, track.volumeKeyframes.sortedBy { it.timeMs })

    fun volumeAt(
        track: AudioTrack,
        timelinePositionMs: Long,
        sortedKeyframes: List<VolumeKeyframe>
    ): Float {
        val fallback = normalizedVolume(track.volume)
        val interpolated = interpolateVolumeKeyframes(sortedKeyframes, fallback, timelinePositionMs)
        return normalizedVolume(interpolated)
    }

    /** Absolute timeline pan; export may pass a list sorted once per track. */
    fun panAt(track: AudioTrack, timelinePositionMs: Long): Float =
        panAt(track, timelinePositionMs, track.panKeyframes.sortedBy { it.timeMs })

    fun panAt(
        track: AudioTrack,
        timelinePositionMs: Long,
        sortedKeyframes: List<Keyframe<Float>>
    ): Float {
        val fallback = normalizedPan(track.pan)
        return normalizedPan(
            AudioTrack.interpolatePanKeyframes(sortedKeyframes, fallback, timelinePositionMs)
        )
    }

    fun stereoGainsAt(track: AudioTrack, timelinePositionMs: Long): Pair<Float, Float> =
        stereoGains(panAt(track, timelinePositionMs))

    fun stereoGainsAt(
        track: AudioTrack,
        timelinePositionMs: Long,
        sortedPanKeyframes: List<Keyframe<Float>>
    ): Pair<Float, Float> = stereoGains(panAt(track, timelinePositionMs, sortedPanKeyframes))

    /** Linear left/right gains preserve the center mix level. */
    fun stereoGains(pan: Float): Pair<Float, Float> {
        val normalizedPan = normalizedPan(pan)
        return (1f - normalizedPan.coerceAtLeast(0f)) to
            (1f + normalizedPan.coerceAtMost(0f))
    }

    /** Linear fade envelope at a millisecond offset within a half-open play window. */
    fun fadeEnvelopeAtMs(
        elapsedMs: Long,
        playDurationMs: Long,
        fadeInMs: Long,
        fadeOutMs: Long
    ): Float = fadeEnvelope(
        position = elapsedMs,
        duration = playDurationMs,
        fadeIn = fadeInMs,
        fadeOut = fadeOutMs
    )

    /** Linear fade envelope at a frame offset, using the shared millisecond conversion. */
    fun fadeEnvelopeAtFrame(
        positionFrames: Long,
        playFrames: Long,
        fadeInMs: Long,
        fadeOutMs: Long,
        sampleRate: Int
    ): Float = fadeEnvelope(
        position = positionFrames,
        duration = playFrames,
        fadeIn = millisToFrames(fadeInMs, sampleRate),
        fadeOut = millisToFrames(fadeOutMs, sampleRate)
    )

    private fun fadeEnvelope(position: Long, duration: Long, fadeIn: Long, fadeOut: Long): Float {
        if (duration <= 0L || position < 0L || position >= duration) return 0f
        val remaining = duration - position
        var envelope = 1.0
        val normalizedFadeIn = fadeIn.coerceAtLeast(0L)
        if (normalizedFadeIn > 0L && position < normalizedFadeIn) {
            envelope = minOf(envelope, position.toDouble() / normalizedFadeIn.toDouble())
        }
        val normalizedFadeOut = fadeOut.coerceAtLeast(0L)
        if (normalizedFadeOut > 0L && remaining < normalizedFadeOut) {
            envelope = minOf(envelope, remaining.toDouble() / normalizedFadeOut.toDouble())
        }
        return envelope.toFloat().coerceIn(0f, 1f)
    }

    private fun interpolateVolumeKeyframes(
        sortedKeyframes: List<VolumeKeyframe>,
        fallback: Float,
        timeMs: Long
    ): Float {
        if (sortedKeyframes.isEmpty()) return fallback
        if (timeMs <= sortedKeyframes.first().timeMs) return sortedKeyframes.first().volume
        if (timeMs >= sortedKeyframes.last().timeMs) return sortedKeyframes.last().volume
        for (index in 0 until sortedKeyframes.lastIndex) {
            val start = sortedKeyframes[index]
            val end = sortedKeyframes[index + 1]
            if (timeMs >= start.timeMs && timeMs <= end.timeMs) {
                val span = (end.timeMs.toDouble() - start.timeMs.toDouble()).coerceAtLeast(1.0)
                val fraction = (
                    (timeMs.toDouble() - start.timeMs.toDouble()) / span
                ).toFloat().coerceIn(0f, 1f)
                return start.volume + (end.volume - start.volume) * fraction
            }
        }
        return fallback
    }

    private fun normalizedVolume(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    private fun normalizedPan(value: Float): Float =
        if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
}
