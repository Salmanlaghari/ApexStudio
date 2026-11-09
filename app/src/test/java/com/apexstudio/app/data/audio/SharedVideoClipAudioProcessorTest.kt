package com.apexstudio.app.data.audio

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.apexstudio.app.domain.model.timeline.Curve
import com.apexstudio.app.domain.model.timeline.Keyframe
import com.apexstudio.app.domain.model.timeline.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedVideoClipAudioProcessorTest {
    @Test
    fun volumeInterpolatesBetweenTypedKeyTimesOnOutputClock() {
        val clip = clip(
            volume = 0.5f,
            volumeKeyframes = listOf(
                Keyframe(0L, 0.5f, Curve.LINEAR),
                Keyframe(1_000L, 1f, Curve.LINEAR)
            )
        )
        // At project time 500ms the typed volume is 0.75; it replaces the
        // baseline instead of being multiplied by it again.
        assertEquals(listOf(7_500), process(clip, globalGain = 1f, channels = 1, frameRate = 1_000, samples = shortArrayOf(10_000)))
    }

    @Test
    fun keyframedVolumeCanRiseFromZeroBaseline() {
        val clip = clip(
            volume = 0f,
            volumeKeyframes = listOf(
                Keyframe(0L, 0f, Curve.LINEAR),
                Keyframe(1_000L, 1f, Curve.LINEAR)
            )
        )
        assertEquals(listOf(5_000), process(clip, globalGain = 1f, channels = 1, frameRate = 1_000, samples = shortArrayOf(10_000)))
    }

    @Test
    fun typedVolumeUsesGlobalGainExactlyOnce() {
        val clip = clip(
            volume = 0.5f,
            volumeKeyframes = listOf(
                Keyframe(0L, 0.5f, Curve.LINEAR),
                Keyframe(1_000L, 1f, Curve.LINEAR)
            )
        )
        assertEquals(listOf(3_750), process(clip, globalGain = 0.5f, channels = 1, frameRate = 1_000, samples = shortArrayOf(10_000)))
    }

    @Test
    fun stereoPanUsesLinearLeftAndRightGains() {
        val clip = clip(
            panKeyframes = listOf(
                Keyframe(0L, 0f, Curve.LINEAR),
                Keyframe(1_000L, 1f, Curve.LINEAR)
            )
        )
        assertEquals(listOf(5_000, 10_000), process(clip, globalGain = 1f, channels = 2, frameRate = 1_000, samples = shortArrayOf(10_000, 10_000)))
    }

    @Test
    fun multichannelAudioPreservesAdditionalChannelsWithVolumeGain() {
        val clip = clip(
            volume = 0.5f,
            panKeyframes = listOf(Keyframe(500L, 1f, Curve.LINEAR))
        )
        assertEquals(
            listOf(0, 5_000, 5_000, 5_000, 5_000, 5_000),
            process(
                clip,
                globalGain = 1f,
                channels = 6,
                frameRate = 1_000,
                samples = shortArrayOf(10_000, 10_000, 10_000, 10_000, 10_000, 10_000)
            )
        )
    }

    @Test
    fun panAnimationRetainsBaseClipVolume() {
        val clip = clip(
            volume = 0.5f,
            panKeyframes = listOf(
                Keyframe(0L, 0f, Curve.LINEAR),
                Keyframe(1_000L, 1f, Curve.LINEAR)
            )
        )
        assertEquals(listOf(2_500, 5_000), process(clip, globalGain = 1f, channels = 2, frameRate = 1_000, samples = shortArrayOf(10_000, 10_000)))
    }

    @Test
    fun monoIgnoresPanWithoutReadingASecondChannel() {
        for (pan in listOf(-1f, 1f)) {
            val clip = clip(panKeyframes = listOf(Keyframe(500L, pan, Curve.LINEAR)))
            assertEquals(listOf(10_000), process(clip, globalGain = 1f, channels = 1, frameRate = 1_000, samples = shortArrayOf(10_000)))
        }
    }

    private fun clip(
        volume: Float = 1f,
        volumeKeyframes: List<Keyframe<Float>> = emptyList(),
        panKeyframes: List<Keyframe<Float>> = emptyList()
    ) = VideoClip(
        uri = Uri.parse("file:///typed.mp4"),
        inMs = 0L,
        outMs = 2_000L,
        volume = volume,
        volumeKeyframes = volumeKeyframes,
        panKeyframes = panKeyframes
    )

    private fun process(
        clip: VideoClip,
        globalGain: Float,
        channels: Int,
        frameRate: Int,
        samples: ShortArray
    ): List<Int> {
        val processor = SharedVideoClipAudioProcessor(clip, globalGain, timelineStartMs = 500L)
        processor.configure(AudioProcessor.AudioFormat(frameRate, channels, C.ENCODING_PCM_16BIT))
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach(input::putShort)
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        return buildList { while (output.hasRemaining()) add(output.short.toInt()) }
    }
}
