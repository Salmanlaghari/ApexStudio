package com.apexstudio.app.data.export

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ExportPcmBufferTest {
    @Test fun `float encoding is four and nonfinite samples become silence`() {
        val buffer = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        listOf(0.25f, -0.5f, Float.NaN, Float.POSITIVE_INFINITY, 2f).forEach { buffer.putFloat(it) }
        assertEquals(4, ExportPcmSamples.bytesPerSample(4))
        assertEquals(0.25f, ExportPcmSamples.sample(buffer, 0, 4), 0f)
        assertEquals(-0.5f, ExportPcmSamples.sample(buffer, 1, 4), 0f)
        assertEquals(0f, ExportPcmSamples.sample(buffer, 2, 4), 0f)
        assertEquals(0f, ExportPcmSamples.sample(buffer, 3, 4), 0f)
        assertEquals(1f, ExportPcmSamples.sample(buffer, 4, 4), 0f)
    }

    @Test fun `unsigned eight bit is not decoded as float`() {
        val buffer = ByteBuffer.wrap(byteArrayOf(0, 128.toByte(), 255.toByte()))
        assertEquals(1, ExportPcmSamples.bytesPerSample(3))
        assertEquals(-1f, ExportPcmSamples.sample(buffer, 0, 3), 0f)
        assertEquals(0f, ExportPcmSamples.sample(buffer, 1, 3), 0f)
        assertEquals(127f / 128f, ExportPcmSamples.sample(buffer, 2, 3), 0f)
    }

    @Test fun `codec buffer offset isolates actual PCM bytes regardless of position`() {
        val buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(123456).putShort(16384).putShort(-16384).putInt(123456)
        buffer.position(10)
        buffer.limit(11)
        val pcm = ExportPcmSamples.window(buffer, 4, 4)
        assertEquals(0.5f, ExportPcmSamples.sample(pcm, 0, 2), 0f)
        assertEquals(-0.5f, ExportPcmSamples.sample(pcm, 1, 2), 0f)
        assertEquals(10, buffer.position())
        assertEquals(11, buffer.limit())
    }

    @Test fun `packed twenty four and signed thirty two bit preserve polarity`() {
        val pcm24 = ByteBuffer.wrap(byteArrayOf(0, 0, 128.toByte(), 0, 0, 64))
        assertEquals(-1f, ExportPcmSamples.sample(pcm24, 0, 21), 0f)
        assertEquals(0.5f, ExportPcmSamples.sample(pcm24, 1, 21), 0f)
        val pcm32 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(Int.MIN_VALUE).putInt(1073741824)
        assertEquals(-1f, ExportPcmSamples.sample(pcm32, 0, 22), 0f)
        assertEquals(0.5f, ExportPcmSamples.sample(pcm32, 1, 22), 0f)
    }

    @Test fun `consumed PCM storage is reused for long exports`() {
        val queue = ExportPcmQueue()
        val initial = queue.samples
        val out = FloatArray(128)
        repeat(10_000) { chunk ->
            queue.prepareAppend(64)
            assertEquals(0, queue.frames)
            repeat(128) { queue.samples[it] = chunk.toFloat() }
            queue.appended(64)
            assertEquals(64, queue.read(out, 0, 64))
            assertTrue(queue.isEmpty)
            assertEquals(chunk.toFloat(), out.last(), 0f)
        }
        assertSame(initial, queue.samples)
    }

    @Test fun `partial reads retain pending stereo order when appending`() {
        val queue = ExportPcmQueue()
        queue.prepareAppend(2)
        floatArrayOf(1f, 2f, 3f, 4f).copyInto(queue.samples)
        queue.appended(2)
        val out = FloatArray(6)
        assertEquals(1, queue.read(out, 0, 1))
        queue.prepareAppend(1)
        queue.samples[4] = 5f
        queue.samples[5] = 6f
        queue.appended(1)
        assertEquals(2, queue.read(out, 1, 2))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f), out, 0f)
        assertTrue(queue.isEmpty)
    }

    @Test fun `invalid PCM ranges encodings and allocations reject before access`() {
        assertThrows(IllegalArgumentException::class.java) { ExportPcmSamples.bytesPerSample(99) }
        assertThrows(IllegalArgumentException::class.java) {
            ExportPcmSamples.window(ByteBuffer.allocate(8), 7, Int.MAX_VALUE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExportPcmSamples.sample(ByteBuffer.allocate(4), Int.MAX_VALUE, 4)
        }
        assertThrows(IllegalArgumentException::class.java) { ExportPcmQueue().prepareAppend(Int.MAX_VALUE) }
    }
}
