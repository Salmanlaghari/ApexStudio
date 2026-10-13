package com.apexstudio.app.data.captions

import android.content.Context
import android.net.Uri
import android.util.Log
import com.apexstudio.app.data.audio.PcmAudioExtractor
import com.apexstudio.app.data.packs.download.PackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * AI auto-captions: on-device Whisper (tiny, multilingual, int8).
 *
 * Pipeline: PackManager model pack -> PCM extract (16 kHz mono) ->
 * 30 s chunks -> log-mel -> Whisper encode/decode -> timestamp tokens ->
 * timed caption segments.
 *
 * The model pack is downloaded on demand and verified (sha256) by
 * PackManager; it is never bundled in the APK.
 */
object WhisperCaptionEngine {

    private const val TAG = "WhisperCaptionEngine"

    /**
     * The Whisper tiny model ships as 3 file packs (one URL per pack is
     * all PackManager supports; a single zip can't be hosted — GitHub
     * release uploads and 60MB+ git blobs are both blocked from here).
     * Files live on HuggingFace (k2-fsa's canonical distribution) with
     * sha256 verification, downloaded on demand, never in the APK.
     * Total ~103MB.
     */
    const val PACK_ENCODER = "whisper-tiny-encoder"
    const val PACK_DECODER = "whisper-tiny-decoder"
    const val PACK_TOKENS = "whisper-tiny-tokens"
    val PACK_IDS = listOf(PACK_ENCODER, PACK_DECODER, PACK_TOKENS)

    /** One timed caption segment. Times are absolute ms on the timeline. */
    data class CaptionSegment(val text: String, val startMs: Long, val endMs: Long)

    /** Language codes the UI offers; "auto" = detect from the audio. */
    const val LANG_AUTO = "auto"

    /** Heuristic tokens per 30 s chunk, for progress display only. */
    private const val EXPECTED_TOKENS_PER_CHUNK = 150

    /** The 3 model files, or null when any is missing. */
    fun modelFiles(context: Context): Triple<File, File, File>? {
        val enc = File(PackManager.packDir(context, PACK_ENCODER), WhisperModel.ENCODER_FILE)
        val dec = File(PackManager.packDir(context, PACK_DECODER), WhisperModel.DECODER_FILE)
        val tok = File(PackManager.packDir(context, PACK_TOKENS), WhisperModel.TOKENS_FILE)
        return if (enc.exists() && dec.exists() && tok.exists()) Triple(enc, dec, tok) else null
    }

    /** True when all 3 model packs are installed with files on disk. */
    suspend fun isModelReady(context: Context): Boolean = withContext(Dispatchers.IO) {
        PACK_IDS.all { PackManager.isDownloaded(context, it) } && modelFiles(context) != null
    }

    /**
     * Transcribe [uri]'s audio into caption segments.
     *
     * @param language "auto" or a whisper language code ("ur", "hi", "en", …).
     * @param trimStartMs / trimEndMs the clip's active source range. The
     *   PCM is sliced to exactly this range (sample-exact), so returned
     *   segment times are **clip-relative** (0 = trim start) — matching
     *   [TextOverlay.startMs], which is defined on the clip's timeline.
     * @param onProgress invoked with 0..1 overall progress.
     * @return segments, or null when there is no usable audio / model.
     *   Empty list = model ran but heard no speech (caller reports that).
     */
    suspend fun transcribe(
        context: Context,
        uri: Uri,
        language: String = LANG_AUTO,
        trimStartMs: Long = 0L,
        trimEndMs: Long = Long.MAX_VALUE,
        onProgress: (Float) -> Unit = {}
    ): List<CaptionSegment>? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val (encFile, decFile, tokFile) = modelFiles(appContext)
            ?: return@withContext null // model files missing
        val model = WhisperModel.load(encFile, decFile, tokFile)
            ?: return@withContext null // caller surfaces "model failed to load"

        try {
            onProgress(0.02f)
            val fullPcm = PcmAudioExtractor.extractMono16k(appContext, uri)
                ?: return@withContext null // no decodable audio
            // Slice to the clip's trim range (sample-exact) — captions
            // must cover only the active portion, on the clip's timeline.
            val startSample = ((trimStartMs.coerceAtLeast(0L) *
                WhisperMelSpectrogram.SAMPLE_RATE) / 1000)
                .toInt().coerceIn(0, fullPcm.size)
            val endSample = if (trimEndMs == Long.MAX_VALUE) fullPcm.size
            else ((trimEndMs.coerceAtLeast(0L) *
                WhisperMelSpectrogram.SAMPLE_RATE) / 1000)
                .toInt().coerceIn(startSample, fullPcm.size)
            val pcm = fullPcm.copyOfRange(startSample, endSample)
            if (pcm.isEmpty()) return@withContext null
            onProgress(0.05f)

            val chunkSamples = WhisperMelSpectrogram.SAMPLE_RATE * 30
            val nChunks = (pcm.size + chunkSamples - 1) / chunkSamples
            val all = ArrayList<CaptionSegment>(64)
            var langTokenId: Int? = if (language == LANG_AUTO) null
            else model.meta.langToId[language]

            if (language != LANG_AUTO && langTokenId == null) {
                Log.w(TAG, "language '$language' not in model; falling back to auto-detect")
            }

            for (c in 0 until nChunks) {
                coroutineContext.ensureActive()
                val start = c * chunkSamples
                val end = minOf(start + chunkSamples, pcm.size)
                val chunk = pcm.copyOfRange(start, end)
                val mel = WhisperMelSpectrogram.compute(chunk)
                val chunkOffsetMs = c * 30_000L
                val result = model.transcribeChunk(mel, langTokenId) { generated ->
                    // Cancel promptly mid-chunk (the decoder loop can run
                    // 10-60 s per chunk; chunk-boundary checks alone would
                    // leave the Cancel button dead for up to 30 s).
                    coroutineContext.ensureActive()
                    // Coarse within-chunk progress. A 30 s chunk usually
                    // yields 50-150 tokens; 150 is the heuristic cap.
                    val frac = (c + (generated.coerceAtMost(EXPECTED_TOKENS_PER_CHUNK)
                        .toFloat() / EXPECTED_TOKENS_PER_CHUNK)) / nChunks
                    onProgress(0.05f + 0.9f * frac.coerceIn(0f, 1f))
                }
                if (langTokenId == null) langTokenId = result.langTokenId
                all += tokensToSegments(model, result.tokenIds, chunkOffsetMs)
                onProgress(0.05f + 0.9f * (c + 1).toFloat() / nChunks)
            }
            onProgress(1f)
            mergeShortSegments(all)
        } finally {
            model.close()
        }
    }

    /**
     * Parse whisper's token stream into timed segments.
     *
     * The model emits timestamp tokens (id >= 50364, each = 20 ms);
     * text between two timestamps is one segment:
     *   <|t0|> hello world <|t1|> <|t1|> foo bar <|t2|>
     */
    private fun tokensToSegments(
        model: WhisperModel,
        tokenIds: IntArray,
        chunkOffsetMs: Long
    ): List<CaptionSegment> {
        val out = ArrayList<CaptionSegment>()
        val text = StringBuilder()
        var segStartMs = 0L
        var hasText = false

        fun flush(endMs: Long) {
            val t = text.toString().trim()
            if (t.isNotEmpty() && endMs > segStartMs) {
                out.add(CaptionSegment(t, chunkOffsetMs + segStartMs, chunkOffsetMs + endMs))
            }
            text.clear()
            hasText = false
        }

        for (id in tokenIds) {
            if (id == model.meta.eot) break
            if (id >= WhisperModel.TIMESTAMP_BEGIN) {
                val tMs = (id - WhisperModel.TIMESTAMP_BEGIN) * WhisperModel.TIMESTAMP_UNIT_MS
                if (hasText) flush(tMs)
                segStartMs = tMs
            } else {
                // Skip control tokens that carry no text.
                if (id == model.meta.noSpeech || id == model.meta.sot) continue
                text.append(model.tokenText(id))
                hasText = true
            }
        }
        // Trailing text without a closing timestamp: close at chunk end.
        if (hasText) flush(30_000L)
        return out
    }

    /**
     * Whisper sometimes emits sub-second fragments; merge a segment
     * shorter than ~1 s into the next one when the gap is small, so
     * captions don't flicker.
     */
    private fun mergeShortSegments(segments: List<CaptionSegment>): List<CaptionSegment> {
        if (segments.size < 2) return segments
        val merged = ArrayList<CaptionSegment>(segments.size)
        var i = 0
        while (i < segments.size) {
            val cur = segments[i]
            val dur = cur.endMs - cur.startMs
            val next = segments.getOrNull(i + 1)
            if (dur < 1000 && next != null && next.startMs - cur.endMs < 1500) {
                merged.add(
                    CaptionSegment(
                        (cur.text + " " + next.text).trim(),
                        cur.startMs, next.endMs
                    )
                )
                i += 2
            } else {
                merged.add(cur)
                i += 1
            }
        }
        return merged
    }
}
