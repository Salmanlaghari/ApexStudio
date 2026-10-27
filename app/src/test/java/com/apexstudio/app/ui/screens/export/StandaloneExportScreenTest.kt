package com.apexstudio.app.ui.screens.export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    @Test fun defaultStandaloneRouteInitializesTheRealExportEngine() {
        compose.setContent {
            ExportScreen(
                projectId = "export-engine-context-regression",
                onBack = {},
                onExport = {}
            )
        }

        compose.onNodeWithText("Engine unavailable").assertDoesNotExist()
        compose.onNodeWithText("Export engine unavailable").assertDoesNotExist()
    }

    @Test fun standaloneExportKeepsTheVerticalSettingsPage() {
        compose.setContent {
            ExportScreen(
                projectId = "standalone-test",
                onBack = {},
                onExport = {},
                vm = EditorViewModel()
            )
        }

        compose.onNodeWithText("Apex").assertIsDisplayed()
        compose.onNodeWithText("Studio").assertIsDisplayed()
        compose.onNodeWithText("AI VIDEO EDITOR").assertIsDisplayed()
        compose.onNodeWithText("EXPORT SETTINGS").assertIsDisplayed()
        compose.onNodeWithText("More export settings").performScrollTo().performClick()
        compose.onNodeWithText("Hide advanced settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("VIDEO SCOPE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("EXPORT SCOPE").assertDoesNotExist()
    }
}
