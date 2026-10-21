package com.apexstudio.app.ui.screens.export

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertTrue
import org.robolectric.annotation.GraphicsMode
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        val clip = com.apexstudio.app.domain.model.MediaClip("v", "Video", "file:///v.mp4", 5000L, trimEndMs = 5000L)
        vm._state.value = vm.state.value.copy(project = com.apexstudio.app.domain.model.Project("p1", "P", 5000L, clips = listOf(clip)), selectedClipId = clip.id)
        // This test exercises settings/UI only, never starts the stubbed encoder.
        vm._export.value = vm.export.value.copy(isExportEngineReady = true)
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
    @Test fun absentBackendDoesNotOfferAnEnabledVideoExportAction() {
        val vm = EditorViewModel()
        compose.setContent { ExportScreen(projectId = "p", onBack = {}, onExport = {}, vm = vm) }
        compose.onAllNodesWithText("Export engine unavailable")[0].assertIsNotEnabled()
        compose.onNodeWithContentDescription("Export").assertDoesNotExist()
    }

    @Test fun emptyScopeCannotEnableTheExportHeaderOrFooter() {
        val vm = EditorViewModel()
        vm._export.value = vm.export.value.copy(isExportEngineReady = true)
        compose.setContent { ExportScreen(projectId = "p", onBack = {}, onExport = {}, vm = vm) }
        compose.runOnIdle {
            vm._state.value = vm.state.value.copy(project = null, selectedClipId = null)
            vm._export.value = vm.export.value.copy(isExportEngineReady = true)
        }
        compose.onNodeWithText("Select a project clip to export").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Export").assertDoesNotExist()
    }

    @Test fun headerDispatchesTheSameRealControllerAsTheFooterNotNavigationOnly() {
        val vm = EditorViewModel()
        val clip = com.apexstudio.app.domain.model.MediaClip("v", "Video", "file:///v.mp4", 5000L, trimEndMs = 5000L)
        vm._state.value = vm.state.value.copy(project = com.apexstudio.app.domain.model.Project("p", "P", 5000L, clips = listOf(clip)), selectedClipId = clip.id)
        vm._export.value = vm.export.value.copy(isExportEngineReady = true)
        var navigationOnlyCalls = 0
        compose.setContent { ExportScreen(projectId = "p", onBack = {}, onExport = { navigationOnlyCalls++ }, vm = vm) }
        compose.onNodeWithContentDescription("Export").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(0, navigationOnlyCalls)
            // The real controller rejects the deliberately uninitialized test engine.
            assertTrue(!vm.export.value.error.isNullOrBlank())
        }
    }

}
