package com.apexstudio.app.ui.screens.colortools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Standalone Color tool intentionally accepts one selected still/video source, never a project render. */
internal object ColorMediaIO {
    const val PREVIEW_MAX_DIMENSION = 1280
    const val MAX_STILL_EXPORT_PIXELS = 12_000_000L
    const val MAX_STILL_EXPORT_DIMENSION = 5000

    data class Frame(val bitmap: Bitmap, val sourceTimeMs: Long, val warning: String? = null)
    data class ImageBounds(val width: Int, val height: Int, val exifOrientation: Int)

    fun selectedSources(clips: List<MediaClip>): List<MediaClip> =
        clips.filter { it.type == ClipType.VIDEO || it.type == ClipType.IMAGE }

    fun sourceUri(clip: MediaClip): String = clip.backgroundReplace?.sourceUri
        ?.takeIf(String::isNotBlank) ?: clip.uri

    fun boundedDimensions(width: Int, height: Int, maximumDimension: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && maximumDimension > 0)
        val scale = minOf(1f, maximumDimension.toFloat() / max(width, height))
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }

    fun previewTimeMs(clip: MediaClip, requestedTimeMs: Long, sourceDurationMs: Long? = null): Long {
        val clipDuration = (clip.durationMs.takeIf { it > 0 } ?: clip.trimEndMs).coerceAtLeast(0L)
        val verifiedSourceDuration = sourceDurationMs?.takeIf { it > 0L }
        val maxTime = if (verifiedSourceDuration != null && clipDuration > 0L) {
            minOf(clipDuration, verifiedSourceDuration)
        } else verifiedSourceDuration ?: clipDuration
        val start = clip.trimStartMs.coerceIn(0L, maxTime)
        val end = (clip.trimEndMs.takeIf { it > 0 } ?: maxTime).coerceIn(start, maxTime)
        return requestedTimeMs.coerceIn(start, end)
    }

    fun stillExportAllowed(width: Int, height: Int): Boolean = width > 0 && height > 0 &&
        width <= MAX_STILL_EXPORT_DIMENSION && height <= MAX_STILL_EXPORT_DIMENSION &&
        width.toLong() * height.toLong() <= MAX_STILL_EXPORT_PIXELS

    suspend fun decodePreview(context: Context, clip: MediaClip, requestedTimeMs: Long): Frame {
        val uri = Uri.parse(sourceUri(clip))
        return if (clip.type == ClipType.IMAGE) {
            val bitmap = decodeImage(context, uri, PREVIEW_MAX_DIMENSION, export = false).first
            Frame(bitmap, 0L)
        } else {
            val videoInfo = ColorVideoExporter.inspect(context, uri)
            require(videoInfo.supported) { videoInfo.reason ?: "Selected video is not supported by the SDR Color preview." }
            val retriever = MediaMetadataRetriever()
            try {
                setSource(context, retriever, uri)
                val hdrWarning = hdrWarning(retriever)
                if (hdrWarning != null) throw UnsupportedOperationException(hdrWarning)
                val durationMs = (videoInfo.durationSeconds * 1000.0).toLong().coerceAtLeast(0L)
                val time = previewTimeMs(clip, requestedTimeMs, durationMs)
                val sourceWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val sourceHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                require(sourceWidth != null && sourceHeight != null && sourceWidth > 0 && sourceHeight > 0) {
                    "Video dimensions are unavailable; a bounded preview cannot be decoded safely."
                }
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val (width, height) = if (rotation == 90 || rotation == 270) sourceHeight to sourceWidth else sourceWidth to sourceHeight
                val (targetWidth, targetHeight) = boundedDimensions(width, height, PREVIEW_MAX_DIMENSION)
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(timeMsToMicros(time), MediaMetadataRetriever.OPTION_CLOSEST,
                        targetWidth, targetHeight)
                } else {
                    val full = retriever.getFrameAtTime(timeMsToMicros(time), MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: throw IllegalStateException("No video frame is available at ${time} ms.")
                    scaleAndRecycle(full, targetWidth, targetHeight)
                } ?: throw IllegalStateException("No video frame is available at ${time} ms.")
                Frame(bitmap, time)
            } finally {
                retriever.release()
            }
        }
    }

    fun inspectImage(context: Context, uri: Uri): ImageBounds {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(context, uri).use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { "The selected image could not be read." }
        val orientation = open(context, uri).use { ExifInterface(it).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        val swaps = orientation in setOf(ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270)
        return ImageBounds(if (swaps) options.outHeight else options.outWidth,
            if (swaps) options.outWidth else options.outHeight, orientation)
    }

    /** Decode sampled previews or a full-size still only after its dimensions pass the export cap. */
    fun decodeImage(context: Context, uri: Uri, maxDimension: Int, export: Boolean): Pair<Bitmap, ImageBounds> {
        val bounds = inspectImage(context, uri)
        if (export) require(stillExportAllowed(bounds.width, bounds.height)) {
            "This still exceeds the safe Color export limit (${MAX_STILL_EXPORT_DIMENSION}px per side and 12 MP). The original was not changed."
        }
        if (export) requireStillExportMemory(bounds.width, bounds.height)
        val sample = if (export) 1 else {
            var value = 1
            while (max(bounds.width, bounds.height) / value > maxDimension) value *= 2
            value
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val decoded = open(context, uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalStateException("The selected image could not be decoded.")
        val oriented = if (bounds.exifOrientation == ExifInterface.ORIENTATION_NORMAL ||
            bounds.exifOrientation == ExifInterface.ORIENTATION_UNDEFINED) decoded else {
            val matrix = Matrix().apply {
                when (bounds.exifOrientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                    else -> Unit
                }
            }
            val rotated = try {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } catch (failure: Throwable) {
                decoded.recycle()
                throw failure
            }
            if (rotated !== decoded) decoded.recycle()
            rotated
        }
        val result = if (export) oriented else {
            val (width, height) = boundedDimensions(oriented.width, oriented.height, maxDimension)
            scaleAndRecycle(oriented, width, height)
        }
        return result to bounds
    }

    private fun requireStillExportMemory(width: Int, height: Int) {
        val runtime = Runtime.getRuntime()
        val available = runtime.freeMemory() + runtime.maxMemory() - runtime.totalMemory()
        val workingSetEstimate = width.toLong() * height.toLong() * 10L
        require(workingSetEstimate <= available * 0.60) {
            "This still exceeds the currently available memory budget for a safe full-size Color export. Try a smaller image; the original was not changed."
        }
    }

    fun exportDirectory(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.cacheDir, "ApexStudio_Exports/Color").apply { mkdirs() }

    private fun hdrWarning(retriever: MediaMetadataRetriever): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val transfer = runCatching {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull()
        }.getOrNull()
        return if (transfer == 6 || transfer == 7) {
            "HDR (PQ/HLG) video is not supported in this SDR Color preview/export. Choose an SDR source."
        } else null
    }

    private fun open(context: Context, uri: Uri): InputStream = when (uri.scheme) {
        null, "" -> File(uri.toString()).inputStream()
        "file" -> File(uri.path ?: error("Invalid source file URI.")).inputStream()
        else -> context.contentResolver.openInputStream(uri) ?: error("The selected media is no longer accessible.")
    }

    private fun setSource(context: Context, retriever: MediaMetadataRetriever, uri: Uri) {
        when (uri.scheme) {
            null, "" -> retriever.setDataSource(uri.toString())
            "file" -> retriever.setDataSource(uri.path ?: error("Invalid source file URI."))
            else -> retriever.setDataSource(context, uri)
        }
    }

    private fun scaleAndRecycle(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) return bitmap
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** MediaMetadataRetriever accepts microseconds; saturate malformed/extreme clip times safely. */
    internal fun timeMsToMicros(timeMs: Long): Long =
        if (timeMs <= 0L) 0L else if (timeMs > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else timeMs * 1_000L
}
