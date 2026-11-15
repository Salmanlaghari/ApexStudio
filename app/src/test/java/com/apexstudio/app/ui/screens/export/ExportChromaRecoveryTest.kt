package com.apexstudio.app.ui.screens.export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
class ExportChromaRecoveryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pendingChromaErrorOffersWorkingRouteToApplyPanel() {
        val vm = EditorViewModel()
        vm._export.value = vm.export.value.copy(
            isExportEngineReady = true,
            error = "Chroma key is pending. Apply it in Chroma key + wallpapers before exporting."
        )
        var routedBackToEditor = false

        compose.setContent {
            ExportScreen(
                projectId = "chroma-recovery-test",
                onBack = {},
                onExport = {},
                vm = vm,
                onOpenChromaKey = { routedBackToEditor = true }
            )
        }

        compose.onNodeWithText("Open Chroma key + wallpapers")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        compose.runOnIdle {
            assertTrue(vm.state.value.chromaKeyPanelOpen)
            assertTrue(routedBackToEditor)
        }
    }
}
