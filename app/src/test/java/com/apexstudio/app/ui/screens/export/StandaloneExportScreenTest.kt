package com.apexstudio.app.ui.screens.export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
class StandaloneExportScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun standaloneExportKeepsTheVerticalSettingsPage() {
        compose.setContent {
            ExportScreen(
                projectId = "standalone-test",
                onBack = {},
                onExport = {},
                vm = EditorViewModel()
            )
        }

        compose.onNodeWithText("ApexStudio • Export Settings").assertIsDisplayed()
        compose.onNodeWithText("VIDEO SCOPE").assertIsDisplayed()
        compose.onNodeWithText("RESOLUTION").assertIsDisplayed()
        compose.onNodeWithText("EXPORT SCOPE").assertDoesNotExist()
    }
}
