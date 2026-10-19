package com.apexstudio.app.data.captions

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import java.io.Closeable
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * On-device Whisper (tiny, multilingual, int8) via the official
 * `com.microsoft.onnxruntime:onnxruntime-android` Maven artifact.
 *
 * Model files come from k2-fsa's sherpa-onnx export
 * (`csukuangfj/sherpa-onnx-whisper-tiny` on HuggingFace). Pinned weights
 * are bundled for offline first use; legacy PackManager installs remain a fallback.
 *
 * The inference contract mirrors k2-fsa's reference `test.py`
 * (scripts/whisper/test.py) exactly:
 *  - Encoder: inputs[0] = mel [1, 80, 3000]; outputs = (crossK, crossV).
 *  - Decoder inputs (positional): [tokens, selfKCache, selfVCache,
 *    crossK, crossV, offset]; outputs: [logits, outSelfK, outSelfV].
 *  - Self-cache shape: [nTextLayer, 1, nTextCtx, nTextState].
 *  - All token ids (sot/eot/lang/...) come from the encoder's embedded
 *    ONNX metadata — nothing is hardcoded except the whisper-standard
 *    timestamp base (50364) and 0.02 s/token.
 *
 * Threading: 4 intra-op threads (matches the reference script).
 */
class WhisperModel private constructor(
    private val env: OrtEnvironment,
    private val encoder: OrtSession,
    private val decoder: OrtSession,
    val meta: Meta,
    private val tokenTable: Map<Int, ByteArray>
) : Closeable {

    data class Meta(
        val sot: Int,
        val eot: Int,
        val transcribe: Int,
        val translate: Int,
        val noTimestamps: Int,
        val noSpeech: Int,
        val blank: Int,
        val sotSequence: IntArray, // [sot, lang, task]
        val langCodes: List<String>,
        val langTokens: List<Int>,
        val isMultilingual: Boolean,
        val nTextLayer: Int,
        val nTextCtx: Int,
        val nTextState: Int,
        val nMels: Int
    ) {
        val langToId: Map<String, Int> = langCodes.zip(langTokens).toMap()
        val idToLang: Map<Int, String> = langTokens.zip(langCodes).toMap()
    }

    data class ChunkResult(
        /** Decoded language token id (detected or requested). */
        val langTokenId: Int,
        /** Raw token ids including timestamp tokens, up to (not incl.) eot. */
        val tokenIds: IntArray
    )

    companion object {
        private const val TAG = "WhisperModel"
        const val ENCODER_FILE = "tiny-encoder.int8.onnx"
        const val DECODER_FILE = "tiny-decoder.int8.onnx"
        const val TOKENS_FILE = "tiny-tokens.txt"

        /** Whisper-standard: first timestamp token id; each step = 20 ms. */
        const val TIMESTAMP_BEGIN = 50364
        const val TIMESTAMP_UNIT_MS = 20L

        /** Denies unapproved processing before file/native access; model-loading failures return null. */
        internal fun load(encoderFile: File, decoderFile: File, tokensFile: File): WhisperModel? {
            GlobalModerationEnforcement.requireApproved(ProtectedAiAction.WHISPER_CAPTIONS)
            if (!encoderFile.exists() || !decoderFile.exists() || !tokensFile.exists()) {
                Log.w(TAG, "whisper model files missing")
                return null
            }
            return try {
                val env = OrtEnvironment.getEnvironment()
                val threads = minOf(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
                OrtSession.SessionOptions().use { opts ->
                    opts.setIntraOpNumThreads(threads)
                    opts.setInterOpNumThreads(1)
                    val encoder = env.createSession(encoderFile.absolutePath, opts)
                    var decoder: OrtSession? = null
                    try {
                        val loadedDecoder = env.createSession(decoderFile.absolutePath, opts)
                        decoder = loadedDecoder
                        val encOk = encoder.inputNames.size == 1 && encoder.outputNames.size == 2
                        val decOk = loadedDecoder.inputNames.size == 6 && loadedDecoder.outputNames.size == 3 &&
                            "tokens" in loadedDecoder.inputNames && "offset" in loadedDecoder.inputNames
                        check(encOk && decOk) { "Unexpected Whisper encoder/decoder input-output contract" }
                        val meta = readMeta(encoder)
                        val tokens = readTokens(tokensFile)
                        check(tokens.isNotEmpty()) { "Whisper token table is empty" }
                        Log.i(TAG, "loaded whisper tiny (multilingual=${meta.isMultilingual}, " +
                            "layers=${meta.nTextLayer}, langs=${meta.langCodes.size})")
                        WhisperModel(env, encoder, loadedDecoder, meta, tokens)
                    } catch (error: Throwable) {
                        try { decoder?.close() } catch (_: Exception) {}
                        try { encoder.close() } catch (_: Exception) {}
                        throw error
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "failed to load whisper model", e)
                null
            }
        }

        private fun readMeta(encoder: OrtSession): Meta {
            val md = encoder.metadata.customMetadata
            fun i(key: String): Int = md[key]?.toIntOrNull()
                ?: throw IllegalStateException("whisper encoder metadata missing '$key'")
            val codes = (md["all_language_codes"] ?: "")
                .split(",").filter { it.isNotBlank() }
            val toks = (md["all_language_tokens"] ?: "")
                .split(",").mapNotNull { it.toIntOrNull() }
            return Meta(
                sot = i("sot"),
                eot = i("eot"),
                transcribe = i("transcribe"),
                translate = i("translate"),
                noTimestamps = i("no_timestamps"),
                noSpeech = i("no_speech"),
                blank = i("blank_id"),
                sotSequence = (md["sot_sequence"] ?: "")
                    .split(",").mapNotNull { it.toIntOrNull() }.toIntArray(),
                langCodes = codes,
                langTokens = toks,
                isMultilingual = (md["is_multilingual"]?.toIntOrNull() ?: 0) == 1,
                nTextLayer = i("n_text_layer"),
                nTextCtx = i("n_text_ctx"),
                nTextState = i("n_text_state"),
                nMels = i("n_mels")
            )
        }

        /** tokens.txt lines are `<base64-token> <id>` (see test.py load_tokens). */
        private fun readTokens(file: File): Map<Int, ByteArray> {
            val map = HashMap<Int, ByteArray>(50257)
            file.forEachLine { line ->
                val parts = line.trim().split(" ")
                if (parts.size == 2) {
                    val id = parts[1].toIntOrNull()
                    if (id != null) {
                        try {
                            val bytes = android.util.Base64.decode(parts[0], android.util.Base64.DEFAULT)
                            map[id] = bytes
                        } catch (_: Exception) { /* skip bad line */ }
                    }
                }
            }
            return map
        }
    }

    /** BPE tokens can divide a UTF-8 code point. Decode only after joining a whole phrase. */
    fun decodeText(tokenIds: List<Int>): String = decodeWhisperTokenBytes(tokenIds, tokenTable)

    /**
     * Transcribe one ≤30 s mel chunk ([80 × 3000] row-major).
     *
     * @param langTokenId language token id, or null to auto-detect from
     *   the audio (first 30 s only — callers reuse the detected id for
     *   later chunks).
     * @param onStep invoked every few generated tokens with the count so
     *   far (for progress UI).
     */
    internal fun transcribeChunk(
        mel: FloatArray,
        langTokenId: Int?,
        onStep: (generated: Int) -> Unit = {}
    ): ChunkResult {
        // Keep the boundary closed even for future in-module callers of the raw model.
        GlobalModerationEnforcement.requireApproved(ProtectedAiAction.WHISPER_CAPTIONS)
        val encIn = encoder.inputNames.toList()
        val melTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(mel),
            longArrayOf(1, meta.nMels.toLong(), WhisperMelSpectrogram.N_FRAMES.toLong())
        )
        // Encoder outputs the cross-attention K/V directly.
        // NOTE: the Result OWNS its output tensors (closing it closes
        // them), so it stays open until crossK/crossV are closed.
        val encResult = melTensor.use { encoder.run(mapOf(encIn[0] to it)) }
        encResult.use {
            val crossK = encResult[0] as OnnxTensor
            val crossV = encResult[1] as OnnxTensor
            val lang = langTokenId ?: detectLanguage(crossK, crossV)
            val ids = greedyDecode(crossK, crossV, lang, onStep)
            return ChunkResult(lang, ids)
        }
    }

    /** Language id: argmax over language tokens only (cf. test.py detect_language). */
    private fun detectLanguage(crossK: OnnxTensor, crossV: OnnxTensor): Int {
        val step = zeroCache().use { kIn ->
            zeroCache().use { vIn ->
                runDecoderStep(longArrayOf(meta.sot.toLong()), kIn, vIn, crossK, crossV, 0L)
            }
        }
        try {
            val row = step.logits
            var best = meta.langTokens.firstOrNull() ?: meta.sot
            var bestV = Float.NEGATIVE_INFINITY
            for (id in meta.langTokens) {
                if (id in row.indices && row[id] > bestV) { bestV = row[id]; best = id }
            }
            Log.i(TAG, "detected language: ${meta.idToLang[best] ?: "?"} (token $best)")
            return best
        } finally {
            step.close()
        }
    }

    private data class StepResult(
        val logits: FloatArray, // last position row, suppressible copy
        /** Fresh output caches — ownership passes to the caller. */
        val kCache: OnnxTensor,
        val vCache: OnnxTensor,
        /** Backing run result is the sole owner of kCache/vCache. */
        private val result: OrtSession.Result
    ) {
        /** Release the result and all its owned output tensors exactly once. */
        fun close() {
            try { result.close() } catch (_: Exception) {}
        }
    }

    private fun zeroCache(): OnnxTensor {
        val n = meta.nTextLayer * 1 * meta.nTextCtx * meta.nTextState
        return OnnxTensor.createTensor(
            env, FloatBuffer.wrap(FloatArray(n)),
            longArrayOf(meta.nTextLayer.toLong(), 1, meta.nTextCtx.toLong(), meta.nTextState.toLong())
        )
    }

    /**
     * One decoder step. Returns a *copy* of the last-position logits row
     * (safe to suppress in place) plus the new KV caches (caller-owned).
     * The returned StepResult keeps the run's Result alive: closing the
     * Result would close the caches it owns.
     */
    private fun runDecoderStep(
        tokenIds: LongArray,
        kCache: OnnxTensor,
        vCache: OnnxTensor,
        crossK: OnnxTensor,
        crossV: OnnxTensor,
        offset: Long
    ): StepResult {
        val decIn = decoder.inputNames.toList()
        val tokens = OnnxTensor.createTensor(
            env, LongBuffer.wrap(tokenIds), longArrayOf(1, tokenIds.size.toLong())
        )
        val res = tokens.use {
            OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(offset)), longArrayOf(1)).use { off ->
                decoder.run(mapOf(
                    decIn[0] to tokens, decIn[1] to kCache, decIn[2] to vCache,
                    decIn[3] to crossK, decIn[4] to crossV, decIn[5] to off
                ))
            }
        }
        try {
            val logitsT = res[0] as OnnxTensor
            val outK = res[1] as OnnxTensor
            val outV = res[2] as OnnxTensor
            val shape = logitsT.info.shape
            check(shape.size == 3 && shape[0] == 1L && shape[1] > 0L && shape[2] in 1L..Int.MAX_VALUE.toLong()) {
                "Invalid Whisper logits shape ${shape.contentToString()}"
            }
            val vocab = shape[2].toInt()
            val buf = logitsT.floatBuffer
            val base = (shape[1] - 1L) * vocab
            check(base <= Int.MAX_VALUE.toLong() - vocab && base + vocab <= buf.remaining().toLong()) {
                "Whisper logits size does not match shape"
            }
            val row = FloatArray(vocab) { i -> buf.get(base.toInt() + i) }
            return StepResult(row, outK, outV, res)
        } catch (error: Throwable) {
            res.close()
            throw error
        }
    }

    private fun suppress(logits: FloatArray, isInitial: Boolean) {
        // Mirrors test.py suppress_tokens. The no-timestamps token is
        // ALWAYS suppressed: captions need timestamp tokens.
        // Bounds-guarded: a model whose metadata disagrees with its
        // vocab must degrade to a wrong token, never a crash.
        fun kill(id: Int) {
            if (id in logits.indices) logits[id] = Float.NEGATIVE_INFINITY
        }
        if (isInitial) {
            kill(meta.eot)
            kill(meta.blank)
        }
        kill(meta.noTimestamps)
        kill(meta.sot)
        kill(meta.noSpeech)
        kill(meta.translate)
    }

    private fun greedyDecode(
        crossK: OnnxTensor,
        crossV: OnnxTensor,
        langTokenId: Int,
        onStep: (Int) -> Unit
    ): IntArray {
        // [sot, lang, transcribe] — no_timestamps deliberately omitted so
        // the model emits timestamp tokens (caption timing).
        val sotSeq = meta.sotSequence.toMutableList()
        if (sotSeq.size >= 2) sotSeq[1] = langTokenId
        if (sotSeq.size >= 3) sotSeq[2] = meta.transcribe

        val results = ArrayList<Int>(128)
        // Ownership: each step's output caches become the next step's
        // inputs. A step's inputs are consumed by run() and closed right
        // after the next step returns; the previous step's Result is
        // closed along with its caches (caches first).
        val decodeBudget = whisperDecodeBudget(meta.nTextCtx, sotSeq.size)
        var step = zeroCache().use { zeroK ->
            zeroCache().use { zeroV ->
                runDecoderStep(sotSeq.map { it.toLong() }.toLongArray(), zeroK, zeroV, crossK, crossV, 0L)
            }
        }
        try {
            var offset = sotSeq.size.toLong()
            suppress(step.logits, isInitial = true)
            var next = argmax(step.logits)

            var count = 0
            while (next != meta.eot && results.size < decodeBudget) {
                results.add(next)
                count++
                onStep(count)
                if (results.size == decodeBudget) break
                val prev = step
                step = runDecoderStep(
                    longArrayOf(next.toLong()), prev.kCache, prev.vCache,
                    crossK, crossV, offset
                )
                prev.close() // its inputs are consumed; close caches + result
                offset += 1
                suppress(step.logits, isInitial = false)
                next = argmax(step.logits)
            }
            onStep(count)
        } finally {
            step.close()
        }
        return results.toIntArray()
    }

    private fun argmax(a: FloatArray): Int {
        if (a.isEmpty()) throw IllegalStateException("empty logits row — model returned no vocab")
        var best = 0
        var bestV = a[0]
        for (i in 1 until a.size) {
            if (a[i] > bestV) { bestV = a[i]; best = i }
        }
        return best
    }

    override fun close() {
        try { encoder.close() } catch (_: Exception) {}
        try { decoder.close() } catch (_: Exception) {}
    }
}

/** Byte-level BPE must be joined before UTF-8 decoding, especially for Urdu/Hindi and emoji. */
internal fun decodeWhisperTokenBytes(tokenIds: List<Int>, table: Map<Int, ByteArray>): String {
    val bytes = ByteArrayOutputStream()
    tokenIds.forEach { id -> table[id]?.let { bytes.write(it, 0, it.size) } }
    return bytes.toByteArray().toString(Charsets.UTF_8)
}

/** Prompt tokens consume the same decoder context as generated tokens. */
internal fun whisperDecodeBudget(contextSize: Int, promptSize: Int): Int {
    require(promptSize > 0 && contextSize > promptSize) { "Whisper prompt must fit in its decoder context" }
    return contextSize - promptSize
}
