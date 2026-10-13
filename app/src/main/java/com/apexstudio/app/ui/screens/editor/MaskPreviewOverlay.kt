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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask
import com.apexstudio.app.ui.theme.ApexPalette

import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Compose [Shape] that clips to a [VideoMask]'s kept region, used for
 * the V2/PiP overlay live preview.
 *
 * Hard-edged and opacity is applied separately via graphicsLayer:
 * the export GL shader adds feather/invert/opacity on top, so this
 * preview is an approximation — the Mask panel notes that feather is
 * export-only on overlays.
 */
class VideoMaskClipShape(private val mask: VideoMask) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cx = mask.centerX * size.width
        val cy = mask.centerY * size.height
        val hw = mask.sizeX * size.width / 2f
        val hh = mask.sizeY * size.height / 2f
        val shapePath = Path().apply {
            when (mask.shape) {
                MaskShape.RECTANGLE ->
                    addRect(Rect(cx - hw, cy - hh, cx + hw, cy + hh))
                MaskShape.ELLIPSE ->
                    addOval(Rect(cx - hw, cy - hh, cx + hw, cy + hh))
                // Kept = left of the wipe line at centerX.
                MaskShape.LINEAR ->
                    addRect(Rect(0f, 0f, cx, size.height))
            }
        }
        return if (!mask.invert) {
            Outline.Generic(shapePath)
        } else {
            // Even-odd full-rect + shape => everything BUT the shape.
            Outline.Generic(
                Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addPath(shapePath)
                    fillType = PathFillType.EvenOdd
                }
            )
        }
    }
}

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
