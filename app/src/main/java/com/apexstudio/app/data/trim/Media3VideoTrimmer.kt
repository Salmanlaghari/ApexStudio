package com.apexstudio.app.data.trim

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.apexstudio.app.data.media.MediaUriResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Narrow lifecycle seam: production delegates to Media3, JVM tests can control each session. */
internal interface TrimTransformerAdapter {
    fun start(mediaItem: EditedMediaItem, path: String)
    fun getProgress(progressHolder: ProgressHolder): Int
    fun cancel()
}

internal interface TrimTransformerFactory {
    fun create(context: Context, callback: TrimTransformerCallback): TrimTransformerAdapter
}

internal interface TrimTransformerCallback {
    fun onCompleted()
    fun onError(error: Throwable)
}

private object Media3TrimTransformerFactory : TrimTransformerFactory {
    override fun create(context: Context, callback: TrimTransformerCallback): TrimTransformerAdapter {
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                callback.onCompleted()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                callback.onError(exportException)
            }
        }
        val encoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(12_000_000)
            .build()
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(encoderSettings)
            .setEnableFallback(true)
            .build()
        val transformer = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .addListener(listener)
            .build()
        return object : TrimTransformerAdapter {
            override fun start(mediaItem: EditedMediaItem, path: String) = transformer.start(mediaItem, path)
            override fun getProgress(progressHolder: ProgressHolder): Int = transformer.getProgress(progressHolder)
            override fun cancel() = transformer.cancel()
        }
    }
}

/**
 * High-performance video trimming engine powered by AndroidX Media3 Transformer.
 * Allows users to extract frame-accurate trimmed video clips with custom start and end timestamps.
 */
class Media3VideoTrimmer internal constructor(
    private val context: Context,
    private val transformerFactory: TrimTransformerFactory,
) {
    constructor(context: Context) : this(context, Media3TrimTransformerFactory)

    companion object {
        private const val TAG = "Media3VideoTrimmer"
        const val MIN_CLIP_DURATION_MS = 100L
    }

    data class TrimRequest(
        val inputUri: String,
        val startMs: Long,
        val endMs: Long,
        val clipId: String? = null,
        val outputFileName: String? = null
    )

    sealed class TrimResult {
        data class Progress(val progress: Float) : TrimResult()
        data class Success(
            val outputUri: String,
            val durationMs: Long,
            val startMs: Long,
            val endMs: Long,
            val clipId: String?
        ) : TrimResult()
        data class Error(val message: String, val throwable: Throwable? = null) : TrimResult()
    }

    private val applicationHandler = Handler(Looper.getMainLooper())
    private val activeSessions = ConcurrentHashMap.newKeySet<TrimSession>()

    /** Executes trimming through a per-collection Media3 session. */
    fun trimVideo(request: TrimRequest): Flow<TrimResult> = callbackFlow {
        val startMs = request.startMs.coerceAtLeast(0L)
        val endMs = request.endMs
        val durationMs = endMs - startMs
        if (durationMs < MIN_CLIP_DURATION_MS) {
            trySend(TrimResult.Error("Trim duration too short: must be at least ${MIN_CLIP_DURATION_MS}ms (requested ${durationMs}ms)"))
            close()
            return@callbackFlow
        }

        // URI lookup and MediaItem preparation can fail too; report those as flow errors.
        val prepared = try {
            val resolvedUri = MediaUriResolver.resolvePlayableUri(context, request.inputUri)
            val outputDir = File(context.filesDir, "trimmed_clips")
            // Another flow can create the shared directory between our check and mkdirs.
            if (!outputDir.isDirectory && !outputDir.mkdirs() && !outputDir.isDirectory) {
                throw IllegalStateException("Unable to create trim output directory: $outputDir")
            }
            val fileName = request.outputFileName ?: "trim_${UUID.randomUUID()}_${startMs}_${endMs}.mp4"
            val outputFile = File(outputDir, fileName)
            check(!outputFile.exists()) { "Trim output already exists; choose a new output filename." }
            val clippingConfig = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .setStartsAtKeyFrame(false)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(resolvedUri)
                .setClippingConfiguration(clippingConfig)
                .build()
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(false)
                .setRemoveVideo(false)
                .build()
            Triple(resolvedUri, outputFile, editedMediaItem)
        } catch (e: Exception) {
            trySend(TrimResult.Error("Unable to prepare video trim: ${e.message ?: "unknown error"}", e))
            close()
            return@callbackFlow
        }
        val (resolvedUri, outputFile, editedMediaItem) = prepared
        // Never remove a pre-existing destination as failure cleanup; only our newly-created partial output.
        val outputExistedBeforeTrim = outputFile.exists()

        val session = TrimSession(
            onRemoved = { activeSessions.remove(it) },
            onCancelled = { close() },
            cleanupPartialOutput = {
                if (!outputExistedBeforeTrim && outputFile.exists()) outputFile.delete()
            },
        )
        activeSessions.add(session)

        val transformerCallback = object : TrimTransformerCallback {
            override fun onCompleted() {
                applicationHandler.post {
                    if (!session.finish()) return@post
                    if (!outputFile.isFile || outputFile.length() == 0L) {
                        session.cleanupFailedOutput()
                        trySend(TrimResult.Error("Media3 Transformer completed without producing a readable output file"))
                        close()
                        return@post
                    }
                    val outUri = Uri.fromFile(outputFile).toString()
                    Log.i(TAG, "Media3 Transformer trim complete -> $outUri (duration: ${durationMs}ms)")
                    trySend(TrimResult.Progress(1f))
                    trySend(TrimResult.Success(outUri, durationMs, startMs, endMs, request.clipId))
                    close()
                }
            }

            override fun onError(error: Throwable) {
                applicationHandler.post {
                    if (!session.failOnApplicationLooper()) return@post
                    Log.e(TAG, "Media3 Transformer trim error", error)
                    trySend(TrimResult.Error(error.message ?: "Trimming failed via Media3 Transformer", error))
                    close()
                }
            }
        }

        try {
            withContext(Dispatchers.Main.immediate) {
                if (!session.isTerminal) {
                    val transformer = transformerFactory.create(context, transformerCallback)
                    session.transformer = transformer
                    if (session.isTerminal) {
                        session.cancelTransformerOnApplicationLooper()
                    } else {
                        val progressHolder = ProgressHolder()
                        session.progressJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                            while (isActive && !session.isTerminal) {
                                delay(80L)
                                if (session.isTerminal) break
                                val state = try {
                                    transformer.getProgress(progressHolder)
                                } catch (failure: Exception) {
                                    transformerCallback.onError(failure)
                                    break
                                }
                                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                    val progress = (progressHolder.progress / 100f).coerceIn(0f, 0.99f)
                                    trySend(TrimResult.Progress(progress))
                                }
                            }
                        }
                        transformer.start(editedMediaItem, outputFile.absolutePath)
                    }
                }
            }
        } catch (e: CancellationException) {
            session.cancel()
            throw e
        } catch (e: Exception) {
            val reported = withContext(Dispatchers.Main.immediate) { session.failOnApplicationLooper() }
            if (reported) {
                Log.e(TAG, "Failed to start Media3 Transformer for $resolvedUri", e)
                trySend(TrimResult.Error("Media3 Transformer initiation failed: ${e.message ?: "unknown error"}", e))
                close()
            }
        }

        awaitClose { session.cancel() }
    }

    fun cancelActiveTrim() {
        // Each flow owns its own Transformer; cancellation never accidentally targets a sibling trim.
        activeSessions.toList().forEach { it.cancel() }
    }

    private inner class TrimSession(
        private val onRemoved: (TrimSession) -> Unit,
        private val onCancelled: () -> Unit,
        private val cleanupPartialOutput: () -> Unit,
    ) {
        private val terminal = AtomicBoolean(false)
        @Volatile var transformer: TrimTransformerAdapter? = null
        @Volatile var progressJob: Job? = null

        val isTerminal: Boolean get() = terminal.get()

        fun finish(): Boolean {
            if (!terminal.compareAndSet(false, true)) return false
            progressJob?.cancel()
            transformer = null
            onRemoved(this)
            return true
        }

        fun failOnApplicationLooper(): Boolean {
            if (!terminal.compareAndSet(false, true)) return false
            progressJob?.cancel()
            cancelTransformerOnApplicationLooper()
            cleanupPartialOutput()
            return true
        }

        fun cleanupFailedOutput() {
            cleanupPartialOutput()
        }

        fun cancel() {
            if (!terminal.compareAndSet(false, true)) return
            progressJob?.cancel()
            onCancelled()
            applicationHandler.post { cancelTransformerOnApplicationLooper() }
        }

        fun cancelTransformerOnApplicationLooper() {
            progressJob?.cancel()
            try {
                transformer?.cancel()
            } catch (ignored: Exception) {
                // Cancellation is best-effort; the session is already terminal.
            } finally {
                transformer = null
                cleanupPartialOutput()
                onRemoved(this)
            }
        }
    }
}
