package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ExportResolution

/** Settings accepted by the selected-clip Media3 H.264/MP4 export path. */
internal fun videoExportSettingsUnsupportedReason(
    resolution: String,
    fps: Int,
    quality: String,
    aspectRatio: String,
    bitrateMbps: Int
): String? {
    val canonicalResolution = ExportResolution.canonical(resolution)
    if (resolution != canonicalResolution) return "Choose a supported output resolution (720P, 1080P, 1440P, or 4K)."
    if (fps !in 24..120) return "Selected-clip export supports frame rates from 24 to 120 fps."
    if (quality.lowercase() !in setOf("low", "medium", "high")) {
        return "Choose Low, Medium, or High quality."
    }
    if (aspectRatio !in setOf("16:9", "9:16", "1:1")) return "Choose 16:9, 9:16, or 1:1 aspect ratio."
    if (bitrateMbps !in 0..100) return "Video bitrate must be Auto (0) or between 1 and 100 Mbps."
    return null
}
