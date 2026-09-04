package com.apexstudio.app.data.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.apexstudio.app.domain.model.TextOverlay

/**
 * Rasterises [TextOverlay]s onto a transparent full-frame bitmap.
 *
 * The same renderer is used in both places a caption has to appear:
 *
 *  - **Editor preview** — the composable renders the bitmap into the
 *    video *content* rect, so the user sees the caption live while
 *    they drag / type.
 *  - **Export** — [com.apexstudio.app.data.effect.TextOverlayGlEffect]
 *    uploads the bitmap as a second GL texture and alpha-composites it
 *    over every exported frame.
 *
 * Every measurement is normalised to the target [width] x [height]
 * canvas, which keeps the on-screen caption and the baked caption
 * pixel-for-pixel consistent regardless of preview vs. output
 * resolution.
 */
object TextSpriteRenderer {

    /** Base caption height as a fraction of the frame height. */
    const val BASE_FONT_FRACTION = 0.07f
    private const val MAX_TEXT_WIDTH_FRACTION = 0.9f
    private const val PILL_PAD_FRACTION_X = 0.012f
    private const val PILL_PAD_FRACTION_Y = 0.008f

    /**
     * Draw [overlays] (already filtered to those visible at the
     * current playhead) into a new transparent [Bitmap].
     *
     * @param highlightId when non-null, the matching overlay gets a
     *   dashed selection outline — used only by the editor preview so
     *   the user can see which caption they are dragging.
     */
    fun render(
        overlays: List<TextOverlay>,
        width: Int,
        height: Int,
        highlightId: String? = null
    ): Bitmap {
        if (width <= 1 || height <= 1 || overlays.isEmpty()) {
            return Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        for (overlay in overlays) {
            drawOverlay(canvas, overlay, width, height, isHighlighted = overlay.id == highlightId)
        }
        return bitmap
    }

    private fun drawOverlay(
        canvas: Canvas,
        overlay: TextOverlay,
        width: Int,
        height: Int,
        isHighlighted: Boolean
    ) {
        if (overlay.text.isBlank()) return
        val centerX = overlay.x.coerceIn(0f, 1f) * width
        val centerY = overlay.y.coerceIn(0f, 1f) * height

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            color = overlay.colorArgb.toInt()
            textSize = height * BASE_FONT_FRACTION * overlay.sizeScale.coerceIn(0.3f, 4f)
        }
        // Auto-fit long captions: start at the requested size and
        // shrink until the string fits the safe width.
        val maxW = width * MAX_TEXT_WIDTH_FRACTION
        if (paint.measureText(overlay.text) > maxW) {
            paint.textSize *= maxW / paint.measureText(overlay.text)
        }

        // Vertical centering: align the text block's middle (between
        // ascent and descent) to the requested centre point.
        val metrics = paint.fontMetrics
        val textTop = centerY - (metrics.descent - metrics.ascent) / 2f - metrics.ascent
        val textWidth = paint.measureText(overlay.text)

        // Optional rounded pill behind the text.
        overlay.bgArgb?.let { bg ->
            val pillLeft = centerX - textWidth / 2f - width * PILL_PAD_FRACTION_X
            val pillRight = centerX + textWidth / 2f + width * PILL_PAD_FRACTION_X
            val pillTop = textTop + metrics.ascent - height * PILL_PAD_FRACTION_Y
            val pillBottom = textTop + metrics.descent + height * PILL_PAD_FRACTION_Y
            val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bg.toInt()
            }
            canvas.drawRoundRect(
                RectF(pillLeft, pillTop, pillRight, pillBottom),
                height * 0.012f,
                height * 0.012f,
                pill
            )
        }

        canvas.drawText(overlay.text, centerX, textTop, paint)

        if (isHighlighted) {
            val border = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = (height * 0.004f).coerceAtLeast(2f)
                color = 0xFF00E5FF.toInt()
            }
            val l = centerX - textWidth / 2f - width * PILL_PAD_FRACTION_X
            val r = centerX + textWidth / 2f + width * PILL_PAD_FRACTION_X
            val t = textTop + metrics.ascent - height * PILL_PAD_FRACTION_Y
            val b = textTop + metrics.descent + height * PILL_PAD_FRACTION_Y
            canvas.drawRoundRect(RectF(l, t, r, b), height * 0.012f, height * 0.012f, border)
        }
    }
}
