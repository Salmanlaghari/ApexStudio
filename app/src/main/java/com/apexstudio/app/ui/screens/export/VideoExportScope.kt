package com.apexstudio.app.ui.screens.export

import androidx.compose.runtime.Composable
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.viewmodel.resolveExportBaseClip

/** Describes the current single-base-clip route, not an unimplemented project render. */
internal data class VideoExportScope(
    val baseClip: MediaClip?,
    val mainClipCount: Int,
    val durationMs: Long
) {
    val notice: String?
        get() = when {
            baseClip == null -> "No main video or photo is available for video export."
            mainClipCount > 1 -> "Selected-clip export saves only the selected main clip (or the first main clip when no main clip is selected), not all $mainClipCount main clips. Whole project (basic) is a separate mode and rejects unsupported edits."
            else -> null
        }
}

internal fun videoExportScope(state: EditorState): VideoExportScope {
    val base = resolveExportBaseClip(state)
    return VideoExportScope(
        baseClip = base,
        mainClipCount = state.project?.clips.orEmpty().count {
            it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE)
        },
        durationMs = base?.effectiveDurationMs()?.coerceAtLeast(0L) ?: 0L
    )
}

/** Media3 can report no percentage while preparing inputs/decoders; don't label that as a stalled render. */
internal fun exportProgressLabel(isExporting: Boolean, progress: Float): String? = when {
    !isExporting -> null
    !progress.isFinite() || progress <= 0f -> "Preparing export…"
    else -> "Exporting…"
}

@Composable
internal fun VideoExportScopeWarning(scope: VideoExportScope) {
    AudioExportWarning(scope.notice)
}
