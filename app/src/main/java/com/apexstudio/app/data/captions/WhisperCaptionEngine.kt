package com.apexstudio.app.data.captions

import android.content.Context
import android.net.Uri
import android.util.Log
import com.apexstudio.app.ai.models.ModelAssetAccess
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import com.apexstudio.app.data.audio.PcmAudioExtractor
import com.apexstudio.app.data.packs.download.PackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * AI auto-captions: on-device Whisper (tiny, multilingual, int8).
 *
 * Pipeline: bundled model (or existing PackManager pack) -> PCM extract
 * (16 kHz mono) -> 30 s chunks -> log-mel -> Whisper encode/decode -> timestamp tokens ->
 * timed caption segments.
 *
 * The pinned multilingual tiny-int8 weights and tokens are bundled in the APK.
 * On first use they are copied, SHA-256-verified, and atomically installed to
 * app-private storage on an IO worker. No runtime network is required. Legacy
 * PackManager installs remain a fallback.
 */
object WhisperCaptionEngine {

    private const val TAG = "WhisperCaptionEngine"


    /** Legacy pack IDs retained so already-installed downloads keep working. */
    const val PACK_ENCODER = "whisper-tiny-encoder"
    const val PACK_DECODER = "whisper-tiny-decoder"
    const val PACK_TOKENS = "whisper-tiny-tokens"
    val PACK_IDS = listOf(PACK_ENCODER, PACK_DECODER, PACK_TOKENS)

    private const val BUNDLED_ASSET_DIR = "models/whisper"
    private const val BUNDLED_REVISION = "65176e2deb88badc814a94058666cadccc29b61c"
    private const val BUNDLED_MARKER = ".verified-revision"
    private data class ModelAsset(val name: String, val size: Long, val sha256: String)
    private val bundledAssets = listOf(
        ModelAsset(WhisperModel.ENCODER_FILE, 12_937_772L,
            "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"),
        ModelAsset(WhisperModel.DECODER_FILE, 89_855_401L,
            "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"),
        ModelAsset(WhisperModel.TOKENS_FILE, 816_730L,
            "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126")
    )

    /** One timed caption segment. Times are absolute ms on the timeline. */
    data class CaptionSegment(val text: String, val startMs: Long, val endMs: Long)

    /** Language codes the UI offers; "auto" = detect from the audio. */
    const val LANG_AUTO = "auto"

    /** Heuristic tokens per 30 s chunk, for progress display only. */
    private const val EXPECTED_TOKENS_PER_CHUNK = 150

    /** Files from the bundled private install, or from a legacy PackManager install. */
    fun modelFiles(context: Context, checkCancelled: () -> Unit = {}): Triple<File, File, File>? {
        downloadedModelFiles(context)?.let { return it }
        bundledModelFiles(context, checkCancelled)?.let { return it }
        return packModelFiles(context)
    }

    /** True offline once the bundled model is present in app-private storage. */
    suspend fun isModelReady(context: Context): Boolean = withContext(Dispatchers.IO) {
        val installContext = coroutineContext
        downloadedModelFiles(context) != null ||
            (try {
                installBundledModel(context.applicationContext) { installContext.ensureActive() }
                bundledModelFiles(context) { installContext.ensureActive() } != null
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }) ||
            (PACK_IDS.all { PackManager.isDownloaded(context, it) } && packModelFiles(context) != null)
    }

    private fun downloadedModelFiles(context: Context): Triple<File, File, File>? {
        val files = bundledAssets.map { asset ->
            ModelAssetAccess.installedFile(context, "$BUNDLED_ASSET_DIR/${asset.name}") ?: return null
        }
        if (files.indices.any { index -> !isWhisperAssetVerified(files[index], bundledAssets[index].size, bundledAssets[index].sha256) }) return null
        return Triple(files[0], files[1], files[2])
    }

    private fun packModelFiles(context: Context): Triple<File, File, File>? {
        val enc = File(PackManager.packDir(context, PACK_ENCODER), WhisperModel.ENCODER_FILE)
        val dec = File(PackManager.packDir(context, PACK_DECODER), WhisperModel.DECODER_FILE)
        val tok = File(PackManager.packDir(context, PACK_TOKENS), WhisperModel.TOKENS_FILE)
        return if (enc.isFile && dec.isFile && tok.isFile) Triple(enc, dec, tok) else null
    }

    private fun bundledModelFiles(context: Context, checkCancelled: () -> Unit = {}): Triple<File, File, File>? {
        checkCancelled()
        val dir = File(context.filesDir, "models/whisper/$BUNDLED_REVISION")
        if (File(dir, BUNDLED_MARKER).takeIf { it.isFile }?.readText() != BUNDLED_REVISION) return null
        val files = bundledAssets.map { File(dir, it.name) }
        if (files.indices.any { index ->
                !isWhisperAssetVerified(
                    files[index], bundledAssets[index].size, bundledAssets[index].sha256, checkCancelled,
                )
            }) return null
        return Triple(files[0], files[1], files[2])
    }

    /** Copy bundled assets into versioned storage; directory rename publishes only a complete verified set. */
    @Synchronized
    internal fun installBundledModel(context: Context, checkCancelled: () -> Unit = {}) {
        checkCancelled()
        if (bundledModelFiles(context, checkCancelled) != null) return
        val root = File(context.filesDir, "models/whisper")
        val target = File(root, BUNDLED_REVISION)
        val staging = File(root, ".staging-${Thread.currentThread().id}-${System.nanoTime()}")
        try {
            checkCancelled()
            check(root.isDirectory || root.mkdirs()) { "Cannot create Whisper model directory" }
            if (!staging.mkdirs()) throw IllegalStateException("Cannot create Whisper model staging directory")
            for (asset in bundledAssets) {
                checkCancelled()
                val stagedFile = File(staging, asset.name)
                val digest = MessageDigest.getInstance("SHA-256")
                ModelAssetAccess.open(context, "$BUNDLED_ASSET_DIR/${asset.name}").use { input ->
                    FileOutputStream(stagedFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
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
                val actualHash = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                check(stagedFile.length() == asset.size && actualHash == asset.sha256) {
                    "Bundled Whisper asset failed integrity verification: ${asset.name}"
                }
            }
            checkCancelled()
            File(staging, BUNDLED_MARKER).writeText(BUNDLED_REVISION)
            checkCancelled()
            if (target.exists()) check(target.deleteRecursively()) { "Cannot replace invalid Whisper model" }
            checkCancelled()
            check(staging.renameTo(target)) { "Could not atomically install bundled Whisper model" }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            try {
                staging.deleteRecursively()
            } catch (_: Exception) {
                // Preserve cancellation as the primary outcome.
            }
            throw cancelled
        } catch (error: Exception) {
            try { staging.deleteRecursively() } catch (_: Exception) { }
            Log.e(TAG, "Could not install bundled Whisper model", error)
        }
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
        // Enforce before model materialization, PCM decode, inference, or progress that implies success.
        GlobalModerationEnforcement.requireApproved(ProtectedAiAction.WHISPER_CAPTIONS)
        WhisperInferenceGate.withLock {
        val appContext = context.applicationContext
        val installContext = coroutineContext
        installBundledModel(appContext) { installContext.ensureActive() }
        val (encFile, decFile, tokFile) = modelFiles(appContext) { installContext.ensureActive() }
            ?: return@withLock null // model files missing
        val model = WhisperModel.load(encFile, decFile, tokFile)
            ?: return@withLock null // caller surfaces "model failed to load"

        try {
            coroutineContext.ensureActive()
            onProgress(0.02f)
            val decodeContext = coroutineContext
            val fullPcm = PcmAudioExtractor.extractMono16k(appContext, uri) { decodeContext.ensureActive() }
                ?: return@withLock null // no decodable audio
            // Slice to the clip's trim range (sample-exact) — captions
            // must cover only the active portion, on the clip's timeline.
            val startSample = sampleIndexAtTime(trimStartMs, WhisperMelSpectrogram.SAMPLE_RATE, fullPcm.size)
            val endSample = if (trimEndMs == Long.MAX_VALUE) fullPcm.size
                else sampleIndexAtTime(trimEndMs, WhisperMelSpectrogram.SAMPLE_RATE, fullPcm.size).coerceAtLeast(startSample)
            val pcm = fullPcm.copyOfRange(startSample, endSample)
            if (pcm.isEmpty()) return@withLock null
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
                val workerContext = coroutineContext
                val mel = WhisperMelSpectrogram.compute(chunk) { workerContext.ensureActive() }
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
                val chunkDurationMs = chunk.size.toLong() * 1000L / WhisperMelSpectrogram.SAMPLE_RATE
                all += tokensToSegments(model, result.tokenIds, chunkOffsetMs, chunkDurationMs)
                onProgress(0.05f + 0.9f * (c + 1).toFloat() / nChunks)
            }
            onProgress(1f)
            mergeShortSegments(all)
        } finally {
            model.close()
        }
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
        chunkOffsetMs: Long,
        chunkDurationMs: Long
    ): List<CaptionSegment> {
        val out = ArrayList<CaptionSegment>()
        val textTokens = ArrayList<Int>()
        var segStartMs = 0L
        var hasText = false

        fun flush(endMs: Long) {
            val t = model.decodeText(textTokens).trim()
            if (t.isNotEmpty() && endMs > segStartMs) {
                out.add(CaptionSegment(t, chunkOffsetMs + segStartMs, chunkOffsetMs + endMs))
            }
            textTokens.clear()
            hasText = false
        }

        for (id in tokenIds) {
            if (id == model.meta.eot) break
            if (id >= WhisperModel.TIMESTAMP_BEGIN) {
                val tMs = constrainWhisperTimestamp(
                    (id - WhisperModel.TIMESTAMP_BEGIN) * WhisperModel.TIMESTAMP_UNIT_MS,
                    segStartMs, chunkDurationMs
                )
                if (hasText) flush(tMs)
                segStartMs = tMs
            } else {
                // Skip control tokens that carry no text.
                if (id == model.meta.noSpeech || id == model.meta.sot) continue
                textTokens.add(id)
                hasText = true
            }
        }
        // Trailing text without a closing timestamp: close at chunk end.
        if (hasText) flush(chunkDurationMs)
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
            val gap = next?.startMs?.minus(cur.endMs)
            if (dur in 1L until 1000L && next != null && gap != null && gap in 0L..1500L) {
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

/** Overflow-safe sample bound for trim times supplied by clips or callers. */
internal fun sampleIndexAtTime(timeMs: Long, sampleRate: Int, sampleCount: Int): Int {
    require(sampleRate > 0 && sampleCount >= 0)
    if (timeMs <= 0L || sampleCount == 0) return 0
    val maxTimeMs = (sampleCount.toLong() * 1000L + sampleRate - 1) / sampleRate
    if (timeMs >= maxTimeMs) return sampleCount
    val wholeSeconds = timeMs / 1000L
    val fractionalSamples = (timeMs % 1000L) * sampleRate / 1000L
    return (wholeSeconds * sampleRate + fractionalSamples).toInt().coerceIn(0, sampleCount)
}

/** Keep decoded timestamp tokens monotonic and inside the actual (possibly short) audio chunk. */
internal fun constrainWhisperTimestamp(timestampMs: Long, previousStartMs: Long, chunkDurationMs: Long): Long {
    require(chunkDurationMs >= 0L)
    return timestampMs.coerceIn(previousStartMs.coerceIn(0L, chunkDurationMs), chunkDurationMs)
}

/** Serializes heavyweight native Whisper sessions across concurrent caption requests. */
internal object WhisperInferenceGate {
    private val mutex = kotlinx.coroutines.sync.Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T {
        mutex.lock() // lock() is cancellable, including while queued behind another inference.
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }
}

/** Verify bytes, not only a marker and file length, before reporting bundled offline readiness. */
internal fun isWhisperAssetVerified(
    file: File,
    expectedSize: Long,
    expectedSha256: String,
    checkCancelled: () -> Unit = {},
): Boolean {
    checkCancelled()
    if (!file.isFile || file.length() != expectedSize || !expectedSha256.matches(Regex("[0-9a-f]{64}"))) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            checkCancelled()
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    val actual = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    return actual == expectedSha256
}
