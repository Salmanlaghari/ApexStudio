package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedCurve
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalVideoAudioExtractionTest {
    private fun clip() = MediaClip(
        id = "v1-clip",
        name = "source.mp4",
        uri = "content://video/source",
        durationMs = 12_000L,
        trimStartMs = 1_000L,
        trimEndMs = 9_000L,
        volume = 0.65f,
        trackIndex = 0,
        type = ClipType.VIDEO,
    )

    private fun plan(
        clip: MediaClip = clip(),
        hasAudio: Boolean = true,
        tracks: List<AudioTrack> = emptyList(),
    ) = planOriginalVideoAudioExtraction(
        clip = clip,
        sourceUri = "content://video/source",
        timelineStartMs = 4_500L,
        existingTracks = tracks,
        sourceHasAudio = hasAudio,
        trackId = "detached-audio",
    )

    @Test
    fun `ready plan creates trimmed placed original video track and mutes source`() {
        val result = plan() as OriginalVideoAudioExtractionPlan.Ready
        assertEquals(AudioTrack.Kind.ORIGINAL_VIDEO, result.track.kind)
        assertEquals("v1-clip", result.track.sourceVideoClipId)
        assertEquals("content://video/source", result.track.uri)
        assertEquals(1_000L, result.track.trimStartMs)
        assertEquals(9_000L, result.track.trimEndMs)
        assertEquals(4_500L, result.track.startTimeMs)
        assertEquals(0.65f, result.track.volume, 0f)
        assertEquals("source.mp4 · audio", result.track.name)
        assertEquals(0f, result.mutedSourceClip.volume, 0f)
        assertEquals(clip(), result.mutedSourceClip.copy(volume = 0.65f))
    }

    @Test
    fun `silent source overlay and speed remapped video are blocked`() {
        assertEquals(
            OriginalVideoAudioBlockReason.NO_READABLE_AUDIO,
            (plan(hasAudio = false) as OriginalVideoAudioExtractionPlan.Blocked).reason,
        )
        assertEquals(
            OriginalVideoAudioBlockReason.NOT_MAIN_VIDEO,
            (plan(clip().copy(trackIndex = 1)) as OriginalVideoAudioExtractionPlan.Blocked).reason,
        )
        assertEquals(
            OriginalVideoAudioBlockReason.TIME_REMAP,
            (plan(clip().copy(speedMultiplier = 0.5f)) as OriginalVideoAudioExtractionPlan.Blocked).reason,
        )
        assertEquals(
            OriginalVideoAudioBlockReason.TIME_REMAP,
            (plan(clip().copy(speedCurve = SpeedCurve.EASE_IN)) as OriginalVideoAudioExtractionPlan.Blocked).reason,
        )
    }

    @Test
    fun `same source cannot be detached twice and track link stays backwards compatible`() {
        val firstTrack = (plan() as OriginalVideoAudioExtractionPlan.Ready).track
        assertEquals(
            OriginalVideoAudioBlockReason.ALREADY_SEPARATED,
            (plan(tracks = listOf(firstTrack)) as OriginalVideoAudioExtractionPlan.Blocked).reason,
        )
        val legacyJson = """{"id":"legacy","name":"Music","uri":"file:///music.m4a"}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<AudioTrack>(legacyJson)
        assertEquals(null, decoded.sourceVideoClipId)
        val encoded = Json.encodeToString(firstTrack)
        assertTrue(encoded.contains("v1-clip"))
    }
}
