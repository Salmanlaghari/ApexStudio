package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
    private val tool = AiToolCard("SMART_TRIM", "Smart trim suggestions", "Not available yet",
        "Smart editing", AI_PROTECTED_STATUS_LABEL, AI_PROTECTED_BLOCKER)

    @Test fun nonAiVideoToolsHaveSeparateEntryWithoutUnlockingProtectedAIOrCamera() {
        var opens = 0
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "", onClose = {},
            onOpenVideoPresets = { opens++ }, onOpenVideoUpscale = {},
            reportTarget = null, reportState = AiContentReportUiState(),
            onSubmitReport = {}, onClearReportFeedback = {}) } }
        compose.onNodeWithTag("open_project_video_presets").activateVisibleControl()
        compose.runOnIdle { assertEquals(1, opens) }
        compose.onNodeWithText("Editor tools").assertExists()
        compose.onNodeWithText("Video effects & titles").assertExists()
        compose.onNodeWithText("Enhance video").assertExists()
        compose.onNodeWithContentDescription("Camera gesture controls").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(0)
    }

    @Test fun compactIconsHideFullDetailsUntilTappedAndNeverExposeProtectedSwitch() {
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "Inventory status", onClose = {}, reportTarget = null,
            reportState = AiContentReportUiState(), onSubmitReport = {}, onClearReportFeedback = {}) } }
        compose.onNodeWithText("Editor tools").assertExists()
        compose.onNodeWithText(tool.detail).assertDoesNotExist()
        compose.onNodeWithText(AI_PROTECTED_BLOCKER).assertDoesNotExist()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(0)
        compose.onNodeWithTag("ai_tool_info_SMART_TRIM").activateVisibleControl()
        // The card remains visible behind the dialog, so this title is intentionally duplicated.
        compose.onAllNodesWithText(tool.title).assertCountEquals(2)
        compose.onNodeWithText(tool.detail).assertExists()
        compose.onNodeWithText(AI_PROTECTED_BLOCKER).assertExists()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(0)
    }

    @Test fun localReviewIconInvokesActualSeparateWorkflow() {
        var reviewOpens = 0
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "", onClose = {}, onOpenLocalAnalysis = { reviewOpens++ },
            reportTarget = null, reportState = AiContentReportUiState(), onSubmitReport = {}, onClearReportFeedback = {}) } }
        compose.onNodeWithContentDescription(LOCAL_REVIEW_ACTION_LABEL).activateVisibleControl()
        compose.runOnIdle { assertEquals(1, reviewOpens) }
    }

    @Test fun safetyDetailsRemainAccessibleFromInfoIcon() {
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "Inventory status", onClose = {}, reportTarget = null,
            reportState = AiContentReportUiState(), onSubmitReport = {}, onClearReportFeedback = {}) } }
        compose.onNodeWithContentDescription("AI availability details").activateVisibleControl()
        compose.onNodeWithText("AI processing availability").assertExists()
        compose.onNodeWithText(AI_PROTECTED_BLOCKER).assertExists()
        compose.onNodeWithText("Face-framing suggestions come from sample frames", substring = true).assertExists()
        compose.onNodeWithText("Artistic style filters and 2D-to-3D video conversion are unavailable", substring = true).assertExists()
    }

    @Test fun downloadIconOpensExplicitModelChooserWithoutEnablingAI() {
        var requests = 0
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "", onClose = {}, onOpenDownloads = { requests++ },
            reportTarget = null, reportState = AiContentReportUiState(), onSubmitReport = {}, onClearReportFeedback = {}) } }
        compose.onNodeWithContentDescription("Optional AI features").activateVisibleControl()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(0)
    }

    @Test fun unavailableEmailFallsBackToClearlyLabelledLocalFlag() {
        var submitted: AiContentReportReason? = null
        val target = AiContentReportTarget("project", "clip", "VIDEO")
        compose.setContent { MaterialTheme {
            AiToolsPanel(listOf(tool), "", onClose = {}, reportTarget = target,
                reportState = AiContentReportUiState(), onSubmitReport = { submitted = it },
                onClearReportFeedback = {}, onComposeReport = { _, _ -> false })
        } }
        compose.onNodeWithText("Flag AI content").activateVisibleControl()
        compose.onNodeWithText("Hateful content").activateVisibleControl()
        compose.onNodeWithText("Open email draft").activateVisibleControl()
        compose.runOnIdle { assertEquals(AiContentReportReason.HATEFUL, submitted) }
    }

    @Test fun emailDraftIsNotClaimedAsSubmittedAndDoesNotSaveLocalFlag() {
        var drafts = 0
        var localFlags = 0
        val target = AiContentReportTarget("project", "clip", "VIDEO")
        compose.setContent { MaterialTheme {
            AiToolsPanel(listOf(tool), "", onClose = {}, reportTarget = target,
                reportState = AiContentReportUiState(), onSubmitReport = { localFlags++ },
                onClearReportFeedback = {}, onComposeReport = { receivedTarget, reason ->
                    assertEquals(target, receivedTarget)
                    assertEquals(AiContentReportReason.VIOLENT, reason)
                    drafts++
                    true
                })
        } }
        compose.onNodeWithText("Flag AI content").activateVisibleControl()
        compose.onNodeWithText("Violent content").activateVisibleControl()
        compose.onNodeWithText("Open email draft").activateVisibleControl()
        compose.runOnIdle { assertEquals(1, drafts); assertEquals(0, localFlags) }
        compose.onNodeWithText("delivery has not been confirmed", substring = true).assertExists()
        compose.onNodeWithText("Report / Suggestions: laghariprince2@gmail.com", substring = true).assertExists()
    }

    @Test fun upscaleHasAnActualActionWithoutPromptOrImageInput() {
        var opens = 0
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "", onClose = {},
            onOpenVideoUpscale = { opens++ }, reportTarget = null, reportState = AiContentReportUiState(),
            onSubmitReport = {}, onClearReportFeedback = {}) } }
        compose.onNodeWithTag("open_project_video_upscale").activateVisibleControl()
        compose.runOnIdle { assertEquals(1, opens) }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(0)
    }

    @Test fun supportedCardsOpenOnlyTheirExistingVideoWorkflowAndKeepInfoAvailable() {
        var analysis = 0
        var upscale = 0
        var captions = 0
        var background = 0
        val cards = listOf(
            tool,
            AiToolCard("ONNX_UPSCALING", "AI upscale", "Backend detected", "Advanced", AI_PROTECTED_STATUS_LABEL, "Details"),
            AiToolCard("SUBTITLES", "Whisper captions", "Backend detected", "Subtitles", AI_PROTECTED_STATUS_LABEL, "Details"),
            AiToolCard("BACKGROUND_REPLACE", "Background replacement", "Backend detected", "Smart editing", AI_PROTECTED_STATUS_LABEL, "Details")
        )
        compose.setContent { MaterialTheme { AiToolsPanel(cards, "", onClose = {},
            onOpenLocalAnalysis = { analysis++ }, onOpenVideoUpscale = { upscale++ },
            onOpenCaptions = { captions++ }, onOpenBackgroundReplace = { background++ },
            reportTarget = null, reportState = AiContentReportUiState(), onSubmitReport = {}, onClearReportFeedback = {}) } }
        compose.onNodeWithTag("protected_ai_card_SMART_TRIM").activateVisibleControl()
        compose.onNodeWithTag("protected_ai_card_ONNX_UPSCALING").activateVisibleControl()
        compose.onNodeWithTag("protected_ai_card_SUBTITLES").activateVisibleControl()
        compose.onNodeWithTag("protected_ai_card_BACKGROUND_REPLACE").activateVisibleControl()
        compose.runOnIdle { assertEquals(1, analysis); assertEquals(1, upscale); assertEquals(1, captions); assertEquals(1, background) }
        compose.onNodeWithTag("ai_tool_info_SMART_TRIM").activateVisibleControl()
        compose.onNodeWithText(tool.unavailableReason).assertExists()
    }

    @Test fun missingCallbackAndUnsupportedToolStayInformational() {
        var actions = 0
        val unsupported = AiToolCard("FACE_AWARE_REFRAME", "Auto reframe", "Face backend detected", "Smart editing",
            AI_PROTECTED_STATUS_LABEL, "Full-clip application is not release-ready.")
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(unsupported), "", onClose = {},
            onOpenLocalAnalysis = { actions++ }, reportTarget = null, reportState = AiContentReportUiState(),
            onSubmitReport = {}, onClearReportFeedback = {}) } }
        compose.onNodeWithTag("protected_ai_card_FACE_AWARE_REFRAME").activateVisibleControl()
        compose.onNodeWithText("Full-clip application is not release-ready.").assertExists()
        compose.runOnIdle { assertEquals(0, actions) }
    }

    private fun SemanticsNodeInteraction.activateVisibleControl() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        try {
            assertIsDisplayed()
        } catch (_: AssertionError) {
            // Sheet cards may be below the fold; visible dialog actions have no scroll ancestor.
            performScrollTo()
            compose.waitForIdle()
        }
        assertIsDisplayed()
        performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
        compose.waitForIdle()
    }
}
