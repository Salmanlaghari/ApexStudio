package com.apexstudio.app.ai.safety

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.apexstudio.app.ai.AiModelUnavailableException
import com.apexstudio.app.ai.onnx.OnnxImageModels
import java.io.File
import java.nio.FloatBuffer
import java.util.concurrent.CancellationException

/** Deliberately distinct visual policy categories; NSFW/NSFL model labels do not imply hate detection. */
enum class SafetyCategory { SEXUAL, VIOLENT, HATEFUL }
data class ClassifierMetadata(
    val modelId: String,
    val modelVersion: String,
    val artifactSha256: String,
    val labels: Set<SafetyCategory>,
    val validationEvidence: String,
)
data class ModerationScores(val scores: Map<SafetyCategory, Float>)
data class ModerationDecision(val allowed: Boolean, val flagged: Set<SafetyCategory>, val scores: Map<SafetyCategory, Float>)

/** Adapter output is evidence from a local classifier, not an assertion that it is approved policy. */
interface ValidatedModerationClassifier {
    val metadata: ClassifierMetadata
    fun classify(imageBytes: ByteArray): ModerationScores
}

/**
 * Fail-closed policy boundary. Model metadata is never self-certifying: only immutable artifact hashes
 * reviewed with a versioned validation protocol can be approved here. The approval list is deliberately
 * empty until a model passes app-specific evaluation for all three required categories.
 */
class FailClosedModerationGate(
    private val classifier: ValidatedModerationClassifier?,
    thresholds: Map<SafetyCategory, Float> = SafetyCategory.entries.associateWith { 0.20f },
) {
    private val thresholds = thresholds.toMap()

    init {
        require(thresholds.keys.containsAll(SafetyCategory.entries))
        require(thresholds.values.all { it.isFinite() && it in 0f..1f })
    }

    fun requireAllowed(imageBytes: ByteArray): ModerationDecision {
        if (imageBytes.isEmpty() || imageBytes.size > MAX_IMAGE_BYTES)
            deny("image input is empty or exceeds the safe byte limit")
        val model = classifier ?: deny("no local classifier is configured")
        val meta = model.metadata
        if (meta.modelId.isBlank() || meta.modelVersion.isBlank() ||
            !meta.artifactSha256.matches(Regex("[0-9a-f]{64}")) || meta.validationEvidence.isBlank()) {
            deny("classifier has no well-formed pinned artifact and validation evidence")
        }
        if (!meta.labels.containsAll(SafetyCategory.entries)) {
            val missing = SafetyCategory.entries - meta.labels
            deny("classifier does not cover required categories: ${missing.joinToString()}")
        }
        if (!ApprovedModerationArtifacts.contains(meta)) {
            deny("classifier artifact is not on the app-reviewed moderation approval list")
        }
        val scores = try { model.classify(imageBytes) } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deny("classifier failed (${e.message ?: "inference error"})")
        }
        if (!scores.scores.keys.containsAll(SafetyCategory.entries) ||
            scores.scores.values.any { !it.isFinite() || it !in 0f..1f }) {
            deny("classifier returned incomplete or invalid scores")
        }
        val flagged = SafetyCategory.entries.filterTo(linkedSetOf()) { scores.scores.getValue(it) >= thresholds.getValue(it) }
        if (flagged.isNotEmpty()) {
            deny("content was flagged for required categories: ${flagged.joinToString()}")
        }
        return ModerationDecision(allowed = true, flagged = emptySet(), scores = scores.scores)
    }

    private fun deny(reason: String): Nothing = throw AiModelUnavailableException(
        "Content safety unavailable: $reason; protected AI action denied."
    )

    private companion object {
        const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
    }
}

/** There are currently no approved artifacts. Add a digest only after reproducible, app-specific validation. */
private object ApprovedModerationArtifacts {
    private val approvedDigests: Set<String> = emptySet()
    fun contains(metadata: ClassifierMetadata): Boolean = metadata.artifactSha256 in approvedDigests
}

/**
 * Offline inference adapter for OwenElliott/image-safety-classifier-xs ONNX. Its published class order is
 * NSFL, NSFW, SFW. NSFL is mapped only to a narrow graphic-gore/violence signal and NSFW only to sexual
 * content. Hateful remains unsupported, so this adapter can never satisfy the gate by itself.
 */
class OnnxNsfwNsflClassifier(private val modelFile: File) : ValidatedModerationClassifier {
    companion object {
        const val MODEL_ID = "OwenElliott/image-safety-classifier-xs"
        const val VERSION = "onnx-sha256-8c28c49d9075f3ad15ebdc2961f02d5b3f99be944815b848b49c9f0e6f3fb689"
        const val SHA256 = "8c28c49d9075f3ad15ebdc2961f02d5b3f99be944815b848b49c9f0e6f3fb689"
        const val MODEL_CARD = "https://huggingface.co/OwenElliott/image-safety-classifier-xs"
        private const val INPUT = "image"
        private const val SIZE = 224
        private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
        private const val MAX_DECODED_PIXELS = 4_194_304L
    }

    override val metadata = ClassifierMetadata(
        modelId = MODEL_ID,
        modelVersion = VERSION,
        artifactSha256 = SHA256,
        labels = setOf(SafetyCategory.SEXUAL, SafetyCategory.VIOLENT),
        validationEvidence = "publisher model card only; not app-validated",
    )

    override fun classify(imageBytes: ByteArray): ModerationScores {
        require(imageBytes.isNotEmpty() && imageBytes.size <= MAX_IMAGE_BYTES) { "Image bytes are empty or exceed the safe byte limit" }
        if (!modelFile.isFile || OnnxImageModels.sha256(modelFile) != SHA256)
            throw IllegalStateException("Local classifier bytes do not match the pinned model digest")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight.toLong() <= MAX_DECODED_PIXELS) {
            "Image dimensions are invalid or exceed the safe decoded pixel limit"
        }
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IllegalArgumentException("Image bytes could not be decoded")
        val scaled = try { Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true) }
        catch (e: Throwable) { bitmap.recycle(); throw e }
        try {
            val pixels = IntArray(SIZE * SIZE)
            scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            val plane = SIZE * SIZE
            val inputValues = FloatArray(3 * plane)
            for (i in pixels.indices) {
                val px = pixels[i]
                inputValues[i] = ((px shr 16) and 255).toFloat()
                inputValues[plane + i] = ((px shr 8) and 255).toFloat()
                inputValues[2 * plane + i] = (px and 255).toFloat()
            }
            val env = OrtEnvironment.getEnvironment()
            OrtSession.SessionOptions().use { options ->
                env.createSession(modelFile.absolutePath, options).use { session ->
                val inputInfo = (session.inputInfo[INPUT]?.info as? TensorInfo)
                    ?: throw IllegalStateException("Expected float tensor input '$INPUT'")
                if (inputInfo.type != OnnxJavaType.FLOAT || !matches(inputInfo.shape, longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())))
                    throw IllegalStateException("Classifier input is not float32 [1,3,224,224]")
                if (session.outputInfo.size != 1) throw IllegalStateException("Expected one classifier output")
                val outputInfo = session.outputInfo.values.first().info as? TensorInfo
                    ?: throw IllegalStateException("Classifier output is not a tensor")
                if (outputInfo.type != OnnxJavaType.FLOAT || !matches(outputInfo.shape, longArrayOf(1, 3)))
                    throw IllegalStateException("Classifier output is not float32 [1,3]")
                OnnxTensor.createTensor(env, FloatBuffer.wrap(inputValues), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { tensor ->
                    session.run(mapOf(INPUT to tensor)).use { result ->
                        val outputName = session.outputInfo.keys.first()
                        val out = result.get(outputName).orElse(null) as? OnnxTensor ?: throw IllegalStateException("Classifier output is not a tensor")
                        out.use {
                            if (!matches(out.info.shape, longArrayOf(1, 3))) throw IllegalStateException("Classifier returned an unexpected output shape")
                            val values = FloatArray(out.floatBuffer.remaining())
                            out.floatBuffer.get(values)
                            if (values.size != 3 || values.any { !it.isFinite() || it !in 0f..1f } || kotlin.math.abs(values.sum() - 1f) > 0.02f)
                                throw IllegalStateException("Classifier output is not a normalized 3-class probability vector")
                            // Source model card documents class_names = [NSFL, NSFW, SFW].
                            return ModerationScores(mapOf(
                                SafetyCategory.VIOLENT to values[0],
                                SafetyCategory.SEXUAL to values[1],
                            ))
                        }
                    }
                }
                }
            }
        } finally {
            try {
                if (scaled !== bitmap) scaled.recycle()
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun matches(actual: LongArray, expected: LongArray): Boolean =
        actual.size == expected.size && actual.indices.all { actual[it] == -1L || actual[it] == expected[it] }
}
