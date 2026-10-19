package com.apexstudio.app.ai.safety

import com.apexstudio.app.ai.AiModelUnavailableException

/** User-content AI actions that may only run after app-reviewed moderation has been approved. */
enum class ProtectedAiAction(val displayName: String) {
    WHISPER_CAPTIONS("Whisper caption generation"),
    WORD_ALIGNMENT("acoustic word alignment"),
    BACKGROUND_REPLACEMENT("full-clip background replacement"),
    GESTURE_EDITING("gesture-driven editor control"),
    FACE_AWARE_REFRAME("face-aware reframing"),
    ONNX_UPSCALING("learned image upscaling"),
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
 * Global fail-closed policy for AI actions that process user media into generated or transformed output.
 *
 * The reviewed moderation artifact catalog is intentionally empty and no validated all-category
 * classifier is configured. This API therefore denies every protected action before model loading,
 * audio/image decoding, inference, cache reuse, or output publication. Local editing and explicitly
 * review-only/offline analysis are not routed through this policy.
 */
object GlobalModerationEnforcement {
    private val required = SafetyCategory.entries.toSet()

    private const val BASE_STATUS =
        "Protected AI is blocked: no app-reviewed offline classifier with validated sexual, violent, " +
            "and hateful coverage is configured. No protected AI processing was run. Keep editing manually " +
            "or use offline analysis/review; retry after a reviewed app update."

    val readiness: ModerationReadiness = ModerationReadiness(
        approved = false,
        requiredCategories = required,
        message = BASE_STATUS,
    )

    fun statusMessage(action: ProtectedAiAction): String =
        "${readiness.message} Blocked action: ${action.displayName}."

    /** Throws before protected processing. There is deliberately no caller-supplied approval flag. */
    fun requireApproved(action: ProtectedAiAction) {
        if (!readiness.approved || !readiness.requiredCategories.containsAll(SafetyCategory.entries)) {
            throw AiModelUnavailableException(statusMessage(action))
        }
    }
}
