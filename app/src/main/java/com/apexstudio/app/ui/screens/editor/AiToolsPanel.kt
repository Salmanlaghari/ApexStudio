package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.apexstudio.app.data.aireport.AI_REPORT_CONTACT
import com.apexstudio.app.data.aireport.openAiContentReportEmail
import com.apexstudio.app.presentation.viewmodel.AiContentReportUiState
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

/** Inventory is not permission to run a protected action. */
data class AiToolCard(
    val id: String,
    val title: String,
    val detail: String,
    val group: String,
    val statusLabel: String,
    val unavailableReason: String
)

/** Existing-video destinations only; inventory entries without a proven destination stay informational. */
internal enum class AiToolAction { LOCAL_ANALYSIS, VIDEO_UPSCALE, CAPTIONS, BACKGROUND_REPLACE }

internal fun aiToolActionForId(id: String): AiToolAction? = when (id) {
    "SMART_TRIM", "SCENE_ANALYSIS", "STYLE_ANALYSIS", "AUTO_CROP", "FACE_TRACKING" -> AiToolAction.LOCAL_ANALYSIS
    "ONNX_UPSCALING" -> AiToolAction.VIDEO_UPSCALE
    "SUBTITLES", "WORD_TIMED_SUBTITLES" -> AiToolAction.CAPTIONS
    "BACKGROUND_REPLACE" -> AiToolAction.BACKGROUND_REPLACE
    else -> null
}

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiToolsPanel(
    tools: List<AiToolCard>,
    message: String,
    onClose: () -> Unit,
    onOpenLocalAnalysis: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenVideoPresets: (() -> Unit)? = null,
    onOpenVideoUpscale: (() -> Unit)? = null,
    onOpenCaptions: (() -> Unit)? = null,
    onOpenBackgroundReplace: (() -> Unit)? = null,
    reportTarget: AiContentReportTarget?,
    reportState: AiContentReportUiState,
    onSubmitReport: (AiContentReportReason) -> Unit,
    onClearReportFeedback: () -> Unit,
    onComposeReport: ((AiContentReportTarget, AiContentReportReason) -> Boolean)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var emailFeedback by remember { mutableStateOf<String?>(null) }
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
                    Icon(Icons.Default.Download, "Optional AI features", tint = ApexPalette.TextPrimary)
                }
                IconButton(onClick = { showSafetyInfo = true }) {
                    Icon(Icons.Default.Info, "AI availability details", tint = ApexPalette.TextSecondary)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close AI tools", tint = ApexPalette.TextPrimary) }
            }
            Text("Editor tools", modifier = Modifier.padding(horizontal = 16.dp),
            color = ApexPalette.TextPrimary, style = MaterialTheme.typography.labelMedium)
            Text("Choose an editing tool for a video or photo in your project.", modifier = Modifier.padding(horizontal = 16.dp),
                color = ApexPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (onOpenVideoPresets != null) OutlinedButton(onClick = onOpenVideoPresets,
                modifier = Modifier.padding(horizontal = 16.dp).testTag("open_project_video_presets")) {
                Text("Video effects & titles")
            }
            if (onOpenVideoUpscale != null) Button(onClick = onOpenVideoUpscale,
                modifier = Modifier.padding(horizontal = 16.dp).testTag("open_project_video_upscale")) {
                Text("Enhance video")
            }
            if (onOpenLocalAnalysis != null) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Review suggestions", color = ApexPalette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    OutlinedIconButton(onClick = onOpenLocalAnalysis) {
                        Icon(Icons.Default.MovieFilter, LOCAL_REVIEW_ACTION_LABEL)
                    }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = group == null, onClick = { group = null }, label = { Text("All") }) }
                items(tools.map { it.group }.distinct()) { value ->
                    FilterChip(selected = group == value, onClick = { group = value }, label = { Text(value) })
                }
            }
            // One vertical scroll surface: a nested capped lazy grid clipped its
            // lower cards and trapped touch/accessibility scrolling on small screens.
            FlowRow(maxItemsInEachRow = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tools.filter { group == null || it.group == group }.forEach { tool ->
                    val action = when (aiToolActionForId(tool.id)) {
                        AiToolAction.LOCAL_ANALYSIS -> onOpenLocalAnalysis
                        AiToolAction.VIDEO_UPSCALE -> onOpenVideoUpscale
                        AiToolAction.CAPTIONS -> onOpenCaptions
                        AiToolAction.BACKGROUND_REPLACE -> onOpenBackgroundReplace
                        null -> null
                    }
                    val actionLabel = when {
                        action == null -> "View details"
                        aiToolActionForId(tool.id) == AiToolAction.LOCAL_ANALYSIS -> "Review suggestions"
                        aiToolActionForId(tool.id) == AiToolAction.VIDEO_UPSCALE -> "Enhance video"
                        aiToolActionForId(tool.id) == AiToolAction.CAPTIONS -> "Create captions"
                        else -> "Replace background"
                    }
                    Surface(onClick = { action?.invoke() ?: run { selectedTool = tool } },
                        modifier = Modifier.weight(1f).heightIn(min = 88.dp).testTag("protected_ai_card_${tool.id}"),
                        shape = MaterialTheme.shapes.medium, color = ApexPalette.BgElevated) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(aiToolIcon(tool.id), null, modifier = Modifier.size(22.dp), tint = ApexPalette.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { selectedTool = tool }, modifier = Modifier.size(32.dp).testTag("ai_tool_info_${tool.id}")) {
                                    Icon(Icons.Default.Info, "${tool.title} availability details", modifier = Modifier.size(18.dp), tint = ApexPalette.TextSecondary)
                                }
                            }
                            Text(tool.title, color = ApexPalette.TextPrimary, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                            Text(actionLabel, color = ApexPalette.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Report AI content", style = MaterialTheme.typography.titleMedium, color = ApexPalette.TextPrimary)
                Text("Report / Suggestions: $AI_REPORT_CONTACT. Open an email draft with a clip reference and reason; no media attachment and no automatic send. If no email app is available, only a local flag is saved, not sent to the developer.",
                    style = MaterialTheme.typography.bodySmall, color = ApexPalette.TextSecondary)
                Button(onClick = { emailFeedback = null; onClearReportFeedback(); showReportDialog = true }, enabled = reportTarget != null && !reportState.isSaving) {
                    Text("Flag AI content")
                }
                if (reportTarget == null) Text("Select a clip in the current project to create a report reference.",
                    style = MaterialTheme.typography.bodySmall, color = ApexPalette.TextSecondary)
                emailFeedback?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ApexPalette.TextSecondary) }
                reportState.message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (reportState.saved) MaterialTheme.colorScheme.primary else ApexPalette.TextSecondary) }
            }
        }
    }
    if (showReportDialog) AiContentReportDialog(
        target = reportTarget,
        state = reportState,
        onDismiss = { showReportDialog = false },
        onSubmit = { reason ->
            reportTarget?.let { target ->
                val opened = onComposeReport?.invoke(target, reason)
                    ?: openAiContentReportEmail(context, target, reason)
                if (opened) {
                    emailFeedback = "Email draft opened for $AI_REPORT_CONTACT. Review and send it in your email app; delivery has not been confirmed."
                    showReportDialog = false
                } else {
                    emailFeedback = "No email app available. Saving a local-only flag; nothing is sent. Contact $AI_REPORT_CONTACT manually."
                    onSubmitReport(reason)
                }
            }
        }
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
        title = { Text("AI processing availability") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(AI_PROTECTED_BLOCKER)
            Text("Face-framing suggestions come from sample frames and may miss quick movement. Review is available, but applying automatic face crops to video is not available yet.")
            Text("Artistic style filters and 2D-to-3D video conversion are unavailable. A feature shown as unavailable cannot be started here.")
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
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose a reason to open an email draft to $AI_REPORT_CONTACT with the selected project clip${if (target?.textOverlayId != null) " and text overlay" else ""} reference. No media or text content is attached. You must send the email yourself. Without an email app, a local-only flag is saved instead.")
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
                Text(when { state.isSaving -> "Saving local flag…"; state.saved -> "Saved locally — not sent"; else -> "Open email draft" })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Cancel") } }
    )
}
