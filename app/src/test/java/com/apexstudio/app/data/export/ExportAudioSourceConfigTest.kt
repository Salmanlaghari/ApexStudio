package com.apexstudio.app.data.export

import com.apexstudio.app.domain.model.SpeedPoint
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regression coverage for configuring each standalone audio item's processors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(UnstableApi::class)
class ExportAudioSourceConfigTest {
    @Test
    fun `variable speed curve receives its own trimmed source span`() {
        val curve = listOf(SpeedPoint(0f, 0.5f), SpeedPoint(1f, 2f))
        val source = ExportEngine.AudioExportSource(
            uri = "file:///audio.m4a",
            trimStartMs = 1_250L,
            trimEndMs = 5_250L,
            speedPoints = curve
        )
        val base = ExportEngine.ExportConfig(clipSpeed = 1f)

        val itemConfig = ExportEngine.audioConfigForSource(base, source)

        assertEquals(1_250L, itemConfig.trimStartMs)
        assertEquals(5_250L, itemConfig.trimEndMs)
        assertEquals(4_000L, itemConfig.trimEndMs - itemConfig.trimStartMs)
        assertEquals(curve, itemConfig.speedPoints)
        assertEquals(1f, itemConfig.clipSpeed)
    }

    @Test
    fun `per-source clean voice remains configured with the same audio item`() {
        val source = ExportEngine.AudioExportSource(
            uri = "file:///voice.m4a",
            trimStartMs = 300L,
            trimEndMs = 1_300L,
            denoiseEnabled = true
        )

        val itemConfig = ExportEngine.audioConfigForSource(
            ExportEngine.ExportConfig(),
            source
        )

        assertEquals(300L, itemConfig.trimStartMs)
        assertEquals(1_300L, itemConfig.trimEndMs)
        assertEquals(true, itemConfig.denoiseEnabled)
    }

    @Test
    fun `source constant speed and gain compose with global volume`() {
        val itemConfig = ExportEngine.audioConfigForSource(
            ExportEngine.ExportConfig(clipSpeed = 7f, volume = 0.5f),
            ExportEngine.AudioExportSource(
                uri = "file:///clip.mp4",
                constantSpeedOverride = 2f,
                sourceGain = 0.4f
            )
        )

        assertEquals(2f, itemConfig.clipSpeed)
        assertEquals(0.2f, itemConfig.volume, 0.0001f)
    }

    @Test
    fun `legacy source without override retains valid config speed`() {
        val itemConfig = ExportEngine.audioConfigForSource(
            ExportEngine.ExportConfig(clipSpeed = 1.5f),
            ExportEngine.AudioExportSource(uri = "file:///legacy.m4a")
        )

        assertEquals(1.5f, itemConfig.clipSpeed)
    }

    @Test
    fun `invalid source and global numbers fall back safely and global mute wins`() {
        val source = ExportEngine.AudioExportSource(
            uri = "file:///invalid.m4a",
            constantSpeedOverride = Float.NaN,
            sourceGain = Float.POSITIVE_INFINITY
        )
        val itemConfig = ExportEngine.audioConfigForSource(
            ExportEngine.ExportConfig(clipSpeed = Float.NEGATIVE_INFINITY, volume = Float.NaN),
            source
        )

        assertEquals(1f, itemConfig.clipSpeed)
        assertEquals(1f, itemConfig.volume)
        val muted = ExportEngine.audioConfigForSource(
            ExportEngine.ExportConfig(volume = 0f),
            source.copy(sourceGain = 1.8f)
        )
        assertEquals(0f, muted.volume)
    }

    @Test
    fun `source gain is bounded to finite supported range`() {
        val loud = ExportEngine.audioConfigForSource(
            ExportEngine.ExportConfig(volume = 2f),
            ExportEngine.AudioExportSource(uri = "file:///loud.m4a", sourceGain = 8f)
        )
        val negative = ExportEngine.audioConfigForSource(
            ExportEngine.ExportConfig(volume = 1f),
            ExportEngine.AudioExportSource(uri = "file:///quiet.m4a", sourceGain = -3f)
        )

        assertEquals(2f, loud.volume)
        assertEquals(0f, negative.volume)
    }

    @Test
    fun `mono and stereo PCM gain processor applies real sample gain`() {
        assertPcmGain(channels = 1, input = shortArrayOf(2_000, -4_000), gain = 0.5f,
            expected = shortArrayOf(1_000, -2_000))
        assertPcmGain(channels = 2, input = shortArrayOf(2_000, -4_000, 6_000, -8_000), gain = 0.5f,
            expected = shortArrayOf(1_000, -2_000, 3_000, -4_000))
    }

    @Test
    fun `mono and stereo PCM gain processor applies zero global mute`() {
        assertPcmGain(channels = 1, input = shortArrayOf(2_000, -4_000), gain = 0f,
            expected = shortArrayOf(0, 0))
        assertPcmGain(channels = 2, input = shortArrayOf(2_000, -4_000), gain = 0f,
            expected = shortArrayOf(0, 0))
    }

    private fun assertPcmGain(channels: Int, input: ShortArray, gain: Float, expected: ShortArray) {
        val processor = ExportEngine.volumeProcessorFor(gain)
        if (gain == 1f) {
            assertNull(processor)
            return
        }
        requireNotNull(processor)
        processor.configure(AudioProcessor.AudioFormat(48_000, channels, C.ENCODING_PCM_16BIT))
        processor.flush()
        val inputBuffer = ByteBuffer.allocateDirect(input.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        input.forEach(inputBuffer::putShort)
        inputBuffer.flip()
        processor.queueInput(inputBuffer)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        val actual = ShortArray(output.remaining() / 2) { output.short }
        assertEquals(expected.toList(), actual.toList())
        processor.reset()
    }
}
