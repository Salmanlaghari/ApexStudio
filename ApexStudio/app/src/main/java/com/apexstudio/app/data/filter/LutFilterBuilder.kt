package com.apexstudio.app.data.filter

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImage3x3TextureSamplingFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLookupFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageToneCurveFilter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Builds a [GPUImageFilter] chain that emulates a 3D LUT lookup
 * without requiring a GL ES 3.0 3D texture.
 *
 * The cyberagent GPUImage library already ships a [GPUImageLookupFilter]
 * that maps each frame through a 2D-strip representation of a 3D LUT
 * (the .cube data is reshaped into a size*size x size tile and
 * uploaded as a regular 2D texture). That's the same trick the
 * original CapCut / Instagram filters use under the hood.
 *
 * For per-preset styling that isn't well-suited to a LUT (e.g. a
 * simple warm/cool shift) we also support a `GPUImageToneCurveFilter`
 * — but the primary path is the LUT lookup.
 */
class LutFilterBuilder(private val context: Context) {

    /**
     * Build a [GPUImageFilter] that applies the given preset at
     * [intensity] (0..1). Returns null if the preset has no .cube
     * asset or the asset is malformed.
     */
    fun build(preset: FilterPreset, intensity: Float): GPUImageFilter? {
        val lut = LutFilterEngine(context).loadLut(preset) ?: return null
        val lookup = buildLookupFromCube(lut) ?: return null
        val lutFilter = GPUImageLookupFilter()
        lutFilter.bitmap = lookup
        lutFilter.setIntensity(intensity.coerceIn(0f, 1f))
        return lutFilter
    }

    /**
     * Convert a 3D LUT (float[size*size*size*3]) into the 2D strip
     * representation GPUImage's lookup filter expects. The strip is
     * a (size*size) x size image: for each B slice, lay out the
     * size x size RG plane as a tile in a horizontal strip.
     */
    private fun buildLookupFromCube(lut: FloatArray): Bitmap? {
        val entries = lut.size / 3
        val size = Math.cbrt(entries.toDouble()).toInt()
        if (size * size * size != entries) return null
        val width = size * size
        val height = size
        val pixels = IntArray(width * height)
        for (varb in 0 until size) {
            for (varg in 0 until size) {
                for (varr in 0 until size) {
                    val srcIdx = (varb * size * size + varg * size + varr) * 3
                    val r = (lut[srcIdx].coerceIn(0f, 1f) * 255f).toInt()
                    val g = (lut[srcIdx + 1].coerceIn(0f, 1f) * 255f).toInt()
                    val b = (lut[srcIdx + 2].coerceIn(0f, 1f) * 255f).toInt()
                    val x = varb * size + varr
                    val y = varg
                    pixels[y * width + x] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
