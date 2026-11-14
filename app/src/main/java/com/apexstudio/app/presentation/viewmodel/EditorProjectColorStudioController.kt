package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.color.hasValidRenderParameters
import com.apexstudio.app.domain.model.ColorStudioGrade
import com.apexstudio.app.presentation.state.EditorState
import kotlinx.coroutines.flow.MutableStateFlow

private const val MAX_PROJECT_COLOR_GRADE_COMMIT_ATTEMPTS = 8

internal sealed class ProjectColorGradeCommitResult {
    object Rejected : ProjectColorGradeCommitResult()
    data class Unchanged(val projectId: String) : ProjectColorGradeCommitResult()
    data class Applied(val projectId: String, val previousState: EditorState) : ProjectColorGradeCommitResult()
}

/** Retry a grade-only merge when an unrelated editor-state update wins the first CAS. */
internal fun commitProjectColorStudioGrade(
    state: MutableStateFlow<EditorState>,
    grade: ColorStudioGrade
): ProjectColorGradeCommitResult {
    if (!grade.hasValidRenderParameters()) return ProjectColorGradeCommitResult.Rejected
    val targetProjectId = state.value.project?.id ?: return ProjectColorGradeCommitResult.Rejected
    val persistedGrade = grade.takeUnless { it == ColorStudioGrade() }

    repeat(MAX_PROJECT_COLOR_GRADE_COMMIT_ATTEMPTS) {
        val before = state.value
        val project = before.project?.takeIf { it.id == targetProjectId }
            ?: return ProjectColorGradeCommitResult.Rejected
        if (project.colorStudioGrade == persistedGrade) {
            return ProjectColorGradeCommitResult.Unchanged(targetProjectId)
        }

        val updated = before.copy(project = project.copy(colorStudioGrade = persistedGrade))
        if (state.compareAndSet(before, updated)) {
            return ProjectColorGradeCommitResult.Applied(targetProjectId, before)
        }
    }
    return ProjectColorGradeCommitResult.Rejected
}

/** Commit a Color Studio draft as the project-wide grade used by preview and the normal final export. */
fun EditorViewModel.applyProjectColorStudioGrade(grade: ColorStudioGrade): Boolean =
    synchronized(_state) {
        // Keep the successful state transition adjacent to its undo entry so concurrent grades
        // cannot append snapshots in the opposite order.
        when (val commit = commitProjectColorStudioGrade(_state, grade)) {
            ProjectColorGradeCommitResult.Rejected -> false
            is ProjectColorGradeCommitResult.Unchanged -> true
            is ProjectColorGradeCommitResult.Applied -> {
                pushUndo(commit.previousState)
                persistProject()
                _state.value.project?.let {
                    it.id == commit.projectId && it.colorStudioGrade == grade.takeUnless { value -> value == ColorStudioGrade() }
                } == true
            }
        }
    }
