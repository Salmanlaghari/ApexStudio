package com.apexstudio.app.data.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.apexstudio.app.domain.model.timeline.VideoClip
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Applies typed main-video volume and pan keyframes to PCM emitted by Media3.
 *
 * This processor runs after the paired speed processor, so its clock is the
 * output clock: project placement plus emitted output frames. [globalGain] is
 * kept separate from the clip's own scalar/keyframed volume to avoid applying
 * the clip baseline twice.
 */
class SharedVideoClipAudioProcessor(
    private val clip: VideoClip,
    globalGain: Float,
    private val timelineStartMs: Long
) : AudioProcessor {
    companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN)
    }

    private val globalGain = globalGain.takeIf { it.isFinite() }?.coerceIn(0f, 2f) ?: 1f
    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var frameClock = 0L
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        require(inputAudioFormat.sampleRate > 0) { "sampleRate must be positive" }
        require(inputAudioFormat.channelCount > 0) { "Audio must contain at least one channel" }
        require(
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        ) { "Unsupported typed video clip audio encoding: ${inputAudioFormat.encoding}" }
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
        if (byteCount == 0) {
            outputBuffer = EMPTY_BUFFER
            return
        }

        val input = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val result = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        repeat(byteCount / frameBytes) {
            val projectMs = saturatingAdd(timelineStartMs.coerceAtLeast(0L), framesToMillis(frameClock))
            val volume = clip.volumeAt(projectMs).takeIf { it.isFinite() }?.coerceIn(0f, 2f) ?: 0f
            val baseGain = (globalGain * volume).coerceIn(0f, 4f)
            val pan = if (channelCount < 2) {
                0f
            } else {
                clip.panAt(projectMs).takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
            }
            // Match AudioPlaybackMix's linear stereo pan. Extra source
            // channels retain the common volume gain without stereo panning.
            val leftGain = baseGain * (1f - pan.coerceAtLeast(0f))
            val rightGain = baseGain * (1f + pan.coerceAtMost(0f))
            repeat(channelCount) { channel ->
                val gain = when {
                    channelCount < 2 -> baseGain
                    channel == 0 -> leftGain
                    channel == 1 -> rightGain
                    else -> baseGain
                }
                if (encoding == C.ENCODING_PCM_16BIT) write16(input, result, gain)
                else writeFloat(input, result, gain)
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

    private fun write16(input: ByteBuffer, output: ByteBuffer, gain: Float) {
        val value = input.short.toInt()
        output.putShort((value * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
    }

    private fun writeFloat(input: ByteBuffer, output: ByteBuffer, gain: Float) {
        val value = input.float
        val clean = if (value.isFinite()) value else 0f
        output.putFloat((clean * gain).coerceIn(-1f, 1f))
    }

    private fun resetStream() {
        frameClock = 0L
        inputEnded = false
        outputBuffer = EMPTY_BUFFER
    }

    private fun framesToMillis(frames: Long): Long {
        if (frames <= 0L || sampleRate <= 0) return 0L
        val millis = frames.toDouble() * 1000.0 / sampleRate.toDouble()
        return if (!millis.isFinite() || millis >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else millis.toLong()
    }

    private fun saturatingAdd(a: Long, b: Long): Long = when {
        b > 0L && a > Long.MAX_VALUE - b -> Long.MAX_VALUE
        b < 0L && a < Long.MIN_VALUE - b -> Long.MIN_VALUE
        else -> a + b
    }
}
