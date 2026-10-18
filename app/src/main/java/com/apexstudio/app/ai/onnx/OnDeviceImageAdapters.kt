package com.apexstudio.app.ai.onnx

import android.graphics.Bitmap
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import java.io.File

/** On-device image adapters require a caller-installed compatible ONNX file; no model is bundled or fetched. */
class BackgroundRemovalAdapter(
    private val modelFile: File,
    private val artifact: ImageModelArtifact,
    private val contract: ImageModelContract,
) {
    init { require(contract.task == ImageTask.BACKGROUND_REMOVAL)
        require(artifact.task == ImageTask.BACKGROUND_REMOVAL)
        require(contract.outputChannels == 1) { "Background mask model must return one NCHW channel" } }

    suspend fun availability(): ModelAvailability = OnnxImageModels.inspect(modelFile, contract, artifact)

    /** Returns the model's single-channel probability/mask as a grayscale bitmap. It does not
     * invent a segmentation when the model is missing or incompatible. */
    suspend fun inferMask(source: Bitmap, onProgress: (ModelProgress) -> Unit = {}): Bitmap =
        OnnxImageModels.runImage(modelFile, source, contract, artifact, onProgress)
}

class SuperResolutionAdapter(
    private val modelFile: File,
    private val artifact: ImageModelArtifact,
    private val contract: ImageModelContract,
) {
    init { require(contract.task == ImageTask.SUPER_RESOLUTION)
        require(artifact.task == ImageTask.SUPER_RESOLUTION)
        require(contract.outputChannels >= 3) { "Super-resolution model must return RGB channels" } }
    suspend fun availability(): ModelAvailability = OnnxImageModels.inspect(modelFile, contract, artifact)
    suspend fun upscale(source: Bitmap, onProgress: (ModelProgress) -> Unit = {}): Bitmap {
        // Keep the public ONNX path behind the same fail-closed policy as the dedicated upscaler.
        GlobalModerationEnforcement.requireApproved(ProtectedAiAction.ONNX_UPSCALING)
        return OnnxImageModels.runImage(modelFile, source, contract, artifact, onProgress)
    }
}

class StyleTransferAdapter(
    private val modelFile: File,
    private val artifact: ImageModelArtifact,
    private val contract: ImageModelContract,
) {
    init { require(contract.task == ImageTask.STYLE_TRANSFER)
        require(artifact.task == ImageTask.STYLE_TRANSFER)
        require(contract.outputChannels >= 3) { "Style-transfer model must return RGB channels" } }
    suspend fun availability(): ModelAvailability = OnnxImageModels.inspect(modelFile, contract, artifact)
    suspend fun stylize(source: Bitmap, onProgress: (ModelProgress) -> Unit = {}): Bitmap =
        OnnxImageModels.runImage(modelFile, source, contract, artifact, onProgress)
}
