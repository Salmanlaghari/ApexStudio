package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun logoCarriesProjectIdentitySemanticsAndKeepsBackAccessible() {
        var backCount = 0
        compose.setContent {
            TopAppBarSection(title = "My project", projectId = "project-123", onBack = { backCount++ })
        }
        val brand = compose.onNodeWithContentDescription("ApexStudio build identity. Project project-123")
        brand.assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("My project").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("project-123").fetchSemanticsNodes().isEmpty())
        brand.performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, backCount) }
    }

    @Test fun logoTapOpensBuildIdentityAndHeaderHasNoFitChipOrProjectTitleBar() {
        compose.setContent { TopAppBarSection(title = "A long project name that must not become a title bar") }
        compose.onNodeWithContentDescription("ApexStudio build identity. Project A long project name that must not become a title bar")
            .performClick()
        compose.onNodeWithText("Installed build").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        assertTrue(compose.onAllNodesWithText("FIT").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("COVER").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Export").assertIsDisplayed()
        compose.onNodeWithText("1080P").assertIsDisplayed()
    }

    @Test fun resolutionMenuRetainsResolutionAndPreviewFitActionsAndExportIsReal() {
        val calls = mutableListOf<String>()
        compose.setContent {
            TopAppBarSection(
                isCoverMode = false,
                onToggleCoverMode = { calls += "fit" },
                onSelectResolution = { calls += "resolution:$it" },
                onExport = { calls += "export" }
            )
        }
        compose.onNodeWithContentDescription("Resolution: 1080P; preview fit").performClick()
        compose.onNodeWithText("Preview: Fit").performClick()
        compose.onNodeWithContentDescription("Resolution: 1080P; preview fit").performClick()
        compose.onNodeWithText("4K").performClick()
        compose.onNodeWithContentDescription("Export").performClick()
        compose.runOnIdle { assertEquals(listOf("fit", "resolution:4K", "export"), calls) }
    }
}
