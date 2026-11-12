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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(EditorLayoutMetrics.PlaybackBarHeight)
            .background(Color(0xFF111820))
            .testTag("editor-playback-controls")
    ) {
        val compact = maxWidth < 360.dp
        val iconWidth = if (compact) 34.dp else 38.dp
        val playWidth = if (compact) 40.dp else 44.dp
        val sideBalanceWidth = 48.dp - iconWidth

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFFCAD4DC), fontWeight = FontWeight.SemiBold)) {
                    append(TimeFormat.msToShort(currentTimeMs))
                }
                withStyle(SpanStyle(color = Color(0xFF7C8A96))) {
                    append(" / ${TimeFormat.msToShort(durationMs)}")
                }
            },
            color = Color(0xFFCAD4DC),
            fontSize = if (compact) 8.sp else 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.TopCenter).testTag("playback-timecode")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(sideBalanceWidth))
                TransportIconButton(Icons.AutoMirrored.Filled.Undo, "Undo", canUndo, onUndo, buttonWidth = iconWidth)
                TransportIconButton(Icons.AutoMirrored.Filled.Redo, "Redo", canRedo, onRedo, buttonWidth = iconWidth)
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportIconButton(Icons.Default.SkipPrevious, "Previous clip boundary", canNavigate, onPrevious, buttonWidth = iconWidth)
                TransportIconButton(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    description = if (isPlaying) "Pause playback" else "Play playback",
                    enabled = canNavigate,
                    onClick = onTogglePlay,
                    buttonWidth = playWidth,
                    prominent = true
                )
                TransportIconButton(Icons.Default.SkipNext, "Next clip boundary", canNavigate, onNext, buttonWidth = iconWidth)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showInlineKeyframe) {
                    KeyframeDiamondAddButton(hasKeyframeAtPlayhead, canEditKeyframe, onToggleKeyframe)
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                TransportIconButton(Icons.Default.Fullscreen, "Toggle fullscreen", true, onFullscreenToggle, buttonWidth = iconWidth)
            }
        }
    }
}

@Composable
private fun TransportIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    buttonWidth: Dp = 38.dp,
    prominent: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(width = buttonWidth, height = 40.dp)
            .padding(2.dp)
            .clip(if (prominent) CircleShape else RoundedCornerShape(8.dp))
            .then(if (prominent) Modifier
                .background(Color(0xFFE4EAF0), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = when {
                !enabled -> EditorPalette.TextMuted
                prominent -> Color(0xFF17212B)
                else -> Color(0xFFB7C3CD)
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
