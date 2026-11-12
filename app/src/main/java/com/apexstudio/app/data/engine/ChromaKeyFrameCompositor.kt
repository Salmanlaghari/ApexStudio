package com.apexstudio.app.data.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.apexstudio.app.data.background.BackgroundReplaceMath
import com.apexstudio.app.domain.model.ChromaKeySettings
import com.apexstudio.app.domain.model.hasValidParameters
import com.apexstudio.app.domain.model.sanitized
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Reusable CPU chroma-key compositor for one video frame at a time.
 * It uses the same Euclidean key distance, smoothstep feather and spill
 * suppression as [ChromaKeyProcessor] and the preview/export GL shader.
 * The background is cover-cropped once; row-sized buffers keep memory use
 * bounded while the engine processes long clips.
 */
class ChromaKeyFrameCompositor(
    private val width: Int,
    private val height: Int,
    background: Bitmap,
    settings: ChromaKeySettings
) {
    private val safeSettings = settings.sanitized()
    private val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val backgroundFrame = coverCrop(background)
    private val sourceRow = IntArray(width)
    private val backgroundRow = IntArray(width)
    private val outputRow = IntArray(width)
    private val keyR = Color.red(safeSettings.keyColorArgb.toInt()) / 255f
    private val keyG = Color.green(safeSettings.keyColorArgb.toInt()) / 255f
    private val keyB = Color.blue(safeSettings.keyColorArgb.toInt()) / 255f
    private var released = false

    val result: Bitmap get() {
        check(!released) { "ChromaKeyFrameCompositor is released" }
        return output
    }

    init {
        require(width > 0 && height > 0) { "Chroma compositor dimensions must be positive." }
        require(background.width > 0 && background.height > 0) { "Wallpaper is empty." }
        require(safeSettings.enabled) { "Chroma key must be enabled." }
        require(safeSettings.hasValidParameters()) { "Chroma key settings are invalid." }
    }

    private fun coverCrop(source: Bitmap): Bitmap {
        val crop = BackgroundReplaceMath.coverCropRect(source.width, source.height, width, height)
        val left = floor(crop[0]).toInt().coerceIn(0, source.width - 1)
        val top = floor(crop[1]).toInt().coerceIn(0, source.height - 1)
        val right = kotlin.math.ceil(crop[2]).toInt().coerceIn(left + 1, source.width)
        val bottom = kotlin.math.ceil(crop[3]).toInt().coerceIn(top + 1, source.height)
        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        val ownedCrop = if (cropped === source) {
            cropped.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException("Could not copy wallpaper bitmap.")
        } else cropped
        if (ownedCrop.width == width && ownedCrop.height == height) return ownedCrop
        val scaled = try {
            Bitmap.createScaledBitmap(ownedCrop, width, height, true)
        } catch (error: Throwable) {
            ownedCrop.recycle()
            throw error
        }
        if (scaled !== ownedCrop) ownedCrop.recycle()
        return scaled
    }

    /** Composite [frame] into the reusable [result] bitmap and return that owned bitmap. */
    fun composite(frame: Bitmap): Bitmap {
        check(!released) { "ChromaKeyFrameCompositor is released" }
        require(frame.width == width && frame.height == height) {
            "Frame ${frame.width}×${frame.height} != compositor ${width}×${height}"
        }
        val similarity = safeSettings.similarity
        val smoothness = safeSettings.smoothness
        val spill = safeSettings.spillSuppression
        for (y in 0 until height) {
            frame.getPixels(sourceRow, 0, width, 0, y, width, 1)
            backgroundFrame.getPixels(backgroundRow, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val pixel = sourceRow[x]
                val r = Color.red(pixel) / 255f
                val g = Color.green(pixel) / 255f
                val b = Color.blue(pixel) / 255f
                val dr = r - keyR
                val dg = g - keyG
                val db = b - keyB
                val distance = sqrt((dr * dr + dg * dg + db * db) / 3f)
                val t = ((distance - similarity) / smoothness).coerceIn(0f, 1f)
                val alpha = t * t * (3f - 2f * t)

                var foregroundR = r
                var foregroundG = g
                var foregroundB = b
                val keyDominant = maxOf(keyR, keyG, keyB)
                if (keyG == keyDominant && g > maxOf(r, b)) {
                    foregroundG = g - (g - maxOf(r, b)) * spill
                } else if (keyB == keyDominant && b > maxOf(r, g)) {
                    foregroundB = b - (b - maxOf(r, g)) * spill
                } else if (keyR == keyDominant && r > maxOf(g, b)) {
                    foregroundR = r - (r - maxOf(g, b)) * spill
                }

                val backgroundPixel = backgroundRow[x]
                val outR = (foregroundR * alpha * 255f + Color.red(backgroundPixel) * (1f - alpha))
                    .toInt().coerceIn(0, 255)
                val outG = (foregroundG * alpha * 255f + Color.green(backgroundPixel) * (1f - alpha))
                    .toInt().coerceIn(0, 255)
                val outB = (foregroundB * alpha * 255f + Color.blue(backgroundPixel) * (1f - alpha))
                    .toInt().coerceIn(0, 255)
                outputRow[x] = Color.rgb(outR, outG, outB)
            }
            output.setPixels(outputRow, 0, width, 0, y, width, 1)
        }
        return output
    }

    /** Idempotent cleanup; the caller retains ownership of the input frame and wallpaper. */
    fun release() {
        if (released) return
        released = true
        if (!output.isRecycled) output.recycle()
        if (!backgroundFrame.isRecycled) backgroundFrame.recycle()
    }
}
