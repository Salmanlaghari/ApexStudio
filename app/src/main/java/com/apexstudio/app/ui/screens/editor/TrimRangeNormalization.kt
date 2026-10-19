package com.apexstudio.app.ui.screens.editor

/** A valid, clip-bounded trim interval in milliseconds. */
internal data class NormalizedTrimRange(val startMs: Long, val endMs: Long)

/**
 * Normalizes a requested interval while keeping a minimum 100 ms selection when possible.
 * Clips shorter than 100 ms use their full duration as the minimum span; empty clips have no
 * valid trim range. Bounds are calculated without adding to untrusted input values.
 */
internal fun normalizeTrimRange(
    startMs: Long,
    endMs: Long,
    durationMs: Long
): NormalizedTrimRange? {
    val duration = durationMs.coerceAtLeast(0L)
    if (duration == 0L || endMs <= startMs) return null

    val minimumSpan = minOf(100L, duration)
    val latestStart = duration - minimumSpan
    val safeStart = startMs.coerceIn(0L, latestStart)
    val earliestEnd = safeStart + minimumSpan // <= duration, so this cannot overflow.
    val safeEnd = endMs.coerceIn(earliestEnd, duration)
    return NormalizedTrimRange(safeStart, safeEnd)
}
