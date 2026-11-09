package com.apexstudio.app.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.audio.sfx.SfxWavCache
import com.apexstudio.app.domain.model.AudioTrack
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SfxProjectCacheRecoveryTest {
    @Test
    fun `load project recovery recreates its cached WAV from the saved effect recipe`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val effectId = "sparkle"
        val durationMs = 800L
        val intensity = 0.65f
        val cached = SfxWavCache.getOrCreate(context, effectId, durationMs, intensity)
        val originalBytes = cached.readBytes()
        val track = AudioTrack(
            id = "saved-sfx",
            name = "Sparkle",
            uri = cached.absolutePath,
            kind = AudioTrack.Kind.SFX,
            sourceDurationMs = durationMs,
            trimEndMs = durationMs,
            sfxEffectId = effectId,
            sfxIntensity = intensity
        )

        assertTrue(File(context.cacheDir, "sfx").deleteRecursively())
        val restored = recoverCachedSfxTrack(context, track)

        assertEquals(cached.absolutePath, restored.uri)
        assertEquals(effectId, restored.sfxEffectId)
        assertEquals(intensity, restored.sfxIntensity, 0.001f)
        assertEquals(durationMs, restored.sourceDurationMs)
        assertTrue(File(restored.uri).isFile)
        assertArrayEquals(originalBytes, File(restored.uri).readBytes())
    }
}
