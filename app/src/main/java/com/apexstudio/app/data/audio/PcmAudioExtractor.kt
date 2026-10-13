package com.apexstudio.app.data.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Extracts a video's audio track as 16 kHz mono float PCM in [-1, 1] —
 * the format on-device speech models (Whisper, …) expect.
 *
 * Extracted from the old Vosk caption engine (since replaced by Whisper).
 * Pipeline: MediaExtractor -> first audio track -> raw PCM frames ->
 * downmix to mono -> linear resample to 16 kHz.
 *
 * All work is synchronous; callers run it on Dispatchers.IO.
 */
object PcmAudioExtractor {

    private const val TAG = "PcmAudioExtractor"
    const val TARGET_RATE = 16000

    /**
     * @return mono float samples at 16 kHz, or null when the clip has no
     *   decodable audio (caller must surface that — never silently skip).
     */
    fun extractMono16k(context: Context, uri: Uri): FloatArray? {
        val (pcm16, srcRate, channels) = readTrackPcm(context, uri) ?: return null
        if (pcm16.isEmpty()) return null
        // Downmix to mono.
        val mono = if (channels > 1) {
            FloatArray(pcm16.size / channels) { i ->
                var acc = 0f
                for (c in 0 until channels) acc += pcm16[i * channels + c]
                acc / channels
            }
        } else pcm16
        // Resample to 16 kHz if needed (linear interpolation).
        if (srcRate == TARGET_RATE) return mono
        val ratio = TARGET_RATE.toDouble() / srcRate
        val outLen = (mono.size * ratio).roundToInt().coerceAtLeast(1)
        return FloatArray(outLen) { i ->
            val pos = i / ratio
            val i0 = pos.toInt().coerceIn(0, mono.size - 1)
            val i1 = (i0 + 1).coerceIn(0, mono.size - 1)
            val frac = (pos - i0).toFloat()
            mono[i0] * (1 - frac) + mono[i1] * frac
        }
    }

    private data class RawPcm(val samples: FloatArray, val rate: Int, val channels: Int)

    private fun readTrackPcm(context: Context, uri: Uri): RawPcm? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            Log.w(TAG, "setDataSource failed: $uri", e)
            extractor.release()
            return null
        }
        val trackIdx = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
        if (trackIdx == null) {
            Log.w(TAG, "no audio track in $uri")
            extractor.release()
            return null
        }
        val format = extractor.getTrackFormat(trackIdx)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        extractor.selectTrack(trackIdx)

        val out = ArrayList<Float>(1 shl 20)
        val buf = ByteBuffer.allocate(1 shl 16).order(ByteOrder.nativeOrder())
        try {
            while (true) {
                buf.clear()
                val n = extractor.readSampleData(buf, 0)
                if (n <= 0) break
                buf.flip()
                val shorts = buf.asShortBuffer()
                val arr = ShortArray(n / 2)
                shorts.get(arr)
                for (s in arr) out.add(s / 32768f)
                extractor.advance()
            }
        } catch (e: Exception) {
            Log.w(TAG, "audio read failed", e)
            extractor.release()
            return null
        } finally {
            extractor.release()
        }
        return RawPcm(out.toFloatArray(), sampleRate, channels)
    }
}
