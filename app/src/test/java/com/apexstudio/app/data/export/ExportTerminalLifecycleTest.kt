package com.apexstudio.app.data.export

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ListenerSet
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit
import java.io.File

/** Exercises production listener ownership without pretending to run native phone codecs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(UnstableApi::class)
class ExportTerminalLifecycleTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun activate(engine: ExportEngine): Transformer {
        val run = requireNotNull(engine.reserveNonProjectExport())
        val active = engine.buildHardwareTransformer(
            ExportEngine.ExportConfig(), File(context.cacheDir, "lifecycle.mp4"), run
        )
        runBlocking { engine.startTransformerOnApplicationThread({ active }) { /* no native codec start */ } }
        return active
    }
    private fun composition() = Composition.Builder(EditedMediaItemSequence.withAudioAndVideoFrom(listOf(
        EditedMediaItem.Builder(MediaItem.fromUri("file:///test.mp4")).build()
    ))).build()

    @Suppress("UNCHECKED_CAST")
    private fun listeners(active: Transformer): ListenerSet<Transformer.Listener> =
        Transformer::class.java.getDeclaredField("listeners").apply { isAccessible = true }
            .get(active) as ListenerSet<Transformer.Listener>

    private fun complete(active: Transformer) {
        listeners(active).sendEvent(0) { it.onCompleted(composition(), ExportResult.Builder().build()) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitExportFinished(engine: ExportEngine) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (engine.exportState.value.isExporting && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun `completion retires renderer and permits a second export without leaving screen`() {
        val engine = ExportEngine(context)
        val first = activate(engine)
        assertEquals(ExportProgressStage.VIDEO_RENDER, engine.exportState.value.stage)
        complete(first)
        awaitExportFinished(engine)
        assertFalse(engine.exportState.value.isExporting)
        assertEquals(1f, engine.exportState.value.progress, 0f)
        assertEquals(ExportProgressStage.COMPLETE, engine.exportState.value.stage)
        val second = activate(engine)
        assertTrue(engine.exportState.value.isExporting)
        assertFalse(engine.finishTransformerRun(first))
        assertTrue(engine.finishTransformerRun(second))
        engine.release()
    }

    @Test fun `error retires renderer and permits retry`() {
        val engine = ExportEngine(context)
        val first = activate(engine)
        val failure = ExportException.createForUnexpected(
            IllegalStateException("codec failed")
        )
        listeners(first).sendEvent(0) { it.onError(composition(), ExportResult.Builder().build(), failure) }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(engine.exportState.value.isExporting)
        assertNotNull(engine.exportState.value.error)
        assertEquals(failure.message, engine.exportState.value.error)
        activate(engine)
        assertTrue(engine.exportState.value.isExporting)
        engine.release()
    }

    @Test fun `unsupported source decoder format gives an actionable export message`() {
        val engine = ExportEngine(context)
        val active = activate(engine)
        val failure = ExportException.createForCodec(
            IllegalArgumentException("Unsupported AV1 profile"),
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            ExportException.CodecInfo("test-decoder", true, true, "video/av01")
        )
        listeners(active).sendEvent(0) { it.onError(composition(), ExportResult.Builder().build(), failure) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(UNSUPPORTED_VIDEO_FORMAT_EXPORT_MESSAGE, engine.exportState.value.error)
        engine.release()
    }

    @Test fun `project export maps unsupported source format but preserves unrelated failures`() {
        val unsupported = ExportException.createForCodec(
            IllegalArgumentException("Unsupported AV1 profile"),
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            ExportException.CodecInfo("test-decoder", true, true, "video/av01")
        )
        val mapped = videoExportFailureForProject(unsupported)
        assertEquals(UNSUPPORTED_VIDEO_FORMAT_EXPORT_MESSAGE, mapped.message)
        assertSame(unsupported, mapped.cause)

        val unrelated = ExportException.createForUnexpected(IllegalStateException("codec failed"))
        assertSame(unrelated, videoExportFailureForProject(unrelated))
        assertEquals(unrelated.message, videoExportFailureMessage(unrelated))
    }

    @Test fun `cancelled old completion cannot finish or detach replacement export`() {
        val engine = ExportEngine(context)
        val first = activate(engine)
        engine.cancelExport()
        val second = activate(engine)
        complete(first)
        assertTrue(engine.exportState.value.isExporting)
        assertNull(engine.exportState.value.outputUri)
        assertTrue(engine.finishTransformerRun(second))
        engine.release()
    }

    @Test fun `queued old completion is ignored when cancelled before publication`() {
        val engine = ExportEngine(context)
        val first = activate(engine)
        listeners(first).sendEvent(0) { it.onCompleted(composition(), ExportResult.Builder().build()) }
        engine.cancelExport()
        activate(engine)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(engine.exportState.value.isExporting)
        assertNull(engine.exportState.value.outputUri)
        engine.release()
    }

    @Test fun `hardware transformer explicitly requests AVC and AAC instead of source HEVC`() {
        val engine = ExportEngine(context)
        val active = activate(engine)
        val request = Transformer::class.java.getDeclaredField("transformationRequest")
            .apply { isAccessible = true }.get(active) as androidx.media3.transformer.TransformationRequest
        assertEquals(MimeTypes.VIDEO_H264, request.videoMimeType)
        assertEquals(MimeTypes.AUDIO_AAC, request.audioMimeType)
        engine.release()
    }
}
