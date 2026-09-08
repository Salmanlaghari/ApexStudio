package com.apexstudio.app.ui.screens.editor

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Interactive, draggable, and animated text overlay rendered inside [VideoPreviewArea].
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
    modifier: Modifier = Modifier
) {
    var posX by remember(overlay.id) { mutableFloatStateOf(overlay.x) }
    var posY by remember(overlay.id) { mutableFloatStateOf(overlay.y) }

    LaunchedEffect(overlay.x, overlay.y) {
        posX = overlay.x
        posY = overlay.y
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

    // Compute animated attributes
    var displayAlpha = 1f
    var scaleAnim = overlay.sizeScale
    var offsetYAnim = 0f
    var displayText = overlay.text

    when (overlay.animationType.uppercase()) {
        "FADE" -> {
            displayAlpha = progress
        }
        "POP" -> {
            val easeOutBack = progress * progress * (2.7f * progress - 1.7f)
            scaleAnim = overlay.sizeScale * (0.3f + 0.7f * easeOutBack.coerceIn(0f, 1.15f))
            displayAlpha = progress.coerceIn(0f, 1f)
        }
        "TYPEWRITER" -> {
            val totalChars = overlay.text.length
            val charsToShow = (totalChars * progress).toInt().coerceIn(0, totalChars)
            displayText = overlay.text.take(charsToShow)
        }
        "SLIDE_UP" -> {
            offsetYAnim = (1f - progress) * 35f
            displayAlpha = progress
        }
        "PULSE" -> {
            scaleAnim = overlay.sizeScale * pulseScale
        }
        "BOUNCE" -> {
            val bounceVal = kotlin.math.sin(progress * kotlin.math.PI * 3.0).toFloat()
            offsetYAnim = bounceVal * -12f
        }
        else -> {
            // "NONE" or unknown -> default static
            displayAlpha = 1f
            scaleAnim = overlay.sizeScale
        }
    }

    val fontFam = when (overlay.fontFamily.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    val textColor = Color(overlay.colorArgb.toInt())
    val shadow = overlay.shadowColorArgb?.let {
        Shadow(color = Color(it.toInt()), offset = Offset(2f, 2f), blurRadius = 4f)
    }

    Box(
        modifier = modifier
            .offset(
                x = containerWidth * posX - 40.dp,
                y = containerHeight * posY - 20.dp + offsetYAnim.dp
            )
            .graphicsLayer {
                scaleX = scaleAnim
                scaleY = scaleAnim
                alpha = displayAlpha
            }
            .pointerInput(overlay.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val newX = (posX + dragAmount.x / size.width.toFloat()).coerceIn(0.05f, 0.95f)
                    val newY = (posY + dragAmount.y / size.height.toFloat()).coerceIn(0.05f, 0.95f)
                    posX = newX
                    posY = newY
                    onPositionChange(newX, newY)
                }
            }
            .clickable { onSelect() }
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
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = displayText.ifEmpty { " " },
            color = textColor,
            fontSize = (18f * overlay.sizeScale).sp,
            fontWeight = if (overlay.isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (overlay.isItalic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = fontFam,
            style = TextStyle(shadow = shadow)
        )
    }
}
