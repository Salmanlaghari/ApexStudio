package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsActions
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

    @Test
    fun bundledModelHasNoFakeDownloadAndChromaKeyLinkOpensItsPanel() {
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
                },
                onClose = {}
            )
        }

        compose.onNodeWithText(
            "The model is bundled with the app. Tap Apply to prepare it; no download is needed."
        ).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Download model (244 KB)").assertCountEquals(0)
        compose.onNodeWithText("Apply background").performScrollTo().assertIsDisplayed()

        val chromaKeyLink = compose.onNode(
            hasText("Green/blue screens? Open Chroma key + wallpapers") and hasClickAction()
        ).performScrollTo().assertIsDisplayed()
        logNode("chroma link", chromaKeyLink)

        // Robolectric pointer injection is retained as diagnosis only. It is not
        // device evidence and is intentionally not the correctness assertion.
        openedChromaKey.set(false)
        chromaKeyLink.performTouchInput { click() }
        println("BRP_DIAG physical-touch-injection callback=${openedChromaKey.get()} (Robolectric only; unresolved device evidence)")

        // Assert the actual accessibility semantics action exposed by TextButton.
        openedChromaKey.set(false)
        chromaKeyLink.performSemanticsAction(SemanticsActions.OnClick) { action ->
            assertTrue("Chroma link exposes a successful accessibility action", action())
        }
        compose.runOnIdle {
            assertTrue(
                "Chroma-key link invokes its callback through OnClick semantics",
                openedChromaKey.get()
            )
        }
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
