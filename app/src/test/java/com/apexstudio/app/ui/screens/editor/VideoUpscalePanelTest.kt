package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.apexstudio.app.presentation.viewmodel.VideoUpscaleUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoUpscalePanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun supportedVideoHasRealActionButNoPromptOrPhotoControl() {
        var requests = 0
        compose.setContent { MaterialTheme { VideoUpscalePanel(true, VideoUpscaleUiState(),
            onApply = { requests++ }, onCancel = {}, onClose = {}) } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("apply_project_video_upscale").performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithTag("cancel_project_video_upscale").assertDoesNotExist()
    }

    @Test fun imageOrStaleTargetCannotStartProcessing() {
        compose.setContent { MaterialTheme { VideoUpscalePanel(false, VideoUpscaleUiState(),
            onApply = { error("Must not start") }, onCancel = {}, onClose = {}) } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("apply_project_video_upscale").assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun processingDisablesDuplicateRequestAndOffersActualCancellation() {
        var cancelled = 0
        compose.setContent { MaterialTheme { VideoUpscalePanel(true,
            VideoUpscaleUiState(processing = true, progress01 = .5f, message = "Upscaling video frames…"),
            onApply = { error("Must not start twice") }, onCancel = { cancelled++ }, onClose = {}) } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("apply_project_video_upscale").assertIsNotEnabled()
        compose.onNodeWithTag("cancel_project_video_upscale").performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(1, cancelled) }
    }
}
