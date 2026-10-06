package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat

// === 3. PLAYBACK CONTROL BAR (CapCut transport layout) ===
// Center: Prev / Play-Pause / Next. Right cluster: Undo, Redo,
// Keyframe diamond (+) — visible only while a clip is selected,
// exactly like CapCut — then Fullscreen.
@Composable
fun PlaybackControlBar(
    currentTimeMs: Long = 4370L,
    totalDurationMs: Long = 18690L,
    isPlaying: Boolean = false,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    showKeyframeButton: Boolean = false,
    hasKeyframeAtPlayhead: Boolean = false,
    onTogglePlay: () -> Unit = {},
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onToggleKeyframe: () -> Unit = {},
    onFullscreenToggle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Time counter matching reference image (e.g. 00:12 / 00:28)
        Text(
            text = "${TimeFormat.msToShort(currentTimeMs)} / ${TimeFormat.msToShort(totalDurationMs)}",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )

        // Center Playback Icons: Previous, Play/Pause, Next
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous Clip",
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onPrev)
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next Clip",
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onNext)
            )
        }

        // Right Icons: Undo, Redo, Keyframe diamond (on selection), Fullscreen
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) Color.White else Color(0xFF4B5563),
                modifier = Modifier
                    .size(20.dp)
                    .clickable(enabled = canUndo, onClick = onUndo)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) Color.White else Color(0xFF4B5563),
                modifier = Modifier
                    .size(20.dp)
                    .clickable(enabled = canRedo, onClick = onRedo)
            )

            // CapCut-style keyframe diamond: appears next to the arrows
            // only while a clip is selected; adds a REAL keyframe at the
            // playhead via toggleKeyframeAtPlayheadFor.
            if (showKeyframeButton) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (hasKeyframeAtPlayhead) ApexPalette.NeonCyan.copy(alpha = 0.25f)
                            else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (hasKeyframeAtPlayhead) ApexPalette.NeonCyan else Color(0xFF6B7280),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(onClick = onToggleKeyframe),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Diamond,
                        contentDescription = if (hasKeyframeAtPlayhead) "Remove keyframe at playhead" else "Add keyframe at playhead",
                        tint = if (hasKeyframeAtPlayhead) ApexPalette.NeonCyan else Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onFullscreenToggle)
            )
        }
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

