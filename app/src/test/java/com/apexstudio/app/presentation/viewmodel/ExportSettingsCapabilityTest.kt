package com.apexstudio.app.presentation.viewmodel

import androidx.media3.common.MimeTypes
import com.apexstudio.app.data.export.EightKExportAvailability
import com.apexstudio.app.data.export.ExportVideoEncoderPlan
import org.junit.Assert.*
import org.junit.Test

class ExportSettingsCapabilityTest {
    private fun reason(
        availability: EightKExportAvailability? = null,
        isPhoto: Boolean = false
    ) = videoExportSettingsUnsupportedReason(
        resolution = "8K",
        fps = 30,
        quality = "high",
        aspectRatio = "16:9",
        bitrateMbps = 0,
        eightKAvailability = availability,
        isPhotoExport = isPhoto
    )

    @Test fun `8K requires a verified encoder plan`() {
        assertNotNull(reason())
        assertNotNull(reason(EightKExportAvailability.Checking))
        assertTrue(reason(EightKExportAvailability.Unsupported("No hardware encoder"))!!.contains("No hardware encoder"))
        assertNull(reason(EightKExportAvailability.Supported(ExportVideoEncoderPlan(MimeTypes.VIDEO_H265, "vendor.hevc.encoder"))))
    }

    @Test fun `8K photo route remains blocked even when video encoding is supported`() {
        val capability = EightKExportAvailability.Supported(
            ExportVideoEncoderPlan(MimeTypes.VIDEO_H265, "vendor.hevc.encoder")
        )
        assertTrue(reason(capability, isPhoto = true)!!.contains("photo export is not available"))
    }

    @Test fun `other resolutions remain unaffected by missing 8K capability`() {
        val reason = videoExportSettingsUnsupportedReason(
            resolution = "4K",
            fps = 30,
            quality = "high",
            aspectRatio = "16:9",
            bitrateMbps = 0
        )
        assertNull(reason)
    }
}
