package com.apexstudio.app.data.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Real FFmpeg smoke coverage; compiled with Android tests and executed on a supported device. */
@RunWith(AndroidJUnit4::class)
class RangeTimeRendererInstrumentedTest {
    @Test fun bundledEncoderRegistersOpenH264() {
        val session = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(arrayOf("-hide_banner", "-encoders"))
        assertTrue("This APK must provide the configured time-render encoder", session.output.orEmpty().contains("libopenh264"))
    }

    @Test fun allOperationsRenderWholeAudioClipAndPreserveOriginal() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val input = File.createTempFile("range-time-audio-", ".mp4", context.cacheDir)
        val outputs = mutableListOf<File>()
        try {
            instrumentation.context.assets.open("video_upscale/source_with_audio.mp4").use { asset ->
                input.outputStream().use { asset.copyTo(it) }
            }
            val originalHash = sha256(input)
            val clip = MediaClip(
                id = "range-time-smoke-audio", name = "fixture", uri = Uri.fromFile(input).toString(),
                durationMs = 1_000L, trimStartMs = 0L, trimEndMs = 1_000L, type = ClipType.VIDEO
            )
            for (operation in RangeTimeOperation.entries) {
                val result = RangeTimeRenderer.render(context, clip, 200L, 700L, operation)
                val output = File(Uri.parse(result.uri).path!!)
                outputs += output
                assertTrue("Derived result should be durable in filesDir", output.absolutePath.startsWith(context.filesDir.absolutePath))
                assertTrue(output.isFile && output.length() > 0L)
                val expectedDuration = if (operation == RangeTimeOperation.REVERSE) 1_000L else 1_500L
                assertTrue("Unexpected duration for $operation: ${result.durationMs}ms", kotlin.math.abs(result.durationMs - expectedDuration) <= 150L)
                assertTrue("The source audio track must survive $operation", hasTrack(output, "audio/"))
            }
            assertEquals("Each applied operation returns a distinct cached output", 3, outputs.map { it.absolutePath }.toSet().size)
            assertTrue("Original clip bytes must remain unchanged", originalHash.contentEquals(sha256(input)))
        } finally {
            input.delete()
            outputs.forEach { it.delete() }
        }
    }

    @Test fun repeatRendersSilentVideoWithoutInventingAnAudioStream() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val input = File.createTempFile("range-time-silent-", ".mp4", context.cacheDir)
        var output: File? = null
        try {
            instrumentation.context.assets.open("audio/overlay_silent_base.mp4").use { asset ->
                input.outputStream().use { asset.copyTo(it) }
            }
            assertFalse(hasTrack(input, "audio/"))
            val clip = MediaClip(
                id = "range-time-smoke-silent", name = "silent fixture", uri = Uri.fromFile(input).toString(),
                durationMs = 1_000L, trimStartMs = 0L, trimEndMs = 1_000L, type = ClipType.VIDEO
            )
            val result = RangeTimeRenderer.render(context, clip, 200L, 700L, RangeTimeOperation.REPEAT)
            output = File(Uri.parse(result.uri).path!!)
            assertNotEquals(input.absolutePath, output.absolutePath)
            assertTrue(output.isFile && result.durationMs in 1_400L..1_600L)
            assertFalse("Silent input stays silent", hasTrack(output, "audio/"))
        } finally {
            input.delete()
            output?.delete()
        }
    }

    private fun hasTrack(file: File, mimePrefix: String): Boolean {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            return (0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(mimePrefix)
            }
        } finally {
            extractor.release()
        }
    }

    private fun sha256(file: File): ByteArray = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
}
