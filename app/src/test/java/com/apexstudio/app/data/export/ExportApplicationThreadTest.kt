package com.apexstudio.app.data.export

import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Real Media3 thread checks; no hardware decoder/encoder acceptance is implied. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(UnstableApi::class)
class ExportApplicationThreadTest {
    private fun transformer() = Transformer.Builder(RuntimeEnvironment.getApplication())
        .setLooper(Looper.getMainLooper()).build()

    private fun engine() = ExportEngine(RuntimeEnvironment.getApplication())

    @Test
    fun `installed Media3 rejects worker-thread progress calls`() {
        val active = transformer()
        try {
            val failure = CompletableFuture.supplyAsync {
                runCatching { active.getProgress(ProgressHolder()) }.exceptionOrNull()
            }.get(5, TimeUnit.SECONDS)
            assertTrue(failure is IllegalStateException)
        } finally {
            active.cancel()
        }
    }

    @Test
    fun `production activation creates and starts transformer on main from IO caller`() {
        val engine = engine()
        var createdOnMain = false
        var startedOnMain = false
        val completion = CompletableFuture.runAsync {
            runBlocking {
                engine.startTransformerOnApplicationThread(
                    factory = {
                        createdOnMain = Looper.myLooper() == Looper.getMainLooper()
                        transformer()
                    },
                    start = { active ->
                        startedOnMain = Looper.myLooper() == Looper.getMainLooper()
                        active.getProgress(ProgressHolder())
                    }
                )
            }
        }
        awaitMain(completion)
        assertTrue(createdOnMain)
        assertTrue(startedOnMain)
        assertNotNull(activeTransformer(engine))
        engine.release()
    }

    @Test
    fun `progress polling and worker cancellation stay on application looper`() {
        val engine = engine()
        @Suppress("UNCHECKED_CAST")
        val progress = engine.exportState as MutableStateFlow<ExportEngine.ExportProgressState>
        progress.value = ExportEngine.ExportProgressState(isExporting = true)
        runBlocking {
            engine.startTransformerOnApplicationThread(::transformer) {
                it.getProgress(ProgressHolder())
            }
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertTrue(engine.exportState.value.isExporting)
        CompletableFuture.runAsync { engine.cancelExport() }.get(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(activeTransformer(engine))
        assertFalse(engine.exportState.value.isExporting)
    }

    @Test
    fun `cancel reaches preparation before a transformer exists`() {
        val engine = engine()
        val preparation = kotlinx.coroutines.Job()
        val field = ExportEngine::class.java.getDeclaredField("exportPreparationJob")
            .apply { isAccessible = true }
        field.set(engine, preparation)
        assertNull(activeTransformer(engine))
        CompletableFuture.runAsync { engine.cancelExport() }.get(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(preparation.isCancelled)
        assertNull(field.get(engine))
        assertFalse(engine.exportState.value.isExporting)
    }

    @Test
    fun `failed startup preserves error and removes inactive transformer`() {
        val engine = engine()
        val expected = IllegalArgumentException("source setup failed")
        val completion = CompletableFuture.supplyAsync {
            runBlocking {
                runCatching {
                    engine.startTransformerOnApplicationThread(::transformer) { throw expected }
                }.exceptionOrNull()
            }
        }
        awaitMain(completion)
        // Coroutine stack-trace recovery may copy an exception across dispatchers.
        val actual = completion.get()
        assertEquals(expected::class, actual!!::class)
        assertEquals(expected.message, actual.message)
        assertNull(activeTransformer(engine))
        engine.release()
    }

    private fun activeTransformer(engine: ExportEngine): Any? = ExportEngine::class.java
        .getDeclaredField("transformer").apply { isAccessible = true }.get(engine)

    private fun awaitMain(future: CompletableFuture<*>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!future.isDone && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        future.get(1, TimeUnit.SECONDS)
    }
}
