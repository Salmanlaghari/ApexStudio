package com.apexstudio.app.data.captions

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * On-device Whisper (tiny, multilingual, int8) via the official
 * `com.microsoft.onnxruntime:onnxruntime-android` Maven artifact.
 *
 * Model files come from k2-fsa's sherpa-onnx export
 * (`csukuangfj/sherpa-onnx-whisper-tiny` on HuggingFace), downloaded
 * on demand through PackManager — never bundled in the APK.
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
    private val tokenTable: Map<Int, String>
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

        /** Returns null (with a log) on any failure — never throws. */
        fun load(encoderFile: File, decoderFile: File, tokensFile: File): WhisperModel? {
            if (!encoderFile.exists() || !decoderFile.exists() || !tokensFile.exists()) {
                Log.w(TAG, "whisper model files missing")
                return null
            }
            return try {
                val env = OrtEnvironment.getEnvironment()
                val threads = minOf(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(threads)
                    setInterOpNumThreads(1)
                }
                val encoder = env.createSession(encoderFile.absolutePath, opts)
                val decoder = env.createSession(decoderFile.absolutePath, opts)
                val meta = readMeta(encoder)
                val tokens = readTokens(tokensFile)
                Log.i(TAG, "loaded whisper tiny (multilingual=${meta.isMultilingual}, " +
                    "layers=${meta.nTextLayer}, langs=${meta.langCodes.size})")
                WhisperModel(env, encoder, decoder, meta, tokens)
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
        private fun readTokens(file: File): Map<Int, String> {
            val map = HashMap<Int, String>(50257)
            file.forEachLine { line ->
                val parts = line.trim().split(" ")
                if (parts.size == 2) {
                    val id = parts[1].toIntOrNull()
                    if (id != null) {
                        try {
                            val bytes = android.util.Base64.decode(parts[0], android.util.Base64.DEFAULT)
                            map[id] = String(bytes, Charsets.UTF_8)
                        } catch (_: Exception) { /* skip bad line */ }
                    }
                }
            }
            return map
        }
    }

    /** Decode a base64 GPT-2 BPE token id to its text ("" if unknown). */
    fun tokenText(id: Int): String = tokenTable[id] ?: ""

    /**
     * Transcribe one ≤30 s mel chunk ([80 × 3000] row-major).
     *
     * @param langTokenId language token id, or null to auto-detect from
     *   the audio (first 30 s only — callers reuse the detected id for
     *   later chunks).
     * @param onStep invoked every few generated tokens with the count so
     *   far (for progress UI).
     */
    fun transcribeChunk(
        mel: FloatArray,
        langTokenId: Int?,
        onStep: (generated: Int) -> Unit = {}
    ): ChunkResult {
        val encIn = encoder.inputNames.toList()
        val melTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(mel),
            longArrayOf(1, meta.nMels.toLong(), WhisperMelSpectrogram.N_FRAMES.toLong())
        )
        // Encoder outputs the cross-attention K/V directly.
        // NOTE: the Result OWNS its output tensors (closing it closes
        // them), so it stays open until crossK/crossV are closed.
        val encResult = encoder.run(mapOf(encIn[0] to melTensor))
        melTensor.close() // input consumed by run()
        val crossK = encResult[0] as OnnxTensor
        val crossV = encResult[1] as OnnxTensor
        try {
            val lang = langTokenId ?: detectLanguage(crossK, crossV)
            val ids = greedyDecode(crossK, crossV, lang, onStep)
            return ChunkResult(lang, ids)
        } finally {
            crossK.close()
            crossV.close()
            encResult.close() // after its values; double-close is guarded
        }
    }

    /** Language id: argmax over language tokens only (cf. test.py detect_language). */
    private fun detectLanguage(crossK: OnnxTensor, crossV: OnnxTensor): Int {
        val kIn = zeroCache()
        val vIn = zeroCache()
        val step = runDecoderStep(
            longArrayOf(meta.sot.toLong()), kIn, vIn, crossK, crossV, 0L
        )
        kIn.close() // inputs consumed by run()
        vIn.close()
        val row = step.logits
        var best = meta.langTokens.firstOrNull() ?: meta.sot
        var bestV = Float.NEGATIVE_INFINITY
        for (id in meta.langTokens) {
            if (id < row.size && row[id] > bestV) { bestV = row[id]; best = id }
        }
        step.close()
        Log.i(TAG, "detected language: ${meta.idToLang[best] ?: "?"} (token $best)")
        return best
    }

    private data class StepResult(
        val logits: FloatArray, // last position row, suppressible copy
        /** Fresh output caches — ownership passes to the caller. */
        val kCache: OnnxTensor,
        val vCache: OnnxTensor,
        /** Backing run result — owns kCache/vCache; closed AFTER them. */
        private val result: OrtSession.Result
    ) {
        /** Close caches, then the backing result (caches first). */
        fun close() {
            try { kCache.close() } catch (_: Exception) {}
            try { vCache.close() } catch (_: Exception) {}
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
        val off = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(offset)), longArrayOf(1))
        val inputs = mapOf(
            decIn[0] to tokens,
            decIn[1] to kCache,
            decIn[2] to vCache,
            decIn[3] to crossK,
            decIn[4] to crossV,
            decIn[5] to off
        )
        val res = decoder.run(inputs)
        val logitsT = res[0] as OnnxTensor
        val outK = res[1] as OnnxTensor
        val outV = res[2] as OnnxTensor
        val shape = logitsT.info.shape // [1, T, vocab]
        val t = shape[1].toInt()
        val vocab = shape[2].toInt()
        val buf = logitsT.floatBuffer
        val row = FloatArray(vocab)
        val base = (t - 1) * vocab
        for (i in 0 until vocab) row[i] = buf.get(base + i)
        tokens.close() // inputs consumed by run()
        off.close()
        // NOTE: res is NOT closed here — it owns outK/outV. The caller's
        // StepResult.close() closes caches first, then res.
        return StepResult(row, outK, outV, res)
    }

    private fun suppress(logits: FloatArray, isInitial: Boolean) {
        // Mirrors test.py suppress_tokens. The no-timestamps token is
        // ALWAYS suppressed: captions need timestamp tokens.
        if (isInitial) {
            logits[meta.eot] = Float.NEGATIVE_INFINITY
            logits[meta.blank] = Float.NEGATIVE_INFINITY
        }
        logits[meta.noTimestamps] = Float.NEGATIVE_INFINITY
        logits[meta.sot] = Float.NEGATIVE_INFINITY
        logits[meta.noSpeech] = Float.NEGATIVE_INFINITY
        logits[meta.translate] = Float.NEGATIVE_INFINITY
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
        val zeroK = zeroCache()
        val zeroV = zeroCache()
        var step = runDecoderStep(
            sotSeq.map { it.toLong() }.toLongArray(),
            zeroK, zeroV, crossK, crossV, 0L
        )
        zeroK.close(); zeroV.close()
        try {
            var offset = sotSeq.size.toLong()
            suppress(step.logits, isInitial = true)
            var next = argmax(step.logits)

            var count = 0
            while (next != meta.eot && results.size < meta.nTextCtx) {
                results.add(next)
                count++
                if (count % 8 == 0) onStep(count)
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
