package com.apexstudio.app.ui.screens.colortools

import com.apexstudio.app.data.export.ExportProgressStage
import com.apexstudio.app.data.export.projectVideoRenderFraction

private const val MIN_TIMELINE_PROGRESS_FRACTION = 0.01f

/** Returns a whole percent only after at least 1% of the source timeline has been encoded. */
internal fun colorVideoExportPercent(encodedTimeMs: Double, durationMs: Double): Int? {
    if (!encodedTimeMs.isFinite() || !durationMs.isFinite() || encodedTimeMs <= 0.0 || durationMs <= 0.0) {
        return null
    }
    return measuredProgressPercent(encodedTimeMs / durationMs)
}

/** Project exports publish a weighted Media3 render estimate; invert that mapping only during rendering. */
internal fun colorTimelineExportPercent(
    overallProgress: Float,
    stage: ExportProgressStage?,
    completedOutputAvailable: Boolean
): Int? {
    if (stage == ExportProgressStage.COMPLETE) return if (completedOutputAvailable) 100 else null
    if (stage != ExportProgressStage.VIDEO_RENDER) return null
    val renderFraction = projectVideoRenderFraction(overallProgress) ?: return null
    // The producer publishes Float progress; use its representable 1% boundary so a forward/inverse
    // Float round-trip at exactly 1% is not misclassified as genuinely below the threshold.
    return measuredProgressPercent(renderFraction.toDouble(), MIN_TIMELINE_PROGRESS_FRACTION.toDouble())
}

private fun measuredProgressPercent(fraction: Double, minimumFraction: Double = 0.01): Int? {
    if (!fraction.isFinite() || fraction < minimumFraction) return null
    return (fraction * 100.0).toInt().coerceIn(1, 99)
}
