package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.viewmodel.EditorViewModel

/** One transient-preview lifecycle for both bottom workspaces; neither writes until Apply. */
@Composable
internal fun ClipEffectWorkspaceHost(
    filters: Boolean,
    projectId: String,
    originalClip: MediaClip,
    vm: EditorViewModel,
    isPlaying: Boolean,
    onPreviewClip: (MediaClip) -> Unit,
    onSeekSource: (Long) -> Unit,
    onTogglePlayback: () -> Unit,
    onDismiss: () -> Unit,
    onReopenFilters: () -> Unit,
    openTimeActions: Boolean = false,
) {
    if (filters) {
        // A restart can replace the source/trim while retaining the clip ID. Reset all
        // remembered previews/thumbnails, not just the draft fields keyed by ID.
        key(projectId, originalClip) {
            FilterWorkspacePanel(projectId, originalClip, vm, isPlaying, onPreviewClip,
                onSeekSource, onTogglePlayback, onDismiss, onReopen = onReopenFilters)
        }
    } else {
        EffectWorkspacePanel(projectId, originalClip, vm, isPlaying, onPreviewClip, onSeekSource, onTogglePlayback, onDismiss,
            openTimeActions = openTimeActions)
    }
}
