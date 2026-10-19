package com.apexstudio.app.ai.onnx

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Learned MODNet photographic portrait matting using the pinned Apache-2.0 ONNX artifact.
 * `inferMask` is a protected user-content action and remains blocked by the app's global
 * moderation gate until that policy is explicitly approved elsewhere.
 */
class ModNetBackgroundRemovalAdapter(private val modelFile: File) {
    companion object {
        const val MODEL_ID = "modnet-photographic"
        const val MODEL_VERSION = "35198d10c55804c519b2a7a182a3050228018735"
        const val MODEL_SHA256 = "5069a5e306b9f5e9f4f2b0360264c9f8ea13b257c7c39943c7cf6a2ec3a102ae"
        const val MODEL_SIZE_BYTES = 25_969_398L
        const val MODEL_ASSET = "models/background_removal/modnet_photographic.onnx"
        const val INPUT_NAME = "input"
        const val OUTPUT_NAME = "output"
        const val BASE_SHORT_SIDE = 512
        const val DIMENSION_ALIGNMENT = 32
        const val MAX_MODEL_PIXELS = 1_048_576
        const val MAX_MODEL_SIDE = 2_048
        const val MAX_SOURCE_PIXELS = 8_388_608L
        const val MAX_ASSET_BYTES = 50_000_000L

        private val env: OrtEnvironment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            OrtEnvironment.getEnvironment()
        }

        /** Install only the exact bundled bytes; no download, substitution, or learned fallback. */
        @Synchronized
        fun fromBundledAssets(context: Context, cacheDir: File = context.cacheDir): ModNetBackgroundRemovalAdapter {
            val target = File(cacheDir, "models/$MODEL_ID.onnx")
            if (!target.isFile || target.length() != MODEL_SIZE_BYTES ||
                OnnxImageModels.sha256(target) != MODEL_SHA256
            ) {
                val parent = target.parentFile ?: error("Cannot resolve model cache directory")
                check(parent.isDirectory || parent.mkdirs()) { "Cannot create model cache directory" }
                val temp = File.createTempFile("modnet-", ".tmp", parent)
                try {
                    context.assets.open(MODEL_ASSET).use { input ->
                        temp.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    check(temp.length() == MODEL_SIZE_BYTES && OnnxImageModels.sha256(temp) == MODEL_SHA256) {
                        "Bundled MODNet model size or SHA-256 mismatch"
                    }
                    if (target.exists()) check(target.delete()) { "Cannot replace invalid cached MODNet model" }
                    check(temp.renameTo(target)) { "Cannot atomically publish verified MODNet model" }
                } finally {
                    temp.delete()
                }
            }
            check(target.length() == MODEL_SIZE_BYTES && OnnxImageModels.sha256(target) == MODEL_SHA256) {
                "Cached MODNet model size or SHA-256 mismatch"
            }
            return ModNetBackgroundRemovalAdapter(target)
        }

        /** MODNet resizes adaptively, preserves aspect ratio, and aligns both axes to 32 pixels. */
        internal fun inputDimensions(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
            require(sourceWidth > 0 && sourceHeight > 0) { "Source bitmap must be non-empty" }
            require(sourceWidth.toLong() * sourceHeight <= MAX_SOURCE_PIXELS) {
                "Source bitmap exceeds the bounded background-removal pixel limit"
            }
            val longer = maxOf(sourceWidth, sourceHeight)
            val shorter = minOf(sourceWidth, sourceHeight)
            val baseScale = if (longer < BASE_SHORT_SIDE || shorter > BASE_SHORT_SIDE) {
                BASE_SHORT_SIDE.toDouble() / shorter
            } else 1.0
            var width = maxOf(1, (sourceWidth * baseScale).toInt())
            var height = maxOf(1, (sourceHeight * baseScale).toInt())
            val boundScale = minOf(
                1.0,
                MAX_MODEL_SIDE.toDouble() / maxOf(width, height),
                sqrt(MAX_MODEL_PIXELS.toDouble() / (width.toDouble() * height)),
            )
            width = alignedDimension(width * boundScale)
            height = alignedDimension(height * boundScale)
            require(width.toLong() * height <= MAX_MODEL_PIXELS && maxOf(width, height) <= MAX_MODEL_SIDE) {
                "Could not bound MODNet input geometry"
            }
            return width to height
        }

        private fun alignedDimension(value: Double): Int {
            val aligned = (floor(value / DIMENSION_ALIGNMENT) * DIMENSION_ALIGNMENT).toInt()
            require(aligned >= DIMENSION_ALIGNMENT) { "Source aspect ratio is too extreme for MODNet" }
            return aligned
        }

        internal fun alphaByte(probability: Float): Int {
            require(probability.isFinite()) { "MODNet matte contains a non-finite value" }
            return (probability.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }
    }

    init {
        require(modelFile.isFile) { "MODNet model is missing: ${modelFile.name}" }
    }

    /** Verifies file identity and the dynamic NCHW RGB -> one-channel matte ONNX contract. */
    suspend fun availability(): ModelAvailability = withContext(Dispatchers.IO) {
        try {
            if (!modelFile.isFile || modelFile.length() != MODEL_SIZE_BYTES || modelFile.length() > MAX_ASSET_BYTES)
                return@withContext ModelAvailability.Unavailable("MODNet model is absent or outside the pinned size bound")
            val digest = OnnxImageModels.sha256(modelFile)
            if (digest != MODEL_SHA256)
                return@withContext ModelAvailability.Unavailable("MODNet model SHA-256 does not match the reviewed asset")
            openSession().use { session ->
                if (session.inputInfo.size != 1 || session.outputInfo.size != 1)
                    return@withContext ModelAvailability.Unavailable("MODNet must have exactly one input and one output")
                val input = session.inputInfo[INPUT_NAME]?.info as? TensorInfo
                    ?: return@withContext ModelAvailability.Unavailable("Missing MODNet input '$INPUT_NAME'")
                val output = session.outputInfo[OUTPUT_NAME]?.info as? TensorInfo
                    ?: return@withContext ModelAvailability.Unavailable("Missing MODNet output '$OUTPUT_NAME'")
                if (input.type != OnnxJavaType.FLOAT || output.type != OnnxJavaType.FLOAT)
                    return@withContext ModelAvailability.Unavailable("MODNet input and output must be float32")
                if (!validDynamicShape(input.shape, expectedChannels = 3) ||
                    !validDynamicShape(output.shape, expectedChannels = 1)
                ) return@withContext ModelAvailability.Unavailable(
                    "MODNet tensor schema must be dynamic NCHW RGB input -> dynamic NCHW one-channel matte"
                )
                ModelAvailability.Ready(input.shape.toList(), output.shape.toList(), MODEL_ID, digest)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ModelAvailability.Unavailable("Cannot inspect pinned MODNet model: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Runs learned portrait matting and returns an opaque grayscale alpha-probability bitmap at
     * source size. The moderation gate is intentionally checked before source inspection or model I/O.
     */
    suspend fun inferMask(source: Bitmap, onProgress: (ModelProgress) -> Unit = {}): Bitmap {
        GlobalModerationEnforcement.requireApproved(ProtectedAiAction.ONNX_BACKGROUND_REMOVAL)
        return withContext(Dispatchers.Default) {
            coroutineContext.ensureActive()
            require(source.width > 0 && source.height > 0) { "Source bitmap must be non-empty" }
            val (inputWidth, inputHeight) = inputDimensions(source.width, source.height)
            onProgress(ModelProgress(0.05f, "validating pinned MODNet model"))
            val ready = availability()
            if (ready !is ModelAvailability.Ready)
                throw LocalModelException((ready as ModelAvailability.Unavailable).reason)
            coroutineContext.ensureActive()

            val resized = Bitmap.createScaledBitmap(source, inputWidth, inputHeight, true)
            val pixels = IntArray(Math.multiplyExact(inputWidth, inputHeight))
            try {
                resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            } finally {
                if (resized !== source) resized.recycle()
            }
            val plane = Math.multiplyExact(inputWidth, inputHeight)
            val nchw = FloatArray(Math.multiplyExact(3, plane))
            for (i in pixels.indices) {
                val pixel = pixels[i]
                nchw[i] = normalize((pixel ushr 16) and 255)
                nchw[plane + i] = normalize((pixel ushr 8) and 255)
                nchw[plane * 2 + i] = normalize(pixel and 255)
            }
            onProgress(ModelProgress(0.2f, "preprocessing normalized RGB"))

            val output = openSession().use { session ->
                val tensor = OnnxTensor.createTensor(
                    env, FloatBuffer.wrap(nchw), longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
                )
                tensor.use {
                    coroutineContext.ensureActive()
                    onProgress(ModelProgress(0.3f, "running on-device MODNet inference"))
                    session.run(mapOf(INPUT_NAME to it)).use { result ->
                        coroutineContext.ensureActive()
                        val matte = result.get(OUTPUT_NAME).orElse(null) as? OnnxTensor
                            ?: throw LocalModelException("MODNet output is not a tensor")
                        matte.use { value ->
                            val shape = value.info.shape
                            if (!validRuntimeOutput(shape, inputHeight, inputWidth))
                                throw LocalModelException("MODNet runtime output shape ${shape.contentToString()} is invalid")
                            val buffer = value.floatBuffer
                            if (buffer.remaining() != plane)
                                throw LocalModelException("MODNet output contains ${buffer.remaining()} values; expected $plane")
                            FloatArray(buffer.remaining()).also { values ->
                                buffer.get(values)
                                if (values.any { !it.isFinite() })
                                    throw LocalModelException("MODNet output contains non-finite values")
                            }
                        }
                    }
                }
            }
            coroutineContext.ensureActive()
            val maskPixels = IntArray(plane)
            for (i in output.indices) {
                val gray = alphaByte(output[i])
                maskPixels[i] = (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
            val lowResolutionMask = Bitmap.createBitmap(maskPixels, inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
            try {
                val fullSizeMask = if (inputWidth == source.width && inputHeight == source.height) lowResolutionMask
                else Bitmap.createScaledBitmap(lowResolutionMask, source.width, source.height, true)
                onProgress(ModelProgress(1f, "complete"))
                fullSizeMask
            } finally {
                if (lowResolutionMask !== source && lowResolutionMask.isRecycled.not() &&
                    (inputWidth != source.width || inputHeight != source.height)
                ) lowResolutionMask.recycle()
            }
        }
    }

    private fun validDynamicShape(shape: LongArray, expectedChannels: Int): Boolean =
        shape.size == 4 && shape[0] in setOf(-1L, 1L) && shape[1] == expectedChannels.toLong() &&
            shape[2] == -1L && shape[3] == -1L

    private fun validRuntimeOutput(shape: LongArray, height: Int, width: Int): Boolean =
        shape.contentEquals(longArrayOf(1, 1, height.toLong(), width.toLong()))

    private fun normalize(channel: Int): Float = (channel / 255f - 0.5f) / 0.5f

    private fun openSession(): OrtSession = OrtSession.SessionOptions().use { options ->
        env.createSession(modelFile.absolutePath, options)
    }
}
