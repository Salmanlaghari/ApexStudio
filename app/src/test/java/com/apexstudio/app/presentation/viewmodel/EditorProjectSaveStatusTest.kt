package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.presentation.state.ProjectSaveStatus
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorProjectSaveStatusTest {
    @Test fun successfulAutosaveClearsErrorAndUpdatesSavedTime() {
        val viewModel = EditorViewModel()
        viewModel._state.update {
            it.copy(
                projectSaveStatus = ProjectSaveStatus.ERROR,
                projectSaveError = "Tap to retry saving."
            )
        }

        viewModel.markAutoSaved(nowMs = 1_234L)

        assertEquals(ProjectSaveStatus.SAVED, viewModel.state.value.projectSaveStatus)
        assertNull(viewModel.state.value.projectSaveError)
        assertEquals(1_234L, viewModel.state.value.lastAutoSaveMs)
    }
}
