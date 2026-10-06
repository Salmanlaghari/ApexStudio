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

    private val cache = object : LruCache<String, Bitmap>(MAX_ENTRIES) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
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
        private const val MAX_ENTRIES = 24
    }
}
