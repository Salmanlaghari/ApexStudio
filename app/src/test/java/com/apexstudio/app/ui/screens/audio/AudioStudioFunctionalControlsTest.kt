package com.apexstudio.app.ui.screens.audio

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AudioStudioFunctionalControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `transient AI placeholders are absent and sound effects opens the real library`() {
        val vm = EditorViewModel()
        compose.setContent {
            AudioStudioScreen(
                projectId = "audio-studio-test",
                onBack = {},
                onExport = {},
                vm = vm
            )
        }

        compose.onNodeWithText("AI Noise Reduction").assertDoesNotExist()
        compose.onNodeWithText("Apply Enhancement").assertDoesNotExist()
        compose.onNodeWithTag("audio-open-sfx-library").performScrollTo().performClick()

        compose.runOnIdle { assertTrue(vm.state.value.sfxLibraryPanelOpen) }
        compose.onNodeWithText("Procedural • built in • no downloads").assertIsDisplayed()
    }
}
