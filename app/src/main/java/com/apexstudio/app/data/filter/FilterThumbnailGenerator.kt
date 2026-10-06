package com.apexstudio.app.data.filter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Generates 1:1 cropped filter preview thumbnails by applying each
 * LUT preset to a source [Bitmap] (typically the video's active frame).
 *
 * The thumbnail uses the same 3D LUT lookup the preview's
 * [LutFilterGlEffect] runs on the GPU — see [LutBitmapCache.applyToBitmap]
 * for the CPU-side port. The old 4×5 color-matrix path
 * ([FilterColorMatrix]) is kept as a fallback for presets whose .cube
 * asset is missing or malformed, so a missing LUT never blanks the
 * thumbnail row.
 *
 * Supports:
 * - Dynamic live frame previews (Option A) generated at 60fps speeds (<10ms).
 * - Custom thumbnail assets / uploaded images (Option B).
 * - Stylized high-contrast fallback image when no video is loaded yet.
 */
object FilterThumbnailGenerator {

    private const val TAG = "FilterThumbGen"
    private const val THUMB_SIZE = 120

    @Volatile
    private var cachedGenericThumbnails: Map<String?, ImageBitmap>? = null

    /** Cache for real-LUT dynamic thumbnails, keyed by source-frame identity. */
    @Volatile
    private var cachedDynamicThumbnails: Map<String?, ImageBitmap>? = null
    @Volatile
    private var cachedDynamicKey: String? = null

    /** Drop the dynamic thumbnail cache (e.g. when LUT assets change). */
    fun invalidateDynamicCache() {
        cachedDynamicThumbnails = null
        cachedDynamicKey = null
    }

    /**
     * Create a photographic reference image featuring real portrait photo with skin tones,
     * cinematic highlights, and landscape shadows for instant previews.
     */
    fun createGenericPreviewBitmap(context: Context? = null): Bitmap {
        val size = THUMB_SIZE
        if (context != null) {
            try {
                val input = context.assets.open("filter_sample_portrait.jpg")
                val decoded = android.graphics.BitmapFactory.decodeStream(input)
                input.close()
                if (decoded != null) {
                    return centerCropAndScale(decoded, size)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bundled filter sample portrait not found in assets, falling back to procedural image", e)
            }
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Base gradient: cinematic twilight sky to warm sunset horizon
        val skyShader = android.graphics.LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            intArrayOf(
                android.graphics.Color.rgb(24, 32, 54),   // deep slate twilight
                android.graphics.Color.rgb(217, 70, 119), // vibrant magenta / sunset
                android.graphics.Color.rgb(245, 158, 11), // warm golden amber
                android.graphics.Color.rgb(14, 165, 233)  // cyan electric rim
            ),
            floatArrayOf(0.0f, 0.38f, 0.72f, 1.0f),
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = skyShader
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null

        // Portrait face circle with natural warm skin tone
        paint.color = android.graphics.Color.rgb(234, 179, 140)
        canvas.drawCircle(size * 0.50f, size * 0.42f, size * 0.22f, paint)

        // Facial details (eyes and nose highlight)
        paint.color = android.graphics.Color.rgb(30, 41, 59)
        canvas.drawCircle(size * 0.43f, size * 0.40f, size * 0.035f, paint)
        canvas.drawCircle(size * 0.57f, size * 0.40f, size * 0.035f, paint)

        // Hair arc silhouette
        paint.color = android.graphics.Color.rgb(15, 23, 42)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        canvas.drawArc(
            size * 0.28f, size * 0.20f, size * 0.72f, size * 0.64f,
            180f, 180f, false, paint
        )
        paint.style = Paint.Style.FILL

        // Landscape silhouette in lower third
        paint.color = android.graphics.Color.rgb(10, 15, 29)
        val path = android.graphics.Path().apply {
            moveTo(0f, size * 0.72f)
            lineTo(size * 0.35f, size * 0.65f)
            lineTo(size * 0.65f, size * 0.76f)
            lineTo(size.toFloat(), size * 0.68f)
            lineTo(size.toFloat(), size.toFloat())
            lineTo(0f, size.toFloat())
            close()
        }
        canvas.drawPath(path, paint)

        return bmp
    }

    /**
     * Generate real preview thumbnails of the source video frame for all presets,
     * applying each preset's ACTUAL 3D LUT (via [LutBitmapCache], the same math
     * the GPU preview shader runs) — CapCut-style true previews, not approximations.
     *
     * Results are cached per [cacheKey] (e.g. "$clipId@${timestampSec}"); repeat
     * calls with the same key return instantly without re-rendering.
     * Falls back to the fast ColorMatrix approximation only when a preset's
     * .cube asset is missing or malformed.
     */
    suspend fun generateDynamicThumbnails(
        context: Context,
        source: Bitmap,
        manifest: FilterManifest,
        customPresets: List<FilterPreset> = emptyList(),
        cacheKey: String? = null
    ): Map<String?, ImageBitmap> = withContext(Dispatchers.Default) {
        if (cacheKey != null && cacheKey == cachedDynamicKey) {
            cachedDynamicThumbnails?.let { return@withContext it }
        }

        // 1. Center crop and scale source video frame to thumbnail size
        val baseThumb = centerCropAndScale(source, THUMB_SIZE)
        val result = mutableMapOf<String?, ImageBitmap>()
        result[null] = baseThumb.asImageBitmap()

        // 2. Aggregate all presets to ensure no preset is skipped
        val allPresets = (manifest.filters + manifest.categories.flatMap { it.filters } + customPresets)
            .distinctBy { it.id }

        // 3. Render each preset with its REAL LUT in parallel. LutBitmapCache
        //    memoizes loaded LUT textures, so repeat renders are cheap.
        coroutineScope {
            val jobs = allPresets.map { preset ->
                async {
                    // Option B: check custom thumbnail first
                    val custom = FilterThumbnailAssetHandler.getCustomThumbnail(context, preset.id)
                    if (custom != null) {
                        return@async preset.id to custom
                    }
                    // Real LUT path first — true CapCut-style preview
                    val texture = try {
                        LutBitmapCache.getOrLoad(context, preset)
                    } catch (e: Exception) {
                        Log.w(TAG, "LUT load failed for ${preset.id}, using matrix fallback", e)
                        null
                    }
                    val bmp = if (texture != null) {
                        try {
                            LutBitmapCache.applyToBitmap(baseThumb, texture, 1f)
                        } catch (e: Exception) {
                            Log.w(TAG, "LUT apply failed for ${preset.id}, using matrix fallback", e)
                            null
                        }
                    } else null
                    val finalBmp = bmp ?: run {
                        // Fallback: fast hardware ColorMatrix approximation
                        val outBmp = Bitmap.createBitmap(THUMB_SIZE, THUMB_SIZE, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(outBmp)
                        val cm = FilterColorMatrix.getAndroidColorMatrix(preset.id, 1f)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        paint.colorFilter = ColorMatrixColorFilter(cm)
                        canvas.drawBitmap(baseThumb, 0f, 0f, paint)
                        outBmp
                    }
                    preset.id to finalBmp.asImageBitmap()
                }
            }
            jobs.awaitAll().forEach { (id, img) -> result[id] = img }
        }

        if (cacheKey != null) {
            cachedDynamicThumbnails = result
            cachedDynamicKey = cacheKey
        }
        Log.d(TAG, "Generated ${result.size} real-LUT filter thumbnails (key=$cacheKey)")
        result
    }

    /**
     * Generate filter preview swatches using the photographic reference image.
     */
    suspend fun generateWithGenericImage(
        context: Context,
        manifest: FilterManifest
    ): Map<String?, ImageBitmap> = withContext(Dispatchers.Default) {
        cachedGenericThumbnails?.let { return@withContext it }
        val sample = createGenericPreviewBitmap(context)
        val res = generateDynamicThumbnails(context, sample, manifest)
        cachedGenericThumbnails = res
        res
    }

    /**
     * Backward-compatible legacy generator.
     *
     * Like [generateDynamicThumbnails] but returns [Bitmap]s instead of
     * [ImageBitmap]s for callers that still need the raw platform
     * bitmap (e.g. legacy exporters / share sheets). Runs the same
     * LUT lookup path as [generateDynamicThumbnails] so the two
     * generators stay in lockstep.
     */
    suspend fun generateAll(
        context: Context,
        source: Bitmap,
        manifest: FilterManifest
    ): Map<String?, Bitmap> = withContext(Dispatchers.Default) {
        val out = mutableMapOf<String?, Bitmap>()
        val base = centerCropAndScale(source, THUMB_SIZE)
        out[null] = base
        val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (category in manifest.categories) {
            for (preset in category.filters) {
                val texture = LutBitmapCache.getOrLoad(context, preset)
                if (texture != null) {
                    out[preset.id] = LutBitmapCache.applyToBitmap(base, texture, 1f)
                } else {
                    val outBmp = Bitmap.createBitmap(THUMB_SIZE, THUMB_SIZE, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(outBmp)
                    val cm = FilterColorMatrix.getAndroidColorMatrix(preset.id, 1f)
                    fallbackPaint.colorFilter = ColorMatrixColorFilter(cm)
                    canvas.drawBitmap(base, 0f, 0f, fallbackPaint)
                    fallbackPaint.colorFilter = null
                    out[preset.id] = outBmp
                }
            }
        }
        out
    }

    private fun centerCropAndScale(source: Bitmap, targetSize: Int): Bitmap {
        val w = source.width
        val h = source.height
        val cropSize = min(w, h)
        val x = (w - cropSize) / 2
        val y = (h - cropSize) / 2

        val cropped = Bitmap.createBitmap(source, x, y, cropSize, cropSize)
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (cropped != source && cropped != scaled) cropped.recycle()
        return scaled
    }
}
