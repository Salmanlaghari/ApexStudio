package com.apexstudio.app.data.effect

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.apexstudio.app.R
import com.apexstudio.app.domain.model.WorkspaceFilterPresetAdditions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * Small photographic thumbnails for the workspace filter picker. The pixel math mirrors the
 * filter branches and intensity blend in [WorkspaceEffectGlEffect.fragmentShaderSource], so
 * the card shows the selected look rather than only its catalog swatch.
 */
internal object WorkspaceFilterThumbnailGenerator {
    private const val SIZE = 88

    suspend fun generate(
        context: Context,
        source: Bitmap?,
        presetIds: List<String>,
        strength: Float = 0.82f,
    ): Map<String, ImageBitmap> = withContext(Dispatchers.Default) {
        val base = source?.let(::centerCrop) ?: bundledSample(context)
        val result = presetIds.distinct().associateWith { id ->
            applyPreset(base, id, strength).asImageBitmap()
        }
        if (base !== source && !base.isRecycled) base.recycle()
        result
    }

    /** Deterministic, testable CPU mirror of the shared GLSL color-look branches. */
    internal fun applyPreset(source: Bitmap, id: String, strength: Float = 0.82f): Bitmap {
        val width = source.width.coerceAtLeast(1)
        val height = source.height.coerceAtLeast(1)
        val input = IntArray(width * height)
        val output = IntArray(input.size)
        source.getPixels(input, 0, width, 0, 0, width, height)
        val blend = strength.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val addition = WorkspaceFilterPresetAdditions.find(id)?.parameters
        for (index in input.indices) {
            val pixel = input[index]
            val r = Color.red(pixel) / 255f
            val g = Color.green(pixel) / 255f
            val b = Color.blue(pixel) / 255f
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val graded = addition?.let { params ->
                var pr = luma + (r - luma) * params.saturation
                var pg = luma + (g - luma) * params.saturation
                var pb = luma + (b - luma) * params.saturation
                pr = (pr - 0.5f) * params.contrast + 0.5f
                pg = (pg - 0.5f) * params.contrast + 0.5f
                pb = (pb - 0.5f) * params.contrast + 0.5f
                floatArrayOf(
                    max0(pr * params.gainR + params.offsetR).pow(1f / params.gamma.coerceAtLeast(0.05f)),
                    max0(pg * params.gainG + params.offsetG).pow(1f / params.gamma.coerceAtLeast(0.05f)),
                    max0(pb * params.gainB + params.offsetB).pow(1f / params.gamma.coerceAtLeast(0.05f)),
                )
            } ?: basePreset(id, r, g, b, luma)
            val outR = (r + (graded[0].coerceIn(0f, 1f) - r) * blend).coerceIn(0f, 1f)
            val outG = (g + (graded[1].coerceIn(0f, 1f) - g) * blend).coerceIn(0f, 1f)
            val outB = (b + (graded[2].coerceIn(0f, 1f) - b) * blend).coerceIn(0f, 1f)
            output[index] = Color.argb(
                Color.alpha(pixel),
                (outR * 255f + 0.5f).toInt(),
                (outG * 255f + 0.5f).toInt(),
                (outB * 255f + 0.5f).toInt(),
            )
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun basePreset(id: String, r: Float, g: Float, b: Float, luma: Float): FloatArray = when (id) {
        "filter_soft_warm" -> floatArrayOf(
            max0(r * 1.06f + 0.018f).pow(0.96f),
            max0(g * 1.01f + 0.006f).pow(0.96f),
            max0(b * 0.94f - 0.012f).pow(0.96f),
        )
        "filter_cool" -> floatArrayOf(
            r * 0.91f + 0.008f * (1f - luma),
            g + 0.018f * (1f - luma),
            b * 1.10f + 0.035f * (1f - luma),
        )
        "filter_faded" -> {
            val mutedR = luma + (r - luma) * 0.76f
            val mutedG = luma + (g - luma) * 0.76f
            val mutedB = luma + (b - luma) * 0.76f
            floatArrayOf(0.075f + (mutedR * 0.86f + 0.10f - 0.075f) * 0.92f,
                0.075f + (mutedG * 0.86f + 0.10f - 0.075f) * 0.92f,
                0.075f + (mutedB * 0.86f + 0.10f - 0.075f) * 0.92f)
        }
        "filter_amber" -> floatArrayOf(r * 1.12f + 0.035f, g * 1.01f + 0.012f, b * 0.83f - 0.022f)
        "filter_rose" -> floatArrayOf(r * 1.045f + 0.018f, g * 0.94f + 0.003f, b + 0.021f)
        "filter_mint" -> floatArrayOf(r * 0.91f + 0.012f, g * 1.035f + 0.025f, b * 0.98f + 0.018f)
        "filter_teal_orange" -> {
            val shadows = 1f - smoothstep(0.18f, 0.72f, luma)
            val highlights = smoothstep(0.24f, 0.82f, luma)
            floatArrayOf(r - 0.015f * shadows + 0.075f * highlights,
                g + 0.035f * shadows + 0.018f * highlights,
                b + 0.055f * shadows - 0.025f * highlights)
        }
        "filter_crisp" -> {
            val cr = (r - 0.5f) * 1.12f + 0.5f
            val cg = (g - 0.5f) * 1.12f + 0.5f
            val cb = (b - 0.5f) * 1.12f + 0.5f
            val gray = 0.299f * cr + 0.587f * cg + 0.114f * cb
            floatArrayOf(gray + (cr - gray) * 1.08f, gray + (cg - gray) * 1.08f, gray + (cb - gray) * 1.08f)
        }
        "filter_matte" -> floatArrayOf((r - 0.5f) * 0.88f + 0.5f + 0.055f,
            (g - 0.5f) * 0.88f + 0.5f + 0.048f,
            (b - 0.5f) * 0.88f + 0.5f + 0.045f)
        "filter_bw_contrast" -> {
            val gray = ((luma - 0.5f) * 1.42f + 0.5f).coerceIn(0f, 1f)
            floatArrayOf(gray, gray, gray)
        }
        "filter_sepia" -> floatArrayOf(luma * 1.12f + 0.025f, luma * 0.91f + 0.012f, luma * 0.70f)
        "filter_lavender" -> floatArrayOf(r * 0.98f + 0.018f, g * 0.91f + 0.012f, b * 1.08f + 0.035f)
        else -> floatArrayOf(r, g, b)
    }

    private fun centerCrop(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height).coerceAtLeast(1)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val cropped = Bitmap.createBitmap(source, left, top, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true)
        if (cropped !== source && cropped !== scaled) cropped.recycle()
        return scaled
    }

    private fun bundledSample(context: Context): Bitmap {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val decoded = BitmapFactory.decodeResource(context.resources, R.drawable.filter_sample_portrait, options)
            ?: return Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        return centerCrop(decoded).also { if (it !== decoded) decoded.recycle() }
    }

    private fun max0(value: Float) = value.coerceAtLeast(0f)
    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
