package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.apexstudio.app.data.picker.MediaMetadata
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Project

/**
 * Convert persisted project media back to picker metadata without inventing assets.
 * Clip dimensions and frame rate are not persisted in the project model, so those fields
 * intentionally remain zero (unknown); source URIs and durations are preserved.
 */
internal fun projectMediaLibraryItems(project: Project?): List<MediaMetadata> {
    if (project == null) return emptyList()

    val clips = project.clips.map { clip ->
        MediaMetadata(
            uri = clip.uri,
            name = clip.name,
            durationMs = clip.durationMs.coerceAtLeast(0L),
            width = 0,
            height = 0,
            fps = 0,
            type = clip.type
        )
    }
    val audio = project.audioTracks.map { track ->
        MediaMetadata(
            uri = track.uri,
            name = track.name,
            durationMs = (track.sourceDurationMs.takeIf { it > 0L } ?: track.trimEndMs).coerceAtLeast(0L),
            width = 0,
            height = 0,
            fps = 0,
            type = if (track.kind == AudioTrack.Kind.SFX) ClipType.SFX else ClipType.AUDIO
        )
    }
    return (clips + audio)
        .filter { it.uri.isNotBlank() }
        .distinctBy { it.uri }
}

/** Project-aware library panel used by the editor's Add Media sheet. */
@Composable
fun ProjectMediaLibraryPanel(
    project: Project?,
    onImportMedia: (List<MediaMetadata>) -> Unit,
    onBrowseVideo: () -> Unit,
    onBrowsePhotos: () -> Unit,
    onBrowseOverlay: () -> Unit,
    onBrowseAudio: () -> Unit,
    onClose: () -> Unit
) {
    val libraryItems = remember(project) { projectMediaLibraryItems(project) }
    MediaLibrarySheet(
        onImportMedia = onImportMedia,
        onOpenSystemPicker = onBrowseVideo,
        onClose = onClose,
        libraryItems = libraryItems,
        onBrowseVideo = onBrowseVideo,
        onBrowsePhotos = onBrowsePhotos,
        onBrowseOverlay = onBrowseOverlay,
        onBrowseAudio = onBrowseAudio
    )
}
