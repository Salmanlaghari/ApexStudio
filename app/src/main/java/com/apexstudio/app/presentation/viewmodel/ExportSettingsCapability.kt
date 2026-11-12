package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ExportResolution
import com.apexstudio.app.data.export.EightKExportAvailability

/** Settings accepted by the selected-clip and whole-project Media3 video export paths. */
internal fun videoExportSettingsUnsupportedReason(
    resolution: String,
    fps: Int,
    quality: String,
    aspectRatio: String,
    bitrateMbps: Int,
    eightKAvailability: EightKExportAvailability? = null,
    isPhotoExport: Boolean = false
): String? {
    val canonicalResolution = ExportResolution.canonical(resolution)
    if (resolution != canonicalResolution) return "Choose a supported output resolution (720P, 1080P, 1440P, 4K, or device-supported 8K)."
    if (fps !in 24..120) return "Selected-clip export supports frame rates from 24 to 120 fps."
    if (quality.lowercase() !in setOf("low", "medium", "high")) {
        return "Choose Low, Medium, or High quality."
    }
    if (aspectRatio !in setOf("16:9", "9:16", "1:1")) return "Choose 16:9, 9:16, or 1:1 aspect ratio."
    if (bitrateMbps !in 0..100) return "Video bitrate must be Auto (0) or between 1 and 100 Mbps."
    if (canonicalResolution == "8K") {
        if (isPhotoExport) {
            return "8K still-photo export is not available yet; the photo renderer is memory-limited to 4K. Choose 4K or lower."
        }
        return when (eightKAvailability) {
            is EightKExportAvailability.Supported -> null
            is EightKExportAvailability.Unsupported -> eightKAvailability.reason
            EightKExportAvailability.Checking -> "Checking device support for 8K. Wait for the check to finish, then retry."
            null -> "8K support has not been verified on this device. Reopen Export and wait for the capability check."
        }
    }
    return null
}
