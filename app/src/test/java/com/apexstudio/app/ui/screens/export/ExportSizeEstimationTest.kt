package com.apexstudio.app.ui.screens.export

import com.apexstudio.app.data.export.ProjectCompositionTimeline
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSizeEstimationTest {
    private val first = MediaClip("first", "first", "file:///first.mp4", 10_000L, trimEndMs = 10_000L)
    private val second = first.copy(
        id = "second",
        trimStartMs = 2_000L,
        trimEndMs = 8_000L,
        speedMultiplier = 2f
    )

    @Test fun videoPayloadScalesWithRenderedDurationAndRequestedBitrate() {
        assertEquals(1_000_000L, estimateVideoPayloadBytes(1_000L, 8_000_000))
        assertEquals(2_000_000L, estimateVideoPayloadBytes(2_000L, 8_000_000))
        assertEquals(2_000_000L, estimateVideoPayloadBytes(1_000L, 16_000_000))
        assertNull(estimateVideoPayloadBytes(0L, 8_000_000))
        assertNull(estimateVideoPayloadBytes(1_000L, 0))
    }

    @Test fun selectedClipAndWholeProjectUseDifferentRenderedDurations() {
        val selectedDurationMs = second.effectiveDurationMs()
        val projectDurationMs = ProjectCompositionTimeline.build(listOf(first, second)).durationMs
        assertEquals(3_000L, selectedDurationMs)
        assertEquals(13_000L, projectDurationMs)
        assertEquals(3_000_000L, estimateVideoPayloadBytes(selectedDurationMs, 8_000_000))
        assertEquals(13_000_000L, estimateVideoPayloadBytes(projectDurationMs, 8_000_000))
    }

    @Test fun unknownAudioBitrateNeverBecomesAFalseTotalAndKnownAudioScales() {
        val unknownAudio = estimateExportPayload(1_000L, 8_000_000, audioExpected = true)
        assertEquals(1_000_000L, unknownAudio?.videoBytes)
        assertNull(unknownAudio?.audioBytes)
        assertNull(unknownAudio?.totalPayloadBytes)

        val configuredAudio = estimateExportPayload(
            1_000L, 8_000_000, audioExpected = true, audioBitrateBps = 128_000
        )
        assertEquals(16_000L, configuredAudio?.audioBytes)
        assertEquals(1_016_000L, configuredAudio?.totalPayloadBytes)

        val noAudio = estimateExportPayload(1_000L, 8_000_000, audioExpected = false)
        assertEquals(0L, noAudio?.audioBytes)
        assertEquals(1_000_000L, noAudio?.totalPayloadBytes)
    }

    @Test fun actualZeroIsMeasuredButMissingAndUnknownSizesStayUnavailable() {
        assertEquals(ActualOutputSize.Measured(0L), actualFileSize(exists = true, isRegularFile = true, bytes = 0L))
        assertEquals("0 B", formatByteCount(0L))
        assertTrue(actualFileSize(exists = false, isRegularFile = false, bytes = -1L) is ActualOutputSize.Unavailable)
        assertTrue(actualContentSize(null) is ActualOutputSize.Unavailable)
        assertTrue(actualContentSize(-1L) is ActualOutputSize.Unavailable)
    }

    @Test fun deniedContentUriIsUnavailableRatherThanZeroBytes() {
        val result = safelyReadActualContentSize { throw SecurityException("permission denied") }
        assertEquals(
            ActualOutputSize.Unavailable("Access to the completed file was denied."),
            result
        )
    }
    @Test fun megabytesUseDecimalUnitsConsistentWithMbps() {
        assertEquals("1.0 MB", formatByteCount(1_000_000L))
        assertEquals("90.2 MB", formatByteCount(90_189_899L))
    }

}
