package com.apexstudio.app.data.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.apexstudio.app.domain.model.VolumeKeyframe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class AudioTrackEnvelopeAudioProcessorTest {
    @Test
    fun `PCM16 fade boundaries match mixer half-open ramps`() {
        val processor = processor(fadeInMs = 4L, fadeOutMs = 4L, durationMs = 8L)
        processor.configure(AudioProcessor.AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.queueInput(pcm16Buffer(ShortArray(8) { 10_000 }))

        assertEquals(listOf(0, 2_500, 5_000, 7_500, 10_000, 7_500, 5_000, 2_500), readPcm16(processor.output))
    }

    @Test
    fun `absolute keyframes use placed timeline time and replace scalar fallback`() {
        val processor = processor(
            scalar = 0.9f,
            keyframes = listOf(VolumeKeyframe("a", 1_000L, 0.25f), VolumeKeyframe("b", 1_004L, 0.75f)),
            timelineMs = 1_000L
        )
        processor.configure(AudioProcessor.AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.queueInput(pcm16Buffer(ShortArray(4) { 10_000 }))

        assertEquals(listOf(2_500, 3_750, 5_000, 6_250), readPcm16(processor.output))
    }

    @Test
    fun `extreme finite keyframe volumes are clamped before interpolation`() {
        val processor = processor(
            keyframes = listOf(
                VolumeKeyframe("high", 0L, Float.MAX_VALUE),
                VolumeKeyframe("low", 2L, -Float.MAX_VALUE)
            )
        )
        processor.configure(AudioProcessor.AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT))
        processor.queueInput(pcm16Buffer(ShortArray(3) { 10_000 }))

        assertEquals(listOf(10_000, 5_000, 0), readPcm16(processor.output))
    }

    @Test
    fun `duplicate keyframe timestamps remain finite and deterministic`() {
        val processor = processor(
            keyframes = listOf(
                VolumeKeyframe("start", 0L, 0f),
                VolumeKeyframe("first duplicate", 1L, 0.25f),
                VolumeKeyframe("second duplicate", 1L, 0.75f),
                VolumeKeyframe("end", 2L, 1f)
            )
        )
        processor.configure(AudioProcessor.AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT))
        processor.queueInput(pcm16Buffer(ShortArray(3) { 10_000 }))

        assertEquals(listOf(0, 2_500, 10_000), readPcm16(processor.output))
    }

    @Test
    fun `negative timeline placement is normalized before keyframe lookup`() {
        val processor = processor(
            keyframes = listOf(VolumeKeyframe("start", 0L, 0f), VolumeKeyframe("end", 2L, 1f)),
            timelineMs = -2L
        )
        processor.configure(AudioProcessor.AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT))
        processor.queueInput(pcm16Buffer(ShortArray(3) { 10_000 }))

        assertEquals(listOf(0, 5_000, 10_000), readPcm16(processor.output))
    }

    @Test
    fun `scalar track volume composes with global gain on actual PCM`() {
        val track = processor(scalar = 0.5f, globalGain = 0.5f)
        track.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT))
        track.flush()
        track.queueInput(pcm16Buffer(shortArrayOf(20_000)))

        assertEquals(listOf(5_000), readPcm16(track.output))
    }

    @Test
    fun `float stereo path and chunk boundaries preserve exact sample sequence`() {
        val floatSamples = floatArrayOf(0.8f, -0.8f, 0.6f, -0.6f, 0.4f, -0.4f, 0.2f, -0.2f)
        val keyframes = listOf(VolumeKeyframe("start", 101L, 0.2f), VolumeKeyframe("end", 104L, 0.8f))
        val whole = processor(scalar = 0.3f, keyframes = keyframes, timelineMs = 100L)
        whole.configure(AudioProcessor.AudioFormat(1_000, 2, C.ENCODING_PCM_FLOAT))
        whole.flush()
        whole.queueInput(floatBuffer(floatSamples))
        val expected = readFloat(whole.output)

        val chunked = processor(scalar = 0.3f, keyframes = keyframes, timelineMs = 100L)
        chunked.configure(AudioProcessor.AudioFormat(1_000, 2, C.ENCODING_PCM_FLOAT))
        chunked.flush()
        val actual = mutableListOf<Float>()
        for (offset in listOf(0, 2, 6)) {
            val count = when (offset) { 0 -> 2; 2 -> 4; else -> 2 }
            chunked.queueInput(floatBuffer(floatSamples.copyOfRange(offset, offset + count)))
            actual += readFloat(chunked.output)
        }
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (a, b) -> assertEquals(a, b, 0.000001f) }
    }

    @Test
    fun `flush resets absolute sample clock and end state`() {
        val processor = processor(keyframes = listOf(VolumeKeyframe("pin", 5_000L, 0.25f)), timelineMs = 5_000L)
        processor.configure(AudioProcessor.AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.queueInput(pcm16Buffer(shortArrayOf(8_000)))
        assertEquals(listOf(2_000), readPcm16(processor.output))
        processor.queueEndOfStream()
        assertTrue(processor.isEnded())

        processor.flush()
        assertFalse(processor.isEnded())
        processor.queueInput(pcm16Buffer(shortArrayOf(8_000)))
        assertEquals(listOf(2_000), readPcm16(processor.output))
    }

    @Test
    fun `invalid finite values are sanitized and signed timeline arithmetic saturates`() {
        val processor = AudioTrackEnvelopeAudioProcessor(
            scalarVolume = Float.NaN,
            volumeKeyframes = listOf(
                VolumeKeyframe("bad", Long.MIN_VALUE, Float.NaN),
                VolumeKeyframe("valid", Long.MAX_VALUE, 0.5f)
            ),
            timelinePositionMs = Long.MAX_VALUE - 1L,
            fadeInMs = -1L,
            fadeOutMs = -2L,
            retainedDurationMs = Long.MAX_VALUE
        )
        processor.configure(AudioProcessor.AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.queueInput(pcm16Buffer(shortArrayOf(10_000, 10_000)))
        assertEquals(listOf(5_000, 5_000), readPcm16(processor.output))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported PCM encoding fails explicitly`() {
        processor().configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_24BIT))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `more than stereo channels are rejected`() {
        processor().configure(AudioProcessor.AudioFormat(48_000, 3, C.ENCODING_PCM_16BIT))
    }

    private fun processor(
        scalar: Float = 1f,
        keyframes: List<VolumeKeyframe> = emptyList(),
        timelineMs: Long = 0L,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        durationMs: Long = 0L,
        globalGain: Float = 1f
    ) = AudioTrackEnvelopeAudioProcessor(scalar, keyframes, timelineMs, fadeInMs, fadeOutMs, durationMs, globalGain)

    private fun pcm16Buffer(samples: ShortArray): ByteBuffer = ByteBuffer.allocateDirect(samples.size * 2)
        .order(ByteOrder.LITTLE_ENDIAN).apply { samples.forEach(::putShort); flip() }

    private fun floatBuffer(samples: FloatArray): ByteBuffer = ByteBuffer.allocateDirect(samples.size * 4)
        .order(ByteOrder.LITTLE_ENDIAN).apply { samples.forEach(::putFloat); flip() }

    private fun readPcm16(buffer: ByteBuffer): List<Int> = buffer.order(ByteOrder.LITTLE_ENDIAN).let { bytes ->
        buildList { while (bytes.remaining() >= 2) add(bytes.short.toInt()) }
    }

    private fun readFloat(buffer: ByteBuffer): List<Float> = buffer.order(ByteOrder.LITTLE_ENDIAN).let { bytes ->
        buildList { while (bytes.remaining() >= 4) add(bytes.float) }
    }
}
