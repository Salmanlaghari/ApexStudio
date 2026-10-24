package com.apexstudio.app.ai.safety

import com.apexstudio.app.ai.AiModelUnavailableException
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
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
    private data class ExistingVideoScope(val sourceUri: String, val actions: Set<ProtectedAiAction>)
    private val existingVideoScope = ThreadLocal<ExistingVideoScope?>()

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
        ProtectedAiAction.WHISPER_CAPTIONS ->
            "Whisper captioning is available only from the editor's Auto Captions action for a selected existing project video."
        ProtectedAiAction.WORD_ALIGNMENT ->
            "English word alignment is available only as an opt-in caption mode for the selected existing project video; unsupported or low-confidence intervals are rejected."
        ProtectedAiAction.FACE_TRACK_REVIEW ->
            "Sampled local face review is available only for a selected existing project video; it does not apply edits. Project export rejects face-crop tracks."
        ProtectedAiAction.FACE_AWARE_REFRAME ->
            "Automatic face reframe application is unavailable: current project export explicitly rejects face-crop tracks. Use the manual crop tool; local sampled face review remains available."
        ProtectedAiAction.BACKGROUND_REPLACEMENT ->
            "Background compositing is available only from the editor for a selected existing project video; replacement images must already be in the project."
        ProtectedAiAction.ONNX_STYLE_TRANSFER ->
            "Artistic ML filters are unavailable: model license/provenance review and full-video preview/export integration are incomplete."
        ProtectedAiAction.GESTURE_EDITING ->
            "Live camera/gesture AI is outside the existing-project-video scope."
        else -> "${action.displayName} is unavailable in this release: this workflow is not enabled in the selected video-only tool scope. Keep editing manually."
    }

    fun requireApproved(action: ProtectedAiAction, sourceUri: String? = null) {
        if (action == ProtectedAiAction.ONNX_UPSCALING && (videoUpscaleDepth.get() ?: 0) > 0) return
        // Still-image processing of an imported project IMAGE clip. Same fail-closed contract:
        // thread-local, URI-bound, and limited to actions the image scope explicitly listed.
        if (ImageAiScope.isAuthorised(action, sourceUri)) return
        val videoScope = existingVideoScope.get()
        // A missing URI is allowed only for nested model construction/inference helpers after the
        // scoped processor has checked the exact source URI at its own entry point.
        if (videoScope != null && action in videoScope.actions &&
            (sourceUri == null || sourceUri == videoScope.sourceUri)
        ) return
        throw AiModelUnavailableException(statusMessage(action))
    }

    /** Internal capability: only wrap a decoded frame inside the validated video pipeline. */
    internal suspend fun <T> withExistingProjectVideoUpscaling(processFrame: suspend () -> T): T =
        withContext(videoUpscaleDepth.asContextElement(1)) { processFrame() }

    /** Caller must resolve [clip] from the active project's clip list before entering this scope. */
    internal suspend fun <T> withExistingProjectVideoCaptions(clip: MediaClip, process: suspend () -> T): T {
        require(clip.type == ClipType.VIDEO && clip.uri.isNotBlank()) { "Captions require an existing project video." }
        val scope = ExistingVideoScope(clip.uri, setOf(ProtectedAiAction.WHISPER_CAPTIONS, ProtectedAiAction.WORD_ALIGNMENT))
        return withContext(existingVideoScope.asContextElement(scope)) { process() }
    }

    /** Caller must resolve [clip] from the active project's clip list before entering this review scope. */
    internal suspend fun <T> withExistingProjectVideoFaceReview(clip: MediaClip, process: suspend () -> T): T {
        require(clip.type == ClipType.VIDEO && clip.uri.isNotBlank()) { "Face review requires an existing project video." }
        val scope = ExistingVideoScope(clip.uri, setOf(ProtectedAiAction.FACE_TRACK_REVIEW))
        return withContext(existingVideoScope.asContextElement(scope)) { process() }
    }

    /** Caller must validate the target video and any replacement image against the active project. */
    internal suspend fun <T> withExistingProjectVideoBackgroundReplacement(
        sourceUri: String,
        process: suspend () -> T,
    ): T {
        require(sourceUri.isNotBlank()) { "Background replacement requires an existing project video source." }
        val scope = ExistingVideoScope(sourceUri, setOf(ProtectedAiAction.BACKGROUND_REPLACEMENT))
        return withContext(existingVideoScope.asContextElement(scope)) { process() }
    }
}
