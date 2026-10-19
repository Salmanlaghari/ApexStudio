package com.apexstudio.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the pure helpers in [MusicExportMixer].
 * The MediaCodec pipeline still needs on-device acceptance;
 * building an APK or running Robolectric does not exercise real codecs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicExportMixerTest {

    private fun env(
        posMs: Long,
        playMs: Long,
        fadeInMs: Long,
        fadeOutMs: Long
    ): Float {
        val posFrames = (posMs * 44100) / 1000
        val playFrames = (playMs * 44100) / 1000
        return MusicExportMixer.fadeEnvelope(posFrames, playFrames, fadeInMs, fadeOutMs)
    }

    @Test
    fun `no fades means full volume everywhere`() {
        assertEquals(1f, env(0, 10_000, 0, 0))
        assertEquals(1f, env(5_000, 10_000, 0, 0))
        assertEquals(1f, env(9_999, 10_000, 0, 0))
    }

    @Test
    fun `fade in ramps linearly from zero`() {
        assertEquals(0f, env(0, 10_000, 2_000, 0))
        assertEquals(0.5f, env(1_000, 10_000, 2_000, 0), 0.01f)
        assertEquals(1f, env(2_000, 10_000, 2_000, 0))
        assertEquals(1f, env(5_000, 10_000, 2_000, 0))
    }

    @Test
    fun `fade out ramps linearly to zero`() {
        assertEquals(1f, env(0, 10_000, 0, 2_000))
        assertEquals(1f, env(7_999, 10_000, 0, 2_000))
        assertEquals(0.5f, env(9_000, 10_000, 0, 2_000), 0.01f)
        assertEquals(0f, env(10_000, 10_000, 0, 2_000))
    }

    @Test
    fun `fades combine by taking the minimum`() {
        // 2s fade-in + 2s fade-out on a 3s window: middle is the bottleneck.
        assertEquals(0.5f, env(1_000, 3_000, 2_000, 2_000), 0.01f)
        assertEquals(0.25f, env(500, 3_000, 2_000, 2_000), 0.01f)
    }

    @Test
    fun `extreme persisted fade durations saturate instead of overflowing`() {
        val playFrames = 10_000L * 44_100L / 1_000L
        assertEquals(
            0f,
            MusicExportMixer.fadeEnvelope(0L, playFrames, Long.MAX_VALUE, 0L)
        )
        assertEquals(
            0f,
            MusicExportMixer.fadeEnvelope(0L, playFrames, 0L, Long.MAX_VALUE),
            0.001f
        )
    }

    @Test
    fun `degenerate windows are silent`() {
        assertEquals(0f, env(0, 0, 1_000, 1_000))
        assertEquals(0f, env(0, -5, 0, 0))
    }

    @Test
    fun `timeline milliseconds map to frames without multiplication overflow`() {
        assertEquals(0L, ExportAudioTimelineMapping.millisToFrames(-1L, 44_100))
        assertEquals(44_100L, ExportAudioTimelineMapping.millisToFrames(1_000L, 44_100))
        assertEquals(44L, ExportAudioTimelineMapping.millisToFrames(1L, 44_100))
        assertEquals(
            Long.MAX_VALUE,
            ExportAudioTimelineMapping.millisToFrames(Long.MAX_VALUE, 44_100)
        )
    }

    @Test
    fun `placed span end saturates instead of making long sources inactive`() {
        assertEquals(300L, ExportAudioTimelineMapping.endFrame(100L, 200L))
        assertEquals(Long.MAX_VALUE, ExportAudioTimelineMapping.endFrame(44_100L, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, ExportAudioTimelineMapping.endFrame(Long.MAX_VALUE - 10L, 11L))
        assertEquals(100L, ExportAudioTimelineMapping.endFrame(100L, -1L))
    }

    @Test
    fun `source trim mapping clamps negative start and rejects empty interval`() {
        assertEquals(
            ExportAudioTimelineMapping.SourceTrim(0L, 22_050L),
            ExportAudioTimelineMapping.sourceTrim(-500L, 500L, 44_100)
        )
        assertEquals(
            ExportAudioTimelineMapping.SourceTrim(2_000L, 0L),
            ExportAudioTimelineMapping.sourceTrim(2_000L, 1_000L, 44_100)
        )
        assertEquals(
            ExportAudioTimelineMapping.SourceTrim(0L, Long.MAX_VALUE),
            ExportAudioTimelineMapping.sourceTrim(0L, Long.MAX_VALUE, 44_100)
        )
    }
}
