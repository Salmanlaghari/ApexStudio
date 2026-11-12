package com.apexstudio.app.ui.screens.export

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportCompletionUxTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun progressRingShowsReadyPreparingAndOneToOneHundredPercent() {
        var isExporting by mutableStateOf(false)
        var progress by mutableFloatStateOf(0f)
        var completed by mutableStateOf(false)
        compose.setContent {
            ExportProjectPreviewCard(
                project = null,
                isExporting = isExporting,
                progress = progress,
                completed = completed
            )
        }

        compose.onNodeWithTag("export-progress-ring").assertIsDisplayed()
        compose.onNodeWithText("Ready").assertIsDisplayed()
        compose.onNodeWithText("0%").assertDoesNotExist()

        compose.runOnIdle { isExporting = true }
        compose.onNodeWithText("Preparing").assertIsDisplayed()
        compose.onNodeWithText("0%").assertDoesNotExist()
        compose.runOnIdle { progress = 0.001f }
        compose.onNodeWithText("1%").assertIsDisplayed()
        compose.runOnIdle { progress = 0.28f }
        compose.onNodeWithText("28%").assertIsDisplayed()
        compose.runOnIdle {
            progress = 1f
            isExporting = false
            completed = true
        }
        compose.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test
    fun failedOrCancelledExportDoesNotPromptAndSuccessfulRunsPromptOnlyOnceEach() {
        val vm = EditorViewModel()
        compose.setContent {
            ExportScreen(projectId = "completion-prompt", onBack = {}, onExport = {}, vm = vm)
        }

        compose.onNodeWithTag("export-completion-dialog").assertDoesNotExist()
        compose.runOnIdle {
            vm._export.value = vm.export.value.copy(isExporting = true, progress = 0f, outputUri = null, error = null)
        }
        compose.runOnIdle {
            vm._export.value = vm.export.value.copy(isExporting = false, outputUri = null, error = "Synthetic failure")
        }
        compose.onNodeWithTag("export-completion-dialog").assertDoesNotExist()

        compose.runOnIdle {
            vm._export.value = vm.export.value.copy(isExporting = true, progress = 0f, outputUri = null, error = null)
        }
        compose.runOnIdle {
            vm._export.value = vm.export.value.copy(isExporting = false, outputUri = null, error = null)
        }
        compose.onNodeWithTag("export-completion-dialog").assertDoesNotExist()

        compose.runOnIdle {
            vm._export.value = vm.export.value.copy(isExporting = true, progress = 0.4f, outputUri = null, error = null)
        }
        compose.runOnIdle {
            vm._export.value = vm.export.value.copy(
                isExporting = false,
                progress = 1f,
                outputUri = "file:///tmp/apexstudio-completed-test.mp4",
                error = null
            )
        }
        compose.onNodeWithTag("export-completion-dialog").assertIsDisplayed()
        compose.onNodeWithText(
            "It is saved in ApexStudio's app-specific export folder on this device. Closing this dialog will not delete it."
        ).assertIsDisplayed()
        compose.onNodeWithText("Share export").assertIsDisplayed()
        compose.onNodeWithText("Premium info").performClick()
        compose.onNodeWithTag("export-premium-info-dialog").assertIsDisplayed()
        compose.onNodeWithText("cannot start a purchase", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("export-completion-dialog").assertDoesNotExist()

        compose.runOnIdle {
            vm._export.value = vm.export.value.copy(outputUri = "file:///tmp/duplicate-terminal.mp4")
        }
        compose.onNodeWithTag("export-completion-dialog").assertDoesNotExist()

        compose.runOnIdle {
            vm._export.value = vm.export.value.copy(isExporting = true, progress = 0f, outputUri = null, error = null)
        }
        compose.runOnIdle {
            vm._export.value = vm.export.value.copy(
                isExporting = false,
                progress = 1f,
                outputUri = "file:///tmp/apexstudio-second-test.mp4",
                error = null
            )
        }
        compose.onNodeWithTag("export-completion-dialog").assertIsDisplayed()
    }
}
