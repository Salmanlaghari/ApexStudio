package com.apexstudio.app.data.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.VolumeKeyframe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * Applies a standalone audio track's scalar/keyframed absolute volume and edge fades.
 * Attach this after pitch/speed processing: its frame clock then follows the samples
 * actually emitted into the AAC timeline, not the pre-transform source clock.
 */
class AudioTrackEnvelopeAudioProcessor(
    scalarVolume: Float,
    volumeKeyframes: List<VolumeKeyframe>,
    timelinePositionMs: Long,
    fadeInMs: Long,
    fadeOutMs: Long,
    retainedDurationMs: Long,
    globalGain: Float = 1f
) : AudioProcessor {
    companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN)
    }

    private val scalarGain = if (scalarVolume.isFinite()) scalarVolume.coerceIn(0f, 1f) else 1f
    private val keyframes = volumeKeyframes
        .filter { it.volume.isFinite() }
        .map { it.copy(volume = it.volume.coerceIn(0f, 1f)) }
        .sortedBy { it.timeMs }
    private val timelinePositionMs = timelinePositionMs.coerceAtLeast(0L)
    private val globalGain = if (globalGain.isFinite()) globalGain.coerceIn(0f, 2f) else 1f
    private val fadeInMs = fadeInMs.coerceAtLeast(0L)
    private val fadeOutMs = fadeOutMs.coerceAtLeast(0L)
    private val retainedDurationMs = retainedDurationMs.coerceAtLeast(0L)

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var frameClock = 0L
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        require(inputAudioFormat.sampleRate > 0) { "sampleRate must be positive" }
        require(inputAudioFormat.channelCount in 1..2) { "Audio track envelope supports mono or stereo PCM" }
        require(inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            "Unsupported audio track envelope encoding: ${inputAudioFormat.encoding}"
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        resetStream()
        return inputAudioFormat
    }

    override fun isActive(): Boolean = true
    override fun getOutput(): ByteBuffer = outputBuffer
    override fun isEnded(): Boolean = inputEnded && !outputBuffer.hasRemaining()

    override fun queueInput(inputBuffer: ByteBuffer) {
        check(sampleRate > 0) { "Audio processor must be configured before queueInput" }
        val bytesPerSample = if (encoding == C.ENCODING_PCM_16BIT) 2 else 4
        val frameBytes = bytesPerSample * channelCount
        val byteCount = inputBuffer.remaining()
        require(byteCount % frameBytes == 0) { "Input must contain complete PCM frames" }
        if (byteCount == 0) return
        val input = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frameCount = byteCount / frameBytes
        val result = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until frameCount) {
            val absoluteMs = saturatingAdd(timelinePositionMs, framesToMillis(frameClock))
            val gain = volumeAt(absoluteMs) * fadeAt(frameClock) * globalGain
            repeat(channelCount) {
                if (encoding == C.ENCODING_PCM_16BIT) {
                    val value = input.short.toInt()
                    val scaled = (value * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    result.putShort(scaled.toShort())
                } else {
                    val value = input.float
                    val clean = if (value.isFinite()) value else 0f
                    result.putFloat((clean * gain).coerceIn(-1f, 1f))
                }
            }
            if (frameClock < Long.MAX_VALUE) frameClock++
        }
        inputBuffer.position(inputBuffer.limit())
        result.flip()
        outputBuffer = result
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun flush() {
        resetStream()
    }

    override fun reset() {
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
    }

    private fun resetStream() {
        frameClock = 0L
        inputEnded = false
        outputBuffer = EMPTY_BUFFER
    }

    private fun volumeAt(timeMs: Long): Float {
        if (keyframes.isEmpty()) return scalarGain
        val first = keyframes.first()
        if (timeMs <= first.timeMs) return first.volume.coerceIn(0f, 1f)
        val last = keyframes.last()
        if (timeMs >= last.timeMs) return last.volume.coerceIn(0f, 1f)
        for (index in 0 until keyframes.lastIndex) {
            val a = keyframes[index]
            val b = keyframes[index + 1]
            if (timeMs >= a.timeMs && timeMs <= b.timeMs) {
                // Double subtraction avoids overflow for extreme signed timestamps.
                val span = (b.timeMs.toDouble() - a.timeMs.toDouble()).coerceAtLeast(1.0)
                val fraction = ((timeMs.toDouble() - a.timeMs.toDouble()) / span).toFloat().coerceIn(0f, 1f)
                return a.volume + (b.volume - a.volume) * fraction
            }
        }
        return scalarGain
    }

    /** Matches MusicExportMixer.fadeEnvelope's linear, half-open frame boundaries. */
    private fun fadeAt(positionFrames: Long): Float {
        var envelope = 1f
        val fadeInFrames = millisToFrames(fadeInMs)
        if (fadeInFrames > 0L && positionFrames < fadeInFrames) {
            envelope = minOf(envelope, positionFrames.toDouble().div(fadeInFrames).toFloat())
        }
        val fadeOutFrames = millisToFrames(fadeOutMs)
        val playFrames = millisToFrames(retainedDurationMs)
        if (fadeOutFrames > 0L && playFrames > 0L) {
            val remaining = playFrames - positionFrames
            if (remaining < fadeOutFrames) {
                envelope = minOf(envelope, remaining.coerceAtLeast(0L).toDouble().div(fadeOutFrames).toFloat())
            }
        }
        return envelope
    }

    private fun millisToFrames(ms: Long): Long {
        if (ms <= 0L || sampleRate <= 0) return 0L
        val frames = floor(ms.toDouble() * sampleRate.toDouble() / 1000.0)
        return if (!frames.isFinite() || frames >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else frames.toLong()
    }

    private fun framesToMillis(frames: Long): Long {
        if (frames <= 0L || sampleRate <= 0) return 0L
        val millis = floor(frames.toDouble() * 1000.0 / sampleRate.toDouble())
        return if (!millis.isFinite() || millis >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else millis.toLong()
    }

    private fun saturatingAdd(a: Long, b: Long): Long = when {
        b > 0L && a > Long.MAX_VALUE - b -> Long.MAX_VALUE
        b < 0L && a < Long.MIN_VALUE - b -> Long.MIN_VALUE
        else -> a + b
    }
}
