package com.apexstudio.app.data.engine

import com.apexstudio.app.domain.model.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioPlaybackPositionTest {
    private fun track() = AudioTrack(
        id = "voice-1",
        name = "Voiceover",
        uri = "/tmp/voice.wav",
        trimStartMs = 1_200L,
        trimEndMs = 5_800L,
        startTimeMs = 9_000L
    )

    @Test
    fun `project seek maps into trimmed source interval`() {
        assertEquals(1_200L, AudioPlaybackPosition.sourcePositionMs(track(), 9_000L))
        assertEquals(1_650L, AudioPlaybackPosition.sourcePositionMs(track(), 9_450L))
    }

    @Test
    fun `project seek outside placed audio has no source position`() {
        assertNull(AudioPlaybackPosition.sourcePositionMs(track(), 8_999L))
        assertNull(AudioPlaybackPosition.sourcePositionMs(track(), 13_600L))
    }

    @Test
    fun `empty source trim is never seekable`() {
        val empty = track().copy(trimEndMs = 1_200L)
        assertNull(AudioPlaybackPosition.sourcePositionMs(empty, 9_000L))
    }

    @Test fun negativeTrimStartDoesNotExtendPlaybackPastSourceEnd() {
        val malformed = track().copy(trimStartMs = -500L, trimEndMs = 1_000L, startTimeMs = 0L)
        assertEquals(999L, AudioPlaybackPosition.sourcePositionMs(malformed, 999L))
        assertNull(AudioPlaybackPosition.sourcePositionMs(malformed, 1_000L))
        assertNull(AudioPlaybackPosition.sourcePositionMs(malformed, -1L))
    }

    @Test fun knownSourceDurationCapsOversizedTrimEnd() {
        val bounded = track().copy(trimEndMs = Long.MAX_VALUE, sourceDurationMs = 2_000L)
        assertEquals(1_999L, AudioPlaybackPosition.sourcePositionMs(bounded, 9_799L))
        assertNull(AudioPlaybackPosition.sourcePositionMs(bounded, 9_800L))
    }

    @Test fun extremeMalformedEndpointsDoNotOverflowIntoPlayableAudio() {
        assertNull(AudioPlaybackPosition.sourcePositionMs(track().copy(
            trimStartMs = Long.MAX_VALUE, trimEndMs = Long.MIN_VALUE), 9_000L))
        assertNull(AudioPlaybackPosition.sourcePositionMs(track().copy(
            trimStartMs = Long.MIN_VALUE, trimEndMs = -1L), 9_000L))
    }

    @Test fun placementNearLongMaximumDoesNotOverflowTimelineEnd() {
        val late = track().copy(startTimeMs = Long.MAX_VALUE - 100L)
        assertEquals(1_300L, AudioPlaybackPosition.sourcePositionMs(late, Long.MAX_VALUE))
        val longSource = track().copy(trimStartMs = 0L, trimEndMs = Long.MAX_VALUE, startTimeMs = 0L)
        assertEquals(Long.MAX_VALUE - 1L,
            AudioPlaybackPosition.sourcePositionMs(longSource, Long.MAX_VALUE - 1L))
        assertNull(AudioPlaybackPosition.sourcePositionMs(longSource, Long.MAX_VALUE))
    }
}
