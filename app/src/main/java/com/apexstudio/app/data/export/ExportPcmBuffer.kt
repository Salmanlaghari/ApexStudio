package com.apexstudio.app.data.export

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decoder-buffer PCM access. Offsets are relative to BufferInfo, not buffer position. */
internal object ExportPcmSamples {
    // Android AudioFormat PCM encodings (numeric values also work below their API level).
    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        2 -> 2 // PCM 16 bit
        3 -> 1 // PCM unsigned 8 bit, NOT float
        4 -> 4 // PCM float
        21 -> 3 // PCM packed signed 24 bit
        22 -> 4 // PCM signed 32 bit
        else -> throw IllegalArgumentException("Unsupported PCM encoding: $encoding")
    }

    fun window(buffer: ByteBuffer, offset: Int, size: Int): ByteBuffer {
        require(offset >= 0 && size >= 0 && offset <= buffer.capacity() - size) {
            "Invalid decoded PCM buffer range"
        }
        return buffer.duplicate().apply {
            clear()
            position(offset)
            limit(offset + size)
        }.slice().order(ByteOrder.LITTLE_ENDIAN)
    }

    fun sample(buffer: ByteBuffer, index: Int, encoding: Int): Float {
        val bytes = bytesPerSample(encoding)
        require(index >= 0 && index.toLong() * bytes + bytes <= buffer.limit())
        val offset = index * bytes
        val value = when (encoding) {
            2 -> buffer.getShort(offset) / 32768f
            3 -> ((buffer.get(offset).toInt() and 0xff) - 128) / 128f
            4 -> buffer.getFloat(offset)
            21 -> {
                val signed = (buffer.get(offset).toInt() and 0xff) or
                    ((buffer.get(offset + 1).toInt() and 0xff) shl 8) or
                    (buffer.get(offset + 2).toInt() shl 16)
                signed / 8388608f
            }
            22 -> (buffer.getInt(offset) / 2147483648.0).toFloat()
            else -> error("Validated encoding")
        }
        return if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
    }
}

/** Reuses storage after consumption rather than retaining PCM for an entire export. */
internal class ExportPcmQueue(private val channels: Int = 2) {
    init { require(channels > 0) }
    var samples = FloatArray(8192 * channels)
        private set
    var frames: Int = 0
        private set
    private var position = 0
    val isEmpty: Boolean get() = position >= frames

    fun prepareAppend(extraFrames: Int) {
        require(extraFrames >= 0)
        if (isEmpty) {
            frames = 0
            position = 0
        }
        val needed = (frames.toLong() + extraFrames) * channels
        require(needed <= Int.MAX_VALUE) { "Decoded PCM buffer too large" }
        if (needed > samples.size) {
            samples = samples.copyOf(maxOf(needed, minOf(samples.size.toLong() * 2, Int.MAX_VALUE.toLong())).toInt())
        }
    }

    fun appended(count: Int) {
        require(count >= 0 && (frames.toLong() + count) * channels <= samples.size)
        frames += count
    }

    fun read(out: FloatArray, outputFrame: Int, requestedFrames: Int): Int {
        require(outputFrame >= 0 && requestedFrames >= 0)
        val count = minOf(requestedFrames, frames - position)
        require((outputFrame.toLong() + count) * channels <= out.size)
        System.arraycopy(samples, position * channels, out, outputFrame * channels, count * channels)
        position += count
        return count
    }
}
