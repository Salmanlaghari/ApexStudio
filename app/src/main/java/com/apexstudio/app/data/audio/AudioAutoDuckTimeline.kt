package com.apexstudio.app.data.audio

import com.apexstudio.app.ai.audio.AudioIntelligence
import com.apexstudio.app.ai.audio.AudioIntelligence.DuckPoint
import com.apexstudio.app.domain.model.AudioTrack

/** Maps source-relative voice analysis onto the music track's absolute project-time window. */
internal object AudioAutoDuckTimeline {
    private const val PRE_ROLL_MS = 400L
    private const val RELEASE_MS = 320L
    private const val BLOCK_MS = 20L

    fun plan(
        musicTrack: AudioTrack,
        voiceTrack: AudioTrack,
        musicPcm: FloatArray,
        voicePcm: FloatArray,
        sampleRateHz: Int,
    ): List<DuckPoint> {
        if (sampleRateHz <= 0 || musicPcm.isEmpty() || voicePcm.isEmpty()) return emptyList()
        val music = pcmWindow(musicTrack, musicPcm, sampleRateHz) ?: return emptyList()
        val voice = pcmWindow(voiceTrack, voicePcm, sampleRateHz) ?: return emptyList()

        val overlapStartMs = maxOf(music.timelineStartMs, voice.timelineStartMs)
        val overlapEndMs = minOf(music.timelineEndMs, voice.timelineEndMs)
        if (overlapEndMs <= overlapStartMs) return emptyList()

        // Keep enough voice history to enter the music window at the correct gain
        // when speech began before the music track, without copying a large PCM array.
        val analysisStartMs = maxOf(voice.timelineStartMs, subtractFloorAtZero(music.timelineStartMs, PRE_ROLL_MS))
        val analysisEndMs = minOf(music.timelineEndMs, saturatingAdd(voice.timelineEndMs, RELEASE_MS + BLOCK_MS))
        if (analysisEndMs <= analysisStartMs) return emptyList()

        val speechStartOffsetFrames = millisToFrames(analysisStartMs - voice.timelineStartMs, sampleRateHz)
            .coerceAtMost(voice.frameCount)
        val speechEndOffsetFrames = millisToFrames(
            minOf(analysisEndMs, voice.timelineEndMs) - voice.timelineStartMs,
            sampleRateHz,
        ).coerceAtMost(voice.frameCount)
        val speechStartFrame = voice.firstFrame + speechStartOffsetFrames
        val speechEndFrame = voice.firstFrame + speechEndOffsetFrames
        if (speechEndFrame <= speechStartFrame) return emptyList()

        val trailingSilenceMs = (analysisEndMs - voice.timelineEndMs).coerceAtLeast(0L)
        val sourcePlan = AudioIntelligence.autoDuckPlanForRange(
            music = musicPcm,
            speech = voicePcm,
            sampleRateHz = sampleRateHz,
            speechStartSampleIndex = speechStartFrame,
            speechEndSampleIndexExclusive = speechEndFrame,
            timelineStartMs = analysisStartMs,
            trailingSilenceMs = trailingSilenceMs,
        )
        if (sourcePlan.isEmpty()) return emptyList()

        // Volume keyframes use project time. Add an explicit normal-level value at
        // music start, then preserve the interpolated voice envelope only while music plays.
        val mapped = mutableListOf(DuckPoint(music.timelineStartMs, gainAt(sourcePlan, music.timelineStartMs)))
        sourcePlan.asSequence()
            .filter { it.timeMs > music.timelineStartMs && it.timeMs < music.timelineEndMs }
            .forEach(mapped::add)
        return mapped
    }

    private fun pcmWindow(track: AudioTrack, pcm: FloatArray, sampleRateHz: Int): PcmWindow? {
        val sourceWindow = AudioTimelineMath.sourceWindow(track) ?: return null
        val firstFrame = AudioTimelineMath.millisToFrames(sourceWindow.startMs, sampleRateHz)
            .coerceAtMost(pcm.size.toLong()).toInt()
        val endFrame = AudioTimelineMath.millisToFrames(sourceWindow.endMs, sampleRateHz)
            .coerceAtMost(pcm.size.toLong()).toInt()
        if (endFrame <= firstFrame) return null

        val timelineStartMs = track.startTimeMs.coerceAtLeast(0L)
        val durationMs = AudioTimelineMath.frameTimeMs((endFrame - firstFrame).toLong(), sampleRateHz)
        if (durationMs <= 0L) return null
        return PcmWindow(
            firstFrame = firstFrame,
            frameCount = endFrame - firstFrame,
            timelineStartMs = timelineStartMs,
            timelineEndMs = saturatingAdd(timelineStartMs, durationMs),
        )
    }

    private fun gainAt(points: List<DuckPoint>, timeMs: Long): Float {
        val before = points.lastOrNull { it.timeMs <= timeMs } ?: return points.first().gain
        val after = points.firstOrNull { it.timeMs >= timeMs } ?: return before.gain
        if (after.timeMs <= before.timeMs) return after.gain
        val fraction = ((timeMs - before.timeMs).toDouble() / (after.timeMs - before.timeMs).toDouble())
            .toFloat()
        return before.gain + (after.gain - before.gain) * fraction
    }

    private fun millisToFrames(millis: Long, sampleRateHz: Int): Int =
        AudioTimelineMath.millisToFrames(millis.coerceAtLeast(0L), sampleRateHz)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun saturatingAdd(first: Long, second: Long): Long {
        val a = first.coerceAtLeast(0L)
        val b = second.coerceAtLeast(0L)
        return if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
    }

    private fun subtractFloorAtZero(value: Long, amount: Long): Long =
        (value.coerceAtLeast(0L) - amount.coerceAtLeast(0L)).coerceAtLeast(0L)

    private data class PcmWindow(
        val firstFrame: Int,
        val frameCount: Int,
        val timelineStartMs: Long,
        val timelineEndMs: Long,
    )
}
