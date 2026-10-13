package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import android.graphics.BlurMaskFilter
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Modifier that clips content to a [VideoMask]'s kept region with a
 * FEATHERED edge and per-pixel opacity — WYSIWYG with the export
 * shaders ([com.apexstudio.app.data.effect.MaskGlEffect] /
 * [com.apexstudio.app.data.effect.PipOverlayGlEffect]), which composite
 * `maskFactor(feathered SDF) * opacity`.
 *
 * The old approach (hard [Shape] clip + uniform `graphicsLayer` alpha)
 * showed a hard edge where the export shows a feathered blend, and a
 * flat opacity where the export scales opacity by the mask factor.
 *
 * Implementation: content is drawn into an offscreen layer, then the
 * feathered mask is punched in with [BlendMode.DstIn]:
 * - RECTANGLE/ELLIPSE: the mask path drawn with a [BlurMaskFilter]
 *   whose radius mirrors the shader's `featherW = uFeather * 0.3`
 *   (frame-height units), so the soft edge lands in the same place.
 * - LINEAR: a horizontal alpha gradient across the wipe transition
 *   zone (`feather * sizeX/2` each side of the wipe line, as the
 *   shader does), instead of a hard rect.
 * - Inverted masks punch the complement; the paint alpha carries
 *   [VideoMask.opacity], exactly like the shader's `m * uOpacity`.
 */
fun Modifier.videoMaskFeatheredClip(mask: VideoMask): Modifier = this.then(
    Modifier.drawWithContent {
        if (!mask.enabled || mask.opacity <= 0.001f || size.isEmpty()) {
            drawContent()
            return@drawWithContent
        }
        drawIntoCanvas { canvas ->
            val bounds = Rect(Offset.Zero, size)
            canvas.saveLayer(bounds, Paint())
            drawContent()
            val opacity = mask.opacity.coerceIn(0f, 1f)
            when (mask.shape) {
                MaskShape.RECTANGLE, MaskShape.ELLIPSE -> {
                    val cx = mask.centerX * size.width
                    val cy = mask.centerY * size.height
                    val hw = mask.sizeX * size.width / 2f
                    val hh = mask.sizeY * size.height / 2f
                    val shapeRect = Rect(cx - hw, cy - hh, cx + hw, cy + hh)
                    // Mirrors the export shader's featherW = uFeather * 0.3
                    // in frame-height units.
                    val featherPx = (mask.feather * 0.3f * size.height)
                        .coerceAtLeast(0f)
                    val path = Path().apply {
                        if (!mask.invert) {
                            if (mask.shape == MaskShape.RECTANGLE) addRect(shapeRect)
                            else addOval(shapeRect)
                        } else {
                            // Even-odd full-rect + shape => everything BUT
                            // the shape, with the same soft edge.
                            addRect(bounds)
                            if (mask.shape == MaskShape.RECTANGLE) addRect(shapeRect)
                            else addOval(shapeRect)
                            fillType = PathFillType.EvenOdd
                        }
                    }
                    val paint = Paint().apply {
                        blendMode = BlendMode.DstIn
                        color = Color.White.copy(alpha = opacity)
                        if (featherPx > 0.5f) {
                            asFrameworkPaint().maskFilter = BlurMaskFilter(
                                featherPx, BlurMaskFilter.Blur.NORMAL
                            )
                        }
                    }
                    canvas.drawPath(path, paint)
                }
                MaskShape.LINEAR -> {
                    // Kept = left of the wipe line at centerX. The export
                    // shader ramps across featherW * fw on each side, with
                    // fw = sizeX * 0.5 (see MaskGlEffect).
                    val lineX = mask.centerX * size.width
                    val t = (mask.feather * (mask.sizeX * 0.5f) * size.width)
                        .coerceAtLeast(0f)
                    val x0 = (lineX - t).coerceIn(0f, size.width)
                    val x1 = (lineX + t).coerceIn(0f, size.width)
                    val f0 = (x0 / size.width).coerceIn(0f, 1f)
                    // Keep a sliver of ramp even at ~zero feather so the
                    // gradient never collapses to duplicate stops.
                    val f1 = ((x1 / size.width).coerceIn(0f, 1f))
                        .coerceAtLeast(f0 + 1e-3f)
                    val stops = if (!mask.invert) {
                        arrayOf(
                            0f to Color.White,
                            f0 to Color.White,
                            f1 to Color.Transparent,
                            1f to Color.Transparent
                        )
                    } else {
                        arrayOf(
                            0f to Color.Transparent,
                            f0 to Color.Transparent,
                            f1 to Color.White,
                            1f to Color.White
                        )
                    }
                    drawRect(
                        brush = Brush.horizontalGradient(
                            *stops,
                            startX = 0f,
                            endX = size.width
                        ),
                        alpha = opacity,
                        blendMode = BlendMode.DstIn
                    )
                }
            }
            canvas.restore()
        }
    }
)

/**
 * Live mask preview for the main (V1) video clip.
 *
 * Everything OUTSIDE the mask shape is darkened with a scrim so the
 * user sees exactly which region the export will keep — the same
 * normalised geometry [VideoMask] carries, resolved against the
 * preview box just like the export shader resolves it against the
 * frame.
 *
 * In [editMode] (mask panel open) the shape outline + a resize handle
 * are drawn: drag anywhere to move the mask, drag the handle to
 * resize. `*Live` callbacks update state without persisting (called
 * every drag frame); the `*Finished` callbacks fire once on release
 * so the project JSON is written a single time per gesture.
 */
@Composable
fun MaskPreviewOverlay(
    mask: VideoMask?,
    editMode: Boolean,
    onCenterChangeLive: (x: Float, y: Float) -> Unit = { _, _ -> },
    onCenterChangeFinished: (x: Float, y: Float) -> Unit = { _, _ -> },
    onSizeChangeLive: (sx: Float, sy: Float) -> Unit = { _, _ -> },
    onSizeChangeFinished: (sx: Float, sy: Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (mask == null || !mask.enabled) return

    // Latest mask for the gesture block: reading through
    // rememberUpdatedState keeps drags alive across recompositions
    // (putting `mask` in the pointerInput keys would cancel the drag
    // on every state update).
    val latestMask by rememberUpdatedState(mask)
    var canvasW by remember { mutableFloatStateOf(0f) }
    var canvasH by remember { mutableFloatStateOf(0f) }

    /** Resize-handle centre in px for the current geometry. */
    fun handlePos(): Offset {
        val m = latestMask
        val cx = m.centerX * canvasW
        val cy = m.centerY * canvasH
        return if (m.shape == MaskShape.LINEAR) {
            // Below the wipe line, clear of the video content.
            Offset(cx, canvasH - 80f)
        } else {
            Offset(cx + m.sizeX * canvasW / 2f, cy + m.sizeY * canvasH / 2f)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                canvasW = it.width.toFloat()
                canvasH = it.height.toFloat()
            }
            .then(
                if (editMode) {
                    Modifier.pointerInput(canvasW, canvasH) {
                        var resizing = false
                        detectDragGestures(
                            onDragStart = { offset ->
                                resizing = (offset - handlePos()).getDistance() < 56f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val m = latestMask
                                if (canvasW <= 0f || canvasH <= 0f) return@detectDragGestures
                                if (resizing) {
                                    val c = Offset(m.centerX * canvasW, m.centerY * canvasH)
                                    val before = (change.position - dragAmount - c).getDistance()
                                    val after = (change.position - c).getDistance()
                                    val scale =
                                        if (before > 1f) (after / before).coerceIn(0.5f, 2f) else 1f
                                    onSizeChangeLive(
                                        (m.sizeX * scale).coerceIn(0.05f, 1f),
                                        (m.sizeY * scale).coerceIn(0.05f, 1f)
                                    )
                                } else {
                                    onCenterChangeLive(
                                        ((m.centerX * canvasW + dragAmount.x) / canvasW).coerceIn(0f, 1f),
                                        ((m.centerY * canvasH + dragAmount.y) / canvasH).coerceIn(0f, 1f)
                                    )
                                }
                            },
                            onDragEnd = {
                                val m = latestMask
                                if (resizing) onSizeChangeFinished(m.sizeX, m.sizeY)
                                else onCenterChangeFinished(m.centerX, m.centerY)
                            },
                            onDragCancel = { }
                        )
                    }
                } else Modifier
            )
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val cx = mask.centerX * w
        val cy = mask.centerY * h
        val hw = mask.sizeX * w / 2f
        val hh = mask.sizeY * h / 2f
        val featherPx = (mask.feather * 0.3f * h).coerceAtLeast(0f)
        val scrimAlpha = 0.55f

        fun shapePath(expandPx: Float = 0f): Path = Path().apply {
            when (mask.shape) {
                MaskShape.RECTANGLE -> addRect(
                    Rect(cx - hw - expandPx, cy - hh - expandPx, cx + hw + expandPx, cy + hh + expandPx)
                )
                MaskShape.ELLIPSE -> addOval(
                    Rect(cx - hw - expandPx, cy - hh - expandPx, cx + hw + expandPx, cy + hh + expandPx)
                )
                MaskShape.LINEAR -> {
                    // Kept = left of the wipe line at centerX.
                    addRect(Rect(0f, 0f, cx + expandPx, h))
                }
            }
        }

        val steps = 6
        if (!mask.invert) {
            // Dim everything outside the feathered edge…
            val scrim = Path().apply {
                addRect(Rect(0f, 0f, w, h))
                op(this, shapePath(featherPx), PathOperation.Difference)
            }
            drawPath(scrim, Color.Black.copy(alpha = scrimAlpha))
            // …with a feather gradient fading in from the mask edge outward.
            // Rings are outward-only (expanded minus base) so the kept
            // interior is never darkened.
            var prev = 0f
            for (i in 1..steps) {
                val f = i.toFloat() / steps
                val ring = Path().apply {
                    op(
                        shapePath(featherPx * f),
                        shapePath(featherPx * prev),
                        PathOperation.Difference
                    )
                }
                drawPath(ring, Color.Black.copy(alpha = scrimAlpha * f))
                prev = f
            }
        } else {
            // Inverted: dim the interior; feather fades inward from the edge.
            // Clamp the feather depth so a small mask can't invert the rect.
            val maxIn = when (mask.shape) {
                MaskShape.LINEAR -> cx
                else -> minOf(hw, hh)
            }.coerceAtLeast(0f)
            val featherIn = featherPx.coerceIn(0f, maxIn)
            drawPath(shapePath(-featherIn), Color.Black.copy(alpha = scrimAlpha))
            var prev = 0f
            for (i in 1..steps) {
                val f = i.toFloat() / steps
                val ring = Path().apply {
                    op(
                        shapePath(-featherIn * prev),
                        shapePath(-featherIn * f),
                        PathOperation.Difference
                    )
                }
                drawPath(ring, Color.Black.copy(alpha = scrimAlpha * f))
                prev = f
            }
        }

        if (editMode) {
            drawPath(shapePath(), ApexPalette.NeonCyan, style = Stroke(width = 2.dp.toPx()))
            val hp = handlePos()
            drawCircle(ApexPalette.NeonCyan, radius = 9.dp.toPx(), center = hp)
            drawCircle(Color.Black, radius = 9.dp.toPx(), center = hp, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
