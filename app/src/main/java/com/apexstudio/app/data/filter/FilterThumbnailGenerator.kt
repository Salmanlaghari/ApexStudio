package com.apexstudio.app.data.filter

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.min

/**
 * Generates 1:1 cropped filter preview thumbnails by applying each
 * LUT preset to a source [Bitmap] (typically the video's first frame).
 *
 * The .cube LUT is applied **on the CPU** with a trilinear 3D lookup —
 * no EGL/GL context required. The previous GPUImage-based version
 * could only render through the cyberagent lookup filter, which
 * hard-codes a 512x512 / 64-grid lookup image, so our arbitrary-size
 * .cube strips (17³ → 289×17, 33³ → 1089×33) either failed to render
 * or produced garbage, leaving the panel stuck on gradient
 * placeholders. CPU trilinear sampling is deterministic, matches the
 * GPU filter closely (both use GL_LINEAR-style trilinear
 * interpolation), and is fast at thumbnail resolution.
 *
 * Generation runs on [Dispatchers.Default] and supports cooperative
 * cancellation — if the source clip changes mid-generation, the old
 * job is cancelled automatically.
 */
object FilterThumbnailGenerator {

    private const val TAG = "FilterThumbGen"
    private const val THUMB_SIZE = 128

    /**
     * Generate 1:1 thumbnails for every preset in [manifest].
     *
     * @param context  Android context (only used to read LUT assets)
     * @param source   The video frame bitmap to apply filters to
     * @param manifest The filter manifest containing all presets
     * @return Map of filter ID → 1:1 thumbnail Bitmap.
     *         The key `null` maps to the unfiltered Original thumbnail.
     */
    suspend fun generateAll(
        context: Context,
        source: Bitmap,
        manifest: FilterManifest
    ): Map<String?, Bitmap> = withContext(Dispatchers.Default) {
        val result = mutableMapOf<String?, Bitmap>()

        // Original (unfiltered) thumbnail — center-cropped 1:1.
        // Kept as the map's `null` entry and NOT recycled until the
        // caller is done with the whole map.
        val originalThumb = centerCropAndScale(source)
        result[null] = originalThumb

        val engine = LutFilterEngine(context)

        // Generate thumbnails per category so the visible ones load first.
        for (category in manifest.categories) {
            for (preset in category.filters) {
                try {
                    ensureActive() // cooperative cancellation

                    val lut = engine.loadLut(preset) ?: continue
                    val entries = lut.size / 3
                    val size = Math.cbrt(entries.toDouble()).toInt()
                    if (size < 2 || size * size * size != entries) continue

                    val filtered = applyLutTrilinear(
                        src = originalThumb,
                        lut = lut,
                        size = size,
                        intensity = 1f
                    )
                    result[preset.id] = filtered
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e // propagate cancellation
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate thumbnail for ${preset.id}", e)
                }
            }
        }

        Log.d(TAG, "Generated ${result.size} filter thumbnails")
        result
    }

    /**
     * Center-crop [source] to a 1:1 square and scale to [THUMB_SIZE].
     */
    private fun centerCropAndScale(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val cropSize = min(w, h)
        val x = (w - cropSize) / 2
        val y = (h - cropSize) / 2

        val cropped = Bitmap.createBitmap(source, x, y, cropSize, cropSize)
        val scaled = Bitmap.createScaledBitmap(cropped, THUMB_SIZE, THUMB_SIZE, true)
        if (cropped != source) cropped.recycle()
        return scaled
    }

    /**
     * Apply a 3D LUT to [src] using exact trilinear interpolation over
     * the .cube lattice, then blend toward the original by [intensity].
     *
     * The .cube data is laid out with R changing fastest, then G, then
     * B — matching [CubeLutParser] and the GL filter's sampling order —
     * so index = b*size*size + g*size + r.
     */
    private fun applyLutTrilinear(
        src: Bitmap,
        lut: FloatArray,
        size: Int,
        intensity: Float
    ): Bitmap {
        val w = src.width
        val h = src.height
        val srcPx = IntArray(w * h)
        src.getPixels(srcPx, 0, w, 0, 0, w, h)

        val out = IntArray(w * h)
        val scale = size - 1
        val plane = size * size
        val stride = 3
        val last = scale

        var i = 0
        while (i < srcPx.size) {
            val argb = srcPx[i]
            val alpha = (argb ushr 24) and 0xFF
            val r8 = (argb ushr 16) and 0xFF
            val g8 = (argb ushr 8) and 0xFF
            val b8 = argb and 0xFF

            if (alpha == 0) {
                out[i] = argb
                i++
                continue
            }

            // Continuous lattice coordinates in [0, scale].
            val rF = r8 * scale / 255f
            val gF = g8 * scale / 255f
            val bF = b8 * scale / 255f

            // Floor/ceil lattice indices (clamped at the last cell).
            val r0 = min(floor(rF).toInt(), last - 1)
            val g0 = min(floor(gF).toInt(), last - 1)
            val b0 = min(floor(bF).toInt(), last - 1)
            val r1 = r0 + 1
            val g1 = g0 + 1
            val b1 = b0 + 1

            val tR = rF - r0
            val tG = gF - g0
            val tB = bF - b0

            // Base offsets of the 8 corners.
            val i000 = (b0 * plane + g0 * size + r0) * stride
            val i100 = i000 + stride
            val i010 = i000 + size * stride
            val i110 = i010 + stride
            val i001 = i000 + plane * stride
            val i101 = i001 + stride
            val i011 = i001 + size * stride
            val i111 = i011 + stride

            // Trilinear weights.
            val w000 = (1f - tR) * (1f - tG) * (1f - tB)
            val w100 = tR * (1f - tG) * (1f - tB)
            val w010 = (1f - tR) * tG * (1f - tB)
            val w110 = tR * tG * (1f - tB)
            val w001 = (1f - tR) * (1f - tG) * tB
            val w101 = tR * (1f - tG) * tB
            val w011 = (1f - tR) * tG * tB
            val w111 = tR * tG * tB

            var rOut = lut[i000] * w000 + lut[i100] * w100 + lut[i010] * w010 + lut[i110] * w110 +
                lut[i001] * w001 + lut[i101] * w101 + lut[i011] * w011 + lut[i111] * w111
            var gOut = lut[i000 + 1] * w000 + lut[i100 + 1] * w100 + lut[i010 + 1] * w010 + lut[i110 + 1] * w110 +
                lut[i001 + 1] * w001 + lut[i101 + 1] * w101 + lut[i011 + 1] * w011 + lut[i111 + 1] * w111
            var bOut = lut[i000 + 2] * w000 + lut[i100 + 2] * w100 + lut[i010 + 2] * w010 + lut[i110 + 2] * w110 +
                lut[i001 + 2] * w001 + lut[i101 + 2] * w101 + lut[i011 + 2] * w011 + lut[i111 + 2] * w111

            // Blend graded colour toward the source by intensity (0..1).
            val origR = r8 / 255f
            val origG = g8 / 255f
            val origB = b8 / 255f
            rOut = origR + (rOut - origR) * intensity
            gOut = origG + (gOut - origG) * intensity
            bOut = origB + (bOut - origB) * intensity

            val rr = (rOut.coerceIn(0f, 1f) * 255f).toInt()
            val gg = (gOut.coerceIn(0f, 1f) * 255f).toInt()
            val bb = (bOut.coerceIn(0f, 1f) * 255f).toInt()
            out[i] = (alpha shl 24) or (rr shl 16) or (gg shl 8) or bb
            i++
        }

        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
