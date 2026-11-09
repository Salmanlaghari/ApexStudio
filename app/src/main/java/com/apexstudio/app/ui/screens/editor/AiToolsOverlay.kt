package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.apexstudio.app.data.aireport.AiContentReportReason
import com.apexstudio.app.data.aireport.AiContentReportTarget
import com.apexstudio.app.presentation.viewmodel.AiContentReportUiState
import com.apexstudio.app.ai.AiAvailability
import com.apexstudio.app.ai.AiFeatureAvailability
import com.apexstudio.app.ai.AiFeatureId
import com.apexstudio.app.ai.AiFeatureRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val AI_PROTECTED_BLOCKER =
    "Whisper captions, English acoustic word timing, background compositing and sampled face review have scoped existing-video editor actions. Photo AI Enhance provides source-scoped ESPCN upscaling and local face crop for selected project images. Other model inventory is not a processing control. No prompts, arbitrary still-image AI, live camera AI, image/video generation, or depth-based 2D-to-3D conversion (no integrated, tested depth model)."
internal const val AI_PROTECTED_STATUS_LABEL = "Backend information · not an editor action"
internal const val LOCAL_REVIEW_ACTION_LABEL = "Open offline scene + face review"

/** Keep model readiness and end-to-end editor readiness separate. */
internal fun aiToolCards(inventory: List<AiFeatureAvailability>): List<AiToolCard> {
    return inventory.map { feature ->
        val name = feature.id.name
        val title = when (name) {
            "SMART_TRIM" -> "Smart trim suggestions"
            "SCENE_ANALYSIS" -> "Scene detection"
            "STYLE_ANALYSIS" -> "Local style analysis"
            "AUTO_CROP" -> "Signal-based crop · not subject AI"
            "FACE_AWARE_REFRAME" -> "Auto reframe · 9:16 / 1:1"
            "SUBTITLES" -> "Whisper auto subtitles"
            "WORD_TIMED_SUBTITLES" -> "Word-timed karaoke"
            "BACKGROUND_REPLACE" -> "Background replacement"
            "MEDIAPIPE_FACE" -> "Face detection & tracking"
            "MEDIAPIPE_POSE" -> "Pose & skeleton overlay"
            "MEDIAPIPE_HAND" -> "Hand & gesture control"
            "ONNX_BACKGROUND_REMOVAL" -> "AI background removal"
            "ONNX_UPSCALING" -> "AI upscale · ESPCN x3 backend"
            "ONNX_STYLE_TRANSFER" -> "Artistic style transfer"
            "CONTENT_MODERATION" -> "Content safety"
            else -> name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        }
        val group = when {
            name.contains("SUBTITLE") -> "Subtitles"
            name.startsWith("ONNX") -> "Advanced"
            name.startsWith("MEDIAPIPE") || name.contains("FACE") || name.contains("POSE") || name.contains("GESTURE") || name.contains("BACKGROUND_BLUR") -> "Face & pose"
            name == "CONTENT_MODERATION" -> "Safety"
            else -> "Smart editing"
        }
        // Inventory is informational. Real actions remain on the relevant video or photo editor panel.
        val inventoryStatus = if (feature.isReady) "Local backend detected" else "Local backend unavailable"
        val scopeNotice = if (feature.id == AiFeatureId.GESTURE_CONTROL || feature.id == AiFeatureId.MEDIAPIPE_HAND)
            "Live camera/gesture editing is outside the existing-project-video scope. " else ""
        AiToolCard(name, title, "$scopeNotice$inventoryStatus: ${feature.message}", group,
            statusLabel = AI_PROTECTED_STATUS_LABEL,
            unavailableReason = when (feature.id) {
                AiFeatureId.SUBTITLES -> "Available in Text → Captions for an existing project video. Local Whisper recognizes speech and inserts editable phrase captions; model readiness and audio access are checked when run."
                AiFeatureId.WORD_TIMED_SUBTITLES -> "Available as an opt-in English mode in Text → Captions for an existing project video. Unsupported or low-confidence alignment fails rather than inventing word times."
                AiFeatureId.BACKGROUND_REPLACE -> "Available from the editor Background tool on an existing project video. Replacement images must already be in the project; segmentation and compositing run locally."
                AiFeatureId.FACE_TRACKING -> "Available as sampled face review in Offline analysis. Dynamic crop tracks are supported by project export, but automatic video-track application remains review-restricted."
                AiFeatureId.FACE_AWARE_REFRAME -> "Still photos can use Face crop in Photo AI Enhance. Sampled face candidates can be reviewed in Offline analysis; automatic video-track application remains review-restricted."
                AiFeatureId.ONNX_UPSCALING -> "Open AI Video Upscale above for a supported selected video; selected still photos use AI upscale in Photo AI Enhance (output capped at 4.2 megapixels)."
                AiFeatureId.ONNX_STYLE_TRANSFER -> "ML artistic filters remain unavailable: license/provenance review and full-video integration are incomplete. Fixed non-AI filters are available in Video tools."
                else -> AI_PROTECTED_BLOCKER
            })
    }
}

@Composable
fun AiToolsOverlay(
    onClose: () -> Unit,
    onOpenLocalAnalysis: (() -> Unit)? = null,
    onOpenVideoPresets: (() -> Unit)? = null,
    onOpenVideoUpscale: (() -> Unit)? = null,
    onOpenCaptions: (() -> Unit)? = null,
    onOpenBackgroundReplace: (() -> Unit)? = null,
    reportTarget: AiContentReportTarget?,
    reportState: AiContentReportUiState,
    onSubmitReport: (AiContentReportReason) -> Unit,
    onClearReportFeedback: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    var cards by remember { mutableStateOf(aiToolCards(AiFeatureId.entries.map { id ->
        AiFeatureAvailability(id, AiAvailability.ENGINE_UNAVAILABLE, "Local pack inventory has not been checked yet.")
    })) }
    var showDownloads by remember { mutableStateOf(false) }
    var inventoryRevision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Checking local model inventory for information only…") }
    LaunchedEffect(context, inventoryRevision) {
        try {
            cards = withContext(Dispatchers.IO) { aiToolCards(AiFeatureRegistry.all(context)) }
            message = "Local model inventory checked for information only. No download starts here."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message = "Could not read the local model inventory. Your project is unchanged; processing will check its own model integrity."
        }
    }
    AiToolsPanel(tools = cards, message = message,
        onClose = onClose, onOpenLocalAnalysis = onOpenLocalAnalysis,
        onOpenVideoPresets = onOpenVideoPresets,
        onOpenVideoUpscale = onOpenVideoUpscale,
        onOpenCaptions = onOpenCaptions,
        onOpenBackgroundReplace = onOpenBackgroundReplace,
        onOpenDownloads = { showDownloads = true },
        reportTarget = reportTarget, reportState = reportState,
        onSubmitReport = onSubmitReport, onClearReportFeedback = onClearReportFeedback)
    if (showDownloads) ModelDownloadsDialog(onDismiss = {
        showDownloads = false
        inventoryRevision++
    })
}
