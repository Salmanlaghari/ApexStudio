package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.stickers.StickerImageCache
import com.apexstudio.app.data.stickers.StickerSpriteRenderer
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Interactive sticker canvas: renders every active [StickerOverlay] over
 * the video preview and provides CapCut/PicsArt-style canvas editing —
 * tap to select, drag to move, pinch to resize / rotate, corner handle
 * to resize, delete handle, rect crop UI, and rect/oval cutout masks.
 *
 * Geometry matches [StickerSpriteRenderer] (and therefore the export GL
 * sprite): the sticker is a square of side
 * `BASE_STICKER_FRACTION × canvas height × sizeScale`, centred at the
 * normalised (x, y). What you drag on screen is what bakes into the MP4.
 */
@Composable
fun StickerCanvas(
    stickers: List<StickerOverlay>,
    selectedStickerId: String?,
    currentTimeMs: Long,
    onSelectSticker: (String?) -> Unit,
    onMoveSticker: (id: String, dx: Float, dy: Float, persist: Boolean) -> Unit,
    onScaleSticker: (id: String, scale: Float, persist: Boolean) -> Unit,
    onRotateSticker: (id: String, deltaDeg: Float, persist: Boolean) -> Unit,
    onStickerGestureEnd: (id: String) -> Unit,
    onRemoveSticker: (String) -> Unit,
    onCropSticker: (id: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onCutoutSticker: (id: String, shape: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageCache = remember { StickerImageCache(context) }
    var cropTargetId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val canvasWpx = with(density) { maxWidth.toPx() }
        val canvasHpx = with(density) { maxHeight.toPx() }

        val activeStickers = remember(stickers, currentTimeMs) {
            stickers.filter { it.isActiveAt(currentTimeMs) }
        }
        val selected = activeStickers.firstOrNull { it.id == selectedStickerId }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Tap = hit-test stickers topmost-first: a hit selects
                // that sticker, a miss deselects. Drags on a sticker are
                // consumed by the item's transform detector, so they
                // never arrive here as taps.
                .pointerInput(activeStickers, selectedStickerId) {
                    detectTapGestures(
                        onTap = { pos ->
                            val hit = activeStickers.asReversed().firstOrNull { s ->
                                stickerHitTest(s, pos, canvasWpx, canvasHpx)
                            }
                            onSelectSticker(hit?.id)
                        }
                    )
                }
        ) {
            for (sticker in activeStickers) {
                key(sticker.id) {
                    StickerItem(
                        sticker = sticker,
                        isSelected = sticker.id == selectedStickerId,
                        canvasWpx = canvasWpx,
                        canvasHpx = canvasHpx,
                        imageCache = imageCache,
                        onMoveSticker = onMoveSticker,
                        onScaleSticker = onScaleSticker,
                        onRotateSticker = onRotateSticker,
                        onStickerGestureEnd = onStickerGestureEnd,
                        onRemoveSticker = onRemoveSticker
                    )
                }
            }

            // Floating action bar above the selected sticker.
            if (selected != null && cropTargetId == null) {
                StickerActionBar(
                    sticker = selected,
                    canvasWpx = canvasWpx,
                    canvasHpx = canvasHpx,
                    onCrop = { cropTargetId = selected.id },
                    onCutout = {
                        val next = when (selected.cutoutShape.uppercase()) {
                            "NONE" -> "RECT"
                            "RECT" -> "OVAL"
                            else -> "NONE"
                        }
                        onCutoutSticker(selected.id, next)
                    },
                    onDelete = { onRemoveSticker(selected.id) }
                )
            }
        }

        // Rect crop editor overlay.
        val cropTarget = activeStickers.firstOrNull { it.id == cropTargetId }
        if (cropTarget != null) {
            StickerCropEditor(
                sticker = cropTarget,
                imageCache = imageCache,
                onApply = { l, t, r, b ->
                    onCropSticker(cropTarget.id, l, t, r, b)
                    cropTargetId = null
                },
                onCancel = { cropTargetId = null }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { imageCache.evictAll() }
    }
}

/** Bounding-square hit test in canvas px (slightly generous for fingers). */
private fun stickerHitTest(
    sticker: StickerOverlay,
    pos: Offset,
    canvasWpx: Float,
    canvasHpx: Float
): Boolean {
    val side = canvasHpx * StickerSpriteRenderer.BASE_STICKER_FRACTION *
        sticker.sizeScale.coerceIn(0.05f, 8f)
    val cx = sticker.x.coerceIn(0f, 1f) * canvasWpx
    val cy = sticker.y.coerceIn(0f, 1f) * canvasHpx
    val half = side / 2f + 12f
    return kotlin.math.abs(pos.x - cx) <= half && kotlin.math.abs(pos.y - cy) <= half
}

@Composable
private fun StickerItem(
    sticker: StickerOverlay,
    isSelected: Boolean,
    canvasWpx: Float,
    canvasHpx: Float,
    imageCache: StickerImageCache,
    onMoveSticker: (id: String, dx: Float, dy: Float, persist: Boolean) -> Unit,
    onScaleSticker: (id: String, scale: Float, persist: Boolean) -> Unit,
    onRotateSticker: (id: String, deltaDeg: Float, persist: Boolean) -> Unit,
    onStickerGestureEnd: (id: String) -> Unit,
    onRemoveSticker: (String) -> Unit
) {
    val density = LocalDensity.current
    val latest by rememberUpdatedState(sticker)

    val imageBitmap = produceState(
        initialValue = null,
        sticker.id, sticker.assetPath
    ) {
        value = withContext(Dispatchers.IO) {
            val path = sticker.assetPath
            if (path.isNullOrBlank()) null
            else imageCache.get(path)?.asImageBitmap()
        }
    }.value

    val sidePx = canvasHpx * StickerSpriteRenderer.BASE_STICKER_FRACTION *
        sticker.sizeScale.coerceIn(0.05f, 8f)
    val sideDp: Dp = with(density) { sidePx.toDp() }
    val cxPx = sticker.x.coerceIn(0f, 1f) * canvasWpx
    val cyPx = sticker.y.coerceIn(0f, 1f) * canvasHpx

    Box(
        modifier = Modifier
            .size(sideDp)
            .offset {
                IntOffset(
                    (cxPx - sidePx / 2f).roundToInt(),
                    (cyPx - sidePx / 2f).roundToInt()
                )
            }
            .graphicsLayer {
                rotationZ = sticker.rotationDeg
                alpha = sticker.opacity.coerceIn(0f, 1f)
            }
            .pointerInput(sticker.id) {
                var gestureActive = false
                detectTransformGestures(
                    onGesture = { _, pan, zoom, rotation ->
                        if (!gestureActive) {
                            gestureActive = true
                            // Selecting on first touch keeps drag / pinch
                            // working even when another sticker is selected.
                        }
                        val s = latest
                        if (pan != Offset.Zero) {
                            onMoveSticker(
                                s.id,
                                pan.x / canvasWpx,
                                pan.y / canvasHpx,
                                false
                            )
                        }
                        if (zoom != 1f) {
                            onScaleSticker(
                                s.id,
                                (s.sizeScale * zoom).coerceIn(0.1f, 8f),
                                false
                            )
                        }
                        if (rotation != 0f) {
                            onRotateSticker(s.id, rotation, false)
                        }
                    },
                    onGestureEnd = {
                        if (gestureActive) {
                            gestureActive = false
                            onStickerGestureEnd(latest.id)
                        }
                    }
                )
            }
    ) {
        val img = imageBitmap
        if (img != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = latest.sanitizedCrop()
                val srcOffset = IntOffset(
                    (c[0] * img.width).roundToInt(),
                    (c[1] * img.height).roundToInt()
                )
                val srcSize = IntSize(
                    ((c[2] - c[0]) * img.width).roundToInt().coerceAtLeast(1),
                    ((c[3] - c[1]) * img.height).roundToInt().coerceAtLeast(1)
                )
                val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                // Rotation is applied by the outer graphicsLayer (so the
                // selection border and handles rotate with the sticker).
                if (latest.cutoutShape.equals("OVAL", ignoreCase = true)) {
                    val oval = Path().apply { addOval(Rect(Offset.Zero, size)) }
                    clipPath(oval) {
                        drawImage(
                            img, srcOffset, srcSize, IntOffset.Zero, dstSize
                        )
                    }
                } else {
                    drawImage(
                        img, srcOffset, srcSize, IntOffset.Zero, dstSize
                    )
                }
            }
        } else {
            // Legacy emoji sticker (or PNG still loading): render the symbol.
            // Rotation / opacity come from the outer graphicsLayer.
            Text(
                text = latest.symbolOrUri.ifBlank { "▦" },
                fontSize = with(density) { (sidePx * 0.62f).toSp() },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (isSelected) {
            // Dashed selection border (rotates with the sticker, like CapCut).
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = Color(0xFF00E5FF),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                )
            }
            // Delete handle (top-end).
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable { onRemoveSticker(sticker.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove sticker",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
            // Resize handle (bottom-end): drag diagonally to scale.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 12.dp, y = 12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.NeonCyan)
                    .pointerInput(sticker.id) {
                        var startScale = 1f
                        var acc = 0f
                        var baseSide = 1f
                        detectDragGestures(
                            onDragStart = {
                                startScale = latest.sizeScale
                                acc = 0f
                                baseSide = (canvasHpx *
                                    StickerSpriteRenderer.BASE_STICKER_FRACTION *
                                    startScale).coerceAtLeast(1f)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                acc += (dragAmount.x + dragAmount.y)
                                val factor = 1f + acc / baseSide
                                onScaleSticker(
                                    latest.id,
                                    (startScale * factor).coerceIn(0.1f, 8f),
                                    false
                                )
                            },
                            onDragEnd = { onStickerGestureEnd(latest.id) },
                            onDragCancel = { onStickerGestureEnd(latest.id) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.OpenInFull,
                    contentDescription = "Resize sticker",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StickerActionBar(
    sticker: StickerOverlay,
    canvasWpx: Float,
    canvasHpx: Float,
    onCrop: () -> Unit,
    onCutout: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val sidePx = canvasHpx * StickerSpriteRenderer.BASE_STICKER_FRACTION *
        sticker.sizeScale.coerceIn(0.05f, 8f)
    val cxPx = sticker.x.coerceIn(0f, 1f) * canvasWpx
    val cyPx = sticker.y.coerceIn(0f, 1f) * canvasHpx
    val barWidthPx = with(density) { 216.dp.toPx() }
    val barX = (cxPx - barWidthPx / 2f).coerceIn(4f, (canvasWpx - barWidthPx).coerceAtLeast(4f))
    val barY = (cyPx - sidePx / 2f - with(density) { 46.dp.toPx() }).coerceAtLeast(4f)

    Row(
        modifier = Modifier
            .offset { IntOffset(barX.roundToInt(), barY.roundToInt()) }
            .width(216.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionChip(
            icon = Icons.Default.Crop,
            label = "Crop",
            onClick = onCrop
        )
        Spacer(Modifier.width(4.dp))
        val cutoutLabel = when (sticker.cutoutShape.uppercase()) {
            "RECT" -> "Rect"
            "OVAL" -> "Oval"
            else -> "Cutout"
        }
        ActionChip(
            icon = Icons.Default.ContentCut,
            label = cutoutLabel,
            highlighted = sticker.cutoutShape.uppercase() != "NONE",
            onClick = onCutout
        )
        Spacer(Modifier.width(4.dp))
        ActionChip(
            icon = Icons.Default.Delete,
            label = "Delete",
            danger = true,
            onClick = onDelete
        )
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    danger: Boolean = false
) {
    val tint = when {
        danger -> Color(0xFFFF6B6B)
        highlighted -> ApexPalette.NeonCyan
        else -> ApexPalette.TextPrimary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Full-canvas rect crop editor. Shows the sticker large and dimmed with
 * the crop region bright; drag any corner handle to adjust. Apply
 * writes the crop fractions back to the sticker.
 */
@Composable
private fun StickerCropEditor(
    sticker: StickerOverlay,
    imageCache: StickerImageCache,
    onApply: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onCancel: () -> Unit
) {
    val density = LocalDensity.current
    val imageBitmap = produceState(
        initialValue = null,
        sticker.id, sticker.assetPath
    ) {
        value = withContext(Dispatchers.IO) {
            val path = sticker.assetPath
            if (path.isNullOrBlank()) null
            else imageCache.get(path)?.asImageBitmap()
        }
    }.value

    var crop by remember(sticker.id) { mutableStateOf(sticker.sanitizedCrop()) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
    ) {
        val boxSidePx = with(density) { minOf(maxWidth, maxHeight).toPx() * 0.72f }
        val boxLeftPx = with(density) { maxWidth.toPx() / 2f - boxSidePx / 2f }
        val boxTopPx = with(density) { maxHeight.toPx() / 2f - boxSidePx / 2f }

        val img = imageBitmap
        if (img != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(sticker.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val corner = nearestCropCorner(down.position, crop, boxLeftPx, boxTopPx, boxSidePx)
                                ?: return@awaitEachGesture
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                val pos = event.changes.first().position
                                val fx = ((pos.x - boxLeftPx) / boxSidePx).coerceIn(0f, 1f)
                                val fy = ((pos.y - boxTopPx) / boxSidePx).coerceIn(0f, 1f)
                                crop = moveCropCorner(crop, corner, fx, fy)
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                val srcFull = IntSize(img.width, img.height)
                val dstBox = IntOffset(boxLeftPx.roundToInt(), boxTopPx.roundToInt())
                val dstSize = IntSize(boxSidePx.roundToInt(), boxSidePx.roundToInt())
                // Dimmed full sticker.
                drawImage(
                    img, IntOffset.Zero, srcFull, dstBox, dstSize, alpha = 0.35f
                )
                // Bright crop region.
                val c = crop
                val srcOffset = IntOffset(
                    (c[0] * img.width).roundToInt(),
                    (c[1] * img.height).roundToInt()
                )
                val srcSize = IntSize(
                    ((c[2] - c[0]) * img.width).roundToInt().coerceAtLeast(1),
                    ((c[3] - c[1]) * img.height).roundToInt().coerceAtLeast(1)
                )
                val cropDst = IntOffset(
                    (boxLeftPx + c[0] * boxSidePx).roundToInt(),
                    (boxTopPx + c[1] * boxSidePx).roundToInt()
                )
                val cropDstSize = IntSize(
                    ((c[2] - c[0]) * boxSidePx).roundToInt().coerceAtLeast(1),
                    ((c[3] - c[1]) * boxSidePx).roundToInt().coerceAtLeast(1)
                )
                drawImage(img, srcOffset, srcSize, cropDst, cropDstSize, alpha = 1f)

                // Crop frame: dashed border + rule-of-thirds + handles.
                val frame = Rect(
                    Offset(boxLeftPx + c[0] * boxSidePx, boxTopPx + c[1] * boxSidePx),
                    Offset(boxLeftPx + c[2] * boxSidePx, boxTopPx + c[3] * boxSidePx)
                )
                drawRect(
                    color = Color(0xFF00E5FF),
                    topLeft = frame.topLeft,
                    size = frame.size,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                )
                val thirdW = frame.size.width / 3f
                val thirdH = frame.size.height / 3f
                for (i in 1..2) {
                    drawLine(
                        Color.White.copy(alpha = 0.5f),
                        Offset(frame.left + thirdW * i, frame.top),
                        Offset(frame.left + thirdW * i, frame.bottom),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        Color.White.copy(alpha = 0.5f),
                        Offset(frame.left, frame.top + thirdH * i),
                        Offset(frame.right, frame.top + thirdH * i),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                for (corner in listOf(frame.topLeft, Offset(frame.right, frame.top), frame.bottomLeft, frame.bottomRight)) {
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 9.dp.toPx(),
                        center = corner
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 9.dp.toPx(),
                        center = corner,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Top bar: title + cancel / apply.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Crop sticker — drag corners",
                color = ApexPalette.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel crop", tint = ApexPalette.TextSecondary, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.NeonCyan)
                    .clickable {
                        val c = crop
                        onApply(c[0], c[1], c[2], c[3])
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = "Apply crop", tint = Color.Black, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private const val CROP_MIN = 0.05f

/** Which crop-rect corner (0=TL, 1=TR, 2=BL, 3=BR) is near [pos], or null. */
private fun nearestCropCorner(
    pos: Offset,
    crop: FloatArray,
    boxLeftPx: Float,
    boxTopPx: Float,
    boxSidePx: Float
): Int? {
    val corners = listOf(
        Offset(boxLeftPx + crop[0] * boxSidePx, boxTopPx + crop[1] * boxSidePx),
        Offset(boxLeftPx + crop[2] * boxSidePx, boxTopPx + crop[1] * boxSidePx),
        Offset(boxLeftPx + crop[0] * boxSidePx, boxTopPx + crop[3] * boxSidePx),
        Offset(boxLeftPx + crop[2] * boxSidePx, boxTopPx + crop[3] * boxSidePx)
    )
    val threshold = boxSidePx * 0.12f
    var best: Int? = null
    var bestDist = threshold
    corners.forEachIndexed { i, c ->
        val d = hypot(pos.x - c.x, pos.y - c.y)
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    return best
}

/** Move crop corner [corner] to fractions ([fx], [fy]), keeping a valid rect. */
internal fun moveCropCorner(
    crop: FloatArray,
    corner: Int,
    fx: Float,
    fy: Float
): FloatArray {
    val (l, t, r, b) = listOf(crop[0], crop[1], crop[2], crop[3])
    return when (corner) {
        0 -> floatArrayOf(fx.coerceIn(0f, r - CROP_MIN), fy.coerceIn(0f, b - CROP_MIN), r, b)
        1 -> floatArrayOf(l, fy.coerceIn(0f, b - CROP_MIN), fx.coerceIn(l + CROP_MIN, 1f), b)
        2 -> floatArrayOf(fx.coerceIn(0f, r - CROP_MIN), t, r, fy.coerceIn(t + CROP_MIN, 1f))
        else -> floatArrayOf(l, t, fx.coerceIn(l + CROP_MIN, 1f), fy.coerceIn(t + CROP_MIN, 1f))
    }
}
