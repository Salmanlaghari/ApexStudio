package com.apexstudio.app.data.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/**
 * In-memory cache of decoded sticker PNGs (512px RGBA each).
 *
 * Loaded lazily from `assets/stickers/...` on the calling thread — call
 * from a background dispatcher for first loads; subsequent reads are
 * cache hits.
 */
class StickerImageCache(context: Context) {

    private val appContext = context.applicationContext

    // Byte-based budget: a 512×512 RGBA sticker is ~1 MB, so the cache
    // holds roughly MAX_SIZE_KB worth of decoded PNGs instead of a fixed
    // entry count that could balloon on large bitmaps.
    private val cache = object : LruCache<String, Bitmap>(MAX_SIZE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    /**
     * Decode the sticker PNG at [assetPath] (e.g. "stickers/love/red-heart.png"),
     * or null when the asset is missing / undecodable.
     */
    @Synchronized
    fun get(assetPath: String): Bitmap? {
        cache.get(assetPath)?.let { return it }
        val bitmap = try {
            appContext.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (_: Exception) {
            null
        }
        if (bitmap != null) cache.put(assetPath, bitmap)
        return bitmap
    }

    @Synchronized
    fun evictAll() = cache.evictAll()

    companion object {
        /** Decoded-bitmap budget for the cache (kilobytes). */
        private const val MAX_SIZE_KB = 8 * 1024
    }
}
