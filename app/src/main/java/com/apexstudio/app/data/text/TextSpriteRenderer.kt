package com.apexstudio.app.data.text

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
 *    over every exported frame, re-rasterising whenever
 *    [TextAnimEngine.stateKey] changes so intro/outro/loop animations
 *    bake into the MP4 exactly as previewed.
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
     * @param states per-overlay animation state from
     *   [TextAnimEngine.compute]; overlays without an entry render
     *   statically.
     */
    fun render(
        overlays: List<TextOverlay>,
        width: Int,
        height: Int,
        highlightId: String? = null,
        states: Map<String, TextAnimEngine.State> = emptyMap()
    ): Bitmap {
        if (width <= 1 || height <= 1 || overlays.isEmpty()) {
            return Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        for (overlay in overlays) {
            drawOverlay(
                canvas, overlay, width, height,
                state = states[overlay.id] ?: TextAnimEngine.State.Identity,
                isHighlighted = overlay.id == highlightId
            )
        }
        return bitmap
    }

    private fun withAlpha(argb: Long, alpha: Float): Int {
        val origA = ((argb shr 24) and 0xFF).toInt()
        val newA = (origA * alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return ((newA shl 24) or (argb and 0xFFFFFFL).toInt())
    }

    private fun drawOverlay(
        canvas: Canvas,
        overlay: TextOverlay,
        width: Int,
        height: Int,
        state: TextAnimEngine.State,
        isHighlighted: Boolean
    ) {
        val fullText = overlay.text
        if (fullText.isBlank()) return
        val text = state.charsToShow?.let { fullText.take(it.coerceIn(0, fullText.length)) } ?: fullText
        if (text.isEmpty() || state.alpha <= 0.01f) return

        val centerX = overlay.x.coerceIn(0f, 1f) * width
        val centerY = overlay.y.coerceIn(0f, 1f) * height

        val typeface = TextFontRegistry.resolve(overlay.fontFamily, overlay.isBold, overlay.isItalic)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textAlign = when (overlay.textAlign.uppercase()) {
                "LEFT" -> Paint.Align.LEFT
                "RIGHT" -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            color = withAlpha(overlay.colorArgb, state.alpha)
            textSize = height * BASE_FONT_FRACTION * overlay.sizeScale.coerceIn(0.3f, 4f)
            letterSpacing = overlay.letterSpacingEm.coerceIn(-0.2f, 1f)
        }

        overlay.shadowColorArgb?.let { shadowColor ->
            paint.setShadowLayer(
                height * 0.015f,
                height * 0.004f,
                height * 0.004f,
                withAlpha(shadowColor, state.alpha)
            )
        }

        if (state.blurFrac > 0f) {
            paint.maskFilter = BlurMaskFilter(state.blurFrac * height, BlurMaskFilter.Blur.NORMAL)
        }

        // Auto-fit long captions: start at the requested size and
        // shrink until the string fits the safe width.
        val maxW = width * MAX_TEXT_WIDTH_FRACTION
        if (paint.measureText(text) > maxW) {
            paint.textSize *= maxW / paint.measureText(text)
        }

        // Vertical centering: align the text block's middle (between
        // ascent and descent) to the requested centre point.
        val metrics = paint.fontMetrics
        val textTop = centerY - (metrics.descent - metrics.ascent) / 2f - metrics.ascent
        val textWidth = paint.measureText(text)

        // Gradient fill spans the text block vertically.
        val gradStart = overlay.gradientStartArgb
        val gradEnd = overlay.gradientEndArgb
        if (gradStart != null && gradEnd != null) {
            paint.shader = LinearGradient(
                0f, textTop + metrics.ascent,
                0f, textTop + metrics.descent,
                withAlpha(gradStart, state.alpha),
                withAlpha(gradEnd, state.alpha),
                Shader.TileMode.CLAMP
            )
        }

        // Text block edges for the pill / selection rect. The (x, y)
        // anchor is the block centre for CENTER, the left edge for
        // LEFT, the right edge for RIGHT.
        val textLeft = when (paint.textAlign) {
            Paint.Align.LEFT -> centerX
            Paint.Align.RIGHT -> centerX - textWidth
            else -> centerX - textWidth / 2f
        }
        val textRight = textLeft + textWidth

        // Apply the animation transform around the overlay centre.
        val scX = state.scaleX
        val scY = state.scaleY
        val needsTransform = scX != 1f || scY != 1f ||
                state.transXFrac != 0f || state.transYFrac != 0f ||
                state.rotXDeg != 0f || state.rotYDeg != 0f || state.rotZDeg != 0f
        if (needsTransform) {
            canvas.save()
            canvas.translate(
                centerX + state.transXFrac * width,
                centerY + state.transYFrac * height
            )
            if (state.rotZDeg != 0f) canvas.rotate(state.rotZDeg)
            if (state.rotXDeg != 0f || state.rotYDeg != 0f) {
                val camera = Camera()
                camera.save()
                camera.rotateX(state.rotXDeg)
                camera.rotateY(state.rotYDeg)
                val m = Matrix()
                camera.getMatrix(m)
                camera.restore()
                canvas.concat(m)
            }
            canvas.scale(scX, scY)
            canvas.translate(-centerX, -centerY)
        }

        try {
            // Optional rounded pill behind the text.
            overlay.bgArgb?.let { bg ->
                val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(bg, state.alpha)
                }
                canvas.drawRoundRect(
                    RectF(
                        textLeft - width * PILL_PAD_FRACTION_X,
                        textTop + metrics.ascent - height * PILL_PAD_FRACTION_Y,
                        textRight + width * PILL_PAD_FRACTION_X,
                        textTop + metrics.descent + height * PILL_PAD_FRACTION_Y
                    ),
                    height * 0.012f,
                    height * 0.012f,
                    pill
                )
            }

            // Draw outline stroke if specified (no gradient on the stroke).
            overlay.strokeColorArgb?.let { strokeColor ->
                val strokePaint = Paint(paint).apply {
                    shader = null
                    style = Paint.Style.STROKE
                    strokeWidth = (height * 0.008f).coerceAtLeast(2f)
                    color = withAlpha(strokeColor, state.alpha)
                    clearShadowLayer()
                    maskFilter = null
                }
                canvas.drawText(text, centerX, textTop, strokePaint)
            }

            canvas.drawText(text, centerX, textTop, paint)

            if (isHighlighted) {
                val border = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (height * 0.004f).coerceAtLeast(2f)
                    color = 0xFF00E5FF.toInt()
                }
                canvas.drawRoundRect(
                    RectF(
                        textLeft - width * PILL_PAD_FRACTION_X,
                        textTop + metrics.ascent - height * PILL_PAD_FRACTION_Y,
                        textRight + width * PILL_PAD_FRACTION_X,
                        textTop + metrics.descent + height * PILL_PAD_FRACTION_Y
                    ),
                    height * 0.012f,
                    height * 0.012f,
                    border
                )
            }
        } finally {
            if (needsTransform) canvas.restore()
        }
    }
}
