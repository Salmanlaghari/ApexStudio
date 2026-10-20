package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.ApexPalette

// Compact transport controls with consistent touch targets and stateful undo/redo.
@Composable
fun PlaybackControlBar(
    isPlaying: Boolean = false,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    hasKeyframeAtPlayhead: Boolean = false,
    onTogglePlay: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onToggleKeyframe: () -> Unit = {},
    onFullscreenToggle: () -> Unit = {},
    canEditKeyframe: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TransportIconButton(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                description = "Undo",
                enabled = canUndo,
                onClick = onUndo
            )
            TransportIconButton(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                description = "Redo",
                enabled = canRedo,
                onClick = onRedo
            )
        }

        Box(
            modifier = Modifier
                .width(52.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE5E7EB))
                .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(12.dp))
                .clickable(onClick = onTogglePlay, role = Role.Button)
                .semantics { contentDescription = if (isPlaying) "Pause playback" else "Play playback" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF111827),
                modifier = Modifier.size(25.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            KeyframeDiamondAddButton(
                hasKeyframeAtPlayhead = hasKeyframeAtPlayhead,
                enabled = canEditKeyframe,
                onClick = onToggleKeyframe
            )
            TransportIconButton(
                imageVector = Icons.Default.Fullscreen,
                description = "Toggle fullscreen",
                onClick = onFullscreenToggle
            )
        }
    }
}

@Composable
private fun TransportIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = if (enabled) Color(0xFF111827) else Color(0xFF9CA3AF),
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun KeyframeDiamondAddButton(
    hasKeyframeAtPlayhead: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val diamondColor = when {
        !enabled -> Color(0xFF9CA3AF)
        hasKeyframeAtPlayhead -> Color(0xFF2563EB)
        else -> Color(0xFF111827)
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .semantics { contentDescription = if (!enabled) "Keyframe editing unavailable" else if (hasKeyframeAtPlayhead) "Remove keyframe" else "Add keyframe" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.width / 2f - 2.dp.toPx()
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
            color = if (!enabled) Color(0xFF9CA3AF) else if (hasKeyframeAtPlayhead) Color(0xFF111827) else Color(0xFF2563EB),
            fontSize = 14.sp,
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
