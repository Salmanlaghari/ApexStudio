package com.apexstudio.app.ai.onnx

import android.graphics.Bitmap
import java.io.File

/** On-device image adapters require a caller-installed compatible ONNX file; no model is bundled or fetched. */
class BackgroundRemovalAdapter(
    private val modelFile: File,
    private val contract: ImageModelContract,
) {
    init { require(contract.outputChannels == 1) { "Background mask model must return one NCHW channel" } }

    suspend fun availability(): ModelAvailability = OnnxImageModels.inspect(modelFile, contract)

    /** Returns the model's single-channel probability/mask as a grayscale bitmap. It does not
     * invent a segmentation when the model is missing or incompatible. */
    suspend fun inferMask(source: Bitmap, onProgress: (ModelProgress) -> Unit = {}): Bitmap =
        OnnxImageModels.runImage(modelFile, source, contract, onProgress)
}

class SuperResolutionAdapter(
    private val modelFile: File,
    private val contract: ImageModelContract,
) {
    init { require(contract.outputChannels >= 3) { "Super-resolution model must return RGB channels" } }
    suspend fun availability(): ModelAvailability = OnnxImageModels.inspect(modelFile, contract)
    suspend fun upscale(source: Bitmap, onProgress: (ModelProgress) -> Unit = {}): Bitmap =
        OnnxImageModels.runImage(modelFile, source, contract, onProgress)
}

class StyleTransferAdapter(
    private val modelFile: File,
    private val contract: ImageModelContract,
) {
    init { require(contract.outputChannels >= 3) { "Style-transfer model must return RGB channels" } }
    suspend fun availability(): ModelAvailability = OnnxImageModels.inspect(modelFile, contract)
    suspend fun stylize(source: Bitmap, onProgress: (ModelProgress) -> Unit = {}): Bitmap =
        OnnxImageModels.runImage(modelFile, source, contract, onProgress)
}
