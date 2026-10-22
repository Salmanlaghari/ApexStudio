package com.example

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apexstudio.app.data.media.SampleVideoGenerator
import com.apexstudio.app.data.trim.Media3VideoTrimmer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises Transformer Looper ownership and per-flow cancellation with native Media3 codecs. */
@RunWith(AndroidJUnit4::class)
class Media3VideoTrimmerConcurrencyTest {

    @Test
    fun cancellingOneConcurrentTrimDoesNotCancelItsSibling() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceUri = withTimeout(120_000L) {
            SampleVideoGenerator.getOrCreateSampleVideo(context)
        }
        val suffix = System.currentTimeMillis()
        val firstName = "concurrent_trim_cancelled_$suffix.mp4"
        val secondName = "concurrent_trim_survivor_$suffix.mp4"
        val trimmer = Media3VideoTrimmer(context)
        val firstProgress = CompletableDeferred<Unit>()
        val secondProgress = CompletableDeferred<Unit>()
        val firstFinished = CompletableDeferred<Unit>()
        val survivorFinished = CompletableDeferred<Unit>()

        try {
            coroutineScope {
                val cancelled = launch {
                    trimmer.trimVideo(
                        Media3VideoTrimmer.TrimRequest(sourceUri, 1_000L, 4_000L, outputFileName = firstName),
                    ).collect { result ->
                        if (result is Media3VideoTrimmer.TrimResult.Progress && result.progress in 0f..0.99f) {
                            firstProgress.complete(Unit)
                        }
                        if (result is Media3VideoTrimmer.TrimResult.Success || result is Media3VideoTrimmer.TrimResult.Error) {
                            firstFinished.complete(Unit)
                        }
                    }
                }
                val survivor = async {
                    var success: Media3VideoTrimmer.TrimResult.Success? = null
                    trimmer.trimVideo(
                        Media3VideoTrimmer.TrimRequest(sourceUri, 1_000L, 4_000L, outputFileName = secondName),
                    ).collect { result ->
                        if (result is Media3VideoTrimmer.TrimResult.Progress && result.progress in 0f..0.99f) {
                            secondProgress.complete(Unit)
                        }
                        if (result is Media3VideoTrimmer.TrimResult.Success) success = result
                        if (result is Media3VideoTrimmer.TrimResult.Success || result is Media3VideoTrimmer.TrimResult.Error) {
                            survivorFinished.complete(Unit)
                        }
                    }
                    success
                }

                withTimeout(30_000L) {
                    firstProgress.await()
                    secondProgress.await()
                }
                assertTrue("Both native trims must still be running at the cancellation point", !firstFinished.isCompleted && !survivorFinished.isCompleted)
                cancelled.cancelAndJoin()

                val success = withTimeout(120_000L) { survivor.await() }
                assertNotNull("Cancelling a sibling must not stop this trim", success)
                val completed = requireNotNull(success)
                assertTrue(completed.outputUri.endsWith(secondName))
                assertTrimmedVideoIsReadable(completed.outputUri, expectedDurationMs = 3_000L)
            }
        } finally {
            trimmer.cancelActiveTrim()
            File(context.filesDir, "trimmed_clips/$firstName").delete()
            File(context.filesDir, "trimmed_clips/$secondName").delete()
        }
    }

    private fun assertTrimmedVideoIsReadable(outputUri: String, expectedDurationMs: Long) {
        val outputFile = File(android.net.Uri.parse(outputUri).path ?: error("Output URI has no path"))
        assertTrue("Transformer must produce a non-empty output file", outputFile.isFile && outputFile.length() > 1_000L)

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(outputFile.absolutePath)
            var hasVideoTrack = false
            for (index in 0 until extractor.trackCount) {
                val format: MediaFormat = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    hasVideoTrack = true
                }
            }
            assertTrue("Output must contain a readable video track", hasVideoTrack)
        } finally {
            extractor.release()
        }

        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(outputFile.absolutePath)
            val actualDurationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: error("Output duration metadata is missing")
            assertTrue(
                "Output duration $actualDurationMs ms must reflect the requested $expectedDurationMs ms trim, not the 10 s source",
                actualDurationMs in (expectedDurationMs - 1_000L)..(expectedDurationMs + 1_000L),
            )
        } finally {
            retriever.release()
        }
    }
}
