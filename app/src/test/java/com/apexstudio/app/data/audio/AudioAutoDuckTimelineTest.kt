package com.apexstudio.app.data.audio

import com.apexstudio.app.domain.model.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAutoDuckTimelineTest {
    @Test
    fun duckEnvelopeUsesVoiceTrimAndAbsoluteProjectPlacement() {
        val sampleRateHz = 1_000
        val musicPcm = FloatArray(6_000)
        val voicePcm = FloatArray(4_000).apply { fill(0.2f, fromIndex = 1_000, toIndex = 1_500) }
        val music = track(
            id = "music",
            sourceDurationMs = 6_000L,
            trimStartMs = 1_000L,
            trimEndMs = 5_000L,
            startTimeMs = 2_000L,
        )
        val voice = track(
            id = "voice",
            sourceDurationMs = 4_000L,
            trimStartMs = 500L,
            trimEndMs = 2_500L,
            startTimeMs = 3_000L,
        )

        val plan = AudioAutoDuckTimeline.plan(music, voice, musicPcm, voicePcm, sampleRateHz)

        assertEquals(2_000L, plan.first().timeMs)
        assertEquals(1f, plan.first().gain, 0.001f)
        assertTrue("speech at source 1,000 ms should duck near project 3,500 ms", plan.any { it.timeMs == 3_520L && it.gain < 0.9f })
        assertEquals(0.25f, gainAt(plan, 3_600L), 0.01f)
        assertEquals("music should recover after the voice ends", 1f, gainAt(plan, 4_500L), 0.01f)
        assertTrue(plan.all { it.timeMs in 2_000L until 6_000L })
    }

    @Test
    fun voiceActivityOutsideRetainedTrimDoesNotDuckMusic() {
        val sampleRateHz = 1_000
        val musicPcm = FloatArray(4_000)
        val voicePcm = FloatArray(4_000).apply {
            fill(0.2f, fromIndex = 0, toIndex = 900)
            fill(0.2f, fromIndex = 2_100, toIndex = 4_000)
        }
        val music = track(id = "music", sourceDurationMs = 4_000L, trimEndMs = 4_000L)
        val voice = track(
            id = "voice",
            sourceDurationMs = 4_000L,
            trimStartMs = 1_000L,
            trimEndMs = 2_000L,
        )

        val plan = AudioAutoDuckTimeline.plan(music, voice, musicPcm, voicePcm, sampleRateHz)

        assertTrue(plan.isNotEmpty())
        assertTrue("speech excluded by the voice trim must not lower the fader", plan.all { it.gain == 1f })
    }

    @Test
    fun speechAlreadyActiveWhenMusicStartsIsDuckedAtMusicStart() {
        val sampleRateHz = 1_000
        val musicPcm = FloatArray(5_000)
        val voicePcm = FloatArray(5_000).apply { fill(0.2f, fromIndex = 1_000, toIndex = 3_000) }
        val music = track(
            id = "music",
            sourceDurationMs = 5_000L,
            trimEndMs = 5_000L,
            startTimeMs = 2_000L,
        )
        val voice = track(id = "voice", sourceDurationMs = 5_000L, trimEndMs = 5_000L)

        val plan = AudioAutoDuckTimeline.plan(music, voice, musicPcm, voicePcm, sampleRateHz)

        assertEquals(2_000L, plan.first().timeMs)
        assertEquals("the voice has already been active through the pre-roll", 0.25f, plan.first().gain, 0.01f)
    }

    @Test
    fun tracksWithoutProjectTimeOverlapDoNotReplaceMusicEnvelope() {
        val sampleRateHz = 1_000
        val musicPcm = FloatArray(4_000)
        val voicePcm = FloatArray(4_000).apply { fill(0.2f) }
        val music = track(id = "music", sourceDurationMs = 4_000L, trimEndMs = 2_000L)
        val voice = track(
            id = "voice",
            sourceDurationMs = 4_000L,
            trimEndMs = 2_000L,
            startTimeMs = 3_000L,
        )

        assertTrue(AudioAutoDuckTimeline.plan(music, voice, musicPcm, voicePcm, sampleRateHz).isEmpty())
    }

    private fun track(
        id: String,
        sourceDurationMs: Long,
        trimStartMs: Long = 0L,
        trimEndMs: Long,
        startTimeMs: Long = 0L,
    ) = AudioTrack(
        id = id,
        name = id,
        uri = "file:///$id.wav",
        volume = 0.8f,
        sourceDurationMs = sourceDurationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        startTimeMs = startTimeMs,
    )

    private fun gainAt(points: List<com.apexstudio.app.ai.audio.AudioIntelligence.DuckPoint>, timeMs: Long): Float {
        val before = points.lastOrNull { it.timeMs <= timeMs } ?: return points.first().gain
        val after = points.firstOrNull { it.timeMs >= timeMs } ?: return before.gain
        if (after.timeMs == before.timeMs) return after.gain
        val fraction = (timeMs - before.timeMs).toFloat() / (after.timeMs - before.timeMs).toFloat()
        return before.gain + (after.gain - before.gain) * fraction
    }
}
