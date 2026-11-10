package com.apexstudio.app.data.export

import android.os.SystemClock

/**
 * Bounded, privacy-conscious breadcrumbs for diagnosing slow or stalled exports.
 * Events intentionally contain only caller-supplied stage names and aggregate
 * numeric details; never pass source URIs, filenames, or user-authored content.
 */
internal class ExportDiagnosticTrace(
    private val emit: (String) -> Unit,
    private val clockMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val startedAtMs = clockMs()
    private var lastProgressBucket = -1
    private var activePhase: String? = null

    @Synchronized
    fun stage(name: String, details: String = "") {
        val elapsedMs = (clockMs() - startedAtMs).coerceAtLeast(0L)
        val suffix = details.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        emit("export_diag elapsedMs=$elapsedMs stage=$name$suffix")
    }

    /** Close the previous phase and start a new one; null closes the active phase. */
    @Synchronized
    fun phase(name: String?) {
        val next = name?.takeIf { it.isNotBlank() }
        if (next == activePhase) return
        activePhase?.let { stage("${it}_completed") }
        activePhase = next
        next?.let { stage("${it}_started") }
    }

    /** Record a failed phase without misreporting it as completed. */
    @Synchronized
    fun failPhase(details: String = "") {
        val failed = activePhase ?: return
        activePhase = null
        stage("${failed}_failed", details)
    }

    /** Log at most one progress breadcrumb per 10% video-render bucket. */
    @Synchronized
    fun progress(percent: Int) {
        val bounded = percent.coerceIn(0, 99)
        val bucket = (bounded / 10) * 10
        if (bucket <= lastProgressBucket) return
        lastProgressBucket = bucket
        stage("video_progress", "percent=$bucket")
    }
}
