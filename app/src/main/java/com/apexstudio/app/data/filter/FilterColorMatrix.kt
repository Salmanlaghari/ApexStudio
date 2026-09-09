package com.apexstudio.app.data.filter

import android.graphics.ColorMatrix as AndroidColorMatrix
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix

/**
 * Provides real-time Color Matrix calculations for all filter presets.
 * Used by:
 * 1. The main video preview viewport (applied directly on the video canvas/viewport
 *    in real time as the user selects a filter or adjusts the intensity slider).
 * 2. Instant dynamic thumbnail generation (renders all 77 filter cards in <10ms).
 */
object FilterColorMatrix {

    private val IDENTITY = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    fun getRawMatrix(filterId: String?): FloatArray {
        return when (filterId) {
            // --- Monochromatic & B/W ---
            "graphite" -> floatArrayOf(
                0.33f, 0.45f, 0.15f, 0f, -8f,
                0.30f, 0.48f, 0.15f, 0f, -8f,
                0.30f, 0.45f, 0.18f, 0f, -4f,
                0f, 0f, 0f, 1f, 0f
            )
            "noir_classic" -> floatArrayOf(
                0.35f, 0.60f, 0.15f, 0f, -15f,
                0.35f, 0.60f, 0.15f, 0f, -15f,
                0.35f, 0.60f, 0.15f, 0f, -15f,
                0f, 0f, 0f, 1f, 0f
            )
            "high_contrast_charcoal" -> floatArrayOf(
                0.40f, 0.70f, 0.20f, 0f, -30f,
                0.40f, 0.70f, 0.20f, 0f, -30f,
                0.40f, 0.70f, 0.20f, 0f, -30f,
                0f, 0f, 0f, 1f, 0f
            )
            "silver_oxide" -> floatArrayOf(
                0.28f, 0.55f, 0.20f, 0f, 20f,
                0.28f, 0.55f, 0.20f, 0f, 20f,
                0.30f, 0.55f, 0.25f, 0f, 25f,
                0f, 0f, 0f, 1f, 0f
            )
            "rich_black" -> floatArrayOf(
                0.38f, 0.65f, 0.15f, 0f, -25f,
                0.38f, 0.65f, 0.15f, 0f, -25f,
                0.38f, 0.65f, 0.15f, 0f, -25f,
                0f, 0f, 0f, 1f, 0f
            )
            "film_bw_warm" -> floatArrayOf(
                0.35f, 0.55f, 0.15f, 0f, 15f,
                0.32f, 0.53f, 0.15f, 0f, 8f,
                0.28f, 0.48f, 0.14f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )
            "film_bw_cool" -> floatArrayOf(
                0.28f, 0.50f, 0.18f, 0f, -8f,
                0.30f, 0.52f, 0.20f, 0f, 5f,
                0.33f, 0.55f, 0.25f, 0f, 22f,
                0f, 0f, 0f, 1f, 0f
            )
            "ink_wash" -> floatArrayOf(
                0.45f, 0.55f, 0.10f, 0f, -20f,
                0.45f, 0.55f, 0.10f, 0f, -20f,
                0.45f, 0.55f, 0.15f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )
            "classic_mono" -> floatArrayOf(
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            "high_key_mono" -> floatArrayOf(
                0.30f, 0.58f, 0.12f, 0f, 40f,
                0.30f, 0.58f, 0.12f, 0f, 40f,
                0.30f, 0.58f, 0.12f, 0f, 40f,
                0f, 0f, 0f, 1f, 0f
            )

            // --- Cinematic ---
            "teal_orange" -> floatArrayOf(
                1.30f, -0.05f, -0.15f, 0f, 25f,
                -0.05f, 1.10f, 0.05f, 0f, -5f,
                -0.20f, 0.15f, 1.35f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            )
            "hollywood" -> floatArrayOf(
                1.22f, 0.05f, -0.10f, 0f, 18f,
                0.02f, 1.12f, -0.05f, 0f, 10f,
                -0.10f, -0.05f, 1.05f, 0f, -12f,
                0f, 0f, 0f, 1f, 0f
            )
            "moody_blockbuster" -> floatArrayOf(
                1.15f, 0f, -0.10f, 0f, -10f,
                -0.05f, 1.05f, 0.05f, 0f, -15f,
                -0.10f, 0.10f, 1.30f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
            "matrix_green" -> floatArrayOf(
                0.60f, 0.20f, 0f, 0f, -20f,
                0.15f, 1.45f, 0.10f, 0f, 25f,
                0.10f, 0.20f, 0.55f, 0f, -25f,
                0f, 0f, 0f, 1f, 0f
            )
            "cinema_teal" -> floatArrayOf(
                0.90f, 0f, -0.10f, 0f, -10f,
                -0.05f, 1.20f, 0.10f, 0f, 15f,
                -0.10f, 0.20f, 1.40f, 0f, 30f,
                0f, 0f, 0f, 1f, 0f
            )
            "thriller_blue" -> floatArrayOf(
                0.80f, 0.10f, -0.05f, 0f, -15f,
                0.05f, 0.95f, 0.15f, 0f, -5f,
                0.10f, 0.20f, 1.45f, 0f, 35f,
                0f, 0f, 0f, 1f, 0f
            )
            "blockbuster_warm" -> floatArrayOf(
                1.35f, 0.05f, -0.15f, 0f, 25f,
                0.05f, 1.15f, -0.05f, 0f, 15f,
                -0.15f, -0.05f, 0.85f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            )
            "epic_dawn" -> floatArrayOf(
                1.35f, -0.05f, 0.10f, 0f, 30f,
                0.05f, 1.05f, -0.05f, 0f, 5f,
                0.10f, -0.10f, 1.25f, 0f, 25f,
                0f, 0f, 0f, 1f, 0f
            )
            "romance_warm" -> floatArrayOf(
                1.25f, 0.05f, 0.05f, 0f, 20f,
                0.05f, 1.05f, 0.05f, 0f, 8f,
                0.05f, 0.05f, 1.10f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            )
            "film_noir_cinema" -> floatArrayOf(
                0.32f, 0.58f, 0.14f, 0f, -15f,
                0.32f, 0.58f, 0.14f, 0f, -15f,
                0.32f, 0.58f, 0.14f, 0f, -15f,
                0f, 0f, 0f, 1f, 0f
            )

            // --- Retro & Film ---
            "vintage_sepia" -> floatArrayOf(
                0.393f * 1.2f, 0.769f * 1.1f, 0.189f * 0.9f, 0f, 20f,
                0.349f * 1.1f, 0.686f * 1.1f, 0.168f * 0.9f, 0f, 12f,
                0.272f * 0.9f, 0.534f * 0.9f, 0.131f * 0.8f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )
            "kodak_35mm" -> floatArrayOf(
                1.20f, 0.05f, -0.05f, 0f, 15f,
                0.02f, 1.12f, -0.02f, 0f, 10f,
                -0.08f, -0.04f, 0.95f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f
            )
            "fuji_chrome" -> floatArrayOf(
                1.05f, -0.02f, 0.05f, 0f, 5f,
                -0.05f, 1.25f, 0.05f, 0f, 12f,
                0.05f, -0.02f, 1.15f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f
            )
            "polaroid_fade" -> floatArrayOf(
                1.05f, 0.05f, 0.05f, 0f, 25f,
                0.05f, 1.02f, 0.05f, 0f, 22f,
                0.05f, 0.05f, 1.08f, 0f, 30f,
                0f, 0f, 0f, 1f, 0f
            )
            "eighties_grain" -> floatArrayOf(
                1.15f, 0.08f, -0.05f, 0f, 18f,
                0.05f, 1.10f, 0.02f, 0f, 12f,
                -0.05f, 0.02f, 0.92f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f
            )
            "super_8" -> floatArrayOf(
                1.20f, 0.05f, -0.10f, 0f, 22f,
                0.05f, 1.08f, -0.05f, 0f, 15f,
                -0.10f, 0.02f, 0.88f, 0f, -12f,
                0f, 0f, 0f, 1f, 0f
            )
            "film_warm" -> floatArrayOf(
                1.18f, 0.04f, -0.06f, 0f, 16f,
                0.02f, 1.10f, -0.02f, 0f, 10f,
                -0.05f, -0.02f, 0.96f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f
            )
            "film_cool" -> floatArrayOf(
                0.95f, 0.02f, 0.02f, 0f, -5f,
                0.02f, 1.05f, 0.05f, 0f, 5f,
                0.02f, 0.05f, 1.25f, 0f, 25f,
                0f, 0f, 0f, 1f, 0f
            )
            "disposable_camera" -> floatArrayOf(
                1.15f, 0.05f, 0.02f, 0f, 20f,
                0.02f, 1.12f, 0.02f, 0f, 18f,
                -0.02f, 0.02f, 0.95f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
            "vhs_warm" -> floatArrayOf(
                1.25f, 0.05f, -0.05f, 0f, 15f,
                0.05f, 1.05f, 0.05f, 0f, 5f,
                -0.08f, 0.05f, 0.90f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )

            // --- Cyberpunk & Neon ---
            "neon_purple" -> floatArrayOf(
                1.30f, -0.10f, 0.20f, 0f, 25f,
                -0.10f, 0.90f, 0.10f, 0f, -10f,
                0.20f, 0.10f, 1.50f, 0f, 40f,
                0f, 0f, 0f, 1f, 0f
            )
            "cyan_glow" -> floatArrayOf(
                0.80f, 0.05f, 0.05f, 0f, -15f,
                0.05f, 1.35f, 0.15f, 0f, 25f,
                0.10f, 0.20f, 1.50f, 0f, 45f,
                0f, 0f, 0f, 1f, 0f
            )
            "synthwave_pink" -> floatArrayOf(
                1.40f, -0.05f, 0.15f, 0f, 35f,
                0.05f, 0.90f, 0.05f, 0f, -5f,
                0.15f, 0.05f, 1.35f, 0f, 30f,
                0f, 0f, 0f, 1f, 0f
            )
            "synthwave_blue" -> floatArrayOf(
                0.90f, -0.05f, 0.10f, 0f, -5f,
                -0.05f, 1.05f, 0.15f, 0f, 10f,
                0.10f, 0.20f, 1.55f, 0f, 40f,
                0f, 0f, 0f, 1f, 0f
            )
            "midnight_dark" -> floatArrayOf(
                0.85f, 0f, 0.05f, 0f, -20f,
                0f, 0.88f, 0.10f, 0f, -15f,
                0.05f, 0.15f, 1.30f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f
            )
            "laser_grid" -> floatArrayOf(
                0.80f, 0.10f, 0f, 0f, -10f,
                0.10f, 1.50f, 0.10f, 0f, 30f,
                0f, 0.10f, 0.85f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )
            "ultraviolet" -> floatArrayOf(
                1.25f, -0.15f, 0.35f, 0f, 25f,
                -0.10f, 0.85f, 0.15f, 0f, -15f,
                0.35f, 0.15f, 1.60f, 0f, 50f,
                0f, 0f, 0f, 1f, 0f
            )

            // --- Portrait & Beauty ---
            "soft_skin_glow" -> floatArrayOf(
                1.15f, 0.04f, -0.02f, 0f, 18f,
                0.02f, 1.08f, 0.02f, 0f, 14f,
                -0.02f, 0.02f, 1.02f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
            "natural_warmth" -> floatArrayOf(
                1.12f, 0.02f, -0.02f, 0f, 12f,
                0.02f, 1.06f, -0.01f, 0f, 8f,
                -0.02f, -0.01f, 0.98f, 0f, -4f,
                0f, 0f, 0f, 1f, 0f
            )
            "peachy_glow" -> floatArrayOf(
                1.20f, 0.02f, -0.02f, 0f, 22f,
                0.02f, 1.08f, 0.02f, 0f, 10f,
                -0.05f, 0.02f, 1.00f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            )
            "porcelain" -> floatArrayOf(
                1.08f, 0.02f, 0.02f, 0f, 15f,
                0.02f, 1.08f, 0.02f, 0f, 15f,
                0.02f, 0.02f, 1.12f, 0f, 18f,
                0f, 0f, 0f, 1f, 0f
            )
            "clean_white" -> floatArrayOf(
                1.05f, 0.02f, 0.02f, 0f, 20f,
                0.02f, 1.05f, 0.02f, 0f, 20f,
                0.02f, 0.02f, 1.05f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            )

            // --- Urban & Moody ---
            "cold_city" -> floatArrayOf(
                0.90f, 0.05f, 0.05f, 0f, -10f,
                0.02f, 1.02f, 0.08f, 0f, -2f,
                0.05f, 0.08f, 1.30f, 0f, 25f,
                0f, 0f, 0f, 1f, 0f
            )
            "street_blue" -> floatArrayOf(
                0.85f, 0.05f, 0.05f, 0f, -15f,
                0.02f, 1.00f, 0.10f, 0f, 0f,
                0.05f, 0.10f, 1.38f, 0f, 30f,
                0f, 0f, 0f, 1f, 0f
            )
            "muted_tones" -> floatArrayOf(
                0.90f, 0.08f, 0.05f, 0f, 5f,
                0.05f, 0.90f, 0.05f, 0f, 5f,
                0.05f, 0.08f, 0.90f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            )
            "industrial" -> floatArrayOf(
                1.05f, 0.05f, -0.05f, 0f, -10f,
                0.05f, 1.05f, 0.02f, 0f, -5f,
                -0.05f, 0.02f, 1.15f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            )
            "night_street" -> floatArrayOf(
                0.95f, 0.02f, 0.02f, 0f, -18f,
                0.02f, 0.95f, 0.05f, 0f, -12f,
                0.05f, 0.10f, 1.25f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f
            )

            // --- Food & Landscape ---
            "vibrant_punch" -> floatArrayOf(
                1.30f, -0.05f, -0.05f, 0f, 10f,
                -0.05f, 1.30f, -0.05f, 0f, 10f,
                -0.05f, -0.05f, 1.25f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
            "sunset_gold" -> floatArrayOf(
                1.35f, 0.05f, -0.15f, 0f, 25f,
                0.05f, 1.15f, -0.05f, 0f, 12f,
                -0.10f, -0.05f, 0.90f, 0f, -15f,
                0f, 0f, 0f, 1f, 0f
            )
            "forest_green" -> floatArrayOf(
                0.95f, 0.05f, -0.05f, 0f, -5f,
                0.05f, 1.35f, 0.05f, 0f, 18f,
                -0.05f, 0.05f, 0.95f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f
            )
            "ocean_blue" -> floatArrayOf(
                0.85f, 0.05f, 0.05f, 0f, -12f,
                0.05f, 1.10f, 0.10f, 0f, 8f,
                0.05f, 0.10f, 1.45f, 0f, 35f,
                0f, 0f, 0f, 1f, 0f
            )
            "golden_hour" -> floatArrayOf(
                1.30f, 0.05f, -0.10f, 0f, 20f,
                0.05f, 1.12f, -0.05f, 0f, 12f,
                -0.10f, -0.05f, 0.92f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )

            // Original or Unknown fallback
            else -> IDENTITY
        }
    }

    /**
     * Compute interpolated matrix based on filter intensity (0.0 to 1.0).
     */
    fun getInterpolatedMatrix(filterId: String?, intensity: Float): FloatArray {
        val clamped = intensity.coerceIn(0f, 1f)
        if (filterId == null || clamped <= 0f) {
            return IDENTITY.copyOf()
        }
        val target = getRawMatrix(filterId)
        val result = FloatArray(20)
        for (i in 0 until 20) {
            result[i] = IDENTITY[i] * (1f - clamped) + target[i] * clamped
        }
        return result
    }

    fun getAndroidColorMatrix(filterId: String?, intensity: Float): AndroidColorMatrix {
        return AndroidColorMatrix(getInterpolatedMatrix(filterId, intensity))
    }

    fun getComposeColorMatrix(filterId: String?, intensity: Float): ComposeColorMatrix {
        return ComposeColorMatrix(getInterpolatedMatrix(filterId, intensity))
    }

    /**
     * Computes a combined 4x5 ColorMatrix incorporating:
     * - Filter preset with intensity interpolation
     * - Video adjustments (brightness, contrast, saturation, temperature, tint, exposure)
     * - Visual FX color grading (sepia, night vision, thermal, bad tv, vhs)
     */
    fun getCombinedMatrix(
        filterId: String? = null,
        intensity: Float = 0f,
        adjustments: com.apexstudio.app.domain.model.VideoAdjustments = com.apexstudio.app.domain.model.VideoAdjustments(),
        fxId: String? = null,
        fxIntensity: Float = 0f
    ): FloatArray {
        val matrix = getInterpolatedMatrix(filterId, intensity).copyOf()

        // 1. Brightness & Exposure
        val bOffset = (adjustments.brightness + adjustments.exposure * 0.5f) * 100f
        if (bOffset != 0f) {
            matrix[4] += bOffset
            matrix[9] += bOffset
            matrix[14] += bOffset
        }

        // 2. Contrast
        if (adjustments.contrast != 1f) {
            val c = adjustments.contrast.coerceAtLeast(0f)
            val t = 128f * (1f - c)
            matrix[0] *= c
            matrix[6] *= c
            matrix[12] *= c
            matrix[4] += t
            matrix[9] += t
            matrix[14] += t
        }

        // 3. Saturation
        if (adjustments.saturation != 1f) {
            val sat = adjustments.saturation.coerceAtLeast(0f)
            val invSat = 1f - sat
            val r = 0.213f * invSat
            val g = 0.715f * invSat
            val b = 0.072f * invSat
            val m0 = r + sat
            val m1 = g
            val m2 = b
            matrix[0] = matrix[0] * m0 + matrix[1] * r + matrix[2] * r
            matrix[6] = matrix[5] * g + matrix[6] * (g + sat) + matrix[7] * g
            matrix[12] = matrix[10] * b + matrix[11] * b + matrix[12] * (b + sat)
        }

        // 4. Temperature (warm vs cool)
        if (adjustments.temperature != 0f) {
            val temp = adjustments.temperature * 30f
            matrix[4] += temp
            matrix[14] -= temp
        }

        // 5. Tint (magenta vs green)
        if (adjustments.tint != 0f) {
            val tint = adjustments.tint * 25f
            matrix[9] += tint
        }

        // 6. FX-specific color shifts
        if (fxId != null && fxIntensity > 0f) {
            when (fxId) {
                "sepia_grain" -> {
                    val s = fxIntensity * 0.8f
                    matrix[4] += 35f * s
                    matrix[9] += 18f * s
                    matrix[14] -= 25f * s
                }
                "night_vision" -> {
                    val s = fxIntensity
                    matrix[0] *= (1f - 0.7f * s)
                    matrix[6] *= (1f + 0.5f * s)
                    matrix[12] *= (1f - 0.8f * s)
                    matrix[9] += 40f * s
                }
                "thermal_vision" -> {
                    val s = fxIntensity
                    matrix[0] *= (1f + 0.6f * s)
                    matrix[12] *= (1f + 0.6f * s)
                }
                "bad_tv", "vhs" -> {
                    matrix[4] += 12f * fxIntensity
                    matrix[14] += 16f * fxIntensity
                }
            }
        }

        return matrix
    }
}
