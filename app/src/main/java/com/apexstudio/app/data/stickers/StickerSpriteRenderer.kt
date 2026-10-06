package com.apexstudio.app.data.stickers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.apexstudio.app.domain.model.StickerOverlay

/**
 * Rasterises a PNG [StickerOverlay] onto a transparent full-frame bitmap.
 *
 * Used by the export GL effect; the editor preview's Canvas draws with the
 * same [dstRect] / [srcRect] geometry, so the on-screen sticker and the
 * baked-in sticker are pixel-consistent regardless of output resolution.
 *
 * Geometry: the sticker is a square whose side is
 * [BASE_STICKER_FRACTION] × frame height × [StickerOverlay.sizeScale],
 * centred at ([StickerOverlay.x], [StickerOverlay.y]) as fractions of
 * the frame. Crop selects a sub-rect of the source bitmap; the OVAL
 * cutout masks the sticker to an inscribed ellipse.
 */
object StickerSpriteRenderer {

    /** Sticker box side as a fraction of the frame height at sizeScale = 1. */
    const val BASE_STICKER_FRACTION = 0.30f

    /**
     * Destination square (in canvas px) for the sticker box.
     * Pure geometry — shared by the preview Canvas and the export path.
     */
    fun dstRect(sticker: StickerOverlay, width: Int, height: Int): RectF {
        val side = height * BASE_STICKER_FRACTION * sticker.sizeScale.coerceIn(0.05f, 8f)
        val cx = sticker.x.coerceIn(0f, 1f) * width
        val cy = sticker.y.coerceIn(0f, 1f) * height
        return RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
    }

    /** Source sub-rect of [bitmap] selected by the sticker's crop fractions. */
    fun srcRect(bitmap: Bitmap, sticker: StickerOverlay): Rect {
        val c = sticker.sanitizedCrop()
        return Rect(
            (c[0] * bitmap.width).toInt(),
            (c[1] * bitmap.height).toInt(),
            (c[2] * bitmap.width).toInt().coerceAtLeast(1),
            (c[3] * bitmap.height).toInt().coerceAtLeast(1)
        )
    }

    /**
     * Draw [bitmap] (the sticker PNG) for [sticker] into a new transparent
     * [width]×[height] bitmap. Returns an empty (fully transparent) bitmap
     * when [bitmap] is null.
     */
    fun render(bitmap: Bitmap?, sticker: StickerOverlay, width: Int, height: Int): Bitmap {
        val out = Bitmap.createBitmap(
            width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888
        )
        if (bitmap == null) return out
        val canvas = Canvas(out)
        draw(canvas, bitmap, sticker, width, height)
        return out
    }

    /**
     * Draw directly onto [canvas] (frame size [width]×[height]): crop →
     * cutout mask → rotation → opacity. The preview Canvas path uses this
     * so WYSIWYG holds against the export bitmap path.
     */
    fun draw(canvas: Canvas, bitmap: Bitmap, sticker: StickerOverlay, width: Int, height: Int) {
        val dst = dstRect(sticker, width, height)
        val src = srcRect(bitmap, sticker)
        val cx = dst.centerX()
        val cy = dst.centerY()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (sticker.opacity.coerceIn(0f, 1f) * 255).toInt()
        }

        val saveCount = canvas.save()
        try {
            if (sticker.rotationDeg != 0f) {
                canvas.rotate(sticker.rotationDeg, cx, cy)
            }
            if (sticker.cutoutShape.equals("OVAL", ignoreCase = true)) {
                val oval = Path().apply { addOval(dst, Path.Direction.CW) }
                canvas.clipPath(oval)
            }
            canvas.drawBitmap(bitmap, src, dst, paint)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }
}
