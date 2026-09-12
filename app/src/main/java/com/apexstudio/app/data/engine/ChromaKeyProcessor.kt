package com.apexstudio.app.data.engine

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Real 3D ChromaKey pixel processing engine.
 * Computes Euclidean color distance in normalized RGB space, applies Hermite smoothstep
 * alpha falloff, and suppresses key color spill on foreground edges.
 */
object ChromaKeyProcessor {

    data class KeyingParams(
        val keyColor: Int,          // 0xAARRGGBB
        val similarity: Float,      // 0.01f..1.0f threshold
        val smoothness: Float,      // 0.01f..0.5f transition softness
        val spillSuppression: Float // 0.0f..1.0f spill desaturation
    )

    fun processFrame(
        source: Bitmap,
        params: KeyingParams,
        background: Bitmap? = null,
        matteViewOnly: Boolean = false
    ): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        val bgPixels = if (background != null) {
            val scaledBg = if (background.width != width || background.height != height) {
                Bitmap.createScaledBitmap(background, width, height, true)
            } else background
            val arr = IntArray(width * height)
            scaledBg.getPixels(arr, 0, width, 0, 0, width, height)
            arr
        } else null

        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val keyR = AndroidColor.red(params.keyColor) / 255f
        val keyG = AndroidColor.green(params.keyColor) / 255f
        val keyB = AndroidColor.blue(params.keyColor) / 255f

        val sim = params.similarity.coerceIn(0.01f, 1.0f)
        val smooth = params.smoothness.coerceIn(0.01f, 0.5f)
        val spill = params.spillSuppression.coerceIn(0f, 1f)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = AndroidColor.red(p) / 255f
            val g = AndroidColor.green(p) / 255f
            val b = AndroidColor.blue(p) / 255f

            // Euclidean distance in RGB color space normalized to [0, 1]
            val dr = r - keyR
            val dg = g - keyG
            val db = b - keyB
            val dist = sqrt((dr * dr + dg * dg + db * db) / 3f)

            // Smoothstep Hermite curve: t in [sim, sim + smooth]
            val t = ((dist - sim) / smooth).coerceIn(0f, 1f)
            val alpha = t * t * (3f - 2f * t)

            if (matteViewOnly) {
                val gray = (alpha * 255).toInt().coerceIn(0, 255)
                pixels[i] = AndroidColor.argb(255, gray, gray, gray)
                continue
            }

            // Spill suppression: desaturate prominent key channel
            var outR = r
            var outG = g
            var outB = b

            if (keyG > keyR && keyG > keyB) {
                // Green screen spill suppression
                val maxOther = max(r, b)
                if (g > maxOther) {
                    outG = g - (g - maxOther) * spill
                }
            } else if (keyB > keyR && keyB > keyG) {
                // Blue screen spill suppression
                val maxOther = max(r, g)
                if (b > maxOther) {
                    outB = b - (b - maxOther) * spill
                }
            }

            val fgR = (outR * 255).toInt().coerceIn(0, 255)
            val fgG = (outG * 255).toInt().coerceIn(0, 255)
            val fgB = (outB * 255).toInt().coerceIn(0, 255)
            val fgA = (alpha * 255).toInt().coerceIn(0, 255)

            if (bgPixels != null) {
                val bg = bgPixels[i]
                val bgR = AndroidColor.red(bg)
                val bgG = AndroidColor.green(bg)
                val bgB = AndroidColor.blue(bg)

                // Alpha composite foreground over background
                val aNorm = alpha
                val cR = (fgR * aNorm + bgR * (1f - aNorm)).toInt().coerceIn(0, 255)
                val cG = (fgG * aNorm + bgG * (1f - aNorm)).toInt().coerceIn(0, 255)
                val cB = (fgB * aNorm + bgB * (1f - aNorm)).toInt().coerceIn(0, 255)
                pixels[i] = AndroidColor.argb(255, cR, cG, cB)
            } else {
                pixels[i] = AndroidColor.argb(fgA, fgR, fgG, fgB)
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
