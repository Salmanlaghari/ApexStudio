package com.apexstudio.app.data.photoedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.apexstudio.app.data.filter.FilterColorMatrix
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.filter.LutBitmapCache
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.filter.LutTexture
import com.apexstudio.app.domain.model.PhotoCropRect
import com.apexstudio.app.domain.model.PhotoEditSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * CPU photo-editing pipeline shared by the live preview and the export.
 *
 * Render order (identical in both so the preview is WYSIWYG):
 *   decode (+EXIF orientation) → rotate/flip → crop → sharpen →
 *   colour adjustments (ColorMatrix) → LUT filter (real .cube data).
 *
 * The preview calls [renderEdited] with a small [maxDim] (fast,
 * re-run on every slider change); the export calls it with a large
 * [maxDim] once and encodes the result to video.
 */
object PhotoEditRenderer {

    private const val TAG = "PhotoEditRenderer"

    // ------------------------------------------------------------------
    // Pure crop-rect math (no Android dependencies — unit tested).
    // ------------------------------------------------------------------

    enum class CropCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /**
     * Clamp a crop rect into the unit square and enforce a minimum
     * size so the rect can never invert or vanish.
     */
    fun clampRect(rect: PhotoCropRect, minSize: Float = 0.05f): PhotoCropRect {
        var l = rect.left.coerceIn(0f, 1f)
        var t = rect.top.coerceIn(0f, 1f)
        var r = rect.right.coerceIn(0f, 1f)
        var b = rect.bottom.coerceIn(0f, 1f)
        if (r - l < minSize) {
            val c = ((l + r) / 2f).coerceIn(minSize / 2f, 1f - minSize / 2f)
            l = c - minSize / 2f
            r = c + minSize / 2f
        }
        if (b - t < minSize) {
            val c = ((t + b) / 2f).coerceIn(minSize / 2f, 1f - minSize / 2f)
            t = c - minSize / 2f
            b = c + minSize / 2f
        }
        return PhotoCropRect(l, t, r, b)
    }

    /**
     * Move one corner of [rect] to the normalised point ([nx], [ny]).
     * When [lockedAspect] (width/height) is non-null the rect is
     * re-fitted to that aspect anchored at the opposite corner, so
     * aspect presets stay exact while dragging.
     */
    fun moveCorner(
        rect: PhotoCropRect,
        corner: CropCorner,
        nx: Float,
        ny: Float,
        lockedAspect: Float? = null
    ): PhotoCropRect {
        // Opposite (anchored) corner in normalised space.
        val ox = if (corner == CropCorner.TOP_LEFT || corner == CropCorner.BOTTOM_LEFT) rect.right else rect.left
        val oy = if (corner == CropCorner.TOP_LEFT || corner == CropCorner.TOP_RIGHT) rect.bottom else rect.top
        var dx = nx - ox
        var dy = ny - oy
        if (abs(dx) < 0.01f) dx = if (dx < 0f) -0.01f else 0.01f
        if (abs(dy) < 0.01f) dy = if (dy < 0f) -0.01f else 0.01f

        if (lockedAspect != null && lockedAspect > 0f) {
            // Fit the dragged box to the aspect, keeping the opposite
            // corner fixed and the drag direction.
            var w = abs(dx)
            var h = abs(dy)
            if (w / h > lockedAspect) w = h * lockedAspect else h = w / lockedAspect
            val sx = if (dx < 0f) -1f else 1f
            val sy = if (dy < 0f) -1f else 1f
            val l = min(ox, ox + sx * w)
            val r = max(ox, ox + sx * w)
            val t = min(oy, oy + sy * h)
            val b = max(oy, oy + sy * h)
            return clampRect(PhotoCropRect(l, t, r, b))
        }

        val l = min(ox, nx)
        val r = max(ox, nx)
        val t = min(oy, ny)
        val b = max(oy, ny)
        return clampRect(PhotoCropRect(l, t, r, b))
    }

    // ------------------------------------------------------------------
    // Bitmap loading.
    // ------------------------------------------------------------------

    /**
     * Decode the image at [uriString] (content://, file:// or raw path),
     * downsampling so the longest side is at most [maxDim] px, with the
     * EXIF orientation applied. Returns a mutable ARGB_8888 software
     * bitmap, or null when the image cannot be decoded.
     */
    suspend fun loadBitmap(
        context: Context,
        uriString: String,
        maxDim: Int = 1280
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            // 1. Bounds pass for downsampling.
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
            val srcW = boundsOpts.outWidth
            val srcH = boundsOpts.outHeight
            if (srcW <= 0 || srcH <= 0) return@withContext null
            var sample = 1
            val longest = max(srcW, srcH)
            while (longest / (sample * 2) >= maxDim) sample *= 2
            // 2. Real decode.
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = openStream(context, uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@withContext null
            // 3. EXIF orientation.
            val orientation = readExifOrientation(context, uri)
            if (orientation != 0) {
                val m = Matrix().apply { postRotate(orientation.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
                if (rotated != bitmap) bitmap.recycle()
                bitmap = rotated
            }
            // 4. Final downscale if still over maxDim (sample is power-of-2).
            val lw = max(bitmap.width, bitmap.height)
            if (lw > maxDim) {
                val scale = maxDim.toFloat() / lw
                val sw = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val sh = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
                if (scaled != bitmap) bitmap.recycle()
                bitmap = scaled
            }
            // Software bitmap: the LUT / sharpen passes touch pixels.
            bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: bitmap
        } catch (e: Exception) {
            Log.w(TAG, "loadBitmap failed for $uriString", e)
            null
        }
    }

    private fun openStream(context: Context, uri: Uri): java.io.InputStream? {
        return try {
            when (uri.scheme) {
                "content" -> context.contentResolver.openInputStream(uri)
                "file" -> File(uri.path ?: return null).inputStream()
                null, "" -> File(uri.toString()).inputStream()
                else -> context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "openStream failed for $uri", e)
            null
        }
    }

    private fun readExifOrientation(context: Context, uri: Uri): Int {
        return try {
            val exif = openStream(context, uri)?.use { ExifInterface(it) } ?: return 0
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    // ------------------------------------------------------------------
    // Edit pipeline.
    // ------------------------------------------------------------------

    /**
     * Full pipeline: load [uriString], apply [settings], return the
     * edited bitmap. [lutLoader] lets callers inject the LUT texture
     * (the preview pre-caches it; the export loads on demand).
     */
    suspend fun renderEdited(
        context: Context,
        uriString: String,
        settings: PhotoEditSettings,
        maxDim: Int = 1280,
        lutLoader: (suspend (FilterPreset) -> LutTexture?)? = null
    ): Bitmap? = withContext(Dispatchers.Default) {
        val src = loadBitmap(context, uriString, maxDim) ?: return@withContext null
        try {
            var lutTexture: LutTexture? = null
            val filterId = settings.filterId
            if (filterId != null) {
                val preset = LutFilterEngine(context).manifest.presetById(filterId)
                if (preset != null) {
                    lutTexture = lutLoader?.invoke(preset)
                        ?: LutBitmapCache.getOrLoad(context, preset)
                }
            }
            applyEdits(src, settings, lutTexture)
        } catch (e: Exception) {
            Log.w(TAG, "renderEdited failed", e)
            src
        }
    }

    /**
     * Apply [settings] to [src] (not modified; a new bitmap is
     * returned). Pure CPU — call off the main thread. Intermediate
     * bitmaps are recycled; the caller owns the returned bitmap.
     */
    fun applyEdits(
        src: Bitmap,
        settings: PhotoEditSettings,
        lutTexture: LutTexture?
    ): Bitmap {
        var cur = src
        // Advance the pipeline, recycling the previous stage's bitmap
        // (never the caller's [src]).
        fun advance(next: Bitmap) {
            if (next !== cur) {
                if (cur !== src) {
                    try {
                        cur.recycle()
                    } catch (_: Exception) {
                    }
                }
                cur = next
            }
        }
        // 1. Rotate / flip (crop coords are defined in this space).
        advance(applyRotateFlip(cur, settings.normalizedRotationSteps, settings.flipHorizontal, settings.flipVertical))
        // 2. Crop.
        val crop = settings.crop
        if (crop != null && !crop.isFullFrame()) {
            advance(applyCrop(cur, crop))
        }
        // 3. Sharpness (unsharp mask).
        val sharp = settings.adjustments.sharpness.coerceIn(0f, 1f)
        if (sharp > 0f) {
            advance(applySharpness(cur, sharp))
        }
        // 4. Colour adjustments via the same ColorMatrix math the video
        //    Adjust tools use, so photo + video adjustments match.
        //    (Sharpness is a convolution, not a ColorMatrix op — it was
        //    already applied in step 3.)
        if (!settings.adjustments.isDefault) {
            advance(applyAdjustments(cur, settings))
        }
        // 5. LUT filter — the real .cube data, same as export.
        if (lutTexture != null && settings.filterId != null && settings.filterIntensity > 0f) {
            advance(LutBitmapCache.applyToBitmap(cur, lutTexture, settings.filterIntensity))
        }
        return cur
    }

    private fun applyRotateFlip(src: Bitmap, steps: Int, flipH: Boolean, flipV: Boolean): Bitmap {
        if (steps == 0 && !flipH && !flipV) return src
        val w = src.width
        val h = src.height
        val m = Matrix()
        m.postTranslate(-w / 2f, -h / 2f)
        if (flipH) m.postScale(-1f, 1f)
        if (flipV) m.postScale(1f, -1f)
        if (steps != 0) m.postRotate(steps * 90f)
        val swap = steps % 2 != 0
        val nw = if (swap) h else w
        val nh = if (swap) w else h
        m.postTranslate(nw / 2f, nh / 2f)
        val out = Bitmap.createBitmap(nw, nh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, m, paint)
        return out
    }

    private fun applyCrop(src: Bitmap, crop: PhotoCropRect): Bitmap {
        val w = src.width
        val h = src.height
        val l = (crop.left.coerceIn(0f, 1f) * w).toInt().coerceIn(0, w - 1)
        val t = (crop.top.coerceIn(0f, 1f) * h).toInt().coerceIn(0, h - 1)
        val r = (crop.right.coerceIn(0f, 1f) * w).toInt().coerceIn(l + 1, w)
        val b = (crop.bottom.coerceIn(0f, 1f) * h).toInt().coerceIn(t + 1, h)
        return try {
            Bitmap.createBitmap(src, l, t, r - l, b - t)
        } catch (e: Exception) {
            Log.w(TAG, "applyCrop failed, keeping full frame", e)
            src
        }
    }

    private fun applyAdjustments(src: Bitmap, settings: PhotoEditSettings): Bitmap {
        val matrixValues = FilterColorMatrix.getCombinedMatrix(
            filterId = null,
            intensity = 0f,
            adjustments = settings.adjustments
        )
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrixValues))
        }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /**
     * Unsharp mask: out = src + strength * (src − boxBlur(src)).
     * 3x3 box blur; strength scales with the 0..1 slider.
     */
    fun applySharpness(src: Bitmap, amount: Float): Bitmap {
        val amt = amount.coerceIn(0f, 1f)
        if (amt <= 0f) return src
        val w = src.width
        val h = src.height
        if (w < 3 || h < 3) return src
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val blurred = IntArray(w * h)
        // 3x3 box blur (edges replicate).
        for (y in 0 until h) {
            for (x in 0 until w) {
                var rs = 0; var gs = 0; var bs = 0
                for (ky in -1..1) {
                    val yy = (y + ky).coerceIn(0, h - 1)
                    for (kx in -1..1) {
                        val xx = (x + kx).coerceIn(0, w - 1)
                        val c = px[yy * w + xx]
                        rs += (c ushr 16) and 0xff
                        gs += (c ushr 8) and 0xff
                        bs += c and 0xff
                    }
                }
                blurred[y * w + x] = (0xff shl 24) or ((rs / 9) shl 16) or ((gs / 9) shl 8) or (bs / 9)
            }
        }
        val strength = amt * 1.5f
        val out = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val b = blurred[i]
            val a = (c ushr 24) and 0xff
            val r = sharpenChannel((c ushr 16) and 0xff, (b ushr 16) and 0xff, strength)
            val g = sharpenChannel((c ushr 8) and 0xff, (b ushr 8) and 0xff, strength)
            val bl = sharpenChannel(c and 0xff, b and 0xff, strength)
            out[i] = (a shl 24) or (r shl 16) or (g shl 8) or bl
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    private fun sharpenChannel(orig: Int, blur: Int, strength: Float): Int =
        (orig + (orig - blur) * strength).toInt().coerceIn(0, 255)
}
