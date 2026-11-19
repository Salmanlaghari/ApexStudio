package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.ui.theme.EditorPalette
import java.util.Locale

/** Compact timeline metadata with a single row of project-backed phone controls. */
@Composable
internal fun ProfessionalTimelineToolbar(
    state: EditorState,
    focused: Boolean,
    onToggleFocus: () -> Unit,
    onToggleSnapping: () -> Unit,
    onToggleRippleEditing: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timecode = preciseEditorTime(state.playerPositionMs)
    val zoomLabel = String.format(
        Locale.ROOT,
        if (state.timelineZoom < 0.1f) "%.2fx" else "%.1fx",
        state.timelineZoom
    )
    val snapLabel = if (state.magneticSnapping) "Magnetic snapping enabled" else "Magnetic snapping disabled"
    val rippleLabel = if (state.rippleEditing) "Ripple editing enabled" else "Ripple editing disabled"
    val surfaceShape = RoundedCornerShape(13.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(EditorLayoutMetrics.TimelineToolbarHeight)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(surfaceShape)
            .background(EditorPalette.GradientSurface)
            .border(1.dp, EditorPalette.BorderGlass, surfaceShape)
            .padding(horizontal = 4.dp)
            .testTag("professional-timeline-toolbar"),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Timeline playhead at $timecode" },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "TIMELINE",
                    color = EditorPalette.TextTertiary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                )
                Text(
                    text = timecode,
                    color = EditorPalette.TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TimelineModeToggle(
                text = "Snap",
                enabled = state.magneticSnapping,
                description = snapLabel,
                testTag = "timeline-snap-toggle",
                onClick = onToggleSnapping,
                modifier = Modifier.widthIn(min = 48.dp),
            )
            TimelineModeToggle(
                text = if (state.rippleEditing) "Ripple on" else "Ripple off",
                enabled = state.rippleEditing,
                description = rippleLabel,
                testTag = "timeline-ripple-toggle",
                onClick = onToggleRippleEditing,
                modifier = Modifier.widthIn(min = 48.dp),
            )

            // Keep the two zoom actions easy to hit without letting the
            // controls consume the entire timeline width.
            Row(
                modifier = Modifier
                    .width(120.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(EditorPalette.BgSurface)
                    .border(1.dp, EditorPalette.BorderGlass, RoundedCornerShape(11.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                TimelineToolbarIconButton(Icons.Default.ZoomOut, "Zoom timeline out", onZoomOut)
                Text(
                    text = zoomLabel,
                    color = EditorPalette.TextPrimary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.width(32.dp),
                )
                TimelineToolbarIconButton(Icons.Default.ZoomIn, "Zoom timeline in", onZoomIn)
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (focused) EditorPalette.BgElevated else EditorPalette.BgSurface)
                    .border(
                        1.dp,
                        if (focused) EditorPalette.TrackBlue.copy(alpha = 0.72f) else EditorPalette.BorderGlass,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(role = Role.Button, onClick = onToggleFocus)
                    .semantics {
                        contentDescription = if (focused) {
                            "Restore preview and normal timeline layout"
                        } else {
                            "Expand timeline to full height"
                        }
                    }
                    .testTag("timeline-focus-toggle"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (focused) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = if (focused) EditorPalette.NeonCyan else EditorPalette.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun TimelineModeToggle(
    text: String,
    enabled: Boolean,
    description: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (enabled) EditorPalette.BgElevated else EditorPalette.BgSurface)
            .border(
                1.dp,
                if (enabled) EditorPalette.TrackBlue.copy(alpha = 0.72f) else EditorPalette.BorderGlass,
                shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(testTag)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(if (enabled) EditorPalette.TrackBlue else EditorPalette.TextMuted),
        )
        Text(
            text = text,
            color = if (enabled) EditorPalette.TextPrimary else EditorPalette.TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun TimelineToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = EditorPalette.TextSecondary, modifier = Modifier.size(17.dp))
    }
}
