package com.apexstudio.app.data.export

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apexstudio.app.data.audio.PcmAudioExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.sqrt

/** Native decoder/encoder/mux acceptance. Compiling this test is not running it. */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class OverlayAudioExportInstrumentedTest {
    @Test fun regularVideoExportContainsPlacedOverlayToneAndLeadingSilence() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engine = ExportEngine(context)
        val base = File.createTempFile("overlay_base_", ".mp4", context.cacheDir)
        val tone = File.createTempFile("overlay_tone_", ".m4a", context.cacheDir)
        val name = "overlay_acceptance_${UUID.randomUUID()}.mp4"
        val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
        var output: File? = null
        fun copyAsset(asset: String, target: File) {
            instrumentation.context.assets.open("audio/$asset").use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        try {
            copyAsset("overlay_silent_base.mp4", base)
            copyAsset("pcm_extractor_fixture.m4a", tone)
            engine.startExport(
                Uri.fromFile(base).toString(),
                ExportEngine.ExportConfig(
                    resolution = "720p", fps = 10, trimEndMs = 1_000L,
                    // Base mute/speed must not leak onto independently placed overlay audio.
                    volume = 0f,
                    overlayAudioSources = listOf(ExportEngine.AudioExportSource(
                        uri = Uri.fromFile(tone).toString(), trimEndMs = 400L,
                        timelinePositionMs = 300L, sourceGain = 0.5f,
                        constantSpeedOverride = 1f
                    ))
                ),
                name
            )
            val result = withTimeout(120_000L) {
                engine.exportState.first { it.error != null || it.outputUri != null }
            }
            assertNull("Export must complete without an error: ${result.error}", result.error)
            assertNull("No audio source should be omitted: ${result.warning}", result.warning)
            output = File(requireNotNull(Uri.parse(result.outputUri).path))
            assertTrue("Saved MP4 must exist", output!!.length() > 0L)
            val pcm = requireNotNull(PcmAudioExtractor.extractMono16k(context, Uri.fromFile(output)))
            fun rms(startMs: Int, endMs: Int): Double {
                val samples = pcm.sliceArray((startMs * 16) until minOf(endMs * 16, pcm.size))
                require(samples.isNotEmpty())
                return sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
            }
            assertTrue("Leading timeline gap should remain silent", rms(50, 200) < 0.005)
            assertTrue("Placed overlay tone must survive base mute and final MP4 mix", rms(350, 550) > 0.01)
            assertTrue("Mix should not extend beyond the one-second base video", pcm.size <= 19_200)
        } finally {
            instrumentation.runOnMainSync { engine.release() }
            base.delete()
            tone.delete()
            output?.delete()
            File(outputDir, name).delete()
            File(outputDir, name.removeSuffix(".mp4") + "_music.mp4").delete()
        }
    }
}
