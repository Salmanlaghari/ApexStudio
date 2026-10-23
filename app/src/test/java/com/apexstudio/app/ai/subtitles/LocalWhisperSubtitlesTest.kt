package com.apexstudio.app.ai.subtitles

import com.apexstudio.app.data.captions.WhisperCaptionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWhisperSubtitlesTest {
    @Test fun whisperWordTokensKeepThePhraseIntervalAndDeclareCoarsePrecision() {
        val segment = WhisperCaptionEngine.CaptionSegment("Hello, careful world!", 125L, 980L)
        val cues = LocalWhisperSubtitles.coarseWordCues(segment)
        assertEquals(listOf("Hello,", "careful", "world!"), cues.map { it.text })
        assertTrue(cues.all { it.startMs == 125L && it.endMs == 980L })
        assertTrue(cues.all { it.precision == TimingPrecision.WHISPER_SEGMENT })
    }
}
