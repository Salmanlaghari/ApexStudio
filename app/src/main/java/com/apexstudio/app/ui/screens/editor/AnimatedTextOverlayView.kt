package com.apexstudio.app.ui.screens.editor

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.text.TextAnimEngine
import com.apexstudio.app.data.text.TextFontRegistry
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Professional CapCut-style interactive, draggable, resizable, and animated text overlay.
 *
 * Animation state comes from [TextAnimEngine] — the same math the
 * export path ([TextOverlayGlEffect]) uses, so the live preview and
 * the baked MP4 match, including intro presets, outros and loops.
 */
@Composable
fun AnimatedTextOverlayView(
    overlay: TextOverlay,
    isSelected: Boolean,
    containerWidth: Dp,
    containerHeight: Dp,
    currentTimeMs: Long,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onPositionChange: (newX: Float, newY: Float) -> Unit,
    onSizeScaleChange: (newScale: Float) -> Unit = {},
    onEditText: () -> Unit = {},
    onDelete: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var posX by remember(overlay.id) { mutableFloatStateOf(overlay.x) }
    var posY by remember(overlay.id) { mutableFloatStateOf(overlay.y) }
    var currentScale by remember(overlay.id, overlay.sizeScale) { mutableFloatStateOf(overlay.sizeScale) }
    var textWidthPx by remember(overlay.id) { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val containerWidthPx = with(density) { containerWidth.toPx() }.coerceAtLeast(1f)
    val containerHeightPx = with(density) { containerHeight.toPx() }.coerceAtLeast(1f)

    LaunchedEffect(overlay.x, overlay.y, overlay.sizeScale) {
        posX = overlay.x
        posY = overlay.y
        currentScale = overlay.sizeScale
    }

    // Shared animation math — identical to what export bakes.
    val anim = TextAnimEngine.compute(overlay, currentTimeMs)

    // Compute keyframe interpolation if keyframes are defined
    val kfTransform = if (overlay.keyframes.keyframes.isNotEmpty()) {
        overlay.keyframes.interpolateAt(currentTimeMs)
    } else {
        null
    }

    // Compute animated attributes
    val displayAlpha = (overlay.opacity * (kfTransform?.opacity ?: 1f) * anim.alpha).coerceIn(0f, 1f)
    val scaleXAnim = currentScale * (kfTransform?.scale ?: 1f) * anim.scaleX
    val scaleYAnim = currentScale * (kfTransform?.scale ?: 1f) * anim.scaleY
    val offsetXPx = (kfTransform?.translateX ?: 0f) * containerWidthPx + anim.transXFrac * containerWidthPx
    val offsetYPx = (kfTransform?.translateY ?: 0f) * containerHeightPx + anim.transYFrac * containerHeightPx
    val rotationZAnim = overlay.rotationDeg + (kfTransform?.rotationDeg ?: 0f) + anim.rotZDeg
    val cameraDistAnim = 16f * density.density
    val displayText = anim.charsToShow?.let { overlay.text.take(it.coerceIn(0, overlay.text.length)) }
        ?: overlay.text

    // LEFT pins the block's left edge at the anchor, RIGHT the right
    // edge; CENTER keeps the classic centred behaviour. The content
    // box is centred on the anchor by layout, so shift it by half the
    // measured text width to move the correct edge onto the anchor.
    val alignShiftDp = with(density) {
        when (overlay.textAlign.uppercase()) {
            "LEFT" -> (textWidthPx / 2f).toDp()
            "RIGHT" -> (-textWidthPx / 2f).toDp()
            else -> 0.dp
        }
    }
    val offsetXDp = with(density) { offsetXPx.toDp() }
    val offsetYDp = with(density) { offsetYPx.toDp() }

    // Bundled OFL fonts ship real bold/italic faces, so the typeface
    // already carries the weight — don't double up with synthetic
    // Compose styling. System families still use B/I styling.
    val isBundledFont = remember(overlay.fontFamily) {
        TextFontRegistry.find(overlay.fontFamily)?.regularAsset != null
    }
    val fontFam = rememberTextFontFamily(overlay.fontFamily, overlay.isBold, overlay.isItalic)

    val textColor = Color(overlay.colorArgb.toInt())
    val shadow = overlay.shadowColorArgb?.let {
        Shadow(color = Color(it.toInt()), offset = Offset(2f, 2f), blurRadius = 4f)
    }
    val gradientBrush = run {
        val s = overlay.gradientStartArgb
        val e = overlay.gradientEndArgb
        if (s != null && e != null) {
            Brush.verticalGradient(listOf(Color(s.toInt()), Color(e.toInt())))
        } else null
    }

    val fontSizeSp = (20f * ((scaleXAnim + scaleYAnim) / 2f)).coerceIn(10f, 80f)
    val blurDp = with(density) { (anim.blurFrac * containerHeightPx).toDp() }

    Box(
        modifier = modifier
            .offset(
                x = containerWidth * posX - 48.dp + offsetXDp + alignShiftDp,
                y = containerHeight * posY - 24.dp + offsetYDp
            )
            .pointerInput(overlay.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val newX = (posX + dragAmount.x / containerWidthPx).coerceIn(0.05f, 0.95f)
                    val newY = (posY + dragAmount.y / containerHeightPx).coerceIn(0.05f, 0.95f)
                    posX = newX
                    posY = newY
                    onPositionChange(newX, newY)
                }
            }
            .pointerInput(overlay.id) {
                detectTapGestures(
                    onTap = { onSelect() },
                    onDoubleTap = {
                        onSelect()
                        onEditText()
                    }
                )
            }
            .padding(10.dp) // Leave room for corner handles
    ) {
        // Main Text Box with Border and Background
        Box(
            modifier = Modifier
                .graphicsLayer {
                    alpha = displayAlpha
                    rotationX = anim.rotXDeg
                    rotationY = anim.rotYDeg
                    rotationZ = rotationZAnim
                    scaleX = scaleXAnim
                    scaleY = scaleYAnim
                    cameraDistance = cameraDistAnim
                    shadowElevation = if (anim.rotXDeg != 0f || anim.rotYDeg != 0f) 8f else 0f
                }
                .then(
                    if (anim.blurFrac > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(blurDp)
                    } else Modifier
                )
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (overlay.bgArgb != null) Color(overlay.bgArgb.toInt())
                    else Color.Transparent
                )
                .border(
                    width = if (isSelected) 1.5.dp else 0.dp,
                    color = if (isSelected) ApexPalette.NeonCyan else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = displayText.ifEmpty { " " },
                color = if (gradientBrush == null) textColor else Color.Unspecified,
                fontSize = fontSizeSp.sp,
                fontWeight = if (!isBundledFont && overlay.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (!isBundledFont && overlay.isItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = fontFam,
                style = TextStyle(
                    brush = gradientBrush,
                    shadow = shadow,
                    letterSpacing = overlay.letterSpacingEm.coerceIn(-0.2f, 1f).sp
                ),
                onTextLayout = { textWidthPx = it.size.width.toFloat() }
            )
        }

        // CapCut-Style Interactive Corner Badges when selected
        if (isSelected) {
            // Top-Left: Delete (✕)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-6).dp, y = (-6).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE11D48))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete Text",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }

            // Top-Right: Quick Edit (✎)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.NeonCyan)
                    .clickable { onEditText() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Text Content",
                    tint = Color.Black,
                    modifier = Modifier.size(13.dp)
                )
            }

            // Bottom-Left: Duplicate (⧉)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-6).dp, y = 6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6366F1))
                    .clickable { onDuplicate() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Duplicate Text",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }

            // Bottom-Right: Resize Handle (⤢)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.NeonCyan)
                    .pointerInput(overlay.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val delta = (dragAmount.x + dragAmount.y) / 80f
                            val newScale = (currentScale + delta).coerceIn(0.4f, 4.0f)
                            currentScale = newScale
                            onSizeScaleChange(newScale)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Resize Text Size",
                    tint = Color.Black,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
