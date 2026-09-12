package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.TextOverlay

/**
 * Screen-level Wording and Objects Container.
 * Renders all wording objects (Text, Subtitles, Stickers, and Cover Titles)
 * directly over the screen layout rather than clipped/constrained inside the video viewport.
 * Fulfills: "Video ke uper jitne be Wording Object hein unko Screen ke uper set karo Video ke uper nahin"
 */
@Composable
fun ScreenWordingObjects(
    stickers: List<StickerOverlay>,
    textOverlays: List<TextOverlay>,
    coverText: String?,
    coverTextStyle: String?,
    selectedTextOverlayId: String?,
    currentTimeMs: Long,
    isPlaying: Boolean,
    onSelectTextOverlay: ((String) -> Unit)? = null,
    onMoveTextOverlay: ((String, Float, Float) -> Unit)? = null,
    onDeleteTextOverlay: ((String) -> Unit)? = null,
    onDuplicateTextOverlay: ((String) -> Unit)? = null,
    onEditTextOverlay: ((String) -> Unit)? = null,
    onSizeScaleChange: ((String, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val screenW = maxWidth
        val screenH = maxHeight

        // 1. Draggable & Resizable Screen Stickers
        for (sticker in stickers) {
            if (!sticker.isActiveAt(currentTimeMs)) continue

            val kf = if (sticker.keyframes.keyframes.isNotEmpty()) sticker.keyframes.interpolateAt(currentTimeMs) else null
            var offsetX by remember(sticker.id) { mutableFloatStateOf(sticker.x) }
            var offsetY by remember(sticker.id) { mutableFloatStateOf(sticker.y) }
            var scale by remember(sticker.id) { mutableFloatStateOf(sticker.sizeScale) }

            Box(
                modifier = Modifier
                    .offset(
                        x = screenW * (offsetX + (kf?.translateX ?: 0f)) - 20.dp,
                        y = screenH * (offsetY + (kf?.translateY ?: 0f)) - 20.dp
                    )
                    .graphicsLayer {
                        scaleX = scale * (kf?.scale ?: 1f)
                        scaleY = scale * (kf?.scale ?: 1f)
                        rotationZ = sticker.rotationDeg + (kf?.rotationDeg ?: 0f)
                        alpha = sticker.opacity * (kf?.opacity ?: 1f)
                    }
                    .pointerInput(sticker.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.3f, 4f)
                            val newX = (offsetX + pan.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val newY = (offsetY + pan.y / size.height.toFloat()).coerceIn(0f, 1f)
                            offsetX = newX
                            offsetY = newY
                        }
                    }
            ) {
                Text(
                    text = sticker.symbolOrUri,
                    fontSize = 32.sp
                )
            }
        }

        // 2. Interactive Animated Text Overlays (Screen Positioned)
        for (overlay in textOverlays) {
            val isSelected = overlay.id == selectedTextOverlayId
            AnimatedTextOverlayView(
                overlay = overlay,
                isSelected = isSelected,
                containerWidth = screenW,
                containerHeight = screenH,
                currentTimeMs = currentTimeMs,
                isPlaying = isPlaying,
                onSelect = { onSelectTextOverlay?.invoke(overlay.id) },
                onPositionChange = { newX, newY ->
                    val dx = newX - overlay.x
                    val dy = newY - overlay.y
                    onMoveTextOverlay?.invoke(overlay.id, dx, dy)
                },
                onSizeScaleChange = { scale ->
                    onSizeScaleChange?.invoke(overlay.id, scale)
                },
                onEditText = {
                    onEditTextOverlay?.invoke(overlay.id)
                },
                onDelete = {
                    onDeleteTextOverlay?.invoke(overlay.id)
                },
                onDuplicate = {
                    onDuplicateTextOverlay?.invoke(overlay.id)
                }
            )
        }

        // 3. Screen Cover Headline Word Banner (if active)
        if (!coverText.isNullOrBlank()) {
            val bgGradient = when (coverTextStyle) {
                "neon" -> listOf(Color(0xFF4A148C), Color(0xFF0D47A1))
                "gold" -> listOf(Color(0xFF3E2723), Color(0xFF212121))
                "cyberpunk" -> listOf(Color(0xFFFFEA00), Color(0xFFFF9100))
                "vlog" -> listOf(Color(0xFFE91E63), Color(0xFF880E4F))
                else -> listOf(Color(0xFFB71C1C), Color(0xFF000000))
            }
            val textColor = when (coverTextStyle) {
                "neon" -> Color(0xFF00F0FF)
                "gold" -> Color(0xFFFFD700)
                "cyberpunk" -> Color.Black
                else -> Color.White
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.horizontalGradient(bgGradient.map { it.copy(alpha = 0.9f) }))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = coverText.uppercase(),
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}
