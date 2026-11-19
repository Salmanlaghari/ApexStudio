package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.ui.theme.EditorPalette

/** Snap/Ripple and the preserved focus action sit at the video-layer viewport's top edge. */
@Composable
internal fun TimelineLayerControls(
    state: EditorState,
    focused: Boolean,
    onToggleFocus: () -> Unit,
    onToggleSnapping: () -> Unit,
    onToggleRippleEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapLabel = if (state.magneticSnapping) "Magnetic snapping enabled" else "Magnetic snapping disabled"
    val rippleLabel = if (state.rippleEditing) "Ripple editing enabled" else "Ripple editing disabled"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EditorLayoutMetrics.TimelineLayerControlsHeight)
            .padding(horizontal = 4.dp)
            .testTag("timeline-layer-controls"),
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
