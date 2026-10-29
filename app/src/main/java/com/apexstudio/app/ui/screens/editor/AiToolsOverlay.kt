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
    "Available actions work with selected project media. Features shown as unavailable will not run. Review suggestions before applying them; some actions are still restricted."
internal const val AI_PROTECTED_STATUS_LABEL = "Availability details"
internal const val LOCAL_REVIEW_ACTION_LABEL = "Review video suggestions"

/** Keep model readiness and end-to-end editor readiness separate. */
internal fun aiToolCards(inventory: List<AiFeatureAvailability>): List<AiToolCard> {
    return inventory.map { feature ->
        val name = feature.id.name
        val title = when (name) {
            "SMART_TRIM" -> "Smart trim suggestions"
            "SCENE_ANALYSIS" -> "Scene detection"
            "STYLE_ANALYSIS" -> "Style suggestions"
            "AUTO_CROP" -> "Crop suggestions"
            "FACE_AWARE_REFRAME" -> "Auto reframe · 9:16 / 1:1"
            "SUBTITLES" -> "Auto captions"
            "WORD_TIMED_SUBTITLES" -> "Word-timed captions"
            "BACKGROUND_REPLACE" -> "Background replacement"
            "MEDIAPIPE_FACE" -> "Face suggestions"
            "MEDIAPIPE_POSE" -> "Pose overlay"
            "MEDIAPIPE_HAND" -> "Hand gestures"
            "ONNX_BACKGROUND_REMOVAL" -> "Background removal"
            "ONNX_UPSCALING" -> "Video enhancement"
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
        val inventoryStatus = if (feature.isReady) "Available" else "Not available yet"
        val scopeNotice = if (feature.id == AiFeatureId.GESTURE_CONTROL || feature.id == AiFeatureId.MEDIAPIPE_HAND)
            "Live camera editing is not available here. " else ""
        AiToolCard(name, title, "$scopeNotice$inventoryStatus", group,
            statusLabel = AI_PROTECTED_STATUS_LABEL,
            unavailableReason = when (feature.id) {
                AiFeatureId.SUBTITLES -> "Create captions from speech in Text → Captions for a video in your project."
                AiFeatureId.WORD_TIMED_SUBTITLES -> "Optional English word timing is available in Text → Captions. Some speech may not support it."
                AiFeatureId.BACKGROUND_REPLACE -> "Replace a video's background from the editor Background tool. Choose an image already in your project."
                AiFeatureId.FACE_TRACKING -> "Review face-framing suggestions in Video suggestions. Applying automatic face crops to video is not available yet."
                AiFeatureId.FACE_AWARE_REFRAME -> "Review face-framing suggestions in Video suggestions. Applying automatic face crops to video is not available yet."
                AiFeatureId.ONNX_UPSCALING -> "Open Video enhancement above for a supported video. Photo Enhance works with selected still images (output capped at 4.2 megapixels)."
                AiFeatureId.ONNX_STYLE_TRANSFER -> "Artistic style filters are not available yet. Standard filters are available in Video tools."
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
        AiFeatureAvailability(id, AiAvailability.ENGINE_UNAVAILABLE, "Availability has not been checked yet.")
    })) }
    var showDownloads by remember { mutableStateOf(false) }
    var inventoryRevision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Checking which AI tools are available…") }
    LaunchedEffect(context, inventoryRevision) {
        try {
            cards = withContext(Dispatchers.IO) { aiToolCards(AiFeatureRegistry.all(context)) }
            message = "Availability checked. Nothing has been started."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message = "Could not check AI availability. Your project is unchanged."
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
