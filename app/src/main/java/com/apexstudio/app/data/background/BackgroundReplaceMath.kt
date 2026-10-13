package com.apexstudio.app.data.background

import kotlin.math.max

/**
 * Pure math for background replace (PR-G) — no Android dependencies,
 * so it is covered by JVM unit tests.
 *
 * The pixel work lives in [FrameCompositor]; this object holds only
 * the mapping functions both the engine and the tests agree on.
 */
object BackgroundReplaceMath {

    /**
     * Smoothstep edge feather for a person-confidence value in 0..1.
     * Below [low] the pixel is fully background (0), above [high]
     * fully person (1), with a smooth S-curve between — the soft
     * edge around hair / clothing that a hard 0.5 threshold would
     * turn into a jagged halo.
     */
    fun smoothstepMask(confidence: Float, low: Float = 0.35f, high: Float = 0.65f): Float {
        if (confidence <= low) return 0f
        if (confidence >= high) return 1f
        val t = ((confidence - low) / (high - low)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Blur divisor for [blurStrength] 0..1: the frame is downscaled
     * by this factor and upscaled again (bilinear), which is the
     * blur. 0 = light blur (÷4), 1 = strongest (÷24). Monotonic so
     * dragging the slider right always blurs more, never less.
     */
    fun blurDivisorFor(blurStrength: Float): Int =
        (4 + blurStrength.coerceIn(0f, 1f) * 20f).toInt().coerceAtLeast(1)

    /**
     * Downscaled working size for a blur pass: never below 1 px in
     * either dimension, even for tiny frames.
     */
    fun blurredSize(size: Int, divisor: Int): Int =
        max(1, size / divisor.coerceAtLeast(1))

    /**
     * Cover-fit rect: scale [srcW]×[srcH] to COVER [dstW]×[dstH]
     * (no letterboxing) and return the source rect to crop, centred.
     * Used to fit a picked background image to the video frame.
     * Returns (srcLeft, srcTop, srcRight, srcBottom) in source pixels.
     */
    fun coverCropRect(srcW: Int, srcH: Int, dstW: Int, dstH: Int): FloatArray {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) {
            return floatArrayOf(0f, 0f, srcW.toFloat(), srcH.toFloat())
        }
        val srcAspect = srcW.toFloat() / srcH
        val dstAspect = dstW.toFloat() / dstH
        return if (srcAspect > dstAspect) {
            // Source wider than destination: crop the sides.
            val cropW = srcH * dstAspect
            val left = (srcW - cropW) / 2f
            floatArrayOf(left, 0f, left + cropW, srcH.toFloat())
        } else {
            // Source taller: crop top/bottom.
            val cropH = srcW / dstAspect
            val top = (srcH - cropH) / 2f
            floatArrayOf(0f, top, srcW.toFloat(), top + cropH)
        }
    }
}
