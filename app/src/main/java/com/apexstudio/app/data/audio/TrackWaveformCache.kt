package com.apexstudio.app.data.audio

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.apexstudio.app.data.media.MediaAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Per-audio-track waveform cache (Phase 4).
 *
 * The timeline's A1 lane used to paint every music/SFX track with the
 * *video clip's* waveform (or a synthetic fallback). This decodes each
 * track's own audio via [MediaAnalyzer.analyzeAudioWaveform] (real PCM
 * decode — no fake data) and caches the buckets in an LRU keyed by
 * uri + trim window, so scrubbing and re-layout stay cheap.
 */
object TrackWaveformCache {

    private const val TAG = "TrackWaveformCache"
    const val BUCKETS = 160

    private val cache = object : LruCache<String, FloatArray>(24) {
        override fun sizeOf(key: String, value: FloatArray): Int = value.size
    }

    private fun key(uri: String, trimStartMs: Long, trimEndMs: Long) =
        "$uri|$trimStartMs|$trimEndMs"

    fun peek(uri: String, trimStartMs: Long, trimEndMs: Long): FloatArray? =
        synchronized(cache) { cache.get(key(uri, trimStartMs, trimEndMs)) }

    /**
     * Returns cached buckets, decoding on a miss. Never throws — null on
     * any failure so the UI can fall back to the shared waveform.
     */
    suspend fun get(
        context: Context,
        uri: String,
        trimStartMs: Long = 0L,
        trimEndMs: Long = Long.MAX_VALUE
    ): FloatArray? = withContext(Dispatchers.IO) {
        val k = key(uri, trimStartMs, trimEndMs)
        synchronized(cache) { cache.get(k) }?.let { return@withContext it }
        try {
            val data = MediaAnalyzer().analyzeAudioWaveform(
                uri = uri,
                context = context.applicationContext,
                sampleCount = BUCKETS,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs
            )
            val buckets = data.samples
            if (buckets.isNotEmpty()) {
                synchronized(cache) { cache.put(k, buckets) }
                buckets
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "waveform decode failed for $uri", e)
            null
        }
    }

    /** Drops cached waveforms (e.g. after a track's trim changes). */
    fun invalidateAll() {
        synchronized(cache) { cache.evictAll() }
    }
}
