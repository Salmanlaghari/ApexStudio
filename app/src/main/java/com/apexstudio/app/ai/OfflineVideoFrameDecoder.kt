package com.apexstudio.app.ai

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build

/** A decoded analysis frame plus the video's display-oriented source pixel geometry. */
internal data class OfflineVideoFrame(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

/**
 * Shared bounded decoder for offline analysis. Android 8.1/API 27+ can scale during decoding, but
 * that path is used only when width, height, and rotation metadata are all valid. If metadata is
 * missing or malformed (or on API 26), decoding falls back to getFrameAtTime and scales the
 * resulting bitmap afterward; this compatibility path can still allocate a full-resolution frame.
 *
 * The caller owns the returned bitmap and must recycle it. The metadata geometry is never inferred
 * from a downscaled bitmap. If reliable geometry is unavailable on the legacy path, the decoded
 * bitmap's dimensions are used as the best available display geometry (not represented as source
 * resolution metadata).
 */
internal object OfflineVideoFrameDecoder {
    fun decode(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        maximumDimension: Int,
    ): OfflineVideoFrame? {
        require(timeUs >= 0L && maximumDimension > 0)
        val metadataGeometry = readMetadataGeometry(retriever)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && metadataGeometry != null) {
            val (width, height) = boundedDimensions(
                metadataGeometry.width,
                metadataGeometry.height,
                maximumDimension,
            )
            // A failed scaled decode is reported as a missing frame; do not silently allocate an
            // unbounded full-resolution bitmap in response to decoder failure.
            val bitmap = retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
                width,
                height,
            ) ?: return null
            return OfflineVideoFrame(bitmap, metadataGeometry.width, metadataGeometry.height)
        }

        // API 26 compatibility and unknown/malformed geometry: frame decoding may allocate at
        // source resolution before the bitmap is scaled. There is no safe scaled-decode size to
        // request until a trustworthy aspect ratio is known.
        val decoded = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
        val geometry = metadataGeometry ?: SourceGeometry(decoded.width, decoded.height)
        val (width, height) = boundedDimensions(decoded.width, decoded.height, maximumDimension)
        val output = try {
            if (width == decoded.width && height == decoded.height) decoded
            else Bitmap.createScaledBitmap(decoded, width, height, true)
        } catch (failure: Throwable) {
            decoded.recycle()
            throw failure
        }
        if (output !== decoded) decoded.recycle()
        return OfflineVideoFrame(output, geometry.width, geometry.height)
    }

    private fun readMetadataGeometry(retriever: MediaMetadataRetriever): SourceGeometry? {
        return try {
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
            if (width == null || height == null || width <= 0 || height <= 0 ||
                rotation == null || rotation !in setOf(0, 90, 180, 270)
            ) return null
            if (rotation == 90 || rotation == 270) SourceGeometry(height, width)
            else SourceGeometry(width, height)
        } catch (_: RuntimeException) {
            // Treat unavailable platform metadata like malformed metadata: use the documented
            // full-frame compatibility path rather than guessing a scaled decode aspect ratio.
            null
        }
    }

    private fun boundedDimensions(width: Int, height: Int, maximumDimension: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= maximumDimension) return width to height
        val scale = maximumDimension.toDouble() / longest
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    private data class SourceGeometry(val width: Int, val height: Int)
}
