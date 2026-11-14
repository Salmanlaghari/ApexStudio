package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ColorStudioGrade
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
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

        assertFalse(viewModel.applyProjectColorStudioGrade(grade.copy(brightness = Float.NaN)))
        assertEquals(grade, viewModel.state.value.project?.colorStudioGrade)

        assertTrue(viewModel.applyProjectColorStudioGrade(ColorStudioGrade()))
        assertNull(viewModel.state.value.project?.colorStudioGrade)
    }
}
