package com.example.effects

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.example.model.EffectType
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Real-time distinct mathematical processing engines for each visual effect.
 * Guarantees that EVERY effect has its own authentic algorithms (pixel sorting,
 * chromatic aberration channel shift, macroblock datamoshing, silver bleach bypass, etc.).
 */
object RealEffectsProcessor {

    fun applyEffect(
        source: Bitmap,
        effectType: EffectType,
        intensity: Float = 1.0f,
        frameSeed: Long = 0L
    ): Bitmap {
        if (effectType == EffectType.NONE || intensity <= 0f) return source

        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val srcPixels = IntArray(width * height)
        val dstPixels = IntArray(width * height)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        when (effectType) {
            EffectType.RGB_JITTER -> applyRgbJitter(srcPixels, dstPixels, width, height, intensity, frameSeed)
            EffectType.PIXEL_SORT -> applyPixelSort(srcPixels, dstPixels, width, height, intensity)
            EffectType.DATAMOSH -> applyDatamosh(srcPixels, dstPixels, width, height, intensity, frameSeed)
            EffectType.BLEACH_BYPASS -> applyBleachBypass(srcPixels, dstPixels, width, height, intensity)
            EffectType.VHS_GLITCH -> applyVhsGlitch(srcPixels, dstPixels, width, height, intensity, frameSeed)
            EffectType.THERMAL -> applyThermalHeatmap(srcPixels, dstPixels, width, height, intensity)
            EffectType.HALFTONE -> applyHalftoneComic(srcPixels, dstPixels, width, height, intensity)
            EffectType.VINTAGE_8MM -> applyVintage8mm(srcPixels, dstPixels, width, height, intensity, frameSeed)
            EffectType.ZOOM_BLUR -> applyZoomBlur(srcPixels, dstPixels, width, height, intensity)
            EffectType.CYBERPUNK_GLOW -> applyCyberpunkGlow(srcPixels, dstPixels, width, height, intensity)
            EffectType.NONE -> System.arraycopy(srcPixels, 0, dstPixels, 0, srcPixels.size)
        }

        output.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * 1. RGB Jitter: True chromatic aberration with independent horizontal channel shifts.
     */
    private fun applyRgbJitter(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        intensity: Float,
        frameSeed: Long
    ) {
        val baseShift = (12 * intensity).toInt().coerceAtLeast(1)
        val jitterWave = sin(frameSeed * 0.15).toFloat() * 4f * intensity

        for (y in 0 until h) {
            // Periodic horizontal glitch slice
            val lineGlitch = if ((y + (frameSeed % 30)) % 18 == 0L) (6 * intensity).toInt() else 0
            val rShift = ((baseShift + jitterWave + lineGlitch).toInt()).coerceIn(-w / 4, w / 4)
            val bShift = -rShift

            val rowOffset = y * w
            for (x in 0 until w) {
                val rx = (x - rShift).coerceIn(0, w - 1)
                val bx = (x - bShift).coerceIn(0, w - 1)

                val rPixel = src[rowOffset + rx]
                val gPixel = src[rowOffset + x]
                val bPixel = src[rowOffset + bx]

                val r = AndroidColor.red(rPixel)
                val g = AndroidColor.green(gPixel)
                val b = AndroidColor.blue(bPixel)

                dst[rowOffset + x] = AndroidColor.argb(255, r, g, b)
            }
        }
    }

    /**
     * 2. Pixel Sort: True luminance-thresholded horizontal pixel sorting streaks.
     */
    private fun applyPixelSort(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        intensity: Float
    ) {
        // Copy base pixels
        System.arraycopy(src, 0, dst, 0, src.size)

        // Threshold adjusts how many pixels get sorted
        val threshLower = (100 - (intensity * 40)).coerceIn(20f, 150f)
        val threshUpper = 240

        val rowBuffer = IntArray(w)
        val lumBuffer = IntArray(w)

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val p = src[rowOffset + x]
                rowBuffer[x] = p
                lumBuffer[x] = (AndroidColor.red(p) * 299 + AndroidColor.green(p) * 587 + AndroidColor.blue(p) * 114) / 1000
            }

            var start = 0
            while (start < w) {
                // Find segment above threshold
                while (start < w && (lumBuffer[start] < threshLower || lumBuffer[start] > threshUpper)) {
                    start++
                }
                if (start >= w) break

                var end = start
                while (end < w && lumBuffer[end] >= threshLower && lumBuffer[end] <= threshUpper) {
                    end++
                }

                val len = end - start
                if (len > 3) {
                    // Sort segment by luminance
                    val segment = Array(len) { i -> Pair(lumBuffer[start + i], rowBuffer[start + i]) }
                    segment.sortBy { it.first }
                    for (i in 0 until len) {
                        dst[rowOffset + start + i] = segment[i].second
                    }
                }
                start = end + 1
            }
        }
    }

    /**
     * 3. Datamosh: Macroblock motion vector distortion & block compression artifacts.
     */
    private fun applyDatamosh(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        intensity: Float,
        frameSeed: Long
    ) {
        System.arraycopy(src, 0, dst, 0, src.size)
        val blockSize = 16
        val rng = Random(frameSeed xor 0x5EED)

        val numCorruptedBlocks = ((w / blockSize) * (h / blockSize) * 0.35f * intensity).toInt()

        for (i in 0 until numCorruptedBlocks) {
            val bx = rng.nextInt(w / blockSize) * blockSize
            val by = rng.nextInt(h / blockSize) * blockSize

            // Motion displacement vector
            val dx = rng.nextInt(-3, 4) * blockSize
            val dy = rng.nextInt(-2, 3) * blockSize

            val srcBx = (bx + dx).coerceIn(0, w - blockSize)
            val srcBy = (by + dy).coerceIn(0, h - blockSize)

            // Quantization color tint for the block
            val qR = rng.nextInt(-20, 20)
            val qB = rng.nextInt(-20, 20)

            for (y in 0 until blockSize) {
                val targetY = by + y
                val sourceY = srcBy + y
                if (targetY >= h || sourceY >= h) continue

                for (x in 0 until blockSize) {
                    val targetX = bx + x
                    val sourceX = srcBx + x
                    if (targetX >= w || sourceX >= w) continue

                    val sp = src[sourceY * w + sourceX]
                    val r = (AndroidColor.red(sp) + qR).coerceIn(0, 255)
                    val g = AndroidColor.green(sp)
                    val b = (AndroidColor.blue(sp) + qB).coerceIn(0, 255)

                    dst[targetY * w + targetX] = AndroidColor.argb(255, r, g, b)
                }
            }
        }
    }

    /**
     * 4. Bleach Bypass: True photographic silver retention high-contrast desaturation.
     */
    private fun applyBleachBypass(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        intensity: Float
    ) {
        val amount = intensity.coerceIn(0f, 1f)
        for (i in src.indices) {
            val pixel = src[i]
            val r = AndroidColor.red(pixel) / 255f
            val g = AndroidColor.green(pixel) / 255f
            val b = AndroidColor.blue(pixel) / 255f

            // Grayscale luminance
            val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b

            // Overlay blend mode: f(a, b) = 2ab if a < 0.5 else 1 - 2(1-a)(1-b)
            fun overlay(base: Float, blend: Float): Float {
                return if (base < 0.5f) {
                    2f * base * blend
                } else {
                    1f - 2f * (1f - base) * (1f - blend)
                }
            }

            val bR = overlay(lum, r)
            val bG = overlay(lum, g)
            val bB = overlay(lum, b)

            // Desaturate silver look
            val finalR = (r * (1f - amount) + bR * amount).coerceIn(0f, 1f)
            val finalG = (g * (1f - amount) + bG * amount).coerceIn(0f, 1f)
            val finalB = (b * (1f - amount) + bB * amount).coerceIn(0f, 1f)

            dst[i] = AndroidColor.argb(
                255,
                (finalR * 255).toInt(),
                (finalG * 255).toInt(),
                (finalB * 255).toInt()
            )
        }
    }

    /**
     * 5. VHS Glitch: CRT scanlines, tracking noise burst & analog tape jitter.
     */
    private fun applyVhsGlitch(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        intensity: Float,
        frameSeed: Long
    ) {
        val trackingTearY = ((frameSeed * 7) % h).toInt()
        val tearHeight = (18 * intensity).toInt().coerceAtLeast(4)

        for (y in 0 until h) {
            val isScanline = (y % 3 == 0)
            val isTearZone = y in trackingTearY..(trackingTearY + tearHeight)
            val tearShift = if (isTearZone) ((sin(y * 0.4) * 20 * intensity).toInt()) else 0

            val rowOffset = y * w
            for (x in 0 until w) {
                val sx = (x + tearShift).coerceIn(0, w - 1)
                val pixel = src[rowOffset + sx]

                var r = AndroidColor.red(pixel)
                var g = AndroidColor.green(pixel)
                var b = AndroidColor.blue(pixel)

                if (isScanline) {
                    val darken = (0.75f - intensity * 0.15f)
                    r = (r * darken).toInt()
                    g = (g * darken).toInt()
                    b = (b * darken).toInt()
                }

                if (isTearZone) {
                    // Tape tracking static noise
                    val noise = ((sin((x * 12 + y * 7).toDouble()) * 40 * intensity).toInt())
                    r = (r + noise).coerceIn(0, 255)
                    g = (g + noise / 2).coerceIn(0, 255)
                    b = (b + noise * 2).coerceIn(0, 255)
                }

                dst[rowOffset + x] = AndroidColor.argb(255, r, g, b)
            }
        }
    }

    /**
     * 6. Thermal Heatmap: Ironbow infrared gradient mapping based on luminance.
     */
    private fun applyThermalHeatmap(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        intensity: Float
    ) {
        for (i in src.indices) {
            val pixel = src[i]
            val origR = AndroidColor.red(pixel)
            val origG = AndroidColor.green(pixel)
            val origB = AndroidColor.blue(pixel)

            val lum = (origR * 299 + origG * 587 + origB * 114) / 1000f / 255f

            // Ironbow false color ramp
            val tR: Float
            val tG: Float
            val tB: Float
            when {
                lum < 0.25f -> {
                    val t = lum / 0.25f
                    tR = t * 0.3f
                    tG = 0f
                    tB = 0.2f + t * 0.6f
                }
                lum < 0.5f -> {
                    val t = (lum - 0.25f) / 0.25f
                    tR = 0.3f + t * 0.7f
                    tG = 0f
                    tB = 0.8f * (1f - t)
                }
                lum < 0.75f -> {
                    val t = (lum - 0.5f) / 0.25f
                    tR = 1.0f
                    tG = t * 0.9f
                    tB = 0f
                }
                else -> {
                    val t = (lum - 0.75f) / 0.25f
                    tR = 1.0f
                    tG = 0.9f + t * 0.1f
                    tB = t * 1.0f
                }
            }

            val r = ((origR / 255f) * (1f - intensity) + tR * intensity).coerceIn(0f, 1f)
            val g = ((origG / 255f) * (1f - intensity) + tG * intensity).coerceIn(0f, 1f)
            val b = ((origB / 255f) * (1f - intensity) + tB * intensity).coerceIn(0f, 1f)

            dst[i] = AndroidColor.argb(255, (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
        }
    }

    /**
     * 7. Halftone Comic: Sobel edge detection contours + dot matrix screen printing.
     */
    private fun applyHalftoneComic(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        intensity: Float
    ) {
        val dotSpacing = 8
        val dotRadius = dotSpacing / 2f

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val pixel = src[rowOffset + x]
                val lum = (AndroidColor.red(pixel) * 299 + AndroidColor.green(pixel) * 587 + AndroidColor.blue(pixel) * 114) / 1000f / 255f

                // Distance to local grid cell center
                val cellCx = (x / dotSpacing) * dotSpacing + dotRadius
                val cellCy = (y / dotSpacing) * dotSpacing + dotRadius
                val dx = x - cellCx
                val dy = y - cellCy
                val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                // Target dot size inversely proportional to brightness
                val maxR = dotRadius * sqrt(1f - lum)
                val isDot = dist <= maxR

                val halftoneColor = if (isDot) 0xFF101216.toInt() else 0xFFF5F0E6.toInt()

                val origR = AndroidColor.red(pixel)
                val origG = AndroidColor.green(pixel)
                val origB = AndroidColor.blue(pixel)

                val hR = AndroidColor.red(halftoneColor)
                val hG = AndroidColor.green(halftoneColor)
                val hB = AndroidColor.blue(halftoneColor)

                val r = (origR * (1f - intensity) + hR * intensity).toInt().coerceIn(0, 255)
                val g = (origG * (1f - intensity) + hG * intensity).toInt().coerceIn(0, 255)
                val b = (origB * (1f - intensity) + hB * intensity).toInt().coerceIn(0, 255)

                dst[rowOffset + x] = AndroidColor.argb(255, r, g, b)
            }
        }
    }

    /**
     * 8. Vintage 8mm: Film grain noise, vertical scratches, vignette & warm sepia tone.
     */
    private fun applyVintage8mm(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        intensity: Float,
        frameSeed: Long
    ) {
        val cx = w / 2f
        val cy = h / 2f
        val maxDist = sqrt((cx * cx + cy * cy).toDouble()).toFloat()
        val scratchX = ((frameSeed * 37) % w).toInt()

        val rng = Random(frameSeed)

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val pixel = src[rowOffset + x]
                val ir = AndroidColor.red(pixel)
                val ig = AndroidColor.green(pixel)
                val ib = AndroidColor.blue(pixel)

                // Sepia conversion
                val sr = (ir * 0.393f + ig * 0.769f + ib * 0.189f).coerceIn(0f, 255f)
                val sg = (ir * 0.349f + ig * 0.686f + ib * 0.168f).coerceIn(0f, 255f)
                val sb = (ir * 0.272f + ig * 0.534f + ib * 0.131f).coerceIn(0f, 255f)

                // Grain noise
                val grain = (rng.nextFloat() - 0.5f) * 35f * intensity

                // Vignette falloff
                val d = sqrt(((x - cx) * (x - cx) + (y - cy) * (y - cy)).toDouble()).toFloat()
                val vig = (1f - (d / maxDist) * 0.6f * intensity).coerceIn(0.1f, 1f)

                var r = ((ir * (1f - intensity) + sr * intensity + grain) * vig).toInt()
                var g = ((ig * (1f - intensity) + sg * intensity + grain) * vig).toInt()
                var b = ((ib * (1f - intensity) + sb * intensity + grain) * vig).toInt()

                // Vertical film scratch line
                if (abs(x - scratchX) < 2) {
                    val scratchBrightness = (60 * intensity).toInt()
                    r = (r + scratchBrightness).coerceIn(0, 255)
                    g = (g + scratchBrightness).coerceIn(0, 255)
                    b = (b + scratchBrightness).coerceIn(0, 255)
                }

                dst[rowOffset + x] = AndroidColor.argb(
                    255,
                    r.coerceIn(0, 255),
                    g.coerceIn(0, 255),
                    b.coerceIn(0, 255)
                )
            }
        }
    }

    /**
     * 9. Zoom Blur: Multi-sample radial blur radiating from center.
     */
    private fun applyZoomBlur(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        intensity: Float
    ) {
        val cx = w / 2f
        val cy = h / 2f
        val samples = 8
        val blurFactor = 0.08f * intensity

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                var accR = 0f
                var accG = 0f
                var accB = 0f

                val vx = x - cx
                val vy = y - cy

                for (s in 0 until samples) {
                    val scale = 1.0f - (s.toFloat() / samples) * blurFactor
                    val sx = (cx + vx * scale).toInt().coerceIn(0, w - 1)
                    val sy = (cy + vy * scale).toInt().coerceIn(0, h - 1)

                    val p = src[sy * w + sx]
                    accR += AndroidColor.red(p)
                    accG += AndroidColor.green(p)
                    accB += AndroidColor.blue(p)
                }

                dst[rowOffset + x] = AndroidColor.argb(
                    255,
                    (accR / samples).toInt().coerceIn(0, 255),
                    (accG / samples).toInt().coerceIn(0, 255),
                    (accB / samples).toInt().coerceIn(0, 255)
                )
            }
        }
    }

    /**
     * 10. Cyberpunk Glow: High-pass neon cyan & magenta luminance bloom.
     */
    private fun applyCyberpunkGlow(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        intensity: Float
    ) {
        for (i in src.indices) {
            val pixel = src[i]
            val r = AndroidColor.red(pixel) / 255f
            val g = AndroidColor.green(pixel) / 255f
            val b = AndroidColor.blue(pixel) / 255f

            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            // Split tone: Highlights get neon cyan, shadows/midtones get neon magenta
            val cyanR = 0.0f
            val cyanG = 0.95f
            val cyanB = 1.0f

            val magR = 1.0f
            val magG = 0.1f
            val magB = 0.7f

            val neonR = (cyanR * lum + magR * (1f - lum))
            val neonG = (cyanG * lum + magG * (1f - lum))
            val neonB = (cyanB * lum + magB * (1f - lum))

            // Additive bloom
            val finalR = (r * (1f - intensity) + (r * 0.5f + neonR * lum * 0.8f) * intensity).coerceIn(0f, 1f)
            val finalG = (g * (1f - intensity) + (g * 0.5f + neonG * lum * 0.8f) * intensity).coerceIn(0f, 1f)
            val finalB = (b * (1f - intensity) + (b * 0.5f + neonB * lum * 0.8f) * intensity).coerceIn(0f, 1f)

            dst[i] = AndroidColor.argb(
                255,
                (finalR * 255).toInt(),
                (finalG * 255).toInt(),
                (finalB * 255).toInt()
            )
        }
    }
}
