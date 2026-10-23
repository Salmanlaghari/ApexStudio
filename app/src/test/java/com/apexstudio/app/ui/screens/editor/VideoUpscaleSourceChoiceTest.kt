package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoUpscaleSourceChoiceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pipSelectionOffersExplicitMainSourceWithoutStartingProcessingOrChangingProject() {
        val main = MediaClip("main", "Main video", "file:///main.mp4", 5000L, trimEndMs = 5000L)
        val pip = main.copy(id = "pip", type = ClipType.OVERLAY, trackIndex = 1)
        val project = Project("p", "Project", 5000L, clips = listOf(main, pip))
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(project = project, selectedClipId = pip.id)
        }
        compose.setContent { VideoUpscaleOverlay(vm, {}) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag("apply_project_video_upscale").assertIsNotEnabled()
        compose.onNodeWithTag("choose_main_video_upscale").performScrollTo()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag("choose_main_video_upscale").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals("Explicit source action must select the main clip", main.id, vm.state.value.selectedClipId) }
        compose.waitForIdle()
        compose.onNodeWithTag("apply_project_video_upscale").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            assertEquals(main.id, vm.state.value.selectedClipId)
            assertEquals(project, vm.state.value.project)
            assertFalse(vm.videoUpscaleUi.value.processing)
            assertTrue(vm.undoStack.isEmpty())
        }
    }
}
