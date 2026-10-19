package com.apexstudio.app.ai

import android.content.Context
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import com.apexstudio.app.ai.mediapipe.BundledMediaPipeModels
import com.apexstudio.app.ai.onnx.CandyStyleTransferPack
import com.apexstudio.app.ai.onnx.ModelAvailability
import com.apexstudio.app.data.background.BackgroundReplaceEngine
import com.apexstudio.app.data.captions.WhisperCaptionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
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
    private const val STYLE_TRANSFER_LICENSE = "BSD-3-Clause model SPDX / Apache-2.0 Hub metadata; see bundled notices"

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
        put(AiFeatureId.ONNX_BACKGROUND_REMOVAL, listOf(AiModelRequirement(
            "bundled:modnet-photographic", "modnet_photographic.onnx", "Apache-2.0; MODNet photographic portrait matting",
            "https://github.com/yakhyo/modnet/releases/download/weights/modnet_photographic.onnx")))
        put(AiFeatureId.ONNX_UPSCALING, listOf(AiModelRequirement("bundled:espcn-x3-luma",
            "super-resolution-10.onnx", "Apache-2.0; ONNX Model Zoo ESPCN x3 luma-only",
            "https://huggingface.co/onnxmodelzoo/super-resolution-10/tree/395472c4b6b92f788357237983d83fd913cfe7e3")))
        put(AiFeatureId.ONNX_STYLE_TRANSFER, listOf(AiModelRequirement(
            "bundled:${CandyStyleTransferPack.MODEL_ID}", "candy-9.onnx", STYLE_TRANSFER_LICENSE,
            CandyStyleTransferPack.ARTIFACT_SOURCE_URL)))
        put(AiFeatureId.WORD_TIMED_SUBTITLES, listOf(AiModelRequirement("bundled:english-ctc-alignment",
            "wav2vec2-base-960h-int8.onnx", "Apache-2.0; English-only wav2vec2-base-960h",
            "https://huggingface.co/onnx-community/wav2vec2-base-960h-ONNX/tree/729c1a6730fb549c20a1c73a3d3f96f11020225e")))
        put(AiFeatureId.CONTENT_MODERATION, listOf(AiModelRequirement("validated-moderation-classifier-not-configured", "", "Not supplied", "")))
    }

    val descriptions: Map<AiFeatureId, AiFeatureDescription> = mapOf(
        AiFeatureId.SMART_TRIM to AiFeatureDescription("Smart trim", "Review heuristic highlight windows from sampled image activity, sharpness and exposure; not learned AI."),
        AiFeatureId.SCENE_ANALYSIS to AiFeatureDescription("Scene analysis", "Review candidate cuts from actual sampled luma histograms and frame differences; not learned AI."),
        AiFeatureId.STYLE_ANALYSIS to AiFeatureDescription("Style analysis", "Rule-based sampled color/contrast/saturation summary; not artistic style transfer."),
        AiFeatureId.AUTO_CROP to AiFeatureDescription("Auto crop", "Luma-gradient crop suggestion; not subject or face detection."),
        AiFeatureId.FACE_AWARE_REFRAME to AiFeatureDescription("Face-aware reframe", "Bundled face model and crop-track geometry. Full-clip application is not release-ready."),
        AiFeatureId.FACE_BLUR to AiFeatureDescription("Face blur", "Bundled face landmarks and frame-level privacy renderer; full-clip editor/export integration remains incomplete."),
        AiFeatureId.FACE_TRACKING to AiFeatureDescription("Face tracking", "Bundled face landmarks; temporal identity/crop geometry is not a validated tracking editor."),
        AiFeatureId.BACKGROUND_BLUR to AiFeatureDescription("Background blur", "Bundled person segmentation supports the existing pre-rendered background workflow; not real-time AI preview."),
        AiFeatureId.POSE_EFFECTS to AiFeatureDescription("Pose effects", "Bundled pose model and frame-level skeleton processing; no validated full-clip effect integration."),
        AiFeatureId.SKELETON_OVERLAY to AiFeatureDescription("Skeleton overlay", "Bundled pose model and frame renderer; not a validated timeline/export tool."),
        AiFeatureId.GESTURE_CONTROL to AiFeatureDescription("Gesture control", "Explicitly armed front-camera hand sessions are wired to bounded source-edge trims and timeline zoom. Live camera/gesture input is outside the existing-project-video scope."),
        AiFeatureId.SUBTITLES to AiFeatureDescription("Auto subtitles", "Local Whisper backend; subtitles are outside the currently enabled video-tool integration."),
        AiFeatureId.WORD_TIMED_SUBTITLES to AiFeatureDescription("Word-timed subtitles", "Local English acoustic alignment backend; not enabled in this video-tool release."),
        AiFeatureId.BACKGROUND_REPLACE to AiFeatureDescription("Background replace", "Local segmentation backend; background replacement is not enabled in the selected video-tool scope."),
        AiFeatureId.MEDIAPIPE_FACE to AiFeatureDescription("Face landmarks", "Official bundled Face Landmarker; SHA-256 checked locally before inference."),
        AiFeatureId.MEDIAPIPE_POSE to AiFeatureDescription("Pose landmarks", "Official bundled Pose Landmarker Lite; SHA-256 checked locally before inference."),
        AiFeatureId.MEDIAPIPE_HAND to AiFeatureDescription("Hand landmarks", "Official bundled Hand Landmarker; SHA-256 checked locally before inference."),
        AiFeatureId.ONNX_BACKGROUND_REMOVAL to AiFeatureDescription("ONNX background removal", "Pinned MODNet photographic portrait-matting backend is bundled. The generic standalone image workflow is unavailable; realtime preview/export and device-quality acceptance remain incomplete."),
        AiFeatureId.ONNX_UPSCALING to AiFeatureDescription("ONNX upscaling", "Learned ESPCN x3 luma video upscaling with bounded offline pre-rendering. Preview and export use the same rendered clip. Not 4K or a generative model."),
        AiFeatureId.ONNX_STYLE_TRANSFER to AiFeatureDescription("ONNX style transfer", "Pinned Candy-9 ONNX pack (fixed 224-square input) is debug/evaluation-only and excluded from release builds pending license/provenance review. Aspect ratio is distorted; standalone image processing is outside the enabled video-only scope. Preview/export and device-quality acceptance are incomplete."),
        AiFeatureId.CONTENT_MODERATION to AiFeatureDescription("Content safety", "No validated classifier is supplied. Local non-generative editing is not blocked solely for this reason.")
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
            "Bundled English CTC supports word-by-word and full-phrase karaoke experiments. Not enabled in the selected video-tool release; multilingual word alignment and device acceptance are incomplete.", requirements)
        if (id == AiFeatureId.ONNX_UPSCALING) return try {
            com.apexstudio.app.ai.onnx.EspcnSuperResolutionAdapter.fromBundledAssets(context)
            AiFeatureAvailability(id, AiAvailability.READY,
                "Pinned Apache-2.0 ESPCN x3 luma model is available locally. Use AI Video Upscale for supported existing project videos only; device codec and resource limits are checked before processing.",
                requirements, requirements.map { it.id })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AiFeatureAvailability(id, AiAvailability.MODEL_MISSING,
                "Pinned ESPCN model is missing or failed integrity verification. No processing or network fallback.", requirements)
        }
        if (id == AiFeatureId.ONNX_BACKGROUND_REMOVAL) return try {
            val adapter = com.apexstudio.app.ai.onnx.ModNetBackgroundRemovalAdapter.fromBundledAssets(context)
            when (val backend = adapter.availability()) {
                is ModelAvailability.Ready -> AiFeatureAvailability(id, AiAvailability.ENGINE_UNAVAILABLE,
                    "Pinned MODNet bytes and dynamic portrait-matting signature verified. The generic standalone image workflow is unavailable; preview/export and device-quality acceptance remain incomplete.",
                    requirements, requirements.map { it.id })
                is ModelAvailability.Unavailable -> AiFeatureAvailability(id, AiAvailability.MODEL_MISSING,
                    "Bundled MODNet verification failed: ${backend.reason}. No network fallback.", requirements)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AiFeatureAvailability(id, AiAvailability.MODEL_MISSING,
                "Bundled MODNet could not be verified or installed. No network fallback; standalone image processing is unavailable.", requirements)
        }
        if (id == AiFeatureId.ONNX_STYLE_TRANSFER) return try {
            CandyStyleTransferPack.materialize(context)
            AiFeatureAvailability(id, AiAvailability.ENGINE_UNAVAILABLE,
                "Pinned Candy-9 bytes verified. License declarations/style-reference provenance require review; the model is not in the app-approved artifact catalog. The generic standalone image workflow is unavailable; preview/export are not enabled.",
                requirements, requirements.map { it.id })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AiFeatureAvailability(id, AiAvailability.MODEL_MISSING,
                "Bundled style-transfer model could not be verified or installed. No network fallback; standalone image processing is unavailable.", requirements)
        }
        if (id == AiFeatureId.CONTENT_MODERATION) return AiFeatureAvailability(id, AiAvailability.ENGINE_UNAVAILABLE,
            "No validated offline classifier. Google Play policy does not explicitly require an all-frame classifier for these non-generative local video tools.", requirements)
        val bundled = mediaPipeModelFor(id)
        if (bundled != null || id == AiFeatureId.SUBTITLES) {
            return try {
                val modelContext = kotlinx.coroutines.currentCoroutineContext()
                val available = if (bundled != null) BundledMediaPipeModels.modelFile(context, bundled) {
                    modelContext.ensureActive()
                }.isFile
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
                        } else "Local model bytes available. Backend readiness does not itself enable a video editor tool."
                    } else "Bundled/local model is missing or could not be materialized; no network request was made. No model-backed action can run without its required local artifact.",
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
