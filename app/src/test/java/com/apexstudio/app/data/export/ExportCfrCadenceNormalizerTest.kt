package com.apexstudio.app.data.export

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCfrCadenceNormalizerTest {
    @Test
    fun commandUsesExplicitCfrAndTheBundledOpenH264Encoder() {
        val input = "/storage/emulated/0/clip with spaces.mp4"
        val output = "/storage/emulated/0/verified 60fps copy.mp4"
        val args = ExportCfrCadenceNormalizer.buildArguments(input, output, 6_000_000).toList()

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
    fun commandUsesTheRequestedCadenceRatherThanAlwaysForcingSixty() {
        val args = ExportCfrCadenceNormalizer.buildArguments(
            inputPath = "/tmp/input.mp4",
            outputPath = "/tmp/output.mp4",
            targetBitrate = 6_000_000,
            targetFps = 30
        )

        assertEquals("fps=30", args[args.indexOf("-vf") + 1])
        assertEquals("cfr", args[args.indexOf("-fps_mode") + 1])
    }

    @Test
    fun audioTimingRejectsMissingSourceDurationInsteadOfSkippingSyncVerification() {
        val failure = runCatching {
            ExportCfrCadenceNormalizer.verifyAudioTrackTiming(
                sourceDurationUs = null,
                candidateDurationUs = 1_800_000L,
                sourceVideoDurationMs = 1_800L,
                candidateVideoDurationMs = 1_800L
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("source audio duration"))
    }

    @Test
    fun audioTimingRejectsShiftedCandidateAndAcceptsPreservedSourceDrift() {
        val preserved = runCatching {
            ExportCfrCadenceNormalizer.verifyAudioTrackTiming(
                sourceDurationUs = 1_810_000L,
                candidateDurationUs = 1_810_000L,
                sourceVideoDurationMs = 1_800L,
                candidateVideoDurationMs = 1_800L
            )
        }
        assertTrue(preserved.isSuccess)

        val shifted = runCatching {
            ExportCfrCadenceNormalizer.verifyAudioTrackTiming(
                sourceDurationUs = 1_810_000L,
                candidateDurationUs = 2_010_000L,
                sourceVideoDurationMs = 1_800L,
                candidateVideoDurationMs = 1_800L
            )
        }.exceptionOrNull()
        assertTrue(shifted is IllegalStateException)
        assertTrue(shifted?.message.orEmpty().contains("duration or A/V synchronization"))
    }

    @Test
    fun fallbackOnlyRunsForReadableCadenceOnlyH264SixtyFpsMismatch() {
        val config = ExportEngine.ExportConfig(
            resolution = "720p", fps = 60, videoMimeType = MimeTypes.VIDEO_H264
        )
        val underCadence = auditEncodedFrameTimestamps(
            timestampsUs = (0 until 72).map { it * 25_000L },
            outputDurationMs = 1_800L,
            requestedFps = 60
        ).copy(videoMimeType = MimeTypes.VIDEO_H264, videoWidth = 1280, videoHeight = 720)

        assertTrue(underCadence.strict60FpsCadenceError != null)
        assertTrue(ExportCfrCadenceNormalizer.shouldNormalize(config, underCadence))

        val verified = auditEncodedFrameTimestamps(
            timestampsUs = (0 until 108).map { it * 16_667L },
            outputDurationMs = 1_800L,
            requestedFps = 60
        ).copy(videoMimeType = MimeTypes.VIDEO_H264, videoWidth = 1280, videoHeight = 720)
        assertFalse(ExportCfrCadenceNormalizer.shouldNormalize(config, verified))
        assertFalse(ExportCfrCadenceNormalizer.shouldNormalize(config.copy(fps = 30), underCadence))
        assertFalse(ExportCfrCadenceNormalizer.shouldNormalize(config.copy(videoMimeType = MimeTypes.VIDEO_H265), underCadence))
        assertFalse(ExportCfrCadenceNormalizer.shouldNormalize(config.copy(resolution = "8K"), underCadence))
        assertFalse(
            ExportCfrCadenceNormalizer.shouldNormalize(
                config,
                underCadence.copy(videoMimeType = MimeTypes.VIDEO_H265)
            )
        )
        assertFalse(
            ExportCfrCadenceNormalizer.shouldNormalize(
                config,
                underCadence.copy(duplicateTimestampCount = 1)
            )
        )
        assertFalse(
            ExportCfrCadenceNormalizer.shouldNormalize(
                config,
                underCadence.copy(transformerFrameCountDelta = -3L)
            )
        )
    }

    @Test
    fun thirtyFpsFallbackRequiresVerifiedFasterCfrSourceAndRepairsTheStrictUnderCadenceCase() {
        val config = ExportEngine.ExportConfig(
            resolution = "720p", fps = 30, videoMimeType = MimeTypes.VIDEO_H264
        )
        val underCadenceTimestamps = listOf(0L, 100_000L) + (4L..28L).map { it * 33_333L }
        val underCadence = auditEncodedFrameTimestamps(
            timestampsUs = underCadenceTimestamps,
            outputDurationMs = 966L,
            requestedFps = 30
        ).copy(videoMimeType = MimeTypes.VIDEO_H264, videoWidth = 1280, videoHeight = 720)
        assertEquals(27, underCadence.sampleCount)
        assertTrue(ExportCfrCadenceNormalizer.targetCadenceError(underCadence, 30) != null)
        assertFalse(ExportCfrCadenceNormalizer.shouldNormalize(config, underCadence))
        assertTrue(
            ExportCfrCadenceNormalizer.shouldNormalize(
                config, underCadence, verifiedFasterCfrSource = true
            )
        )

        val repairedCadence = auditEncodedFrameTimestamps(
            timestampsUs = (0L..28L).map { it * 33_333L },
            outputDurationMs = 966L,
            requestedFps = 30
        ).copy(videoMimeType = MimeTypes.VIDEO_H264, videoWidth = 1280, videoHeight = 720)
        assertEquals(29, repairedCadence.sampleCount)
        assertEquals(null, ExportCfrCadenceNormalizer.targetCadenceError(repairedCadence, 30))
        assertFalse(
            ExportCfrCadenceNormalizer.shouldNormalize(
                config, repairedCadence, verifiedFasterCfrSource = true
            )
        )
    }
}
