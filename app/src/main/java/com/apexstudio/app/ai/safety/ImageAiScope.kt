package com.apexstudio.app.ai.safety

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

/**
 * Scope extension for on-device AI on still-image clips.
 *
 * Rationale: [GlobalModerationEnforcement] historically authorised only project VIDEO clips.
 * `ClipType.IMAGE` is a first-class project clip type (the dashboard has an image-editing
 * session and the editor renders IMAGE clips), so refusing every AI action on it was a scope
 * gap rather than a policy requirement. These tools stay non-generative: they segment, matte,
 * upscale or detect landmarks in pixels the user already imported. Nothing here synthesizes
 * new imagery, accepts a prompt, or touches the camera.
 *
 * The same fail-closed rules apply: permission is a thread-local that exists only for the
 * duration of one call, is bound to the exact source URI, and never enables an action that
 * was not explicitly listed.
 */
object ImageAiScope {

    private data class ImageScope(val sourceUri: String, val actions: Set<ProtectedAiAction>)
    private val scope = ThreadLocal<ImageScope?>()

    /** Actions that are meaningful on a single decoded still image. */
    private val IMAGE_ACTIONS: Set<ProtectedAiAction> = setOf(
        ProtectedAiAction.ONNX_BACKGROUND_REMOVAL,
        ProtectedAiAction.ONNX_UPSCALING,
        ProtectedAiAction.FACE_AWARE_REFRAME,
        ProtectedAiAction.PORTRAIT_BACKGROUND_EFFECTS,
    )

    /**
     * True when [action] is currently authorised for [sourceUri] on this thread.
     * Consulted by [GlobalModerationEnforcement.requireApproved] before its video checks.
     */
    fun isAuthorised(action: ProtectedAiAction, sourceUri: String?): Boolean {
        val active = scope.get() ?: return false
        if (action !in active.actions) return false
        return sourceUri == null || sourceUri == active.sourceUri
    }

    /** Human-readable reason shown when an image action is attempted outside a valid scope. */
    fun denialMessage(action: ProtectedAiAction): String = when (action) {
        ProtectedAiAction.ONNX_BACKGROUND_REMOVAL ->
            "Background removal runs on an existing project image from the editor's image tools. No generated content, no prompts, no camera input."
        ProtectedAiAction.ONNX_UPSCALING ->
            "Upscaling runs on an existing project image from the editor's image tools. It is a learned resampler, not a generative model."
        ProtectedAiAction.FACE_AWARE_REFRAME ->
            "Face cropping runs on an existing project image from the editor's Photo AI Enhance tools; the user applies the crop to that photo."
        ProtectedAiAction.PORTRAIT_BACKGROUND_EFFECTS ->
            "Portrait background effects run on an existing project image from the editor's image tools."
        else ->
            "${action.displayName} is not an image-scope action. Only imported project images are processed; generation and camera AI stay unavailable."
    }

    /**
     * Runs [process] with [action] authorised for exactly one still-image clip.
     * The caller must resolve [clip] from the active project's clip list first.
     */
    suspend fun <T> withExistingProjectImage(
        clip: MediaClip,
        action: ProtectedAiAction,
        process: suspend () -> T,
    ): T {
        require(clip.type == ClipType.IMAGE) {
            "Image AI requires a project IMAGE clip; received ${clip.type}."
        }
        require(clip.uri.isNotBlank()) { "Image AI requires a clip with a resolvable URI." }
        require(action in IMAGE_ACTIONS) { "${action.displayName} has no image-scope backend." }
        val bound = ImageScope(clip.uri, setOf(action))
        return withContext(scope.asContextElement(bound)) { process() }
    }
}

/**
 * Thrown when an image-scope action is requested outside [ImageAiScope.withExistingProjectImage].
 * Distinct from [AiModelUnavailableException] so callers can tell a scope error from a
 * missing-model error without string matching.
 */
class ImageAiScopeException(message: String) : IllegalStateException(message)
