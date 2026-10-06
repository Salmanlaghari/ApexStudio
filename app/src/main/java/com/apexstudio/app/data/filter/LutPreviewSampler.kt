package com.apexstudio.app.data.filter

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Derives live-preview grade characteristics from the REAL 3D LUT.
 *
 * The editor preview renders video through a native ExoPlayer PlayerView,
 * so Compose cannot apply a per-pixel LUT there. Instead of the old
 * hand-written per-filter gradient overlays (which never matched the real
 * filter), the preview overlay now samples the actual LUT and reproduces
 * its true color cast + tone response as layered blend washes.
 *
 * What is sampled:
 * - Midtone cast: LUT(0.5, 0.5, 0.5) — the filter's real color tint.
 * - Shadow cast:  LUT(0.18, 0.18, 0.18) — tint in the shadows.
 * - Highlight cast: LUT(0.82, 0.82, 0.82) — tint in the highlights.
 * - Tone contrast: how much the LUT stretches/compresses the
 *   0.1 → 0.9 luminance range vs. the identity.
 * - Desaturation: how much the LUT pulls colors toward gray.
 */
object LutPreviewSampler {

    data class LutGrade(
        /** The filter's real midtone color cast. */
        val midCast: Color,
        /** Shadow-region tint. */
        val shadowCast: Color,
        /** Highlight-region tint. */
        val highlightCast: Color,
        /** >0 = LUT adds contrast, <0 = LUT flattens. Range roughly -1..1. */
        val contrast: Float,
        /** 0 = keeps saturation, 1 = fully desaturates. */
        val desaturation: Float,
        /** Overall strength of the color shift, 0..1. */
        val strength: Float
    )

    fun sampleGrade(texture: LutTexture): LutGrade {
        val mid = LutBitmapCache.sample(texture, 0.5f, 0.5f, 0.5f)
        val shadow = LutBitmapCache.sample(texture, 0.18f, 0.18f, 0.18f)
        val highlight = LutBitmapCache.sample(texture, 0.82f, 0.82f, 0.82f)
        val dark = LutBitmapCache.sample(texture, 0.1f, 0.1f, 0.1f)
        val bright = LutBitmapCache.sample(texture, 0.9f, 0.9f, 0.9f)

        fun lum(c: FloatArray) = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2]

        // Contrast: LUT's 0.1->0.9 luminance stretch vs identity (0.8).
        val lutRange = lum(bright) - lum(dark)
        val contrast = ((lutRange - 0.8f) / 0.8f).coerceIn(-1f, 1f)

        // Desaturation: sample a saturated red through the LUT.
        val red = LutBitmapCache.sample(texture, 1f, 0.15f, 0.1f)
        val redSatIn = max(1f, max(0.15f, 0.1f)) - min(1f, min(0.15f, 0.1f)) // 0.9
        val redSatOut = max(red[0], max(red[1], red[2])) - min(red[0], min(red[1], red[2]))
        val desaturation = (1f - redSatOut / redSatIn.coerceAtLeast(0.001f)).coerceIn(0f, 1f)

        // Strength: how far the midtone cast deviates from neutral gray.
        val midLum = lum(mid)
        val castDist = abs(mid[0] - midLum) + abs(mid[1] - midLum) + abs(mid[2] - midLum)
        val strength = (castDist * 2.5f + desaturation * 0.5f + abs(contrast) * 0.5f)
            .coerceIn(0f, 1f)

        fun toColor(c: FloatArray) = Color(c[0].coerceIn(0f, 1f), c[1].coerceIn(0f, 1f), c[2].coerceIn(0f, 1f))

        return LutGrade(
            midCast = toColor(mid),
            shadowCast = toColor(shadow),
            highlightCast = toColor(highlight),
            contrast = contrast,
            desaturation = desaturation,
            strength = strength
        )
    }
}
