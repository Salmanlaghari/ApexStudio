package com.apexstudio.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Extracts thumbnail frames from video files using MediaMetadataRetriever.
 * Thumbnails are cached to disk so repeated timeline renders don't
 * re-decode the same frames.
 */
object VideoThumbnailExtractor {
    private const val TAG = "VideoThumbnailExtractor"
    private const val THUMB_WIDTH = 120
    private const val THUMB_HEIGHT = 68
    private const val MAX_THUMBS_PER_CLIP = 20

    /**
     * Extract a single thumbnail at [timeMs] milliseconds into the video.
     * Returns null if extraction fails.
     */
    suspend fun extractFrame(
        context: Context,
        videoUri: String,
        timeMs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(videoUri))
            val bitmap = retriever.getFrameAtTime(
                timeMs * 1000, // microseconds
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            retriever.release()
            bitmap?.let { resize(it, THUMB_WIDTH, THUMB_HEIGHT) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract frame at ${timeMs}ms from $videoUri", e)
            null
        }
    }

    /**
     * Extract multiple evenly-spaced thumbnails across the clip duration.
     * Returns a list of (timeMs, bitmap) pairs. Failed extractions are skipped.
     */
    suspend fun extractStrip(
        context: Context,
        videoUri: String,
        durationMs: Long,
        count: Int = MAX_THUMBS_PER_CLIP
    ): List<Pair<Long, Bitmap>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<Long, Bitmap>>()
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(videoUri))
            val interval = durationMs / (count + 1)
            for (i in 1..count) {
                val timeMs = i * interval
                try {
                    val bitmap = retriever.getFrameAtTime(
                        timeMs * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    if (bitmap != null) {
                        val resized = resize(bitmap, THUMB_WIDTH, THUMB_HEIGHT)
                        if (resized != null) {
                            results.add(timeMs to resized)
                        }
                    }
                } catch (_: Exception) {
                    // Skip failed frames
                }
            }
            retriever.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract thumbnail strip from $videoUri", e)
        }
        results
    }

    /**
     * Get video duration in milliseconds. Returns 0 on failure.
     */
    suspend fun getDurationMs(context: Context, videoUri: String): Long = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(videoUri))
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            retriever.release()
            duration
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get duration for $videoUri", e)
            0L
        }
    }

    private fun resize(bitmap: Bitmap, targetW: Int, targetH: Int): Bitmap? {
        return try {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true).also {
                if (it != bitmap) bitmap.recycle()
            }
        } catch (e: Exception) {
            bitmap.recycle()
            null
        }
    }
}
