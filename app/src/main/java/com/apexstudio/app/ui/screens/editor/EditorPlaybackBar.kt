package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.ApexPalette
// === 3. PLAYBACK CONTROL BAR (reference mockup transport row) ===
// Layout matches the reference design:
// Left: Undo + Redo (dim white when unavailable).
// Center: skip-prev / play / skip-next trio, plain white icons.
// Right: keyframe diamond with "+" badge (safe no-op with no clip
// selected) + fullscreen corners icon.
@Composable
fun PlaybackControlBar(
    isPlaying: Boolean = false,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    hasKeyframeAtPlayhead: Boolean = false,
    onTogglePlay: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onToggleKeyframe: () -> Unit = {},
    onFullscreenToggle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Undo + Redo (dim white when unavailable)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) Color.White else Color(0xFF4B5563),
                modifier = Modifier
                    .size(22.dp)
                    .clickable(enabled = canUndo, onClick = onUndo)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) Color.White else Color(0xFF4B5563),
                modifier = Modifier
                    .size(22.dp)
                    .clickable(enabled = canRedo, onClick = onRedo)
            )
        }

        // Center: skip-prev / play / skip-next (mockup transport trio)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onSkipPrevious)
            )
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onTogglePlay)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onSkipNext)
            )
        }

        // Right: keyframe diamond+ + fullscreen
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            KeyframeDiamondAddButton(
                hasKeyframeAtPlayhead = hasKeyframeAtPlayhead,
                onClick = onToggleKeyframe
            )
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
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
    STICKER_STK,
    FX_LAYER,
    AUDIO_A1
}
