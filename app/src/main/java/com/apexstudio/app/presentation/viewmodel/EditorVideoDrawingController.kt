package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.VideoDrawingLayer
import kotlinx.coroutines.flow.update

/** Commits one completed freehand stroke to the persisted project model. */
fun EditorViewModel.updateVideoDrawingLayer(layer: VideoDrawingLayer) {
    val project = _state.value.project ?: return
    val clean = layer.sanitized()
    val prior = project.videoDrawings.firstOrNull { it.id == clean.id }
    if (prior == clean) return
    pushUndo()
    _state.update { state ->
        val current = state.project ?: return@update state
        val drawings = current.videoDrawings.toMutableList()
        val index = drawings.indexOfFirst { it.id == clean.id }
        if (index >= 0) drawings[index] = clean else drawings += clean
        state.copy(project = current.copy(videoDrawings = drawings))
    }
    persistProject()
}

/** Remove the most recent completed mark for the currently drawn clip only. */
fun EditorViewModel.undoVideoDrawingStroke(clipId: String?) {
    val project = _state.value.project ?: return
    val index = project.videoDrawings.indexOfLast { it.clipId == clipId && it.strokes.isNotEmpty() }
    if (index < 0) return
    pushUndo()
    _state.update { state ->
        val current = state.project ?: return@update state
        val drawings = current.videoDrawings.toMutableList()
        val updated = drawings[index].undo()
        if (updated.strokes.isEmpty()) drawings.removeAt(index) else drawings[index] = updated
        state.copy(project = current.copy(videoDrawings = drawings))
    }
    persistProject()
}

/** Clear artwork on one clip without affecting the video or artwork on other clips. */
fun EditorViewModel.clearVideoDrawings(clipId: String?) {
    val project = _state.value.project ?: return
    if (project.videoDrawings.none { it.clipId == clipId }) return
    pushUndo()
    _state.update { state ->
        val current = state.project ?: return@update state
        state.copy(project = current.copy(videoDrawings = current.videoDrawings.filterNot { it.clipId == clipId }))
    }
    persistProject()
}
