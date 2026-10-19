package com.apexstudio.app.ui.screens.export

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
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
}
