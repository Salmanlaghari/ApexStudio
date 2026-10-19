package com.apexstudio.app.ui.screens.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

    @Test fun compactIconsHideFullDetailsUntilTappedAndNeverExposeProtectedSwitch() {
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "Inventory status", onClose = {}) } }
        compose.onNodeWithText("Protected AI paused · safety approval required").assertExists()
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
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "", onClose = {}, onOpenLocalAnalysis = { reviewOpens++ }) } }
        compose.onNodeWithContentDescription(LOCAL_REVIEW_ACTION_LABEL).activateVisibleControl()
        compose.runOnIdle { assertEquals(1, reviewOpens) }
    }

    @Test fun safetyDetailsRemainAccessibleFromInfoIcon() {
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "Inventory status", onClose = {}) } }
        compose.onNodeWithContentDescription("AI availability details").activateVisibleControl()
        compose.onNodeWithText("Protected AI is unavailable").assertExists()
        compose.onNodeWithText(AI_PROTECTED_BLOCKER).assertExists()
    }

    @Test fun downloadIconOpensExplicitModelChooserWithoutEnablingAI() {
        var requests = 0
        compose.setContent { MaterialTheme { AiToolsPanel(listOf(tool), "", onClose = {}, onOpenDownloads = { requests++ }) } }
        compose.onNodeWithContentDescription("Download optional AI models").activateVisibleControl()
        compose.runOnIdle { assertEquals(1, requests) }
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
