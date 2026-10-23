package com.apexstudio.app.ai.subtitles

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apexstudio.app.data.captions.WhisperCaptionEngine
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Compiled-only device smoke test; executes bundled CPU inference when explicitly run on Android. */
@RunWith(AndroidJUnit4::class)
class EnglishWordAlignmentInstrumentedTest {
    @Test fun alignsNaturalEnglishFixtureIntoBoundedWordLevelCues() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "english_alignment_fixture.wav")
        instrumentation.context.assets.open("audio/english_alignment_fixture.wav").use { input ->
            fixture.outputStream().use { output -> input.copyTo(output) }
        }
        try {
            val transcript = "MISTER QUILTER IS THE APOSTLE OF THE MIDDLE CLASSES AND WE ARE GLAD TO WELCOME HIS GOSPEL"
            val cues = EnglishWordAlignment.align(
                context = context,
                uri = Uri.fromFile(fixture),
                segments = listOf(WhisperCaptionEngine.CaptionSegment(transcript, 0L, 5_855L)),
                sourceStartMs = 0L,
                sourceEndMs = 5_855L,
            )
            assertEquals(transcript.split(' '), cues.map { it.text })
            assertEquals(17, cues.size)
            assertTrue(cues.all {
                it.precision == TimingPrecision.CTC_FORCED_WORD_ALIGNMENT &&
                    it.startMs in 0L..5_855L && it.endMs in 0L..5_855L && it.startMs < it.endMs &&
                    (it.confidence ?: 0f) >= 0.08f
            })
            assertTrue(cues.map { it.endMs - it.startMs }.distinct().size > 1)
        } finally {
            fixture.delete()
        }
    }
}
