package com.apexstudio.app.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Answers "is this overlay URI a video?" — the one fact the preview and
 * the overlay-player binding need but [com.apexstudio.app.domain.model.MediaClip]
 * cannot carry: an overlay clip's [com.apexstudio.app.domain.model.ClipType]
 * is always OVERLAY, so the original video/image distinction is lost when
 * the picker result is re-tagged (see `onMediaPicked`).
 *
 * ROOT-CAUSE FIX (empty black PiP box): the preview used to branch on
 * `clip.type == ClipType.VIDEO` — never true for OVERLAY clips — so video
 * overlays fell through to Coil `AsyncImage`, which cannot decode video,
 * and rendered as an empty black box. The overlay-player binding had the
 * same bug (`it.type == ClipType.VIDEO` over an OVERLAY-only list), so the
 * player was never bound and video overlays never played.
 *
 * This probe asks the media itself (MediaMetadataRetriever HAS_VIDEO),
 * which matches the export's own probe in PipOverlayGlEffect, so preview
 * and export can never disagree about video-vs-image. Results are cached
 * per URI for the process lifetime; failures default to image (false)
 * so a broken URI degrades to the image path instead of crashing.
 */
object OverlayMediaProbe {

    private val cache = ConcurrentHashMap<String, Boolean>()

    suspend fun isVideo(context: Context, uri: String): Boolean {
        cache[uri]?.let { return it }
        val result = withContext(Dispatchers.IO) {
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, Uri.parse(uri))
                    retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO
                    ) == "yes"
                }
            } catch (_: Exception) {
                false
            }
        }
        cache[uri] = result
        return result
    }
}
