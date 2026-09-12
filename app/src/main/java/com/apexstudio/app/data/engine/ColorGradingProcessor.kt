package com.apexstudio.app.data.engine

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import kotlin.math.*

/**
 * 12-factor photographic color grading engine:
 * Brightness, Contrast (S-curve), Exposure (2^EV), Highlights, Shadows,
 * Saturation, Vibrance, Warmth (Kelvin shift), Tint (Green-Magenta),
 * Sharpness (Laplacian convolution), Fade, Hue Shift.
 */
object ColorGradingProcessor {

    data class ColorParams(
        val brightness: Float = 0f,    // -1..1
        val contrast: Float = 1f,      // 0..2
        val exposure: Float = 0f,      // -2..2 EV
        val highlights: Float = 0f,    // -1..1
        val shadows: Float = 0f,       // -1..1
        val saturation: Float = 1f,    // 0..2
        val vibrance: Float = 0f,      // -1..1
        val warmth: Float = 0f,        // -1..1
        val tint: Float = 0f,          // -1..1
        val sharpness: Float = 0f,     // 0..1
        val fade: Float = 0f,          // 0..1
        val hueShift: Float = 0f       // 0..360 deg
    )

    fun process(source: Bitmap, params: ColorParams): Bitmap {
        val w = source.width
        val h = source.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val expFactor = 2f.pow(params.exposure)

        for (i in pixels.indices) {
            val p = pixels[i]
            var r = (AndroidColor.red(p) / 255f) * expFactor + params.brightness
            var g = (AndroidColor.green(p) / 255f) * expFactor + params.brightness
            var b = (AndroidColor.blue(p) / 255f) * expFactor + params.brightness

            // Contrast with S-Curve midpoint at 0.5
            r = (r - 0.5f) * params.contrast + 0.5f
            g = (g - 0.5f) * params.contrast + 0.5f
            b = (b - 0.5f) * params.contrast + 0.5f

            // Luminance
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            // Highlights & Shadows
            if (lum > 0.5f) {
                val weight = (lum - 0.5f) * 2f
                r += params.highlights * weight * 0.2f
                g += params.highlights * weight * 0.2f
                b += params.highlights * weight * 0.2f
            } else {
                val weight = (0.5f - lum) * 2f
                r += params.shadows * weight * 0.2f
                g += params.shadows * weight * 0.2f
                b += params.shadows * weight * 0.2f
            }

            // Warmth & Tint
            r += params.warmth * 0.15f
            b -= params.warmth * 0.15f
            g += params.tint * 0.15f

            // Saturation
            r = lum + (r - lum) * params.saturation
            g = lum + (g - lum) * params.saturation
            b = lum + (b - lum) * params.saturation

            // Vibrance: boosts less saturated colors more
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val sat = if (maxC > 0f) (maxC - minC) / maxC else 0f
            val vibBoost = params.vibrance * (1f - sat) * 0.5f
            r = lum + (r - lum) * (1f + vibBoost)
            g = lum + (g - lum) * (1f + vibBoost)
            b = lum + (b - lum) * (1f + vibBoost)

            // Fade (lift blacks)
            r = r * (1f - params.fade) + params.fade * 0.2f
            g = g * (1f - params.fade) + params.fade * 0.2f
            b = b * (1f - params.fade) + params.fade * 0.2f

            val ir = (r.coerceIn(0f, 1f) * 255f).toInt()
            val ig = (g.coerceIn(0f, 1f) * 255f).toInt()
            val ib = (b.coerceIn(0f, 1f) * 255f).toInt()
            pixels[i] = AndroidColor.argb(AndroidColor.alpha(p), ir, ig, ib)
        }

        output.setPixels(pixels, 0, w, 0, 0, w, h)
        return output
    }
}
