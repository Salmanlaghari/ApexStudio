package com.apexstudio.app.data.filter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.util.LruCache
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLookupFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSaturationFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance, GPU-accelerated LUT Color Grading Engine
 * utilizing cyberagent [GPUImage] and [GPUImageLookupFilter].
 *
 * Provides:
 * - 3D .cube LUT to 2D texture strip caching for zero-latency switching
 * - Multi-filter chaining via [GPUImageFilterGroup] (LUT + Temp + Tint + Contrast + Saturation)
 * - Instant bitmap frame rendering for live timeline previews
 * - Real-time A/B split-screen comparison generator
 * - Custom .cube LUT import and storage
 */
class GpuImageLutEngine(private val context: Context) {

    companion object {
        private const val TAG = "GpuImageLutEngine"
        private val stripBitmapCache = ConcurrentHashMap<String, Bitmap>()
        private val previewCache = LruCache<String, Bitmap>(24)
    }

    private val lutEngine = LutFilterEngine(context)

    /**
     * Retrieves or converts the 2D strip lookup bitmap from the preset's 3D .cube LUT.
     * Caches the resulting bitmap in memory for subsequent instant accesses.
     */
    fun getLookupBitmap(preset: FilterPreset): Bitmap? {
        val cached = stripBitmapCache[preset.id]
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val rawCube = if (preset.asset.startsWith("custom/")) {
            loadCustomLut(preset.asset)
        } else {
            lutEngine.loadLut(preset)
        } ?: return null

        val stripBitmap = convertCubeToStripBitmap(rawCube) ?: return null
        stripBitmapCache[preset.id] = stripBitmap
        return stripBitmap
    }

    /**
     * Converts raw 3D LUT float values (size^3 * 3) into a 2D strip bitmap
     * expected by [GPUImageLookupFilter].
     */
    private fun convertCubeToStripBitmap(lut: FloatArray): Bitmap? {
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

    /**
     * Builds a chained [GPUImageFilter] combining the 3D LUT with optional
     * secondary color grading adjustments (Contrast, Saturation, Brightness, White Balance).
     */
    fun buildFilter(
        preset: FilterPreset?,
        intensity: Float = 1.0f,
        contrast: Float = 1.0f,
        saturation: Float = 1.0f,
        brightness: Float = 0.0f,
        temperatureK: Float = 5000f,
        tint: Float = 0f
    ): GPUImageFilter {
        val filters = mutableListOf<GPUImageFilter>()

        // 1. Primary 3D LUT Lookup Filter
        if (preset != null && intensity > 0f) {
            val lookupBmp = getLookupBitmap(preset)
            if (lookupBmp != null) {
                val lookupFilter = GPUImageLookupFilter()
                lookupFilter.bitmap = lookupBmp
                lookupFilter.setIntensity(intensity.coerceIn(0f, 1f))
                filters.add(lookupFilter)
            }
        }

        // 2. White Balance (Color Temperature & Tint)
        if (temperatureK != 5000f || tint != 0f) {
            val wbFilter = GPUImageWhiteBalanceFilter(temperatureK, tint)
            filters.add(wbFilter)
        }

        // 3. Contrast adjustment (1.0 = neutral)
        if (contrast != 1.0f) {
            val contrastFilter = GPUImageContrastFilter(contrast.coerceIn(0.5f, 2.0f))
            filters.add(contrastFilter)
        }

        // 4. Saturation adjustment (1.0 = neutral)
        if (saturation != 1.0f) {
            val saturationFilter = GPUImageSaturationFilter(saturation.coerceIn(0f, 2.0f))
            filters.add(saturationFilter)
        }

        // 5. Brightness adjustment (0.0 = neutral)
        if (brightness != 0.0f) {
            val brightnessFilter = GPUImageBrightnessFilter(brightness.coerceIn(-0.5f, 0.5f))
            filters.add(brightnessFilter)
        }

        return when {
            filters.isEmpty() -> GPUImageFilter()
            filters.size == 1 -> filters[0]
            else -> GPUImageFilterGroup(filters)
        }
    }

    /**
     * Applies the LUT and color grading chain to a [Bitmap] using hardware-accelerated [GPUImage].
     */
    suspend fun applyGrade(
        source: Bitmap,
        preset: FilterPreset?,
        intensity: Float = 1.0f,
        contrast: Float = 1.0f,
        saturation: Float = 1.0f,
        brightness: Float = 0.0f,
        temperatureK: Float = 5000f,
        tint: Float = 0f
    ): Bitmap = withContext(Dispatchers.Default) {
        if (preset == null && contrast == 1f && saturation == 1f && brightness == 0f && temperatureK == 5000f && tint == 0f) {
            return@withContext source
        }

        val cacheKey = "${preset?.id}_${(intensity * 100).toInt()}_${(contrast * 100).toInt()}_${(saturation * 100).toInt()}_${(temperatureK).toInt()}"
        previewCache.get(cacheKey)?.let { return@withContext it }

        try {
            val gpuImage = GPUImage(context)
            gpuImage.setImage(source)
            val filter = buildFilter(preset, intensity, contrast, saturation, brightness, temperatureK, tint)
            gpuImage.setFilter(filter)
            val result = gpuImage.bitmapWithFilterApplied ?: source
            previewCache.put(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply GPUImage color grade", e)
            source
        }
    }

    /**
     * Creates a split Before/After comparison bitmap:
     * Left side shows the Original un-graded frame, Right side shows the GPUImage-graded frame,
     * with a subtle vertical dividing line.
     */
    suspend fun createSplitComparisonBitmap(
        source: Bitmap,
        preset: FilterPreset?,
        intensity: Float = 1.0f,
        splitPosition: Float = 0.5f
    ): Bitmap = withContext(Dispatchers.Default) {
        val graded = applyGrade(source, preset, intensity)
        if (preset == null || splitPosition <= 0f) return@withContext graded
        if (splitPosition >= 1f) return@withContext source

        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val splitPx = (width * splitPosition).toInt().coerceIn(0, width)

        // Draw left side (Original)
        val leftSrc = Rect(0, 0, splitPx, height)
        val leftDst = Rect(0, 0, splitPx, height)
        canvas.drawBitmap(source, leftSrc, leftDst, paint)

        // Draw right side (Graded)
        val rightSrc = Rect(splitPx, 0, width, height)
        val rightDst = Rect(splitPx, 0, width, height)
        canvas.drawBitmap(graded, rightSrc, rightDst, paint)

        // Draw sleek vertical divider line
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 3f
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        canvas.drawLine(splitPx.toFloat(), 0f, splitPx.toFloat(), height.toFloat(), linePaint)

        output
    }

    /**
     * Imports a user-provided .cube file from an [InputStream] into the app's internal storage
     * and returns a [FilterPreset] ready for live use.
     */
    fun importCustomCube(name: String, inputStream: InputStream): FilterPreset? {
        return try {
            val customDir = File(context.filesDir, "custom_luts").apply { mkdirs() }
            val sanitizedName = name.replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
            val fileName = "lut_${System.currentTimeMillis()}_$sanitizedName.cube"
            val targetFile = File(customDir, fileName)

            FileOutputStream(targetFile).use { out ->
                inputStream.copyTo(out)
            }

            val preset = FilterPreset(
                id = "custom_${targetFile.nameWithoutExtension}",
                name = name.removeSuffix(".cube").replace('_', ' ').capitalizeWords(),
                category = "Custom",
                asset = "custom/$fileName"
            )

            // Validate that it parses properly
            val parsed = loadCustomLut(preset.asset)
            if (parsed != null) {
                preset
            } else {
                targetFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import custom .cube LUT", e)
            null
        }
    }

    private fun loadCustomLut(assetPath: String): FloatArray? {
        val fileName = assetPath.removePrefix("custom/")
        val file = File(File(context.filesDir, "custom_luts"), fileName)
        if (!file.exists()) return null

        return try {
            file.bufferedReader().use { reader ->
                CubeLutParser.parse(reader)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse custom LUT file: $file", e)
            null
        }
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}
