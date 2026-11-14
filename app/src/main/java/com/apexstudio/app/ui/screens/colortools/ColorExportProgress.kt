package com.apexstudio.app.ui.screens.colortools

import com.apexstudio.app.data.export.ExportProgressStage
import com.apexstudio.app.data.export.projectVideoRenderFraction

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
    stage: ExportProgressStage,
    completedOutputAvailable: Boolean
): Int? {
    if (stage == ExportProgressStage.COMPLETE) return if (completedOutputAvailable) 100 else null
    if (stage != ExportProgressStage.VIDEO_RENDER) return null
    val renderFraction = projectVideoRenderFraction(overallProgress) ?: return null
    return measuredProgressPercent(renderFraction.toDouble())
}

private fun measuredProgressPercent(fraction: Double): Int? {
    if (!fraction.isFinite() || fraction < 0.01) return null
    return (fraction * 100.0).toInt().coerceIn(1, 99)
}
