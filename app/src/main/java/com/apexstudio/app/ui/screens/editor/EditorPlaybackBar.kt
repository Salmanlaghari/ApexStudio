package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.ApexPalette

// === 3. PLAYBACK CONTROL BAR (mockup transport row) ===
// Layout matches the approved mockup exactly:
// Left: Undo (dim when unavailable) + Redo (cyan when available).
// Center: BIG circular play button with blue gradient.
// Right of play: keyframe diamond with "+" badge — ALWAYS visible in the
// new layout (tapping with no clip selected is a safe no-op; the call site
// null-checks the clip). Far right: fullscreen corners icon.
// No time counter and no prev/next buttons here: the time pill lives inside
// the preview ("REC • 00:20 / 00:25") per the mockup.
@Composable
fun PlaybackControlBar(
    isPlaying: Boolean = false,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    showKeyframeButton: Boolean = false,
    hasKeyframeAtPlayhead: Boolean = false,
    onTogglePlay: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onToggleKeyframe: () -> Unit = {},
    onFullscreenToggle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Undo + Redo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) ApexPalette.NeonCyan else Color(0xFF4B5563),
                modifier = Modifier
                    .size(24.dp)
                    .clickable(enabled = canUndo, onClick = onUndo)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) ApexPalette.NeonCyan else Color(0xFF4B5563),
                modifier = Modifier
                    .size(24.dp)
                    .clickable(enabled = canRedo, onClick = onRedo)
            )
        }

        // Center: BIG blue-gradient play button (mockup)
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(ApexPalette.NeonCyan, ApexPalette.NeonCyanGlow),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }

        // Right: keyframe diamond+ (always visible) + fullscreen
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            if (showKeyframeButton) {
                KeyframeDiamondAddButton(
                    hasKeyframeAtPlayhead = hasKeyframeAtPlayhead,
                    onClick = onToggleKeyframe
                )
            }

            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onFullscreenToggle)
            )
        }
    }
}

/**
 * Mockup keyframe control: white diamond outline with a cyan "+" badge
 * centered inside. Tints cyan while a keyframe sits at the playhead.
 */
@Composable
private fun KeyframeDiamondAddButton(
    hasKeyframeAtPlayhead: Boolean,
    onClick: () -> Unit
) {
    val diamondColor = if (hasKeyframeAtPlayhead) ApexPalette.NeonCyan else Color.White
    Box(
        modifier = Modifier
            .size(30.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.width / 2f - 3.dp.toPx()
            val diamond = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r, cy)
                lineTo(cx, cy + r)
                lineTo(cx - r, cy)
                close()
            }
            drawPath(diamond, color = diamondColor, style = Stroke(width = 2.2.dp.toPx()))
        }
        Text(
            text = "+",
            color = ApexPalette.NeonCyan,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

enum class SelectedLayerType {
    NONE,
    OVERLAY_V2,
    VIDEO_V1,
    TEXT_TXT,
    FX_LAYER,
    AUDIO_A1
}
