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
    private val tool = AiToolCard("SMART_TRIM", "Smart trim suggestions", "Local backend detected: Signal analysis",
        "Smart editing", AI_PROTECTED_STATUS_LABEL, AI_PROTECTED_BLOCKER)

    @Test fun nonAiVideoToolsHaveSeparateEntryWithoutUnlockingProtectedAIOrCamera() {
        var opens = 0
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "", onClose = {},
            onOpenVideoPresets = { opens++ }, reportTarget = null, reportState = AiContentReportUiState(),
            onSubmitReport = {}, onClearReportFeedback = {}) } }
        compose.onNodeWithTag("open_project_video_presets").activateVisibleControl()
        compose.runOnIdle { assertEquals(1, opens) }
        compose.onNodeWithText("Local video editing tools").assertExists()
        compose.onNodeWithText("Enhance, filters, templates + animated titles · not ML AI").assertExists()
        compose.onNodeWithContentDescription("Camera gesture controls").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(0)
    }

    @Test fun compactIconsHideFullDetailsUntilTappedAndNeverExposeProtectedSwitch() {
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "Inventory status", onClose = {}, reportTarget = null,
            reportState = AiContentReportUiState(), onSubmitReport = {}, onClearReportFeedback = {}) } }
        compose.onNodeWithText("Local video editing tools").assertExists()
        compose.onNodeWithText(tool.detail).assertDoesNotExist()
        compose.onNodeWithText(AI_PROTECTED_BLOCKER).assertDoesNotExist()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(0)
        compose.onNodeWithTag("protected_ai_card_SMART_TRIM").activateVisibleControl()
        compose.onNodeWithText(tool.title).assertExists()
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
        compose.onNodeWithText("Video-only processing availability").assertExists()
        compose.onNodeWithText(AI_PROTECTED_BLOCKER).assertExists()
        compose.onNodeWithText("not a blanket video exemption", substring = true).assertExists()
        compose.onNodeWithText("without prompts or new media generation", substring = true).assertExists()
        compose.onNodeWithText("does not waive privacy or restricted-content policies", substring = true).assertExists()
        compose.onNodeWithText("Face-crop application remains unavailable", substring = true).assertExists()
    }

    @Test fun downloadIconOpensExplicitModelChooserWithoutEnablingAI() {
        var requests = 0
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "", onClose = {}, onOpenDownloads = { requests++ },
            reportTarget = null, reportState = AiContentReportUiState(), onSubmitReport = {}, onClearReportFeedback = {}) } }
        compose.onNodeWithContentDescription("Download optional AI models").activateVisibleControl()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(0)
    }

    @Test fun reportReasonSelectionInvokesRequiredSubmitCallback() {
        var submitted: AiContentReportReason? = null
        val target = AiContentReportTarget("project", "clip", "VIDEO")
        compose.setContent { MaterialTheme {
            AiToolsPanel(listOf(tool), "", onClose = {}, reportTarget = target,
                reportState = AiContentReportUiState(), onSubmitReport = { submitted = it }, onClearReportFeedback = {})
        } }
        compose.onNodeWithText("Flag AI content").activateVisibleControl()
        compose.onNodeWithText("Hateful content").activateVisibleControl()
        compose.onNodeWithText("Save on device").activateVisibleControl()
        compose.runOnIdle { assertEquals(AiContentReportReason.HATEFUL, submitted) }
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

    private fun SemanticsNodeInteraction.activateVisibleControl() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertIsDisplayed()
        performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
        compose.waitForIdle()
    }
}
