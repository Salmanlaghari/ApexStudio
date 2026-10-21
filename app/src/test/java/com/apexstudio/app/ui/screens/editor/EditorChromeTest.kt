package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun projectIdentityAndBackNavigationAreVisibleOutsidePreview() {
        var backCount = 0
        compose.setContent {
            TopAppBarSection(title = "My project", projectId = "project-123", onBack = { backCount++ })
        }
        compose.onNodeWithText("My project").assertIsDisplayed()
        compose.onNodeWithText("project-123").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, backCount) }
    }

    @Test fun longProjectNameDoesNotHideBackOrExport() {
        compose.setContent {
            TopAppBarSection(title = "A long project name that must leave both controls accessible")
        }
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithText("Export").assertIsDisplayed()
        compose.onNodeWithText("FIT").assertIsDisplayed()
        compose.onNodeWithText("1080P").assertIsDisplayed()
    }

    @Test fun headerControlsInvokeTheirRealCallbacks() {
        val calls = mutableListOf<String>()
        compose.setContent {
            TopAppBarSection(
                isCoverMode = false,
                onToggleCoverMode = { calls += "fit" },
                onSelectResolution = { calls += "resolution:$it" },
                onExport = { calls += "export" }
            )
        }
        compose.onNodeWithContentDescription("Switch to cover").performClick()
        compose.onNodeWithContentDescription("Resolution: 1080P").performClick()
        compose.onNodeWithText("4K").performClick()
        compose.onNodeWithContentDescription("Export").performClick()
        compose.runOnIdle { assertEquals(listOf("fit", "resolution:4K", "export"), calls) }
    }
}
