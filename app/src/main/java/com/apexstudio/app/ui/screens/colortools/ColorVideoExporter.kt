package com.apexstudio.app.ui.screens.colortools

import android.content.Context
import android.net.Uri
import android.util.Log
import com.apexstudio.app.data.color.ColorStudioGrade
import com.apexstudio.app.data.color.ColorStudioLut
import com.apexstudio.app.data.color.hasValidRenderParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Standalone selected-source export only; never reads or renders project timeline composition. */
internal object ColorVideoExporter {
    private const val MAX_VIDEO_DURATION_SECONDS = 30 * 60
    private const val MAX_VIDEO_DIMENSION = 3840
    private const val MAX_STAGED_INPUT_BYTES = 2_000_000_000L
    private const val CANCEL_DRAIN_TIMEOUT_MS = 5_000L
    private const val NO_SESSION_ID = Long.MIN_VALUE
    private const val TAG = "ColorVideoExporter"
    private val nativeFileRegistryLock = Any()
    private val activeNativeFilePaths = ConcurrentHashMap.newKeySet<String>()

    /** Delete native inputs only after FFmpeg reports that it has stopped reading them. */
    internal class NativeFileLease {
        private val files = mutableListOf<File>()
        private val nativeFinished = AtomicBoolean(false)
        private val cleanupRequested = AtomicBoolean(false)
        private val cleaned = AtomicBoolean(false)

        internal fun register(file: File) {
            synchronized(ColorVideoExporter.nativeFileRegistryLock) {
                check(!cleanupRequested.get()) { "Cannot register files after Color cleanup was requested." }
                files += file
                ColorVideoExporter.activeNativeFilePaths.add(file.absolutePath)
            }
        }

        internal fun markNativeFinished() {
            nativeFinished.set(true)
            cleanupWhenSafe()
        }

        internal fun requestCleanup() {
            cleanupRequested.set(true)
            cleanupWhenSafe()
        }

        private fun cleanupWhenSafe() {
            if (!nativeFinished.get() || !cleanupRequested.get()) return
            synchronized(ColorVideoExporter.nativeFileRegistryLock) {
                if (!nativeFinished.get() || !cleanupRequested.get() || !cleaned.compareAndSet(false, true)) return
                files.forEach { it.delete() }
                files.forEach { ColorVideoExporter.activeNativeFilePaths.remove(it.absolutePath) }
            }
        }
    }

    internal data class SourceInfo(
        val width: Int,
        val height: Int,
        val durationSeconds: Double,
        val pixelFormat: String,
        val transfer: String,
        val supported: Boolean,
        val reason: String? = null
    )

    /** Re-probe every time: content URIs may refer to changed bytes since the previous inspection. */
    suspend fun inspect(context: Context, uri: Uri): SourceInfo {
        val staged = stage(context, uri, "color_probe_")
        try {
            currentCoroutineContext().ensureActive()
            val session = withContext(Dispatchers.IO) { FFprobeKit.getMediaInformation(staged.absolutePath) }
            val info = session.mediaInformation ?: throw IllegalStateException("Video metadata could not be read.")
            require(session.returnCode?.value == 0) { "Video metadata could not be verified for Color processing." }
            val video = info.streams.firstOrNull { it.type == "video" }
                ?: throw IllegalArgumentException("The selected source has no video stream.")
            val width = video.width?.toInt() ?: 0
            val height = video.height?.toInt() ?: 0
            val duration = info.duration?.toDoubleOrNull() ?: 0.0
            val pixelFormat = video.getStringProperty("pix_fmt")?.lowercase(Locale.ROOT).orEmpty()
            val transfer = video.getStringProperty("color_transfer")?.lowercase(Locale.ROOT).orEmpty()
            val reason = when {
                width <= 0 || height <= 0 || !duration.isFinite() || duration <= 0.0 -> "Video dimensions or duration are unavailable; Color cannot safely process this source."
                width > MAX_VIDEO_DIMENSION || height > MAX_VIDEO_DIMENSION -> "Selected video exceeds the 4K-per-side Color export limit."
                duration > MAX_VIDEO_DURATION_SECONDS -> "Selected video exceeds the 30-minute Color export limit."
                transfer in HDR_TRANSFERS -> "HDR (PQ/HLG) video is unsupported by this SDR Color pipeline. Choose an SDR source."
                transfer !in SDR_TRANSFERS -> "Video transfer metadata does not confirm SDR. Color processing is disabled rather than guessing."
                !isVerifiedSdr8Bit(pixelFormat, transfer) -> "Video pixel format '$pixelFormat' is not a verified 8-bit SDR format."
                else -> null
            }
            return SourceInfo(width, height, duration, pixelFormat, transfer, reason == null, reason)
        } finally {
            staged.delete()
        }
    }

    /** Writes an independent MP4. The input is the full original source, not the project trim or composition. */
    suspend fun export(
        context: Context,
        uri: Uri,
        grade: ColorStudioGrade,
        onProgress: (Int) -> Unit = {}
    ): File {
        require(grade.hasValidRenderParameters()) { "Color adjustment values are outside the supported range." }
        pruneOrphanedNativeFiles(context)
        val lease = NativeFileLease()
        var nativeLaunchAttempted = false
        var publishedOutput: File? = null
        val lastReportedProgress = AtomicInteger(0)
        try {
            val info = inspect(context, uri)
            require(info.supported) { info.reason ?: "Selected video is unsupported." }
            currentCoroutineContext().ensureActive()
            val input = stage(context, uri, "color_input_", lease)
            val cubeFile = createRegisteredTempFile("color_", ".cube", context.cacheDir, lease)
            val outputFile = createRegisteredTempFile(
                "color_pending_", ".mp4", ColorMediaIO.exportDirectory(context), lease
            )
            withContext(Dispatchers.IO) { ColorStudioLut(grade).writeCube(cubeFile) }
            currentCoroutineContext().ensureActive()
            // executeWithArgumentsAsync keeps native encoding off the caller/main thread.
            // Paths remain separate arguments; no shell interpolation is performed.
            val filter = buildVideoFilter(cubeFile.absolutePath, grade)
            val args = arrayOf(
                "-y", "-i", input.absolutePath,
                "-map", "0:v:0", "-map", "0:a?",
                "-vf", filter,
                "-c:v", "libopenh264", "-b:v", "8M", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart",
                outputFile.absolutePath
            )
            nativeLaunchAttempted = true
            val session = executeCancellable(args, lease::markNativeFinished) { encodedTimeMs ->
                colorVideoExportPercent(encodedTimeMs, info.durationSeconds * 1_000.0)?.let { measured ->
                    val percent = updateMaximumProgress(lastReportedProgress, measured)
                    runCatching { onProgress(percent) }
                }
            }
            val returnCode = session.returnCode?.value ?: -1
            check(returnCode == 0 && outputFile.isFile && outputFile.length() > 0L) {
                "FFmpeg could not finish the standalone Color video export (code $returnCode)."
            }
            return publishPendingOutput(outputFile).also {
                publishedOutput = it
                val completedPercent = updateMaximumProgress(lastReportedProgress, 100)
                runCatching { onProgress(completedPercent) }
            }
        } catch (failure: Throwable) {
            publishedOutput?.delete()
            throw failure
        } finally {
            if (!nativeLaunchAttempted) lease.markNativeFinished()
            lease.requestCleanup()
        }
    }

    private fun pruneOrphanedNativeFiles(context: Context) {
        val candidates = mutableListOf<File>()
        context.cacheDir.listFiles()?.forEach { file ->
            if ((file.name.startsWith("color_input_") && file.extension == "media") ||
                (file.name.startsWith("color_") && file.extension == "cube")) candidates += file
        }
        ColorMediaIO.exportDirectory(context).listFiles()?.forEach { file ->
            if (file.name.startsWith("color_pending_") && file.extension == "mp4") candidates += file
        }
        synchronized(nativeFileRegistryLock) {
            candidates.filterNot { activeNativeFilePaths.contains(it.absolutePath) }.forEach { it.delete() }
        }
    }

    internal fun publishPendingOutput(pending: File): File {
        require(pending.isFile && pending.length() > 0L &&
            pending.name.startsWith("color_pending_") && pending.extension == "mp4") {
            "The standalone Color export result is not ready to publish."
        }
        val directory = pending.parentFile ?: error("The Color export directory is unavailable.")
        val suffix = pending.name.removePrefix("color_pending_").removeSuffix(".mp4")
        var candidate = File(directory, "color_$suffix.mp4")
        var collision = 1
        while (candidate.exists()) {
            candidate = File(directory, "color_${suffix}_$collision.mp4")
            collision++
        }
        check(pending.renameTo(candidate)) { "The completed Color export could not be finalized." }
        return candidate
    }

    internal fun buildVideoFilter(cubePath: String, grade: ColorStudioGrade): String {
        require(grade.hasValidRenderParameters()) { "Color adjustment values are outside the supported range." }
        val filters = mutableListOf("lut3d=file=$cubePath:interp=trilinear")
        if (grade.sharpness > 0f) {
            val amount = grade.sharpness * 1.6f
            val edge = String.format(Locale.US, "%.3f", -0.15f * amount)
            val center = String.format(Locale.US, "%.3f", 1f + 0.6f * amount)
            val matrix = "0 $edge 0 $edge $center $edge 0 $edge 0"
            filters += "format=gbrp"
            filters += "convolution=0m='$matrix':1m='$matrix':2m='$matrix':0rdiv=1:1rdiv=1:2rdiv=1"
            filters += "format=yuv420p"
        }
        return filters.joinToString(",")
    }

    private suspend fun executeCancellable(
        arguments: Array<String>,
        onNativeFinished: () -> Unit,
        onStatistics: (Double) -> Unit
    ): FFmpegSession =
        executeCancellableNativeSession(
            startSession = { onComplete ->
                val session = FFmpegKit.executeWithArgumentsAsync(
                    arguments,
                    { completed ->
                        if (completed == null) onComplete(null, IllegalStateException("FFmpegKit returned no session result."))
                        else onComplete(completed, null)
                    },
                    null,
                    // FFmpegKit Statistics.getTime() reports milliseconds; the maintained 8.1 API
                    // retains the upstream unit, matching the FFprobe duration passed to the mapper.
                    { statistics -> onStatistics(statistics.time) }
                )
                session.sessionId
            },
            cancelSession = { sessionId -> FFmpegKit.cancel(sessionId) },
            onNativeFinished = onNativeFinished,
            drainTimeoutMillis = CANCEL_DRAIN_TIMEOUT_MS,
            onDrainTimeout = {
                Log.w(TAG, "FFmpeg cancellation callback timed out; retaining staged Color files until native completion.")
            }
        )

    private fun updateMaximumProgress(progress: AtomicInteger, candidate: Int): Int {
        while (true) {
            val previous = progress.get()
            val next = maxOf(previous, candidate)
            if (previous == next || progress.compareAndSet(previous, next)) return next
        }
    }

    internal suspend fun <T : Any> executeCancellableNativeSession(
        startSession: (onComplete: (T?, Throwable?) -> Unit) -> Long,
        cancelSession: (sessionId: Long) -> Unit,
        onNativeFinished: () -> Unit,
        drainTimeoutMillis: Long = CANCEL_DRAIN_TIMEOUT_MS,
        onDrainTimeout: () -> Unit = {}
    ): T {
        require(drainTimeoutMillis >= 0L)
        val nativeFinished = CompletableDeferred<Unit>()
        val sessionId = AtomicLong(NO_SESSION_ID)
        val startInvoked = AtomicBoolean(false)
        val cancellationRequested = AtomicBoolean(false)
        val cancellationSent = AtomicBoolean(false)
        val nativeFinishedSignalled = AtomicBoolean(false)

        fun signalNativeFinished() {
            if (nativeFinishedSignalled.compareAndSet(false, true)) {
                runCatching(onNativeFinished)
                nativeFinished.complete(Unit)
            }
        }

        fun cancelNativeSessionOnce(id: Long) {
            if (!nativeFinishedSignalled.get() && cancellationSent.compareAndSet(false, true)) {
                runCatching { cancelSession(id) }
            }
        }

        fun requestNativeCancellation() {
            cancellationRequested.set(true)
            val id = sessionId.get()
            if (id != NO_SESSION_ID) cancelNativeSessionOnce(id)
        }

        return try {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { requestNativeCancellation() }
                try {
                    startInvoked.set(true)
                    val id = startSession { value, failure ->
                        signalNativeFinished()
                        if (continuation.isActive) {
                            when {
                                failure != null -> continuation.resumeWithException(failure)
                                value != null -> continuation.resume(value)
                                else -> continuation.resumeWithException(
                                    IllegalStateException("FFmpegKit returned no session result.")
                                )
                            }
                        }
                    }
                    sessionId.set(id)
                    if (cancellationRequested.get()) cancelNativeSessionOnce(id)
                } catch (failure: Throwable) {
                    signalNativeFinished()
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (!startInvoked.get()) signalNativeFinished()
            val drained = withContext(NonCancellable) {
                withTimeoutOrNull(drainTimeoutMillis) {
                    nativeFinished.await()
                    true
                } ?: false
            }
            if (!drained) runCatching(onDrainTimeout)
            throw cancelled
        }
    }

    private fun createRegisteredTempFile(
        prefix: String,
        suffix: String,
        directory: File,
        lease: NativeFileLease
    ): File = synchronized(nativeFileRegistryLock) {
        File.createTempFile(prefix, suffix, directory).also(lease::register)
    }

    private suspend fun stage(
        context: Context,
        uri: Uri,
        prefix: String,
        lease: NativeFileLease? = null
    ): File = withContext(Dispatchers.IO) {
        val staged = if (lease == null) File.createTempFile(prefix, ".media", context.cacheDir)
        else createRegisteredTempFile(prefix, ".media", context.cacheDir, lease)
        try {
            val source = when (uri.scheme) {
                null, "" -> File(uri.toString()).inputStream()
                "file" -> File(uri.path ?: error("Invalid source video URI.")).inputStream()
                else -> context.contentResolver.openInputStream(uri)
                    ?: error("The selected video is no longer accessible.")
            }
            source.use { input -> staged.outputStream().buffered().use { target ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= MAX_STAGED_INPUT_BYTES) {
                        "Selected video exceeds the 2 GB safe staging limit."
                    }
                    target.write(buffer, 0, count)
                }
            } }
            currentCoroutineContext().ensureActive()
            check(staged.isFile && staged.length() > 0L) { "Selected video could not be staged for export." }
            staged
        } catch (failure: Throwable) {
            staged.delete()
            throw failure
        }
    }

    internal fun isVerifiedSdr8Bit(pixelFormat: String, transfer: String): Boolean =
        pixelFormat.lowercase(Locale.ROOT) in SDR_8BIT_PIXEL_FORMATS &&
            transfer.lowercase(Locale.ROOT) in SDR_TRANSFERS

    private val SDR_TRANSFERS = setOf("bt709", "smpte170m", "iec61966-2-1", "bt470bg", "gamma22", "gamma28", "smpte240m")
    private val HDR_TRANSFERS = setOf("smpte2084", "arib-std-b67", "bt2020-10", "bt2020-12")
    private val SDR_8BIT_PIXEL_FORMATS = setOf(
        "yuv420p", "yuv422p", "yuv444p", "yuvj420p", "yuvj422p", "yuvj444p",
        "nv12", "nv21", "yuv420p8", "yuv422p8", "yuv444p8", "gbrp", "rgb24", "bgr24", "gray"
    )
}
