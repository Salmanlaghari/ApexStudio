package com.apexstudio.app.data.text

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.TextOverlayKaraoke
import com.apexstudio.app.domain.model.VideoMask
import com.apexstudio.app.domain.model.MaskModel
import com.apexstudio.app.domain.model.MaskPoint

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
     * @param masks per-overlay shape mask, already evaluated at the
     *   frame time (see [TextOverlay.effectiveMaskAt]); overlays
     *   without an entry render unmasked. Applied in FRAME space
     *   after the animation transform, so a travelling wipe clips
     *   exactly the pixels the preview shows.
     */
    fun render(
        overlays: List<TextOverlay>,
        width: Int,
        height: Int,
        highlightId: String? = null,
        states: Map<String, TextAnimEngine.State> = emptyMap(),
        masks: Map<String, VideoMask> = emptyMap()
    ): Bitmap {
        if (width <= 1 || height <= 1 || overlays.isEmpty()) {
            return Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        for (overlay in overlays) {
            val mask = masks[overlay.id]?.takeIf { it.enabled }
            if (mask == null) {
                drawOverlay(
                    canvas, overlay, width, height,
                    state = states[overlay.id] ?: TextAnimEngine.State.Identity,
                    isHighlighted = overlay.id == highlightId
                )
            } else {
                // Isolate the overlay in a layer so the DST_IN mask
                // only clips this overlay, never its neighbours.
                val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
                try {
                    drawOverlay(
                        canvas, overlay, width, height,
                        state = states[overlay.id] ?: TextAnimEngine.State.Identity,
                        isHighlighted = overlay.id == highlightId
                    )
                    drawMask(canvas, mask, width, height)
                } finally {
                    canvas.restoreToCount(layer)
                }
            }
        }
        return bitmap
    }

    private fun withAlpha(argb: Long, alpha: Float): Int {
        val origA = ((argb shr 24) and 0xFF).toInt()
        val newA = (origA * alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return ((newA shl 24) or (argb and 0xFFFFFFL).toInt())
    }

    /**
     * Paints [mask] onto [canvas] with DST_IN (keep the shape) or
     * DST_OUT (keep everything outside the shape), inside the caller's
     * saveLayer so only the current overlay is clipped. Geometry is
     * normalised frame coords (0..1), matching [MaskGlEffect]'s
     * shader: LINEAR keeps the LEFT side with a feather ramp ending
     * AT the centre, RECTANGLE/ELLIPSE keep the shape, and [VideoMask.opacity]
     * scales the kept alpha.
     */
    private fun drawMask(canvas: Canvas, mask: VideoMask, width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        val alpha = (mask.opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        // Inverted LINEAR keeps the RIGHT side: DST_IN with a mirrored
        // gradient. (DST_OUT with the normal gradient would also work,
        // but DST_IN + mirror keeps every shape on one blend mode.)
        // Shapes use DST_OUT for invert (erase the shape, keep outside).
        val mode = if (mask.invert && mask.shape != MaskShape.LINEAR) {
            PorterDuff.Mode.DST_OUT
        } else {
            PorterDuff.Mode.DST_IN
        }
        when (mask.shape) {
            MaskShape.LINEAR -> {
                // Same ramp as the shader: fw = sizeX/2; the keep
                // region ends at centreX, feathered over
                // feather * fw to its left. Inverted keeps the
                // RIGHT side (gradient mirrored).
                val edge = mask.centerX * w
                val featherPx = (mask.feather * (mask.sizeX / 2f) * w).coerceAtLeast(1f)
                val start = edge - featherPx
                val keep = (alpha shl 24) or 0x00FFFFFF
                val clear = 0x00000000
                val colors = if (mask.invert) {
                    intArrayOf(clear, clear, keep, keep)
                } else {
                    intArrayOf(keep, keep, clear, clear)
                }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(mode)
                    shader = LinearGradient(
                        start, 0f, edge, 0f,
                        colors,
                        floatArrayOf(0f, 0f, 1f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            MaskShape.RECTANGLE -> {
                val rect = RectF(
                    (mask.centerX - mask.sizeX / 2f) * w,
                    (mask.centerY - mask.sizeY / 2f) * h,
                    (mask.centerX + mask.sizeX / 2f) * w,
                    (mask.centerY + mask.sizeY / 2f) * h
                )
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(mode)
                    color = (alpha shl 24) or 0x00FFFFFF
                    maskFilter = if (mask.feather > 0f) {
                        BlurMaskFilter(mask.feather * w * 0.25f, BlurMaskFilter.Blur.NORMAL)
                    } else null
                }
                canvas.drawRoundRect(rect, h * 0.02f, h * 0.02f, paint)
            }
            MaskShape.ELLIPSE -> {
                val rect = RectF(
                    (mask.centerX - mask.sizeX / 2f) * w,
                    (mask.centerY - mask.sizeY / 2f) * h,
                    (mask.centerX + mask.sizeX / 2f) * w,
                    (mask.centerY + mask.sizeY / 2f) * h
                )
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(mode)
                    color = (alpha shl 24) or 0x00FFFFFF
                    maskFilter = if (mask.feather > 0f) {
                        BlurMaskFilter(mask.feather * w * 0.25f, BlurMaskFilter.Blur.NORMAL)
                    } else null
                }
                canvas.drawOval(rect, paint)
            }
            else -> {
                // Bug 4: the modes added with the top-level Mask tool.
                // Circle is forced square; mirror / blur-faces keep an
                // oval of the mask extent; the polygonal modes (heart,
                // star, triangle, pen) are stroked from the SAME
                // MaskModel points the clip mask and the export shader
                // use, so all three render one region, not three.
                val rectNew = RectF(
                    (mask.centerX - mask.sizeX / 2f) * w,
                    (mask.centerY - mask.sizeY / 2f) * h,
                    (mask.centerX + mask.sizeX / 2f) * w,
                    (mask.centerY + mask.sizeY / 2f) * h
                )
                val paintNew = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(mode)
                    color = (alpha shl 24) or 0x00FFFFFF
                    isAntiAlias = true
                    style = Paint.Style.FILL
                    maskFilter = if (mask.feather > 0f) {
                        BlurMaskFilter(mask.feather * w * 0.25f, BlurMaskFilter.Blur.NORMAL)
                    } else null
                }
                when (mask.shape) {
                    MaskShape.CIRCLE -> {
                        val side = minOf(rectNew.width(), rectNew.height())
                        val cxN = rectNew.left + rectNew.width() / 2f
                        val cyN = rectNew.top + rectNew.height() / 2f
                        val square = RectF(cxN - side / 2f, cyN - side / 2f, cxN + side / 2f, cyN + side / 2f)
                        canvas.drawOval(square, paintNew)
                    }
                    MaskShape.MIRROR, MaskShape.BLUR_FACE -> canvas.drawOval(rectNew, paintNew)
                    MaskShape.RECTANGLE, MaskShape.ELLIPSE, MaskShape.LINEAR -> Unit // above
                    else -> {
                        val poly: List<MaskPoint> = MaskModel.outline(mask.shape, mask.drawPoints)
                        if (poly.size >= 4) {
                            val rad = Math.toRadians(mask.rotationDeg.toDouble())
                            val cu = Math.cos(rad).toFloat()
                            val su = Math.sin(rad).toFloat()
                            val rectPoly = android.graphics.Path()
                            poly.forEachIndexed { i, pt ->
                                val rx = pt.x * cu - pt.y * su
                                val ry = pt.x * su + pt.y * cu
                                val px = rectNew.centerX() + rx * rectNew.width() / 2f
                                val py = rectNew.centerY() + ry * rectNew.height() / 2f
                                if (i == 0) rectPoly.moveTo(px, py) else rectPoly.lineTo(px, py)
                            }
                            rectPoly.close()
                            canvas.drawPath(rectPoly, paintNew)
                        }
                    }
                }
            }
        }
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
            val karaokeRange = TextOverlayKaraoke.validRange(overlay)
                ?.takeIf { it.endUtf16Exclusive <= text.length }
            if (karaokeRange != null) {
                val fullTextLeft = when (paint.textAlign) {
                    Paint.Align.LEFT -> centerX
                    Paint.Align.RIGHT -> centerX - paint.measureText(text)
                    else -> centerX - paint.measureText(text) / 2f
                }
                val prefixWidth = paint.getRunAdvance(
                    text, 0, text.length, 0, text.length, false, karaokeRange.startUtf16
                )
                val highlightPaint = Paint(paint).apply {
                    shader = null
                    color = withAlpha(overlay.highlightColorArgb, state.alpha)
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawTextRun(text, karaokeRange.startUtf16, karaokeRange.endUtf16Exclusive,
                    0, text.length, fullTextLeft + prefixWidth, textTop, false, highlightPaint)
            }

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
