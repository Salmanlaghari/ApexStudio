package com.apexstudio.app.ai

import android.content.Context
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import com.apexstudio.app.ai.mediapipe.BundledMediaPipeModels
import com.apexstudio.app.data.background.BackgroundReplaceEngine
import com.apexstudio.app.data.captions.WhisperCaptionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stable IDs intended for editor/UI integration. */
enum class AiFeatureId {
    SMART_TRIM, SCENE_ANALYSIS, STYLE_ANALYSIS, AUTO_CROP, FACE_AWARE_REFRAME,
    FACE_BLUR, FACE_TRACKING, BACKGROUND_BLUR, POSE_EFFECTS, SKELETON_OVERLAY,
    GESTURE_CONTROL, SUBTITLES, WORD_TIMED_SUBTITLES, BACKGROUND_REPLACE,
    MEDIAPIPE_FACE, MEDIAPIPE_POSE, MEDIAPIPE_HAND, ONNX_BACKGROUND_REMOVAL,
    ONNX_UPSCALING, ONNX_STYLE_TRANSFER, CONTENT_MODERATION
}

enum class AiAvailability { READY, MODEL_REQUIRED, MODEL_MISSING, ENGINE_UNAVAILABLE }
data class AiModelRequirement(val id: String, val fileName: String, val license: String, val source: String)
data class AiFeatureAvailability(
    val id: AiFeatureId,
    val availability: AiAvailability,
    val message: String,
    val requiredModels: List<AiModelRequirement> = emptyList(),
    val installedModels: List<String> = emptyList()
) {
    /** Backend/model readiness only. NOT permission to enable an editor AI action. */
    val isReady: Boolean get() = availability == AiAvailability.READY
}
data class AiFeatureDescription(val title: String, val summary: String)

/**
 * File-only backend inventory. Call off the main thread: bundled models are SHA-256 verified and
 * materialized into private storage. This never downloads models or uploads media. Model bytes do
 * not establish inference accuracy, safety validation, real-time preview or export integration.
 */
object AiFeatureRegistry {
    private const val WHISPER_LICENSE = "OpenAI Whisper weights: MIT; sherpa-onnx exporter: Apache-2.0"
    private const val MEDIAPIPE_LICENSE = "Apache-2.0; bundled official MediaPipe distribution, see model notices"

    internal fun mediaPipeModelFor(id: AiFeatureId): BundledMediaPipeModels.Model? = when (id) {
        AiFeatureId.MEDIAPIPE_FACE, AiFeatureId.FACE_BLUR, AiFeatureId.FACE_TRACKING,
        AiFeatureId.FACE_AWARE_REFRAME -> BundledMediaPipeModels.Model.FACE
        AiFeatureId.MEDIAPIPE_POSE, AiFeatureId.POSE_EFFECTS,
        AiFeatureId.SKELETON_OVERLAY -> BundledMediaPipeModels.Model.POSE
        AiFeatureId.MEDIAPIPE_HAND, AiFeatureId.GESTURE_CONTROL -> BundledMediaPipeModels.Model.HAND
        AiFeatureId.BACKGROUND_BLUR, AiFeatureId.BACKGROUND_REPLACE -> BundledMediaPipeModels.Model.SELFIE_SEGMENTER
        else -> null
    }

    private fun mediaPipeRequirement(model: BundledMediaPipeModels.Model): AiModelRequirement {
        val source = when (model) {
            BundledMediaPipeModels.Model.FACE -> "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
            BundledMediaPipeModels.Model.POSE -> "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
            BundledMediaPipeModels.Model.HAND -> "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
            BundledMediaPipeModels.Model.SELFIE_SEGMENTER -> "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite"
        }
        return AiModelRequirement("bundled:${model.assetName}", model.assetName, MEDIAPIPE_LICENSE, source)
    }

    val knownRequirements: Map<AiFeatureId, List<AiModelRequirement>> = buildMap {
        AiFeatureId.entries.forEach { id ->
            mediaPipeModelFor(id)?.let { put(id, listOf(mediaPipeRequirement(it))) }
        }
        put(AiFeatureId.SUBTITLES, listOf(
            AiModelRequirement(WhisperCaptionEngine.PACK_ENCODER, "tiny-encoder.int8.onnx", WHISPER_LICENSE,
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c"),
            AiModelRequirement(WhisperCaptionEngine.PACK_DECODER, "tiny-decoder.int8.onnx", WHISPER_LICENSE,
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c"),
            AiModelRequirement(WhisperCaptionEngine.PACK_TOKENS, "tiny-tokens.txt", WHISPER_LICENSE,
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c")
        ))
        put(AiFeatureId.ONNX_BACKGROUND_REMOVAL, listOf(missingOnnx("onnx-background-removal.onnx")))
        put(AiFeatureId.ONNX_UPSCALING, listOf(AiModelRequirement("bundled:espcn-x3-luma",
            "super-resolution-10.onnx", "Apache-2.0; ONNX Model Zoo ESPCN x3 luma-only",
            "https://huggingface.co/onnxmodelzoo/super-resolution-10/tree/395472c4b6b92f788357237983d83fd913cfe7e3")))
        put(AiFeatureId.ONNX_STYLE_TRANSFER, listOf(missingOnnx("onnx-style-transfer.onnx")))
        put(AiFeatureId.WORD_TIMED_SUBTITLES, listOf(AiModelRequirement("bundled:english-ctc-alignment",
            "wav2vec2-base-960h-int8.onnx", "Apache-2.0; English-only wav2vec2-base-960h",
            "https://huggingface.co/onnx-community/wav2vec2-base-960h-ONNX/tree/729c1a6730fb549c20a1c73a3d3f96f11020225e")))
        put(AiFeatureId.CONTENT_MODERATION, listOf(AiModelRequirement("validated-moderation-classifier-not-configured", "", "Not supplied", "")))
    }

    private fun missingOnnx(file: String) = AiModelRequirement(
        "not-configured:$file", file, "Model license/contract must be reviewed before distribution", ""
    )

    val descriptions: Map<AiFeatureId, AiFeatureDescription> = mapOf(
        AiFeatureId.SMART_TRIM to AiFeatureDescription("Smart trim", "Review heuristic highlight windows from sampled image activity, sharpness and exposure; not learned AI."),
        AiFeatureId.SCENE_ANALYSIS to AiFeatureDescription("Scene analysis", "Review candidate cuts from actual sampled luma histograms and frame differences; not learned AI."),
        AiFeatureId.STYLE_ANALYSIS to AiFeatureDescription("Style analysis", "Rule-based sampled color/contrast/saturation summary; not artistic style transfer."),
        AiFeatureId.AUTO_CROP to AiFeatureDescription("Auto crop", "Luma-gradient crop suggestion; not subject or face detection."),
        AiFeatureId.FACE_AWARE_REFRAME to AiFeatureDescription("Face-aware reframe", "Bundled face model and crop-track geometry. Full-clip application is not release-ready."),
        AiFeatureId.FACE_BLUR to AiFeatureDescription("Face blur", "Bundled face landmarks and frame-level privacy renderer; full-clip safety/export validation remains required."),
        AiFeatureId.FACE_TRACKING to AiFeatureDescription("Face tracking", "Bundled face landmarks; temporal identity/crop geometry is not a validated tracking editor."),
        AiFeatureId.BACKGROUND_BLUR to AiFeatureDescription("Background blur", "Bundled person segmentation supports the existing pre-rendered background workflow; not real-time AI preview."),
        AiFeatureId.POSE_EFFECTS to AiFeatureDescription("Pose effects", "Bundled pose model and frame-level skeleton processing; no validated full-clip effect integration."),
        AiFeatureId.SKELETON_OVERLAY to AiFeatureDescription("Skeleton overlay", "Bundled pose model and frame renderer; not a validated timeline/export tool."),
        AiFeatureId.GESTURE_CONTROL to AiFeatureDescription("Gesture control", "Explicitly armed front-camera hand sessions are wired to bounded source-edge trims and timeline zoom. Camera access and commands remain blocked until safety approval; no device acceptance yet."),
        AiFeatureId.SUBTITLES to AiFeatureDescription("Auto subtitles", "Bundled multilingual int8 Whisper is local, but protected transcript inference is blocked until app-reviewed sexual/violent/hateful moderation is configured."),
        AiFeatureId.WORD_TIMED_SUBTITLES to AiFeatureDescription("Word-timed subtitles", "English acoustic word alignment is protected processing and is blocked until app-reviewed sexual/violent/hateful moderation is configured."),
        AiFeatureId.BACKGROUND_REPLACE to AiFeatureDescription("Background replace", "The bundled segmentation backend is local, but full-clip processing is blocked until app-reviewed sexual/violent/hateful moderation is configured."),
        AiFeatureId.MEDIAPIPE_FACE to AiFeatureDescription("Face landmarks", "Official bundled Face Landmarker; SHA-256 checked locally before inference."),
        AiFeatureId.MEDIAPIPE_POSE to AiFeatureDescription("Pose landmarks", "Official bundled Pose Landmarker Lite; SHA-256 checked locally before inference."),
        AiFeatureId.MEDIAPIPE_HAND to AiFeatureDescription("Hand landmarks", "Official bundled Hand Landmarker; SHA-256 checked locally before inference."),
        AiFeatureId.ONNX_BACKGROUND_REMOVAL to AiFeatureDescription("ONNX background removal", "A configured licensed segmentation model and validated end-to-end integration are required."),
        AiFeatureId.ONNX_UPSCALING to AiFeatureDescription("ONNX upscaling", "Bundled learned ESPCN x3 luma backend with tiled host inference. Not an approved editor/export tool, exact 1080p converter or 4K pipeline."),
        AiFeatureId.ONNX_STYLE_TRANSFER to AiFeatureDescription("ONNX style transfer", "A configured licensed artistic model and validated preview/export are required."),
        AiFeatureId.CONTENT_MODERATION to AiFeatureDescription("Content safety", "Fail-closed: validated sexual/violent/hateful coverage is not supplied.")
    )

    suspend fun all(context: Context): List<AiFeatureAvailability> = AiFeatureId.entries.map { get(context, it) }

    suspend fun get(context: Context, id: AiFeatureId): AiFeatureAvailability = withContext(Dispatchers.IO) {
        inspectLocalBackend(context, id)
    }

    private suspend fun inspectLocalBackend(context: Context, id: AiFeatureId): AiFeatureAvailability {
        val requirements = knownRequirements[id].orEmpty()
        if (id in setOf(AiFeatureId.SMART_TRIM, AiFeatureId.SCENE_ANALYSIS, AiFeatureId.AUTO_CROP, AiFeatureId.STYLE_ANALYSIS)) {
            return AiFeatureAvailability(id, AiAvailability.READY,
                "Deterministic local signal analysis, not learned AI. Review suggestions before applying edits.")
        }
        if (id == AiFeatureId.WORD_TIMED_SUBTITLES) return AiFeatureAvailability(id, AiAvailability.ENGINE_UNAVAILABLE,
            "Bundled English CTC supports word-by-word and full-phrase karaoke experiments. New inference is blocked by global moderation; multilingual word alignment and device acceptance are incomplete.", requirements)
        if (id == AiFeatureId.ONNX_UPSCALING) return AiFeatureAvailability(id, AiAvailability.ENGINE_UNAVAILABLE,
            "Pinned ESPCN x3 luma weights are bundled for model evaluation. User-content inference is blocked by global moderation; editor integration, 4K processing and device/image-quality acceptance are incomplete.", requirements)
        if (id == AiFeatureId.CONTENT_MODERATION) return AiFeatureAvailability(id, AiAvailability.ENGINE_UNAVAILABLE,
            "No validated offline sexual/violent/hateful classifier. Protected AI actions remain OFF; pixel heuristics are not moderation.", requirements)
        val bundled = mediaPipeModelFor(id)
        if (bundled != null || id == AiFeatureId.SUBTITLES) {
            return try {
                val available = if (bundled != null) BundledMediaPipeModels.modelFile(context, bundled).isFile
                    else WhisperCaptionEngine.isModelReady(context)
                AiFeatureAvailability(id, if (available) AiAvailability.READY else AiAvailability.MODEL_MISSING,
                    if (available) {
                        val protectedAction = when (id) {
                            AiFeatureId.SUBTITLES -> ProtectedAiAction.WHISPER_CAPTIONS
                            AiFeatureId.BACKGROUND_REPLACE -> ProtectedAiAction.BACKGROUND_REPLACEMENT
                            else -> null
                        }
                        if (protectedAction != null) {
                            "Local model bytes are available, but ${GlobalModerationEnforcement.statusMessage(protectedAction)}"
                        } else "Local model bytes available. Backend readiness is not full-clip/editor/safety approval."
                    } else "Bundled/local model is missing or could not be materialized; no network request was made. Protected user-content AI also remains blocked until moderation is approved.",
                    requirements, if (available) requirements.map { it.id } else emptyList())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AiFeatureAvailability(id, AiAvailability.MODEL_MISSING,
                    "Local model verification or installation failed. Protected AI stays OFF; no network request was made.", requirements)
            }
        }
        return AiFeatureAvailability(id, AiAvailability.MODEL_REQUIRED,
            "A reviewed model with a validated input/output contract is not configured for this feature. No automatic download.", requirements)
    }
}
