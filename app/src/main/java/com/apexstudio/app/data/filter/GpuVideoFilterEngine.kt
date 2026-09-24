package com.apexstudio.app.data.filter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import android.util.LruCache
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Supported Stylistic Effects powered by CyberAgent GPUImage.
 */
@Serializable
enum class StylisticEffectType(
    val title: String,
    val description: String,
    val category: String,
    val defaultParam: Float = 0.5f,
    val paramName: String = "Intensity"
) {
    NONE("Original", "No stylistic effect applied", "Basic"),
    VIGNETTE("Vignette", "Soft cinema border shading focusing attention to center", "Cinematic", 0.75f, "Border Depth"),
    HALFTONE("Halftone Print", "Classic retro comic & newspaper dot matrix pattern", "Artistic", 0.4f, "Dot Density"),
    TOON("Cel Cartoon", "Stylized animation contours with quantized color bands", "Artistic", 0.5f, "Threshold"),
    SMOOTH_TOON("Smooth Anime", "Smooth cel-shaded anime look with softened edges", "Artistic", 0.5f, "Quantization"),
    SKETCH("Pencil Sketch", "Hand-drawn monochrome pencil illustration lineart", "Artistic", 0.8f, "Line Weight"),
    PIXELATION("8-Bit Pixel", "Retro arcade game pixel grid aesthetic", "Retro", 0.35f, "Pixel Size"),
    EMBOSS("Metallic Emboss", "Tactile 3D metallic stamped surface relief", "Stylistic", 0.5f, "Relief Depth"),
    SWIRL("Vortex Swirl", "Gravitational optical vortex spiral distortion", "Distortion", 0.5f, "Twist Angle"),
    BULGE("Fish-Eye Bulge", "Wide-angle curved glass lens perspective", "Distortion", 0.6f, "Curvature"),
    GLASS_SPHERE("Crystal Ball", "Optical refractive crystal sphere inversion", "Distortion", 0.5f, "Refraction"),
    KUWAHARA("Oil Painting", "Impressionistic painterly brush stroke smoothing", "Artistic", 0.5f, "Brush Radius"),
    POSTERIZE("Pop Art Poster", "Bold Pop-art color separation and tonal reduction", "Artistic", 0.4f, "Color Bands"),
    CROSSHATCH("Crosshatch", "Finely etched pen-and-ink crosshatched shading", "Artistic", 0.4f, "Hatch Spacing"),
    SOLARIZE("Solarize", "Experimental Sabattier photographic tone reversal", "Stylistic", 0.5f, "Threshold"),
    GAUSSIAN_BLUR("Dreamy Blur", "Soft atmospheric bloom and ethereal glow", "Cinematic", 0.5f, "Blur Radius"),
    SHARPEN("Crisp Clarity", "Ultra-high definition edge clarity and texture", "Cinematic", 0.6f, "Sharpness"),
    SEPIA("Antique Sepia", "Warm golden 19th-century vintage photograph", "Retro", 0.8f, "Warmth"),
    INVERT("Color Negative", "Striking reversed chromatic optical negative", "Stylistic", 1.0f, "Inversion")
}

/**
 * Complete GPUImage filter configuration combining 3D LUT grading,
 * parametric color adjustments, and artistic stylistic effects.
 */
@Serializable
data class GpuFilterConfig(
    val filterPresetId: String? = null,
    val filterIntensity: Float = 1.0f,
    val stylisticEffect: StylisticEffectType = StylisticEffectType.NONE,
    val stylisticIntensity: Float = 1.0f,
    val effectParam: Float = 0.5f,
    // Color Grading Parameters
    val brightness: Float = 0f,        // -1.0 .. 1.0
    val contrast: Float = 1.0f,        // 0.0 .. 2.0
    val saturation: Float = 1.0f,      // 0.0 .. 2.0
    val temperature: Float = 5000f,    // 2000K .. 8000K
    val tint: Float = 0f,              // -100.0 .. 100.0
    val exposure: Float = 0f,          // -2.0 .. 2.0
    val gamma: Float = 1.0f,           // 0.2 .. 3.0
    val highlights: Float = 1.0f,      // 0.0 .. 1.0
    val shadows: Float = 0f,           // 0.0 .. 1.0
    val vibrance: Float = 0f           // -1.0 .. 1.0
) {
    val isDefault: Boolean
        get() = filterPresetId == null &&
                stylisticEffect == StylisticEffectType.NONE &&
                brightness == 0f &&
                contrast == 1.0f &&
                saturation == 1.0f &&
                temperature == 5000f &&
                tint == 0f &&
                exposure == 0f &&
                gamma == 1.0f &&
                highlights == 1.0f &&
                shadows == 0f &&
                vibrance == 0f
}

/**
 * High-performance Video Filtering Engine utilizing [jp.co.cyberagent.android.gpuimage.GPUImage]
 * for real-time color grading and artistic stylistic effects.
 */
class GpuVideoFilterEngine(private val context: Context) {

    companion object {
        private const val TAG = "GpuVideoFilterEngine"
        private val frameCache = LruCache<String, Bitmap>(16)
    }

    private val lutEngine = GpuImageLutEngine(context)

    /**
     * Constructs a composite [GPUImageFilterGroup] incorporating LUT color grading,
     * fine color adjustments, and stylistic shaders in an optimal pipeline sequence.
     */
    fun buildFilterGroup(config: GpuFilterConfig, lutBitmap: Bitmap? = null): GPUImageFilterGroup {
        val filterList = mutableListOf<GPUImageFilter>()

        // 1. 3D LUT Color Grading Layer
        if (lutBitmap != null && config.filterIntensity > 0f) {
            val lookupFilter = GPUImageLookupFilter(config.filterIntensity.coerceIn(0f, 1f))
            lookupFilter.bitmap = lutBitmap
            filterList.add(lookupFilter)
        }

        // 2. Parametric Color Grading Adjustments
        if (config.temperature != 5000f || config.tint != 0f) {
            filterList.add(GPUImageWhiteBalanceFilter(config.temperature, config.tint))
        }

        if (config.exposure != 0f) {
            filterList.add(GPUImageExposureFilter(config.exposure.coerceIn(-2f, 2f)))
        }

        if (config.brightness != 0f) {
            filterList.add(GPUImageBrightnessFilter(config.brightness.coerceIn(-1f, 1f)))
        }

        if (config.contrast != 1.0f) {
            filterList.add(GPUImageContrastFilter(config.contrast.coerceIn(0.1f, 2.5f)))
        }

        if (config.saturation != 1.0f) {
            filterList.add(GPUImageSaturationFilter(config.saturation.coerceIn(0f, 2.5f)))
        }

        if (config.gamma != 1.0f) {
            filterList.add(GPUImageGammaFilter(config.gamma.coerceIn(0.2f, 3.0f)))
        }

        if (config.shadows != 0f || config.highlights != 1.0f) {
            filterList.add(GPUImageHighlightShadowFilter(config.shadows.coerceIn(0f, 1f), config.highlights.coerceIn(0f, 1f)))
        }

        if (config.vibrance != 0f) {
            filterList.add(GPUImageVibranceFilter(config.vibrance.coerceIn(-1f, 1f)))
        }

        // 3. Artistic Stylistic Effects
        if (config.stylisticEffect != StylisticEffectType.NONE && config.stylisticIntensity > 0f) {
            val effectFilter = buildStylisticFilter(config.stylisticEffect, config.stylisticIntensity, config.effectParam)
            if (effectFilter != null) {
                filterList.add(effectFilter)
            }
        }

        return if (filterList.isEmpty()) {
            GPUImageFilterGroup(listOf(GPUImageFilter()))
        } else {
            GPUImageFilterGroup(filterList)
        }
    }

    /**
     * Builds the corresponding CyberAgent [GPUImageFilter] for the selected [StylisticEffectType].
     */
    private fun buildStylisticFilter(type: StylisticEffectType, intensity: Float, param: Float): GPUImageFilter? {
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        val clampedParam = param.coerceIn(0f, 1f)

        return when (type) {
            StylisticEffectType.NONE -> null

            StylisticEffectType.VIGNETTE -> {
                val center = PointF(0.5f, 0.5f)
                val color = floatArrayOf(0.0f, 0.0f, 0.0f)
                val start = (0.85f - clampedIntensity * 0.45f).coerceAtLeast(0.1f)
                val end = (1.5f - clampedIntensity * 0.5f).coerceAtLeast(start + 0.1f)
                GPUImageVignetteFilter(center, color, start, end)
            }

            StylisticEffectType.HALFTONE -> {
                val filter = GPUImageHalftoneFilter()
                val pixelSize = 0.005f + clampedParam * 0.035f
                filter.setFractionalWidthOfAPixel(pixelSize)
                filter
            }

            StylisticEffectType.TOON -> {
                val filter = GPUImageToonFilter()
                filter.setThreshold(0.2f + clampedParam * 0.5f)
                filter.setQuantizationLevels(4.0f + (1f - clampedParam) * 6.0f)
                filter
            }

            StylisticEffectType.SMOOTH_TOON -> {
                val filter = GPUImageSmoothToonFilter()
                filter.setThreshold(0.2f + clampedParam * 0.5f)
                filter.setQuantizationLevels(4.0f + (1f - clampedParam) * 6.0f)
                filter.setBlurSize(0.5f + clampedIntensity * 1.5f)
                filter
            }

            StylisticEffectType.SKETCH -> {
                GPUImageSketchFilter()
            }

            StylisticEffectType.PIXELATION -> {
                val filter = GPUImagePixelationFilter()
                val pixel = 4.0f + clampedParam * 40.0f
                filter.setPixel(pixel)
                filter
            }

            StylisticEffectType.EMBOSS -> {
                val filter = GPUImageEmbossFilter()
                filter.intensity = clampedIntensity * 2.5f
                filter
            }

            StylisticEffectType.SWIRL -> {
                val filter = GPUImageSwirlFilter()
                filter.setCenter(PointF(0.5f, 0.5f))
                filter.setRadius(0.55f)
                filter.setAngle(1.0f + clampedParam * 4.0f)
                filter
            }

            StylisticEffectType.BULGE -> {
                val filter = GPUImageBulgeDistortionFilter()
                filter.setCenter(PointF(0.5f, 0.5f))
                filter.setRadius(0.55f)
                val scale = (clampedParam * 1.2f) - 0.4f
                filter.setScale(scale)
                filter
            }

            StylisticEffectType.GLASS_SPHERE -> {
                val filter = GPUImageGlassSphereFilter()
                filter.setCenter(PointF(0.5f, 0.5f))
                filter.setRadius(0.42f)
                filter.setRefractiveIndex(1.0f + clampedParam * 0.65f)
                filter
            }

            StylisticEffectType.KUWAHARA -> {
                val radius = (2 + (clampedParam * 6).toInt()).coerceIn(2, 8)
                GPUImageKuwaharaFilter(radius)
            }

            StylisticEffectType.POSTERIZE -> {
                val levels = (3 + (clampedParam * 8).toInt()).coerceIn(2, 10)
                GPUImagePosterizeFilter(levels)
            }

            StylisticEffectType.CROSSHATCH -> {
                val filter = GPUImageCrosshatchFilter()
                filter.setCrossHatchSpacing(0.015f + clampedParam * 0.035f)
                filter.setLineWidth(0.003f + clampedParam * 0.005f)
                filter
            }

            StylisticEffectType.SOLARIZE -> {
                val filter = GPUImageSolarizeFilter()
                filter.setThreshold(0.2f + clampedParam * 0.6f)
                filter
            }

            StylisticEffectType.GAUSSIAN_BLUR -> {
                val filter = GPUImageGaussianBlurFilter(clampedIntensity * 3.5f)
                filter
            }

            StylisticEffectType.SHARPEN -> {
                val filter = GPUImageSharpenFilter()
                filter.setSharpness(clampedIntensity * 3.0f)
                filter
            }

            StylisticEffectType.SEPIA -> {
                GPUImageSepiaToneFilter(clampedIntensity)
            }

            StylisticEffectType.INVERT -> {
                GPUImageColorInvertFilter()
            }
        }
    }

    /**
     * Applies the complete [GpuFilterConfig] chain to a source bitmap asynchronously on the GPU.
     */
    suspend fun applyFilter(
        sourceBitmap: Bitmap,
        config: GpuFilterConfig,
        lutBitmap: Bitmap? = null
    ): Bitmap = withContext(Dispatchers.Default) {
        if (config.isDefault && lutBitmap == null) {
            return@withContext sourceBitmap
        }

        try {
            val gpuImage = GPUImage(context)
            gpuImage.setImage(sourceBitmap)
            val group = buildFilterGroup(config, lutBitmap)
            gpuImage.setFilter(group)
            gpuImage.bitmapWithFilterApplied
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply GPUImage filter: ${e.message}", e)
            sourceBitmap
        }
    }

    /**
     * Renders a real-time side-by-side A/B split comparison bitmap (Left: Raw Original, Right: Filtered).
     */
    suspend fun generateSplitComparison(
        sourceBitmap: Bitmap,
        config: GpuFilterConfig,
        lutBitmap: Bitmap? = null,
        splitXFraction: Float = 0.5f
    ): Bitmap = withContext(Dispatchers.Default) {
        val filtered = applyFilter(sourceBitmap, config, lutBitmap)
        val splitClamped = splitXFraction.coerceIn(0.05f, 0.95f)

        val width = sourceBitmap.width
        val height = sourceBitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val splitPx = (width * splitClamped).toInt()

        // Draw Left: Raw Source
        val rawSrcRect = Rect(0, 0, splitPx, height)
        val rawDstRect = Rect(0, 0, splitPx, height)
        canvas.drawBitmap(sourceBitmap, rawSrcRect, rawDstRect, paint)

        // Draw Right: Filtered
        val filtSrcRect = Rect(splitPx, 0, width, height)
        val filtDstRect = Rect(splitPx, 0, width, height)
        canvas.drawBitmap(filtered, filtSrcRect, filtDstRect, paint)

        // Draw Divider Line
        val linePaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(splitPx.toFloat(), 0f, splitPx.toFloat(), height.toFloat(), linePaint)

        output
    }
}
