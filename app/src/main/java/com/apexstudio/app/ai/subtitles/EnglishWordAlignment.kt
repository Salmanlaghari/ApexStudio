package com.apexstudio.app.ai.subtitles

import android.content.Context
import com.apexstudio.app.ai.models.ModelAssetAccess
import android.net.Uri
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import com.apexstudio.app.data.audio.PcmAudioExtractor
import com.apexstudio.app.data.captions.WhisperCaptionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Failure categories are explicit so callers can distinguish unsupported text from model/audio errors. */
enum class EnglishAlignmentFailure {
    MODEL_UNAVAILABLE,
    AUDIO_UNAVAILABLE,
    NON_LOCAL_AUDIO_URI,
    INVALID_RANGE,
    UNSUPPORTED_TRANSCRIPT,
    INCOMPATIBLE_MODEL,
    ALIGNMENT_FAILED,
    LOW_CONFIDENCE,
}

class EnglishWordAlignmentException(
    val failure: EnglishAlignmentFailure,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** Installs the bundled model to app-private storage only after SHA-256 and size verification. */
object BundledEnglishAlignmentModel {
    const val SHA256 = "1d9a366c27b2966625cd5035ac3db8847c53bba617169912bb251b42975a3a22"
    const val SIZE_BYTES = 95_212_816L
    const val REVISION = "729c1a6730fb549c20a1c73a3d3f96f11020225e"
    private const val ASSET_PATH = "models/alignment/wav2vec2-base-960h-int8.onnx"
    private const val FILE_NAME = "wav2vec2-base-960h-int8.onnx"
    private const val BUFFER_SIZE = 64 * 1024

    @Synchronized
    fun modelFile(context: Context, checkCancelled: () -> Unit = {}): File {
        checkCancelled()
        val appContext = context.applicationContext
        ModelAssetAccess.installedFile(appContext, ASSET_PATH)?.let { installed ->
            check(isVerified(installed, checkCancelled)) { "Downloaded English alignment model failed verification" }
            return installed
        }
        val root = File(appContext.filesDir, "models/alignment")
        val targetDir = File(root, REVISION)
        val target = File(targetDir, FILE_NAME)
        val marker = File(targetDir, ".verified")
        if (marker.isFile && marker.readText() == REVISION && isVerified(target, checkCancelled)) return target

        root.mkdirs()
        val staging = File(root, ".staging-${Thread.currentThread().id}-${System.nanoTime()}")
        try {
            check(staging.mkdirs()) { "Cannot create English alignment model staging directory" }
            val staged = File(staging, FILE_NAME)
            val digest = MessageDigest.getInstance("SHA-256")
            checkCancelled()
            ModelAssetAccess.open(appContext, ASSET_PATH).use { input ->
                FileOutputStream(staged).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        checkCancelled()
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            val actual = digest.digest().toHex()
            check(staged.length() == SIZE_BYTES && actual == SHA256) {
                "Bundled English alignment model failed integrity verification"
            }
            checkCancelled()
            File(staging, ".verified").writeText(REVISION)
            checkCancelled()
            if (targetDir.exists()) check(targetDir.deleteRecursively()) { "Cannot replace invalid alignment model" }
            checkCancelled()
            check(staging.renameTo(targetDir)) { "Could not atomically install English alignment model" }
            check(isVerified(target, checkCancelled)) { "Installed English alignment model failed verification" }
            return target
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            try {
                staging.deleteRecursively()
            } catch (_: Exception) {
                // Preserve the caller's cancellation as the primary outcome.
            }
            throw cancelled
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw EnglishWordAlignmentException(
                EnglishAlignmentFailure.MODEL_UNAVAILABLE,
                "Bundled English word-alignment model is unavailable or failed verification: ${error.message}",
                error,
            )
        }
    }

    private fun isVerified(file: File, checkCancelled: () -> Unit): Boolean {
        checkCancelled()
        if (!file.isFile || file.length() != SIZE_BYTES) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                checkCancelled()
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex() == SHA256
    }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

/**
 * Genuine, local English word timing from wav2vec2-base-960h CTC emissions. Input transcript
 * segments are relative to the selected source trim (the same convention as WhisperCaptionEngine);
 * returned word cues remain relative to that trim. This is English-only, and does not translate,
 * transliterate or guess timings when alignment is unsupported or unreliable.
 */
object EnglishWordAlignment {
    private const val SAMPLE_RATE = 16_000
    private const val FRAME_STRIDE_SAMPLES = 320 // wav2vec2 conv stride product: 20 ms
    private const val FRAME_CENTER_OFFSET_MS = 12L // center of first 400-sample (25 ms) receptive field is 12.47 ms
    private const val MAX_WINDOW_SAMPLES = SAMPLE_RATE * 15
    private const val CONTEXT_PADDING_MS = 300L
    private const val MAX_SEGMENT_MS = 14_400L // leaves room for bounded context on both sides
    private const val VOCAB_SIZE = 32
    private const val BLANK_ID = 0
    private const val MIN_WORD_CONFIDENCE = 0.08f
    private const val MIN_ALIGNMENT_SCORE = 0.08f
    private val environment: OrtEnvironment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OrtEnvironment.getEnvironment()
    }

    /**
     * @param sourceStartMs/sourceEndMs absolute bounds in [uri]. Segment times are relative to this
     * trim; the returned cues are also relative to it (not absolute file time).
     * @throws EnglishWordAlignmentException for unsupported text, unavailable assets/audio, bad
     * ranges, incompatible inference outputs, or low-confidence/no-path alignment.
     */
    suspend fun align(
        context: Context,
        uri: Uri,
        segments: List<WhisperCaptionEngine.CaptionSegment>,
        sourceStartMs: Long,
        sourceEndMs: Long,
        onProgress: (Float) -> Unit = {},
    ): List<WordCue> = withContext(Dispatchers.Default) {
        GlobalModerationEnforcement.requireApproved(ProtectedAiAction.WORD_ALIGNMENT)
        if (sourceStartMs < 0L || sourceEndMs <= sourceStartMs) {
            fail(EnglishAlignmentFailure.INVALID_RANGE, "Source trim must have a non-negative start and a later end")
        }
        if (!isAllowedLocalAudioUri(uri.scheme, uri.authority)) {
            fail(EnglishAlignmentFailure.NON_LOCAL_AUDIO_URI,
                "English alignment accepts only local file, content, or android.resource URIs")
        }
        if (segments.isEmpty()) {
            onProgress(1f)
            return@withContext emptyList()
        }
        val prepared = segments.mapIndexed { index, segment ->
            if (segment.startMs < 0L || segment.endMs <= segment.startMs) {
                fail(EnglishAlignmentFailure.INVALID_RANGE, "Caption segment $index has an invalid time interval")
            }
            if (segment.endMs - segment.startMs > MAX_SEGMENT_MS) {
                fail(EnglishAlignmentFailure.INVALID_RANGE, "Caption segment $index exceeds the 14.4-second alignment window limit")
            }
            tokenizeEnglish(segment.text, index)
        }
        validateOrderedAlignmentSegments(segments)
        coroutineContext.ensureActive()
        onProgress(0.03f)
        val modelContext = coroutineContext
        val modelFile = withContext(Dispatchers.IO) {
            modelContext.ensureActive()
            BundledEnglishAlignmentModel.modelFile(context.applicationContext) { modelContext.ensureActive() }
        }
        val decodeContext = coroutineContext
        val fullPcm = withContext(Dispatchers.IO) {
            PcmAudioExtractor.extractMono16k(context.applicationContext, uri) { decodeContext.ensureActive() }
        } ?: fail(EnglishAlignmentFailure.AUDIO_UNAVAILABLE, "URI has no decodable audio track")
        if (fullPcm.isEmpty()) fail(EnglishAlignmentFailure.AUDIO_UNAVAILABLE, "Decoded audio is empty")
        val audioDurationMs = fullPcm.size.toLong() * 1000L / SAMPLE_RATE
        if (sourceStartMs >= audioDurationMs) {
            fail(EnglishAlignmentFailure.INVALID_RANGE, "Source trim starts beyond the decoded audio")
        }
        val effectiveEndMs = minOf(sourceEndMs, audioDurationMs)
        if (effectiveEndMs <= sourceStartMs) {
            fail(EnglishAlignmentFailure.INVALID_RANGE, "Source trim does not overlap decoded audio")
        }
        val trimStartSample = millisToSamplesFloor(sourceStartMs).coerceIn(0, fullPcm.size)
        val trimEndSample = millisToSamplesCeil(effectiveEndMs).coerceIn(trimStartSample, fullPcm.size)
        val trimSampleCount = trimEndSample - trimStartSample
        if (trimSampleCount == 0) fail(EnglishAlignmentFailure.INVALID_RANGE, "Source trim contains no audio samples")
        val trimDurationMs = trimSampleCount.toLong() * 1000L / SAMPLE_RATE
        onProgress(0.12f)

        val session = try {
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                environment.createSession(modelFile.absolutePath, options)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw EnglishWordAlignmentException(
                EnglishAlignmentFailure.INCOMPATIBLE_MODEL,
                "Cannot open bundled wav2vec2 alignment model: ${error.message}", error,
            )
        }
        session.use { ort ->
            validateSchema(ort)
            val cues = ArrayList<WordCue>()
            for ((index, segment) in segments.withIndex()) {
                coroutineContext.ensureActive()
                if (segment.endMs > trimDurationMs) {
                    fail(EnglishAlignmentFailure.INVALID_RANGE, "Caption segment $index extends beyond the selected audio trim")
                }
                val transcript = prepared[index]
                val segmentStart = segment.startMs
                val segmentEnd = segment.endMs
                val windowStartMs = maxOf(0L, segmentStart - CONTEXT_PADDING_MS)
                val windowEndMs = minOf(trimDurationMs, segmentEnd + CONTEXT_PADDING_MS)
                val localStartSample = millisToSamplesFloor(windowStartMs).coerceIn(0, trimSampleCount)
                val localEndSample = millisToSamplesCeil(windowEndMs).coerceIn(localStartSample, trimSampleCount)
                val startSample = trimStartSample + localStartSample
                val endSample = trimStartSample + localEndSample
                val windowLength = localEndSample - localStartSample
                if (windowLength <= 0 || windowLength > MAX_WINDOW_SAMPLES) {
                    fail(EnglishAlignmentFailure.INVALID_RANGE, "Caption segment $index exceeds the bounded 15-second model input")
                }
                val waveform = normalizeWaveform(fullPcm, startSample, endSample, index)
                val inferenceContext = coroutineContext
                val emissions = runModel(ort, waveform, index) { inferenceContext.ensureActive() }
                val frameDurationMs = FRAME_STRIDE_SAMPLES * 1000f / SAMPLE_RATE
                val receptiveOffset = localStartSample.toLong() * 1000L / SAMPLE_RATE + FRAME_CENTER_OFFSET_MS
                val alignContext = coroutineContext
                val aligned = try {
                    CtcForcedAligner.align(
                        logProbabilities = emissions,
                        blankId = BLANK_ID,
                        targetTokenIds = transcript.tokenIds,
                        wordIndexForToken = transcript.wordIndices,
                        wordTexts = transcript.displayWords,
                        frameDurationMs = frameDurationMs,
                        startOffsetMs = receptiveOffset,
                        checkCancelled = { alignContext.ensureActive() },
                    )
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    throw EnglishWordAlignmentException(
                        EnglishAlignmentFailure.ALIGNMENT_FAILED,
                        "Could not align English caption segment $index: ${error.message}", error,
                    )
                }
                if (aligned.words.size != transcript.displayWords.size || aligned.score < MIN_ALIGNMENT_SCORE ||
                    aligned.words.any { (it.confidence ?: 0f) < MIN_WORD_CONFIDENCE }) {
                    fail(EnglishAlignmentFailure.LOW_CONFIDENCE,
                        "English caption segment $index has low CTC confidence (${"%.3f".format(Locale.ROOT, aligned.score)})")
                }
                val bounded = aligned.words.map { cue ->
                    val start = cue.startMs.coerceIn(segmentStart, segmentEnd).coerceIn(0L, trimDurationMs)
                    val end = cue.endMs.coerceIn(segmentStart, segmentEnd).coerceIn(0L, trimDurationMs)
                    if (end <= start) {
                        fail(EnglishAlignmentFailure.LOW_CONFIDENCE, "A word in caption segment $index collapsed at the segment boundary")
                    }
                    cue.copy(startMs = start, endMs = end)
                }
                cues += bounded
                onProgress(0.12f + 0.88f * (index + 1).toFloat() / segments.size)
            }
            cues
        }
    }

    private fun validateSchema(session: OrtSession) {
        val inputs = session.inputInfo
        val outputs = session.outputInfo
        if (inputs.size != 1 || outputs.size != 1) {
            fail(EnglishAlignmentFailure.INCOMPATIBLE_MODEL, "Expected exactly one wav2vec2 input and output")
        }
        val input = inputs["input_values"]?.info as? TensorInfo
        val output = outputs["logits"]?.info as? TensorInfo
        if (input == null || output == null || input.type != OnnxJavaType.FLOAT || output.type != OnnxJavaType.FLOAT) {
            fail(EnglishAlignmentFailure.INCOMPATIBLE_MODEL, "Expected input_values and logits float32 tensors")
        }
        val inputShape = input.shape
        val outputShape = output.shape
        if (inputShape.size != 2 || (inputShape[0] > 0L && inputShape[0] != 1L) ||
            outputShape.size != 3 || (outputShape[0] > 0L && outputShape[0] != 1L) ||
            (outputShape[2] > 0L && outputShape[2] != VOCAB_SIZE.toLong())) {
            fail(EnglishAlignmentFailure.INCOMPATIBLE_MODEL,
                "Expected float input_values [1,samples] and logits [1,frames,32], found ${inputShape.contentToString()} and ${outputShape.contentToString()}")
        }
    }

    private fun runModel(
        session: OrtSession,
        waveform: FloatArray,
        segmentIndex: Int,
        checkCancelled: () -> Unit,
    ): Array<FloatArray> {
        val env = environment
        val tensor = try {
            OnnxTensor.createTensor(env, FloatBuffer.wrap(waveform), longArrayOf(1L, waveform.size.toLong()))
        } catch (error: Exception) {
            throw EnglishWordAlignmentException(EnglishAlignmentFailure.INCOMPATIBLE_MODEL,
                "Could not create wav2vec2 input tensor: ${error.message}", error)
        }
        return tensor.use { input ->
            try {
                // OrtSession.Result owns its output tensors: close the result once, without separately
                // closing the borrowed output tensor.
                session.run(mapOf("input_values" to input)).use { result ->
                    val output = result.get("logits").orElse(null) as? OnnxTensor
                        ?: fail(EnglishAlignmentFailure.INCOMPATIBLE_MODEL, "wav2vec2 logits output is not a tensor")
                    val shape = output.info.shape
                    if (shape.size != 3 || shape[0] != 1L || shape[1] <= 0L || shape[2] != VOCAB_SIZE.toLong() ||
                        shape[1] > MAX_OUTPUT_FRAMES || shape[1] * shape[2] > MAX_LOGIT_VALUES) {
                        fail(EnglishAlignmentFailure.INCOMPATIBLE_MODEL, "Invalid or oversized wav2vec2 output shape ${shape.contentToString()}")
                    }
                    val frameCount = shape[1].toInt()
                    val buffer = output.floatBuffer
                    if (buffer.remaining().toLong() != shape[1] * shape[2]) {
                        fail(EnglishAlignmentFailure.INCOMPATIBLE_MODEL, "wav2vec2 logits size does not match its shape")
                    }
                    Array(frameCount) { frame ->
                        if (frame % 64 == 0) checkCancelled()
                        val row = FloatArray(VOCAB_SIZE)
                        var max = Float.NEGATIVE_INFINITY
                        for (token in row.indices) {
                            val value = buffer.get()
                            if (!value.isFinite()) fail(EnglishAlignmentFailure.INCOMPATIBLE_MODEL, "Non-finite wav2vec2 logits")
                            row[token] = value
                            if (value > max) max = value
                        }
                        var sum = 0.0
                        for (token in row.indices) sum += exp((row[token] - max).toDouble())
                        val logNormalizer = max + ln(sum).toFloat()
                        for (token in row.indices) row[token] -= logNormalizer
                        row
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: EnglishWordAlignmentException) {
                throw error
            } catch (error: Exception) {
                throw EnglishWordAlignmentException(EnglishAlignmentFailure.INCOMPATIBLE_MODEL,
                    "wav2vec2 inference failed for caption segment $segmentIndex: ${error.message}", error)
            }
        }
    }

    private fun normalizeWaveform(samples: FloatArray, start: Int, end: Int, segmentIndex: Int): FloatArray {
        val length = end - start
        val normalized = FloatArray(length)
        var mean = 0.0
        for (i in 0 until length) {
            val sample = samples[start + i]
            if (!sample.isFinite()) fail(EnglishAlignmentFailure.AUDIO_UNAVAILABLE, "Non-finite PCM in caption segment $segmentIndex")
            mean += sample
        }
        mean /= length
        var variance = 0.0
        for (i in 0 until length) {
            val delta = samples[start + i] - mean
            variance += delta * delta
        }
        variance /= length
        val denominator = sqrt(variance + 1e-7)
        for (i in 0 until length) normalized[i] = ((samples[start + i] - mean) / denominator).toFloat()
        return normalized
    }

    private fun millisToSamplesFloor(ms: Long): Int = ((ms * SAMPLE_RATE) / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    private fun millisToSamplesCeil(ms: Long): Int = ceil(ms.toDouble() * SAMPLE_RATE / 1000.0).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private data class Transcript(val displayWords: List<String>, val tokenIds: IntArray, val wordIndices: IntArray)

    private fun tokenizeEnglish(text: String, segmentIndex: Int): Transcript = try {
        val parsed = tokenizeEnglishTranscript(text)
        Transcript(parsed.displayedWords, parsed.tokenIds, parsed.wordIndices)
    } catch (error: IllegalArgumentException) {
        fail(EnglishAlignmentFailure.UNSUPPORTED_TRANSCRIPT,
            "Caption segment $segmentIndex is not supported by the English CTC tokenizer: ${error.message}")
    }

    private fun fail(reason: EnglishAlignmentFailure, message: String): Nothing =
        throw EnglishWordAlignmentException(reason, message)

    private const val MAX_OUTPUT_FRAMES = 751L // 15 s at 20 ms, with bounded rounding slack
    private const val MAX_LOGIT_VALUES = MAX_OUTPUT_FRAMES * VOCAB_SIZE
}

/** Package-visible tokenizer view for deterministic unit tests without Android or ONNX execution. */
internal data class EnglishTranscriptTokens(
    val displayedWords: List<String>,
    val tokenIds: IntArray,
    val wordIndices: IntArray,
)

internal const val MAX_ENGLISH_TRANSCRIPT_CHARACTERS = 4_096

internal fun tokenizeEnglishTranscript(text: String): EnglishTranscriptTokens {
    // Kept independent of Context and model installation; mirror the public backend's strict parser.
    if (text.length > MAX_ENGLISH_TRANSCRIPT_CHARACTERS) {
        throw IllegalArgumentException("Transcript exceeds the $MAX_ENGLISH_TRANSCRIPT_CHARACTERS-character alignment limit")
    }
    if (text.isBlank()) throw IllegalArgumentException("Transcript has no English words")
    val punctuation = text.all { ch ->
        ch in 'A'..'Z' || ch in 'a'..'z' || ch == '\'' || ch.isAsciiWhitespace() ||
            (ch.code in 0x21..0x7e && !ch.isLetterOrDigit())
    }
    if (!punctuation) throw IllegalArgumentException("Only ASCII English letters, apostrophes, whitespace, and punctuation are supported")
    val matches = Regex("[A-Za-z]+(?:'[A-Za-z]+)*").findAll(text).toList()
    if (matches.isEmpty()) throw IllegalArgumentException("Transcript has no supported English words")
    val vocab = mapOf(
        'A' to 7, 'B' to 24, 'C' to 19, 'D' to 14, 'E' to 5, 'F' to 20, 'G' to 21,
        'H' to 11, 'I' to 10, 'J' to 29, 'K' to 26, 'L' to 15, 'M' to 17, 'N' to 9,
        'O' to 8, 'P' to 23, 'Q' to 30, 'R' to 13, 'S' to 12, 'T' to 6, 'U' to 16,
        'V' to 25, 'W' to 18, 'X' to 28, 'Y' to 22, 'Z' to 31, '\'' to 27,
    )
    val ids = ArrayList<Int>()
    val wordMap = ArrayList<Int>()
    matches.forEachIndexed { index, match ->
        if (index > 0) { ids += 4; wordMap += -1 }
        for (letter in match.value.uppercase(Locale.ROOT)) {
            ids += vocab.getValue(letter)
            wordMap += index
        }
    }
    val displayed = matches.mapIndexed { index, match ->
        val end = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
        val leading = if (index == 0) text.substring(0, match.range.first).filterNot { it.isAsciiWhitespace() } else ""
        leading + text.substring(match.range.first, end).trimEnd()
    }
    return EnglishTranscriptTokens(displayed, ids.toIntArray(), wordMap.toIntArray())
}

/** Phrase intervals from Whisper must be ordered and disjoint before per-phrase alignment starts. */
internal fun validateOrderedAlignmentSegments(segments: List<WhisperCaptionEngine.CaptionSegment>) {
    segments.zipWithNext().forEachIndexed { index, (current, next) ->
        if (next.startMs < current.endMs) {
            throw EnglishWordAlignmentException(
                EnglishAlignmentFailure.INVALID_RANGE,
                "Caption segments $index and ${index + 1} overlap or are out of order",
            )
        }
    }
}

private fun Char.isAsciiWhitespace(): Boolean = this == ' ' || this in '\t'..'\r'

/** Pure URI policy for JVM testing; file authorities are remote/UNC, unlike local file paths. */
internal fun isAllowedLocalAudioUri(scheme: String?, authority: String?): Boolean =
    when (scheme?.lowercase(Locale.ROOT)) {
        null -> authority.isNullOrEmpty()
        "file" -> authority.isNullOrEmpty() || authority.equals("localhost", ignoreCase = true)
        "content", "android.resource" -> true
        else -> false
    }
