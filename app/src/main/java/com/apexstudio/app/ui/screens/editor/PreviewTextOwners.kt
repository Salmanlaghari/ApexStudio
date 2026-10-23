package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip

/** Selection controls editing, never whether another visible video's titles are drawn. */
internal fun previewTextOwners(
    mainPreviewClip: MediaClip?,
    overlayClips: List<MediaClip>,
    projectTimeMs: Long
): List<MediaClip> = buildList {
    mainPreviewClip?.let(::add)
    overlayClips.filter {
        it.type == ClipType.OVERLAY && it.isOverlayActiveAt(projectTimeMs) && it.textOverlays.isNotEmpty()
    }.sortedBy { it.trackIndex }.forEach(::add)
}.distinctBy { it.id }
