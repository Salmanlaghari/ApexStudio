package com.apexstudio.app.domain.model

import kotlin.math.abs

/**
 * Edit surface for an OVERLAY (PiP) clip — Bug 3.
 *
 * The overlay already lives on [MediaClip] (same object the rest of the
 * editor uses), but it was edited through two different windows: the
 * preview gestures wrote normalised transform numbers directly, while
 * the properties sheet talked about blend/mask/volume. There was no
 * single value type answering "what can you change about this
 * overlay?", and no trim model at all — trimming an overlay went through
 * the video-only trim sheet, which shows [MediaClip.durationMs] rather
 * than the overlay's own source window.
 *
 * [OverlayClip] is that value type: a LOSSLESS view. [from] reads, [to]
 * writes back only the fields it owns and leaves everything else (mask,
 * keyframes, blend, photo edits, sticker layers...) untouched, so it can
 * be used as a round-trip without side effects — [OverlayClipTest] pins
 * exactly that.
 *
 * Geometry contract (identical to preview + export, and the same logos
 * the gestures already use):
 * - [x] / [y] are the normalised centre of the overlay (0..1, left/top origin);
 * - [scale] is width as a FRACTION OF THE FRAME (0.05..0.9);
 * - [rotationDeg] clockwise, -180..180;
 * - [opacity] 0..1.
 * and the trim is a source window: [startMs]/[endMs] are cut points
 * INSIDE the overlay's own media, never negative, never inverted.
 *
 * [layer] is the multi-layer track number (1..9 for overlays; 0 is the
 * main video lane). [separated] reports whether this overlay has ever
 * been given a real lane of its own — the task asks for overlays to
 * show as a separate track, and this makes that a testable property of
 * the model rather than a rendering coincidence.
 */
data class OverlayClip(
    val id: String,
    val uri: String,
    val sourceDurationMs: Long = 0L,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val x: Float = 0.85f,
    val y: Float = 0.85f,
    val scale: Float = 0.25f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val layer: Int = 1
) {
    /** Playable length after trimming, in milliseconds. */
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    /** True once the overlay sits on a lane of its own (not lane 0). */
    val isOnOwnTrack: Boolean get() = layer >= 1 && layer <= MAX_LAYER

    companion object {
        /** Highest legal overlay lane — V1 (main) occupies lane 0. */
        const val MAX_LAYER = 9

        /** Scale slider bounds, matching the preview pinch range. */
        const val MIN_SCALE = 0.05f
        const val MAX_SCALE = 0.9f

        /**
         * Project an [overlay]-typed clip into [OverlayClip], clamping
         * stale values from older projects rather than failing to load:
         * a project written with a scale of 5f (a pre-normalisation
         * bug) still opens, it just opens at MAX_SCALE.
         */
        fun from(clip: MediaClip): OverlayClip {
            val source = clip.durationMs.coerceAtLeast(0L)
            val start = clip.trimStartMs.coerceIn(0L, source)
            val end = clip.trimEndMs.coerceIn(start, source)
            return OverlayClip(
                id = clip.id,
                uri = clip.uri,
                sourceDurationMs = source,
                startMs = start,
                endMs = if (end < start) start else end,
                x = clip.pipX.coerceIn(0f, 1f),
                y = clip.pipY.coerceIn(0f, 1f),
                scale = clip.pipScale.coerceIn(MIN_SCALE, MAX_SCALE),
                rotationDeg = clip.pipRotationDeg.coerceIn(-180f, 180f),
                opacity = clip.pipOpacity.coerceIn(0f, 1f),
                layer = clip.trackIndex.coerceIn(1, MAX_LAYER)
            )
        }

        /** Layer number to park an overlay on when it has none yet. */
        fun preferredLayer(occupied: Collection<Int>): Int =
            (1..MAX_LAYER).firstOrNull { it !in occupied } ?: 1
    }

    /**
     * Move the start of the window, clamped to [0, endMs) — a drag can
     * slide the head against the tail but can never empty the overlay.
     */
    fun withStart(millis: Long): OverlayClip {
        val maxStart = (endMs - 1L).coerceAtLeast(0L)
        return copy(startMs = millis.coerceIn(0L, maxStart))
    }

    /**
     * Move the end of the window. Clamped inside the source so the
     * overlay can't out-grow the media it plays from.
     */
    fun withEnd(millis: Long): OverlayClip {
        val lo = startMs + 1L
        val hi = sourceDurationMs.coerceAtLeast(lo)
        return copy(endMs = millis.coerceIn(lo, hi))
    }

    /** A uniform clamp of both ends; tolerant of a reversed drag. */
    fun trimmed(start: Long, end: Long): OverlayClip {
        val lo = start.coerceIn(0L, sourceDurationMs.coerceAtLeast(0L))
        val hi = end.coerceAtLeast(lo + 1)
        return copy(
            startMs = lo,
            endMs = hi.coerceAtMost(sourceDurationMs.coerceAtLeast(hi))
        )
    }

    /**
     * True when [other] differs only in fields this class believes it
     * owns — used by tests and the dirty-check that avoids re-rendering
     * the preview for a one-slider nudge.
     */
    fun sameGeometryAs(other: OverlayClip): Boolean = this == other

    /** Maximum combined drift across the normalised transform. */
    fun transformDrift(other: OverlayClip): Float = maxOf(
        abs(x - other.x), abs(y - other.y),
        abs(scale - other.scale),
        abs(rotationDeg - other.rotationDeg) / 180f,
        abs(opacity - other.opacity)
    )

    /**
     * Write this view back onto [clip], touching ONLY the five
     * transform fields, the trim window and the lane — every other
     * property survives untouched.
     */
    fun writeTo(clip: MediaClip): MediaClip {
        require(clip.id == id) { "OverlayClip(${clip.id}) cannot write to ${clip.id}" }
        return clip.copy(
            trimStartMs = startMs,
            trimEndMs = endMs,
            pipX = x.coerceIn(0f, 1f),
            pipY = y.coerceIn(0f, 1f),
            pipScale = scale.coerceIn(MIN_SCALE, MAX_SCALE),
            pipRotationDeg = rotationDeg.coerceIn(-180f, 180f),
            pipOpacity = opacity.coerceIn(0f, 1f),
            trackIndex = if (clip.type == ClipType.OVERLAY) layer.coerceIn(1, MAX_LAYER) else clip.trackIndex
        )
    }
}
