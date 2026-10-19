package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.DenoiseStrength
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorAudioExportSourcesTest {
    private fun clip(
        id: String,
        speed: Float,
        gain: Float,
        denoise: Boolean = false,
        curve: List<SpeedPoint> = emptyList(),
        type: ClipType = ClipType.VIDEO
    ) = MediaClip(
        id = id,
        name = id,
        uri = "file:///$id",
        durationMs = 5_000L,
        trimStartMs = 100L,
        trimEndMs = 4_900L,
        speedMultiplier = speed,
        volume = gain,
        denoiseEnabled = denoise,
        denoiseStrength = DenoiseStrength.STRONG,
        speedPoints = curve,
        type = type
    )

    @Test
    fun `video clips retain heterogeneous constant speed and gain`() {
        val sources = buildAudioExportSources(
            clips = listOf(clip("slow", 0.5f, 0.25f), clip("fast", 2f, 1.75f)),
            audioTracks = emptyList()
        )

        assertEquals(2, sources.size)
        assertEquals("file:///slow", sources[0].uri)
        assertEquals(0.5f, sources[0].constantSpeedOverride)
        assertEquals(0.25f, sources[0].sourceGain)
        assertEquals("file:///fast", sources[1].uri)
        assertEquals(2f, sources[1].constantSpeedOverride)
        assertEquals(1.75f, sources[1].sourceGain)
    }

    @Test
    fun `music remains one times even after differently sped clips`() {
        val music = AudioTrack(
            id = "music", name = "music", uri = "file:///music.m4a", volume = 0.6f,
            trimStartMs = 20L, trimEndMs = 2_000L
        )
        val sources = buildAudioExportSources(
            clips = listOf(clip("first", 4f, 1f), clip("second", 0.25f, 1f)),
            audioTracks = listOf(music)
        )

        val musicSource = sources.last()
        assertEquals(music.uri, musicSource.uri)
        assertEquals(1f, musicSource.constantSpeedOverride)
        assertEquals(0.6f, musicSource.sourceGain)
        assertEquals(music.trimStartMs, musicSource.trimStartMs)
        assertEquals(music.trimEndMs, musicSource.trimEndMs)
    }

    @Test
    fun `muted tracks and non-video clips are excluded`() {
        val muted = AudioTrack("muted", "muted", "file:///muted.m4a", isMuted = true)
        val audible = AudioTrack("audible", "audible", "file:///audible.m4a")
        val sources = buildAudioExportSources(
            clips = listOf(clip("video", 1f, 1f), clip("still", 1f, 1f, type = ClipType.IMAGE)),
            audioTracks = listOf(muted, audible)
        )

        assertEquals(listOf("file:///video", "file:///audible.m4a"), sources.map { it.uri })
        assertFalse(sources.any { it.uri == muted.uri })
        assertTrue(sources.all { it.uri != "file:///still" })
    }

    @Test
    fun `per-source clean voice and speed curve survive source construction`() {
        val curve = listOf(SpeedPoint(0f, 0.5f), SpeedPoint(1f, 1.5f))
        val source = buildAudioExportSources(
            clips = listOf(clip("voice", 1.25f, 0.8f, denoise = true, curve = curve)),
            audioTracks = emptyList()
        ).single()

        assertEquals(curve, source.speedPoints)
        assertEquals(1.25f, source.constantSpeedOverride)
        assertTrue(source.denoiseEnabled)
        assertEquals(DenoiseStrength.STRONG, source.denoiseStrength)
    }
}
