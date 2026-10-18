package com.apexstudio.app.data.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** PCM encoding values defined by android.media.AudioFormat. Kept numeric here so this helper is JVM-testable. */
internal object PcmEncoding {
    const val PCM_16BIT = 2
    const val PCM_8BIT = 3
    const val PCM_FLOAT = 4
    const val PCM_24BIT_PACKED = 21
    const val PCM_32BIT = 22
}

/** Thrown instead of treating an unknown decoder output layout as PCM16. */
internal class UnsupportedPcmEncodingException(encoding: Int) :
    IllegalArgumentException("Unsupported MediaCodec PCM encoding: $encoding")

/** Primitive sample collector; grows geometrically without boxing each sample. */
internal class FloatSampleBuffer(initialCapacity: Int = 16_384) {
    private var values = FloatArray(initialCapacity.coerceAtLeast(1))
    var size: Int = 0
        private set

    fun append(value: Float) {
        if (size == values.size) values = values.copyOf(values.size.coerceAtMost(Int.MAX_VALUE / 2) * 2)
        values[size++] = value
    }

    fun toFloatArray(): FloatArray = values.copyOf(size)
}

/** Stateful linear resampler. Input may be supplied in arbitrary chunk sizes without boundary discontinuities. */
internal class StreamingLinearResampler(
    private val sourceRate: Int,
    private val targetRate: Int,
    private val emit: (Float) -> Unit
) {
    private var inputFrames = 0L
    private var outputFrames = 0L
    private var previous = 0f
    private var last = 0f
    private var finished = false

    init {
        require(sourceRate > 0) { "sourceRate must be positive" }
        require(targetRate > 0) { "targetRate must be positive" }
    }

    fun accept(sample: Float) {
        check(!finished) { "resampler already finished" }
        val index = inputFrames
        last = sample
        if (index == 0L) {
            emit(sample)
            outputFrames++
        } else {
            while (outputPosition(outputFrames) <= index.toDouble()) {
                val position = outputPosition(outputFrames)
                val fraction = (position - (index - 1L)).toFloat().coerceIn(0f, 1f)
                emit(previous + (sample - previous) * fraction)
                outputFrames++
            }
        }
        previous = sample
        inputFrames++
    }

    fun finish() {
        if (finished) return
        finished = true
        if (inputFrames == 0L) return
        // Match the conventional round(N * target/source) frame count while extending
        // beyond the final source instant by holding the final sample, as a finite clip requires.
        val expected = (inputFrames.toDouble() * targetRate / sourceRate).roundToInt().toLong()
        while (outputFrames < expected) {
            emit(last)
            outputFrames++
        }
    }

    private fun outputPosition(frame: Long): Double = frame.toDouble() * sourceRate / targetRate
}

/** Decode complete interleaved PCM frames from [bytes], average channels, and stream into [resampler]. */
internal object PcmAudioProcessor {
    fun process(bytes: ByteBuffer, encoding: Int, channels: Int, resampler: StreamingLinearResampler): Int {
        require(channels in 1..32) { "PCM channel count must be between 1 and 32" }
        val input = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = when (encoding) {
            PcmEncoding.PCM_8BIT -> 1
            PcmEncoding.PCM_16BIT -> 2
            PcmEncoding.PCM_24BIT_PACKED -> 3
            PcmEncoding.PCM_32BIT, PcmEncoding.PCM_FLOAT -> 4
            else -> throw UnsupportedPcmEncodingException(encoding)
        }
        val frameBytes = bytesPerSample * channels
        require(input.remaining() % frameBytes == 0) { "Decoder output ends inside a PCM frame" }
        val frames = input.remaining() / frameBytes
        repeat(frames) {
            var mono = 0f
            repeat(channels) { mono += readSample(input, encoding) }
            resampler.accept((mono / channels).coerceIn(-1f, 1f))
        }
        return frames
    }

    private fun readSample(input: ByteBuffer, encoding: Int): Float = when (encoding) {
        PcmEncoding.PCM_8BIT -> ((input.get().toInt() and 0xff) - 128) / 128f
        PcmEncoding.PCM_16BIT -> input.short / 32768f
        PcmEncoding.PCM_24BIT_PACKED -> {
            val b0 = input.get().toInt() and 0xff
            val b1 = input.get().toInt() and 0xff
            val b2 = input.get().toInt()
            val value = (b2 shl 16) or (b1 shl 8) or b0
            (if (value and 0x800000 != 0) value or -0x1000000 else value) / 8_388_608f
        }
        PcmEncoding.PCM_32BIT -> (input.int / 2_147_483_648.0).toFloat()
        PcmEncoding.PCM_FLOAT -> input.float.let { if (it.isFinite()) it else 0f }
        else -> throw UnsupportedPcmEncodingException(encoding)
    }
}
