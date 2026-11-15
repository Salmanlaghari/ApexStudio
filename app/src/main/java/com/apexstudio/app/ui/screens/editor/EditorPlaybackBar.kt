package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    showInlineKeyframe: Boolean = true,
    keyframeUnavailableDescription: String = "Keyframe editing unavailable"
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(EditorLayoutMetrics.PlaybackBarHeight)
            .background(EditorPalette.BgBase)
            .testTag("editor-playback-controls")
    ) {
        val compact = maxWidth < 360.dp
        val iconWidth = if (compact) 34.dp else 38.dp
        val playWidth = if (compact) 40.dp else 44.dp
        val sideBalanceWidth = 48.dp - iconWidth

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = EditorPalette.TextPrimary, fontWeight = FontWeight.SemiBold)) {
                    append(TimeFormat.msToShort(currentTimeMs))
                }
                withStyle(SpanStyle(color = EditorPalette.TextSecondary)) {
                    append(" / ${TimeFormat.msToShort(durationMs)}")
                }
            },
            color = EditorPalette.TextPrimary,
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
                    KeyframeDiamondAddButton(
                        hasKeyframeAtPlayhead,
                        canEditKeyframe,
                        onToggleKeyframe,
                        keyframeUnavailableDescription,
                    )
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
                .background(EditorPalette.BgElevated, CircleShape)
                .border(1.dp, EditorPalette.TrackBlue, CircleShape) else Modifier)
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
                else -> EditorPalette.TextSecondary
            },
            modifier = Modifier.size(if (prominent) 25.dp else 23.dp)
        )
    }
}

@Composable
private fun KeyframeDiamondAddButton(
    hasKeyframeAtPlayhead: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    unavailableDescription: String = "Keyframe editing unavailable",
) {
    val color = if (!enabled) EditorPalette.TextMuted else if (hasKeyframeAtPlayhead) EditorPalette.NeonCyan else EditorPalette.TextSecondary
    Box(
        Modifier.size(width = 48.dp, height = 40.dp)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .testTag("playback-keyframe-button")
            .semantics {
                contentDescription = if (!enabled) unavailableDescription
                else if (hasKeyframeAtPlayhead) "Remove keyframe" else "Add keyframe"
            },
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
