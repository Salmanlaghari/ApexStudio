package com.apexstudio.app.data.audio.sfx

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SfxWavCacheTest {
    @Test
    fun `cache uses recipe keyed WAV and regenerates identical bytes after eviction`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cached = SfxWavCache.getOrCreate(context, "camera_shutter", 350L, 0.75f)
        val originalBytes = cached.readBytes()
        assertTrue(cached.absolutePath.startsWith(File(context.cacheDir, "sfx").absolutePath))
        assertEquals("RIFF", String(originalBytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(originalBytes, 8, 4, Charsets.US_ASCII))
        assertEquals(SfxSynth.WAV_HEADER_BYTES + SfxSynth.render("camera_shutter", 350L, 0.75f).size * 2, originalBytes.size)
        RandomAccessFile(cached, "rw").use {
            it.seek(20L)
            it.write(0) // Corrupt the PCM format field without changing length or RIFF/WAVE magic.
        }
        val repaired = SfxWavCache.getOrCreate(context, "camera_shutter", 350L, 0.75f)
        assertEquals(cached.absolutePath, repaired.absolutePath)
        assertArrayEquals(originalBytes, repaired.readBytes())
        assertEquals(cached, SfxWavCache.getOrCreate(context, "camera_shutter", 350L, 0.75f))
        assertNotEquals(cached, SfxWavCache.getOrCreate(context, "camera_shutter", 350L, 0.5f))

        assertTrue(File(context.cacheDir, "sfx").deleteRecursively())
        val restored = SfxWavCache.getOrCreate(context, "camera_shutter", 350L, 0.75f)
        assertEquals(cached.absolutePath, restored.absolutePath)
        assertEquals(originalBytes.contentHashCode(), restored.readBytes().contentHashCode())
    }
}
