package com.apexstudio.app.ai.onnx

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Pinned local Candy-9 model pack. Materialization is file-only: there is no downloader or
 * remote fallback. Call [materialize] off the main thread. The resulting adapter still checks the
 * global moderation gate before any user-image inference.
 */
object CandyStyleTransferPack {
    const val MODEL_ID = "onnx-model-zoo-candy-9"
    const val REVISION = "f24fcf558bd4564360f41cf3b5321975a33f962f"
    const val SHA256 = "9d11a3529d1e547da6ae07201d93484dbab2ec0a3614535752c8f40f0fe2968a"
    const val ASSET_PATH = "models/style_transfer/candy-9.onnx"
    const val ARTIFACT_SOURCE_URL =
        "https://huggingface.co/onnxmodelzoo/candy-9/resolve/$REVISION/candy-9.onnx"

    val artifact = ImageModelArtifact(
        modelId = MODEL_ID,
        version = REVISION,
        sha256 = SHA256,
        sourceUrl = ARTIFACT_SOURCE_URL,
        license = "BSD-3-Clause model SPDX / Apache-2.0 Hub metadata; see bundled notices",
        task = ImageTask.STYLE_TRANSFER,
    )
    /** Actual ONNX Runtime metadata: fixed 224-square NCHW RGB, float32 in byte range [0,255]. */
    val contract = ImageModelContracts.styleTransfer(
        inputName = "input1",
        outputName = "output1",
        width = 224,
        height = 224,
        range = FloatRange.BYTE_0_TO_255,
    )

    private val lock = Any()

    /** Copy the bundled, checksum-pinned asset into app-private storage without any network I/O. */
    fun materialize(context: Context): File = synchronized(lock) {
        val directory = File(context.filesDir, "ai-models/style-transfer")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create private style-transfer model directory")
        }
        val target = File(directory, "candy-9.onnx")
        if (target.isFile && OnnxImageModels.sha256(target) == SHA256) return@synchronized target

        val temporary = File(directory, "candy-9.onnx.part")
        try {
            context.assets.open(ASSET_PATH).use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (OnnxImageModels.sha256(temporary) != SHA256) {
                throw IOException("Bundled Candy-9 asset SHA-256 mismatch")
            }
            if (target.exists() && !target.delete()) {
                throw IOException("Cannot replace invalid cached Candy-9 model")
            }
            if (!temporary.renameTo(target)) {
                throw IOException("Cannot install verified Candy-9 model")
            }
            target
        } finally {
            temporary.delete()
        }
    }

    /** Build the existing guarded adapter over a verified bundled file. Call off the main thread. */
    fun adapter(context: Context): StyleTransferAdapter =
        StyleTransferAdapter(materialize(context), artifact, contract)
}