package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.DenoiseStrength
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedPoint
import com.apexstudio.app.domain.model.VolumeKeyframe
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
            trimStartMs = 20L, trimEndMs = 2_000L, startTimeMs = 1_300L
        )
        val sources = buildAudioExportSources(
            clips = listOf(clip("first", 4f, 1f), clip("second", 0.25f, 1f)),
            audioTracks = listOf(music)
        )

        val musicSource = sources.last()
        assertEquals(music.uri, musicSource.uri)
        assertEquals(1f, musicSource.constantSpeedOverride)
        assertEquals(1f, musicSource.sourceGain)
        assertEquals(0.6f, musicSource.trackVolume!!)
        assertEquals(music.trimStartMs, musicSource.trimStartMs)
        assertEquals(music.trimEndMs, musicSource.trimEndMs)
        assertEquals(1_300L, musicSource.timelinePositionMs)
    }

    @Test
    fun `procedural SFX uses the ordinary file-backed export source with placement and fades`() {
        val effect = AudioTrack(
            id = "effect",
            name = "Cinematic Hit",
            uri = "/cache/sfx/cinematic_hit_1200.wav",
            trimStartMs = 100L,
            trimEndMs = 1_200L,
            sourceDurationMs = 1_200L,
            startTimeMs = 3_400L,
            fadeInMs = 80L,
            fadeOutMs = 250L,
            kind = AudioTrack.Kind.SFX,
            sfxEffectId = "cinematic_hit",
            sfxIntensity = 0.8f
        )

        val source = buildAudioExportSources(emptyList(), listOf(effect)).single()

        assertEquals(effect.uri, source.uri)
        assertEquals(effect.trimStartMs, source.trimStartMs)
        assertEquals(effect.trimEndMs, source.trimEndMs)
        assertEquals(effect.startTimeMs, source.timelinePositionMs)
        assertEquals(effect.fadeInMs, source.trackFadeInMs)
        assertEquals(effect.fadeOutMs, source.trackFadeOutMs)
        assertEquals(1f, source.sourceGain)
        assertEquals(effect.volume, source.trackVolume!!)
    }

    @Test
    fun `music and voiceover fade and absolute keyframe configuration survive mapping`() {
        val keyframes = listOf(
            VolumeKeyframe("start", 900L, 0.2f),
            VolumeKeyframe("later", 1_900L, 0.8f)
        )
        val voice = AudioTrack(
            id = "voice", name = "voice", uri = "file:///voice.m4a", volume = 0.7f,
            trimStartMs = -250L, trimEndMs = 2_500L, startTimeMs = -50L,
            fadeInMs = 120L, fadeOutMs = 300L, volumeKeyframes = keyframes,
            kind = AudioTrack.Kind.SFX
        )

        val source = buildAudioExportSources(emptyList(), listOf(voice)).single()

        assertEquals(-250L, source.trimStartMs)
        assertEquals(2_500L, source.trimEndMs)
        assertEquals(0L, source.timelinePositionMs) // negative placement starts at the export boundary
        assertEquals(1f, source.sourceGain)
        assertEquals(0.7f, source.trackVolume!!)
        assertEquals(120L, source.trackFadeInMs)
        assertEquals(300L, source.trackFadeOutMs)
        assertEquals(keyframes, source.trackVolumeKeyframes)
    }

    @Test
    fun `main clip sources map to sequential editor timeline positions`() {
        val sources = buildAudioExportSources(
            clips = listOf(clip("first", 2f, 1f), clip("second", 0.5f, 1f)),
            audioTracks = emptyList()
        )

        // The first source's 2x speed halves its 4.8s trimmed span on V1.
        assertEquals(listOf(0L, 2_400L), sources.map { it.timelinePositionMs })
    }

    @Test
    fun `still images preserve silence before subsequent main video`() {
        val sources = buildAudioExportSources(
            listOf(clip("still", 1f, 1f, type = ClipType.IMAGE), clip("video", 1f, 1f)),
            emptyList()
        )
        assertEquals(1, sources.size)
        assertEquals(4_800L, sources.single().timelinePositionMs)
    }

    @Test
    fun `absolute video layers do not advance the main timeline cursor`() {
        val layer = clip("layer", 1f, 1f).copy(trackIndex = 2, timelineOffsetMs = 1_200L)
        val sources = buildAudioExportSources(
            listOf(clip("first", 1f, 1f), layer, clip("last", 1f, 1f)),
            emptyList()
        )
        assertEquals(listOf(0L, 1_200L, 4_800L), sources.map { it.timelinePositionMs })
    }

    @Test
    fun `overlay clip audio retains trim absolute placement gain and speed curve`() {
        val curve = listOf(SpeedPoint(0f, 0.5f), SpeedPoint(1f, 1.5f))
        val overlay = clip("overlay", 1.25f, 0.35f, curve = curve, type = ClipType.OVERLAY)
            .copy(timelineOffsetMs = 2_400L, overlayVolume = 0.7f)

        val source = buildAudioExportSources(listOf(overlay), emptyList()).single()

        assertEquals(overlay.uri, source.uri)
        assertEquals(overlay.trimStartMs, source.trimStartMs)
        assertEquals(overlay.trimEndMs, source.trimEndMs)
        assertEquals(2_400L, source.timelinePositionMs)
        assertEquals(0.7f, source.sourceGain)
        assertEquals(curve, source.speedPoints)
        assertEquals(1f, source.constantSpeedOverride)
    }

    @Test
    fun `overlay clip audio uses absolute project time without moving V1 cursor`() {
        val overlay = clip("overlay", 1f, 1f, type = ClipType.OVERLAY)
            .copy(timelineOffsetMs = 900L, overlayVolume = 1f)
        val sources = buildAudioExportSources(
            listOf(clip("first", 1f, 1f), overlay, clip("last", 1f, 1f)),
            emptyList()
        )

        assertEquals(listOf(0L, 900L, 4_800L), sources.map { it.timelinePositionMs })
    }

    @Test
    fun `muted legacy image and hidden overlays do not enter standalone audio`() {
        val base = clip("overlay", 2f, 1f, type = ClipType.OVERLAY)
        val visible = base.copy(id = "visible", uri = "file:///visible", overlayVolume = 0.5f, trackIndex = 1)
        val hidden = visible.copy(id = "hidden", uri = "file:///hidden", trackIndex = 2)
        val invalid = visible.copy(id = "invalid", overlayVolume = Float.NaN)
        val sources = buildAudioExportSources(listOf(base, visible, hidden, invalid), emptyList(), setOf(2))
        assertEquals(listOf(visible.uri), sources.map { it.uri })
        assertEquals(0.5f, sources.single().sourceGain)
        assertEquals(1f, sources.single().constantSpeedOverride)
    }

    @Test
    fun `negative persisted track placement clamps to timeline zero`() {
        val track = AudioTrack("music", "music", "file:///music.m4a", startTimeMs = -200L)
        assertEquals(0L, buildAudioExportSources(emptyList(), listOf(track)).single().timelinePositionMs)
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
    fun `added track solo routing matches preview including a muted solo`() {
        val ordinary = AudioTrack("music", "music", "file:///music.m4a")
        val solo = AudioTrack("voice", "voice", "file:///voice.m4a", isSolo = true)
        assertEquals(listOf(solo.uri), buildAudioExportSources(emptyList(), listOf(ordinary, solo)).map { it.uri })
        assertTrue(buildAudioExportSources(emptyList(), listOf(ordinary, solo.copy(isMuted = true))).isEmpty())
        assertEquals(listOf(ordinary.uri), buildAudioExportSources(emptyList(), listOf(ordinary)).map { it.uri })
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
