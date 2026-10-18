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

    /**
     * Output/timeline duration represented by a source-time interval in
     * [clip]. Both boundaries are mapped against the same original trimmed
     * span; recalculating the curve against only the removed interval would
     * incorrectly restart a ramp at its first speed point.
     */
    fun outputDurationBetweenSourceTimes(
        clip: MediaClip,
        sourceStartMs: Long,
        sourceEndMs: Long
    ): Long {
        val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
        val from = (sourceStartMs - clip.trimStartMs).coerceIn(0L, span)
        val to = (sourceEndMs - clip.trimStartMs).coerceIn(from, span)
        return SpeedCurveMath.sourceToOutputMs(to, span, clip.speedPoints) -
            SpeedCurveMath.sourceToOutputMs(from, span, clip.speedPoints)
    }
}
