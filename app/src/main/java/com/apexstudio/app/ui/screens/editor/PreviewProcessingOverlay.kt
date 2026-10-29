package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

/** A legible, screen-reader-announced processing state centered on the preview itself. */
@Composable
internal fun PreviewProcessingOverlay(
    title: String,
    status: String,
    progress: Float?,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    val safeProgress = progress?.coerceIn(0f, 1f)
    val accessibilityStatus = if (safeProgress == null) "$title. $status" else
        "$title. $status. ${(safeProgress * 100).toInt()} percent complete"
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityStatus
                liveRegion = LiveRegionMode.Polite
                safeProgress?.let { progressBarRangeInfo = ProgressBarRangeInfo(it, 0f..1f) }
            }
            .testTag("preview_processing_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xF20B1020),
            contentColor = ApexPalette.TextPrimary,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 280.dp).padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (safeProgress == null) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp), color = ApexPalette.NeonCyan)
                } else {
                    CircularProgressIndicator(
                        progress = { safeProgress },
                        modifier = Modifier.size(48.dp),
                        color = ApexPalette.NeonCyan,
                        trackColor = ApexPalette.NeonCyan.copy(alpha = 0.22f),
                        strokeWidth = 5.dp,
                    )
                }
                Text(title, color = ApexPalette.TextPrimary)
                Text(status, color = ApexPalette.TextPrimary)
                if (safeProgress != null) {
                    Text("${(safeProgress * 100).toInt()}%", color = ApexPalette.NeonCyan)
                }
                onCancel?.let { cancel ->
                    TextButton(onClick = cancel, modifier = Modifier.testTag("preview_processing_cancel")) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
