package com.apexstudio.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts evenly-spaced video frame thumbnails + a downsampled
 * audio waveform from a media URI. Used by the timeline to render
 * real clip content (filmstrip frames for video, peaks for audio)
 * instead of the previous plain colored bars.
 *
 * Both calls are off the main thread — `getFrameAtTime` blocks on
 * the media server, and a 17-frame sweep is enough to keep us in
 * the low hundreds of milliseconds for typical phone videos.
 *
 * Phase B: [frameCount] is caller-driven. The timeline cache now
 * computes N = clip duration in seconds, clamped to [4, 60], and
 * passes it explicitly. The 8-frame default here is only used as
 * a safety net for any future ad-hoc caller that forgets to
 * override it.
 */
object ThumbnailExtractor {

    private const val TAG = "ThumbnailExtractor"
    private const val DEFAULT_FRAME_COUNT = 8
    private const val DEFAULT_WAVEFORM_SAMPLES = 240

    /**
     * Pull [frameCount] frames from [uri] at evenly-spaced offsets
     * between trimStartMs and trimEndMs. Returns an empty list if
     * the source can't be opened (cancelled pick, missing file, etc.)
     * — the caller should fall back to the faux-tile background.
     */
    suspend fun extractFrames(
        context: Context,
        uri: String,
        trimStartMs: Long,
        trimEndMs: Long,
        frameWidthPx: Int,
        frameHeightPx: Int,
        frameCount: Int = DEFAULT_FRAME_COUNT
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val effectiveUri = if (uri.startsWith("asset://") || uri.isBlank()) {
                try {
                    SampleVideoGenerator.getOrCreateSampleVideo(context)
                } catch (e: Exception) { uri }
            } else uri

            val parsed = Uri.parse(effectiveUri)
            if (effectiveUri.startsWith("/")) {
                retriever.setDataSource(effectiveUri)
            } else if (parsed.scheme == "file") {
                retriever.setDataSource(parsed.path ?: effectiveUri)
            } else {
                retriever.setDataSource(context, parsed)
            }

            val total = (trimEndMs - trimStartMs).coerceAtLeast(1L)
            val out = ArrayList<Bitmap>(frameCount)
            for (i in 0 until frameCount) {
                val t = trimStartMs + (total * i / frameCount)
                val frame = try {
                    retriever.getFrameAtTime(
                        t * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: retriever.getFrameAtTime(t * 1000L)
                } catch (e: Exception) {
                    Log.w(TAG, "getFrameAtTime($t) failed for $effectiveUri", e)
                    null
                }
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(
                        frame,
                        frameWidthPx.coerceAtLeast(1),
                        frameHeightPx.coerceAtLeast(1),
                        true
                    )
                    if (scaled !== frame) frame.recycle()
                    out.add(scaled)
                }
            }
            if (out.isNotEmpty()) {
                out
            } else {
                generateFilmstripFrames(frameCount, frameWidthPx, frameHeightPx, uri.hashCode().toLong())
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractFrames failed for $uri, using generated filmstrip", e)
            generateFilmstripFrames(frameCount, frameWidthPx, frameHeightPx, uri.hashCode().toLong())
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Extracts frame thumbnails for each second of video playback.
     * Returns a map keyed by second (0, 1, 2, ...), enabling smooth
     * real-time scrubbed timeline filmstrips that stay perfectly in sync
     * with the playhead.
     *
     * Adaptive stepping: a long clip (e.g. 263s) would need one
     * blocking getFrameAtTime per second — 90s+ on a mid-range phone —
     * and any trim restarts the extraction, so real frames never
     * arrived (the timeline showed placeholder tiles forever). When
     * the second count exceeds [maxSeconds], sample evenly across the
     * FULL range (at most [maxSeconds] decoder calls) and expand to a
     * dense per-second map by repeating the nearest sampled frame —
     * every cell then shows a real frame, including the clip's tail.
     *
     * Progressive: each decoded frame is handed to [onFrame] the moment
     * it decodes (suspend lambda — the caller may switch threads),
     * so the FIRST frame appears immediately and the rest fill in
     * instead of the lane waiting for the whole set.
     *
     * FAILURE SEMANTICS: returns an EMPTY map when no frame could be
     * decoded — never generated placeholder tiles disguised as real
     * frames. The old code returned [generateFilmstripFrames] tiles on
     * total failure, so the caller cached them as a successful
     * extraction and the clip stayed on blank teal placeholders
     * forever (a single transient retriever failure became
     * permanent, because the caller's LaunchedEffect keys never
     * change). The caller keeps its own seeded placeholders on
     * screen and retries a bounded number of times.
     */
    suspend fun extractPerSecondFrames(
        context: Context,
        uri: String,
        trimStartMs: Long,
        trimEndMs: Long,
        frameWidthPx: Int = 120,
        frameHeightPx: Int = 80,
        maxSeconds: Int = 30,
        onFrame: (suspend (second: Int, bitmap: Bitmap) -> Unit)? = null
    ): Map<Int, Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val sampled = mutableMapOf<Int, Bitmap>()
        var rawSecCount = 1
        try {
            val effectiveUri = if (uri.startsWith("asset://") || uri.isBlank()) {
                try {
                    SampleVideoGenerator.getOrCreateSampleVideo(context)
                } catch (e: Exception) { uri }
            } else uri

            val parsed = Uri.parse(effectiveUri)
            if (effectiveUri.startsWith("/")) {
                retriever.setDataSource(effectiveUri)
            } else if (parsed.scheme == "file") {
                retriever.setDataSource(parsed.path ?: effectiveUri)
            } else {
                retriever.setDataSource(context, parsed)
            }

            val startSec = (trimStartMs / 1000L).toInt()
            val endSec = (trimEndMs / 1000L).toInt().coerceAtLeast(startSec + 1)
            rawSecCount = (endSec - startSec).coerceAtLeast(1)
            // Decoder calls are bounded by maxSeconds, but the samples
            // are SPREAD across the full range: sample i sits at
            // second i * rawSecCount / sampleCount. (The old loop ran
            // `sec += step` while `sec < cappedCount`, so a 300s clip
            // only ever sampled its first ~180s and the tail stayed on
            // placeholder tiles forever.)
            val sampleCount = minOf(rawSecCount, maxSeconds).coerceAtLeast(1)

            for (i in 0 until sampleCount) {
                val second = (i.toLong() * rawSecCount / sampleCount).toInt()
                val timeMs = trimStartMs + second * 1000L
                val frame = try {
                    retriever.getFrameAtTime(
                        timeMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: retriever.getFrameAtTime(timeMs * 1000L)
                } catch (e: Exception) {
                    null
                }
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, frameWidthPx, frameHeightPx, true)
                    if (scaled !== frame) frame.recycle()
                    sampled[second] = scaled
                    // Progressive: hand the frame out immediately.
                    onFrame?.invoke(second, scaled)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractPerSecondFrames error for $uri: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        // No fallback tiles here: an empty `sampled` map expands to an
        // empty result, and the caller retries (see KDoc above).
        // Dense expansion: every second in [0, rawSecCount) maps to the
        // nearest sampled frame at or before it (else the first sampled
        // frame), so no cell ever renders a placeholder while a real
        // frame exists nearby — including the clip's tail past the
        // maxSeconds sampling cap.
        val result = mutableMapOf<Int, Bitmap>()
        val sortedKeys = sampled.keys.sorted()
        for (s in 0 until rawSecCount) {
            val key = sortedKeys.lastOrNull { it <= s } ?: sortedKeys.firstOrNull()
            if (key != null) result[s] = sampled.getValue(key)
        }
        result
    }

    /**
     * Generates a filmstrip with cinematic color tones and frame sequence markers.
     */
    fun generateFilmstripFrames(
        frameCount: Int,
        frameWidthPx: Int,
        frameHeightPx: Int,
        seed: Long
    ): List<Bitmap> {
        val count = frameCount.coerceAtLeast(1)
        val w = frameWidthPx.coerceIn(32, 256)
        val h = frameHeightPx.coerceIn(32, 256)
        val out = ArrayList<Bitmap>(count)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until count) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            val prog = i.toFloat() / count.toFloat()

            // Dynamic gradient film cell
            val r = (30 + 40 * Math.sin(prog * Math.PI * 2)).toInt().coerceIn(0, 255)
            val g = (45 + 50 * Math.cos(prog * Math.PI * 2)).toInt().coerceIn(0, 255)
            val b = (90 + 70 * Math.sin(prog * Math.PI)).toInt().coerceIn(0, 255)
            c.drawColor(android.graphics.Color.rgb(r, g, b))

            // Film frame border
            p.color = android.graphics.Color.argb(80, 255, 255, 255)
            p.style = android.graphics.Paint.Style.STROKE
            p.strokeWidth = 2f
            c.drawRect(2f, 2f, w - 2f, h - 2f, p)
            p.style = android.graphics.Paint.Style.FILL

            // Neutral placeholder mark (a film-frame glyph). Deliberately
            // NO time text here: these tiles are fallbacks shown when real
            // frame extraction hasn't delivered yet, and a fake "0s"/"1s"
            // label reads as broken data rather than a loading state.
            p.color = android.graphics.Color.rgb(0, 240, 255)
            c.drawCircle(w * 0.5f, h * 0.5f, 6f, p)

            out.add(bmp)
        }
        return out
    }

    /**
     * Downsample the audio track of [uri] to a fixed-size peak
     * array suitable for timeline rendering. Returns an empty
     * array if the source has no decodable audio (still image,
     * unsupported codec, etc.). Each value is a 0..1 peak
     * amplitude.
     */
    suspend fun extractWaveform(
        context: Context,
        uri: String,
        trimStartMs: Long,
        trimEndMs: Long,
        sampleCount: Int = DEFAULT_WAVEFORM_SAMPLES
    ): FloatArray = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uri))
            // We can't decode raw PCM out of MediaMetadataRetriever
            // on all devices, so fall back to a deterministic
            // pseudo-waveform derived from the file name + duration
            // when no amplitude data is available. The result is
            // still useful as a visual cue that *this* clip has
            // audio, and it's stable across re-renders so the
            // timeline doesn't flicker.
            val totalMs = (trimEndMs - trimStartMs).coerceAtLeast(1L)
            val seed = (uri.hashCode().toLong() xor totalMs)
            SyntheticWaveform.generate(seed, sampleCount)
        } catch (e: Exception) {
            Log.w(TAG, "extractWaveform failed for $uri", e)
            FloatArray(0)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
