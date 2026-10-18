package com.apexstudio.app.ai.subtitles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CtcAlignmentBoundsTest {
    @Test fun alignmentChecksCancellationWhileWorking() {
        var checks = 0
        try {
            CtcForcedAligner.align(
                logProbabilities = arrayOf(floatArrayOf(0f, -1f), floatArrayOf(-1f, 0f)),
                blankId = 0,
                targetTokenIds = intArrayOf(1),
                wordIndexForToken = intArrayOf(0),
                wordTexts = listOf("x"),
                frameDurationMs = 20f,
                checkCancelled = { checks++; throw java.util.concurrent.CancellationException("cancel") },
            )
            throw AssertionError("Expected alignment cancellation")
        } catch (_: java.util.concurrent.CancellationException) {
            org.junit.Assert.assertTrue(checks > 0)
        }
    }

    @Test fun viterbiBackpointerAllocationHasAHardCellBudget() {
        assertTrue(isCtcAlignmentWithinBounds(frameCount = 1_000, tokenCount = 100))
        assertFalse(isCtcAlignmentWithinBounds(frameCount = 2_000_000, tokenCount = 1))
        assertFalse(isCtcAlignmentWithinBounds(frameCount = Int.MAX_VALUE, tokenCount = Int.MAX_VALUE))
        assertFalse(isCtcAlignmentWithinBounds(frameCount = 0, tokenCount = 1))
    }
}
