package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

/** Keep the preview substantial in normal portrait editing; focus mode can expand the timeline. */
internal fun editorPreviewLayoutWeight(isTimelineFocused: Boolean): Float =
    if (isTimelineFocused) 0.001f else 1f
/** Normal mode keeps layered tracks usable without letting them dominate the preview. */
internal fun timelineTrackLayoutWeight(isTimelineFocused: Boolean): Float =
    if (isTimelineFocused) 3f else 1.2f

/**
 * Persistent, discoverable mode switch. Keeping it beside transport makes the
 * expanded timeline reversible without changing selection or playback state.
 */
@Composable
internal fun TimelineFocusModeControl(
    isFocused: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (isFocused) "Timeline focus" else "Taller editing view",
            modifier = Modifier.weight(1f),
            color = ApexPalette.TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(
            onClick = onToggle,
            modifier = Modifier.semantics {
                contentDescription = if (isFocused) {
                    "Restore preview and normal timeline layout"
                } else {
                    "Expand timeline to full height"
                }
            }
        ) {
            Text(
                text = if (isFocused) "Show preview" else "Expand timeline",
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}
