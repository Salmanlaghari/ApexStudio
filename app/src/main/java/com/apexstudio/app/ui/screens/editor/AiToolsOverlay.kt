package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.apexstudio.app.ai.AiFeatureAvailability
import com.apexstudio.app.ai.AiFeatureId
import com.apexstudio.app.ai.AiFeatureRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keep model readiness and end-to-end editor readiness separate. */
internal fun aiToolCards(inventory: List<AiFeatureAvailability>): List<AiToolCard> {
    val safetyReady = inventory.any { it.id == AiFeatureId.CONTENT_MODERATION && it.isReady }
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
            "ONNX_UPSCALING" -> "AI upscale · 1080p / 4K"
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
        // A runtime/algorithm is not an editor effect. Until a reviewed safety
        // classifier AND validated full-clip/export integration exist, do not
        // turn a switch on or claim the user's video has been processed.
        val reason = when {
            !feature.isReady -> feature.message
            !safetyReady -> "Blocked: a validated offline sexual/violent/hateful-content classifier is not configured. " + feature.message
            else -> "Backend available; full-clip editor application and export validation are still required."
        }
        AiToolCard(name, title, feature.message, group, ready = false, unavailableReason = reason)
    }
}

@Composable
fun AiToolsOverlay(onClose: () -> Unit, onOpenLocalAnalysis: (() -> Unit)? = null) {
    val context = LocalContext.current.applicationContext
    var cards by remember { mutableStateOf<List<AiToolCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("Checking installed local models…") }
    LaunchedEffect(context) {
        try {
            cards = withContext(Dispatchers.IO) { aiToolCards(AiFeatureRegistry.all(context)) }
            message = "Bundled model availability is not safety or editor approval. Protected AI stays OFF without validated moderation and integration. Ordinary non-AI analysis is separate. Existing Captions and Background tools use bundled local models; no download starts here."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message = "Could not read the local model inventory. AI remains OFF; your project is unchanged."
        } finally {
            loading = false
        }
    }
    AiToolsPanel(tools = cards, processing = loading, progress = 0f, message = message,
        onToggle = { _, _ -> message = "This feature is not ready; no changes were made." },
        onCancel = onClose, onClose = onClose, onOpenLocalAnalysis = onOpenLocalAnalysis)
}
