package com.apexstudio.app.data.audio

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PcmAudioExtractorIntegrationTest {
    @Test
    fun decodesAacFixtureToNonzeroMono16kSamples() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "pcm_extractor_fixture.m4a")
        instrumentation.context.assets.open("audio/pcm_extractor_fixture.m4a").use { input ->
            fixture.outputStream().use { output -> input.copyTo(output) }
        }

        try {
            val samples = requireNotNull(PcmAudioExtractor.extractMono16k(context, Uri.fromFile(fixture))) {
                "AAC fixture should decode"
            }
            assertTrue("half-second AAC should resample near 8k frames, got ${samples.size}", kotlin.math.abs(samples.size - 8_000) <= 1_000)
            assertTrue("decoded audio should contain the fixture sine tone", samples.any { kotlin.math.abs(it) > 0.02f })
            assertTrue("PCM output must be finite and normalized", samples.all { it.isFinite() && it in -1f..1f })
        } finally {
            fixture.delete()
        }
    }
}
