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
