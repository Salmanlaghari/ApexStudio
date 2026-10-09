package com.apexstudio.app.data.effect

import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlMatrixTransformation

/**
 * Pro Phase 1: aspect-ratio conversion for export (16:9 / 9:16 / 1:1).
 *
 * Center-crops the input frame to the target aspect ratio and scales the
 * crop window to [targetWidth] x [targetHeight]. The frame processor calls
 * [configure] with the real input dimensions and uses the returned [Size]
 * as the output size; [getGlMatrixArray] supplies the column-major 4x4 NDC
 * mapping that makes the centered crop window fill the output frame.
 *
 * All on-device, no extra dependencies. License-clean (own code).
 */
@UnstableApi
class AspectCropTransformation(
    private val targetWidth: Int,
    private val targetHeight: Int
) : GlMatrixTransformation {

    // Centered crop window in input pixels, computed in configure().
    // Centered => the NDC mapping is a pure scale (no translation).
    private var scaleX = 1f
    private var scaleY = 1f

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (inputWidth <= 0 || inputHeight <= 0) {
            scaleX = 1f
            scaleY = 1f
            return Size(targetWidth, targetHeight)
        }
        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
        val inputAspect = inputWidth.toFloat() / inputHeight.toFloat()

        // Close enough: no crop needed.
        if (kotlin.math.abs(inputAspect - targetAspect) / targetAspect < 0.02f) {
            scaleX = 1f
            scaleY = 1f
            return Size(inputWidth, inputHeight)
        }

        val (cropW, cropH) = if (inputAspect > targetAspect) {
            // Input wider than target: crop the sides.
            val h = inputHeight.toFloat()
            (h * targetAspect) to h
        } else {
            // Input taller than target: crop top/bottom.
            val w = inputWidth.toFloat()
            w to (w / targetAspect)
        }
        // Map crop window (input NDC) -> full output NDC (-1..1):
        // scale = input extent / crop extent per axis.
        scaleX = inputWidth.toFloat() / cropW
        scaleY = inputHeight.toFloat() / cropH
        return Size(targetWidth, targetHeight)
    }

    override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray {
        // Column-major 4x4: pure scale (centered crop needs no translation).
        return floatArrayOf(
            scaleX, 0f, 0f, 0f,
            0f, scaleY, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
    }

    companion object {
        /**
         * Target output dimensions for a resolution label + aspect ratio.
         * Resolution labels match the Export screen pills ("720p", "1080p", "4K", "8K"...).
         */
        fun targetSizeFor(resolution: String, aspectRatio: String): Pair<Int, Int> {
            val longEdge = when {
                resolution.contains("4320") || resolution.contains("8k", ignoreCase = true) -> 7680
                resolution.contains("2160") || resolution.contains("4k", ignoreCase = true) -> 3840
                resolution.contains("1440") -> 2560
                resolution.contains("720") -> 1280
                else -> 1920 // 1080p and below
            }
            val shortEdge = longEdge * 9 / 16
            return when (aspectRatio) {
                "9:16" -> shortEdge to longEdge
                "1:1" -> shortEdge to shortEdge
                else -> longEdge to shortEdge // "16:9"
            }
        }
    }
}
