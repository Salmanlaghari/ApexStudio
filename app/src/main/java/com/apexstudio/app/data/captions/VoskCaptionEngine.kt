package com.apexstudio.app.data.captions

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

/**
 * Offline auto-caption engine powered by Vosk (https://github.com/alphacep/vosk-api,
 * Apache-2.0). 100% on-device: no audio ever leaves the phone.
 *
 * The speech model (~40MB) is NOT bundled in the APK — it downloads once on
 * first use into the app's files dir, with progress callbacks.
 *
 * Pipeline: media URI -> MediaCodec PCM decode -> resample to 16kHz mono ->
 * Vosk recognizer (word timestamps) -> [CaptionSegment] list.
 */
object VoskCaptionEngine {

    private const val TAG = "VoskCaptionEngine"
    private const val MODEL_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    private const val MODEL_DIR_NAME = "vosk-model-small-en-us-0.15"
    private const val TARGET_SAMPLE_RATE = 16000

    data class CaptionWord(
        val word: String,
        val startMs: Long,
        val endMs: Long,
        val confidence: Float
    )

    data class CaptionSegment(
        val text: String,
        val startMs: Long,
        val endMs: Long
    )

    sealed interface ModelState {
        data object NotDownloaded : ModelState
        data class Downloading(val progress01: Float) : ModelState
        data object Ready : ModelState
        data class Failed(val reason: String) : ModelState
    }

    /** Returns the model dir if fully downloaded, null otherwise. */
    fun modelDir(context: Context): File? {
        val dir = File(context.filesDir, MODEL_DIR_NAME)
        // A complete model has these marker files.
        return if (File(dir, "am/final.mdl").exists() || File(dir, "conf/model.conf").exists()) dir
        else null
    }

    /**
     * Downloads + unzips the Vosk model on first use. Calls [onProgress]
     * with 0..1. Returns the model dir, or null on failure.
     */
    suspend fun ensureModel(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        modelDir(context)?.let { onProgress(1f); return@withContext it }
        val tmpZip = File(context.cacheDir, "vosk-model.zip")
        try {
            Log.i(TAG, "Downloading Vosk model…")
            val url = URL(MODEL_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            conn.connect()
            val total = conn.contentLengthLong.coerceAtLeast(1L)
            var downloaded = 0L
            conn.inputStream.use { input ->
                FileOutputStream(tmpZip).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        onProgress((downloaded.toFloat() / total).coerceIn(0f, 0.99f))
                    }
                }
            }
            Log.i(TAG, "Unzipping Vosk model…")
            unzip(tmpZip, context.filesDir)
            onProgress(1f)
            modelDir(context)
        } catch (e: Exception) {
            Log.e(TAG, "Vosk model download failed", e)
            null
        } finally {
            tmpZip.delete()
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry = zis.nextEntry
            val buf = ByteArray(64 * 1024)
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                // Zip-slip guard.
                require(outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    "Bad zip entry: ${entry.name}"
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out ->
                        while (true) {
                            val n = zis.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Transcribes [uri] into word-timestamped captions. Heavy: runs on IO.
     * [onProgress] receives 0..1 as decode+recognition proceed.
     */
    suspend fun transcribe(
        context: Context,
        uri: String,
        onProgress: (Float) -> Unit = {}
    ): List<CaptionWord> = withContext(Dispatchers.IO) {
        val modelPath = modelDir(context)?.absolutePath
            ?: throw IllegalStateException("Vosk model not downloaded")
        // Decode to 16kHz mono PCM first (Vosk requirement).
        onProgress(0.05f)
        val pcm = decodeTo16kMono(context, uri) { frac ->
            onProgress(0.05f + 0.45f * frac)
        } ?: throw IllegalStateException("Could not decode audio")
        if (pcm.isEmpty()) return@withContext emptyList<CaptionWord>()

        val words = mutableListOf<CaptionWord>()
        try {
            Model(modelPath).use { model ->
                Recognizer(model, TARGET_SAMPLE_RATE.toFloat()).use { rec ->
                    rec.setWords(true)
                    // Partial results disabled for speed; we only need finals.
                    val chunk = 4096 * 2 // bytes (16-bit samples)
                    var offset = 0
                    while (offset < pcm.size) {
                        val len = minOf(chunk, pcm.size - offset)
                        val end = offset + len >= pcm.size
                        if (end) {
                            rec.acceptWaveForm(pcm, len)
                            words += parseWords(rec.finalResult)
                        } else {
                            if (rec.acceptWaveForm(pcm.copyOfRange(offset, offset + len), len)) {
                                words += parseWords(rec.result)
                            }
                        }
                        offset += len
                        if (offset % (chunk * 64) == 0) {
                            onProgress(0.5f + 0.5f * (offset.toFloat() / pcm.size))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vosk transcription failed", e)
            throw e
        }
        onProgress(1f)
        words
    }

    /**
     * Groups words into readable caption segments (CapCut-style):
     * max 10 words or ~4.5s per segment, split on sentence punctuation.
     */
    fun toSegments(words: List<CaptionWord>): List<CaptionSegment> {
        if (words.isEmpty()) return emptyList()
        val segments = mutableListOf<CaptionSegment>()
        val current = mutableListOf<CaptionWord>()
        fun flush() {
            if (current.isNotEmpty()) {
                segments += CaptionSegment(
                    text = current.joinToString(" ") { it.word },
                    startMs = current.first().startMs,
                    endMs = current.last().endMs
                )
                current.clear()
            }
        }
        for (w in words) {
            // Skip empty/hallucinated tokens.
            if (w.word.isBlank()) continue
            current += w
            val spanMs = w.endMs - current.first().startMs
            val sentenceEnd = w.word.lastOrNull()?.let { it in ".!?…" } == true
            if (current.size >= 10 || spanMs > 4500 || (sentenceEnd && current.size >= 4)) {
                flush()
            }
        }
        flush()
        return segments
    }

    // ------------------------------------------------------------------
    // PCM decode + resample (16kHz mono, 16-bit LE)
    // ------------------------------------------------------------------

    private fun decodeTo16kMono(
        context: Context,
        uri: String,
        onProgress: (Float) -> Unit
    ): ByteArray? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            val parsed = android.net.Uri.parse(uri)
            val pfd = context.contentResolver.openFileDescriptor(parsed, "r")
                ?: return null
            pfd.use {
                extractor.setDataSource(it.fileDescriptor)
            }
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                val mime = extractor.getTrackFormat(i)
                    .getString(MediaFormat.KEY_MIME) ?: ""
                mime.startsWith("audio/")
            } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
                format.getLong(MediaFormat.KEY_DURATION) else 0L

            decoder = MediaCodec.createDecoderByType(mime)
            decoder!!.configure(format, null, null, 0)
            decoder!!.start()

            // Collect raw PCM (native rate, interleaved) first.
            val rawChunks = mutableListOf<ByteArray>()
            var totalBytes = 0
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var idle = 0
            var guard = 0
            while (!outputEnded && guard++ < 500_000) {
                var progressed = false
                if (!inputEnded) {
                    val inIdx = decoder!!.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        if (extractor.sampleSize <= 0) {
                            decoder!!.queueInputBuffer(
                                inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        } else {
                            decoder!!.getInputBuffer(inIdx)?.let { inBuf ->
                                inBuf.clear()
                                val read = extractor.readSampleData(inBuf, 0)
                                    .coerceAtLeast(0)
                                decoder!!.queueInputBuffer(
                                    inIdx, 0, read,
                                    extractor.sampleTime.coerceAtLeast(0L), 0
                                )
                                extractor.advance()
                            }
                        }
                        progressed = true
                    }
                }
                val outIdx = decoder!!.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        progressed = true
                        idle = 0
                        val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val cfg = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!cfg && info.size > 0) {
                            decoder!!.getOutputBuffer(outIdx)?.let { outBuf ->
                                val bytes = ByteArray(info.size)
                                outBuf.position(info.offset)
                                outBuf.get(bytes)
                                rawChunks += bytes
                                totalBytes += bytes.size
                            }
                        }
                        decoder!!.releaseOutputBuffer(outIdx, false)
                        if (eos) outputEnded = true
                    }
                    else -> {
                        if (progressed) idle = 0 else idle++
                        if (idle > 400) break
                    }
                }
                if (guard % 2000 == 0 && durationUs > 0) {
                    onProgress(0.5f) // coarse; refined below
                }
            }
            if (rawChunks.isEmpty()) return null
            val raw = ByteArray(totalBytes)
            var pos = 0
            for (c in rawChunks) {
                c.copyInto(raw, pos)
                pos += c.size
            }
            return resampleTo16kMono(raw, sampleRate, channelCount)
        } catch (e: Exception) {
            Log.e(TAG, "PCM decode failed", e)
            return null
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /** 16-bit LE interleaved -> 16kHz mono 16-bit LE. Linear interpolation. */
    private fun resampleTo16kMono(
        raw: ByteArray,
        srcRate: Int,
        channels: Int
    ): ByteArray {
        if (srcRate <= 0 || channels <= 0 || raw.size < 2) return ByteArray(0)
        val srcFrames = raw.size / (2 * channels)
        if (srcFrames <= 0) return ByteArray(0)
        // Mono mix first (float).
        val mono = FloatArray(srcFrames)
        var bi = 0
        for (f in 0 until srcFrames) {
            var sum = 0f
            for (c in 0 until channels) {
                val lo = raw[bi].toInt() and 0xFF
                val hi = raw[bi + 1].toInt()
                sum += ((lo or (hi shl 8)).toShort()).toFloat() / 32768f
                bi += 2
            }
            mono[f] = sum / channels
        }
        if (srcRate == TARGET_SAMPLE_RATE) {
            return floatsToBytes16(mono)
        }
        val ratio = srcRate.toDouble() / TARGET_SAMPLE_RATE
        val dstFrames = (srcFrames / ratio).roundToInt().coerceAtLeast(1)
        val out = FloatArray(dstFrames)
        for (i in 0 until dstFrames) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val i1 = (i0 + 1).coerceIn(0, srcFrames - 1)
            val frac = (srcPos - i0).toFloat()
            out[i] = mono[i0] * (1f - frac) + mono[i1] * frac
        }
        return floatsToBytes16(out)
    }

    private fun floatsToBytes16(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            out[i * 2] = (v.toInt() and 0xFF).toByte()
            out[i * 2 + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    // ------------------------------------------------------------------
    // Vosk JSON parsing (no extra deps — tiny hand parser for the known shape)
    // ------------------------------------------------------------------

    private fun parseWords(json: String): List<CaptionWord> {
        val words = mutableListOf<CaptionWord>()
        // Find "result": [ ... ] array; parse each {"word":..,"start":..,"end":..,"conf":..}
        val resultIdx = json.indexOf("\"result\"")
        if (resultIdx < 0) return words
        val arrStart = json.indexOf('[', resultIdx)
        val arrEnd = json.indexOf(']', arrStart)
        if (arrStart < 0 || arrEnd < 0) return words
        val arr = json.substring(arrStart + 1, arrEnd)
        // Split top-level objects.
        var depth = 0
        var start = -1
        for (i in arr.indices) {
            when (arr[i]) {
                '{' -> { if (depth == 0) start = i; depth++ }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        parseWordObj(arr.substring(start, i + 1))?.let { words += it }
                        start = -1
                    }
                }
            }
        }
        return words
    }

    private fun parseWordObj(obj: String): CaptionWord? {
        fun str(key: String): String? {
            val k = "\"$key\""
            val i = obj.indexOf(k)
            if (i < 0) return null
            val c = obj.indexOf(':', i + k.length)
            if (c < 0) return null
            var s = c + 1
            while (s < obj.length && obj[s].isWhitespace()) s++
            return if (obj[s] == '"') {
                val e = obj.indexOf('"', s + 1)
                if (e < 0) null else obj.substring(s + 1, e)
            } else {
                var e = s
                while (e < obj.length && (obj[e].isDigit() || obj[e] == '.' || obj[e] == '-' || obj[e] == 'e' || obj[e] == 'E' || obj[e] == '+')) e++
                obj.substring(s, e)
            }
        }
        val word = str("word") ?: return null
        val start = str("start")?.toDoubleOrNull() ?: return null
        val end = str("end")?.toDoubleOrNull() ?: return null
        val conf = str("conf")?.toFloatOrNull() ?: 0f
        return CaptionWord(word, (start * 1000).toLong(), (end * 1000).toLong(), conf)
    }
}
