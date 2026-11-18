package com.apexstudio.app.data.adjust

import com.apexstudio.app.domain.model.HslColorRange
import com.apexstudio.app.domain.model.SelectiveHslAdjustments
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * CPU reference for the selective HSL operation used by the Media3 shader.
 * The output array is caller-owned so bitmap loops can reuse it without a
 * per-pixel allocation.
 */
object SelectiveHslMath {
    private val ranges = HslColorRange.values()

    fun apply(
        red: Float,
        green: Float,
        blue: Float,
        adjustments: SelectiveHslAdjustments,
        output: FloatArray
    ) {
        require(output.size >= 3) { "HSL output buffer must contain three channels" }
        if (adjustments.isDefault) {
            output[0] = red
            output[1] = green
            output[2] = blue
            return
        }

        val sourceRed = red.coerceIn(0f, 1f)
        val sourceGreen = green.coerceIn(0f, 1f)
        val sourceBlue = blue.coerceIn(0f, 1f)
        val maximum = max(sourceRed, max(sourceGreen, sourceBlue))
        val minimum = min(sourceRed, min(sourceGreen, sourceBlue))
        val delta = maximum - minimum
        val lightness = (maximum + minimum) * 0.5f
        val saturation = if (delta <= 0.00001f) 0f else delta / (1f - abs(2f * lightness - 1f)).coerceAtLeast(0.00001f)
        if (saturation <= 0.00001f) {
            output[0] = red
            output[1] = green
            output[2] = blue
            return
        }

        val hue = when {
            maximum == sourceRed -> 60f * (((sourceGreen - sourceBlue) / delta) % 6f)
            maximum == sourceGreen -> 60f * ((sourceBlue - sourceRed) / delta + 2f)
            else -> 60f * ((sourceRed - sourceGreen) / delta + 4f)
        }.let { if (it < 0f) it + 360f else it }

        var hueShift = 0f
        var saturationShift = 0f
        var lightnessShift = 0f
        ranges.forEach { range ->
            val band = adjustments.forRange(range)
            val distance = abs(hue - range.centerDegrees).let { min(it, 360f - it) }
            val t = (distance / 60f).coerceIn(0f, 1f)
            val weight = 1f - t * t * (3f - 2f * t)
            hueShift += band.hueShift.coerceIn(-180f, 180f) * weight
            saturationShift += band.saturation.coerceIn(-1f, 1f) * weight
            lightnessShift += band.lightness.coerceIn(-1f, 1f) * weight
        }

        val adjustedHue = (hue + hueShift + 360f) % 360f
        val adjustedSaturation = (saturation + saturationShift).coerceIn(0f, 1f)
        val adjustedLightness = (lightness + lightnessShift).coerceIn(0f, 1f)
        hslToRgb(adjustedHue, adjustedSaturation, adjustedLightness, output)
    }

    private fun hslToRgb(hueDegrees: Float, saturation: Float, lightness: Float, output: FloatArray) {
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val hueSector = (hueDegrees % 360f) / 60f
        val secondary = chroma * (1f - abs(hueSector % 2f - 1f))
        var red = 0f
        var green = 0f
        var blue = 0f
        when {
            hueSector < 1f -> { red = chroma; green = secondary }
            hueSector < 2f -> { red = secondary; green = chroma }
            hueSector < 3f -> { green = chroma; blue = secondary }
            hueSector < 4f -> { green = secondary; blue = chroma }
            hueSector < 5f -> { red = secondary; blue = chroma }
            else -> { red = chroma; blue = secondary }
        }
        val match = lightness - chroma * 0.5f
        output[0] = red + match
        output[1] = green + match
        output[2] = blue + match
    }
}
