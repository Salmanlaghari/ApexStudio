package com.apexstudio.app.data.photoedit

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Conservative, deterministic single-image leveling math. This can correct small camera-roll angles
 * when the image has a dominant edge direction; it cannot remove temporal motion or motion blur.
 */
object PhotoStabilizationMath {
    const val MAX_CORRECTION_DEGREES = 12f
    private const val MIN_CORRECTION_DEGREES = 0.75f
    private const val MIN_ANISOTROPY = 0.18
    private const val MIN_EDGE_MAGNITUDE = 36

    /** Returns the clockwise correction angle, or null when evidence is weak or too far from level. */
    fun estimateCorrectionDegrees(width: Int, height: Int, argb: IntArray): Float? {
        require(width > 0 && height > 0 && width.toLong() * height == argb.size.toLong())
        if (width < 8 || height < 8) return null
        val step = maxOf(1, maxOf(width, height) / 512)
        var xx = 0.0
        var yy = 0.0
        var xy = 0.0
        var edgeCount = 0
        for (y in 1 until height - 1 step step) {
            for (x in 1 until width - 1 step step) {
                val p = y * width + x
                val gx = -luma(argb[p - width - 1]) + luma(argb[p - width + 1]) -
                    2 * luma(argb[p - 1]) + 2 * luma(argb[p + 1]) -
                    luma(argb[p + width - 1]) + luma(argb[p + width + 1])
                val gy = -luma(argb[p - width - 1]) - 2 * luma(argb[p - width]) -
                    luma(argb[p - width + 1]) + luma(argb[p + width - 1]) +
                    2 * luma(argb[p + width]) + luma(argb[p + width + 1])
                val magnitudeSquared = gx * gx + gy * gy
                if (magnitudeSquared < MIN_EDGE_MAGNITUDE * MIN_EDGE_MAGNITUDE) continue
                xx += gx.toDouble() * gx
                yy += gy.toDouble() * gy
                xy += gx.toDouble() * gy
                edgeCount++
            }
        }
        if (edgeCount < 8) return null
        val trace = xx + yy
        if (trace <= 0.0) return null
        val anisotropy = hypot(xx - yy, 2.0 * xy) / trace
        if (!anisotropy.isFinite() || anisotropy < MIN_ANISOTROPY) return null

        // The dominant gradient is normal to the dominant line direction.
        val gradientDegrees = 0.5 * atan2(2.0 * xy, xx - yy) * 180.0 / PI
        val lineDegrees = normalizeHalfTurn(gradientDegrees - 90.0)
        val correction = -lineDegrees
        if (!correction.isFinite() || kotlin.math.abs(correction) < MIN_CORRECTION_DEGREES ||
            kotlin.math.abs(correction) > MAX_CORRECTION_DEGREES
        ) return null
        return correction.toFloat()
    }

    /** Same-aspect central crop scale that keeps every rotated output pixel over source content. */
    fun safeCropScale(width: Int, height: Int, rotationDegrees: Float): Float {
        require(width > 0 && height > 0 && rotationDegrees.isFinite())
        val radians = kotlin.math.abs(rotationDegrees) * PI / 180.0
        val c = cos(radians)
        val s = sin(radians)
        val scaleX = width / (width * c + height * s)
        val scaleY = height / (width * s + height * c)
        return min(1.0, min(scaleX, scaleY)).toFloat().coerceIn(0f, 1f)
    }

    /** Largest centered axis-aligned rectangle safely inside the rotated image, excluding AA edges. */
    fun inscribedCropSize(width: Int, height: Int, rotationDegrees: Float): Pair<Int, Int> {
        require(width > 0 && height > 0 && rotationDegrees.isFinite())
        val radians = kotlin.math.abs(rotationDegrees) * PI / 180.0
        if (radians < 1e-6) return width to height
        val c = cos(radians)
        val s = sin(radians)
        val denominator = cos(2.0 * radians)
        if (denominator <= 0.0) return 1 to 1
        // Two pixels of guard keep bilinear/antialias sampling away from transparent corner wedges.
        val innerWidth = floor((width * c - height * s) / denominator).toInt() - 2
        val innerHeight = floor((height * c - width * s) / denominator).toInt() - 2
        return innerWidth.coerceIn(1, width) to innerHeight.coerceIn(1, height)
    }

    internal fun safeCropInsets(width: Int, height: Int, rotationDegrees: Float): Pair<Int, Int> {
        val scale = safeCropScale(width, height, rotationDegrees)
        val cropWidth = (width * scale).roundToInt().coerceIn(1, width)
        val cropHeight = (height * scale).roundToInt().coerceIn(1, height)
        return ((width - cropWidth) / 2) to ((height - cropHeight) / 2)
    }

    private fun normalizeHalfTurn(degrees: Double): Double {
        var value = degrees % 180.0
        if (value > 90.0) value -= 180.0
        if (value < -90.0) value += 180.0
        return value
    }

    private fun luma(pixel: Int): Int =
        (77 * (pixel ushr 16 and 255) + 150 * (pixel ushr 8 and 255) + 29 * (pixel and 255)) shr 8
}
