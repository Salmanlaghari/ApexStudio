package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.apexstudio.app.data.aireport.AiContentReportReason
import com.apexstudio.app.data.aireport.AiContentReportTarget
import com.apexstudio.app.presentation.viewmodel.AiContentReportUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiToolsPanelTest {
    @get:Rule val compose = createComposeRule()

    private val inventory = listOf(
        card("SMART_TRIM", "Smart trim suggestions"),
        card("SCENE_ANALYSIS", "Scene detection"),
        card("SUBTITLES", "Auto captions"),
        card("WORD_TIMED_SUBTITLES", "Word-timed captions"),
        card("ONNX_UPSCALING", "Video enhancement"),
        card("FACE_BLUR", "Face blur"),
        card("ONNX_STYLE_TRANSFER", "Artistic style transfer")
    )

    @Test
    fun showsOneCurrentVideoTargetAndOnlyFourDistinctSupportedActions() {
        var reviewOpens = 0
        var captionsOpens = 0
        var backgroundOpens = 0
        var upscaleOpens = 0
        compose.setContent { MaterialTheme {
            AiToolsPanel(
                tools = inventory,
                message = "Availability checked",
                targetVideoName = "interview-take-02.mp4",
                onClose = {},
                onOpenLocalAnalysis = { reviewOpens++ },
                onOpenCaptions = { captionsOpens++ },
                onOpenBackgroundReplace = { backgroundOpens++ },
                onOpenVideoUpscale = { upscaleOpens++ },
                reportTarget = null,
                reportState = AiContentReportUiState(),
                onSubmitReport = { _, _ -> false },
                onClearReportFeedback = {}
            )
        } }

        compose.onNodeWithText("AI video tools").assertExists()
        compose.onNodeWithText("Target video").assertExists()
        compose.onNodeWithText("interview-take-02.mp4").assertExists()
        compose.onNodeWithTag("ai_action_review").assertIsEnabled()
        compose.onNodeWithTag("ai_action_captions").assertIsEnabled()
        compose.onNodeWithTag("ai_action_background").assertIsEnabled()
        compose.onNodeWithTag("ai_action_upscale").assertIsEnabled()
        compose.onAllNodesWithText("Review suggestions").assertCountEquals(1)
        compose.onAllNodesWithText("Captions").assertCountEquals(1)
        compose.onAllNodesWithText("Background replace").assertCountEquals(1)
        compose.onAllNodesWithText("Upscale video").assertCountEquals(1)
        compose.onNodeWithText("Video effects & titles").assertDoesNotExist()
        compose.onNodeWithText("Smart trim suggestions").assertDoesNotExist()
        compose.onNodeWithText("Word-timed captions").assertDoesNotExist()
        compose.onNodeWithText("Artistic style transfer").assertDoesNotExist()

        compose.onNodeWithTag("ai_action_review").activateVisibleControl()
        compose.onNodeWithTag("ai_action_captions").activateVisibleControl()
        compose.onNodeWithTag("ai_action_background").activateVisibleControl()
        compose.onNodeWithTag("ai_action_upscale").activateVisibleControl()
        compose.runOnIdle {
            assertEquals(1, reviewOpens)
            assertEquals(1, captionsOpens)
            assertEquals(1, backgroundOpens)
            assertEquals(1, upscaleOpens)
        }
    }

    @Test
    fun actionsStayDisabledUntilAProjectMainTrackVideoIsSelected() {
        compose.setContent { MaterialTheme {
            AiToolsPanel(
                tools = inventory,
                message = "",
                targetVideoName = null,
                onClose = {},
                onOpenLocalAnalysis = {},
                onOpenCaptions = {},
                onOpenBackgroundReplace = {},
                onOpenVideoUpscale = {},
                reportTarget = null,
                reportState = AiContentReportUiState(),
                onSubmitReport = { _, _ -> false },
                onClearReportFeedback = {}
            )
        } }

        compose.onNodeWithText("Select a main-track video on the timeline").assertExists()
        compose.onNodeWithText("Photos and overlays are not supported here; actions stay disabled until a main-track video is selected.").assertExists()
        compose.onNodeWithTag("ai_action_review").assertIsNotEnabled()
        compose.onNodeWithTag("ai_action_captions").assertIsNotEnabled()
        compose.onNodeWithTag("ai_action_background").assertIsNotEnabled()
        compose.onNodeWithTag("ai_action_upscale").assertIsNotEnabled()
    }

    @Test
    fun missingRouteIsNotRenderedAsAnAction() {
        compose.setContent { MaterialTheme {
            AiToolsPanel(
                tools = inventory,
                message = "",
                targetVideoName = "main.mp4",
                onClose = {},
                onOpenLocalAnalysis = {},
                reportTarget = null,
                reportState = AiContentReportUiState(),
                onSubmitReport = { _, _ -> false },
                onClearReportFeedback = {}
            )
        } }

        compose.onNodeWithText("Review suggestions").assertExists()
        compose.onNodeWithText("Captions").assertDoesNotExist()
        compose.onNodeWithText("Background replace").assertDoesNotExist()
        compose.onNodeWithText("Upscale video").assertDoesNotExist()
    }

    @Test
    fun availabilityDetailsExplainWhyUnwiredInventoryIsNotAnAction() {
        compose.setContent { MaterialTheme {
            AiToolsPanel(
                tools = inventory,
                message = "Availability checked",
                targetVideoName = "main.mp4",
                onClose = {},
                reportTarget = null,
                reportState = AiContentReportUiState(),
                onSubmitReport = { _, _ -> false },
                onClearReportFeedback = {}
            )
        } }
        compose.onNodeWithContentDescription("AI availability details").activateVisibleControl()
        compose.onNodeWithText("AI processing availability").assertExists()
        compose.onNodeWithText(AI_PROTECTED_BLOCKER).assertExists()
        compose.onNodeWithText("2 catalog/model entries are not shown as editing actions", substring = true).assertExists()
        compose.onNodeWithText("Availability checked").assertExists()
        compose.onNodeWithText("Artistic style transfer, pose overlay, content moderation and 2D-to-3D video conversion are not editing actions in this release.").assertExists()
    }

    @Test
    fun downloadIconOpensExplicitModelChooserWithoutEnablingAI() {
        var requests = 0
        compose.setContent { MaterialTheme {
            AiToolsPanel(
                tools = inventory,
                message = "",
                targetVideoName = null,
                onClose = {},
                onOpenLocalAnalysis = {},
                onOpenDownloads = { requests++ },
                reportTarget = null,
                reportState = AiContentReportUiState(),
                onSubmitReport = { _, _ -> false },
                onClearReportFeedback = {}
            )
        } }
        compose.onNodeWithContentDescription("Optional AI features").activateVisibleControl()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithTag("ai_action_review").assertIsNotEnabled()
    }

    @Test
    fun unavailableEmailFallsBackToClearlyLabelledLocalFlag() {
        var submitted: AiContentReportReason? = null
        val target = AiContentReportTarget("project", "clip", "VIDEO")
        compose.setContent { MaterialTheme {
            AiToolsPanel(
                tools = inventory, message = "", onClose = {}, reportTarget = target,
                reportState = AiContentReportUiState(), onSubmitReport = { _, reason -> submitted = reason; true },
                onClearReportFeedback = {}, onComposeReport = { _, _ -> false }
            )
        } }
        compose.onNodeWithText("Flag AI content").activateVisibleControl()
        compose.onNodeWithText("Hateful content").activateVisibleControl()
        compose.onNodeWithText("Open email draft").activateVisibleControl()
        compose.runOnIdle { assertEquals(AiContentReportReason.HATEFUL, submitted) }
    }

    @Test
    fun emailDraftIsNotClaimedAsSubmittedAndDoesNotSaveLocalFlag() {
        var drafts = 0
        var localFlags = 0
        val target = AiContentReportTarget("project", "clip", "VIDEO")
        compose.setContent { MaterialTheme {
            AiToolsPanel(
                tools = inventory, message = "", onClose = {}, reportTarget = target,
                reportState = AiContentReportUiState(), onSubmitReport = { _, _ -> localFlags++; true },
                onClearReportFeedback = {}, onComposeReport = { receivedTarget, reason ->
                    assertEquals(target, receivedTarget)
                    assertEquals(AiContentReportReason.VIOLENT, reason)
                    drafts++
                    true
                }
            )
        } }
        compose.onNodeWithText("Flag AI content").activateVisibleControl()
        compose.onNodeWithText("Violent content").activateVisibleControl()
        compose.onNodeWithText("Open email draft").activateVisibleControl()
        compose.runOnIdle { assertEquals(1, drafts); assertEquals(0, localFlags) }
        compose.onNodeWithText("delivery has not been confirmed", substring = true).assertExists()
        compose.onNodeWithText("Report / Suggestions: laghariprince2@gmail.com", substring = true).assertExists()
    }

    @Test
    fun staleReportTargetDoesNotOpenEmailOrRetargetLocalFlag() {
        var drafts = 0
        var localFlags = 0
        val target = AiContentReportTarget("project", "clip", "VIDEO")
        compose.setContent { MaterialTheme {
            AiToolsPanel(
                tools = inventory, message = "", onClose = {}, reportTarget = target,
                reportState = AiContentReportUiState(),
                onSubmitReport = { _, _ -> localFlags++; true },
                onClearReportFeedback = {},
                isReportTargetCurrent = { false },
                onComposeReport = { _, _ -> drafts++; true }
            )
        } }
        compose.onNodeWithText("Flag AI content").activateVisibleControl()
        compose.onNodeWithText("Hateful content").activateVisibleControl()
        compose.onNodeWithText("Open email draft").activateVisibleControl()

        compose.runOnIdle { assertEquals(0, drafts); assertEquals(0, localFlags) }
        compose.onNodeWithText("This report target is no longer in the current project. Nothing was submitted; reopen the report for a current clip.").assertExists()
    }

    @Test
    fun reportModalKeepsTheTargetCapturedWhenItWasOpened() {
        val originalTarget = AiContentReportTarget("project", "clip-a", "VIDEO")
        val newSelection = AiContentReportTarget("project", "clip-b", "VIDEO")
        val activeTarget = mutableStateOf(originalTarget)
        var emailTarget: AiContentReportTarget? = null
        compose.setContent { MaterialTheme {
            AiToolsPanel(
                tools = inventory, message = "", onClose = {}, reportTarget = activeTarget.value,
                reportState = AiContentReportUiState(), onSubmitReport = { _, _ -> false },
                onClearReportFeedback = {}, isReportTargetCurrent = { it == originalTarget },
                onComposeReport = { target, _ -> emailTarget = target; true }
            )
        } }
        compose.onNodeWithText("Flag AI content").activateVisibleControl()
        compose.runOnIdle { activeTarget.value = newSelection }
        compose.onNodeWithText("Hateful content").activateVisibleControl()
        compose.onNodeWithText("Open email draft").activateVisibleControl()

        compose.runOnIdle { assertEquals(originalTarget, emailTarget) }
    }

    private fun SemanticsNodeInteraction.activateVisibleControl() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        try {
            assertIsDisplayed()
        } catch (_: AssertionError) {
            performScrollTo()
            compose.waitForIdle()
        }
        assertIsDisplayed()
        performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
        compose.waitForIdle()
    }

    private fun card(id: String, title: String) = AiToolCard(
        id = id,
        title = title,
        detail = "Backend available",
        group = "Inventory",
        statusLabel = AI_PROTECTED_STATUS_LABEL,
        unavailableReason = AI_PROTECTED_BLOCKER
    )
}
