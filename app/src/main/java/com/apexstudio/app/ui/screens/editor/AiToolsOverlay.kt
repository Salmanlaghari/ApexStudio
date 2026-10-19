package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.apexstudio.app.ai.AiAvailability
import com.apexstudio.app.ai.AiFeatureAvailability
import com.apexstudio.app.ai.AiFeatureId
import com.apexstudio.app.ai.AiFeatureRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val AI_PROTECTED_BLOCKER =
    "Blocked pending validated all-category moderation for sexual, violent, and hateful content across every protected workflow. This validation is not available yet."
internal const val AI_PROTECTED_STATUS_LABEL = "Unavailable · safety validation pending"
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
        // Inventory is informational only. Model readiness never changes the
        // protected-tool status or creates a control that could enable it.
        val inventoryStatus = if (feature.isReady) "Local backend detected" else "Local backend unavailable"
        AiToolCard(name, title, "$inventoryStatus: ${feature.message}", group,
            statusLabel = AI_PROTECTED_STATUS_LABEL,
            unavailableReason = AI_PROTECTED_BLOCKER)
    }
}

@Composable
fun AiToolsOverlay(
    onClose: () -> Unit,
    onOpenLocalAnalysis: (() -> Unit)? = null,
    onOpenGestures: (() -> Unit)? = null,
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
            message = "Could not read the local model inventory. Protected AI remains unavailable; your project is unchanged."
        }
    }
    AiToolsPanel(tools = cards, message = message,
        onClose = onClose, onOpenLocalAnalysis = onOpenLocalAnalysis,
        onOpenGestures = onOpenGestures, onOpenDownloads = { showDownloads = true })
    if (showDownloads) ModelDownloadsDialog(onDismiss = {
        showDownloads = false
        inventoryRevision++
    })
}
