package com.apexstudio.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.apexstudio.app.domain.model.MediaClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedHashMap

/**
 * One clip's timeline media: a downsampled filmstrip (video
 * overlays) and a peak waveform (audio / SFX tracks). Either can
 * be empty if extraction hasn't finished yet, the source isn't
 * decodable, or the asset is a still image with no audio.
 *
 * [cacheKey] is an internal versioning marker the timeline cache
 * uses to know whether a stored entry was produced for the same
 * (uri, width) as the one the timeline is currently asking for.
 * It isn't surfaced to the Composable.
 */
data class ClipMedia(
    val frames: List<Bitmap> = emptyList(),
    val waveform: FloatArray = FloatArray(0),
    val cacheKey: String = ""
) {
    // Override equals/hashCode so the timeline cache can compare
    // entries reliably. Without this the data class would only
    // use the reference identity for hashCode.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClipMedia) return false
        if (cacheKey != other.cacheKey) return false
        if (frames.size != other.frames.size) return false
        if (waveform.size != other.waveform.size) return false
        return true
    }
    override fun hashCode(): Int = cacheKey.hashCode()
}

/**
 * Per-clip thumbnail + waveform cache used by the editor timeline.
 *
 * The timeline calls [observe] from a Composable; the cache kicks
 * off extraction jobs in a background coroutine scope (one per
 * EditorScreen lifetime) and publishes results through a
 * [StateFlow]. The Composable reads the flow with
 * `collectAsStateWithLifecycle`, so frames appear progressively
 * without blocking the timeline render.
 *
 * The cache is content-keyed by (uri, trimStart, trimEnd, frame
 * width). Zoom-level changes that affect the frame width
 * invalidate the entry so the timeline re-extracts at the new
 * resolution.
 */
class TimelineMediaCache(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<Map<String, ClipMedia>>(emptyMap())
    val state: StateFlow<Map<String, ClipMedia>> = _state.asStateFlow()

    private val inFlight = HashMap<String, Job>()

    // Phase B: LRU byte budget. accessOrder=true so reads via
    // state.value / observe also bump recency. Forgotten (no longer
    // pinned) clips drop out first; pinned ones are protected.
    private val maxBytes = 64L * 1024L * 1024L
    private val byteBudget = LinkedHashMap<String, Long>(16, 0.75f, true)
    private var currentBytes = 0L

    // Phase B: race protection for zoom / trim changes. Every
    // observe() call records the "desired" cache key for each
    // pinned clip. Extraction publishes via _state.update only
    // when its computed key still matches the desired key — so a
    // slow extraction started before a zoom change can't clobber
    // a fresh extraction that started after.
    private val desiredKeyByClipId = HashMap<String, String>()

    /**
     * Ensure every clip in [clips] has a media entry. The current
     * map is returned synchronously from [state.value] so the
     * Composable can render the first frame immediately; extraction
     * jobs are launched in the background and the flow updates
     * as they finish.
     */
    fun observe(clips: List<MediaClip>, pxPerMs: Float): Map<String, ClipMedia> {
        val frameWidth = (pxPerMs * 1000f).toInt().coerceAtLeast(32) // ~1s slice
        // Build the pinned set so we never evict a clip the timeline
        // is currently showing, even if its byte budget is large.
        val pinned = clips.mapTo(HashSet()) { it.id }
        for (clip in clips) {
            val frameCount = frameCountForClip(clip)
            val key = cacheKey(clip, frameWidth, frameCount)
            // Record the latest desired key so in-flight extractions
            // for an older (uri, trim, width, count) tuple know to
            // drop their result on the floor if it would clobber a
            // newer request.
            desiredKeyByClipId[clip.id] = key
            if (inFlight.containsKey(key)) continue
            val existing = _state.value[clip.id]
            if (existing != null && existing.cacheKey == key) continue
            inFlight[key] = scope.launch {
                extractFor(clip, frameWidth, frameCount, key)
                // After extraction finishes the entry is published;
                // enforce the LRU ceiling. Forgotten clips (those no
                // longer in [pinned]) are evicted first.
                evictIfNeeded(pinned)
            }
        }
        return _state.value
    }

    /**
     * Compute the per-second frame count for the filmstrip.
     * N = clip duration in seconds, clamped to [4, 60] so very short
     * clips are still recognisable and very long clips don't run
     * the extractor into the seconds-per-frame range.
     */
    private fun frameCountForClip(clip: MediaClip): Int {
        val durMs = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
        val secs = (durMs / 1000L).toInt().coerceAtLeast(0)
        return secs.coerceIn(4, 60)
    }

    private suspend fun extractFor(
        clip: MediaClip,
        frameWidth: Int,
        frameCount: Int,
        key: String
    ) {
        try {
            val media = when (clip.type) {
                com.apexstudio.app.domain.model.ClipType.VIDEO,
                com.apexstudio.app.domain.model.ClipType.OVERLAY -> {
                    val frames = ThumbnailExtractor.extractFrames(
                        context = context,
                        uri = clip.uri,
                        trimStartMs = clip.trimStartMs,
                        trimEndMs = clip.trimEndMs,
                        frameWidthPx = frameWidth,
                        frameHeightPx = 64,
                        frameCount = frameCount
                    )
                    ClipMedia(frames = frames, cacheKey = key)
                }
                com.apexstudio.app.domain.model.ClipType.AUDIO,
                com.apexstudio.app.domain.model.ClipType.SFX -> {
                    val samples = ThumbnailExtractor.extractWaveform(
                        context = context,
                        uri = clip.uri,
                        trimStartMs = clip.trimStartMs,
                        trimEndMs = clip.trimEndMs
                    )
                    ClipMedia(waveform = samples, cacheKey = key)
                }
            }
            _state.value = _state.value + (clip.id to media)
            // Phase B: race protection. If the desired key for this
            // clip moved on while we were extracting (zoom / trim
            // change), skip publishing — a newer extraction is
            // already underway and will land its own _state.update.
            // Also skip the LRU byte-tracking for stale results so
            // they don't count against the 64MB ceiling.
            val desired = desiredKeyByClipId[clip.id]
            if (desired != null && desired != key) {
                Log.d(TAG, "drop stale extraction for ${clip.id}: wanted $desired, got $key")
                return
            }
            // Track byte cost for the LRU ceiling.
            byteBudget[clip.id] = bytesOf(media)
            currentBytes = byteBudget.values.sum()
        } catch (e: Exception) {
            Log.w(TAG, "extract failed for ${clip.uri}", e)
        } finally {
            inFlight.remove(key)
        }
    }

    /**
     * Drop oldest entries until we're back under [maxBytes]. Pinned
     * clips (those currently in the timeline) are protected — only
     * forgotten ones get evicted, in LRU order.
     */
    private fun evictIfNeeded(pinned: Set<String>) {
        while (currentBytes > maxBytes && byteBudget.isNotEmpty()) {
            // LinkedHashMap with accessOrder=true: iterate() yields
            // least-recently-used first.
            val victim = byteBudget.keys.firstOrNull { it !in pinned }
                ?: break // everything pinned → bail out
            val cost = byteBudget.remove(victim) ?: 0L
            currentBytes -= cost
            _state.value = _state.value - victim
            Log.d(TAG, "evicted $victim (-${cost / 1024}KB) → ${currentBytes / 1024}KB used")
        }
    }

    private fun bytesOf(media: ClipMedia): Long {
        var sum = 0L
        for (bmp in media.frames) sum += bmp.byteCount.toLong()
        // Audio waveform is a FloatArray(240) = 960 bytes — negligible.
        return sum
    }

    private fun cacheKey(clip: MediaClip, frameWidth: Int, frameCount: Int): String =
        "${clip.uri}|${clip.trimStartMs}|${clip.trimEndMs}|w=$frameWidth|n=$frameCount"

    fun release() {
        scope.cancel()
    }

    companion object {
        private const val TAG = "TimelineMediaCache"
    }
}
