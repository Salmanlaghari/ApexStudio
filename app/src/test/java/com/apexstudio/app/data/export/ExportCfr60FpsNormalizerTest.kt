package com.apexstudio.app.data.export

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCfr60FpsNormalizerTest {
    @Test
    fun commandUsesExplicitCfrAndTheBundledOpenH264Encoder() {
        val input = "/storage/emulated/0/clip with spaces.mp4"
        val output = "/storage/emulated/0/verified 60fps copy.mp4"
        val args = ExportCfr60FpsNormalizer.buildArguments(input, output, 6_000_000).toList()

        assertEquals(input, args[args.indexOf("-i") + 1])
        assertEquals("fps=60", args[args.indexOf("-vf") + 1])
        assertEquals("cfr", args[args.indexOf("-fps_mode") + 1])
        assertEquals("libopenh264", args[args.indexOf("-c:v") + 1])
        assertEquals("copy", args[args.indexOf("-c:a") + 1])
        assertTrue(args.contains("0:a?"))
        assertTrue(args.contains("yuv420p"))
        assertEquals(output, args.last())
        assertFalse("The app's FFmpeg build does not include GPL libx264", args.contains("libx264"))
    }

    @Test
    fun fallbackOnlyRunsForReadableCadenceOnlyH264SixtyFpsMismatch() {
        val config = ExportEngine.ExportConfig(resolution = "720p", fps = 60)
        val underCadence = auditEncodedFrameTimestamps(
            timestampsUs = (0 until 72).map { it * 25_000L },
            outputDurationMs = 1_800L,
            requestedFps = 60
        ).copy(videoMimeType = MimeTypes.VIDEO_H264, videoWidth = 1280, videoHeight = 720)

        assertTrue(underCadence.strict60FpsCadenceError != null)
        assertTrue(ExportCfr60FpsNormalizer.shouldNormalize(config, underCadence))

        val verified = auditEncodedFrameTimestamps(
            timestampsUs = (0 until 108).map { it * 16_667L },
            outputDurationMs = 1_800L,
            requestedFps = 60
        ).copy(videoMimeType = MimeTypes.VIDEO_H264, videoWidth = 1280, videoHeight = 720)
        assertFalse(ExportCfr60FpsNormalizer.shouldNormalize(config, verified))
        assertFalse(ExportCfr60FpsNormalizer.shouldNormalize(config.copy(fps = 30), underCadence))
        assertFalse(ExportCfr60FpsNormalizer.shouldNormalize(config.copy(videoMimeType = MimeTypes.VIDEO_H265), underCadence))
        assertFalse(ExportCfr60FpsNormalizer.shouldNormalize(config.copy(resolution = "8K"), underCadence))
        assertFalse(
            ExportCfr60FpsNormalizer.shouldNormalize(
                config,
                underCadence.copy(videoMimeType = MimeTypes.VIDEO_H265)
            )
        )
        assertFalse(
            ExportCfr60FpsNormalizer.shouldNormalize(
                config,
                underCadence.copy(duplicateTimestampCount = 1)
            )
        )
        assertFalse(
            ExportCfr60FpsNormalizer.shouldNormalize(
                config,
                underCadence.copy(transformerFrameCountDelta = -3L)
            )
        )
    }
}
