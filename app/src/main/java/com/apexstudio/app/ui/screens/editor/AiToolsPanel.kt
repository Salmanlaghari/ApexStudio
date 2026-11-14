package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.aireport.AI_REPORT_CONTACT
import com.apexstudio.app.data.aireport.AiContentReportReason
import com.apexstudio.app.data.aireport.AiContentReportTarget
import com.apexstudio.app.data.aireport.isCurrentAiContentReportTarget
import com.apexstudio.app.data.aireport.openAiContentReportEmail
import com.apexstudio.app.data.aireport.resolveAiContentReportTarget
import com.apexstudio.app.data.export.ProjectTransitionTimeline
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
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
    "SMART_TRIM", "SCENE_ANALYSIS", "STYLE_ANALYSIS", "AUTO_CROP", "FACE_TRACKING", "FACE_AWARE_REFRAME" -> AiToolAction.LOCAL_ANALYSIS
    "ONNX_UPSCALING" -> AiToolAction.VIDEO_UPSCALE
    "SUBTITLES", "WORD_TIMED_SUBTITLES" -> AiToolAction.CAPTIONS
    "BACKGROUND_REPLACE" -> AiToolAction.BACKGROUND_REPLACE
    else -> null
}

internal fun isAiMainTrackVideo(clip: MediaClip?): Boolean =
    clip?.type == ClipType.VIDEO && clip.trackIndex == 0

internal fun isCurrentProjectAiVideoTarget(
    expectedProjectId: String,
    currentProjectId: String?,
    clip: MediaClip?
): Boolean = currentProjectId == expectedProjectId && isAiMainTrackVideo(clip)

/** Never silently retarget an explicit photo, overlay, or stale selection to a different clip. */
internal fun resolveAiTargetClip(
    selectedClip: MediaClip?,
    selectedClipId: String?,
    mainPreviewClip: MediaClip?
): MediaClip? = if (selectedClipId != null) {
    selectedClip?.takeIf { isAiMainTrackVideo(it) }
} else {
    mainPreviewClip?.takeIf { isAiMainTrackVideo(it) }
}

/** Resolve the target from the latest editor snapshot using its remembered transition plan. */
internal fun resolveCurrentAiVideoTarget(
    expectedProjectId: String,
    state: EditorState,
    playheadMs: Long,
    transitionPlan: ProjectTransitionTimeline.Plan
): MediaClip? {
    val project = state.project?.takeIf { it.id == expectedProjectId } ?: return null
    val selectedClip = state.selectedClipId?.let { id -> project.clips.firstOrNull { it.id == id } }
    val previewClip = transitionPlan.entryAt(playheadMs)?.clip ?: timelinePreviewClip(project.clips, playheadMs)
    return resolveAiTargetClip(selectedClip, state.selectedClipId, previewClip)
        ?.takeIf { isCurrentProjectAiVideoTarget(expectedProjectId, project.id, it) }
}

/** Keep the sheet's displayed target stable while playback advances; reject it if removed or switched away. */
internal fun resolveLockedAiVideoTarget(
    expectedProjectId: String,
    state: EditorState,
    lockedClipId: String?
): MediaClip? {
    val project = state.project?.takeIf { it.id == expectedProjectId } ?: return null
    val clipId = lockedClipId?.takeIf(String::isNotBlank) ?: return null
    val clip = project.clips.firstOrNull { it.id == clipId } ?: return null
    return clip.takeIf { isCurrentProjectAiVideoTarget(expectedProjectId, project.id, it) }
}

internal fun isAiReportTargetCurrent(
    expectedProjectId: String,
    currentProject: Project?,
    target: AiContentReportTarget?
): Boolean = isCurrentAiContentReportTarget(expectedProjectId, currentProject, target)

internal fun resolveAiVideoReportTarget(
    expectedProjectId: String,
    project: Project?,
    selectedVideo: MediaClip?,
    selectedTextOverlayId: String?
): AiContentReportTarget? {
    val currentProject = project ?: return null
    val selected = selectedVideo ?: return null
    if (currentProject.id != expectedProjectId) return null
    val currentVideo = currentProject.clips.firstOrNull { it.id == selected.id } ?: return null
    if (!isAiMainTrackVideo(currentVideo)) return null
    if (selectedTextOverlayId != null && currentVideo.textOverlays.none { it.id == selectedTextOverlayId }) return null
    return resolveAiContentReportTarget(expectedProjectId, currentProject, currentVideo.id, selectedTextOverlayId)
}

private data class AiActionSpec(
    val id: String,
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiToolsPanel(
    tools: List<AiToolCard>,
    message: String,
    targetVideoName: String? = null,
    onClose: () -> Unit,
    onOpenLocalAnalysis: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenVideoUpscale: (() -> Unit)? = null,
    onOpenCaptions: (() -> Unit)? = null,
    onOpenBackgroundReplace: (() -> Unit)? = null,
    reportTarget: AiContentReportTarget?,
    reportState: AiContentReportUiState,
    onSubmitReport: (AiContentReportTarget, AiContentReportReason) -> Unit,
    onClearReportFeedback: () -> Unit,
    isReportTargetCurrent: (AiContentReportTarget) -> Boolean = { true },
    onComposeReport: ((AiContentReportTarget, AiContentReportReason) -> Boolean)? = null
) {
    val context = LocalContext.current
    var emailFeedback by remember { mutableStateOf<String?>(null) }
    var showSafetyInfo by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportDialogTarget by remember { mutableStateOf<AiContentReportTarget?>(null) }
    var reportDialogFeedback by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasVideoTarget = !targetVideoName.isNullOrBlank()
    val detailsOnlyCount = tools.count { aiToolActionForId(it.id) == null }
    val actions = buildList {
        onOpenLocalAnalysis?.let {
            add(AiActionSpec("review", "Review suggestions",
                "Scenes, trim windows, crops and face framing. Review each edit before applying.",
                Icons.Default.MovieFilter, it))
        }
        onOpenCaptions?.let {
            add(AiActionSpec("captions", "Captions",
                "Open this video's caption editor and choose the timing option there.",
                Icons.Default.Subtitles, it))
        }
        onOpenBackgroundReplace?.let {
            add(AiActionSpec("background", "Background replace",
                "Choose blur, colour or a project image, then apply it to this video.",
                Icons.Default.Person, it))
        }
        onOpenVideoUpscale?.let {
            add(AiActionSpec("upscale", "Upscale video",
                "Review supported upscaling options. Processing starts only after you tap Enhance.",
                Icons.Default.HighQuality, it))
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, containerColor = ApexPalette.BgSurface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("AI video tools", style = MaterialTheme.typography.titleLarge, color = ApexPalette.TextPrimary,
                    modifier = Modifier.weight(1f))
                if (onOpenDownloads != null) IconButton(onClick = onOpenDownloads) {
                    Icon(Icons.Default.Download, "Optional AI features", tint = ApexPalette.TextPrimary)
                }
                IconButton(onClick = { showSafetyInfo = true }) {
                    Icon(Icons.Default.Info, "AI availability details", tint = ApexPalette.TextSecondary)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close AI tools", tint = ApexPalette.TextPrimary) }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("ai_video_target"),
                shape = MaterialTheme.shapes.medium,
                color = ApexPalette.BgElevated
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Target video", color = ApexPalette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Text(
                        targetVideoName?.takeIf { it.isNotBlank() } ?: "Select a main-track video on the timeline",
                        color = ApexPalette.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (hasVideoTarget) "Main-track video · only this clip is targeted."
                        else "Photos and overlays are not supported here; actions stay disabled until a main-track video is selected.",
                        color = ApexPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text("Video actions", modifier = Modifier.padding(horizontal = 16.dp),
                color = ApexPalette.TextPrimary, style = MaterialTheme.typography.labelMedium)
            if (actions.isEmpty()) {
                Text("No supported video actions are available from this panel.",
                    modifier = Modifier.padding(horizontal = 16.dp), color = ApexPalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
            } else {
                FlowRow(
                    maxItemsInEachRow = 2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    actions.forEach { action ->
                        AiActionCard(action = action, enabled = hasVideoTarget, modifier = Modifier.weight(1f))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Report AI content", style = MaterialTheme.typography.titleMedium, color = ApexPalette.TextPrimary)
                Text("Report / Suggestions: $AI_REPORT_CONTACT. Open an email draft with a clip reference and reason; no media attachment and no automatic send. If no email app is available, a local-only save is attempted on this device; nothing is sent to the developer.",
                    style = MaterialTheme.typography.bodySmall, color = ApexPalette.TextSecondary)
                Button(onClick = {
                    emailFeedback = null
                    reportDialogFeedback = null
                    onClearReportFeedback()
                    reportDialogTarget = reportTarget
                    showReportDialog = true
                },
                    enabled = reportTarget != null && !reportState.isSaving) {
                    Text("Flag AI content")
                }
                if (reportTarget == null) Text("A current main-track video is required; stale text-layer references are not reportable.",
                    style = MaterialTheme.typography.bodySmall, color = ApexPalette.TextSecondary)
                if (!showReportDialog) {
                    emailFeedback?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ApexPalette.TextSecondary) }
                    reportState.message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = if (reportState.saved) MaterialTheme.colorScheme.primary else ApexPalette.TextSecondary) }
                }
            }
        }
    }

    if (showReportDialog) AiContentReportDialog(
        target = reportDialogTarget,
        state = reportState,
        feedback = reportDialogFeedback,
        onDismiss = { showReportDialog = false; reportDialogTarget = null; reportDialogFeedback = null },
        onSubmit = { reason ->
            reportDialogTarget?.let { target ->
                if (!isReportTargetCurrent(target)) {
                    emailFeedback = "This report target is no longer in the current project. Nothing was submitted; reopen the report for a current clip."
                    showReportDialog = false
                    reportDialogTarget = null
                    reportDialogFeedback = null
                } else {
                    val opened = try {
                        onComposeReport?.invoke(target, reason) ?: openAiContentReportEmail(context, target, reason)
                    } catch (_: Exception) {
                        reportDialogFeedback = "Could not open an email draft. Nothing was submitted."
                        null
                    }
                    if (opened == true) {
                        emailFeedback = "Email draft opened for $AI_REPORT_CONTACT. Review and send it in your email app; delivery has not been confirmed."
                        showReportDialog = false
                        reportDialogTarget = null
                        reportDialogFeedback = null
                    } else if (opened == false) {
                        try {
                            onSubmitReport(target, reason)
                            // reportState alone confirms asynchronous local persistence.
                            emailFeedback = null
                            reportDialogFeedback = null
                        } catch (_: Exception) {
                            reportDialogFeedback = "Could not start saving the report. Nothing was submitted."
                        }
                    }
                }
            }
        }
    )

    if (showSafetyInfo) AlertDialog(onDismissRequest = { showSafetyInfo = false },
        title = { Text("AI processing availability") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(AI_PROTECTED_BLOCKER)
                Text("Only the supported actions above are connected to the current project video. Model availability by itself does not mean a feature can edit, preview and export a project.")
                if (detailsOnlyCount > 0) Text("$detailsOnlyCount catalog/model entries are not shown as editing actions because they do not have a complete project-video route in this editor.")
                Text("Face-framing and local suggestions sample frames; review each suggestion before applying it.")
                Text("Artistic style transfer, pose overlay, content moderation and 2D-to-3D video conversion are not editing actions in this release.")
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { showSafetyInfo = false }) { Text("Close") } })
}

@Composable
private fun AiActionCard(action: AiActionSpec, enabled: Boolean, modifier: Modifier = Modifier) {
    Surface(
        onClick = action.onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 112.dp)
            .alpha(if (enabled) 1f else 0.48f)
            .testTag("ai_action_${action.id}")
            .semantics {
                if (!enabled) stateDescription = "Select a main-track video to enable this action"
            },
        shape = MaterialTheme.shapes.medium,
        color = ApexPalette.BgElevated
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(action.icon, contentDescription = null, tint = ApexPalette.TextSecondary, modifier = Modifier.size(22.dp))
            Text(action.title, color = ApexPalette.TextPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(action.detail, color = ApexPalette.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3)
        }
    }
}

@Composable
private fun AiContentReportDialog(
    target: AiContentReportTarget?,
    state: AiContentReportUiState,
    feedback: String?,
    onDismiss: () -> Unit,
    onSubmit: (AiContentReportReason) -> Unit
) {
    var selectedReason by remember { mutableStateOf<AiContentReportReason?>(null) }
    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text("Flag AI content") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose a reason to open an email draft to $AI_REPORT_CONTACT with the selected project clip${if (target?.textOverlayId != null) " and text overlay" else ""} reference. No media or text content is attached. You must send the email yourself. Without an email app, a local-only save is attempted on this device; nothing is sent.")
                AiContentReportReason.entries.forEach { reason ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !state.isSaving) { selectedReason = reason },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason }, enabled = !state.isSaving)
                        Text(reason.label, modifier = Modifier.weight(1f))
                    }
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
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
