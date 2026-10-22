package com.apexstudio.app.ui.screens.export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
class EmbeddedExportScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun embeddedModeShowsControlGroupsAndAccessibleCloseWithoutStandaloneBar() {
        val vm = EditorViewModel()
        var backCalls = 0
        compose.setContent {
            ExportScreen(
                projectId = "embedded-test",
                onBack = { backCalls++ },
                onExport = {},
                vm = vm,
                embedded = true
            )
        }

        compose.onNodeWithText("EXPORT SCOPE").assertIsDisplayed()
        compose.onNodeWithText("VIDEO OUTPUT").assertExists()
        compose.onNodeWithText("ENCODING").assertExists()
        compose.onNodeWithText("SIZE & PROGRESS").assertExists()
        compose.onNodeWithText("EXPORT & SHARE").assertExists()
        compose.onNodeWithContentDescription("Close export controls").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, backCalls) }
        compose.onNodeWithText("ApexStudio • Export Settings").assertDoesNotExist()
    }
}
