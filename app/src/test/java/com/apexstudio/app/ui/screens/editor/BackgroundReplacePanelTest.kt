package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.viewmodel.BackgroundPhase
import com.apexstudio.app.presentation.viewmodel.BackgroundUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
class BackgroundReplacePanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bundledModelHasNoFakeDownloadAndChromaKeyLinkOpensItsPanel() {
        val clip = MediaClip(
            id = "video",
            name = "Video",
            uri = "file:///video.mp4",
            durationMs = 5_000L,
            trimEndMs = 5_000L
        )
        val openedChromaKey = AtomicBoolean(false)

        compose.setContent {
            BackgroundReplacePanel(
                clip = clip,
                ui = BackgroundUiState(phase = BackgroundPhase.NEED_DOWNLOAD),
                onApply = {},
                onRestoreOriginal = {},
                onCancel = {},
                onPickImage = {},
                onOpenChromaKey = {
                    println("BRP_DIAG callback onOpenChromaKey invoked thread=${Thread.currentThread().name}")
                    openedChromaKey.set(true)
                    println("BRP_DIAG callback onOpenChromaKey recorded=${openedChromaKey.get()}")
                },
                onClose = {}
            )
        }

        logSemantics("before assertions")
        compose.onNodeWithText(
            "The model is bundled with the app. Tap Apply to prepare it; no download is needed."
        ).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Download model (244 KB)").assertCountEquals(0)
        val applyButton = compose.onNodeWithText("Apply background")
        applyButton.performScrollTo().assertIsDisplayed()
        logSemantics("after apply scroll")
        compose.onNodeWithText("Green/blue screens? Open Chroma key + wallpapers").assertIsDisplayed()
        val chromaKeyLink = compose.onNode(
            hasText("Green/blue screens? Open Chroma key + wallpapers") and hasClickAction()
        )
        logNode("chroma link", chromaKeyLink)
        logNode("apply button", applyButton)
        println("BRP_DIAG callback flag before click=${openedChromaKey.get()}")
        compose.onNodeWithTag("background-chroma-key-link")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performTouchInput { click() }
        println("BRP_DIAG callback flag immediately after click=${openedChromaKey.get()}")

        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        println("BRP_DIAG callback flag after advanceTimeBy(300)+waitForIdle=${openedChromaKey.get()}")
        logSemantics("after click and clock advance")

        compose.runOnIdle { assertTrue("Chroma-key link invokes its callback", openedChromaKey.get()) }
    }

    private fun logSemantics(stage: String) {
        println("BRP_DIAG stage=$stage")
        logNode("root merged", compose.onRoot(useUnmergedTree = false))
        logNode("root unmerged", compose.onRoot(useUnmergedTree = true))
    }

    private fun logNode(label: String, node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val description = try {
            val semantics = node.fetchSemanticsNode()
            "boundsInRoot=${semantics.boundsInRoot} config=${semantics.config} children=${semantics.children.size}"
        } catch (error: Throwable) {
            "unavailable=${error::class.simpleName}: ${error.message}"
        }
        println("BRP_DIAG node=$label {$description}")
    }
}
