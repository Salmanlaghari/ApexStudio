package com.apexstudio.app.data.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.apexstudio.app.domain.model.VideoAdjustments
import com.apexstudio.app.domain.model.toVideoAdjustments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Produces the photo samples shown on curated GPU filter-profile cards.
 *
 * The input is the unfiltered thumbnail frame already extracted for the LUT
 * gallery. Each profile uses its persisted config and the same shared mapping
 * as Media3 preview/export. The per-pixel adjustment chain mirrors the shader;
 * optional LUT presets use the repository's CPU color-matrix fallback.
 */
object GpuColorProfileThumbnailGenerator {
    private const val THUMB_SIZE = 120

    suspend fun generate(
        source: Bitmap,
        profiles: List<GpuColorProfile>
    ): Map<String, ImageBitmap> = withContext(Dispatchers.Default) {
        if (profiles.isEmpty() || source.width <= 0 || source.height <= 0) return@withContext emptyMap()
        val base = centerCropAndScale(source)
        profiles.associate { profile ->
            profile.id to renderProfile(base, profile).asImageBitmap()
        }
    }

    private fun renderProfile(source: Bitmap, profile: GpuColorProfile): Bitmap {
        val config = profile.applyWithIntensity(1f)
        val adjustments = config.toVideoAdjustments()
        val filteredSource = if (config.filterPresetId != null && config.filterIntensity > 0f) {
            Bitmap.createBitmap(THUMB_SIZE, THUMB_SIZE, Bitmap.Config.ARGB_8888).also { filtered ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(
                        ColorMatrix(FilterColorMatrix.getInterpolatedMatrix(config.filterPresetId, config.filterIntensity))
                    )
                }
                Canvas(filtered).drawBitmap(source, 0f, 0f, paint)
            }
        } else source

        val input = IntArray(THUMB_SIZE * THUMB_SIZE)
        val output = IntArray(input.size)
        filteredSource.getPixels(input, 0, THUMB_SIZE, 0, 0, THUMB_SIZE, THUMB_SIZE)
        for (y in 0 until THUMB_SIZE) {
            for (x in 0 until THUMB_SIZE) {
                val index = y * THUMB_SIZE + x
                val pixel = input[index]
                val color = applyColorChain(
                    Color.red(pixel) / 255f,
                    Color.green(pixel) / 255f,
                    Color.blue(pixel) / 255f,
                    x,
                    y,
                    adjustments
                )
                output[index] = Color.argb(
                    Color.alpha(pixel),
                    (color[0].coerceIn(0f, 1f) * 255f).roundToInt(),
                    (color[1].coerceIn(0f, 1f) * 255f).roundToInt(),
                    (color[2].coerceIn(0f, 1f) * 255f).roundToInt()
                )
            }
        }
        return Bitmap.createBitmap(THUMB_SIZE, THUMB_SIZE, Bitmap.Config.ARGB_8888).also { result ->
            result.setPixels(output, 0, THUMB_SIZE, 0, 0, THUMB_SIZE, THUMB_SIZE)
            if (filteredSource !== source) filteredSource.recycle()
        }
    }

    /** Mirrors AdjustmentsGlEffect.applyColorChain for static thumbnail pixels. */
    private fun applyColorChain(r0: Float, g0: Float, b0: Float, x: Int, y: Int, a: VideoAdjustments): FloatArray {
        var r = r0
        var g = g0
        var b = b0

        val exposureScale = 2.0.pow(a.exposure.toDouble()).toFloat()
        r *= exposureScale; g *= exposureScale; b *= exposureScale
        r += a.brightness; g += a.brightness; b += a.brightness

        val lumaLight = luma(r, g, b)
        val midLight = (1f - kotlin.math.abs(lumaLight - 0.5f) * 2f).coerceIn(0f, 1f)
        val lightnessLift = a.lightness * 0.4f * midLight
        r += lightnessLift; g += lightnessLift; b += lightnessLift

        r = (r - 0.5f) * a.contrast + 0.5f
        g = (g - 0.5f) * a.contrast + 0.5f
        b = (b - 0.5f) * a.contrast + 0.5f

        val blackIn = max(a.blacks, 0f) * 0.3f
        val blackOut = max(-a.blacks, 0f) * 0.3f
        val whiteIn = 1f - max(a.whites, 0f) * 0.3f
        val whiteOut = 1f + min(a.whites, 0f) * 0.3f
        if (whiteIn - blackIn > 0.0001f) {
            val scale = (whiteOut - blackOut) / (whiteIn - blackIn)
            r = blackOut + (r - blackIn) * scale
            g = blackOut + (g - blackIn) * scale
            b = blackOut + (b - blackIn) * scale
        }

        val lumaHs = luma(r, g, b)
        val hiFactor = smoothstep(0.5f, 1f, lumaHs)
        val shadowFactor = 1f - smoothstep(0f, 0.5f, lumaHs)
        r = (r + a.highlights * hiFactor + a.shadows * shadowFactor).coerceIn(0f, 1f)
        g = (g + a.highlights * hiFactor + a.shadows * shadowFactor).coerceIn(0f, 1f)
        b = (b + a.highlights * hiFactor + a.shadows * shadowFactor).coerceIn(0f, 1f)

        val lumaClarity = luma(r, g, b)
        val midClarity = (1f - kotlin.math.abs(lumaClarity - 0.5f) * 2f).coerceIn(0f, 1f)
        val clarityScale = 1f + a.clarity * 0.7f
        val rClarity = (r - 0.5f) * clarityScale + 0.5f
        val gClarity = (g - 0.5f) * clarityScale + 0.5f
        val bClarity = (b - 0.5f) * clarityScale + 0.5f
        r += (rClarity - r) * midClarity * 0.9f
        g += (gClarity - g) * midClarity * 0.9f
        b += (bClarity - b) * midClarity * 0.9f

        val lumaSat = luma(r, g, b)
        r = lumaSat + (r - lumaSat) * a.saturation
        g = lumaSat + (g - lumaSat) * a.saturation
        b = lumaSat + (b - lumaSat) * a.saturation

        if (a.vibrance != 0f) {
            val maxChannel = max(max(r, g), b)
            val minChannel = min(min(r, g), b)
            val saturation = if (maxChannel > 0f) ((maxChannel - minChannel) / maxChannel).coerceIn(0f, 1f) else 0f
            val amount = a.vibrance * (1f - saturation) * 0.5f
            val lumaVibrance = luma(r, g, b)
            r = lumaVibrance + (r - lumaVibrance) * (1f + amount)
            g = lumaVibrance + (g - lumaVibrance) * (1f + amount)
            b = lumaVibrance + (b - lumaVibrance) * (1f + amount)
        }

        if (a.hue != 0f) {
            val angle = a.hue * (PI.toFloat() / 180f)
            val c = cos(angle); val s = sin(angle)
            val rr = floatArrayOf(0.299f + 0.701f * c + 0.168f * s, 0.587f - 0.587f * c + 0.330f * s, 0.114f - 0.114f * c - 0.497f * s)
            val gg = floatArrayOf(0.299f - 0.299f * c - 0.328f * s, 0.587f + 0.413f * c + 0.035f * s, 0.114f - 0.114f * c + 0.292f * s)
            val bb = floatArrayOf(0.299f - 0.299f * c + 1.250f * s, 0.587f - 0.587f * c - 1.050f * s, 0.114f - 0.114f * c - 0.203f * s)
            val oldR = r; val oldG = g; val oldB = b
            r = oldR * rr[0] + oldG * rr[1] + oldB * rr[2]
            g = oldR * gg[0] + oldG * gg[1] + oldB * gg[2]
            b = oldR * bb[0] + oldG * bb[1] + oldB * bb[2]
        }

        r *= 1f + a.temperature * 0.2f
        g *= 1f - a.tint * 0.2f
        b *= 1f - a.temperature * 0.2f

        if (a.vignette > 0f) {
            val u = (x + 0.5f) / THUMB_SIZE
            val v = (y + 0.5f) / THUMB_SIZE
            val distance = kotlin.math.sqrt((u - 0.5f) * (u - 0.5f) + (v - 0.5f) * (v - 0.5f))
            val factor = 1f - a.vignette * smoothstep(0.25f, 0.85f, distance)
            r *= factor; g *= factor; b *= factor
        }
        return floatArrayOf(r, g, b)
    }

    private fun luma(r: Float, g: Float, b: Float): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun centerCropAndScale(source: Bitmap): Bitmap {
        val side = min(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val cropped = Bitmap.createBitmap(source, left, top, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, THUMB_SIZE, THUMB_SIZE, true)
        if (cropped !== source && cropped !== scaled) cropped.recycle()
        return scaled
    }
}
