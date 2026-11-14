package com.apexstudio.app.data.export

/** A user-visible phase of video export, also used to delimit diagnostic timings. */
enum class ExportProgressStage(
    val label: String,
    val diagnosticName: String?
) {
    PREPARING("Preparing export…", "export_preparation"),
    VIDEO_RENDER("Rendering video…", "video_render"),
    AUDIO_PREPARATION("Preparing audio…", "audio_preparation"),
    AUDIO_MIX("Mixing audio…", "audio_mix"),
    MUX("Muxing audio and video…", "mux"),
    FINALIZATION("Finalizing export…", "finalization"),
    COMPLETE("Export complete", null)
}

internal const val PROJECT_VIDEO_RENDER_PROGRESS_OFFSET = 0.01f
internal const val PROJECT_VIDEO_RENDER_PROGRESS_WEIGHT = 0.89f

internal fun projectVideoRenderProgress(renderFraction: Float): Float =
    PROJECT_VIDEO_RENDER_PROGRESS_OFFSET + renderFraction.coerceIn(0f, 1f) * PROJECT_VIDEO_RENDER_PROGRESS_WEIGHT

internal fun projectVideoRenderFraction(overallProgress: Float): Float? {
    val renderEnd = PROJECT_VIDEO_RENDER_PROGRESS_OFFSET + PROJECT_VIDEO_RENDER_PROGRESS_WEIGHT
    if (!overallProgress.isFinite() || overallProgress < PROJECT_VIDEO_RENDER_PROGRESS_OFFSET || overallProgress > renderEnd) {
        return null
    }
    return ((overallProgress - PROJECT_VIDEO_RENDER_PROGRESS_OFFSET) / PROJECT_VIDEO_RENDER_PROGRESS_WEIGHT).coerceIn(0f, 1f)
}
