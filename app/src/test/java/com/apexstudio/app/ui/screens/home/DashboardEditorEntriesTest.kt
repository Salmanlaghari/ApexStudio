package com.apexstudio.app.ui.screens.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
class DashboardEditorEntriesTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun imageAndVideoEntriesAreVisibleAndInvokeTheirEditorActions() {
        var imageActionCount = 0
        var videoActionCount = 0
        compose.setContent {
            DashboardEditorEntries(
                onImageEditing = { imageActionCount++ },
                onVideoEditor = { videoActionCount++ }
            )
        }

        compose.onNodeWithText("Image Editing Session").assertIsDisplayed().performClick()
        compose.onNodeWithText("Video Editor").assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertEquals(1, imageActionCount)
            assertEquals(1, videoActionCount)
        }
    }
}
