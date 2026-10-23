package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.Composable
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
    onDismiss: () -> Unit
) {
    if (filters) {
        FilterWorkspacePanel(projectId, originalClip, vm, isPlaying, onPreviewClip, onSeekSource, onTogglePlayback, onDismiss)
    } else {
        EffectWorkspacePanel(projectId, originalClip, vm, isPlaying, onPreviewClip, onSeekSource, onTogglePlayback, onDismiss)
    }
}
