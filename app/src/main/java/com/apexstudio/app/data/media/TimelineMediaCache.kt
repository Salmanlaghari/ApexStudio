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
 *
 * Phase B additions:
 * - **LRU eviction** with a soft bitmap-memory budget (default
 *   64 MB). Without this, a project with 30+ clips kept every
 *   bitmap resident and OOM'd on long sessions.
 * - **Race protection** for zoom changes: an in-flight extraction
 *   that loses the race for a clip's *current* desired cache key
 *   is dropped instead of clobbering the newer one.
 * - The extractor now produces 1-fps strips (length-dependent,
 *   clamped 4-60 frames), so a 10-second clip gets 10 cells while
 *   a 5-minute clip tops out at 60.
 */
class TimelineMediaCache(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<Map<String, ClipMedia>>(emptyMap())
    val state: StateFlow<Map<String, ClipMedia>> = _state.asStateFlow()

    // Keyed by clip.id → the full cache key currently desired for
    // that clip. In-flight jobs use this map to detect "I lost the
    // race" and skip their write.
    private val currentKey = HashMap<String, String>()

    // Jobs in flight, keyed by full cache key. Multiple in-flight
    // jobs are fine (different clips / different keys) because
    // SupervisorJob + Dispatchers.IO gives us multi-clip parallelism
    // for free.
    private val inFlight = HashMap<String, Job>()

    /**
     * Ensure every clip in [clips] has a media entry. The current
     * map is returned synchronously from [state.value] so the
     * Composable can render the first frame immediately; extraction
     * jobs are launched in the background and the flow updates
     * as they finish.
     *
     * Also marks every requested clip as "recently wanted" so the
     * LRU evicts entries the timeline isn't looking at anymore.
     */
    fun observe(clips: List<MediaClip>, pxPerMs: Float): Map<String, ClipMedia> {
        val frameWidth = (pxPerMs * 1000f).toInt().coerceAtLeast(32) // ~1s slice
        for (clip in clips) {
            val key = cacheKey(clip, frameWidth)
            currentKey[clip.id] = key
            if (inFlight.containsKey(key)) continue
            val existing = _state.value[clip.id]
            if (existing != null && existing.cacheKey == key) continue
            inFlight[key] = scope.launch { extractFor(clip, frameWidth, key) }
        }
        // Touch the LRU for everything we just asked about so the
        // eviction policy knows which clips are still in use.
        touchAccessOrder(clips.map { it.id })
        evictIfOverBudget()
        return _state.value
    }

    private suspend fun extractFor(clip: MediaClip, frameWidth: Int, key: String) {
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
                        frameHeightPx = 64
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
            // Race protection: only publish if our key is still the
            // current desired key for this clip. A zoom change in the
            // middle of extraction would have bumped currentKey, so
            // our stale frames shouldn't overwrite the new extraction's.
            if (currentKey[clip.id] == key) {
                _state.value = _state.value + (clip.id to media)
            } else {
                // Drop the bitmaps we just decoded — they'll never be
                // shown and would otherwise sit on the LRU.
                media.frames.forEach { if (!it.isRecycled) it.recycle() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extract failed for ${clip.uri}", e)
        } finally {
            inFlight.remove(key)
        }
    }

    private fun cacheKey(clip: MediaClip, frameWidth: Int): String =
        "${clip.uri}|${clip.trimStartMs}|${clip.trimEndMs}|w=$frameWidth"

    /**
     * Tracks recently-touched clip IDs in insertion order. Used by
     * [evictIfOverBudget] to know which entries the timeline is
     * still asking about. We don't use a real LRU (LinkedHashMap
     * with access-order=true) because touchAccessOrder mutates on
     * every observe() call and we want to keep that cheap.
     *
     * Instead: insert new IDs at the tail; on eviction, walk from
     * the head (oldest first) and drop entries that aren't in
     * `currentKey` of any active request.
     */
    private val accessOrder = LinkedHashMap<String, Unit>()

    private fun touchAccessOrder(clipIds: List<String>) {
        // Move-to-end semantics: remove + re-insert each id.
        for (id in clipIds) {
            accessOrder.remove(id)
            accessOrder[id] = Unit
        }
    }

    /**
     * If the cached bitmaps exceed [MAX_BITMAP_BYTES], drop the
     * oldest entries (by access order) until we're under budget.
     * Audio waveform arrays count toward memory at a tiny rate so
     * we don't track them explicitly.
     */
    private fun evictIfOverBudget() {
        val snapshot = _state.value
        var total = snapshot.values.sumOf { media -> media.frames.sumOf { bitmapBytes(it) } }
        if (total <= MAX_BITMAP_BYTES) return
        // Walk oldest → newest, drop anything not currently pinned.
        val activeClipIds = currentKey.keys
        val toEvict = ArrayList<String>()
        for (clipId in accessOrder.keys) {
            if (total <= MAX_BITMAP_BYTES) break
            if (clipId in activeClipIds) continue
            val media = snapshot[clipId] ?: continue
            val bytes = media.frames.sumOf { bitmapBytes(it) }
            media.frames.forEach { if (!it.isRecycled) it.recycle() }
            total -= bytes
            toEvict.add(clipId)
        }
        if (toEvict.isNotEmpty()) {
            val newMap = _state.value.toMutableMap()
            for (id in toEvict) {
                newMap.remove(id)
                accessOrder.remove(id)
            }
            _state.value = newMap
        }
    }

    private fun bitmapBytes(b: Bitmap): Long {
        if (b.isRecycled) return 0L
        val bytesPerPixel = when (b.config) {
            Bitmap.Config.ALPHA_8 -> 1
            Bitmap.Config.RGB_565 -> 2
            Bitmap.Config.ARGB_4444 -> 2
            else -> 4 // ARGB_8888 + RGBA_F16 (approx)
        }
        return b.width.toLong() * b.height.toLong() * bytesPerPixel
    }

    fun release() {
        scope.cancel()
        // Recycle all cached bitmaps so we don't leak GL textures
        // if the user is on a low-memory device.
        _state.value.values.forEach { media ->
            media.frames.forEach { if (!it.isRecycled) it.recycle() }
        }
        _state.value = emptyMap()
        accessOrder.clear()
        currentKey.clear()
    }

    companion object {
        private const val TAG = "TimelineMediaCache"
        // 64 MB covers ~400 frames at 240x80 ARGB_8888 which is
        // enough for 5-10 typical clips even with a generous zoom.
        // Bump this if the timeline routinely shows >20 clips.
        private const val MAX_BITMAP_BYTES = 64L * 1024L * 1024L
    }
}
