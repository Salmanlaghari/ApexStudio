package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask

/**
 * Applies a [VideoMask] to a text overlay in the live preview.
 *
 * The mask geometry is defined in normalised FRAME coordinates
 * (0..1), but the text Box lives in its own local layout space and
 * is additionally scaled/rotated by its animation graphicsLayer.
 * This modifier is applied OUTSIDE that graphicsLayer, so the mask
 * is evaluated in the Box's untransformed layout space: the frame
 * edge is mapped back through the Box's on-screen origin
 * ([innerLeftPx]/[innerTopPx]) and the animation scale around the
 * Box centre. Rotation is NOT un-rotated (a rotated text mask is a
 * preview approximation — the export path masks in true frame space).
 *
 * The content is drawn into an offscreen layer so the DstIn/DstOut
 * blend only affects this text, never neighbouring Compose siblings.
 *
 * Shape masks preview with a hard edge (feather is export-only for
 * shapes); the LINEAR wipe reproduces the export shader's feather
 * ramp exactly.
 */
fun Modifier.textMaskClip(
    mask: VideoMask?,
    innerLeftPx: Float,
    innerTopPx: Float,
    scaleX: Float,
    scaleY: Float,
    containerWidthPx: Float,
    containerHeightPx: Float
): Modifier {
    if (mask == null || !mask.enabled) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawTextMask(
                mask = mask,
                innerLeftPx = innerLeftPx,
                innerTopPx = innerTopPx,
                scaleX = scaleX,
                scaleY = scaleY,
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx
            )
        }
}

private fun DrawScope.drawTextMask(
    mask: VideoMask,
    innerLeftPx: Float,
    innerTopPx: Float,
    scaleX: Float,
    scaleY: Float,
    containerWidthPx: Float,
    containerHeightPx: Float
) {
    val sX = if (scaleX == 0f) 1f else scaleX
    val sY = if (scaleY == 0f) 1f else scaleY
    val cx = size.width / 2f
    val cy = size.height / 2f
    // Un-transform: layout point = centre + (frame point - origin - centre) / scale.
    fun frameToLocalX(frameX: Float) = cx + (frameX - innerLeftPx - cx) / sX
    fun frameToLocalY(frameY: Float) = cy + (frameY - innerTopPx - cy) / sY
    val alpha = mask.opacity.coerceIn(0f, 1f)

    when (mask.shape) {
        MaskShape.LINEAR -> {
            // Export shader: fw = sizeX/2 (frame fraction); kept when
            // (x - centreX)/fw <= -feather, i.e. x <= centreX - feather*fw.
            val edgeFrame = mask.centerX * containerWidthPx
            val featherFrame = (mask.feather * (mask.sizeX / 2f) * containerWidthPx)
                .coerceAtLeast(1f)
            val localStart = frameToLocalX(edgeFrame - featherFrame)
            val localEnd = frameToLocalX(edgeFrame)
            val w = size.width
            if (w <= 0f) return
            if (mask.invert) {
                // Inverted wipe keeps the RIGHT side: erase the left.
                drawLinearWipe(localStart, localEnd, alpha, keepLeft = false)
            } else {
                drawLinearWipe(localStart, localEnd, alpha, keepLeft = true)
            }
        }
        MaskShape.RECTANGLE -> {
            val left = frameToLocalX((mask.centerX - mask.sizeX / 2f) * containerWidthPx)
            val top = frameToLocalY((mask.centerY - mask.sizeY / 2f) * containerHeightPx)
            val right = frameToLocalX((mask.centerX + mask.sizeX / 2f) * containerWidthPx)
            val bottom = frameToLocalY((mask.centerY + mask.sizeY / 2f) * containerHeightPx)
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, right, bottom),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                )
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = alpha),
                blendMode = if (mask.invert) androidx.compose.ui.graphics.BlendMode.DstOut
                else androidx.compose.ui.graphics.BlendMode.DstIn
            )
        }
        MaskShape.ELLIPSE -> {
            val left = frameToLocalX((mask.centerX - mask.sizeX / 2f) * containerWidthPx)
            val top = frameToLocalY((mask.centerY - mask.sizeY / 2f) * containerHeightPx)
            val right = frameToLocalX((mask.centerX + mask.sizeX / 2f) * containerWidthPx)
            val bottom = frameToLocalY((mask.centerY + mask.sizeY / 2f) * containerHeightPx)
            val path = Path().apply { addOval(Rect(left, top, right, bottom)) }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = alpha),
                blendMode = if (mask.invert) androidx.compose.ui.graphics.BlendMode.DstOut
                else androidx.compose.ui.graphics.BlendMode.DstIn
            )
        }
    }
}

/**
 * Horizontal wipe in local coords: fully opaque up to [localStart],
 * ramping to transparent at [localEnd] (keepLeft), or the reverse
 * (inverted wipe keeps the right side).
 */
private fun DrawScope.drawLinearWipe(
    localStart: Float,
    localEnd: Float,
    alpha: Float,
    keepLeft: Boolean
) {
    val w = size.width
    if (w <= 0f) return
    val f0 = (localStart / w).coerceIn(0f, 1f)
    val f1 = (localEnd / w).coerceIn(0f, 1f)
    if (f1 - f0 < 0.001f) {
        // Feather ~0 or the edge is off-screen: solid keep / erase.
        val keepAll = if (keepLeft) localEnd > 0f else localStart < w
        if (keepAll) return // fully visible — nothing to erase
        drawRect(
            color = Color.White.copy(alpha = alpha),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstOut
        )
        return
    }
    val opaque = Color.White.copy(alpha = alpha)
    val transparent = Color.White.copy(alpha = 0f)
    val stops = if (keepLeft) {
        arrayOf(0f to opaque, f0 to opaque, f1 to transparent, 1f to transparent)
    } else {
        arrayOf(0f to transparent, f0 to transparent, f1 to opaque, 1f to opaque)
    }
    drawRect(
        brush = Brush.horizontalGradient(colorStops = stops),
        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        topLeft = Offset.Zero,
        size = size
    )
}
