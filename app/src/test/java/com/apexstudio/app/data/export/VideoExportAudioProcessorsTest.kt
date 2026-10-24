package com.apexstudio.app.data.export

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.apexstudio.app.data.audio.DenoiseAudioProcessor
import com.apexstudio.app.data.audio.SharedVideoClipAudioProcessor
import com.apexstudio.app.domain.model.timeline.Curve
import com.apexstudio.app.domain.model.timeline.Keyframe
import com.apexstudio.app.domain.model.timeline.VideoClip
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Exercises the production video-export chain, not only its standalone gain helper. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(UnstableApi::class)
class VideoExportAudioProcessorsTest {
    private fun chain(volume: Float) = ExportEngine(RuntimeEnvironment.getApplication())
        .buildVideoExportAudioProcessors(ExportEngine.ExportConfig(volume = volume))

    @Test
    fun `mono phone audio obeys video export attenuation`() {
        assertEquals(listOf(1_000, -2_000), process(1, 0.5f, shortArrayOf(2_000, -4_000)))
    }

    @Test
    fun `mono phone audio obeys video export mute`() {
        assertEquals(listOf(0, 0), process(1, 0f, shortArrayOf(2_000, -4_000)))
    }

    @Test
    fun `stereo channels keep independent samples during attenuation and mute`() {
        assertEquals(listOf(1_000, -2_000, 3_000, -4_000),
            process(2, 0.5f, shortArrayOf(2_000, -4_000, 6_000, -8_000)))
        assertEquals(listOf(0, 0), process(2, 0f, shortArrayOf(2_000, -4_000)))
    }

    @Test
    fun `unity and nonfinite legacy volume bypass gain safely`() {
        for (volume in listOf(1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertTrue(chain(volume).isEmpty())
        }
    }

    @Test
    fun `finite gain is bounded and clips only at PCM output range`() {
        assertEquals(listOf(0, 0), process(1, -2f, shortArrayOf(20_000, -20_000)))
        assertEquals(listOf(32_767, -32_768), process(1, 9f, shortArrayOf(20_000, -20_000)))
    }

    @Test
    fun `typed animation uses one per-frame processor and applies global gain once`() {
        val clip = VideoClip(
            uri = Uri.parse("file:///typed.mp4"),
            inMs = 0L,
            outMs = 1_000L,
            volume = 0.5f,
            volumeKeyframes = listOf(
                Keyframe(0L, 0.5f, Curve.LINEAR),
                Keyframe(1_000L, 1f, Curve.LINEAR)
            )
        )
        val processors = ExportEngine(RuntimeEnvironment.getApplication()).buildVideoExportAudioProcessors(
            ExportEngine.ExportConfig(
                volume = 0.25f,
                globalAudioVolume = 0.5f,
                sharedVideoClip = clip
            )
        )
        assertEquals(1, processors.size)
        val processor = processors.single()
        assertTrue(processor is SharedVideoClipAudioProcessor)
        try {
            processor.configure(AudioProcessor.AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT))
            val input = ByteBuffer.allocateDirect(501 * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(501) { input.putShort(10_000) }
            input.flip()
            processor.queueInput(input)
            val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
            var lastSample = 0
            while (output.hasRemaining()) lastSample = output.short.toInt()
            // 500ms: clip volume .75 * global gain .5 = .375.
            assertEquals(3_750, lastSample)
        } finally {
            processor.reset()
        }
    }

    @Test
    fun `typed clip with no audio keys stays on the legacy scalar processor`() {
        val clip = VideoClip(
            uri = Uri.parse("file:///typed.mp4"),
            inMs = 0L,
            outMs = 1_000L,
            volume = 0.5f
        )
        val processors = ExportEngine(RuntimeEnvironment.getApplication()).buildVideoExportAudioProcessors(
            ExportEngine.ExportConfig(volume = 0.5f, sharedVideoClip = clip)
        )
        assertEquals(1, processors.size)
        assertFalse(processors.single() is SharedVideoClipAudioProcessor)
        processors.forEach { it.reset() }
    }

    @Test
    fun `denoise pitch gain and paired speed retain production order without duplicate speed`() {
        val engine = ExportEngine(RuntimeEnvironment.getApplication())
        val pairedSpeed = SonicAudioProcessor()
        val config = ExportEngine.ExportConfig(denoiseEnabled = true, pitchSemitones = 2f, volume = 0.5f)
        val first = engine.buildVideoExportAudioProcessors(config, pairedSpeed)
        val second = engine.buildVideoExportAudioProcessors(config)
        assertEquals(4, first.size)
        assertTrue(first[0] is DenoiseAudioProcessor)
        assertTrue(first[1] is SonicAudioProcessor)
        assertSame(pairedSpeed, first.last())
        assertEquals(1, first.count { it === pairedSpeed })
        assertEquals(3, second.size)
        for (index in second.indices) assertNotSame(first[index], second[index])
        first.forEach { it.reset() }
        second.forEach { it.reset() }
    }

    private fun process(channels: Int, volume: Float, samples: ShortArray): List<Int> {
        val processor = chain(volume).single()
        try {
            processor.configure(AudioProcessor.AudioFormat(48_000, channels, C.ENCODING_PCM_16BIT))
            processor.flush()
            val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach(input::putShort)
            input.flip()
            processor.queueInput(input)
            assertFalse(input.hasRemaining())
            val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
            return buildList { while (output.hasRemaining()) add(output.short.toInt()) }
        } finally {
            processor.reset()
        }
    }
}
