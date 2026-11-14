package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.color.hasValidRenderParameters
import com.apexstudio.app.domain.model.ColorStudioGrade
import kotlinx.coroutines.flow.update

/** Commit a Color Studio draft as the project-wide grade used by preview and the normal final export. */
fun EditorViewModel.applyProjectColorStudioGrade(grade: ColorStudioGrade): Boolean {
    if (!grade.hasValidRenderParameters()) return false
    val project = _state.value.project ?: return false
    val persistedGrade = grade.takeUnless { it == ColorStudioGrade() }
    if (project.colorStudioGrade == persistedGrade) return true

    pushUndo()
    _state.update { current ->
        val currentProject = current.project ?: return@update current
        if (currentProject.id != project.id) return@update current
        current.copy(project = currentProject.copy(colorStudioGrade = persistedGrade))
    }
    persistProject()
    return _state.value.project?.let {
        it.id == project.id && it.colorStudioGrade == persistedGrade
    } == true
}
