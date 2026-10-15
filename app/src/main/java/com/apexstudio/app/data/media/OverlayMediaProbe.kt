package com.apexstudio.app.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.CancellationException
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
 * and export can never disagree about video-vs-image.
 *
 * FAILURE SEMANTICS (black-box fix): a failed probe must NEVER be
 * cached as "image". The old code cached every result — including
 * `false` from a retriever exception — so a video overlay whose first
 * probe failed transiently (e.g. the URI was not yet readable) was
 * treated as an image for the rest of the process: Coil tried to
 * decode video bytes as an image, failed silently, and the preview
 * showed an empty black box. Now: a retriever failure falls back to
 * the ContentResolver MIME type (a video MIME type → video, an image
 * MIME type → image); only a successful probe (or a MIME-backed answer) is
 * cached, and an unanswerable probe returns `false` WITHOUT caching
 * so a later retry can still succeed. (Note: this KDoc deliberately
 * avoids writing a MIME pattern like video-slash-star inline — the
 * slash-star sequence would open a nested block comment.)
 */
object OverlayMediaProbe {

    private val cache = ConcurrentHashMap<String, Boolean>()

    /** Extensions that unambiguously identify a still-image file. */
    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif"
    )

    /**
     * Synchronous still-image check for a media URI. CONSERVATIVE:
     * returns true ONLY on positive image evidence — an image MIME
     * type from the ContentResolver, or a known image extension on a
     * URI whose last path segment is a real filename (`file://`,
     * plain paths). A `content://` URI is NEVER judged by an
     * "extension" scraped from its string: content URIs carry no
     * filename, so a dot in the authority, path or query (e.g. a
     * provider ID or a `?…jpg` query) could misclassify a video as
     * an image and route it to PhotoClipPreview. For `content://`
     * URIs the MIME type is the only signal — unknown MIME means
     * unknown, NOT image; the async [isVideo] probe (which the
     * callers await / prefer) resolves those. Used as a fast path
     * where the probe result is not yet available (player binding
     * in EditorScreen, photoClip selection in
     * EditorScreenOverlays): an image-backed OVERLAY clip must take
     * the PhotoClipPreview path, never the ExoPlayer path — feeding
     * a JPEG to ExoPlayer renders a black preview.
     */
    fun isImageUri(context: Context, uri: String): Boolean {
        val parsed = try {
            Uri.parse(uri)
        } catch (_: Exception) {
            null
        }
        // 1. MIME type is authoritative wherever the resolver can
        // answer it: an image MIME is positive image evidence, a
        // video MIME must never be overridden by a stray extension.
        if (parsed != null) {
            val mime = try {
                context.contentResolver.getType(parsed) ?: ""
            } catch (_: Exception) {
                ""
            }
            if (mime.startsWith("image/")) return true
            if (mime.startsWith("video/")) return false
            // For content:// URIs the MIME type is the ONLY reliable
            // signal — their paths carry no real filename. Unknown
            // MIME here means unknown, not image.
            if (parsed.scheme == "content") return false
        }
        // 2. Non-content URIs (file://, plain paths): the last path
        // segment is a real filename, so a known image extension is
        // positive image evidence. (Stripped of query/fragment by
        // using lastPathSegment, never the raw URI string.)
        val filename = parsed?.lastPathSegment ?: uri.substringAfterLast('/')
        val ext = filename.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    suspend fun isVideo(context: Context, uri: String): Boolean {
        cache[uri]?.let { return it }
        val result = withContext(Dispatchers.IO) {
            // 1. Ask the media itself.
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, Uri.parse(uri))
                    val hasVideo = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO
                    )
                    if (hasVideo != null) {
                        val answer = hasVideo == "yes"
                        cache[uri] = answer
                        return@withContext answer
                    }
                    // Retriever opened the file but reports no
                    // HAS_VIDEO — fall through to the MIME check.
                }
            } catch (e: CancellationException) {
                // Callers await this probe inside LaunchedEffect: a
                // cancelled probe must propagate cancellation, never
                // fall through and bind a stale clip.
                throw e
            } catch (_: Exception) {
                // Retriever could not open the URI — MIME fallback below.
            }
            // 2. MIME fallback: cheap, needs no codec. A video MIME type
            // whose retriever probe failed (permissions, odd container)
            // must still take the video-player path, not Coil.
            try {
                val mime = context.contentResolver.getType(Uri.parse(uri)) ?: ""
                when {
                    mime.startsWith("video/") -> {
                        cache[uri] = true
                        true
                    }
                    mime.startsWith("image/") -> {
                        cache[uri] = false
                        false
                    }
                    else -> false // Unanswerable: do NOT cache.
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false // Unanswerable: do NOT cache.
            }
        }
        return result
    }
}
