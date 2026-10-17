package com.apexstudio.app.data.stickers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the path classification that decides whether the canvas and the
 * export shader animate a sticker (`LottieFrameCache`) or draw it as a
 * bitmap.
 *
 * Regression risk: after adding the animated pack (Bug 2) the path
 * became `stickers/animated/*.json`. With the old `startsWith("lottie/")`
 * check the pack previewed nowhere and exported as an empty layer, while
 * loosening the rule to "any `.json`" would mispaint nothing
 * today but would break the instant a manifest or other data file is
 * handed to the canvas. Both directions are pinned here.
 *
 * Pure JVM — no Android, no rendering.
 */
class LottieFrameCacheTest {

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
