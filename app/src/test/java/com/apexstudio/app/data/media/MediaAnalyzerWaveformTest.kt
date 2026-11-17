package com.apexstudio.app.data.media

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaAnalyzerWaveformTest {
    @Test
    fun `overlapping decoder buffer is clipped at trim start`() {
        val analyzer = MediaAnalyzer()
        val trimStartUs = 1_000_000L
        val sums = DoubleArray(4)
        val counts = LongArray(4)

        val bufferEntirelyBeforeTrim = pcm16(32767, 32767, 32767, 32767)
        analyzer.accumulatePcm(
            bufferEntirelyBeforeTrim,
            offset = 0,
            size = bufferEntirelyBeforeTrim.remaining(),
            presentationTimeUs = 996_000L,
            startUs = trimStartUs,
            sampleRate = 1_000,
            channelCount = 1,
            totalFrames = 4L,
            sums = sums,
            counts = counts
        )
        assertEquals("a buffer ending before trim start contributes nothing", 0L, counts.sum())

        val overlappingBuffer = pcm16(
            32767, 32767, 32767, 32767,
            8192, 16384, 24576, 32767
        )
        analyzer.accumulatePcm(
            overlappingBuffer,
            offset = 0,
            size = overlappingBuffer.remaining(),
            presentationTimeUs = 996_000L,
            startUs = trimStartUs,
            sampleRate = 1_000,
            channelCount = 1,
            totalFrames = 4L,
            sums = sums,
            counts = counts
        )

        for (bucket in counts.indices) assertEquals("bucket $bucket should contain one in-window frame", 1L, counts[bucket])
        assertEquals(0.25, sums[0] / counts[0], 0.00001)
        assertEquals(0.5, sums[1] / counts[1], 0.00001)
        assertEquals(0.75, sums[2] / counts[2], 0.00001)
        assertEquals(32767.0 / 32768.0, sums[3] / counts[3], 0.00001)
    }

    @Test
    fun `fractional trim boundary skips sample that starts before trim`() {
        val analyzer = MediaAnalyzer()
        val sums = DoubleArray(2)
        val counts = LongArray(2)
        val buffer = pcm16(32767, 8192, 16384)

        analyzer.accumulatePcm(
            buffer,
            offset = 0,
            size = buffer.remaining(),
            presentationTimeUs = 999_750L,
            startUs = 1_000_000L,
            sampleRate = 2_000,
            channelCount = 1,
            totalFrames = 2L,
            sums = sums,
            counts = counts
        )

        assertEquals(1L, counts[0])
        assertEquals(1L, counts[1])
        assertEquals(0.25, sums[0] / counts[0], 0.00001)
        assertEquals(0.5, sums[1] / counts[1], 0.00001)
    }

    private fun pcm16(vararg samples: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buffer.putShort(it.toShort()) }
        buffer.flip()
        return buffer
    }
}
