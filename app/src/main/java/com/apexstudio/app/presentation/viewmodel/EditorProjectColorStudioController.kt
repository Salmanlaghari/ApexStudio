package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.color.hasValidRenderParameters
import com.apexstudio.app.domain.model.ColorStudioGrade

/** Commit a Color Studio draft as the project-wide grade used by preview and the normal final export. */
fun EditorViewModel.applyProjectColorStudioGrade(grade: ColorStudioGrade): Boolean {
    if (!grade.hasValidRenderParameters()) return false
    val before = _state.value
    val project = before.project ?: return false
    val persistedGrade = grade.takeUnless { it == ColorStudioGrade() }
    if (project.colorStudioGrade == persistedGrade) return true

    val updated = before.copy(project = project.copy(colorStudioGrade = persistedGrade))
    if (!_state.compareAndSet(before, updated)) return false
    // Capture the pre-edit state explicitly; only append undo history once the state commit succeeded.
    pushUndo(before)
    persistProject()
    return _state.value.project?.let {
        it.id == project.id && it.colorStudioGrade == persistedGrade
    } == true
}
