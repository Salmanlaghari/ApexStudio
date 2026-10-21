package com.apexstudio.app.ui.screens.colortools

import android.content.Context
import android.net.Uri
import com.apexstudio.app.data.color.ColorStudioGrade
import com.apexstudio.app.data.color.ColorStudioLut
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Standalone selected-source export only; never reads or renders project timeline composition. */
internal object ColorVideoExporter {
    private const val MAX_VIDEO_DURATION_SECONDS = 30 * 60
    private const val MAX_VIDEO_DIMENSION = 3840
    private const val MAX_STAGED_INPUT_BYTES = 2_000_000_000L

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
    suspend fun export(context: Context, uri: Uri, grade: ColorStudioGrade): File {
        val info = inspect(context, uri)
        require(info.supported) { info.reason ?: "Selected video is unsupported." }
        currentCoroutineContext().ensureActive()
        val input = stage(context, uri, "color_input_")
        var cube: File? = null
        var output: File? = null
        try {
            val cubeFile = File.createTempFile("color_", ".cube", context.cacheDir).also { cube = it }
            val outputFile = File.createTempFile("color_", ".mp4", ColorMediaIO.exportDirectory(context)).also { output = it }
            withContext(Dispatchers.IO) { ColorStudioLut(grade).writeCube(cubeFile) }
            currentCoroutineContext().ensureActive()
            // executeWithArgumentsAsync keeps native encoding off the caller/main thread.
            // Paths remain separate arguments; no shell interpolation is performed.
            val filter = "lut3d=file=${cubeFile.absolutePath}:interp=trilinear"
            val args = arrayOf(
                "-y", "-i", input.absolutePath,
                "-map", "0:v:0", "-map", "0:a?",
                "-vf", filter,
                "-c:v", "libopenh264", "-b:v", "8M", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart",
                outputFile.absolutePath
            )
            val session = executeCancellable(args)
            val returnCode = session.returnCode?.value ?: -1
            check(returnCode == 0 && outputFile.isFile && outputFile.length() > 0L) {
                "FFmpeg could not finish the standalone Color video export (code $returnCode)."
            }
            return outputFile
        } catch (failure: Throwable) {
            output?.delete()
            throw failure
        } finally {
            input.delete()
            cube?.delete()
        }
    }

    private suspend fun executeCancellable(arguments: Array<String>): FFmpegSession {
        val nativeFinished = CompletableDeferred<FFmpegSession>()
        return try {
            suspendCancellableCoroutine { continuation ->
            val sessionRef = AtomicReference<FFmpegSession?>()
            val cancelled = AtomicBoolean(false)
            continuation.invokeOnCancellation {
                cancelled.set(true)
                sessionRef.get()?.let { FFmpegKit.cancel(it.sessionId) }
            }
            val session = FFmpegKit.executeWithArgumentsAsync(arguments) { completed ->
                if (completed != null) {
                    nativeFinished.complete(completed)
                    if (continuation.isActive) continuation.resume(completed)
                } else {
                    nativeFinished.completeExceptionally(IllegalStateException("FFmpegKit returned no session result."))
                    if (continuation.isActive) continuation.cancel(IllegalStateException("FFmpegKit returned no session result."))
                }
            }
            sessionRef.set(session)
            if (cancelled.get()) FFmpegKit.cancel(session.sessionId)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            // Cancellation handler requests only this session to stop. Keep its staged input and LUT
            // alive until the native completion callback confirms the encoder has stopped reading.
            withContext(NonCancellable) { runCatching { nativeFinished.await() } }
            throw cancelled
        }
    }

    private suspend fun stage(context: Context, uri: Uri, prefix: String): File = withContext(Dispatchers.IO) {
        val staged = File.createTempFile(prefix, ".media", context.cacheDir)
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
