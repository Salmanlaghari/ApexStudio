package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.ai.audio.AudioIntelligence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorAudioIntelligenceActionsTest {
    @Test
    fun retainedRangesAreClippedToTrackTrimAndRemainSourceRelative() {
        val ranges = retainedAudioRanges(
            intervals = listOf(
                AudioIntelligence.Interval(0, 8),
                AudioIntelligence.Interval(12, 20),
                AudioIntelligence.Interval(19, 30)
            ),
            sampleRateHz = 1_000,
            trimStartMs = 5L,
            trimEndMs = 25L
        )

        assertEquals(listOf(RetainedAudioRange(5L, 8L), RetainedAudioRange(12L, 25L)), ranges)
    }

    @Test
    fun retainedRangesRejectInvalidRateAndEmptyTrim() {
        val interval = AudioIntelligence.Interval(0, 10_000)
        assertTrue(retainedAudioRanges(listOf(interval), 0, 0L, 10L).isEmpty())
        assertTrue(retainedAudioRanges(listOf(interval), 1_000, 10L, 10L).isEmpty())
    }
}
