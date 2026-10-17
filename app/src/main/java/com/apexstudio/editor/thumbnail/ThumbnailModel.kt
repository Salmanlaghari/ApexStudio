package com.apexstudio.editor.thumbnail

import android.graphics.Bitmap

/**
 * Represents a single video thumbnail frame.
 *
 * @property id Unique identifier for the thumbnail.
 * @property timestampMs The position of the frame in the source video, in milliseconds.
 * @property bitmap The decoded thumbnail image, or `null` if it has not been generated or was recycled.
 */
data class Thumbnail(
    val id: String,
    val timestampMs: Long,
    val bitmap: Bitmap?
)

/**
 * Represents an ordered collection of thumbnails extracted from a single video.
 *
 * @property videoUri URI of the source video.
 * @property durationMs Total duration of the source video, in milliseconds.
 * @property intervalMs Time gap between consecutive thumbnails, in milliseconds. Defaults to 1000 ms.
 * @property thumbnails Ordered list of generated thumbnails for the video.
 */
data class ThumbnailSequence(
    val videoUri: String,
    val durationMs: Long,
    val intervalMs: Long = 1000,
    val thumbnails: List<Thumbnail>
)
