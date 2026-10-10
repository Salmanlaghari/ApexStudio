package com.apexstudio.app.data.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.util.LruCache
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable

/**
 * Renders Lottie animations (assets/lottie/ JSON files — original ApexStudio
 * animations, plus the Apache-2.0 lottie-android runtime) into bitmap frame
 * strips so [com.apexstudio.app.data.effect.StickerGlEffect] can bake
 * *animated* stickers into exports.
 *
 * Frames are rendered once per asset at a modest size and cached in an LRU;
 * the GL effect picks frames by presentation time and re-uploads only when
 * the frame index changes.
 */
object LottieFrameCache {

    private const val TAG = "LottieFrameCache"

    /** Frames pre-rendered per animation (enough for smooth short loops). */
    const val FRAME_COUNT = 24

    /** Render size of each frame (square; sprite renderer handles placement). */
    const val FRAME_SIZE = 256

    private val cache = object : LruCache<String, List<Bitmap>>(4) {
        override fun sizeOf(key: String, value: List<Bitmap>): Int {
            // ~256KB per frame.
            return value.size
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: List<Bitmap>,
            newValue: List<Bitmap>?
        ) {
            oldValue.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    /** True for sticker asset paths that are Lottie animations. */
    fun isLottieAsset(assetPath: String?): Boolean =
        assetPath?.startsWith("lottie/") == true && assetPath.endsWith(".json")

    /**
     * Returns the cached frame strip for [assetPath] (assets-relative, e.g.
     * "lottie/bouncing_ball.json"), rendering it on first use. Null when
     * the composition can't be loaded.
     */
    @Synchronized
    fun frames(context: Context, assetPath: String): List<Bitmap>? {
        cache.get(assetPath)?.let { return it }
        return try {
            val result = LottieCompositionFactory.fromAssetSync(context, assetPath)
            val composition = result.value
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
            cache.put(assetPath, frames)
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
