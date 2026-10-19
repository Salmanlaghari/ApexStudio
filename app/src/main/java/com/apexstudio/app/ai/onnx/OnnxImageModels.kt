package com.apexstudio.app.ai.onnx

import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.nio.FloatBuffer
import java.util.concurrent.CancellationException
import kotlin.coroutines.coroutineContext

enum class ImageTask { BACKGROUND_REMOVAL, SUPER_RESOLUTION, STYLE_TRANSFER }

/** No generic caller-supplied record is trusted; add entries only after model/license/behavior review. */
internal object ImageModelReviewCatalog {
    private val reviewedSha256ToTask: Map<String, ImageTask> = emptyMap()
    fun isReviewed(artifact: ImageModelArtifact): Boolean =
        reviewedSha256ToTask[artifact.sha256.lowercase()] == artifact.task
}

/** Reviewed identity for exact model bytes; shape/name alone never establishes model semantics. */
data class ImageModelArtifact(
    val modelId: String,
    val version: String,
    val sha256: String,
    val sourceUrl: String,
    val license: String,
    val task: ImageTask,
) {
    init {
        require(modelId.isNotBlank() && version.isNotBlank())
        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Model SHA-256 must contain 64 hex digits" }
        require(sourceUrl.startsWith("https://") && license.isNotBlank())
    }
}

/** Explicit NCHW RGB model contract. Dimensions are fixed by the declared local model pack. */
data class ImageModelContract(
    val inputName: String,
    val outputName: String,
    val width: Int,
    val height: Int,
    val inputRange: FloatRange = FloatRange.ZERO_TO_ONE,
    val outputRange: FloatRange = FloatRange.ZERO_TO_ONE,
    val outputChannels: Int = 3,
    val outputWidth: Int = width,
    val outputHeight: Int = height,
    val task: ImageTask? = null,
) {
    init {
        require(inputName.isNotBlank() && outputName.isNotBlank())
        require(width > 0 && height > 0 && outputChannels in setOf(1, 3, 4))
        require(outputWidth > 0 && outputHeight > 0)
        require(pixelCount(width, height) <= MAX_IMAGE_PIXELS) { "Input image tensor exceeds the safe pixel limit" }
        // Match nchwToBitmap: a contract must not advertise readiness for an output the renderer
        // will reject after expensive inference. Full 4K needs a separate tiled pipeline.
        require(pixelCount(outputWidth, outputHeight) <= MAX_IMAGE_PIXELS) { "Output image exceeds the safe pixel limit" }
        require(pixelCount(outputWidth, outputHeight) <= MAX_TENSOR_ELEMENTS / outputChannels) {
            "Output image tensor exceeds the safe element limit"
        }
    }

    val inputShape: LongArray get() = longArrayOf(1, 3, height.toLong(), width.toLong())
    val outputShape: LongArray get() = longArrayOf(1, outputChannels.toLong(), outputHeight.toLong(), outputWidth.toLong())

    /** Validate a model's declared tensors before it is ever used on an image. -1 denotes dynamic. */
    fun validateShapes(input: LongArray, output: LongArray): String? =
        shapeMismatch("input", input, inputShape) ?: shapeMismatch("output", output, outputShape)

    private fun shapeMismatch(label: String, actual: LongArray, expected: LongArray): String? {
        if (actual.size != expected.size) return "$label rank ${actual.size}; expected ${expected.size} (NCHW)"
        for (i in expected.indices) {
            if (actual[i] == 0L || actual[i] < -1L) return "$label dimension $i has invalid value ${actual[i]}"
            if (actual[i] != -1L && actual[i] != expected[i])
                return "$label dimension $i is ${actual[i]}; expected ${expected[i]}"
        }
        return null
    }

    companion object {
        private const val MAX_IMAGE_PIXELS = 4_194_304L
        private const val MAX_TENSOR_ELEMENTS = 16_777_216L

        private fun pixelCount(width: Int, height: Int): Long = width.toLong() * height.toLong()
    }
}

enum class FloatRange(val min: Float, val max: Float) {
    ZERO_TO_ONE(0f, 1f), MINUS_ONE_TO_ONE(-1f, 1f), BYTE_0_TO_255(0f, 255f)
}

sealed interface ModelAvailability {
    data class Ready(val inputShape: List<Long>, val outputShape: List<Long>, val modelId: String, val sha256: String) : ModelAvailability
    data class Unavailable(val reason: String) : ModelAvailability
}

data class ModelProgress(val fraction: Float, val stage: String)

class LocalModelException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** ONNX Runtime image-to-image adapters. No URL, downloader, or implicit fallback exists here. */
object OnnxImageModels {
    private val env: OrtEnvironment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OrtEnvironment.getEnvironment() }

    /** All image-to-image tasks process user pixels and must pass the global fail-closed policy. */
    internal fun protectedActionFor(task: ImageTask): ProtectedAiAction = when (task) {
        ImageTask.BACKGROUND_REMOVAL -> ProtectedAiAction.ONNX_BACKGROUND_REMOVAL
        ImageTask.SUPER_RESOLUTION -> ProtectedAiAction.ONNX_UPSCALING
        ImageTask.STYLE_TRANSFER -> ProtectedAiAction.ONNX_STYLE_TRANSFER
    }

    /** Introspect the local file only; absent/unreadable/mismatched files are reported as unavailable. */
    suspend fun inspect(modelFile: File, contract: ImageModelContract, artifact: ImageModelArtifact? = null): ModelAvailability = withContext(Dispatchers.IO) {
        if (artifact == null) return@withContext ModelAvailability.Unavailable("No reviewed model manifest is configured; filename and tensor shape are not proof of model identity")
        if (contract.task == null || contract.task != artifact.task) return@withContext ModelAvailability.Unavailable("Reviewed model task does not match the image adapter")
        if (!ImageModelReviewCatalog.isReviewed(artifact)) return@withContext ModelAvailability.Unavailable("Model identity is not in the app-reviewed artifact catalog")
        if (!modelFile.isFile) return@withContext ModelAvailability.Unavailable("Model file is not installed: ${modelFile.name}")
        try {
            val canonical = modelFile.canonicalFile
            if (!canonical.isFile || canonical.length() == 0L) return@withContext ModelAvailability.Unavailable("Model file is empty or unavailable")
            val actualDigest = sha256(canonical)
            if (!actualDigest.equals(artifact.sha256, ignoreCase = true)) return@withContext ModelAvailability.Unavailable("Model SHA-256 does not match the reviewed manifest")
            openSession(canonical).use { session ->
                val input = (session.inputInfo[contract.inputName]?.info as? TensorInfo)?.shape
                    ?: return@withContext ModelAvailability.Unavailable("Missing ONNX input '${contract.inputName}'")
                val output = (session.outputInfo[contract.outputName]?.info as? TensorInfo)?.shape
                    ?: return@withContext ModelAvailability.Unavailable("Missing ONNX output '${contract.outputName}'")
                val inputInfo = session.inputInfo.getValue(contract.inputName).info as TensorInfo
                val outputInfo = session.outputInfo.getValue(contract.outputName).info as TensorInfo
                if (inputInfo.type != OnnxJavaType.FLOAT || outputInfo.type != OnnxJavaType.FLOAT)
                    return@withContext ModelAvailability.Unavailable("Image model input and output must be float32 tensors")
                val mismatch = contract.validateShapes(input, output)
                if (mismatch != null) ModelAvailability.Unavailable("Incompatible ONNX model: $mismatch")
                else ModelAvailability.Ready(input.toList(), output.toList(), artifact.modelId, actualDigest)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ModelAvailability.Unavailable("Cannot open local ONNX model: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Run one local image. Cancellation is checked before and after native inference; ONNX Runtime's
     * synchronous native run itself is not interruptible. Output is never fabricated on failure. */
    suspend fun runImage(
        modelFile: File,
        bitmap: Bitmap,
        contract: ImageModelContract,
        artifact: ImageModelArtifact? = null,
        onProgress: (ModelProgress) -> Unit = {},
    ): Bitmap = withContext(Dispatchers.Default) {
        val task = contract.task ?: throw LocalModelException("Image model task is not declared; refusing inference")
        GlobalModerationEnforcement.requireApproved(protectedActionFor(task))
        coroutineContext.ensureActive()
        onProgress(ModelProgress(0.05f, "validating local model"))
        val availability = inspect(modelFile, contract, artifact)
        if (availability !is ModelAvailability.Ready) {
            throw LocalModelException((availability as ModelAvailability.Unavailable).reason)
        }
        coroutineContext.ensureActive()
        val input = imageToNchw(bitmap, contract.width, contract.height, contract.inputRange)
        onProgress(ModelProgress(0.2f, "preprocessing image"))
        val canonical = modelFile.canonicalFile
        openSession(canonical).use { session ->
            val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), contract.inputShape)
            tensor.use {
                coroutineContext.ensureActive()
                onProgress(ModelProgress(0.3f, "running on-device ONNX inference"))
                session.run(mapOf(contract.inputName to tensor)).use { result ->
                    coroutineContext.ensureActive()
                    val outputTensor = result.get(contract.outputName).orElse(null) as? OnnxTensor
                        ?: throw LocalModelException("ONNX output '${contract.outputName}' is not a tensor")
                    outputTensor.use {
                        val shape = outputTensor.info.shape
                        val mismatch = contract.validateShapes(contract.inputShape, shape)
                        if (mismatch != null) throw LocalModelException("Invalid model output: $mismatch")
                        if (!shape.contentEquals(contract.outputShape))
                            throw LocalModelException("Runtime output shape ${shape.contentToString()} does not match ${contract.outputShape.contentToString()}")
                        val outputBuffer = outputTensor.floatBuffer
                        val output = FloatArray(outputBuffer.remaining())
                        outputBuffer.get(output)
                        val expected = Math.multiplyExact(contract.outputChannels,
                            Math.multiplyExact(contract.outputWidth, contract.outputHeight))
                        if (output.size != expected) throw LocalModelException("Output contains ${output.size} values; expected $expected")
                        if (output.any { !it.isFinite() }) throw LocalModelException("Output contains non-finite values")
                        coroutineContext.ensureActive()
                        val image = nchwToBitmap(output, contract.outputChannels, contract.outputWidth,
                            contract.outputHeight, contract.outputRange)
                        onProgress(ModelProgress(1f, "complete"))
                        image
                    }
                }
            }
        }
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun openSession(file: File): OrtSession = OrtSession.SessionOptions().use { options ->
        env.createSession(file.absolutePath, options)
    }

    internal fun imageToNchw(bitmap: Bitmap, width: Int, height: Int, range: FloatRange): FloatArray {
        require(width > 0 && height > 0 && width.toLong() * height <= 4_194_304L) { "Input dimensions exceed the safe pixel limit" }
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            val plane = Math.multiplyExact(width, height)
            val pixels = IntArray(plane)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            val out = FloatArray(Math.multiplyExact(3, plane))
            for (i in pixels.indices) {
                val p = pixels[i]
                out[i] = normalize((p shr 16) and 255, range)
                out[plane + i] = normalize((p shr 8) and 255, range)
                out[2 * plane + i] = normalize(p and 255, range)
            }
            return out
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun normalize(channel: Int, range: FloatRange): Float =
        range.min + channel / 255f * (range.max - range.min)

    internal fun nchwToBitmap(values: FloatArray, channels: Int, width: Int, height: Int, range: FloatRange): Bitmap {
        require(channels == 1 || channels == 3 || channels == 4) { "Output must have one grayscale, RGB, or RGBA channels" }
        require(width > 0 && height > 0 && width.toLong() * height <= 4_194_304L) { "Output dimensions exceed the safe pixel limit" }
        val plane = Math.multiplyExact(width, height)
        require(values.size == Math.multiplyExact(channels, plane)) { "Output value count does not match image dimensions" }
        require(values.all { it.isFinite() }) { "Output contains non-finite values" }
        val pixels = IntArray(plane)
        for (i in 0 until plane) {
            fun byte(v: Float): Int = (((v.coerceIn(range.min, range.max) - range.min) /
                (range.max - range.min) * 255f).toInt()).coerceIn(0, 255)
            val r = byte(values[i])
            val g = if (channels == 1) r else byte(values[plane + i])
            val b = if (channels == 1) r else byte(values[2 * plane + i])
            val a = if (channels >= 4) byte(values[3 * plane + i]) else 255
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}

/** Conventional fixed-shape contracts for task-specific model families. */
object ImageModelContracts {
    fun backgroundMask(inputName: String, outputName: String, width: Int, height: Int) =
        ImageModelContract(inputName, outputName, width, height, outputChannels = 1, task = ImageTask.BACKGROUND_REMOVAL)

    fun superResolution(inputName: String, outputName: String, width: Int, height: Int, scale: Int) : ImageModelContract {
        require(scale > 1)
        return ImageModelContract(inputName, outputName, width, height,
            outputWidth = Math.multiplyExact(width, scale), outputHeight = Math.multiplyExact(height, scale), task = ImageTask.SUPER_RESOLUTION)
    }

    fun styleTransfer(inputName: String, outputName: String, width: Int, height: Int, range: FloatRange = FloatRange.MINUS_ONE_TO_ONE) =
        ImageModelContract(inputName, outputName, width, height, inputRange = range, outputRange = range, task = ImageTask.STYLE_TRANSFER)
}
