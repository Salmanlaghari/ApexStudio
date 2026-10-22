package com.apexstudio.app.data.color

import android.graphics.Bitmap
import java.io.File
import java.util.Locale
import kotlinx.serialization.Serializable
import com.apexstudio.app.data.filter.LutTexture
import kotlin.math.*

@Serializable
data class ColorWheelVector(val x: Float = 0f, val y: Float = 0f)

/** Standalone, non-destructive SDR color grade. No inference, prompts or generated media. */
@Serializable
data class ColorStudioGrade(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val shadows: ColorWheelVector = ColorWheelVector(),
    val midtones: ColorWheelVector = ColorWheelVector(),
    val highlights: ColorWheelVector = ColorWheelVector(),
    val rCurve: List<Float> = IDENTITY_CURVE,
    val gCurve: List<Float> = IDENTITY_CURVE,
    val bCurve: List<Float> = IDENTITY_CURVE,
    val lumaCurve: List<Float> = IDENTITY_CURVE
) {
    companion object { val IDENTITY_CURVE = listOf(0f, 0.25f, 0.5f, 0.75f, 1f) }
}

/** Reject malformed values before a project render instead of relying on LUT-side clamping. */
fun ColorStudioGrade.hasValidRenderParameters(): Boolean =
    brightness.isFinite() && brightness in -1f..1f &&
        contrast.isFinite() && contrast in 0f..2f &&
        saturation.isFinite() && saturation in 0f..2f &&
        listOf(shadows, midtones, highlights).all { it.x.isFinite() && it.x in -1f..1f && it.y.isFinite() && it.y in -1f..1f } &&
        listOf(rCurve, gCurve, bCurve, lumaCurve).all { curve ->
            curve.size == 5 && curve.all { it.isFinite() && it in 0f..1f }
        }

/** Build the 2D RGB LUT strip expected by LutFilterGlEffect from the same LUT as CPU preview/export. */
fun ColorStudioGrade.toLutTexture(size: Int = 33): LutTexture {
    require(size in 2..65)
    val lut = ColorStudioLut(this, size)
    val pixels = IntArray(size * size * size)
    for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
        val input = 0xFF000000.toInt() or
            (((r * 255 + (size - 1) / 2) / (size - 1)) shl 16) or
            (((g * 255 + (size - 1) / 2) / (size - 1)) shl 8) or
            ((b * 255 + (size - 1) / 2) / (size - 1))
        val output = lut.processColor(input)
        pixels[r * size * size + b * size + g] = output
    }
    return LutTexture(pixels, size * size, size, size)
}

/**
 * One 3D lattice serves both CPU still/frame previews and FFmpeg lut3d video
 * export. BOTH use trilinear interpolation, not two unrelated grading engines.
 * Cube ordering is standard .cube: red changes fastest, then green, then blue.
 * This is an 8-bit SDR/sRGB-oriented operation, not HDR mastering/color management.
 */
class ColorStudioLut(grade: ColorStudioGrade, val size: Int = 33) {
    private val cube: FloatArray
    private val brightness = finite(grade.brightness, 0f).coerceIn(-1f, 1f)
    private val contrast = finite(grade.contrast, 1f).coerceIn(0f, 2f)
    private val saturation = finite(grade.saturation, 1f).coerceIn(0f, 2f)
    private val shadows = wheel(grade.shadows)
    private val midtones = wheel(grade.midtones)
    private val highlights = wheel(grade.highlights)
    private val curves = listOf(curve(grade.rCurve), curve(grade.gCurve), curve(grade.bCurve))
    private val master = curve(grade.lumaCurve)

    init {
        require(size in 2..65) { "Color LUT size must be between 2 and 65" }
        cube = FloatArray(size * size * size * 3)
        val scale = (size - 1).toFloat()
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            val base = index(r, g, b)
            val original = floatArrayOf(r / scale, g / scale, b / scale)
            val adjusted = FloatArray(3) { ((original[it] - 0.5f) * contrast + 0.5f + brightness).coerceIn(0f, 1f) }
            val luma = (adjusted[0] * 0.2126f + adjusted[1] * 0.7152f + adjusted[2] * 0.0722f).coerceIn(0f, 1f)
            val shadowWeight = (1f - luma).pow(2)
            val midWeight = 4f * luma * (1f - luma)
            val highlightWeight = luma * luma
            for (c in 0..2) adjusted[c] += shadows[c] * shadowWeight + midtones[c] * midWeight + highlights[c] * highlightWeight
            val satLuma = adjusted[0] * 0.2126f + adjusted[1] * 0.7152f + adjusted[2] * 0.0722f
            for (c in 0..2) {
                val channel = (satLuma + (adjusted[c] - satLuma) * saturation).coerceIn(0f, 1f)
                adjusted[c] = evaluate(curves[c], channel)
            }
            // L is a luminance curve, not an unrelated decorative RGB line.
            val curvedLuma = adjusted[0] * 0.2126f + adjusted[1] * 0.7152f + adjusted[2] * 0.0722f
            val targetLuma = evaluate(master, curvedLuma)
            for (c in 0..2) {
                cube[base + c] = (if (curvedLuma > 0.00001f) adjusted[c] * targetLuma / curvedLuma else targetLuma)
                    .coerceIn(0f, 1f)
            }
        }
    }

    /** Returns a fresh bitmap. The source bitmap/pixels and its alpha never change. */
    fun process(source: Bitmap): Bitmap {
        require(!source.isRecycled) { "Original media frame is unavailable" }
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val row = IntArray(source.width)
        try {
            for (y in 0 until source.height) {
                source.getPixels(row, 0, source.width, 0, y, source.width, 1)
                for (x in row.indices) row[x] = processColor(row[x])
                output.setPixels(row, 0, source.width, 0, y, source.width, 1)
            }
        } catch (error: Throwable) {
            output.recycle()
            throw error
        }
        return output
    }

    fun processColor(argb: Int): Int {
        val scale = (size - 1) / 255f
        val r = ((argb ushr 16) and 255) * scale
        val g = ((argb ushr 8) and 255) * scale
        val b = (argb and 255) * scale
        val r0 = r.toInt().coerceAtMost(size - 2); val rf = r - r0
        val g0 = g.toInt().coerceAtMost(size - 2); val gf = g - g0
        val b0 = b.toInt().coerceAtMost(size - 2); val bf = b - b0
        var packed = argb and -0x1000000
        for (c in 0..2) {
            val bottom0 = lerp(cube[index(r0, g0, b0) + c], cube[index(r0 + 1, g0, b0) + c], rf)
            val bottom1 = lerp(cube[index(r0, g0 + 1, b0) + c], cube[index(r0 + 1, g0 + 1, b0) + c], rf)
            val top0 = lerp(cube[index(r0, g0, b0 + 1) + c], cube[index(r0 + 1, g0, b0 + 1) + c], rf)
            val top1 = lerp(cube[index(r0, g0 + 1, b0 + 1) + c], cube[index(r0 + 1, g0 + 1, b0 + 1) + c], rf)
            val value = lerp(lerp(bottom0, bottom1, gf), lerp(top0, top1, gf), bf)
            packed = packed or ((value * 255f).roundToInt().coerceIn(0, 255) shl (16 - c * 8))
        }
        return packed
    }

    /** Call off the main thread, into a new temporary file owned by the export session. */
    fun writeCube(file: File) {
        file.bufferedWriter().use { writer ->
            writer.write("TITLE \"ApexStudio conventional SDR grade\"\nLUT_3D_SIZE $size\nDOMAIN_MIN 0 0 0\nDOMAIN_MAX 1 1 1\n")
            for (i in cube.indices step 3) {
                writer.write(String.format(Locale.US, "%.7f %.7f %.7f\n", cube[i], cube[i + 1], cube[i + 2]))
            }
        }
    }

    private fun index(r: Int, g: Int, b: Int) = ((b * size + g) * size + r) * 3

    companion object {
        private fun finite(value: Float, fallback: Float) = if (value.isFinite()) value else fallback
        private fun lerp(a: Float, b: Float, fraction: Float) = a + (b - a) * fraction
        private fun curve(points: List<Float>): List<Float> =
            if (points.size == 5 && points.all { it.isFinite() }) points.map { it.coerceIn(0f, 1f) }
            else ColorStudioGrade.IDENTITY_CURVE
        private fun evaluate(points: List<Float>, input: Float): Float {
            val position = input.coerceIn(0f, 1f) * 4f
            val lower = position.toInt().coerceAtMost(3)
            return lerp(points[lower], points[lower + 1], position - lower)
        }
        private fun wheel(vector: ColorWheelVector): FloatArray {
            val x = finite(vector.x, 0f).coerceIn(-1f, 1f)
            val y = finite(vector.y, 0f).coerceIn(-1f, 1f)
            val strength = hypot(x, y).coerceAtMost(1f) * 0.25f
            if (strength == 0f) return floatArrayOf(0f, 0f, 0f)
            val sector = (((atan2(y, x) / (2f * PI.toFloat())) + 1f) % 1f) * 6f
            val fraction = sector - floor(sector)
            val rgb = when (floor(sector).toInt() % 6) {
                0 -> floatArrayOf(1f, fraction, 0f)
                1 -> floatArrayOf(1f - fraction, 1f, 0f)
                2 -> floatArrayOf(0f, 1f, fraction)
                3 -> floatArrayOf(0f, 1f - fraction, 1f)
                4 -> floatArrayOf(fraction, 0f, 1f)
                else -> floatArrayOf(1f, 0f, 1f - fraction)
            }
            val average = rgb.average().toFloat()
            return FloatArray(3) { (rgb[it] - average) * strength }
        }
    }
}
