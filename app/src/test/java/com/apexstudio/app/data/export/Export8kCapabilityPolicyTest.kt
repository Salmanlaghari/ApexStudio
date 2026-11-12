package com.apexstudio.app.data.export

import androidx.media3.common.MimeTypes
import android.media.MediaFormat
import org.junit.Assert.*
import org.junit.Test

class Export8kCapabilityPolicyTest {
    private val request = EightKCapabilityRequest("16:9", fps = 30, bitrateBps = 100_000_000)
    private val readyGates = EightKDeviceGates(
        is64BitProcess = true,
        exposesHardwareEncoderFlag = true,
        isLowRamDevice = false,
        appMemoryClassMb = 768,
        totalMemoryBytes = 12L * 1024 * 1024 * 1024,
        availableMemoryBytes = 4L * 1024 * 1024 * 1024,
        maxTextureSize = 8192,
        maxRenderbufferSize = 8192,
        maxViewportWidth = 8192,
        maxViewportHeight = 8192
    )
    private val hevc = EightKEncoderCandidate(
        mimeType = MimeTypes.VIDEO_H265,
        codecName = "vendor.hevc.encoder",
        hardwareAccelerated = true,
        supportsSizeAndRate = true,
        bitrateMin = 1_000_000,
        bitrateMax = 160_000_000
    )
    private val avc = EightKEncoderCandidate(
        mimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
        codecName = "vendor.avc.encoder",
        hardwareAccelerated = true,
        supportsSizeAndRate = true,
        bitrateMin = 1_000_000,
        bitrateMax = 120_000_000
    )

    @Test fun `prefers verified HEVC and retains the exact codec name`() {
        val result = evaluateEightKCapability(request, readyGates, listOf(avc, hevc))
        assertEquals(
            EightKExportAvailability.Supported(ExportVideoEncoderPlan(MimeTypes.VIDEO_H265, "vendor.hevc.encoder")),
            result
        )
    }

    @Test fun `uses verified AVC only when no eligible HEVC encoder exists`() {
        val result = evaluateEightKCapability(request, readyGates, listOf(hevc.copy(supportsSizeAndRate = false), avc))
        assertEquals(
            EightKExportAvailability.Supported(ExportVideoEncoderPlan(MediaFormat.MIMETYPE_VIDEO_AVC, "vendor.avc.encoder")),
            result
        )
    }

    @Test fun `rejects encoders without hardware size rate or bitrate support`() {
        val result = evaluateEightKCapability(
            request,
            readyGates,
            listOf(hevc.copy(bitrateMax = 80_000_000), avc.copy(hardwareAccelerated = false))
        ) as EightKExportAvailability.Unsupported
        assertTrue(result.reason.contains("No hardware"))
        assertTrue(result.reason.contains("7680×4320"))
    }

    @Test fun `rejects every unverified device resource gate`() {
        val reasons = listOf(
            readyGates.copy(is64BitProcess = false),
            readyGates.copy(exposesHardwareEncoderFlag = false),
            readyGates.copy(isLowRamDevice = null),
            readyGates.copy(appMemoryClassMb = 384),
            readyGates.copy(totalMemoryBytes = null),
            readyGates.copy(availableMemoryBytes = 1L * 1024 * 1024 * 1024),
            readyGates.copy(maxTextureSize = null),
            readyGates.copy(maxRenderbufferSize = 4096),
            readyGates.copy(maxViewportHeight = 4096)
        ).map { gates ->
            (evaluateEightKCapability(request, gates, listOf(hevc)) as EightKExportAvailability.Unsupported).reason
        }
        assertEquals(7, reasons.distinct().size)
    }

    @Test fun `unreadable device RAM is not reported as insufficient physical RAM`() {
        val result = evaluateEightKCapability(
            request,
            readyGates.copy(totalMemoryBytes = null, availableMemoryBytes = null),
            listOf(hevc)
        ) as EightKExportAvailability.Unsupported
        assertTrue(result.reason.contains("memory status could not be verified"))
        assertFalse(result.reason.contains("at least 8 GB"))
    }

    @Test fun `GLES viewport is checked against the actual aspect ratio dimensions`() {
        val landscape = evaluateEightKCapability(
            request,
            readyGates.copy(maxViewportWidth = 8192, maxViewportHeight = 4608),
            listOf(hevc)
        )
        assertTrue(landscape is EightKExportAvailability.Supported)

        val portrait = evaluateEightKCapability(
            request.copy(aspectRatio = "9:16"),
            readyGates.copy(maxViewportWidth = 4608, maxViewportHeight = 8192),
            listOf(hevc)
        )
        assertTrue(portrait is EightKExportAvailability.Supported)

        val tooShort = evaluateEightKCapability(
            request,
            readyGates.copy(maxViewportWidth = 8192, maxViewportHeight = 4096),
            listOf(hevc)
        )
        assertTrue(tooShort is EightKExportAvailability.Unsupported)
    }

    @Test fun `square output is not mislabeled as 8K UHD`() {
        val result = evaluateEightKCapability(request.copy(aspectRatio = "1:1"), readyGates, listOf(hevc))
        assertTrue(result is EightKExportAvailability.Unsupported)
        assertTrue((result as EightKExportAvailability.Unsupported).reason.contains("square"))
    }

    @Test fun `settings builder matches auto and manual bitrate used by output policy`() {
        val automatic = eightKCapabilityRequestForSettings("16:9", 30, "high", 0)
        assertEquals(100_000_000, automatic.bitrateBps)
        val manual = eightKCapabilityRequestForSettings("9:16", 30, "medium", 42)
        assertEquals(42_000_000, manual.bitrateBps)
        assertEquals("9:16", manual.aspectRatio)
    }

    @Test fun `final stream validation rejects a downgraded size or changed codec`() {
        assertNull(
            eightKOutputFormatMismatch(
                expectedMimeType = MimeTypes.VIDEO_H265,
                expectedWidth = 7680,
                expectedHeight = 4320,
                actualMimeType = MimeTypes.VIDEO_H265,
                actualWidth = 7680,
                actualHeight = 4320
            )
        )
        val downgraded = eightKOutputFormatMismatch(
            expectedMimeType = MimeTypes.VIDEO_H265,
            expectedWidth = 7680,
            expectedHeight = 4320,
            actualMimeType = MimeTypes.VIDEO_H265,
            actualWidth = 3840,
            actualHeight = 2160
        )
        assertTrue(downgraded.orEmpty().contains("3840×2160"))
        val wrongCodec = eightKOutputFormatMismatch(
            expectedMimeType = MimeTypes.VIDEO_H265,
            expectedWidth = 7680,
            expectedHeight = 4320,
            actualMimeType = MimeTypes.VIDEO_H264,
            actualWidth = 7680,
            actualHeight = 4320
        )
        assertTrue(wrongCodec.orEmpty().contains(MimeTypes.VIDEO_H264))
    }
}
