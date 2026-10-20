package com.apexstudio.app.data.video

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Compiled host-side; requires an actual supported Android codec device to execute. */
@RunWith(AndroidJUnit4::class)
class VideoUpscaleInstrumentedTest {
    @Test fun realVideoPipelineUpscalesEveryFrameAndPreservesAudioPackets() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val input = File.createTempFile("espcn-source-", ".mp4", context.cacheDir)
        val output = File(context.cacheDir, "espcn-output-${System.nanoTime()}.mp4")
        try {
            instrumentation.context.assets.open("video_upscale/source_with_audio.mp4").use { source ->
                input.outputStream().use { source.copyTo(it) }
            }
            val beforeVideo = track(input, "video/")
            val beforeAudio = track(input, "audio/")
            VideoUpscaleEngine.process(context, input.toURI().toString(), output, allowPublish = { true })
            val afterVideo = track(output, "video/")
            val afterAudio = track(output, "audio/")
            assertEquals(beforeVideo.width * 3, afterVideo.width)
            assertEquals(beforeVideo.height * 3, afterVideo.height)
            assertEquals("Every source video frame must be retained", beforeVideo.times, afterVideo.times)
            assertEquals("Audio packet timestamps must remain unchanged", beforeAudio.times, afterAudio.times)
            assertEquals("Audio payload is passthrough, not regenerated", beforeAudio.digest, afterAudio.digest)
            assertTrue("Shorter source audio remains shorter than processed video", afterAudio.durationUs < afterVideo.durationUs)
            assertTrue(output.length() > 0)
        } finally {
            input.delete()
            output.delete()
        }
    }

    @Test fun longerAacIsTrimmedToTheActualProcessedVideoDuration() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val input = File.createTempFile("espcn-long-audio-source-", ".mp4", context.cacheDir)
        val output = File(context.cacheDir, "espcn-long-audio-output-${System.nanoTime()}.mp4")
        try {
            instrumentation.context.assets.open("video_upscale/source_with_long_audio.mp4").use { source ->
                input.outputStream().use { source.copyTo(it) }
            }
            val beforeVideo = track(input, "video/")
            val beforeAudio = track(input, "audio/")
            assertTrue("Fixture must have an audio tail", beforeAudio.durationUs > beforeVideo.durationUs)

            VideoUpscaleEngine.process(context, input.toURI().toString(), output, allowPublish = { true })

            val afterVideo = track(output, "video/")
            val afterAudio = track(output, "audio/")
            assertTrue("Video track must report its processed duration", afterVideo.durationUs > 0L)
            assertTrue("Muxed AAC duration must not outlast the processed video", afterAudio.durationUs <= afterVideo.durationUs)
            assertTrue("Audio tail must be trimmed", afterAudio.times.size < beforeAudio.times.size)
            assertEquals("Trimmed audio remains a synchronized passthrough prefix",
                beforeAudio.times.take(afterAudio.times.size), afterAudio.times)
            assertTrue(output.length() > 0)
        } finally {
            input.delete()
            output.delete()
        }
    }

    private data class Track(val width: Int, val height: Int, val durationUs: Long, val times: List<Long>, val digest: String)

    private fun track(file: File, prefix: String): Track {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val index = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true
            }
            val format = extractor.getTrackFormat(index)
            extractor.selectTrack(index)
            val times = mutableListOf<Long>()
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            while (extractor.sampleTime >= 0) {
                buffer.clear()
                val count = extractor.readSampleData(buffer, 0)
                if (count < 0) break
                times += extractor.sampleTime
                digest.update(buffer.array(), 0, count)
                if (!extractor.advance()) break
            }
            return Track(
                if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0,
                if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0,
                if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L,
                times, digest.digest().joinToString("") { "%02x".format(it) }
            )
        } finally { extractor.release() }
    }
}
