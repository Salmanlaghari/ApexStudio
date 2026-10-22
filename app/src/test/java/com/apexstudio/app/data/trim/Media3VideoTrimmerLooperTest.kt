package com.apexstudio.app.data.trim

import android.content.Context
import android.os.Looper
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Media3VideoTrimmerLooperTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun transformerStartProgressAndCancelUseMainLooperWhenCollectedFromDefaultAndIo(): Unit = runBlocking {
        for (dispatcher in listOf(Dispatchers.Default, Dispatchers.IO)) {
            val factory = RecordingFactory()
            val trimmer = Media3VideoTrimmer(context, factory)
            val progressSeen = CompletableDeferred<Unit>()
            val input = sourceFile()
            val collection = async(dispatcher) {
                trimmer.trimVideo(request(input, "looper_${dispatcher.hashCode()}.mp4")).collect { result ->
                    if (result is Media3VideoTrimmer.TrimResult.Progress && result.progress < 1f) {
                        progressSeen.complete(Unit)
                    }
                }
            }

            pumpUntil { factory.adapters.size == 1 && factory.adapters.first().started.isCompleted }
            pumpUntil { progressSeen.isCompleted }
            collection.cancelAndJoin()
            val adapter = factory.adapters.single()
            pumpUntil { adapter.cancelled.isCompleted }

            assertTrue("Factory creation must be on the application Looper", factory.onMainLooper)
            assertTrue("start/getProgress/cancel must be on the application Looper", adapter.allCallsOnMainLooper)
            assertTrue(adapter.progressCalls > 0)
            input.delete()
        }
    }

    @Test
    fun cancellingOneSessionDoesNotCancelSiblingAndSiblingCanComplete(): Unit = runBlocking {
        val factory = RecordingFactory()
        val trimmer = Media3VideoTrimmer(context, factory)
        val input = sourceFile()
        File(context.filesDir, "trimmed_clips").deleteRecursively()
        val first = async(Dispatchers.Default) {
            trimmer.trimVideo(request(input, "isolated_cancel.mp4")).collect { result ->
                check(result !is Media3VideoTrimmer.TrimResult.Error) { "First trim failed: $result" }
            }
        }
        val siblingResult = CompletableDeferred<Media3VideoTrimmer.TrimResult.Success>()
        val sibling = async(Dispatchers.IO) {
            trimmer.trimVideo(request(input, "isolated_survivor.mp4")).collect { result ->
                check(result !is Media3VideoTrimmer.TrimResult.Error) { "Sibling trim failed: $result" }
                if (result is Media3VideoTrimmer.TrimResult.Success) siblingResult.complete(result)
            }
        }

        pumpUntil { factory.adapters.size == 2 && factory.adapters.all { it.started.isCompleted } }
        val firstAdapter = factory.adapters.single { it.outputName == "isolated_cancel.mp4" }
        val siblingAdapter = factory.adapters.single { it.outputName == "isolated_survivor.mp4" }
        first.cancelAndJoin()
        pumpUntil { firstAdapter.cancelled.isCompleted }
        assertFalse("Cancelling one collection must not cancel the other Transformer", siblingAdapter.cancelled.isCompleted)

        siblingAdapter.complete()
        pumpUntil { siblingResult.isCompleted }
        sibling.await()
        assertTrue(siblingResult.await().outputUri.endsWith("isolated_survivor.mp4"))
        assertFalse(siblingAdapter.cancelled.isCompleted)
        assertTrue(firstAdapter.allCallsOnMainLooper)
        assertTrue(siblingAdapter.allCallsOnMainLooper)
        File(context.filesDir, "trimmed_clips/isolated_cancel.mp4").delete()
        File(context.filesDir, "trimmed_clips/isolated_survivor.mp4").delete()
        input.delete()
    }

    @Test
    fun startFailureEmitsErrorAndDeletesOnlyPartialOutput(): Unit = runBlocking {
        val factory = RecordingFactory().apply {
            onStart = { path -> File(path).writeText("not an mp4") }
            startFailure = IllegalStateException("codec initialization failed")
        }
        val trimmer = Media3VideoTrimmer(context, factory)
        val input = sourceFile()
        val originalBytes = input.readBytes()
        val name = "failed_trim_${System.nanoTime()}.mp4"
        val results = mutableListOf<Media3VideoTrimmer.TrimResult>()

        val collection = async(Dispatchers.IO) {
            trimmer.trimVideo(request(input, name)).collect(results::add)
        }
        pumpUntil { collection.isCompleted }
        collection.await()

        assertTrue(results.single() is Media3VideoTrimmer.TrimResult.Error)
        assertEquals(originalBytes.toList(), input.readBytes().toList())
        assertFalse(File(context.filesDir, "trimmed_clips/$name").exists())
        assertTrue(factory.adapters.single().allCallsOnMainLooper)
        input.delete()
    }

    @Test
    fun pollingFailureEmitsErrorRatherThanCrashingAnIndependentCoroutine(): Unit = runBlocking {
        val factory = RecordingFactory().apply {
            progressFailure = IllegalStateException("progress unavailable")
        }
        val input = sourceFile()
        val results = mutableListOf<Media3VideoTrimmer.TrimResult>()
        val collection = async(Dispatchers.Default) {
            Media3VideoTrimmer(context, factory).trimVideo(request(input, "poll_${System.nanoTime()}.mp4"))
                .collect(results::add)
        }
        pumpUntil { collection.isCompleted }
        collection.await()
        assertTrue(results.single() is Media3VideoTrimmer.TrimResult.Error)
        assertTrue(factory.adapters.single().cancelled.isCompleted)
        assertTrue(factory.adapters.single().allCallsOnMainLooper)
        input.delete()
    }

    private fun sourceFile(): File = File(context.cacheDir, "trim_test_${System.nanoTime()}.mp4").apply {
        writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))
    }

    private fun request(file: File, outputName: String) = Media3VideoTrimmer.TrimRequest(
        inputUri = file.absolutePath,
        startMs = 0L,
        endMs = 1_000L,
        outputFileName = outputName,
    )

    /** Drives Robolectric's main queue while a background collector awaits main-dispatched work. */
    private suspend fun pumpUntil(condition: () -> Boolean) {
        withTimeout(10_000L) {
            while (!condition()) {
                shadowOf(Looper.getMainLooper()).idleFor(20L, TimeUnit.MILLISECONDS)
                delay(5L)
            }
            shadowOf(Looper.getMainLooper()).idleFor(20L, TimeUnit.MILLISECONDS)
        }
    }

    private class RecordingFactory : TrimTransformerFactory {
        val adapters = CopyOnWriteArrayList<RecordingAdapter>()
        @Volatile var onMainLooper = false
        var onStart: ((String) -> Unit)? = null
        var startFailure: Throwable? = null
        var progressFailure: Throwable? = null

        override fun create(context: Context, callback: TrimTransformerCallback): TrimTransformerAdapter {
            onMainLooper = Looper.myLooper() == Looper.getMainLooper()
            return RecordingAdapter(callback, onStart, startFailure, progressFailure).also(adapters::add)
        }
    }

    private class RecordingAdapter(
        private val callback: TrimTransformerCallback,
        private val onStart: ((String) -> Unit)?,
        private val startFailure: Throwable?,
        private val progressFailure: Throwable?,
    ) : TrimTransformerAdapter {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        private val mainChecks = ConcurrentLinkedQueue<Boolean>()
        @Volatile var progressCalls = 0
            private set
        @Volatile private var outputPath: String? = null
        val allCallsOnMainLooper: Boolean get() = mainChecks.isNotEmpty() && mainChecks.all { it }
        val outputName: String? get() = outputPath?.let { File(it).name }

        override fun start(mediaItem: EditedMediaItem, path: String) {
            recordLooper()
            outputPath = path
            started.complete(Unit)
            onStart?.invoke(path)
            startFailure?.let { throw it }
        }

        override fun getProgress(progressHolder: ProgressHolder): Int {
            recordLooper()
            progressCalls++
            progressFailure?.let { throw it }
            progressHolder.progress = 40
            return Transformer.PROGRESS_STATE_AVAILABLE
        }

        override fun cancel() {
            recordLooper()
            cancelled.complete(Unit)
        }

        fun complete() {
            File(requireNotNull(outputPath)).writeText("fake output from the injected Transformer")
            callback.onCompleted()
        }

        private fun recordLooper() {
            mainChecks.add(Looper.myLooper() == Looper.getMainLooper())
        }
    }
}
