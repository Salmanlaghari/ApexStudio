package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ColorStudioGrade
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorProjectColorStudioControllerTest {
    @Test fun `applying valid grade updates project and identity clears it`() {
        val clip = MediaClip("main", "Main", "file:///main.mp4", 4_000L, trimEndMs = 4_000L)
        val viewModel = EditorViewModel()
        viewModel._state.value = EditorState(
            project = Project("color-project", "Color project", 4_000L, clips = listOf(clip))
        )
        val grade = ColorStudioGrade(brightness = .24f, contrast = 1.15f, sharpness = .3f)

        assertTrue(viewModel.applyProjectColorStudioGrade(grade))
        assertEquals(grade, viewModel.state.value.project?.colorStudioGrade)
        assertTrue(viewModel.state.value.canUndo)
        assertNull(viewModel.undoStack.last().project.colorStudioGrade)

        assertFalse(viewModel.applyProjectColorStudioGrade(grade.copy(brightness = Float.NaN)))
        assertEquals(grade, viewModel.state.value.project?.colorStudioGrade)

        assertTrue(viewModel.applyProjectColorStudioGrade(ColorStudioGrade()))
        assertNull(viewModel.state.value.project?.colorStudioGrade)
    }

    @Test fun `grade commit retries unrelated state conflict and returns latest undo snapshot`() {
        val project = Project("color-project", "Color project", 4_000L)
        val state = ConflictingStateFlow(EditorState(project = project)) { current ->
            current.copy(playerPositionMs = 271L)
        }
        val grade = ColorStudioGrade(brightness = .2f)

        val result = commitProjectColorStudioGrade(state, grade)

        assertTrue(result is ProjectColorGradeCommitResult.Applied)
        assertEquals(271L, state.value.playerPositionMs)
        assertEquals(grade, state.value.project?.colorStudioGrade)
        assertEquals(271L, (result as ProjectColorGradeCommitResult.Applied).previousState.playerPositionMs)
    }

    @Test fun `grade commit rejects a project switch during a failed compare and set`() {
        val original = Project("color-project", "Color project", 4_000L)
        val replacement = Project("replacement", "Replacement", 4_000L)
        val state = ConflictingStateFlow(EditorState(project = original)) { current ->
            current.copy(project = replacement)
        }

        val result = commitProjectColorStudioGrade(state, ColorStudioGrade(brightness = .2f))

        assertEquals(ProjectColorGradeCommitResult.Rejected, result)
        assertEquals("replacement", state.value.project?.id)
        assertNull(state.value.project?.colorStudioGrade)
    }

    private class ConflictingStateFlow(
        private val backing: MutableStateFlow<EditorState>,
        private val updateOnFirstCompareAndSet: (EditorState) -> EditorState
    ) : MutableStateFlow<EditorState> by backing {
        private var injectConflict = true

        override fun compareAndSet(expect: EditorState, update: EditorState): Boolean {
            if (injectConflict) {
                injectConflict = false
                backing.value = updateOnFirstCompareAndSet(backing.value)
            }
            return backing.compareAndSet(expect, update)
        }
    }
}
