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

    /**
     * Ensure every clip in [clips] has a media entry. The current
     * map is returned synchronously from [state.value] so the
     * Composable can render the first frame immediately; extraction
     * jobs are launched in the background and the flow updates
     * as they finish.
     */
    fun observe(clips: List<MediaClip>, pxPerMs: Float): Map<String, ClipMedia> {
        val frameWidth = (pxPerMs * 1000f).toInt().coerceAtLeast(32) // ~1s slice
        for (clip in clips) {
            val key = cacheKey(clip, frameWidth)
            if (inFlight.containsKey(key)) continue
            val existing = _state.value[clip.id]
            if (existing != null && existing.cacheKey == key) continue
            inFlight[key] = scope.launch { extractFor(clip, frameWidth, key) }
        }
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
            _state.value = _state.value + (clip.id to media)
        } catch (e: Exception) {
            Log.w(TAG, "extract failed for ${clip.uri}", e)
        } finally {
            inFlight.remove(key)
        }
    }

    private fun cacheKey(clip: MediaClip, frameWidth: Int): String =
        "${clip.uri}|${clip.trimStartMs}|${clip.trimEndMs}|w=$frameWidth"

    fun release() {
        scope.cancel()
    }

    companion object {
        private const val TAG = "TimelineMediaCache"
    }
}
