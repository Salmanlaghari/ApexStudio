package com.apexstudio.app.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Must run on Android; compilation alone is not a native extractor acceptance result. */
@RunWith(AndroidJUnit4::class)
class AudioExportSourceProbeInstrumentedTest {
    @Test fun audioContainerIsAcceptedAndImageIsNot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val audio = File.createTempFile("audio_probe_", ".m4a", context.cacheDir)
        val image = File.createTempFile("image_probe_", ".png", context.cacheDir)
        try {
            assets.open("audio/pcm_extractor_fixture.m4a").use { input ->
                audio.outputStream().use { output -> input.copyTo(output) }
            }
            assets.open("super_resolution/espcn_fixture.png").use { input ->
                image.outputStream().use { output -> input.copyTo(output) }
            }
            assertTrue(AudioExportSourceProbe.hasAudio(context, audio.absolutePath))
            assertTrue(AudioExportSourceProbe.hasAudio(context, audio.toURI().toString()))
            assertFalse(AudioExportSourceProbe.hasAudio(context, image.absolutePath))
        } finally {
            audio.delete()
            image.delete()
        }
    }
}
