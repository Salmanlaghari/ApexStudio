package com.apexstudio.app.domain.model

/** Project playheads and source trim points are different coordinate systems. */
object ClipTimeMapping {
    fun sourceAtProjectTime(clips: List<MediaClip>, clip: MediaClip, projectMs: Long): Long {
        val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
        val elapsed = (projectMs - SpeedCurveMath.timelineStartMsForClip(clips, clip)).coerceAtLeast(0L)
        return clip.trimStartMs + SpeedCurveMath.outputToSourceMs(elapsed, span, clip.speedPoints)
    }

    fun projectAtSourceTime(clips: List<MediaClip>, clip: MediaClip, sourceMs: Long): Long {
        val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
        return SpeedCurveMath.timelineStartMsForClip(clips, clip) +
            SpeedCurveMath.sourceToOutputMs((sourceMs - clip.trimStartMs).coerceIn(0L, span), span, clip.speedPoints)
    }
}
