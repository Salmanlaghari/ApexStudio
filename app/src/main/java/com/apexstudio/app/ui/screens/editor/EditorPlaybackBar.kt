package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.EditorPalette
import com.apexstudio.app.util.TimeFormat

/** Flat transport row beneath the preview, wired to the editor's real actions. */
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
        modifier = Modifier
            .fillMaxWidth()
            .height(EditorLayoutMetrics.PlaybackBarHeight)
            .background(EditorPalette.BgBase)
            .testTag("editor-playback-controls")
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = EditorPalette.NeonCyan, fontWeight = FontWeight.SemiBold)) {
                    append(TimeFormat.msToShort(currentTimeMs))
                }
                withStyle(SpanStyle(color = EditorPalette.TextMuted)) {
                    append(" / ${TimeFormat.msToShort(durationMs)}")
                }
            },
            color = EditorPalette.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f).padding(start = 3.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TransportIconButton(Icons.Default.SkipPrevious, "Previous clip boundary", canNavigate, onPrevious)
            TransportIconButton(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                description = if (isPlaying) "Pause playback" else "Play playback",
                enabled = canNavigate,
                onClick = onTogglePlay,
                prominent = true
            )
            TransportIconButton(Icons.Default.SkipNext, "Next clip boundary", canNavigate, onNext)
            TransportIconButton(Icons.AutoMirrored.Filled.Undo, "Undo", canUndo, onUndo)
            TransportIconButton(Icons.AutoMirrored.Filled.Redo, "Redo", canRedo, onRedo)
            if (showInlineKeyframe) {
                KeyframeDiamondAddButton(hasKeyframeAtPlayhead, canEditKeyframe, onToggleKeyframe)
            }
            TransportIconButton(Icons.Default.Fullscreen, "Toggle fullscreen", true, onFullscreenToggle)
        }
    }
}

@Composable
private fun TransportIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    prominent: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 40.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (prominent) Modifier.background(EditorPalette.NeonPurple)
                .border(1.dp, EditorPalette.NeonPurple, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = when {
                !enabled -> EditorPalette.TextMuted
                prominent -> EditorPalette.TextPrimary
                else -> EditorPalette.TextPrimary
            },
            modifier = Modifier.size(if (prominent) 25.dp else 23.dp)
        )
    }
}

/** Only shown for the selected video layer, immediately above the timeline. */
@Composable
internal fun SelectedVideoKeyframeControl(
    hasKeyframeAtPlayhead: Boolean,
    onToggleKeyframe: () -> Unit,
    onOpenAnimation: () -> Unit,
    onOpenPosition: () -> Unit = {},
    onOpenScale: () -> Unit = {},
    onOpenRotation: () -> Unit = {},
    onOpenOpacity: () -> Unit = {},
    onPreviousFrame: (() -> Unit)? = null,
    onNextFrame: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Historical single-row transport: keep the preview/timeline space, with
    // newer property shortcuts reachable horizontally rather than a second card.
    Row(
        modifier = modifier.fillMaxWidth().height(44.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeyframeDiamondAddButton(hasKeyframeAtPlayhead, true, onToggleKeyframe)
            Text("Keyframe", color = EditorPalette.TextPrimary, fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onToggleKeyframe, role = Role.Button))
        }
        Row(
            Modifier.height(40.dp).clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onOpenAnimation, role = Role.Button)
                .semantics { contentDescription = "Edit video keyframe animation" }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Link, null, tint = EditorPalette.TextSecondary, modifier = Modifier.size(17.dp))
            Text("Animation", color = EditorPalette.TextSecondary, fontSize = 11.sp,
                modifier = Modifier.padding(start = 3.dp))
        }
        SelectedVideoPropertyButton("Position", onOpenPosition, Modifier.width(72.dp).height(36.dp))
        SelectedVideoPropertyButton("Scale", onOpenScale, Modifier.width(64.dp).height(36.dp))
        SelectedVideoPropertyButton("Rotation", onOpenRotation, Modifier.width(72.dp).height(36.dp))
        SelectedVideoPropertyButton("Opacity", onOpenOpacity, Modifier.width(72.dp).height(36.dp))
        onPreviousFrame?.let { SelectedVideoPropertyButton("−1f", it, Modifier.width(56.dp).height(36.dp)) }
        onNextFrame?.let { SelectedVideoPropertyButton("+1f", it, Modifier.width(56.dp).height(36.dp)) }
    }
}

@Composable
private fun SelectedVideoPropertyButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(6.dp))
            .background(EditorPalette.BgBase)
            .border(1.dp, EditorPalette.BorderGlass, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = "Open $label keyframe controls" },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = EditorPalette.TextSecondary, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun KeyframeDiamondAddButton(hasKeyframeAtPlayhead: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = if (!enabled) EditorPalette.TextMuted else if (hasKeyframeAtPlayhead) EditorPalette.NeonCyan else EditorPalette.TextSecondary
    Box(
        Modifier.size(width = 48.dp, height = 40.dp).clickable(enabled = enabled, onClick = onClick, role = Role.Button)
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
