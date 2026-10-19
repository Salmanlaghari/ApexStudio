package com.apexstudio.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Lazy timeline filmstrip frame cache (Item 1 — full-length clip +
 * thumbnails).
 *
 * The old pipeline eagerly decoded EVERY second of EVERY clip up front
 * (up to 180 decoder calls per clip) — a 3-minute clip took tens of
 * seconds to fill, so only its first seconds ever showed real frames.
 * This cache instead serves ONE frame at a time, on demand:
 *
 * - Memory: [LruCache] (32 MB, byte-counted). Evicted entries are NOT
 *   recycled — an evicted bitmap may still be displayed by a lane, and
 *   recycling a displayed bitmap crashes the draw. GC reclaims the
 *   native memory once the last reference drops.
 * - Disk: JPEG files under `cacheDir/timeline_thumbs`, capped at 128 MB
 *   (oldest-first eviction), so frames survive app restarts and a
 *   revisited clip fills instantly.
 * - Key: `uri + time + size` — a frame is only reused for the exact
 *   (clip, clip-relative time, pixel size) it was decoded for.
 *
 * Cancellation: [frameFor] checks [ensureActive] around the blocking
 * retriever call, so a scrolled-away range stops paying for frames it
 * no longer needs (the call itself runs to completion — one frame).
 */
object TimelineThumbnailCache {

    private const val TAG = "TimelineThumbnailCache"
    private const val MEMORY_MAX_BYTES = 32 * 1024 * 1024
    private const val DISK_MAX_BYTES = 128L * 1024 * 1024
    private const val DISK_DIR = "timeline_thumbs"
    private const val JPEG_QUALITY = 92

    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.byteCount.coerceAtLeast(1)
    }
    private val diskWriteLock = Any()

    private fun cacheKey(clip: com.apexstudio.app.domain.model.MediaClip, timeMs: Long, w: Int, h: Int): TimelineThumbnailCacheKey =
        TimelineThumbnailCacheKey.from(clip, timeMs, w, h)

    private fun diskFile(context: Context, key: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, DISK_DIR), "$digest.jpg")
    }

    /**
     * Frame for [clip] at [keyMs] (clip-relative ms from its trim start),
     * decoded/scaled to [w]×[h] px. Returns null on any failure — the
     * caller renders a placeholder. Never throws.
     */
    suspend fun frameFor(
        context: Context,
        clip: com.apexstudio.app.domain.model.MediaClip,
        keyMs: Long,
        w: Int,
        h: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        // Key on the SOURCE time (trimStart + clip-relative key), NOT
        // the clip-relative key alone: after a re-trim, key 0 is a
        // different source frame, and a clip-relative key would return
        // the old trim's frame. Same source frame -> same entry, so a
        // re-trim to an overlapping range reuses cached frames.
        val keyData = cacheKey(clip, keyMs, w, h)
        val key = keyData.toString()
        synchronized(memoryCache) { memoryCache.get(key) }?.let { return@withContext it }

        // Disk hit: decode + promote to memory.
        val file = diskFile(context, key)
        if (file.exists()) {
            try {
                val cached = BitmapFactory.decodeFile(file.absolutePath)
                if (cached != null) {
                    val bmp = cached
                    synchronized(memoryCache) { memoryCache.put(key, bmp) }
                    return@withContext bmp
                }
                // A truncated/stale JPEG must not be retried on every scroll pass.
                file.delete()
            } catch (e: Exception) {
                Log.w(TAG, "disk frame decode failed, re-extracting", e)
                file.delete()
            }
        }

        ensureActive()
        val bmp = extractFrame(context, clip, keyData.sourceTimeMs, w, h) ?: return@withContext null
        ensureActive()
        synchronized(memoryCache) { memoryCache.put(key, bmp) }
        try {
            writeDisk(file, bmp)
        } catch (e: Exception) {
            Log.w(TAG, "disk frame write failed", e)
        }
        bmp
    }

    private suspend fun extractFrame(
        context: Context,
        clip: com.apexstudio.app.domain.model.MediaClip,
        sourceTimeMs: Long,
        w: Int,
        h: Int
    ): Bitmap? {
        // IMAGE and image-backed OVERLAY clips share the same edit pipeline
        // as the preview/export. If the URI is actually video, a failed photo
        // decode falls through to MediaMetadataRetriever below.
        return imageFirstFallback(
            clip.type,
            imageDecode = {
                try {
                    com.apexstudio.app.data.photoedit.PhotoEditRenderer.renderEdited(
                        context, clip.uri, clip.photoEdit, maxDim = maxOf(w, h)
                    )?.let { scaled(it, w, h) }
                } catch (e: Exception) {
                    Log.w(TAG, "photo frame decode failed for ${clip.uri}", e)
                    null
                }
            },
            videoDecode = { extractVideoFrame(context, clip, sourceTimeMs, w, h) },
        )
    }

    /**
     * Try the photo renderer for stills and overlays before the video retriever.
     * Kept as a small seam so both overlay-image selection and video fallback
     * behavior can be tested without platform codec dependencies.
     */
    internal suspend fun <T> imageFirstFallback(
        type: com.apexstudio.app.domain.model.ClipType,
        imageDecode: suspend () -> T?,
        videoDecode: suspend () -> T?,
    ): T? {
        if (type == com.apexstudio.app.domain.model.ClipType.IMAGE ||
            type == com.apexstudio.app.domain.model.ClipType.OVERLAY
        ) {
            imageDecode()?.let { return it }
        }
        return videoDecode()
    }

    private suspend fun extractVideoFrame(
        context: Context,
        clip: com.apexstudio.app.domain.model.MediaClip,
        sourceTimeMs: Long,
        w: Int,
        h: Int,
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            val effectiveUri = if (clip.uri.startsWith("asset://") || clip.uri.isBlank()) {
                try {
                    SampleVideoGenerator.getOrCreateSampleVideo(context)
                } catch (e: Exception) {
                    clip.uri
                }
            } else clip.uri
            val parsed = Uri.parse(effectiveUri)
            if (effectiveUri.startsWith("/")) {
                retriever.setDataSource(effectiveUri)
            } else if (parsed.scheme == "file") {
                retriever.setDataSource(parsed.path ?: effectiveUri)
            } else {
                retriever.setDataSource(context, parsed)
            }
            val timeUs = if (sourceTimeMs > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE
                else sourceTimeMs * 1_000L
            val frame = try {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(timeUs)
            } catch (e: Exception) {
                Log.w(TAG, "getFrameAtTime($timeUs) failed for $effectiveUri", e)
                null
            } ?: return null
            return scaled(frame, w, h)
        } catch (e: Exception) {
            Log.w(TAG, "extractFrame failed for ${clip.uri}", e)
            return null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun scaled(src: Bitmap, w: Int, h: Int): Bitmap {
        if (src.width == w && src.height == h) return src
        val out = Bitmap.createScaledBitmap(src, w.coerceAtLeast(1), h.coerceAtLeast(1), true)
        if (out !== src) src.recycle()
        return out
    }

    private fun writeDisk(file: File, bmp: Bitmap) {
        synchronized(diskWriteLock) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            FileOutputStream(tmp).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            // Best-effort atomic swap; a concurrent writer of the same
            // key writes identical bytes, so a lost race is harmless.
            tmp.renameTo(file)
            enforceDiskCap(file.parentFile ?: return)
        }
    }

    private fun enforceDiskCap(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".jpg") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= DISK_MAX_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { f ->
            if (total <= DISK_MAX_BYTES) return@forEach
            total -= f.length()
            f.delete()
        }
    }
}
