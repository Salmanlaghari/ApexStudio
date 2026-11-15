package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
                onOpenChromaKey = { openedChromaKey.set(true) },
                onClose = {}
            )
        }

        compose.onNodeWithText(
            "The model is bundled with the app. Tap Apply to prepare it; no download is needed."
        ).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Download model (244 KB)").assertCountEquals(0)
        compose.onNodeWithText("Apply background").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Green/blue screens? Open Chroma key + wallpapers").assertIsDisplayed()
        compose.onNodeWithText("Green/blue screens? Open Chroma key + wallpapers")
            .performScrollTo()
            .performClick()

        compose.runOnIdle { assertTrue("Chroma-key link invokes its callback", openedChromaKey.get()) }
    }
}
