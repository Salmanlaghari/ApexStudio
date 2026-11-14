package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.color.hasValidRenderParameters
import com.apexstudio.app.domain.model.ColorStudioGrade
import com.apexstudio.app.presentation.state.EditorState
import kotlinx.coroutines.flow.MutableStateFlow

private const val MAX_PROJECT_COLOR_GRADE_COMMIT_ATTEMPTS = 8

internal sealed class ProjectColorGradeCommitResult {
    object InvalidGrade : ProjectColorGradeCommitResult()
    object NoActiveProject : ProjectColorGradeCommitResult()
    data class ProjectChanged(val expectedProjectId: String, val actualProjectId: String?) : ProjectColorGradeCommitResult()
    object ContentionExhausted : ProjectColorGradeCommitResult()
    data class Unchanged(val projectId: String) : ProjectColorGradeCommitResult()
    data class Applied(val projectId: String, val previousState: EditorState) : ProjectColorGradeCommitResult()

    val failureMessage: String?
        get() = when (this) {
            InvalidGrade -> "Color grade has unsupported values. Reset or adjust it, then try again."
            NoActiveProject -> "No project is open. Reopen the project and try again."
            is ProjectChanged -> "The project changed while Color was being applied. Reopen Color and try again."
            ContentionExhausted -> "The project kept changing while Color was being applied. Try again."
            is Unchanged, is Applied -> null
        }
}

/**
 * Merge only the project grade into the latest editor snapshot. Eight quick, yielded CAS attempts
 * tolerate short bursts of playhead/autosave/analysis updates without spinning indefinitely.
 */
internal fun commitProjectColorStudioGrade(
    state: MutableStateFlow<EditorState>,
    grade: ColorStudioGrade
): ProjectColorGradeCommitResult {
    if (!grade.hasValidRenderParameters()) return ProjectColorGradeCommitResult.InvalidGrade
    val targetProjectId = state.value.project?.id ?: return ProjectColorGradeCommitResult.NoActiveProject
    val persistedGrade = grade.takeUnless { it == ColorStudioGrade() }

    repeat(MAX_PROJECT_COLOR_GRADE_COMMIT_ATTEMPTS) {
        val before = state.value
        val project = before.project
            ?: return ProjectColorGradeCommitResult.ProjectChanged(targetProjectId, actualProjectId = null)
        if (project.id != targetProjectId) {
            return ProjectColorGradeCommitResult.ProjectChanged(targetProjectId, project.id)
        }
        if (project.colorStudioGrade == persistedGrade) {
            return ProjectColorGradeCommitResult.Unchanged(targetProjectId)
        }

        val updated = before.copy(project = project.copy(colorStudioGrade = persistedGrade))
        if (state.compareAndSet(before, updated)) {
            return ProjectColorGradeCommitResult.Applied(targetProjectId, before)
        }
        Thread.yield()
    }
    return ProjectColorGradeCommitResult.ContentionExhausted
}

/** Commit a Color Studio draft as the project-wide grade used by preview and the normal final export. */
internal fun EditorViewModel.applyProjectColorStudioGrade(
    grade: ColorStudioGrade
): ProjectColorGradeCommitResult = synchronized(projectColorGradeCommitLock) {
    when (val commit = commitProjectColorStudioGrade(_state, grade)) {
        is ProjectColorGradeCommitResult.Applied -> {
            pushUndo(commit.previousState)
            persistProject()
            // The successful CAS is the commit point. Later playhead, autosave, or project changes
            // must not turn a committed edit into a false failure after its undo snapshot was added.
            commit
        }
        else -> commit
    }
}
