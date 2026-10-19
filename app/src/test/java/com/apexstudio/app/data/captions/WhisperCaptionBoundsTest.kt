package com.apexstudio.app.data.captions

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperCaptionBoundsTest {
    @Test fun trimTimesAreClippedWithoutLongOverflow() {
        assertEquals(0, sampleIndexAtTime(-1L, 16_000, 100))
        assertEquals(16, sampleIndexAtTime(1L, 16_000, 100))
        assertEquals(100, sampleIndexAtTime(Long.MAX_VALUE, 16_000, 100))
    }

    @Test fun timestampTokensStayMonotoneAndWithinShortChunk() {
        assertEquals(500L, constrainWhisperTimestamp(400L, 500L, 750L))
        assertEquals(750L, constrainWhisperTimestamp(30_000L, 200L, 750L))
        assertEquals(0L, constrainWhisperTimestamp(-20L, 0L, 750L))
    }
}
