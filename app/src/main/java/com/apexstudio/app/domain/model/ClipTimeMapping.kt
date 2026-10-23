package com.apexstudio.app.domain.model

/** Project playheads and source trim points are different coordinate systems. */
object ClipTimeMapping {
    /**
     * Main-track clip duration in project/output milliseconds. The export
     * applies [MediaClip.speedMultiplier] when no variable curve is present,
     * so the timeline must use the same source-to-output conversion.
     */
    fun mainClipDurationMs(clip: MediaClip): Long {
        val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
        val points = SpeedCurveMath.sanitize(clip.speedPoints)
        if (points.size >= 2) return SpeedCurveMath.effectiveDurationMs(span, points)
        val speed = SpeedCurveMath.sanitizeConstantSpeed(clip.speedMultiplier)
        return (span.toDouble() / speed.toDouble()).toLong().coerceAtLeast(if (span > 0L) 1L else 0L)
    }

    /** Main-track source-relative offset for an elapsed project/output time. */
    fun mainSourceOffsetAtOutputMs(clip: MediaClip, outputMs: Long): Long {
        val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
        val points = SpeedCurveMath.sanitize(clip.speedPoints)
        return if (points.size >= 2) {
            SpeedCurveMath.outputToSourceMs(outputMs, span, points)
        } else {
            val speed = SpeedCurveMath.sanitizeConstantSpeed(clip.speedMultiplier)
            (outputMs.coerceAtLeast(0L).toDouble() * speed.toDouble()).toLong().coerceIn(0L, span)
        }
    }

    /** Main-track project/output offset corresponding to an absolute source timestamp. */
    fun mainOutputOffsetAtSourceMs(clip: MediaClip, sourceMs: Long): Long {
        val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
        val sourceOffset = (sourceMs - clip.trimStartMs).coerceIn(0L, span)
        val points = SpeedCurveMath.sanitize(clip.speedPoints)
        return if (points.size >= 2) {
            SpeedCurveMath.sourceToOutputMs(sourceOffset, span, points)
        } else {
            val speed = SpeedCurveMath.sanitizeConstantSpeed(clip.speedMultiplier)
            (sourceOffset.toDouble() / speed.toDouble()).toLong().coerceIn(0L, mainClipDurationMs(clip))
        }
    }

    /** Text windows already use output time. Preserve negatives so future clips remain inactive. */
    fun outputAtProjectTime(clips: List<MediaClip>, clip: MediaClip, projectMs: Long): Long =
        projectMs - SpeedCurveMath.timelineStartMsForClip(clips, clip)

    fun sourceAtProjectTime(clips: List<MediaClip>, clip: MediaClip, projectMs: Long): Long {
        val elapsed = (projectMs - SpeedCurveMath.timelineStartMsForClip(clips, clip)).coerceAtLeast(0L)
        val sourceOffset = if (isMainVideo(clip)) mainSourceOffsetAtOutputMs(clip, elapsed) else {
            val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
            SpeedCurveMath.outputToSourceMs(elapsed, span, clip.speedPoints)
        }
        return clip.trimStartMs + sourceOffset
    }

    fun projectAtSourceTime(clips: List<MediaClip>, clip: MediaClip, sourceMs: Long): Long {
        return SpeedCurveMath.timelineStartMsForClip(clips, clip) + outputOffsetAtSourceTime(clip, sourceMs)
    }

    /** Clip-relative output offset for an absolute source timestamp. */
    fun outputOffsetAtSourceTime(clip: MediaClip, sourceMs: Long): Long {
        if (isMainVideo(clip)) return mainOutputOffsetAtSourceMs(clip, sourceMs)
        val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
        return SpeedCurveMath.sourceToOutputMs(
            (sourceMs - clip.trimStartMs).coerceIn(0L, span), span, clip.speedPoints
        )
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
        val trimEnd = clip.trimStartMs + span
        val from = sourceStartMs.coerceIn(clip.trimStartMs, trimEnd)
        val to = sourceEndMs.coerceIn(from, trimEnd)
        return outputOffsetAtSourceTime(clip, to) - outputOffsetAtSourceTime(clip, from)
    }

    private fun isMainVideo(clip: MediaClip): Boolean =
        clip.trackIndex == 0 && clip.type == ClipType.VIDEO
}
