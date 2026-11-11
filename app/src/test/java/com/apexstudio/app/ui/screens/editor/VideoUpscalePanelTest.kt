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

    @Test fun ownersTwoMinute59SecondSourceCanStartProcessing() {
        compose.setContent { MaterialTheme { VideoUpscalePanel(true, VideoUpscaleUiState(),
            onApply = {}, onCancel = {}, onClose = {}, sourceDurationMs = 179_466L) } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("video_upscale_duration_blocker").assertDoesNotExist()
        compose.onNodeWithText("5 minutes (up to 9,000 frames)", substring = true).assertExists()
        compose.onNodeWithText("a timeline trim does not shorten the source", substring = true).assertExists()
        compose.onNodeWithTag("apply_project_video_upscale").assertIsEnabled()
        compose.onNodeWithTag("cancel_project_video_upscale").assertDoesNotExist()
    }

    @Test fun sourcePastFiveMinutesRemainsBlocked() {
        compose.setContent { MaterialTheme { VideoUpscalePanel(true, VideoUpscaleUiState(),
            onApply = { error("Over-limit source must not start") }, onCancel = {}, onClose = {}, sourceDurationMs = 300_001L) } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("video_upscale_duration_blocker").assertIsDisplayed()
        compose.onNodeWithText("05:00 or shorter", substring = true).assertExists()
        compose.onNodeWithTag("apply_project_video_upscale").assertIsNotEnabled()
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
            VideoUpscaleUiState(processing = true, progress01 = .5f, message = "Enhancing video…"),
            onApply = { error("Must not start twice") }, onCancel = { cancelled++ }, onClose = {}) } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("apply_project_video_upscale").assertIsNotEnabled()
        compose.onNodeWithTag("cancel_project_video_upscale").performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(1, cancelled) }
    }

    @Test fun completedEnhancementSurfacesReviewActionBeforeLongDetails() {
        var reviewed = 0
        compose.setContent { MaterialTheme { VideoUpscalePanel(true,
            VideoUpscaleUiState(progress01 = 1f, message = "Enhanced video ready.", outputUri = "file:///enhanced.mp4"),
            onApply = { error("Completed output must not be processed again") }, onCancel = {}, onClose = { reviewed++ }) } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("video_upscale_result").assertIsDisplayed()
        compose.onNodeWithText("Enhanced video ready").assertIsDisplayed()
        compose.onNodeWithTag("apply_project_video_upscale").assertIsNotEnabled()
        compose.onNodeWithTag("review_project_video_upscale").performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(1, reviewed) }
    }
}
