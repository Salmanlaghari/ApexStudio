package com.apexstudio.app.ai.safety

import com.apexstudio.app.ai.AiModelUnavailableException
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

/** Legacy backend actions. Only explicitly integrated, video-only workflows may authorize one. */
enum class ProtectedAiAction(val displayName: String) {
    WHISPER_CAPTIONS("Whisper caption generation"),
    WORD_ALIGNMENT("acoustic word alignment"),
    BACKGROUND_REPLACEMENT("full-clip background replacement"),
    GESTURE_EDITING("gesture-driven editor control"),
    FACE_AWARE_REFRAME("face-aware reframing"),
    FACE_TRACK_REVIEW("model-based face-track review"),
    ONNX_UPSCALING("learned video-frame upscaling"),
    ONNX_BACKGROUND_REMOVAL("ONNX background removal"),
    ONNX_STYLE_TRANSFER("ONNX style transfer"),
    PORTRAIT_BACKGROUND_EFFECTS("MediaPipe portrait background effects"),
}

data class ModerationReadiness(
    val approved: Boolean,
    val requiredCategories: Set<SafetyCategory>,
    val message: String,
)

/**
 * Existing-project video scope, not an invented Google Play all-frame moderation requirement.
 * https://support.google.com/googleplay/android-developer/answer/13985936
 *
 * Play's generative-content policy does not explicitly require offline frame-by-frame classifiers
 * for non-generative local editing. No classifier approval is claimed here. The old generic APIs
 * still cannot process arbitrary still images, prompts, camera inputs or generate new content.
 * The bounded video-upscale pipeline authorizes only its scoped frame inference, after
 * validating the selected project VIDEO and decoding it. Permission never survives that call,
 * leaks to unrelated coroutines/threads, or permits another model/action. Readiness is NOT a global unlock switch.
 */
object GlobalModerationEnforcement {
    private val videoUpscaleDepth = ThreadLocal<Int>()

    val readiness = ModerationReadiness(
        approved = false,
        requiredCategories = emptySet(),
        message = "Only explicitly wired existing-project video tools are available. " +
            "No prompts, still-image AI, live camera AI, or image/video generation. " +
            "Google Play policy does not impose a blanket offline all-frame classifier on these local effects.",
    )

    fun statusMessage(action: ProtectedAiAction): String = when (action) {
        ProtectedAiAction.ONNX_UPSCALING ->
            "Use AI Video Upscale on a supported existing project video. Standalone still-image inference is unavailable."
        ProtectedAiAction.ONNX_STYLE_TRANSFER ->
            "Artistic ML filters are unavailable: model license/provenance review and full-video preview/export integration are incomplete."
        ProtectedAiAction.GESTURE_EDITING ->
            "Live camera/gesture AI is outside the existing-project-video scope."
        else -> "${action.displayName} is unavailable in this release: this workflow is not enabled in the selected video-only tool scope. Keep editing manually."
    }

    fun requireApproved(action: ProtectedAiAction) {
        if (action == ProtectedAiAction.ONNX_UPSCALING && (videoUpscaleDepth.get() ?: 0) > 0) return
        throw AiModelUnavailableException(statusMessage(action))
    }

    /** Internal capability: only wrap a decoded frame inside the validated video pipeline. */
    internal suspend fun <T> withExistingProjectVideoUpscaling(processFrame: suspend () -> T): T =
        withContext(videoUpscaleDepth.asContextElement(1)) { processFrame() }
}
