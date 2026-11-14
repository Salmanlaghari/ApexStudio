package com.apexstudio.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFrameRateValidationTest {
    @Test fun sourceMetadataDoesNotInventCadenceWhenUnknown() {
        val unknown = SourceVideoFormatAudit(null, null, null).diagnosticSummary()
        assertTrue(unknown.contains("sourceReportedFps=unknown"))
        assertTrue(unknown.contains("metadataOnly=true"))
        val known = SourceVideoFormatAudit(29.97f, 1920, 1080).diagnosticSummary()
        assertTrue(known.contains("sourceReportedFps=29.97"))
        assertTrue(known.contains("sourceWidth=1920"))
    }

    @Test
    fun sourceCadenceAuditAcceptsConstantSixtyFpsWithDecodeOrderReordering() {
        val orderedPts = (0 until 60).map { it * 16_667L }
        val decodeOrderPts = listOf(orderedPts[0], orderedPts[2], orderedPts[1]) + orderedPts.drop(3)

        val audit = auditSourceFrameTimestamps(decodeOrderPts, reportedFps = 60f)

        assertEquals(60, audit.sampleCount)
        assertTrue(audit.constantFrameRateFps!! in 59.5f..60.5f)
        assertEquals(16_667L, audit.minIntervalUs)
        assertEquals(16_667L, audit.maxIntervalUs)
    }

    @Test
    fun sourceCadenceAuditRejectsVfrAndNominalRateMismatch() {
        val vfr = auditSourceFrameTimestamps(
            timestampsUs = listOf(0L, 16_667L, 33_334L, 83_334L, 100_001L, 116_668L),
            reportedFps = 60f
        )
        val mismatch = auditSourceFrameTimestamps(
            timestampsUs = (0 until 30).map { it * 33_333L },
            reportedFps = 60f
        )

        assertEquals(null, vfr.constantFrameRateFps)
        assertEquals(50_000L, vfr.maxIntervalUs)
        assertEquals(null, mismatch.constantFrameRateFps)
    }

    @Test
    fun `reports actual encoded samples average rate and reference CFR count separately`() {
        // A 24 fps source exported at 30 fps can validly contain fewer than the
        // theoretical CFR reference count; that is not by itself evidence of drops.
        val audit = auditEncodedFrameTimestamps(
            timestampsUs = listOf(0L, 41_667L, 83_334L, 125_001L),
            outputDurationMs = 167L,
            requestedFps = 30,
            transformerFrameCount = 4
        )

        assertEquals(4, audit.sampleCount)
        assertEquals(6L, audit.expectedCfrSampleCount)
        assertEquals(4 * 1000.0 / 167.0, audit.encodedAverageFps!!, 0.0001)
        assertEquals(0L, audit.transformerFrameCountDelta)
        assertEquals(0, audit.largeTimestampGapCount)
        assertNull(audit.warning)
    }

    @Test
    fun `counts timestamp gaps and does not label them source frame drops`() {
        val audit = auditEncodedFrameTimestamps(
            timestampsUs = listOf(0L, 33_333L, 100_000L),
            outputDurationMs = 134L,
            requestedFps = 30
        )

        assertEquals(1, audit.largeTimestampGapCount)
        assertTrue(audit.maxTimestampGapUs > 66_666L)
        assertNull(audit.warning)
        assertTrue(audit.diagnosticSummary().contains("gapsOverOneAndHalfFrameIntervals=1"))
        assertFalse(audit.diagnosticSummary().contains("duplicateContent"))
    }

    @Test
    fun `valid B frame decode order regressions are not treated as corrupt timestamps`() {
        val audit = auditEncodedFrameTimestamps(
            timestampsUs = listOf(0L, 66_667L, 33_333L, 100_000L),
            outputDurationMs = 134L,
            requestedFps = 30,
            transformerFrameCount = 4
        )

        assertEquals(1, audit.decodeOrderTimestampRegressionCount)
        assertEquals(0, audit.duplicateTimestampCount)
        assertEquals(0L, audit.firstPresentationTimeUs)
        assertEquals(100_000L, audit.lastPresentationTimeUs)
        assertNull(audit.warning)
    }

    @Test
    fun `rejects duplicate and negative presentation timestamps`() {
        val audit = auditEncodedFrameTimestamps(
            timestampsUs = listOf(-1L, 33_333L, 33_333L, 20_000L),
            outputDurationMs = 100L,
            requestedFps = 30
        )

        assertEquals(1, audit.negativeTimestampCount)
        assertEquals(1, audit.duplicateTimestampCount)
        assertEquals(1, audit.decodeOrderTimestampRegressionCount)
        assertEquals("The exported video has invalid frame timestamps.", audit.warning)
    }

    @Test
    fun `flags substantial disagreement between muxed samples and renderer report`() {
        val audit = auditEncodedFrameTimestamps(
            timestampsUs = (0 until 100).map { it * 33_333L },
            outputDurationMs = 3_333L,
            requestedFps = 30,
            transformerFrameCount = 80
        )

        assertEquals(-20L, audit.transformerFrameCountDelta)
        assertEquals("The exported video frame count differs from the renderer's report.", audit.warning)
    }

    @Test
    fun `unknown duration leaves CFR expectation and average rate unknown`() {
        val audit = auditEncodedFrameTimestamps(
            timestampsUs = listOf(0L, 40_000L),
            outputDurationMs = 0L,
            requestedFps = 25
        )

        assertNull(audit.expectedCfrSampleCount)
        assertNull(audit.encodedAverageFps)
    }

    @Test
    fun `warns when actual encoded average rate exceeds the requested cap`() {
        val audit = auditEncodedFrameTimestamps(
            timestampsUs = (0 until 40).map { it * 25_000L },
            outputDurationMs = 1_000L,
            requestedFps = 30
        )

        assertEquals(40.0, audit.encodedAverageFps!!, 0.0001)
        assertEquals(
            "The encoded video frame rate exceeds the requested output rate.",
            audit.warning
        )
    }

    @Test
    fun `accepts measured 60 fps constant cadence`() {
        val audit = auditEncodedFrameTimestamps(
            timestampsUs = (0 until 60).map { it * 16_667L },
            outputDurationMs = 1_000L,
            requestedFps = 60
        )

        assertEquals(60, audit.sampleCount)
        assertEquals(60.0, audit.encodedAverageFps!!, 0.1)
        assertEquals(16_667L, audit.maxTimestampGapUs)
        assertEquals(16_667L, audit.minPositiveTimestampGapUs)
        assertNull(audit.strict60FpsCadenceError)
        assertNull(audit.warning)
    }

    @Test
    fun `rejects output below requested 60 fps`() {
        val audit = auditEncodedFrameTimestamps(
            timestampsUs = (0 until 30).map { it * 33_333L },
            outputDurationMs = 1_000L,
            requestedFps = 60
        )

        assertEquals(30.0, audit.encodedAverageFps!!, 0.1)
        assertTrue(requireNotNull(audit.warning).contains("did not meet the requested 60 fps cadence"))
    }

    @Test
    fun `rejects large VFR gap even when average sample count equals 60 fps`() {
        var timeUs = 0L
        val timestamps = buildList {
            add(timeUs)
            for (index in 1 until 60) {
                timeUs += if (index == 30) 50_000L else 16_000L
                add(timeUs)
            }
        }
        val audit = auditEncodedFrameTimestamps(
            timestampsUs = timestamps,
            outputDurationMs = 1_000L,
            requestedFps = 60
        )

        assertEquals(60.0, audit.encodedAverageFps!!, 0.1)
        assertEquals(50_000L, audit.maxTimestampGapUs)
        assertTrue(requireNotNull(audit.warning).contains("did not meet the requested 60 fps cadence"))
    }
}
