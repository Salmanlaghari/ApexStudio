package com.apexstudio.app.data.audio

import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.VolumeKeyframe
import com.apexstudio.app.domain.model.timeline.Curve
import com.apexstudio.app.domain.model.timeline.Keyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTimelineMathTest {
    private val sampleRate = 44_100

    private fun track(
        id: String = "audio",
        startMs: Long = 0L,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 10_000L,
        sourceDurationMs: Long = 0L,
        volume: Float = 1f,
        isMuted: Boolean = false,
        isSolo: Boolean = false,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        volumeKeyframes: List<VolumeKeyframe> = emptyList(),
        pan: Float = 0f,
        panKeyframes: List<Keyframe<Float>> = emptyList()
    ) = AudioTrack(
        id = id,
        name = id,
        uri = "/tmp/$id.wav",
        volume = volume,
        isMuted = isMuted,
        isSolo = isSolo,
        volumeKeyframes = volumeKeyframes,
        pan = pan,
        panKeyframes = panKeyframes,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        startTimeMs = startMs,
        sourceDurationMs = sourceDurationMs,
        fadeInMs = fadeInMs,
        fadeOutMs = fadeOutMs
    )

    @Test
    fun `preview and export share the source-duration-capped half-open window`() {
        val music = track(
            startMs = 1_000L,
            trimStartMs = 500L,
            trimEndMs = 5_000L,
            sourceDurationMs = 2_000L
        )

        assertEquals(AudioTimelineMath.SourceWindow(500L, 2_000L), AudioTimelineMath.sourceWindow(music))
        assertNull(AudioTimelineMath.sourcePositionMs(music, 999L))
        assertEquals(500L, AudioTimelineMath.sourcePositionMs(music, 1_000L))
        assertEquals(1_999L, AudioTimelineMath.sourcePositionMs(music, 2_499L))
        assertNull(AudioTimelineMath.sourcePositionMs(music, 2_500L))

        val placement = AudioTimelineMath.placement(music, sampleRate)!!
        assertEquals(44_100L, placement.startFrame)
        assertEquals(66_150L, placement.playFrames)
        assertEquals(110_250L, placement.endFrame)
        assertEquals(500L, placement.sourceWindow.startMs)
        assertEquals(2_000L, placement.sourceWindow.endMs)
    }

    @Test
    fun `negative trims are normalized and empty windows are rejected`() {
        val normalized = track(trimStartMs = -500L, trimEndMs = 1_000L)
        assertEquals(AudioTimelineMath.SourceWindow(0L, 1_000L), AudioTimelineMath.sourceWindow(normalized))
        assertEquals(999L, AudioTimelineMath.sourcePositionMs(normalized, 999L))
        assertNull(AudioTimelineMath.sourcePositionMs(normalized, 1_000L))
        assertNull(AudioTimelineMath.sourcePositionMs(normalized, -1L))

        val empty = track(trimStartMs = 5_000L, trimEndMs = 4_000L, sourceDurationMs = 8_000L)
        assertNull(AudioTimelineMath.sourceWindow(empty))
        assertNull(AudioTimelineMath.placement(empty, sampleRate))
    }

    @Test
    fun `millisecond and frame conversions use explicit floor and saturation rules`() {
        assertEquals(0L, AudioTimelineMath.millisToFrames(-1L, sampleRate))
        assertEquals(44L, AudioTimelineMath.millisToFrames(1L, sampleRate))
        assertEquals(44_055L, AudioTimelineMath.millisToFrames(999L, sampleRate))
        assertEquals(44_100L, AudioTimelineMath.millisToFrames(1_000L, sampleRate))
        assertEquals(44_144L, AudioTimelineMath.millisToFrames(1_001L, sampleRate))
        assertEquals(Long.MAX_VALUE, AudioTimelineMath.millisToFrames(Long.MAX_VALUE, sampleRate))

        assertEquals(0L, AudioTimelineMath.frameTimeMs(44L, sampleRate))
        assertEquals(1L, AudioTimelineMath.frameTimeMs(45L, sampleRate))
        assertEquals(1_000L, AudioTimelineMath.frameTimeMs(44_144L, sampleRate))
        assertEquals(1_001L, AudioTimelineMath.frameTimeMs(44_145L, sampleRate))
        assertEquals(Long.MAX_VALUE, AudioTimelineMath.frameTimeMs(Long.MAX_VALUE, 1))
    }

    @Test
    fun `placement reports the documented millisecond to frame quantization`() {
        val music = track(startMs = 1_001L, trimStartMs = 100L, trimEndMs = 3_000L, sourceDurationMs = 2_000L)
        val placement = AudioTimelineMath.placement(music, sampleRate)!!

        assertEquals(44_144L, placement.startFrame)
        assertEquals(1_000L, AudioTimelineMath.frameTimeMs(placement.startFrame, sampleRate))
        assertNull(AudioTimelineMath.sourcePositionMs(music, 1_000L))
        assertEquals(100L, AudioTimelineMath.sourcePositionMs(music, 1_001L))
        assertEquals(83_790L, placement.playFrames)
        assertEquals(127_934L, placement.endFrame)
    }

    @Test
    fun `preview gain matches export gain at frame samples within millisecond quantization`() {
        val music = track(
            startMs = 1_000L,
            trimStartMs = 500L,
            trimEndMs = 3_500L,
            sourceDurationMs = 3_500L,
            fadeInMs = 1_000L,
            fadeOutMs = 1_000L,
            volumeKeyframes = listOf(
                VolumeKeyframe("start", 1_000L, 0.2f),
                VolumeKeyframe("end", 4_000L, 1f)
            ),
            panKeyframes = listOf(
                Keyframe(1_000L, -1f, Curve.LINEAR),
                Keyframe(4_000L, 1f, Curve.LINEAR)
            )
        )
        val placement = AudioTimelineMath.placement(music, sampleRate)!!
        val tracks = listOf(music)
        val sortedVolume = music.volumeKeyframes.sortedBy { it.timeMs }
        val sortedPan = music.panKeyframes.sortedBy { it.timeMs }
        val sampleOffsets = listOf(0L, 22_050L, 44_100L, 88_200L, 110_250L, 132_299L)

        for (offset in sampleOffsets) {
            val timelineMs = AudioTimelineMath.frameTimeMs(placement.startFrame + offset, sampleRate)
            val previewGain = AudioTimelineMath.playbackVolumeAt(music, timelineMs, tracks)
            val exportGain = AudioTimelineMath.volumeAt(music, timelineMs, sortedVolume) *
                AudioTimelineMath.fadeEnvelopeAtFrame(
                    offset, placement.playFrames, music.fadeInMs, music.fadeOutMs, sampleRate
                )
            assertEquals("gain at frame offset $offset", previewGain, exportGain, 0.0011f)

            val previewPan = AudioTimelineMath.stereoGainsAt(music, timelineMs)
            val exportPan = AudioTimelineMath.stereoGainsAt(music, timelineMs, sortedPan)
            assertEquals("pan at frame offset $offset", previewPan, exportPan)
        }

        assertEquals(
            0f,
            AudioTimelineMath.fadeEnvelopeAtFrame(
                placement.playFrames, placement.playFrames, music.fadeInMs, music.fadeOutMs, sampleRate
            ),
            0f
        )
    }

    @Test
    fun `volume and pan interpolation stay finite at extreme timestamps`() {
        val extreme = track(
            volume = Float.NaN,
            volumeKeyframes = listOf(
                VolumeKeyframe("first", Long.MIN_VALUE, 0f),
                VolumeKeyframe("last", Long.MAX_VALUE, 1f)
            ),
            pan = Float.NaN,
            panKeyframes = listOf(
                Keyframe(Long.MIN_VALUE, -1f, Curve.LINEAR),
                Keyframe(Long.MAX_VALUE, 1f, Curve.LINEAR)
            )
        )

        assertEquals(0.5f, AudioTimelineMath.volumeAt(extreme, 0L), 0.0001f)
        assertEquals(0f, AudioTimelineMath.panAt(extreme, 0L), 0.0001f)
        assertEquals(1f to 1f, AudioTimelineMath.stereoGainsAt(extreme, 0L))

        val malformed = extreme.copy(
            volumeKeyframes = listOf(VolumeKeyframe("bad-volume", 0L, Float.POSITIVE_INFINITY)),
            panKeyframes = listOf(Keyframe(0L, Float.NaN, Curve.LINEAR))
        )
        assertEquals(0f, AudioTimelineMath.volumeAt(malformed, 0L), 0f)
        assertEquals(0f, AudioTimelineMath.panAt(malformed, 0L), 0f)
    }

    @Test
    fun `mute and solo policy is shared by preview and export selection`() {
        val ordinary = track(id = "ordinary")
        val muted = track(id = "muted", isMuted = true)
        val solo = track(id = "solo", isSolo = true)
        val mutedSolo = solo.copy(isMuted = true)

        assertTrue(AudioTimelineMath.isEnabled(ordinary, listOf(ordinary, muted)))
        assertFalse(AudioTimelineMath.isEnabled(muted, listOf(ordinary, muted)))
        assertTrue(AudioTimelineMath.isEnabled(solo, listOf(ordinary, solo)))
        assertFalse(AudioTimelineMath.isEnabled(ordinary, listOf(ordinary, solo)))
        assertFalse(AudioTimelineMath.isEnabled(ordinary, listOf(ordinary, mutedSolo)))
        assertFalse(AudioTimelineMath.isEnabled(mutedSolo, listOf(ordinary, mutedSolo)))
    }

    @Test
    fun `extreme source placement saturates frame end without wrapping`() {
        val late = track(
            startMs = Long.MAX_VALUE - 100L,
            trimStartMs = 0L,
            trimEndMs = Long.MAX_VALUE
        )
        val placement = AudioTimelineMath.placement(late, sampleRate)!!

        assertEquals(Long.MAX_VALUE, placement.startFrame)
        assertEquals(Long.MAX_VALUE, placement.playFrames)
        assertEquals(Long.MAX_VALUE, placement.endFrame)
        assertEquals(100L, AudioTimelineMath.sourcePositionMs(late, Long.MAX_VALUE))
    }
}
