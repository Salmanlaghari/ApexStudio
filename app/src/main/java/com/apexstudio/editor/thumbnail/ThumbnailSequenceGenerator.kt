package com.apexstudio.editor.thumbnail

/**
 * Full-duration thumbnail sampling maths — Bug 1 fix.
 *
 * The timeline used to ask the extractor for a FIXED 8 frames per clip
 * (`EditorProjectController.loadClipThumbnails`, `count = 8`), so a
 * three-minute video produced eight thumbnails and the video layer went
 * blank after the first few seconds. This object derives the frame
 * count and each frame's timestamp from the CLIP'S OWN DURATION, so
 * every clip is covered end-to-end regardless of length.
 *
 * Deliberately pure Kotlin — no Android, no I/O, no coroutines — so the
 * maths is unit-testable on the JVM. Decoding stays behind
 * `VideoThumbnailExtractor`, which is driven by the timestamps produced
 * here.
 *
 * Loading model:
 * - [sampleCount] / [timestamps] size the strip ONCE (eager, cheap).
 * - [windowKeys] answers "which of those frames does the visible
 *   scroll window need right now?" so decoding stays LAZY while the
 *   user scrolls and never blocks the UI thread.
 * - [frameIndexAt] maps an arbitrary time to its owning cell so a
 *   preview or a dropped frame can reuse the nearest real thumbnail.
 */
object ThumbnailSequenceGenerator {

    /** One frame per second — the default sampling interval. */
    const val DEFAULT_INTERVAL_MS: Long = 1_000L

    /** Short clips still yield this many samples so the strip is recognisable. */
    const val MIN_FRAMES = 8

    /** Upper bound: past this the decoder is called more often than a screen can show. */
    const val MAX_FRAMES = 300

    /**
     * Number of frames needed to cover [durationMs] at one per
     * [intervalMs], clamped to [[MIN_FRAMES], [MAX_FRAMES]].
     *
     * Uses ceiling division so 4_500 ms at 1_000 ms/frame yields 5
     * frames (never a truncated 4 that would drop the tail clip).
     */
    fun sampleCount(durationMs: Long, intervalMs: Long = DEFAULT_INTERVAL_MS): Int {
        if (durationMs <= 0L) return MIN_FRAMES
        val step = intervalMs.coerceAtLeast(1L)
        val raw = ((durationMs + step - 1L) / step).toInt()
        return raw.coerceIn(MIN_FRAMES, MAX_FRAMES)
    }

    /**
     * [frameCount] timestamps covering the WHOLE of [durationMs],
     * evenly spaced from 0 to [durationMs] inclusive.
     *
     * Spacing by duration rather than multiplying a fixed interval is
     * what guarantees full coverage: the last element is always exactly
     * `durationMs`, so the final second of a long clip is never blank.
     */
    fun timestamps(durationMs: Long, frameCount: Int): LongArray {
        val count = frameCount.coerceIn(1, MAX_FRAMES * 2)
        if (count <= 1) return longArrayOf(0L)
        val span = durationMs.coerceAtLeast(0L)
        return LongArray(count) { i -> (span * i) / (count - 1) }
    }

    /**
     * Build a [ThumbnailSequence] placeholder list for [uri] — one
     * [Thumbnail] per sampled timestamp with a `null` bitmap for the
     * decode stage to fill in.
     */
    fun sequence(
        uri: String,
        durationMs: Long,
        frameCount: Int = sampleCount(durationMs),
        intervalMs: Long = DEFAULT_INTERVAL_MS
    ): ThumbnailSequence = ThumbnailSequence(
        videoUri = uri,
        durationMs = durationMs.coerceAtLeast(0L),
        intervalMs = intervalMs,
        thumbnails = timestamps(durationMs, frameCount).mapIndexed { index, at ->
            Thumbnail(id = "$index", timestampMs = at, bitmap = null)
        }
    )

    /**
     * The subset of [timestamps] that falls inside
     * `[windowStartMs, windowEndMs]` — the LAZY-LOAD half of the fix:
     * a scrolled-to window requests only the frames it can show, so
     * a 10-minute clip never decodes all 300 frames up front.
     *
     * A window that does not intersect the clip at all (entirely
     * before 0, or entirely past the duration) returns an empty array
     * BEFORE any clamping, so a stale scroll position can never surface
     * a single frame clamped to the clip edge. A window that only
     * partially overlaps is clamped inward and yields the frames inside
     * it. Neither case throws.
     */
    fun windowKeys(
        durationMs: Long,
        windowStartMs: Long,
        windowEndMs: Long,
        frameCount: Int
    ): LongArray {
        // Kilo issue 3: an inverted window (RangeSlider dragged out of
        // order) is a degenerate selection, not a one-pixel window.
        // Reject it before clamping so two reversed handles can never
        // resolve to a frame sitting on the clamped edge.
        if (windowStartMs > windowEndMs) return LongArray(0)
        val span = durationMs.coerceAtLeast(0L)
        // No intersection with [0, span]: nothing is visible to lazy-load.
        if (windowEndMs < 0L || windowStartMs > span) return LongArray(0)
        val from = windowStartMs.coerceIn(0L, span)
        val to = windowEndMs.coerceIn(from, span)
        return timestamps(span, frameCount).filter { it in from..to }.toLongArray()
    }

    /**
     * Index of the frame responsible for [timeMs], clamped to the
     * strip — used to place the playhead against an already-decoded
     * strip without scanning the array.
     */
    fun frameIndexAt(durationMs: Long, timeMs: Long, frameCount: Int): Int {
        val count = frameCount.coerceIn(1, MAX_FRAMES * 2)
        if (count <= 1) return 0
        val span = durationMs.coerceAtLeast(0L)
        if (span == 0L) return 0
        val clamped = timeMs.coerceIn(0L, span)
        return ((clamped * (count - 1)) / span).toInt().coerceIn(0, count - 1)
    }
}
