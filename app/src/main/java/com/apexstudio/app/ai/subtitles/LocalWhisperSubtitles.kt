package com.apexstudio.app.ai.subtitles

import android.content.Context
import android.net.Uri
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.apexstudio.app.data.audio.PcmAudioExtractor
import com.apexstudio.app.data.captions.WhisperCaptionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.io.File
import kotlin.coroutines.coroutineContext

/** Timing provenance is part of the data contract so clients cannot mistake phrase times for word times. */
enum class TimingPrecision { WHISPER_SEGMENT, CTC_FORCED_WORD_ALIGNMENT }

data class WordCue(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val precision: TimingPrecision,
    /** Mean posterior probability of the aligned CTC target token frames; null for coarse timings. */
    val confidence: Float? = null,
)

data class SubtitleResult(
    val segments: List<WhisperCaptionEngine.CaptionSegment>,
    /** Split for styling only; all words in one Whisper segment intentionally share its boundaries. */
    val words: List<WordCue>,
    val hasWordLevelTiming: Boolean = false,
)

/** File-only, fully local wrapper over the app's existing multilingual Whisper implementation. */
object LocalWhisperSubtitles {
    /**
     * Transcribe an on-device audio file. This API accepts File (not arbitrary network/content URIs).
     * The bundled, pinned Whisper model is initialized locally without network access; existing
     * PackManager-installed files are a fallback. Failure to load any usable model returns null.
     * Word cues are conservatively stamped with the complete Whisper phrase interval: no equal-split
     * timestamps are fabricated. Callers should highlight the whole phrase unless CTC alignment is run.
     */
    suspend fun transcribeFile(
        context: Context,
        audioFile: File,
        language: String = WhisperCaptionEngine.LANG_AUTO,
        onProgress: (Float) -> Unit = {},
    ): SubtitleResult? = withContext(Dispatchers.IO) {
        val local = audioFile.canonicalFile
        if (!local.isFile || local.length() == 0L) return@withContext null
        coroutineContext.ensureActive()
        val captions = WhisperCaptionEngine.transcribe(context.applicationContext, Uri.fromFile(local), language,
            onProgress = onProgress) ?: return@withContext null
        val cues = captions.flatMap(::coarseWordCues)
        SubtitleResult(captions, cues, hasWordLevelTiming = false)
    }

    /** Conservative words for typography only: every token retains the entire source phrase interval. */
    fun coarseWordCues(segment: WhisperCaptionEngine.CaptionSegment): List<WordCue> =
        Regex("[^\\s]+", RegexOption.MULTILINE).findAll(segment.text).map { match ->
            WordCue(match.value, segment.startMs, segment.endMs, TimingPrecision.WHISPER_SEGMENT)
        }.toList()
}

/** Result of genuine CTC forced alignment. Word boundaries come from model frame emissions, not
 * proportional allocation across transcript characters or words. */
data class CtcAlignmentResult(val words: List<WordCue>, val score: Float)

object CtcForcedAligner {
    /**
     * Align target CTC token IDs to log-probability emissions [frames][vocabulary].
     * [wordIndexForToken] maps each target token to its transcript word (or -1 for separators).
     * Inputs must be emitted by a compatible local acoustic/aligner model and its tokenizer.
     *
     * This exact Viterbi alignment primitive does not itself choose/download a model: consumers must
     * run their explicitly installed compatible local ONNX aligner and pass its emissions here.
     */
    fun align(
        logProbabilities: Array<FloatArray>,
        blankId: Int,
        targetTokenIds: IntArray,
        wordIndexForToken: IntArray,
        wordTexts: List<String>,
        frameDurationMs: Float,
        startOffsetMs: Long = 0L,
    ): CtcAlignmentResult {
        require(logProbabilities.isNotEmpty()) { "No CTC frames" }
        require(targetTokenIds.isNotEmpty() && targetTokenIds.size == wordIndexForToken.size) { "Target tokens and word map must be non-empty and equal length" }
        require(wordTexts.isNotEmpty() && wordIndexForToken.filter { it >= 0 }.all { it < wordTexts.size }) { "Word map references missing transcript text" }
        require(frameDurationMs > 0f && frameDurationMs.isFinite())
        val vocab = logProbabilities.first().size
        require(blankId in 0 until vocab && targetTokenIds.all { it in 0 until vocab }) { "Token ID outside vocabulary" }
        require(logProbabilities.all { it.size == vocab && it.all(Float::isFinite) }) { "Ragged or non-finite CTC emissions" }
        require(wordIndexForToken.all { it >= -1 })

        val states = targetTokenIds.size * 2 + 1
        val labels = IntArray(states) { if (it % 2 == 0) blankId else targetTokenIds[it / 2] }
        val frames = logProbabilities.size
        require(frames >= targetTokenIds.size) { "Fewer acoustic frames than target tokens" }
        val back = Array(frames) { IntArray(states) { -1 } }
        var prev = FloatArray(states) { Float.NEGATIVE_INFINITY }
        prev[0] = logProbabilities[0][blankId]
        prev[1] = logProbabilities[0][labels[1]]
        for (t in 1 until frames) {
            val row = logProbabilities[t]
            val next = FloatArray(states) { Float.NEGATIVE_INFINITY }
            for (s in 0 until states) {
                var best = prev[s]
                var from = s
                if (s > 0 && prev[s - 1] > best) { best = prev[s - 1]; from = s - 1 }
                if (s > 1 && labels[s] != blankId && labels[s] != labels[s - 2] && prev[s - 2] > best) {
                    best = prev[s - 2]; from = s - 2
                }
                if (best.isFinite()) {
                    next[s] = best + row[labels[s]]
                    back[t][s] = from
                }
            }
            prev = next
        }
        var state = if (prev[states - 1] > prev[states - 2]) states - 1 else states - 2
        if (!prev[state].isFinite()) throw IllegalArgumentException("Transcript cannot be aligned to supplied emissions")
        val tokenFrames = Array(targetTokenIds.size) { ArrayList<Int>() }
        var pathScore = 0f
        for (t in frames - 1 downTo 0) {
            if (state % 2 == 1) {
                val token = state / 2
                tokenFrames[token].add(t)
                pathScore += logProbabilities[t][targetTokenIds[token]]
            }
            if (t > 0) {
                state = back[t][state]
                if (state < 0) throw IllegalArgumentException("Incomplete CTC alignment path")
            }
        }
        val grouped = sortedMapOf<Int, MutableList<Int>>()
        val tokenConfidence = mutableMapOf<Int, MutableList<Float>>()
        tokenFrames.forEachIndexed { token, times ->
            val word = wordIndexForToken[token]
            if (word >= 0 && times.isNotEmpty()) {
                grouped.getOrPut(word) { mutableListOf() }.addAll(times)
                tokenConfidence.getOrPut(word) { mutableListOf() }.addAll(times.map {
                    kotlin.math.exp(logProbabilities[it][targetTokenIds[token]]).coerceIn(0f, 1f)
                })
            }
        }
        val cues = grouped.map { (word, times) ->
            val first = times.minOrNull()!!
            val last = times.maxOrNull()!! + 1
            val confidence = tokenConfidence.getValue(word).average().toFloat()
            WordCue(wordTexts[word], startOffsetMs + (first * frameDurationMs).toLong(),
                startOffsetMs + (last * frameDurationMs).toLong(),
                TimingPrecision.CTC_FORCED_WORD_ALIGNMENT, confidence)
        }
        val totalTokens = tokenFrames.sumOf { it.size }.coerceAtLeast(1)
        return CtcAlignmentResult(cues, kotlin.math.exp(pathScore / totalTokens).coerceIn(0f, 1f))
    }
}

/** Signature for an explicitly installed acoustic CTC model used to align known transcript tokens. */
data class CtcOnnxModelContract(
    val inputName: String,
    val outputName: String,
    val blankTokenId: Int,
    val vocabularySize: Int,
    val frameDurationMs: Float,
    val sampleRateHz: Int = 16_000,
) {
    init {
        require(inputName.isNotBlank() && outputName.isNotBlank())
        require(blankTokenId >= 0 && vocabularySize > 0 && blankTokenId < vocabularySize)
        require(frameDurationMs > 0 && frameDurationMs.isFinite() && sampleRateHz == 16_000) {
            "The local PCM extractor supplies 16 kHz mono audio"
        }
    }
}

/**
 * Local-only ONNX CTC forced aligner. Contract is waveform `[1,samples]` float32 and raw logits
 * `[1,frames,vocabularySize]` float32. Token IDs and tokenizer are caller-supplied because there
 * is no single CTC vocabulary shared by Whisper or other acoustic models. No model is downloaded.
 */
class OnnxCtcWordAligner(
    private val modelFile: File,
    private val contract: CtcOnnxModelContract,
) {
    private val environment: OrtEnvironment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OrtEnvironment.getEnvironment() }

    suspend fun alignFile(
        context: Context,
        audioFile: File,
        targetTokenIds: IntArray,
        wordIndexForToken: IntArray,
        wordTexts: List<String>,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): CtcAlignmentResult = withContext(Dispatchers.Default) {
        val audio = audioFile.canonicalFile
        val model = modelFile.canonicalFile
        if (!audio.isFile || audio.length() == 0L) throw IllegalArgumentException("Local audio file is unavailable")
        if (!model.isFile || model.length() == 0L) throw IllegalStateException("Compatible local CTC ONNX model is not installed")
        coroutineContext.ensureActive()
        onProgress(0.05f, "decoding local audio")
        val pcm = withContext(Dispatchers.IO) { PcmAudioExtractor.extractMono16k(context.applicationContext, Uri.fromFile(audio)) }
            ?: throw IllegalArgumentException("Audio file has no decodable audio stream")
        require(pcm.isNotEmpty()) { "Audio file is empty" }
        coroutineContext.ensureActive()
        onProgress(0.2f, "validating local CTC model")
        val session = try {
            OrtSession.SessionOptions().use { options -> environment.createSession(model.absolutePath, options) }
        }
        catch (e: Exception) { throw IllegalStateException("Cannot open local CTC ONNX model: ${e.message}", e) }
        session.use { s ->
            val inputInfo = s.inputInfo[contract.inputName]?.info as? TensorInfo
                ?: throw IllegalStateException("CTC model has no tensor input '${contract.inputName}'")
            val outputInfo = s.outputInfo[contract.outputName]?.info as? TensorInfo
                ?: throw IllegalStateException("CTC model has no tensor output '${contract.outputName}'")
            val inShape = inputInfo.shape
            val outShape = outputInfo.shape
            if (inputInfo.type != OnnxJavaType.FLOAT || inShape.size != 2 || (inShape[0] > 0 && inShape[0] != 1L))
                throw IllegalStateException("Expected float32 CTC input [1,samples], found ${inShape.contentToString()} ${inputInfo.type}")
            if (outputInfo.type != OnnxJavaType.FLOAT || outShape.size != 3 || (outShape[0] > 0 && outShape[0] != 1L) ||
                (outShape[2] > 0 && outShape[2] != contract.vocabularySize.toLong()))
                throw IllegalStateException("Expected float32 CTC output [1,frames,${contract.vocabularySize}], found ${outShape.contentToString()} ${outputInfo.type}")
            coroutineContext.ensureActive()
            onProgress(0.35f, "running local CTC inference")
            val inputTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(pcm), longArrayOf(1, pcm.size.toLong()))
            inputTensor.use { tensor ->
                s.run(mapOf(contract.inputName to tensor)).use { results ->
                    coroutineContext.ensureActive()
                    val logits = results.get(contract.outputName).orElse(null) as? OnnxTensor
                        ?: throw IllegalStateException("CTC output is not a tensor")
                    logits.use { output ->
                        val shape = output.info.shape
                        if (shape.size != 3 || shape[0] != 1L || shape[1] <= 0L || shape[2] != contract.vocabularySize.toLong())
                            throw IllegalStateException("Invalid CTC output shape ${shape.contentToString()}")
                        val frames = shape[1].toInt()
                        val buffer = output.floatBuffer
                        if (buffer.remaining() != frames * contract.vocabularySize)
                            throw IllegalStateException("CTC logits tensor has unexpected data size")
                        val emissions = Array(frames) { frame ->
                            if (frame % 128 == 0) coroutineContext.ensureActive()
                            val row = FloatArray(contract.vocabularySize)
                            var max = Float.NEGATIVE_INFINITY
                            for (v in row.indices) { row[v] = buffer.get(); if (row[v] > max) max = row[v] }
                            var sum = 0.0
                            for (v in row.indices) sum += kotlin.math.exp((row[v] - max).toDouble())
                            val logNormalizer = max + kotlin.math.ln(sum).toFloat()
                            for (v in row.indices) row[v] -= logNormalizer
                            row
                        }
                        onProgress(0.85f, "aligning transcript tokens")
                        val result = CtcForcedAligner.align(emissions, contract.blankTokenId, targetTokenIds,
                            wordIndexForToken, wordTexts, contract.frameDurationMs)
                        coroutineContext.ensureActive()
                        onProgress(1f, "complete")
                        result
                    }
                }
            }
        }
    }
}
