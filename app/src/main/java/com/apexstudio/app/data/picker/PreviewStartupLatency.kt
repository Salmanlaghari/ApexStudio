package com.apexstudio.app.data.picker

import java.util.concurrent.ConcurrentHashMap

/** Correlates a picker-return timestamp with the first rendered frame of that exact video clip. */
internal class PreviewStartupLatencyTracker {
    private data class Selection(
        val mediaId: String,
        val selectedAtElapsedRealtimeMs: Long
    )

    private val pendingByProject = ConcurrentHashMap<String, Selection>()

    fun begin(projectId: String, mediaId: String, selectedAtElapsedRealtimeMs: Long) {
        if (projectId.isBlank() || mediaId.isBlank() || selectedAtElapsedRealtimeMs < 0L) return
        pendingByProject[projectId] = Selection(mediaId, selectedAtElapsedRealtimeMs)
    }

    /** Returns a duration once, only for the expected clip; unrelated frames leave it pending. */
    fun recordFirstFrame(
        projectId: String,
        mediaId: String?,
        renderedAtElapsedRealtimeMs: Long
    ): Long? {
        if (mediaId.isNullOrBlank() || renderedAtElapsedRealtimeMs < 0L) return null
        val selection = pendingByProject[projectId] ?: return null
        if (selection.mediaId != mediaId || !pendingByProject.remove(projectId, selection)) return null
        return (renderedAtElapsedRealtimeMs - selection.selectedAtElapsedRealtimeMs).takeIf { it >= 0L }
    }

    fun clear(projectId: String) {
        pendingByProject.remove(projectId)
    }
}

/** Process-local measurement only; no media URI or user data is sent or persisted. */
internal object PreviewStartupLatency {
    private val tracker = PreviewStartupLatencyTracker()

    fun begin(projectId: String, mediaId: String, selectedAtElapsedRealtimeMs: Long) =
        tracker.begin(projectId, mediaId, selectedAtElapsedRealtimeMs)

    fun recordFirstFrame(projectId: String, mediaId: String?, renderedAtElapsedRealtimeMs: Long): Long? =
        tracker.recordFirstFrame(projectId, mediaId, renderedAtElapsedRealtimeMs)

    fun clear(projectId: String) = tracker.clear(projectId)
}
