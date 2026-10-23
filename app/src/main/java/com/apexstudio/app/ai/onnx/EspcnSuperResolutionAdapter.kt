package com.apexstudio.app.ai.onnx

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import com.apexstudio.app.ai.models.ModelAssetAccess
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

/**
 * Adapter for the ONNX Model Zoo sub-pixel CNN, not an RGB model and not RealESRGAN.
 * It accepts ordinary Android RGB bitmaps and returns RGB bitmaps after YCbCr luma inference.
 */
class EspcnSuperResolutionAdapter(private val modelFile: File) {
    companion object {
        const val MODEL_ID = "onnxmodelzoo-super-resolution-10"
        const val MODEL_VERSION = "hf-395472c4b6b92f788357237983d83fd913cfe7e3"
        const val MODEL_SHA256 = "85f36ff88cc504a24af5e0602148bc56a8aa09a58eca8c0da2756f3e8186035e"
        const val INPUT_NAME = "input"
        const val OUTPUT_NAME = "output"
        const val TILE_SIZE = 224
        const val SCALE = 3
        private const val TILE_STEP = 192
        private const val OUTPUT_PIXEL_LIMIT = 4_194_304L
        private val env: OrtEnvironment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OrtEnvironment.getEnvironment() }

        internal fun outputDimensions(width: Int, height: Int): Pair<Int, Int> {
            require(width > 0 && height > 0) { "Input bitmap must be non-empty" }
            val outWidth = Math.multiplyExact(width, SCALE)
            val outHeight = Math.multiplyExact(height, SCALE)
            require(outWidth.toLong() * outHeight <= OUTPUT_PIXEL_LIMIT) { "x3 ESPCN output exceeds the safe pixel limit; 4K processing is not available" }
            return outWidth to outHeight
        }

        internal fun tileStarts(length: Int): IntArray {
            require(length > 0)
            if (length <= TILE_SIZE) return intArrayOf(0)
            val starts = ArrayList<Int>()
            var position = 0
            while (position.toLong() + TILE_SIZE < length) {
                starts.add(position)
                position += TILE_STEP
            }
            val last = length - TILE_SIZE
            if (starts.last() != last) starts.add(last)
            return starts.toIntArray()
        }

        internal fun ownedInterval(length: Int, starts: IntArray, index: Int): IntRange {
            val origin = starts[index]
            val left = if (index == 0) 0 else (starts[index - 1] + TILE_SIZE + origin) / 2
            val right = if (index == starts.lastIndex) length else ((origin + TILE_SIZE + starts[index + 1]) / 2).coerceAtMost(length)
            return left until right
        }

        /** Extract only the bundled, pinned model; there is no network fetch or fallback. */
        @Synchronized
        fun fromBundledAssets(context: Context, cacheDir: File = context.cacheDir): EspcnSuperResolutionAdapter {
            ModelAssetAccess.installedFile(context, "models/super_resolution/super-resolution-10.onnx")?.let { installed ->
                require(OnnxImageModels.sha256(installed) == MODEL_SHA256) { "Downloaded ESPCN model SHA-256 mismatch" }
                return EspcnSuperResolutionAdapter(installed)
            }
            val target = File(cacheDir, "models/$MODEL_ID.onnx")
            if (!target.isFile || OnnxImageModels.sha256(target) != MODEL_SHA256) {
                target.parentFile?.mkdirs()
                val temp = File.createTempFile("espcn-", ".tmp", target.parentFile)
                try {
                    ModelAssetAccess.open(context, "models/super_resolution/super-resolution-10.onnx").use { input ->
                        temp.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    require(OnnxImageModels.sha256(temp) == MODEL_SHA256) { "Bundled ESPCN model SHA-256 mismatch" }
                    check(temp.renameTo(target)) { "Cannot atomically publish verified ESPCN model" }
                } finally { temp.delete() }
            }
            require(OnnxImageModels.sha256(target) == MODEL_SHA256) { "Cached ESPCN model SHA-256 mismatch" }
            return EspcnSuperResolutionAdapter(target)
        }
    }

    init { require(modelFile.isFile) { "ESPCN model file is missing: ${modelFile.name}" } }

    /** Validate the pinned bytes and exact one-channel NCHW float contract without enabling any feature switch. */
    suspend fun availability(): ModelAvailability = withContext(Dispatchers.IO) {
        try {
            if (OnnxImageModels.sha256(modelFile) != MODEL_SHA256)
                return@withContext ModelAvailability.Unavailable("ESPCN model SHA-256 does not match the pinned artifact")
            openSession().use { session ->
                val input = session.inputInfo[INPUT_NAME]?.info as? TensorInfo
                    ?: return@withContext ModelAvailability.Unavailable("Missing ESPCN input '$INPUT_NAME'")
                val output = session.outputInfo[OUTPUT_NAME]?.info as? TensorInfo
                    ?: return@withContext ModelAvailability.Unavailable("Missing ESPCN output '$OUTPUT_NAME'")
                if (input.type != OnnxJavaType.FLOAT || output.type != OnnxJavaType.FLOAT)
                    return@withContext ModelAvailability.Unavailable("ESPCN inputs and outputs must be float32")
                if (!input.shape.contentEquals(longArrayOf(-1, 1, TILE_SIZE.toLong(), TILE_SIZE.toLong())) ||
                    !output.shape.contentEquals(longArrayOf(-1, 1, (TILE_SIZE * SCALE).toLong(), (TILE_SIZE * SCALE).toLong())))
                    return@withContext ModelAvailability.Unavailable("ESPCN tensor schema differs from pinned 1x224x224 -> 1x672x672 luma contract")
                ModelAvailability.Ready(input.shape.toList(), output.shape.toList(), MODEL_ID, MODEL_SHA256)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ModelAvailability.Unavailable("Cannot inspect pinned ESPCN model: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Run learned x3 Y-luma upscaling on a tiled arbitrary-size bitmap, preserving its aspect ratio. */
    suspend fun upscale(source: Bitmap, onProgress: (ModelProgress) -> Unit = {}): Bitmap {
        GlobalModerationEnforcement.requireApproved(ProtectedAiAction.ONNX_UPSCALING)
        return inferForModelValidation(source, onProgress)
    }

    /** Open one pinned, schema-validated session for sequential frames in the bounded video engine. */
    internal suspend fun openValidatedVideoSession(): OrtSession {
        val status = availability()
        if (status !is ModelAvailability.Ready) {
            throw LocalModelException((status as ModelAvailability.Unavailable).reason)
        }
        return withContext(Dispatchers.IO) { openSession() }
    }

    /** Internal frame entrypoint; still-image callers continue to use [upscale] and remain blocked by scope. */
    internal suspend fun upscaleExistingProjectVideoFrame(
        source: Bitmap,
        session: OrtSession,
        onProgress: (ModelProgress) -> Unit = {}
    ): Bitmap {
        GlobalModerationEnforcement.requireApproved(ProtectedAiAction.ONNX_UPSCALING)
        return inferForModelValidation(source, onProgress, session)
    }

    /** Low-level model-evaluation seam for instrumentation, not an approved user-content action. */
    internal suspend fun inferForModelValidation(
        source: Bitmap,
        onProgress: (ModelProgress) -> Unit = {},
        reusableSession: OrtSession? = null
    ): Bitmap = withContext(Dispatchers.Default) {
        coroutineContext.ensureActive()
        val (outWidth, outHeight) = outputDimensions(source.width, source.height)
        if (reusableSession == null) {
            val modelReady = availability()
            if (modelReady !is ModelAvailability.Ready)
                throw LocalModelException((modelReady as ModelAvailability.Unavailable).reason)
        }

        val inputPixels = IntArray(Math.multiplyExact(source.width, source.height))
        source.getPixels(inputPixels, 0, source.width, 0, 0, source.width, source.height)
        val luma = FloatArray(inputPixels.size)
        val cb = FloatArray(inputPixels.size)
        val cr = FloatArray(inputPixels.size)
        for (i in inputPixels.indices) {
            val pixel = inputPixels[i]
            val r = pixel ushr 16 and 255
            val g = pixel ushr 8 and 255
            val b = pixel and 255
            // Match Pillow/ITU-R BT.601 full-range integer RGB -> YCbCr conversion used by the model card.
            luma[i] = ((19595 * r + 38470 * g + 7471 * b + 32768) shr 16) / 255f
            cb[i] = ((-11059 * r - 21709 * g + 32768 * b + 8421376) shr 16).toFloat()
            cr[i] = ((32768 * r - 27439 * g - 5329 * b + 8421376) shr 16).toFloat()
        }
        onProgress(ModelProgress(0.05f, "preprocessing YCbCr luma tiles"))
        val xs = tileStarts(source.width)
        val ys = tileStarts(source.height)
        val output = IntArray(Math.multiplyExact(outWidth, outHeight))
        var complete = 0
        val total = xs.size * ys.size
        val inferenceContext = coroutineContext
        val runTiles: (OrtSession) -> Unit = { session ->
            for (yi in ys.indices) for (xi in xs.indices) {
                inferenceContext.ensureActive()
                val x = xs[xi]
                val y = ys[yi]
                val tile = FloatArray(TILE_SIZE * TILE_SIZE)
                for (ty in 0 until TILE_SIZE) {
                    val sy = (y + ty).coerceAtMost(source.height - 1)
                    val sourceOffset = sy * source.width
                    val tileOffset = ty * TILE_SIZE
                    for (tx in 0 until TILE_SIZE) {
                        val sx = (x + tx).coerceAtMost(source.width - 1)
                        tile[tileOffset + tx] = luma[sourceOffset + sx]
                    }
                }
                val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(tile), longArrayOf(1, 1, TILE_SIZE.toLong(), TILE_SIZE.toLong()))
                val values = tensor.use {
                    session.run(mapOf(INPUT_NAME to it)).use { result ->
                        val tensorOut = result.get(OUTPUT_NAME).orElse(null) as? OnnxTensor
                            ?: throw LocalModelException("ESPCN output is not a tensor")
                        tensorOut.let { out ->
                            if (!out.info.shape.contentEquals(longArrayOf(1, 1, (TILE_SIZE * SCALE).toLong(), (TILE_SIZE * SCALE).toLong())))
                                throw LocalModelException("ESPCN runtime output shape is unexpected")
                            val buffer = out.floatBuffer
                            if (buffer.remaining() != TILE_SIZE * SCALE * TILE_SIZE * SCALE)
                                throw LocalModelException("ESPCN runtime output element count is unexpected")
                            FloatArray(buffer.remaining()).also { buffer.get(it) }
                        }
                    }
                }
                if (values.any { !it.isFinite() }) throw LocalModelException("ESPCN output contains non-finite values")
                inferenceContext.ensureActive()
                val xInterval = ownedInterval(source.width, xs, xi)
                val yInterval = ownedInterval(source.height, ys, yi)
                for (sy in yInterval) {
                    val outY0 = sy * SCALE
                    val localY = (sy - y) * SCALE
                    for (sx in xInterval) {
                        val outX0 = sx * SCALE
                        val localX = (sx - x) * SCALE
                        for (subY in 0 until SCALE) for (subX in 0 until SCALE) {
                            val dx = outX0 + subX
                            val dy = outY0 + subY
                            val yy = (values[(localY + subY) * TILE_SIZE * SCALE + localX + subX].coerceIn(0f, 1f) * 255f).toInt()
                            val chromaX = bicubic(cb, source.width, source.height, dx, dy, outWidth, outHeight)
                            val chromaY = bicubic(cr, source.width, source.height, dx, dy, outWidth, outHeight)
                            val u = chromaX - 128f
                            val v = chromaY - 128f
                            val r = (yy + ((91881f * v + 32768f) / 65536f).toInt()).coerceIn(0, 255)
                            val g = (yy - ((22554f * u + 46802f * v + 32768f) / 65536f).toInt()).coerceIn(0, 255)
                            val b = (yy + ((116130f * u + 32768f) / 65536f).toInt()).coerceIn(0, 255)
                            output[dy * outWidth + dx] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                }
                complete++
                onProgress(ModelProgress(0.1f + 0.85f * complete / total, "ESPCN luma tiles $complete/$total"))
            }
        }
        if (reusableSession != null) runTiles(reusableSession)
        else openSession().use(runTiles)
        coroutineContext.ensureActive()
        onProgress(ModelProgress(1f, "complete"))
        Bitmap.createBitmap(output, outWidth, outHeight, Bitmap.Config.ARGB_8888)
    }

    private fun openSession(): OrtSession = OrtSession.SessionOptions().use { env.createSession(modelFile.absolutePath, it) }

    /** Catmull-Rom bicubic resize, matching the model card's bicubic Cb/Cr reconstruction. */
    private fun bicubic(values: FloatArray, width: Int, height: Int, x: Int, y: Int, dstWidth: Int, dstHeight: Int): Float {
        val fx = (x + 0.5f) * width / dstWidth - 0.5f
        val fy = (y + 0.5f) * height / dstHeight - 0.5f
        val ix = kotlin.math.floor(fx).toInt()
        val iy = kotlin.math.floor(fy).toInt()
        var sum = 0f
        var weightSum = 0f
        for (j in -1..2) for (i in -1..2) {
            val weight = cubicWeight(fx - (ix + i)) * cubicWeight(fy - (iy + j))
            val sx = (ix + i).coerceIn(0, width - 1)
            val sy = (iy + j).coerceIn(0, height - 1)
            sum += values[sy * width + sx] * weight
            weightSum += weight
        }
        return if (weightSum == 0f) 128f else (sum / weightSum).coerceIn(0f, 255f)
    }

    private fun cubicWeight(x: Float): Float {
        val a = -0.5f
        val t = kotlin.math.abs(x)
        return when {
            t <= 1f -> (a + 2f) * t * t * t - (a + 3f) * t * t + 1f
            t < 2f -> a * t * t * t - 5f * a * t * t + 8f * a * t - 4f * a
            else -> 0f
        }
    }
}
