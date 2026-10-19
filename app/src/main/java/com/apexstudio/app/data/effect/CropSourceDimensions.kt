package com.apexstudio.app.data.effect

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream

/** Dimensions in the coordinate space cropped by the photo/video renderer, not raw JPEG bytes. */
data class CropSourceDimensions(val width: Int, val height: Int) {
    val aspect: Float get() = width.toFloat() / height
    companion object {
        fun oriented(width: Int, height: Int, rotationDegrees: Int): CropSourceDimensions? {
            if (width <= 0 || height <= 0) return null
            val rotation = ((rotationDegrees % 360) + 360) % 360
            return if (rotation == 90 || rotation == 270) CropSourceDimensions(height, width)
            else CropSourceDimensions(width, height)
        }
    }
}

/** The caller runs metadata I/O off the UI thread; native metadata calls cannot be force-cancelled. */
suspend fun readCropSourceDimensions(context: Context, clip: MediaClip): CropSourceDimensions? {
    currentCoroutineContext().ensureActive()
    if (clip.type != ClipType.IMAGE) {
        val retriever = MediaMetadataRetriever()
        val dimensions = try {
            val uri = Uri.parse(clip.uri)
            if (uri.scheme.isNullOrBlank()) retriever.setDataSource(clip.uri)
            else retriever.setDataSource(context, uri)
            CropSourceDimensions.oriented(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            )
        } catch (_: Exception) { null } finally { try { retriever.release() } catch (_: Exception) {} }
        currentCoroutineContext().ensureActive()
        if (dimensions != null) return dimensions
    }
    val dimensions = try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openCropSource(context, clip.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        currentCoroutineContext().ensureActive()
        val exifRotation = try {
            openCropSource(context, clip.uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0 // Mirrors follow the existing photo renderer's orientation policy.
                }
            } ?: 0
        } catch (_: Exception) { 0 }
        val photoRotation = if (clip.type == ClipType.IMAGE) clip.photoEdit.normalizedRotationSteps * 90 else 0
        CropSourceDimensions.oriented(options.outWidth, options.outHeight, exifRotation + photoRotation)
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
      catch (_: Exception) { null }
    currentCoroutineContext().ensureActive()
    return dimensions
}

private fun openCropSource(context: Context, source: String): InputStream? {
    val uri = Uri.parse(source)
    return when (uri.scheme) {
        null, "" -> File(source).inputStream()
        "file" -> File(uri.path ?: return null).inputStream()
        else -> context.contentResolver.openInputStream(uri)
    }
}
