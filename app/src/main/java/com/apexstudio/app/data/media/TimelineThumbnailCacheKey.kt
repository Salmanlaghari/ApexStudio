package com.apexstudio.app.data.media

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.PhotoEditSettings

/**
 * Stable identity for a decoded timeline frame. Photo edits change still-image pixels even
 * when the source URI and timestamp do not, so IMAGE and image-backed OVERLAY keys include
 * the persisted photo-edit settings.
 */
internal data class TimelineThumbnailCacheKey(
    val uri: String,
    val type: ClipType,
    val sourceTimeMs: Long,
    val width: Int,
    val height: Int,
    val photoEdit: PhotoEditSettings?,
) {
    companion object {
        fun from(clip: MediaClip, clipRelativeTimeMs: Long, width: Int, height: Int): TimelineThumbnailCacheKey {
            val relative = clipRelativeTimeMs.coerceAtLeast(0L)
            val absolute = if (clip.trimStartMs > Long.MAX_VALUE - relative) {
                Long.MAX_VALUE
            } else {
                (clip.trimStartMs + relative).coerceAtLeast(0L)
            }
            return TimelineThumbnailCacheKey(
                uri = clip.uri,
                type = clip.type,
                sourceTimeMs = absolute,
                width = width.coerceAtLeast(1),
                height = height.coerceAtLeast(1),
                // OVERLAY also represents still photos; their edits change
                // the decoded pixels just like edits on ordinary IMAGE clips.
                photoEdit = clip.photoEdit.takeIf {
                    clip.type == ClipType.IMAGE || clip.type == ClipType.OVERLAY
                },
            )
        }
    }
}
