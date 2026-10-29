package com.apexstudio.app.data.picker

import android.net.Uri
import com.apexstudio.app.domain.model.ClipType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class MediaMetadataBatchTest {
    @Test
    fun `batch preserves picker order and never exceeds two extraction jobs`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val uris = (0 until 5).map { Uri.parse("content://media/$it") }

        val result = extractMetadataBatch(
            uris = uris,
            dispatcher = dispatcher,
            nowMs = { testScheduler.currentTime },
            extractor = { uri ->
                val current = active.incrementAndGet()
                maximum.updateAndGet { old -> maxOf(old, current) }
                delay(if (uri.lastPathSegment == "0") 30L else 5L)
                active.decrementAndGet()
                metadata(uri)
            }
        )

        assertEquals(uris.map(Uri::toString), result.map(MediaMetadata::uri))
        assertEquals(2, maximum.get())
    }

    @Test
    fun `cancellation stops batch and does not produce a partial result`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val published = AtomicReference<List<MediaMetadata>?>(null)
        val uris = (0 until 4).map { Uri.parse("content://media/$it") }
        val job = async {
            try {
                val result = extractMetadataBatch(
                    uris = uris,
                    dispatcher = dispatcher,
                    nowMs = { testScheduler.currentTime },
                    extractor = { uri ->
                        delay(100L)
                        metadata(uri)
                    }
                )
                published.set(result)
            } catch (_: CancellationException) {
                // Expected: callers must not publish cancelled batches.
            }
        }

        testScheduler.runCurrent()
        job.cancelAndJoin()
        assertEquals(null, published.get())
    }

    @Test
    fun `progress text uses plain user-facing wording`() = runTest {
        val progress = MediaImportProgress(running = true, completed = 1, total = 3, elapsedMs = 42L)
        assertEquals("Importing media (1 of 3)…", progress.text)
        assertTrue(MediaImportProgress.IDLE.text.isEmpty())
    }

    private fun metadata(uri: Uri) = MediaMetadata(
        uri = uri.toString(), name = uri.lastPathSegment.orEmpty(), durationMs = 1L,
        width = 1, height = 1, fps = 30, type = ClipType.VIDEO
    )
}
