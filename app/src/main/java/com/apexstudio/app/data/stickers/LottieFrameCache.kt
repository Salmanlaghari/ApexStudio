package com.apexstudio.app.data.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.util.LruCache
import com.airbnb.lottie.LottieDrawable

/**
 * Renders Lottie animations (assets/lottie/ JSON files — original ApexStudio
 * animations, plus the Apache-2.0 lottie-android runtime) into bitmap frame
 * strips so [com.apexstudio.app.data.effect.StickerGlEffect] can bake
 * *animated* stickers into exports.
 *
 * Frames are rendered once per asset at a modest size and cached in an LRU;
 * the GL effect picks frames by presentation time and re-uploads only when
 * the frame index changes. Eviction drops only the cache's reference: a GL
 * effect may still be using the returned strip, so frames are left for GC.
 */
object LottieFrameCache {

    private const val TAG = "LottieFrameCache"

    /** Frames pre-rendered per animation (enough for smooth short loops). */
    const val FRAME_COUNT = 24

    /** Retain up to four animation strips; cache size is measured in frames. */
    private const val CACHED_ANIMATION_COUNT = 4

    /** Render size of each frame (square; sprite renderer handles placement). */
    const val FRAME_SIZE = 256

    private val cache = object : LruCache<String, List<Bitmap>>(FRAME_COUNT * CACHED_ANIMATION_COUNT) {
        override fun sizeOf(key: String, value: List<Bitmap>): Int {
            // ~256KB per frame.
            return value.size
        }
    }

    /**
     * True for sticker asset paths that are Lottie animations.
     *
     * Recognises bundled assets and the stable `stickerpack://` paths
     * stored for verified on-demand packs. The same resolver is used by
     * the picker, editor canvas, and export shader.
     *
     * Anything else ending in `.json` is rejected on purpose: the static
     * packs are `.webp` / `.svg` and must keep falling through to the
     * image path.
     */
    fun isLottieAsset(assetPath: String?): Boolean =
        StickerAssetResolver.isLottieSticker(assetPath)

    /**
     * Returns the cached frame strip for [assetPath] (assets-relative, e.g.
     * "lottie/bouncing_ball.json"), rendering it on first use. Null when
     * the composition can't be loaded.
     */
    @Synchronized
    fun frames(context: Context, assetPath: String): List<Bitmap>? {
        val resolvedCacheKey = if (StickerAssetResolver.isPackUri(assetPath)) {
            val file = StickerAssetResolver.downloadedFile(context, assetPath) ?: return null
            "$assetPath:${file.length()}:${file.lastModified()}"
        } else assetPath
        cache.get(resolvedCacheKey)?.let { return it }
        return try {
            val composition = StickerAssetResolver.loadComposition(context, assetPath)
                ?: run {
                    Log.w(TAG, "Lottie composition failed: $assetPath")
                    return null
                }
            val drawable = LottieDrawable()
            drawable.composition = composition
            drawable.setBounds(0, 0, FRAME_SIZE, FRAME_SIZE)
            val frames = ArrayList<Bitmap>(FRAME_COUNT)
            for (i in 0 until FRAME_COUNT) {
                drawable.progress = i.toFloat() / FRAME_COUNT
                val bmp = Bitmap.createBitmap(
                    FRAME_SIZE, FRAME_SIZE, Bitmap.Config.ARGB_8888
                )
                drawable.draw(Canvas(bmp))
                frames += bmp
            }
            cache.put(resolvedCacheKey, frames)
            frames
        } catch (e: Exception) {
            Log.w(TAG, "Lottie frame render failed: $assetPath", e)
            null
        }
    }

    /**
     * Frame index for [timeMs] within a sticker whose animation loops.
     * Animation time is relative to the sticker's visible start.
     */
    fun frameIndex(timeMs: Long, stickerStartMs: Long, frameCount: Int): Int {
        if (frameCount <= 0) return 0
        // 30fps timeline -> frame every ~33ms; loop the strip.
        val rel = (timeMs - stickerStartMs).coerceAtLeast(0L)
        return ((rel / 33) % frameCount).toInt()
    }
}
