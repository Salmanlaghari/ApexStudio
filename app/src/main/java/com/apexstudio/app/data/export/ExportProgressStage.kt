package com.apexstudio.app.data.export

/** A user-visible phase of video export, also used to delimit diagnostic timings. */
enum class ExportProgressStage(
    val label: String,
    val diagnosticName: String?
) {
    PREPARING("Preparing export…", "export_preparation"),
    VIDEO_RENDER("Rendering video…", "video_render"),
    VIDEO_OUTPUT_VERIFICATION("Checking video output…", "video_output_verification"),
    AUDIO_PREPARATION("Preparing audio…", "audio_preparation"),
    AUDIO_MIX("Mixing audio…", "audio_mix"),
    MUX("Muxing audio and video…", "mux"),
    FRAME_RATE_VERIFICATION("Verifying frame rate…", "frame_rate_verification"),
    FINALIZATION("Finalizing export…", "finalization"),
    COMPLETE("Export complete", null)
}

internal const val PROJECT_VIDEO_RENDER_PROGRESS_OFFSET = 0.01f
internal const val PROJECT_VIDEO_RENDER_PROGRESS_WEIGHT = 0.89f

internal fun videoRenderProgress(renderFraction: Float): Float =
    PROJECT_VIDEO_RENDER_PROGRESS_OFFSET + renderFraction.coerceIn(0f, 1f) * PROJECT_VIDEO_RENDER_PROGRESS_WEIGHT

internal fun projectVideoRenderProgress(renderFraction: Float): Float =
    videoRenderProgress(renderFraction)

internal fun audioExportProgress(renderFraction: Float): Float =
    (if (renderFraction.isFinite()) renderFraction else 0f).coerceIn(0f, 0.99f)

internal fun canAdvanceExportStage(
    currentStage: ExportProgressStage?,
    nextStage: ExportProgressStage
): Boolean = currentStage == null || nextStage.ordinal >= currentStage.ordinal

internal fun nextVerificationProgress(
    isExporting: Boolean,
    currentStage: ExportProgressStage?,
    currentProgress: Float,
    reportedProgress: Float,
    expectedStage: ExportProgressStage = ExportProgressStage.FRAME_RATE_VERIFICATION
): Float? {
    if (!isExporting || currentStage != expectedStage || !reportedProgress.isFinite()) return null
    val next = reportedProgress.coerceIn(FINALIZATION_PROGRESS_START, FINALIZATION_PROGRESS_END)
    return next.takeIf { it > currentProgress }
}

internal fun projectVideoRenderFraction(overallProgress: Float): Float? {
    val renderEnd = PROJECT_VIDEO_RENDER_PROGRESS_OFFSET + PROJECT_VIDEO_RENDER_PROGRESS_WEIGHT
    if (!overallProgress.isFinite() || overallProgress < PROJECT_VIDEO_RENDER_PROGRESS_OFFSET || overallProgress > renderEnd) {
        return null
    }
    return ((overallProgress - PROJECT_VIDEO_RENDER_PROGRESS_OFFSET) / PROJECT_VIDEO_RENDER_PROGRESS_WEIGHT).coerceIn(0f, 1f)
}
