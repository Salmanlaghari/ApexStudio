package com.apexstudio.app.data.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayFrameSelectionPolicyTest {
    @Test fun advancesThroughAllFramesWithinTheFastTargetWindow() {
        val eligibleFrames = listOf(0L, 33_000L, 66_000L, 99_000L)
            .filter {
                OverlayFrameSelectionPolicy.actionForOutput(it, targetTimeUs = 100_000L) ==
                    OverlayFrameSelectionPolicy.OutputAction.RENDER_AND_CONTINUE
            }

        assertEquals(listOf(0L, 33_000L, 66_000L, 99_000L), eligibleFrames)
        assertEquals(
            OverlayFrameSelectionPolicy.OutputAction.HOLD_AND_STOP,
            OverlayFrameSelectionPolicy.actionForOutput(134_000L, targetTimeUs = 100_000L)
        )
    }

    @Test fun futureFrameIsHeldUntilTargetReachesItInsteadOfBeingDiscarded() {
        val framePtsUs = 100_000L
        assertEquals(
            OverlayFrameSelectionPolicy.OutputAction.HOLD_AND_STOP,
            OverlayFrameSelectionPolicy.actionForOutput(framePtsUs, targetTimeUs = 60_000L)
        )
        assertEquals(
            OverlayFrameSelectionPolicy.OutputAction.RENDER_AND_CONTINUE,
            OverlayFrameSelectionPolicy.actionForOutput(framePtsUs, targetTimeUs = 67_000L)
        )
    }

    @Test fun reuseOfFutureToleranceFrameDoesNotLookLikeBackwardSeek() {
        assertTrue(OverlayFrameSelectionPolicy.canReuseFrame(80_000L, 100_000L, hasFrame = true))
        assertFalse(OverlayFrameSelectionPolicy.canReuseFrame(101_000L, 100_000L, hasFrame = true))
        assertFalse(OverlayFrameSelectionPolicy.canReuseFrame(80_000L, 100_000L, hasFrame = false))

        assertFalse(OverlayFrameSelectionPolicy.mustSeek(80_000L, previousTargetTimeUs = 60_000L))
        assertTrue(OverlayFrameSelectionPolicy.mustSeek(50_000L, previousTargetTimeUs = 60_000L))
        assertFalse(OverlayFrameSelectionPolicy.mustSeek(60_000L, previousTargetTimeUs = null))
    }

    @Test fun timestampConversionsSaturateInsteadOfWrapping() {
        assertEquals(Long.MAX_VALUE, OverlayFrameSelectionPolicy.millisecondsToMicroseconds(Long.MAX_VALUE))
        assertEquals(Long.MIN_VALUE, OverlayFrameSelectionPolicy.millisecondsToMicroseconds(Long.MIN_VALUE))
        assertEquals(
            Long.MAX_VALUE,
            OverlayFrameSelectionPolicy.sourceTimeUs(Long.MAX_VALUE, 1L)
        )
        assertEquals(5_001_000L, OverlayFrameSelectionPolicy.sourceTimeUs(1L, 5_000L))
    }
}
