package com.apexstudio.app.data.filter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLookupFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Generates 1:1 cropped filter preview thumbnails by applying each
 * LUT preset to a source [Bitmap] (typically the video's first frame).
 *
 * The thumbnails are center-cropped to a square and scaled down to
 * [THUMB_SIZE] px for fast rendering in the FilterPanel LazyRow.
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
     * @param context  Android context for GPUImage
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

        // Original (unfiltered) thumbnail — center-cropped 1:1
        val originalThumb = centerCropAndScale(source)
        result[null] = originalThumb

        // Use a single GPUImage instance for all presets (reuses GL context)
        val gpuImage = try {
            GPUImage(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create GPUImage instance", e)
            return@withContext result
        }

        // Generate thumbnails per category so the visible ones load first
        for (category in manifest.categories) {
            for (preset in category.filters) {
                try {
                    ensureActive() // cooperative cancellation

                    val lut = LutFilterEngine(context).loadLut(preset) ?: continue
                    val lookupBitmap = buildLookupStrip(lut) ?: continue

                    val filter = GPUImageLookupFilter()
                    filter.bitmap = lookupBitmap
                    filter.setIntensity(1f)

                    gpuImage.setFilter(filter)
                    val filtered = gpuImage.getBitmapWithFilterApplied(source)
                    val thumb = centerCropAndScale(filtered)
                    result[preset.id] = thumb

                    // Recycle intermediate bitmap
                    if (filtered != source) {
                        filtered.recycle()
                    }
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
     * Convert a 3D LUT (float[]) into the 2D strip Bitmap that
     * GPUImageLookupFilter expects. Same packing as [LutFilterBuilder].
     */
    private fun buildLookupStrip(lut: FloatArray): Bitmap? {
        val entries = lut.size / 3
        val size = Math.cbrt(entries.toDouble()).toInt()
        if (size * size * size != entries) return null

        val width = size * size
        val height = size
        val pixels = IntArray(width * height)

        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val srcIdx = (b * size * size + g * size + r) * 3
                    val rr = (lut[srcIdx].coerceIn(0f, 1f) * 255f).toInt()
                    val gg = (lut[srcIdx + 1].coerceIn(0f, 1f) * 255f).toInt()
                    val bb = (lut[srcIdx + 2].coerceIn(0f, 1f) * 255f).toInt()
                    val x = b * size + r
                    val y = g
                    pixels[y * width + x] = 0xFF000000.toInt() or (rr shl 16) or (gg shl 8) or bb
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
