package com.apexstudio.app.ui.screens.export

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.setSelectedResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportScreenCancelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun runningExportOffersAWorkingCancelAction() {
        val vm = EditorViewModel()
        vm._export.value = vm.export.value.copy(isExporting = true, progress = 0f)
        compose.setContent { ExportScreen(projectId = "p1", onBack = {}, onExport = {}, vm = vm) }
        compose.onNodeWithText("Cancel • Preparing export…").performClick()
        compose.runOnIdle { assertFalse(vm.export.value.isExporting) }
    }

    @Test fun resolutionSelectionKeepsEditorAndExportSettingsInSync() {
        val vm = EditorViewModel()
        compose.setContent { ExportScreen(projectId = "p1", onBack = {}, onExport = {}, vm = vm) }

        compose.onNodeWithText("Export 1080P • 16:9 @ 60fps").assertIsDisplayed()
        compose.onNodeWithText("Export Complete").assertDoesNotExist()
        compose.onNodeWithText("4K").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("4K", vm.state.value.selectedResolution)
            assertEquals("4K", vm.export.value.settings.resolution)
        }
        compose.onNodeWithText("Export 4K • 16:9 @ 60fps").assertIsDisplayed()
    }

    @Test fun editorHeaderResolutionSelectionUpdatesExportSettings() {
        val vm = EditorViewModel()
        vm.setSelectedResolution("1440P")
        assertEquals("1440P", vm.state.value.selectedResolution)
        assertEquals("1440P", vm.export.value.settings.resolution)
    }
}
