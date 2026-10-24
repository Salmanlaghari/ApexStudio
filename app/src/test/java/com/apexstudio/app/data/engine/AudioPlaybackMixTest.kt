package com.apexstudio.app.data.engine

import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.VolumeKeyframe
import com.apexstudio.app.domain.model.timeline.Curve
import com.apexstudio.app.domain.model.timeline.Keyframe
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPlaybackMixTest {
    private fun track(
        id: String = "music",
        startMs: Long = 5_000L,
        trimStartMs: Long = 1_000L,
        trimEndMs: Long = 9_000L,
        volume: Float = 1f,
        isMuted: Boolean = false,
        isSolo: Boolean = false,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        keyframes: List<VolumeKeyframe> = emptyList()
    ) = AudioTrack(
        id = id,
        name = id,
        uri = "/tmp/$id.wav",
        volume = volume,
        isMuted = isMuted,
        isSolo = isSolo,
        volumeKeyframes = keyframes,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        startTimeMs = startMs,
        sourceDurationMs = 10_000L,
        fadeInMs = fadeInMs,
        fadeOutMs = fadeOutMs
    )

    @Test
    fun `solo silences other tracks while mute still silences the solo track`() {
        val ordinary = track(id = "ordinary", volume = 0.8f)
        val solo = track(id = "solo", volume = 0.6f, isSolo = true)
        val tracks = listOf(ordinary, solo)

        assertEquals(0f, AudioPlaybackMix.volumeAt(ordinary, 7_000L, tracks), 0.0001f)
        assertEquals(0.6f, AudioPlaybackMix.volumeAt(solo, 7_000L, tracks), 0.0001f)

        val mutedSolo = solo.copy(isMuted = true)
        val mutedSoloMix = listOf(ordinary, mutedSolo)
        assertEquals(0f, AudioPlaybackMix.volumeAt(mutedSolo, 7_000L, mutedSoloMix), 0.0001f)
        assertEquals(0f, AudioPlaybackMix.volumeAt(ordinary, 7_000L, mutedSoloMix), 0.0001f)

        assertEquals(0.8f, AudioPlaybackMix.volumeAt(ordinary, 7_000L, listOf(ordinary)), 0.0001f)
    }

    @Test
    fun `placed timeline keyframes compose with fades relative to trimmed audio edges`() {
        val music = track(
            volume = 0.1f,
            fadeInMs = 2_000L,
            fadeOutMs = 4_000L,
            keyframes = listOf(
                VolumeKeyframe("start", 5_000L, 0.5f),
                VolumeKeyframe("end", 9_000L, 1f)
            )
        )

        // Fade starts at the track's timeline placement (5000ms), not source trim (1000ms).
        assertEquals(0f, AudioPlaybackMix.volumeAt(music, 5_000L, listOf(music)), 0.0001f)
        // At +1s, keyframe volume is 0.625 and the local fade-in is 0.5.
        assertEquals(0.3125f, AudioPlaybackMix.volumeAt(music, 6_000L, listOf(music)), 0.0001f)
        // At +2s, fade-in is complete; keyframe interpolation remains timeline-absolute.
        assertEquals(0.75f, AudioPlaybackMix.volumeAt(music, 7_000L, listOf(music)), 0.0001f)
        // At 11s there are 2s remaining in the 8s trimmed playback window.
        assertEquals(0.5f, AudioPlaybackMix.volumeAt(music, 11_000L, listOf(music)), 0.0001f)
        assertEquals(0f, AudioPlaybackMix.volumeAt(music, 13_000L, listOf(music)), 0.0001f)
    }

    @Test
    fun `pan produces distinct stereo gains and samples typed pan keyframes`() {
        val base = track(startMs = 0L, trimStartMs = 0L, trimEndMs = 10_000L)
        assertEquals(1f to 1f, AudioPlaybackMix.stereoGainsAt(base, 0L))
        assertEquals(1f to 0f, AudioPlaybackMix.stereoGainsAt(base.copy(pan = -1f), 0L))
        assertEquals(0f to 1f, AudioPlaybackMix.stereoGainsAt(base.copy(pan = 1f), 0L))

        val animated = base.copy(
            panKeyframes = listOf(
                Keyframe(0L, -1f, Curve.LINEAR),
                Keyframe(1_000L, 1f, Curve.LINEAR)
            )
        )
        assertEquals(0f to 1f, AudioPlaybackMix.stereoGainsAt(animated, 1_000L))
        assertEquals(1f to 1f, AudioPlaybackMix.stereoGainsAt(animated, 500L))
    }
}
