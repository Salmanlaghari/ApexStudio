package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.aireport.AiContentReportReason
import com.apexstudio.app.data.aireport.AiContentReportTarget
import com.apexstudio.app.presentation.viewmodel.AiContentReportUiState
import com.apexstudio.app.ui.theme.ApexPalette

/** Inventory is not permission to run a protected action. */
data class AiToolCard(
    val id: String,
    val title: String,
    val detail: String,
    val group: String,
    val statusLabel: String,
    val unavailableReason: String
)

private fun aiToolIcon(id: String): ImageVector = when {
    id.contains("CAPTION") || id.contains("WHISPER") -> Icons.Default.Subtitles
    id.contains("CROP") || id.contains("REFRAME") -> Icons.Default.Crop
    id.contains("TRIM") -> Icons.Default.ContentCut
    id.contains("SCENE") -> Icons.Default.MovieFilter
    id.contains("GESTURE") || id.contains("HAND") -> Icons.Default.PanTool
    id.contains("BACKGROUND") || id.contains("SEGMENT") -> Icons.Default.Person
    id.contains("STYLE") -> Icons.Default.Palette
    id.contains("UPSCAL") -> Icons.Default.HighQuality
    id.contains("FACE") -> Icons.Default.Face
    else -> Icons.Default.AutoAwesome
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiToolsPanel(
    tools: List<AiToolCard>,
    message: String,
    onClose: () -> Unit,
    onOpenLocalAnalysis: (() -> Unit)? = null,
    onOpenGestures: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    reportTarget: AiContentReportTarget?,
    reportState: AiContentReportUiState,
    onSubmitReport: (AiContentReportReason) -> Unit,
    onClearReportFeedback: () -> Unit
) {
    var group by remember { mutableStateOf<String?>(null) }
    var selectedTool by remember { mutableStateOf<AiToolCard?>(null) }
    var showSafetyInfo by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, containerColor = ApexPalette.BgSurface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("AI tools", style = MaterialTheme.typography.titleLarge, color = ApexPalette.TextPrimary, modifier = Modifier.weight(1f))
                if (onOpenDownloads != null) IconButton(onClick = onOpenDownloads) {
                    Icon(Icons.Default.Download, "Download optional AI models", tint = ApexPalette.TextPrimary)
                }
                IconButton(onClick = { showSafetyInfo = true }) {
                    Icon(Icons.Default.Info, "AI availability details", tint = ApexPalette.TextSecondary)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close AI tools", tint = ApexPalette.TextPrimary) }
            }
            Text("Protected AI paused · safety approval required", modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            if (onOpenLocalAnalysis != null || onOpenGestures != null) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Local tools", color = ApexPalette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    if (onOpenLocalAnalysis != null) OutlinedIconButton(onClick = onOpenLocalAnalysis) {
                        Icon(Icons.Default.MovieFilter, LOCAL_REVIEW_ACTION_LABEL)
                    }
                    if (onOpenGestures != null) OutlinedIconButton(onClick = onOpenGestures) {
                        Icon(Icons.Default.PanTool, "Camera gesture controls")
                    }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = group == null, onClick = { group = null }, label = { Text("All") }) }
                items(tools.map { it.group }.distinct()) { value ->
                    FilterChip(selected = group == value, onClick = { group = value }, label = { Text(value) })
                }
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(72.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tools.filter { group == null || it.group == group }, key = { it.id }) { tool ->
                    Surface(onClick = { selectedTool = tool }, modifier = Modifier.aspectRatio(1f).testTag("protected_ai_card_${tool.id}"),
                        shape = MaterialTheme.shapes.medium, color = ApexPalette.BgElevated) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(aiToolIcon(tool.id), "${tool.title}; unavailable, tap for details", modifier = Modifier.size(28.dp), tint = ApexPalette.TextSecondary)
                            Icon(Icons.Default.Lock, null, modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp).size(12.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Report AI content", style = MaterialTheme.typography.titleMedium, color = ApexPalette.TextPrimary)
                Text("Reports are saved on this device only. They are not sent to the developer and do not constitute a developer submission.",
                    style = MaterialTheme.typography.bodySmall, color = ApexPalette.TextSecondary)
                Button(onClick = { onClearReportFeedback(); showReportDialog = true }, enabled = reportTarget != null && !reportState.isSaving) {
                    Text("Flag AI content")
                }
                if (reportTarget == null) Text("Select a clip in the current project to create a report reference.",
                    style = MaterialTheme.typography.bodySmall, color = ApexPalette.TextSecondary)
                reportState.message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (reportState.saved) MaterialTheme.colorScheme.primary else ApexPalette.TextSecondary) }
            }
        }
    }
    if (showReportDialog) AiContentReportDialog(
        target = reportTarget,
        state = reportState,
        onDismiss = { showReportDialog = false },
        onSubmit = onSubmitReport
    )
    selectedTool?.let { tool ->
        AlertDialog(onDismissRequest = { selectedTool = null },
            icon = { Icon(aiToolIcon(tool.id), null) },
            title = { Text(tool.title) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tool.statusLabel, color = MaterialTheme.colorScheme.error)
                Text(tool.unavailableReason)
                Text(tool.detail, style = MaterialTheme.typography.bodySmall)
            } },
            dismissButton = { if (onOpenDownloads != null) TextButton(onClick = {
                selectedTool = null
                onOpenDownloads()
            }) { Text("Optional models") } },
            confirmButton = { TextButton(onClick = { selectedTool = null }) { Text("Close") } })
    }
    if (showSafetyInfo) AlertDialog(onDismissRequest = { showSafetyInfo = false },
        title = { Text("Protected AI is unavailable") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(AI_PROTECTED_BLOCKER)
            Text("Local review tools are separate. Detecting or downloading a model does not authorize protected AI.")
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { showSafetyInfo = false }) { Text("Close") } })
}

@Composable
private fun AiContentReportDialog(
    target: AiContentReportTarget?,
    state: AiContentReportUiState,
    onDismiss: () -> Unit,
    onSubmit: (AiContentReportReason) -> Unit
) {
    var selectedReason by remember { mutableStateOf<AiContentReportReason?>(null) }
    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text("Flag AI content") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose the reason. A reference to the selected project clip${if (target?.textOverlayId != null) " and selected text overlay" else ""} and your reason will be stored on this device. No media or text is copied or uploaded.")
                AiContentReportReason.entries.forEach { reason ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !state.isSaving) { selectedReason = reason },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason }, enabled = !state.isSaving)
                        Text(reason.label, modifier = Modifier.weight(1f))
                    }
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { selectedReason?.let(onSubmit) }, enabled = selectedReason != null && target != null && !state.isSaving && !state.saved) {
                Text(when { state.isSaving -> "Saving…"; state.saved -> "Saved"; else -> "Save on device" })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Cancel") } }
    )
}
