package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.TextOverlay
import kotlinx.coroutines.flow.update

/** Local title only: no social account, publishing, ranking or remote challenge claims. */
internal fun normalizeChallengeTag(value: String): String? {
    val tag = value.trim().removePrefix("#")
    if (tag.length !in 1..48) return null
    val first = tag.codePointAt(0)
    if (!Character.isLetterOrDigit(first) && first != '_'.code) return null
    val valid = tag.codePoints().allMatch { code ->
        Character.isLetterOrDigit(code) || code == '_'.code || when (Character.getType(code)) {
            Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt() -> true
            else -> false
        }
    }
    return if (valid) "#$tag" else null
}

internal fun EditorViewModel.applyLocalChallenge(projectId: String, original: MediaClip, value: String): Boolean = synchronized(this) {
    val tag = normalizeChallengeTag(value) ?: return@synchronized false
    val state = _state.value
    val project = state.project ?: return@synchronized false
    if (project.id != projectId || state.selectedClipId != original.id || project.clips.firstOrNull { it.id == original.id } != original ||
        original.type != ClipType.VIDEO || original.trackIndex != 0 || original.uri.isBlank() || original.durationMs <= 0L || original.trimStartMs < 0L ||
        original.trimEndMs <= original.trimStartMs || original.trimEndMs > original.durationMs) return@synchronized false
    pushUndo()
    val title = TextOverlay.of(text = tag, x = 0.5f, y = 0.8f)
    updateClip(original.id) { it.copy(textOverlays = it.textOverlays + title) }
    _state.update { it.copy(selectedTextOverlayId = title.id, selectedStickerId = null) }
    true
}
