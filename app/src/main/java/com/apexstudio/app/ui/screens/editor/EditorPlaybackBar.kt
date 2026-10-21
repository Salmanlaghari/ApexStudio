package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.EditorPalette
import com.apexstudio.app.util.TimeFormat

/** Real transport time stays outside the media, as in the approved editor. */
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
    canEditKeyframe: Boolean = true,
    currentTimeMs: Long = 0L,
    durationMs: Long = 0L,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    canNavigate: Boolean = true,
    showInlineKeyframe: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().height(EditorLayoutMetrics.PlaybackBarHeight).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "${TimeFormat.msToShort(currentTimeMs)} / ${TimeFormat.msToShort(durationMs)}",
            color = EditorPalette.TextPrimary, fontSize = 10.sp, maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        TransportIconButton(Icons.Default.SkipPrevious, "Previous clip boundary", canNavigate, onPrevious)
        TransportIconButton(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (isPlaying) "Pause playback" else "Play playback", canNavigate, onTogglePlay
        )
        TransportIconButton(Icons.Default.SkipNext, "Next clip boundary", canNavigate, onNext)
        TransportIconButton(Icons.AutoMirrored.Filled.Undo, "Undo", canUndo, onUndo)
        TransportIconButton(Icons.AutoMirrored.Filled.Redo, "Redo", canRedo, onRedo)
        if (showInlineKeyframe) KeyframeDiamondAddButton(hasKeyframeAtPlayhead, canEditKeyframe, onToggleKeyframe)
        TransportIconButton(Icons.Default.Fullscreen, "Toggle fullscreen", true, onFullscreenToggle)
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
        Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector, null, tint = if (enabled) EditorPalette.TextPrimary else EditorPalette.TextMuted,
            modifier = Modifier.size(22.dp))
    }
}

/** Only shown for the selected video layer, immediately above the timeline. */
@Composable
internal fun SelectedVideoKeyframeControl(
    hasKeyframeAtPlayhead: Boolean,
    onToggleKeyframe: () -> Unit,
    onOpenAnimation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        KeyframeDiamondAddButton(hasKeyframeAtPlayhead, true, onToggleKeyframe)
        Text("Keyframe", color = EditorPalette.TextPrimary, fontSize = 11.sp)
        Row(
            Modifier.padding(start = 12.dp).height(36.dp)
                .clickable(onClick = onOpenAnimation, role = Role.Button)
                .semantics { contentDescription = "Edit video keyframe animation" },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Animation, null, tint = EditorPalette.TextSecondary, modifier = Modifier.size(18.dp))
            Text("Animation", color = EditorPalette.TextSecondary, fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun KeyframeDiamondAddButton(hasKeyframeAtPlayhead: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = if (!enabled) EditorPalette.TextMuted else if (hasKeyframeAtPlayhead)
        EditorPalette.NeonCyan else EditorPalette.TextPrimary
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .semantics { contentDescription = if (!enabled) "Keyframe editing unavailable" else if (hasKeyframeAtPlayhead) "Remove keyframe" else "Add keyframe" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.width / 2f - 2.dp.toPx()
            drawPath(Path().apply {
                moveTo(cx, cy-r); lineTo(cx+r, cy); lineTo(cx, cy+r); lineTo(cx-r, cy); close()
            }, color, style = Stroke(2.dp.toPx()))
        }
        Text(if (hasKeyframeAtPlayhead) "−" else "+", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

enum class SelectedLayerType { NONE, OVERLAY_V2, VIDEO_V1, TEXT_TXT, STICKER_STK, FX_LAYER, AUDIO_A1 }
