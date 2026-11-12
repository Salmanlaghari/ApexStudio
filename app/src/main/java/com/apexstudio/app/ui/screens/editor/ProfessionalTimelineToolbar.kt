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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.presentation.state.EditorState
import java.util.Locale

/** Compact, project-backed controls for the editor's real timeline. */
@Composable
internal fun ProfessionalTimelineToolbar(
    state: EditorState,
    focused: Boolean,
    onToggleFocus: () -> Unit,
    onToggleSnapping: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timecode = preciseEditorTime(state.playerPositionMs)
    val zoomLabel = String.format(Locale.ROOT, "%.1fx", state.timelineZoom)
    val snapLabel = if (state.magneticSnapping) "Magnetic snapping enabled" else "Magnetic snapping disabled"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EditorLayoutMetrics.TimelineToolbarHeight)
            .background(Color(0xFF111820))
            .padding(horizontal = 7.dp)
            .testTag("professional-timeline-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 68.dp)
                .semantics { contentDescription = "Timeline playhead at $timecode" },
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "TIMELINE",
                color = Color(0xFF81909D),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
            )
            Text(
                text = timecode,
                color = Color(0xFFCBD4DC),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (state.magneticSnapping) Color(0xFF243640) else Color(0xFF18212A))
                .border(
                    1.dp,
                    if (state.magneticSnapping) Color(0xFF4B6573) else Color(0xFF33414D),
                    RoundedCornerShape(9.dp),
                )
                .clickable(onClick = onToggleSnapping)
                .semantics { contentDescription = snapLabel }
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (state.magneticSnapping) Color(0xFF94B5C2) else Color(0xFF73818C)),
            )
            Text(
                text = "Snap",
                color = if (state.magneticSnapping) Color(0xFFD0DDE3) else Color(0xFF9CA9B3),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }

        Row(
            modifier = Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF18212A))
                .border(1.dp, Color(0xFF33414D), RoundedCornerShape(9.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimelineToolbarIconButton(Icons.Default.ZoomOut, "Zoom timeline out", onZoomOut)
            Text(
                text = zoomLabel,
                color = Color(0xFFCDD6DE),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.width(31.dp),
            )
            TimelineToolbarIconButton(Icons.Default.ZoomIn, "Zoom timeline in", onZoomIn)
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .then(if (focused) Modifier.background(Color(0xFF26343E)) else Modifier)
                .border(
                    1.dp,
                    if (focused) Color(0xFF607383) else Color(0xFF33414D),
                    RoundedCornerShape(9.dp),
                )
                .clickable(onClick = onToggleFocus)
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
                tint = if (focused) Color(0xFFD2DCE3) else Color(0xFFA4B0BA),
                modifier = Modifier.size(20.dp),
            )
        }
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
            .size(38.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFB0BDC7), modifier = Modifier.size(16.dp))
    }
}
