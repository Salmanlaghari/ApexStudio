package com.apexstudio.app.data.export

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apexstudio.app.data.audio.PcmAudioExtractor
import com.apexstudio.app.domain.model.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.sqrt

/** Exercises the native extractor path used by project audio mixing. */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class MusicExportMixerFileUriInstrumentedTest {
    @Test
    fun fileUriWithEscapedSpaceIsDecodedAndMixed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val base = File.createTempFile("music_uri_base_", ".mp4", context.cacheDir)
        val source = File(context.cacheDir, "music source ${UUID.randomUUID()}.m4a")
        val output = File(context.cacheDir, "music_uri_result_${UUID.randomUUID()}.mp4")

        fun copyAsset(asset: String, target: File) {
            instrumentation.context.assets.open("audio/$asset").use { input ->
                target.outputStream().use { outputStream -> input.copyTo(outputStream) }
            }
        }

        try {
            copyAsset("overlay_silent_base.mp4", base)
            copyAsset("pcm_extractor_fixture.m4a", source)
            val sourceUri = Uri.fromFile(source).toString()
            assertTrue("The source URI must contain an encoded space: $sourceUri", sourceUri.contains("%20"))

            val result = MusicExportMixer.mixMusicIntoVideo(
                context = context,
                inputVideo = base,
                tracks = listOf(
                    AudioTrack(
                        id = "space-uri-track",
                        name = "Space URI source",
                        uri = sourceUri,
                        volume = 1f,
                        trimEndMs = 400L,
                        startTimeMs = 300L,
                        sourceDurationMs = 400L
                    )
                ),
                outputVideo = output
            )

            assertEquals("The file URI source should be decoded rather than skipped", 1, result.mixedTracks)
            assertEquals("No requested track should be skipped", 0, result.skippedTracks)
            assertTrue("The mixed output must exist", output.isFile && output.length() > 0L)

            val pcm = requireNotNull(PcmAudioExtractor.extractMono16k(context, Uri.fromFile(output)))
            fun rms(startMs: Int, endMs: Int): Double {
                val samples = pcm.sliceArray((startMs * 16) until minOf(endMs * 16, pcm.size))
                require(samples.isNotEmpty())
                return sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
            }
            assertTrue("The placed source tone should be present in the mixed output", rms(350, 550) > 0.01)
        } finally {
            base.delete()
            source.delete()
            output.delete()
        }
    }
}
