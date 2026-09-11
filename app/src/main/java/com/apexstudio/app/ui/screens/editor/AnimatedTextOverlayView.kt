package com.apexstudio.app.ui.screens.editor

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Professional CapCut-style interactive, draggable, resizable, and animated text overlay.
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

    val density = LocalDensity.current
    val containerWidthPx = with(density) { containerWidth.toPx() }.coerceAtLeast(1f)
    val containerHeightPx = with(density) { containerHeight.toPx() }.coerceAtLeast(1f)

    LaunchedEffect(overlay.x, overlay.y, overlay.sizeScale) {
        posX = overlay.x
        posY = overlay.y
        currentScale = overlay.sizeScale
    }

    // Animation progress calculation
    val animDuration = overlay.animationDurationMs.coerceAtLeast(200L)
    val elapsed = (currentTimeMs - overlay.startMs).coerceAtLeast(0L)
    val progress = (elapsed.toFloat() / animDuration.toFloat()).coerceIn(0f, 1f)

    // Running pulse animation for looping types
    val infiniteTransition = rememberInfiniteTransition(label = "text_loop")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Compute keyframe interpolation if keyframes are defined
    val kfTransform = if (overlay.keyframes.keyframes.isNotEmpty()) {
        overlay.keyframes.interpolateAt(currentTimeMs)
    } else {
        null
    }

    // Compute animated attributes
    var displayAlpha = overlay.opacity * (kfTransform?.opacity ?: 1f)
    var scaleAnim = currentScale * (kfTransform?.scale ?: 1f)
    var offsetXAnim = (kfTransform?.translateX ?: 0f) * containerWidthPx
    var offsetYAnim = (kfTransform?.translateY ?: 0f) * containerHeightPx
    var rotationAnim = overlay.rotationDeg + (kfTransform?.rotationDeg ?: 0f)
    var displayText = overlay.text

    when (overlay.animationType.uppercase()) {
        "FADE" -> {
            displayAlpha *= progress
        }
        "POP" -> {
            val easeOutBack = progress * progress * (2.7f * progress - 1.7f)
            scaleAnim *= (0.3f + 0.7f * easeOutBack.coerceIn(0f, 1.15f))
            displayAlpha *= progress.coerceIn(0f, 1f)
        }
        "TYPEWRITER" -> {
            val totalChars = overlay.text.length
            val charsToShow = (totalChars * progress).toInt().coerceIn(0, totalChars)
            displayText = overlay.text.take(charsToShow)
        }
        "SLIDE_UP" -> {
            offsetYAnim += (1f - progress) * 35f
            displayAlpha *= progress
        }
        "PULSE" -> {
            scaleAnim *= pulseScale
        }
        "BOUNCE" -> {
            val bounceVal = kotlin.math.sin(progress * kotlin.math.PI * 3.0).toFloat()
            offsetYAnim += bounceVal * -12f
        }
        else -> {
            // Default: keyframe transform attributes already applied
        }
    }

    val fontFam = when (overlay.fontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive", "script" -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    val textColor = Color(overlay.colorArgb.toInt())
    val shadow = overlay.shadowColorArgb?.let {
        Shadow(color = Color(it.toInt()), offset = Offset(2f, 2f), blurRadius = 4f)
    }

    val fontSizeSp = (20f * scaleAnim).coerceIn(10f, 80f)

    Box(
        modifier = modifier
            .offset(
                x = containerWidth * posX - 48.dp + containerWidth * (kfTransform?.translateX ?: 0f),
                y = containerHeight * posY - 24.dp + (containerHeight * (kfTransform?.translateY ?: 0f)) + offsetYAnim.dp
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
                    rotationZ = rotationAnim
                }
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
                color = textColor,
                fontSize = fontSizeSp.sp,
                fontWeight = if (overlay.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (overlay.isItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = fontFam,
                style = TextStyle(shadow = shadow)
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
