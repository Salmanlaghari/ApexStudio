package com.apexstudio.app.data.stickers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Guards the path classification that decides whether the canvas and the
 * export shader animate a sticker (`LottieFrameCache`) or draw it as a
 * bitmap.
 *
 * Regression risk: after adding the animated pack (Bug 2) the path
 * became JSON files under `stickers/animated/`. With the old `startsWith("lottie/")`
 * check the pack previewed nowhere and exported as an empty layer, while
 * loosening the rule to "any `.json`" would mispaint nothing
 * today but would break the instant a manifest or other data file is
 * handed to the canvas. Both directions are pinned here.
 *
 * Asset classification is pure; the eviction regression uses Robolectric to
 * retain real Android bitmaps across production-cache eviction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LottieFrameCacheTest {

    @Test
    fun `evicting a frame strip does not recycle bitmaps still held by its consumer`() {
        @Suppress("UNCHECKED_CAST")
        val cache = LottieFrameCache::class.java.getDeclaredField("cache").let { field ->
            field.isAccessible = true
            field.get(LottieFrameCache) as android.util.LruCache<String, List<android.graphics.Bitmap>>
        }
        cache.evictAll()
        val strips = (0..4).map {
            List(24) { android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888) }
        }
        val inUseBitmap = strips.first().first()
        inUseBitmap.eraseColor(android.graphics.Color.MAGENTA)
        try {
            strips.forEachIndexed { index, strip -> cache.put("consumer-owned-$index", strip) }
            org.junit.Assert.assertNull("The first strip must actually be evicted", cache.get("consumer-owned-0"))
            assertFalse("An evicted strip may still be in use by StickerGlEffect", inUseBitmap.isRecycled)
            assertEquals(android.graphics.Color.MAGENTA, inUseBitmap.getPixel(0, 0))
        } finally {
            cache.evictAll()
            strips.flatten().forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    @Test
    fun `original animations under lottie stay recognised`() {
        assertTrue(LottieFrameCache.isLottieAsset("lottie/bouncing_ball.json"))
        assertTrue(LottieFrameCache.isLottieAsset("lottie/heart_pop.json"))
        assertTrue(LottieFrameCache.isLottieAsset("lottie/walking_cat.json"))
    }

    @Test
    fun `animated pack paths are recognised`() {
        assertTrue(LottieFrameCache.isLottieAsset("stickers/animated/hearts/beating-heart.json"))
        assertTrue(LottieFrameCache.isLottieAsset("stickers/animated/animals/froggy.json"))
    }

    @Test
    fun `static webp and svg packs are not lottie`() {
        assertFalse(LottieFrameCache.isLottieAsset("stickers/love/red-heart.webp"))
        assertFalse(LottieFrameCache.isLottieAsset("stickers/social/like.svg"))
        assertFalse(LottieFrameCache.isLottieAsset("stickers/Halo.webp"))
    }

    @Test
    fun `json outside both animation directories is rejected`() {
        assertFalse(LottieFrameCache.isLottieAsset("sticks/animated/nope.json"))
        assertFalse(LottieFrameCache.isLottieAsset("stickers/manifest.json"))
        assertFalse(LottieFrameCache.isLottieAsset("animation.json"))
        assertFalse(LottieFrameCache.isLottieAsset("lottie/notes.txt"))
    }

    @Test
    fun `null path never resolves to an animation`() {
        assertFalse(LottieFrameCache.isLottieAsset(null))
        assertFalse(LottieFrameCache.isLottieAsset(""))
    }
}
