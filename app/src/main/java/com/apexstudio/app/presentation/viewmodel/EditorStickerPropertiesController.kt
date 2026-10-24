package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.StickerOverlay
import kotlinx.coroutines.flow.update

/** Applies one validated property edit to a sticker and records exactly one undo snapshot. */
fun EditorViewModel.updateStickerProperties(
    stickerId: String,
    transform: (StickerOverlay) -> StickerOverlay
) {
    val project = _state.value.project ?: return
    val current = project.stickers.firstOrNull { it.id == stickerId }
        ?: project.clips.asSequence().flatMap { it.stickers.asSequence() }.firstOrNull { it.id == stickerId }
        ?: return
    val duration = project.durationMs.coerceAtLeast(1L)
    val changed = transform(current).let { candidate ->
        val start = candidate.startMs.coerceIn(0L, duration - 1L)
        candidate.copy(
            opacity = candidate.opacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: current.opacity,
            sizeScale = candidate.sizeScale.takeIf { it.isFinite() }?.coerceIn(0.1f, 8f) ?: current.sizeScale,
            rotationDeg = candidate.rotationDeg.takeIf { it.isFinite() }?.coerceIn(-360f, 360f) ?: current.rotationDeg,
            startMs = start,
            endMs = candidate.endMs.coerceIn(start + 1L, duration)
        )
    }
    if (changed == current) return
    pushUndo()
    updateSticker(stickerId) { changed }
}

/** Set a non-empty sticker interval within the current project's actual duration. */
fun EditorViewModel.setStickerPropertyRange(stickerId: String, startMs: Long, endMs: Long) =
    updateStickerProperties(stickerId) { sticker ->
        val duration = _state.value.project?.durationMs?.coerceAtLeast(1L) ?: return@updateStickerProperties sticker
        val start = startMs.coerceIn(0L, duration - 1L)
        sticker.copy(startMs = start, endMs = endMs.coerceIn(start + 1L, duration))
    }

/** Duplicate the selected sticker as a separately editable project/clip sticker. */
fun EditorViewModel.duplicateSticker(stickerId: String) {
    val project = _state.value.project ?: return
    val source = project.stickers.firstOrNull { it.id == stickerId }
        ?: project.clips.asSequence().flatMap { it.stickers.asSequence() }.firstOrNull { it.id == stickerId }
        ?: return
    val duplicate = source.copy(
        id = java.util.UUID.randomUUID().toString(),
        x = (source.x + 0.04f).coerceAtMost(1f),
        y = (source.y + 0.04f).coerceAtMost(1f)
    )
    pushUndo()
    _state.update { state ->
        val current = state.project ?: return@update state
        val next = if (current.stickers.any { it.id == stickerId }) {
            current.copy(stickers = current.stickers + duplicate)
        } else {
            current.copy(clips = current.clips.map { clip ->
                if (clip.stickers.any { it.id == stickerId }) clip.copy(stickers = clip.stickers + duplicate) else clip
            })
        }
        state.copy(project = next, selectedStickerId = duplicate.id)
    }
    persistProject(refreshLegacyTextStickerFields = true)
}
