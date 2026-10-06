package com.apexstudio.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the pure helpers in [MusicExportMixer].
 * (The MediaCodec pipeline itself is exercised on-device/CI via the build;
 * Robolectric cannot run real codecs.)
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
    fun `degenerate windows are silent`() {
        assertEquals(0f, env(0, 0, 1_000, 1_000))
        assertEquals(0f, env(0, -5, 0, 0))
    }
}
