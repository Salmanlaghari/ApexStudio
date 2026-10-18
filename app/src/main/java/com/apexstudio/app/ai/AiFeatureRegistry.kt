package com.apexstudio.app.ai

import android.content.Context
import com.apexstudio.app.data.background.BackgroundReplaceEngine
import com.apexstudio.app.data.captions.WhisperCaptionEngine
import com.apexstudio.app.data.packs.download.PackManager
import java.io.File

/** Stable IDs intended for editor/UI integration. */
enum class AiFeatureId {
    SMART_TRIM,
    SCENE_ANALYSIS,
    STYLE_ANALYSIS,
    AUTO_CROP,
    FACE_AWARE_REFRAME,
    FACE_BLUR,
    FACE_TRACKING,
    BACKGROUND_BLUR,
    POSE_EFFECTS,
    SKELETON_OVERLAY,
    GESTURE_CONTROL,
    SUBTITLES,
    WORD_TIMED_SUBTITLES,
    BACKGROUND_REPLACE,
    MEDIAPIPE_FACE,
    MEDIAPIPE_POSE,
    MEDIAPIPE_HAND,
    ONNX_BACKGROUND_REMOVAL,
    ONNX_UPSCALING,
    ONNX_STYLE_TRANSFER,
    CONTENT_MODERATION
}

enum class AiAvailability { READY, MODEL_REQUIRED, MODEL_MISSING, ENGINE_UNAVAILABLE }

data class AiModelRequirement(
    val id: String,
    val fileName: String,
    val license: String,
    val source: String
)

data class AiFeatureAvailability(
    val id: AiFeatureId,
    val availability: AiAvailability,
    val message: String,
    val requiredModels: List<AiModelRequirement> = emptyList(),
    val installedModels: List<String> = emptyList()
) {
    val isReady: Boolean get() = availability == AiAvailability.READY
}

data class AiFeatureDescription(val title: String, val summary: String)

/**
 * A local-only inventory of what can actually run. This class never downloads, refreshes,
 * or otherwise contacts the network. Call [all] off the main thread: PackManager's installed
 * registry is read from disk. Download prompts and consent remain the UI's responsibility.
 */
object AiFeatureRegistry {
    private const val WHISPER_LICENSE = "OpenAI Whisper model: MIT; sherpa-onnx packaging: Apache-2.0"
    private const val MEDIAPIPE_LICENSE = "Apache-2.0 (MediaPipe Tasks/model distribution; verify model-card terms)"

    val knownRequirements: Map<AiFeatureId, List<AiModelRequirement>> = mapOf(
        AiFeatureId.BACKGROUND_REPLACE to listOf(
            AiModelRequirement(
                BackgroundReplaceEngine.PACK_ID,
                BackgroundReplaceEngine.MODEL_FILE,
                MEDIAPIPE_LICENSE,
                "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite"
            )
        ),
        AiFeatureId.SUBTITLES to listOf(
            AiModelRequirement(WhisperCaptionEngine.PACK_ENCODER, "tiny-encoder.int8.onnx", WHISPER_LICENSE,
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny"),
            AiModelRequirement(WhisperCaptionEngine.PACK_DECODER, "tiny-decoder.int8.onnx", WHISPER_LICENSE,
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny"),
            AiModelRequirement(WhisperCaptionEngine.PACK_TOKENS, "tiny-tokens.txt", WHISPER_LICENSE,
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny")
        ),
        AiFeatureId.MEDIAPIPE_FACE to listOf(missingPack("mediapipe-face-landmarker.task", "https://developers.google.com/mediapipe")),
        AiFeatureId.MEDIAPIPE_POSE to listOf(missingPack("mediapipe-pose-landmarker.task", "https://developers.google.com/mediapipe")),
        AiFeatureId.MEDIAPIPE_HAND to listOf(missingPack("mediapipe-hand-landmarker.task", "https://developers.google.com/mediapipe")),
        AiFeatureId.FACE_BLUR to listOf(missingPack("mediapipe-face-landmarker.task", "https://developers.google.com/mediapipe")),
        AiFeatureId.FACE_TRACKING to listOf(missingPack("mediapipe-face-landmarker.task", "https://developers.google.com/mediapipe")),
        AiFeatureId.POSE_EFFECTS to listOf(missingPack("mediapipe-pose-landmarker.task", "https://developers.google.com/mediapipe")),
        AiFeatureId.SKELETON_OVERLAY to listOf(missingPack("mediapipe-pose-landmarker.task", "https://developers.google.com/mediapipe")),
        AiFeatureId.GESTURE_CONTROL to listOf(missingPack("mediapipe-hand-landmarker.task", "https://developers.google.com/mediapipe")),
        AiFeatureId.BACKGROUND_BLUR to listOf(AiModelRequirement(
            BackgroundReplaceEngine.PACK_ID, BackgroundReplaceEngine.MODEL_FILE, MEDIAPIPE_LICENSE,
            "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite"
        )),
        AiFeatureId.ONNX_BACKGROUND_REMOVAL to listOf(missingOnnx("onnx-background-removal.onnx")),
        AiFeatureId.ONNX_UPSCALING to listOf(missingOnnx("onnx-upscale.onnx")),
        AiFeatureId.ONNX_STYLE_TRANSFER to listOf(missingOnnx("onnx-style-transfer.onnx")),
        AiFeatureId.FACE_AWARE_REFRAME to listOf(missingPack("mediapipe-face-landmarker.task", "https://developers.google.com/mediapipe")),
        AiFeatureId.WORD_TIMED_SUBTITLES to listOf(
            AiModelRequirement("forced-aligner-not-configured", "", "Not supplied", "")
        ),
        AiFeatureId.CONTENT_MODERATION to listOf(
            AiModelRequirement("validated-moderation-classifier-not-configured", "", "Not supplied", "")
        )
    )

    private fun missingPack(file: String, source: String) = AiModelRequirement(
        "not-configured:$file", file, "Model license must be reviewed before distribution", source
    )

    private fun missingOnnx(file: String) = AiModelRequirement(
        "not-configured:$file", file, "Model license must be reviewed before distribution", ""
    )

    val descriptions: Map<AiFeatureId, AiFeatureDescription> = mapOf(
        AiFeatureId.SMART_TRIM to AiFeatureDescription("Smart trim", "Ranks sampled video windows using measured image activity, sharpness, exposure and optional audio energy."),
        AiFeatureId.SCENE_ANALYSIS to AiFeatureDescription("Scene analysis", "Finds candidate cuts from sampled luma histogram and pixel differences."),
        AiFeatureId.STYLE_ANALYSIS to AiFeatureDescription("Style analysis", "Summarizes sampled color, contrast and saturation with transparent deterministic rules."),
        AiFeatureId.AUTO_CROP to AiFeatureDescription("Auto crop", "Computes a rule-based crop suggestion from luma gradients; this is not subject or face detection."),
        AiFeatureId.FACE_AWARE_REFRAME to AiFeatureDescription("Face-aware reframe", "Follows locally detected face boxes; requires an installed Face Landmarker task model."),
        AiFeatureId.FACE_BLUR to AiFeatureDescription("Face blur", "Requires local face landmarks and a renderer; the detector adapter is present, no model pack is bundled."),
        AiFeatureId.FACE_TRACKING to AiFeatureDescription("Face tracking", "Per-frame MediaPipe face landmarks; temporal video tracking and an installed model are required."),
        AiFeatureId.BACKGROUND_BLUR to AiFeatureDescription("Background blur", "Reuses the existing MediaPipe person-segmentation background engine and installed model."),
        AiFeatureId.POSE_EFFECTS to AiFeatureDescription("Pose effects", "Requires locally installed MediaPipe Pose Landmarker model and an effect renderer."),
        AiFeatureId.SKELETON_OVERLAY to AiFeatureDescription("Skeleton overlay", "Requires pose landmarks and a timeline renderer; no pose model is bundled."),
        AiFeatureId.GESTURE_CONTROL to AiFeatureDescription("Gesture control", "Requires locally installed MediaPipe Hand Landmarker model and validated gesture rules."),
        AiFeatureId.SUBTITLES to AiFeatureDescription("Auto subtitles", "Offline Whisper inference from installed model packs; timestamps are segment-level."),
        AiFeatureId.WORD_TIMED_SUBTITLES to AiFeatureDescription("Word-timed subtitles", "Unavailable without a validated forced aligner; no approximate word boundaries are emitted."),
        AiFeatureId.BACKGROUND_REPLACE to AiFeatureDescription("Background replace", "Reuses the existing MediaPipe segmentation and frame compositing engine."),
        AiFeatureId.MEDIAPIPE_FACE to AiFeatureDescription("Face landmarks", "Image-mode MediaPipe Face Landmarker adapter; no model pack is installed or bundled."),
        AiFeatureId.MEDIAPIPE_POSE to AiFeatureDescription("Pose landmarks", "Image-mode MediaPipe Pose Landmarker adapter; no model pack is installed or bundled."),
        AiFeatureId.MEDIAPIPE_HAND to AiFeatureDescription("Hand landmarks", "Image-mode MediaPipe Hand Landmarker adapter; no model pack is installed or bundled."),
        AiFeatureId.ONNX_BACKGROUND_REMOVAL to AiFeatureDescription("ONNX background removal", "Requires a configured, licensed ONNX segmentation model; none is installed."),
        AiFeatureId.ONNX_UPSCALING to AiFeatureDescription("ONNX upscaling", "Requires a configured, licensed ONNX super-resolution model; none is installed."),
        AiFeatureId.ONNX_STYLE_TRANSFER to AiFeatureDescription("ONNX style transfer", "Requires a configured, licensed ONNX image model; none is installed."),
        AiFeatureId.CONTENT_MODERATION to AiFeatureDescription("Content safety", "Fail-closed; unavailable until a validated sexual/violent/hateful-content classifier is installed.")
    )

    fun all(context: Context): List<AiFeatureAvailability> = AiFeatureId.entries.map { get(context, it) }

    fun get(context: Context, id: AiFeatureId): AiFeatureAvailability {
        val requirements = knownRequirements[id].orEmpty()
        if (id == AiFeatureId.SMART_TRIM || id == AiFeatureId.SCENE_ANALYSIS || id == AiFeatureId.AUTO_CROP) {
            return AiFeatureAvailability(id, AiAvailability.READY,
                "Available: deterministic local video-signal analysis; this is not a learned model.")
        }
        if (id == AiFeatureId.STYLE_ANALYSIS) {
            return AiFeatureAvailability(id, AiAvailability.READY,
                "Available: deterministic local color/contrast analysis; learned style transfer is separate.")
        }
        if (id == AiFeatureId.WORD_TIMED_SUBTITLES) {
            return AiFeatureAvailability(id, AiAvailability.ENGINE_UNAVAILABLE,
                "Whisper provides segment-level timestamp tokens here, not validated word alignment. No word times are fabricated.", requirements)
        }
        if (id == AiFeatureId.CONTENT_MODERATION) {
            return AiFeatureAvailability(id, AiAvailability.ENGINE_UNAVAILABLE,
                "Unavailable: no validated sexual/violent/hateful-content classifier is configured. Safety checks fail closed; pixel heuristics are not moderation.", requirements)
        }
        val installed = requirements.filter { req ->
            !req.id.startsWith("not-configured:") && PackManager.isDownloaded(context, req.id) &&
                File(PackManager.packDir(context, req.id), req.fileName).isFile
        }
        if (requirements.any { it.id.startsWith("not-configured:") }) {
            return AiFeatureAvailability(id, AiAvailability.MODEL_REQUIRED,
                "Unavailable until a reviewed, licensed model pack is configured and installed. No network download is started automatically.", requirements)
        }
        val ready = installed.size == requirements.size && requirements.isNotEmpty()
        return AiFeatureAvailability(id, if (ready) AiAvailability.READY else AiAvailability.MODEL_MISSING,
            if (ready) "Required model files are installed locally." else "Required model files are not installed locally. Download only after an explicit user action.",
            requirements, installed.map { it.id })
    }
}
