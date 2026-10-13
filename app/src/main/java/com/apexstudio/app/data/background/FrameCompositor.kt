package com.apexstudio.app.data.background

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.apexstudio.app.domain.model.BackgroundReplaceMode
import com.apexstudio.app.domain.model.BackgroundReplaceSettings

/**
 * Composites one video frame with a person-confidence mask (PR-G).
 *
 * Pipeline per frame:
 *   1. mask (confidence 0..1) → smoothstep feather → ALPHA_8 mask,
 *      bilinear-upscaled to the frame size (soft edges, no jagged halo).
 *   2. background: blurred copy of the frame (BLUR), a solid colour
 *      (COLOR), or a cover-cropped image (IMAGE).
 *   3. foreground = frame masked with DST_IN (person keeps its pixels,
 *      background pixels become transparent), drawn over the background.
 *
 * All working bitmaps are allocated once in the constructor and reused
 * for every frame of a processing run — a per-frame allocation here
 * would churn ~18 MB/frame at 1080p through the GC. Call [release]
 * when the run ends.
 *
 * This class is deliberately dumb about where frames come from: the
 * engine feeds it decoded frames + MediaPipe masks, and reads
 * [result] back for encoding. The same composite math serves the
 * (single) processing path, so there is no second implementation
 * that could drift from it.
 */
class FrameCompositor(
    private val width: Int,
    private val height: Int,
    settings: BackgroundReplaceSettings,
    backgroundImage: Bitmap? = null
) {
    private val sanitized = settings.sanitized()

    /** Final composited frame — owned by this compositor, do not recycle. */
    val result: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val resultCanvas = Canvas(result)

    private val scratch: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val scratchCanvas = Canvas(scratch)

    private val filterPaint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
    }
    private val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val fullRect = Rect(0, 0, width, height)

    // Mask path: small ALPHA_8 mask (model size) upscaled to the frame.
    private var maskBitmap: Bitmap? = null
    private var maskW = 0
    private var maskH = 0
    private val scaledMask: Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
    private val scaledMaskCanvas = Canvas(scaledMask)

    // BLUR path: downscale → upscale (bilinear) = fast blur.
    private val blurSmall: Bitmap?
    private val blurSmallCanvas: Canvas?
    private val blurSmallRect: Rect?

    // COLOR path: pre-filled solid bitmap.
    private val solidBg: Bitmap?

    // IMAGE path: precomputed cover-cropped background at frame size.
    private val imageBg: Bitmap?

    init {
        when (sanitized.mode) {
            BackgroundReplaceMode.BLUR -> {
                val div = BackgroundReplaceMath.blurDivisorFor(sanitized.blurStrength)
                val sw = BackgroundReplaceMath.blurredSize(width, div)
                val sh = BackgroundReplaceMath.blurredSize(height, div)
                blurSmall = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
                blurSmallCanvas = Canvas(blurSmall)
                blurSmallRect = Rect(0, 0, sw, sh)
                solidBg = null
                imageBg = null
            }
            BackgroundReplaceMode.COLOR -> {
                blurSmall = null
                blurSmallCanvas = null
                blurSmallRect = null
                solidBg = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    .also { it.eraseColor(sanitized.colorArgb.toInt()) }
                imageBg = null
            }
            BackgroundReplaceMode.IMAGE -> {
                blurSmall = null
                blurSmallCanvas = null
                blurSmallRect = null
                solidBg = null
                imageBg = backgroundImage?.let { coverCropToFrame(it) }
            }
        }
    }

    /** Cover-crop [src] to exactly [width]×[height] (no letterboxing). */
    private fun coverCropToFrame(src: Bitmap): Bitmap {
        val crop = BackgroundReplaceMath.coverCropRect(src.width, src.height, width, height)
        // drawBitmap's src rect is an int Rect (there is no
        // (Bitmap, RectF, RectF, Paint) overload); sub-pixel crop
        // precision is irrelevant at frame scale.
        val srcRect = Rect(
            crop[0].toInt(), crop[1].toInt(), crop[2].toInt(), crop[3].toInt()
        )
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, srcRect, RectF(0f, 0f, width.toFloat(), height.toFloat()), filterPaint)
        return out
    }

    // Idempotent-release guard (Kilo review): recycle() on an
    // already-recycled bitmap throws; a second release() must be
    // a no-op instead of relying on try-catch.
    private var released = false

    /**
     * Composite [frame] with [mask] (row-major confidence 0..1,
     * [maskW]×[maskH]) into [result]. [frame] must be [width]×[height].
     *
     * @throws IllegalArgumentException when the mask size mismatches
     *   its length, or the frame size mismatches the compositor.
     */
    fun composite(frame: Bitmap, mask: FloatArray, maskW: Int, maskH: Int) {
        check(!released) { "FrameCompositor is released" }
        require(frame.width == width && frame.height == height) {
            "Frame ${frame.width}×${frame.height} != compositor ${width}×${height}"
        }
        require(mask.size == maskW * maskH) {
            "Mask length ${mask.size} != ${maskW}×${maskH}"
        }
        if (maskBitmap == null || this.maskW != maskW || this.maskH != maskH) {
            maskBitmap?.recycle()
            maskBitmap = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ALPHA_8)
            this.maskW = maskW
            this.maskH = maskH
        }
        // Confidence → feathered alpha bytes.
        val alpha = ByteArray(mask.size)
        for (i in mask.indices) {
            alpha[i] = (BackgroundReplaceMath.smoothstepMask(mask[i]) * 255f).toInt()
                .coerceIn(0, 255).toByte()
        }
        maskBitmap!!.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(alpha))

        // Upscale the mask to frame size (bilinear = soft edge).
        // Must erase first: drawing over the previous frame's mask
        // with SRC_OVER would accumulate alpha.
        scaledMask.eraseColor(Color.TRANSPARENT)
        scaledMaskCanvas.drawBitmap(maskBitmap!!, null, fullRect, filterPaint)

        // Foreground: frame masked to the person.
        scratchCanvas.drawBitmap(frame, 0f, 0f, null)
        scratchCanvas.drawBitmap(scaledMask, 0f, 0f, maskPaint)

        // Background + foreground.
        when (sanitized.mode) {
            BackgroundReplaceMode.BLUR -> {
                blurSmallCanvas!!.drawBitmap(frame, null, blurSmallRect!!, filterPaint)
                resultCanvas.drawBitmap(blurSmall!!, null, fullRect, filterPaint)
            }
            BackgroundReplaceMode.COLOR -> {
                resultCanvas.drawBitmap(solidBg!!, 0f, 0f, null)
            }
            BackgroundReplaceMode.IMAGE -> {
                if (imageBg != null) {
                    resultCanvas.drawBitmap(imageBg, 0f, 0f, null)
                } else {
                    // Image failed to load after init (defensive):
                    // fall back to black rather than a garbage frame.
                    resultCanvas.drawColor(Color.BLACK)
                }
            }
        }
        resultCanvas.drawBitmap(scratch, 0f, 0f, null)
    }

    /** Recycle all working bitmaps. Idempotent. The compositor must not be used after this. */
    fun release() {
        if (released) return
        released = true
        maskBitmap?.recycle()
        maskBitmap = null
        scaledMask.recycle()
        scratch.recycle()
        result.recycle()
        blurSmall?.recycle()
        solidBg?.recycle()
        imageBg?.recycle()
    }
}
