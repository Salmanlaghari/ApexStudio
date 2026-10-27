package com.apexstudio.app.data.media

import com.apexstudio.app.domain.model.ClipTimeMapping
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
            // Filmstrip positions are output/project time. Convert them back to
            // source time before indexing and decoding; adding output ms directly
            // selected the wrong source frame for retimed clips.
            val sourceOffset = when {
                clip.type == ClipType.VIDEO && clip.trackIndex == 0 ->
                    ClipTimeMapping.mainSourceOffsetAtOutputMs(clip, relative)
                clip.type == ClipType.OVERLAY -> clip.overlaySourceOffsetMs(relative)
                clip.speedPoints.size >= 2 -> com.apexstudio.app.domain.model.SpeedCurveMath
                    .outputToSourceMs(relative, (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L), clip.speedPoints)
                else -> relative
            }
            val absolute = if (clip.trimStartMs > Long.MAX_VALUE - sourceOffset) {
                Long.MAX_VALUE
            } else {
                (clip.trimStartMs + sourceOffset).coerceAtLeast(0L)
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
