package com.example

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apexstudio.app.data.media.SampleVideoGenerator
import com.apexstudio.app.data.trim.Media3VideoTrimmer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native codec integration belongs on-device, not in the Robolectric JVM suite. */
@RunWith(AndroidJUnit4::class)
class Media3VideoTrimmerInstrumentedTest {

    @Test
    fun `trims a real sample video to the requested interval`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceUri = withTimeout(120_000L) {
            SampleVideoGenerator.getOrCreateSampleVideo(context)
        }
        val outputName = "instrumented_trim_${System.currentTimeMillis()}.mp4"
        val trimmer = Media3VideoTrimmer(context)
        var success: Media3VideoTrimmer.TrimResult.Success? = null
        val request = Media3VideoTrimmer.TrimRequest(
            inputUri = sourceUri,
            startMs = 1_000L,
            endMs = 4_000L,
            clipId = "instrumented_clip",
            outputFileName = outputName,
        )

        try {
            withTimeout(120_000L) {
                trimmer.trimVideo(request).collect { result ->
                    if (result is Media3VideoTrimmer.TrimResult.Success) success = result
                }
            }

            val result = success
            assertNotNull("Media3 must complete a trim for a valid encoded video", result)
            assertEquals(3_000L, result?.durationMs)
            assertEquals(1_000L, result?.startMs)
            assertEquals(4_000L, result?.endMs)
            assertEquals("instrumented_clip", result?.clipId)
            assertTrue("Output URI must not be empty", !result?.outputUri.isNullOrEmpty())

            val outputFile = File(Uri.parse(requireNotNull(result).outputUri).path!!)
            assertTrue("Trimmed file must exist and contain encoded data", outputFile.isFile && outputFile.length() > 0L)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(outputFile.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                assertNotNull("Output must be a readable video with a duration", duration)
                assertTrue(
                    "Encoded output should be close to the requested 3-second interval, got ${duration}ms",
                    kotlin.math.abs(requireNotNull(duration) - 3_000L) <= 500L,
                )
            } finally {
                retriever.release()
            }
        } finally {
            trimmer.cancelActiveTrim()
            File(context.filesDir, "trimmed_clips/$outputName").delete()
        }
    }
}
