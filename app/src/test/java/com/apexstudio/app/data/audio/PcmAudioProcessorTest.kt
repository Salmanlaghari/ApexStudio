package com.apexstudio.app.data.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class PcmAudioProcessorTest {
    @Test fun malformedFramesAndChannelCountsAreRejected() {
        val output = mutableListOf<Float>()
        val resampler = StreamingLinearResampler(16_000, 16_000, output::add)
        assertThrows(IllegalArgumentException::class.java) {
            PcmAudioProcessor.process(ByteBuffer.wrap(byteArrayOf(1, 2, 3)), PcmEncoding.PCM_16BIT, 2, resampler)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PcmAudioProcessor.process(ByteBuffer.allocate(0), PcmEncoding.PCM_32BIT, Int.MAX_VALUE, resampler)
        }
        assertTrue(output.isEmpty())
    }

    @Test
    fun `resampling is continuous when frames arrive in separate chunks`() {
        val actual = mutableListOf<Float>()
        val resampler = StreamingLinearResampler(4, 8, actual::add)
        floatArrayOf(0f, 1f).forEach(resampler::accept)
        floatArrayOf(0f).forEach(resampler::accept)
        resampler.finish()

        assertEquals(listOf(0f, 0.5f, 1f, 0.5f, 0f, 0f), actual)
    }

    @Test
    fun `downmix averages interleaved stereo samples`() {
        val output = mutableListOf<Float>()
        val resampler = StreamingLinearResampler(16_000, 16_000, output::add)
        val pcm = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(16_384).putShort(16_384)
            .putShort(16_384).putShort((-16_384).toShort())
            .flip() as ByteBuffer
        PcmAudioProcessor.process(pcm, PcmEncoding.PCM_16BIT, 2, resampler)
        resampler.finish()

        assertEquals(listOf(0.5f, 0f), output)
    }

    @Test
    fun `decoder buffer offset and limit are honored for each supported PCM encoding`() {
        val cases = listOf(
            PcmEncoding.PCM_8BIT to byteArrayOf(192.toByte()),
            PcmEncoding.PCM_16BIT to byteArrayOf(0x00, 0x40),
            PcmEncoding.PCM_24BIT_PACKED to byteArrayOf(0x00, 0x00, 0x40),
            PcmEncoding.PCM_32BIT to byteArrayOf(0x00, 0x00, 0x00, 0x40),
            PcmEncoding.PCM_FLOAT to byteArrayOf(0x00, 0x00, 0x00, 0x3f)
        )
        for ((encoding, sample) in cases) {
            val guarded = byteArrayOf(0x55, 0x66) + sample + byteArrayOf(0x77, 0x7f)
            val bytes = ByteBuffer.wrap(guarded).order(ByteOrder.LITTLE_ENDIAN)
                .apply { position(2); limit(2 + sample.size) }
            val output = mutableListOf<Float>()
            val resampler = StreamingLinearResampler(16_000, 16_000, output::add)
            assertEquals(1, PcmAudioProcessor.process(bytes, encoding, 1, resampler))
            resampler.finish()
            assertEquals("encoding $encoding", 0.5f, output.single(), 0.00001f)
        }
    }

    @Test
    fun `unknown PCM encoding is rejected rather than decoded as signed shorts`() {
        val output = mutableListOf<Float>()
        val resampler = StreamingLinearResampler(16_000, 16_000, output::add)
        try {
            PcmAudioProcessor.process(ByteBuffer.wrap(byteArrayOf(1, 2)), 999, 1, resampler)
            throw AssertionError("expected UnsupportedPcmEncodingException")
        } catch (_: UnsupportedPcmEncodingException) {
            assertTrue(output.isEmpty())
        }
    }
}
