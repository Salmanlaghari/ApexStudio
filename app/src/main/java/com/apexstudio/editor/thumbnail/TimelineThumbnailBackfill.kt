package com.apexstudio.editor.thumbnail

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.PhotoEditSettings
import com.apexstudio.app.domain.model.SpeedPoint

/** Only source-changing edits invalidate a filmstrip, not selection or PiP gestures. */
internal data class TimelineThumbnailSourceKey(
    val id: String,
    val uri: String,
    val type: ClipType,
    val durationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val speed: Float,
    val speedPoints: List<SpeedPoint>,
    val photoEdit: PhotoEditSettings?,
) {
    companion object {
        fun from(clip: MediaClip) = TimelineThumbnailSourceKey(
            clip.id, clip.uri, clip.type, clip.durationMs, clip.trimStartMs,
            clip.trimEndMs, clip.speedMultiplier, clip.speedPoints,
            clip.photoEdit.takeIf { clip.type == ClipType.IMAGE || clip.type == ClipType.OVERLAY },
        )
    }
}

/**
 * Bounded full-clip thumbnail backfill planning for the editor's lazy filmstrip.
 *
 * A caller should advance its persistent cursor only through the keys returned
 * here. In particular, a zero-capacity pass returns no keys and therefore cannot
 * skip the unrequested tail of a clip.
 */
object TimelineThumbnailBackfill {
    fun batch(
        cursorMs: Long,
        durationMs: Long,
        intervalMs: Long,
        maxKeys: Int,
        visibleWorkPending: Boolean = false,
    ): LongArray {
        val duration = durationMs.coerceAtLeast(0L)
        val interval = intervalMs.coerceAtLeast(1L)
        val limit = maxKeys.coerceAtLeast(0)
        if (cursorMs < 0L || cursorMs > duration || limit == 0 || visibleWorkPending) return longArrayOf()

        val first = (cursorMs / interval) * interval
        val count = minOf(limit.toLong(), ((duration - first) / interval) + 1L).toInt()
        return LongArray(count) { index -> first + index * interval }
    }
}
