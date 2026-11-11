package com.apexstudio.app.data.effect

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.apexstudio.app.R
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import com.apexstudio.app.domain.model.WorkspaceFilterPresetAdditions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.pow

/**
 * Small photographic thumbnails for the workspace filter and visual-effect pickers. The pixel
 * math mirrors the shared GLSL look/effect branches at fixed representative animation times, so
 * cards show the selected look instead of only its catalog swatch. The active video preview still
 * uses the authoritative GLSL renderer.
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
        require(id in WorkspaceEffectPresets.all) { "Unsupported workspace effect preview: $id" }
        if (id !in WorkspaceEffectPresets.filterIds) return applyShaderEffect(source, id, strength)
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
            output[index] = color(pixel, r, g, b, graded, blend)
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    /** CPU mirror of non-filter branches in the shared GLSL; animated samples use fixed times. */
    private fun applyShaderEffect(source: Bitmap, id: String, strength: Float): Bitmap {
        val width = source.width.coerceAtLeast(1)
        val height = source.height.coerceAtLeast(1)
        val input = IntArray(width * height)
        val output = IntArray(input.size)
        source.getPixels(input, 0, width, 0, 0, width, height)
        val intensity = strength.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val kind = when (id) {
            WorkspaceEffectPresets.MONO -> 0
            WorkspaceEffectPresets.WARM -> 1
            WorkspaceEffectPresets.PIXELATE -> 2
            WorkspaceEffectPresets.SPLIT_2 -> 3
            WorkspaceEffectPresets.SPLIT_3 -> 4
            WorkspaceEffectPresets.CARTOON -> 5
            WorkspaceEffectPresets.BUBBLE -> 6
            WorkspaceEffectPresets.HEARTS -> 7
            WorkspaceEffectPresets.HEARTBEAT -> 8
            WorkspaceEffectPresets.ICE -> 9
            WorkspaceEffectPresets.VFX_CHROMATIC_SPLIT -> 23
            WorkspaceEffectPresets.VFX_SCAN_GLITCH -> 24
            WorkspaceEffectPresets.VFX_LIGHT_LEAK -> 25
            WorkspaceEffectPresets.VFX_VIGNETTE -> 26
            WorkspaceEffectPresets.VFX_EDGE_GLOW -> 27
            else -> error("Unsupported shader effect preview: $id")
        }
        val aspect = width.toFloat() / height.toFloat()
        val elapsedSeconds = when (kind) {
            6 -> 0.6f  // show moving bubbles away from their initial coincidence
            7 -> 0.4f  // keep the hearts visibly floating
            8 -> 0.09f // sample at the first heartbeat peak, not its near-zero phase
            24 -> SCAN_GLITCH_SAMPLE_TIME_SECONDS
            else -> 0f
        }

        fun pixel(x: Int, y: Int): FloatArray {
            val packed = input[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]
            return floatArrayOf(
                Color.red(packed) / 255f,
                Color.green(packed) / 255f,
                Color.blue(packed) / 255f
            )
        }

        /** GLSL v=0 is the bottom row; Android bitmaps store their top row first. */
        fun sampleUv(u: Float, v: Float): FloatArray {
            val x = u.coerceIn(0f, 1f) * width - 0.5f
            val y = (1f - v.coerceIn(0f, 1f)) * height - 0.5f
            val x0 = floor(x).toInt()
            val y0 = floor(y).toInt()
            val fx = x - x0
            val fy = y - y0
            val topLeft = pixel(x0, y0)
            val topRight = pixel(x0 + 1, y0)
            val bottomLeft = pixel(x0, y0 + 1)
            val bottomRight = pixel(x0 + 1, y0 + 1)
            return FloatArray(3) { channel ->
                val top = mixScalar(topLeft[channel], topRight[channel], fx)
                val bottom = mixScalar(bottomLeft[channel], bottomRight[channel], fx)
                mixScalar(top, bottom, fy)
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val original = pixel(x, y)
                val u = (x + 0.5f) / width
                val v = 1f - (y + 0.5f) / height
                var effected = original
                when (kind) {
                    0 -> luma(original).let { effected = floatArrayOf(it, it, it) }
                    1 -> effected = floatArrayOf(original[0] * 1.08f, original[1], original[2] * 0.86f)
                    2 -> {
                        val blockPx = 32f + (6f - 32f) * intensity
                        val sampleX = floor(u * width / blockPx) * blockPx + blockPx * 0.5f
                        val sampleY = floor(v * height / blockPx) * blockPx + blockPx * 0.5f
                        effected = sampleUv(sampleX / width, sampleY / height)
                    }
                    3, 4 -> {
                        val count = if (kind == 3) 2f else 3f
                        effected = sampleUv(fract(u * count), v)
                    }
                    5 -> {
                        val north = sampleUv(u, v + 2f / height)
                        val east = sampleUv(u + 2f / width, v)
                        val edge = maxOf(distance(original, north), distance(original, east))
                        val ink = smoothstep(0.12f, 0.38f, edge)
                        val steps = 9f + (4f - 9f) * intensity
                        val poster = original.map { floor(it * steps + 0.5f) / steps }.toFloatArray()
                        val inkColor = floatArrayOf(0.22f, 0.25f, 0.31f)
                        effected = FloatArray(3) { channel ->
                            poster[channel] * mixScalar(1f, inkColor[channel], ink * 0.8f)
                        }
                    }
                    6 -> {
                        var warpedU = u
                        var warpedV = v
                        var rim = 0f
                        var shine = 0f
                        for (index in 0..2) {
                            val centerX = when (index) {
                                0 -> 0.27f + 0.035f * kotlin.math.sin(elapsedSeconds * 0.65f)
                                1 -> 0.69f + 0.03f * kotlin.math.cos(elapsedSeconds * 0.5f + 1f)
                                else -> 0.48f + 0.04f * kotlin.math.sin(elapsedSeconds * 0.4f + 2f)
                            }
                            val centerY = when (index) {
                                0 -> 0.34f + 0.025f * kotlin.math.cos(elapsedSeconds * 0.8f)
                                1 -> 0.62f + 0.035f * kotlin.math.sin(elapsedSeconds * 0.7f)
                                else -> 0.22f + 0.025f * kotlin.math.cos(elapsedSeconds * 0.6f)
                            }
                            val dx = (u - centerX) * aspect
                            val dy = v - centerY
                            val radius = if (index == 1) 0.145f else 0.105f
                            val distanceToCenter = kotlin.math.sqrt(dx * dx + dy * dy)
                            val inside = 1f - smoothstep(radius - 0.012f, radius + 0.012f, distanceToCenter)
                            val edge = 1f - smoothstep(0.014f, 0.033f, kotlin.math.abs(distanceToCenter - radius))
                            warpedU += (centerX - u) * (inside * 0.18f)
                            warpedV += (centerY - v) * (inside * 0.18f)
                            rim = maxOf(rim, edge)
                            val glintX = dx + radius * 0.36f
                            val glintY = dy - radius * 0.48f
                            val glint = 1f - smoothstep(0.018f, 0.045f, kotlin.math.sqrt(glintX * glintX + glintY * glintY))
                            shine = maxOf(shine, glint * inside)
                        }
                        val refracted = sampleUv(warpedU, warpedV)
                        effected = add(
                            mix(refracted, floatArrayOf(0.72f, 0.91f, 1f), rim * 0.58f),
                            floatArrayOf(0.18f, 0.28f, 0.34f),
                            shine
                        )
                    }
                    7 -> {
                        val bob = kotlin.math.sin(elapsedSeconds * 2f) * 0.035f
                        effected = addHeart(original, u, v, 0.22f, 0.28f + bob, 0.095f, floatArrayOf(1f, 0.25f, 0.40f))
                        effected = addHeart(effected, u, v, 0.53f, 0.66f - bob, 0.078f, floatArrayOf(1f, 0.48f, 0.66f))
                        effected = addHeart(effected, u, v, 0.79f, 0.39f + bob, 0.088f, floatArrayOf(0.98f, 0.31f, 0.52f))
                    }
                    8 -> {
                        val phase = fract(elapsedSeconds * 1.15f)
                        val first = (phase - 0.09f) * 27f
                        val second = (phase - 0.24f) * 25f
                        val beat = maxOf(kotlin.math.exp(-first * first), 0.72f * kotlin.math.exp(-second * second))
                        effected = floatArrayOf(
                            original[0] * (1f + 0.14f * beat) + 0.10f * beat,
                            original[1] * (1f - 0.16f * beat) + 0.008f * beat,
                            original[2] * (1f - 0.10f * beat) + 0.018f * beat
                        )
                    }
                    9 -> {
                        val cellX = floor(u * 54f)
                        val cellY = floor(v * 54f)
                        val frost = if (hash21(cellX, cellY) >= 0.994f) 1f else 0f
                        val radius = kotlin.math.sqrt(((u - 0.5f) * aspect) * ((u - 0.5f) * aspect) + (v - 0.5f) * (v - 0.5f))
                        val vignette = smoothstep(0f, 0.7f, radius)
                        effected = floatArrayOf(
                            original[0] * 0.78f + 0.05f * vignette + 0.75f * frost * 0.45f,
                            original[1] * 0.93f + 0.12f * vignette + 0.91f * frost * 0.45f,
                            original[2] * 1.17f + 0.18f * vignette + frost * 0.45f
                        )
                    }
                    23 -> {
                        val shiftU = (2f + 5f * intensity) / width
                        effected = floatArrayOf(sampleUv(u + shiftU, v)[0], original[1], sampleUv(u - shiftU, v)[2])
                    }
                    24 -> {
                        val band = if (fract(v * 18f + elapsedSeconds * 2.7f) >= 0.70f) 1f else 0f
                        val row = floor(v * 32f)
                        val burst = if (hash21(floor(elapsedSeconds * 7f), row) >= 0.72f) 1f else 0f
                        val active = band * burst * intensity
                        val displacement = (hash21(elapsedSeconds, row) - 0.5f) * 0.10f * active
                        val displacedU = (u + displacement).coerceIn(0f, 1f)
                        effected = sampleUv(displacedU, v)
                        val shiftedRed = sampleUv(displacedU + 2f / width, v)[0]
                        effected[0] = mixScalar(effected[0], shiftedRed, active * 0.7f)
                    }
                    25 -> {
                        val sweep = 0.5f + 0.5f * kotlin.math.sin(elapsedSeconds * 0.42f + u * 3.2f - v * 1.8f)
                        val distance = kotlin.math.sqrt((u - 0.08f) * (u - 0.08f) + (v - 0.82f) * (v - 0.82f))
                        val leak = smoothstep(0.25f, 0.95f, sweep) * (1f - smoothstep(0.15f, 0.75f, distance))
                        effected = add(original, floatArrayOf(1f, 0.42f, 0.12f), leak * 0.78f)
                    }
                    26 -> {
                        val radius = kotlin.math.sqrt(((u - 0.5f) * aspect) * ((u - 0.5f) * aspect) + (v - 0.5f) * (v - 0.5f))
                        val shade = 1f - smoothstep(0.22f, 0.82f, radius)
                        effected = FloatArray(3) { original[it] * mixScalar(0.35f, 1f, shade) }
                    }
                    27 -> {
                        val north = sampleUv(u, v + 1f / height)
                        val east = sampleUv(u + 1f / width, v)
                        val south = sampleUv(u, v - 1f / height)
                        val west = sampleUv(u - 1f / width, v)
                        val edge = (distance(original, north) + distance(original, east) + distance(original, south) + distance(original, west)) * 1.8f
                        val glow = smoothstep(0.10f, 0.65f, edge) * 0.9f
                        effected = add(original, floatArrayOf(0.22f, 0.62f, 1f), glow)
                    }
                }
                val packed = input[y * width + x]
                output[y * width + x] = Color.argb(
                    Color.alpha(packed),
                    (mixScalar(original[0], effected[0], intensity).coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
                    (mixScalar(original[1], effected[1], intensity).coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
                    (mixScalar(original[2], effected[2], intensity).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                )
            }
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun color(pixel: Int, r: Float, g: Float, b: Float, graded: FloatArray, blend: Float) = Color.argb(Color.alpha(pixel), ((r + (graded[0].coerceIn(0f,1f)-r)*blend)*255f+.5f).toInt(), ((g + (graded[1].coerceIn(0f,1f)-g)*blend)*255f+.5f).toInt(), ((b + (graded[2].coerceIn(0f,1f)-b)*blend)*255f+.5f).toInt())
    private fun luma(c: FloatArray) = .299f*c[0] + .587f*c[1] + .114f*c[2]
    private fun distance(a: FloatArray, b: FloatArray) = kotlin.math.sqrt((a[0]-b[0])*(a[0]-b[0])+(a[1]-b[1])*(a[1]-b[1])+(a[2]-b[2])*(a[2]-b[2]))
    private fun mixScalar(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun mix(a: FloatArray, b: FloatArray, t: Float) = FloatArray(3) { a[it] + (b[it] - a[it]) * t }
    private fun add(a: FloatArray, b: FloatArray, t: Float) = FloatArray(3) { a[it] + b[it] * t }
    private fun fract(value: Float) = value - floor(value)
    private fun hash21(x: Float, y: Float): Float {
        var px = fract(x * 123.34f)
        var py = fract(y * 456.21f)
        val dot = px * (px + 45.32f) + py * (py + 45.32f)
        px += dot
        py += dot
        return fract(px * py)
    }

    private fun addHeart(base: FloatArray, u: Float, v: Float, centerU: Float, centerV: Float, size: Float, color: FloatArray): FloatArray {
        val px = kotlin.math.abs((u - centerU) / size)
        val py = (centerV - v) / size
        val a = px * px + py * py - 1f
        val heart = a * a * a - px * px * py * py * py
        val shape = 1f - smoothstep(-0.025f, 0.035f, heart)
        val shineX = px - (-0.27f)
        val shineY = py - 0.38f
        val shine = 1f - smoothstep(0f, 0.24f, kotlin.math.sqrt(shineX * shineX + shineY * shineY))
        val highlighted = FloatArray(3) { minOf(color[it] * (1f + shine * 0.28f), 1f) }
        return mix(base, highlighted, shape * 0.92f)
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
    private const val SCAN_GLITCH_SAMPLE_TIME_SECONDS = 0.41f
    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
