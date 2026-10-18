package com.apexstudio.app.ai.onnx

import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext

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
) {
    init {
        require(inputName.isNotBlank() && outputName.isNotBlank())
        require(width > 0 && height > 0 && outputChannels in 1..4)
        require(outputWidth > 0 && outputHeight > 0)
    }

    val inputShape: LongArray get() = longArrayOf(1, 3, height.toLong(), width.toLong())
    val outputShape: LongArray get() = longArrayOf(1, outputChannels.toLong(), outputHeight.toLong(), outputWidth.toLong())

    /** Validate a model's declared tensors before it is ever used on an image. -1 denotes dynamic. */
    fun validateShapes(input: LongArray, output: LongArray): String? =
        shapeMismatch("input", input, inputShape) ?: shapeMismatch("output", output, outputShape)

    private fun shapeMismatch(label: String, actual: LongArray, expected: LongArray): String? {
        if (actual.size != expected.size) return "$label rank ${actual.size}; expected ${expected.size} (NCHW)"
        for (i in expected.indices) {
            if (actual[i] > 0 && actual[i] != expected[i])
                return "$label dimension $i is ${actual[i]}; expected ${expected[i]}"
        }
        return null
    }
}

enum class FloatRange(val min: Float, val max: Float) {
    ZERO_TO_ONE(0f, 1f), MINUS_ONE_TO_ONE(-1f, 1f)
}

sealed interface ModelAvailability {
    data class Ready(val inputShape: List<Long>, val outputShape: List<Long>) : ModelAvailability
    data class Unavailable(val reason: String) : ModelAvailability
}

data class ModelProgress(val fraction: Float, val stage: String)

class LocalModelException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** ONNX Runtime image-to-image adapters. No URL, downloader, or implicit fallback exists here. */
object OnnxImageModels {
    private val env: OrtEnvironment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OrtEnvironment.getEnvironment() }

    /** Introspect the local file only; absent/unreadable/mismatched files are reported as unavailable. */
    suspend fun inspect(modelFile: File, contract: ImageModelContract): ModelAvailability = withContext(Dispatchers.IO) {
        if (!modelFile.isFile) return@withContext ModelAvailability.Unavailable("Model file is not installed: ${modelFile.name}")
        try {
            val canonical = modelFile.canonicalFile
            if (!canonical.isFile || canonical.length() == 0L) return@withContext ModelAvailability.Unavailable("Model file is empty or unavailable")
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
                else ModelAvailability.Ready(input.toList(), output.toList())
            }
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
        onProgress: (ModelProgress) -> Unit = {},
    ): Bitmap = withContext(Dispatchers.Default) {
        coroutineContext.ensureActive()
        onProgress(ModelProgress(0.05f, "validating local model"))
        val availability = inspect(modelFile, contract)
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
                        val outputBuffer = outputTensor.floatBuffer
                        val output = FloatArray(outputBuffer.remaining())
                        outputBuffer.get(output)
                        val expected = contract.outputChannels * contract.outputWidth * contract.outputHeight
                        if (output.size != expected) throw LocalModelException("Output contains ${output.size} values; expected $expected")
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

    private fun openSession(file: File): OrtSession = OrtSession.SessionOptions().use { options ->
        env.createSession(file.absolutePath, options)
    }

    internal fun imageToNchw(bitmap: Bitmap, width: Int, height: Int, range: FloatRange): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()
        val out = FloatArray(3 * width * height)
        val plane = width * height
        for (i in pixels.indices) {
            val p = pixels[i]
            out[i] = normalize((p shr 16) and 255, range)
            out[plane + i] = normalize((p shr 8) and 255, range)
            out[2 * plane + i] = normalize(p and 255, range)
        }
        return out
    }

    private fun normalize(channel: Int, range: FloatRange): Float =
        range.min + channel / 255f * (range.max - range.min)

    internal fun nchwToBitmap(values: FloatArray, channels: Int, width: Int, height: Int, range: FloatRange): Bitmap {
        require(channels == 1 || channels >= 3) { "Output must have one grayscale or at least three RGB channels" }
        val plane = width * height
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
        ImageModelContract(inputName, outputName, width, height, outputChannels = 1)

    fun superResolution(inputName: String, outputName: String, width: Int, height: Int, scale: Int) : ImageModelContract {
        require(scale > 1)
        return ImageModelContract(inputName, outputName, width, height,
            outputWidth = Math.multiplyExact(width, scale), outputHeight = Math.multiplyExact(height, scale))
    }

    fun styleTransfer(inputName: String, outputName: String, width: Int, height: Int, range: FloatRange = FloatRange.MINUS_ONE_TO_ONE) =
        ImageModelContract(inputName, outputName, width, height, inputRange = range, outputRange = range)
}
