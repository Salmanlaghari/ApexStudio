package com.apexstudio.app.ui.screens.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun narrowDashboardStacksEditorEntriesInsteadOfCompressingThemSideBySide() {
        compose.setContent {
            Box(Modifier.width(320.dp)) {
                DashboardEditorEntries(onImageEditing = {}, onVideoEditor = {})
            }
        }
        val imageTop = compose.onNodeWithTag("dashboard-image-editing-session").fetchSemanticsNode().boundsInRoot.top
        val videoTop = compose.onNodeWithTag("dashboard-video-editor").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Mobile editor cards should be stacked", videoTop > imageTop)
    }

    @Test
    fun largeFontsStackEntriesEvenOnWiderPhones() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.8f)
            ) {
                Box(Modifier.width(420.dp)) {
                    DashboardEditorEntries(onImageEditing = {}, onVideoEditor = {})
                }
            }
        }
        val image = compose.onNodeWithTag("dashboard-image-editing-session").fetchSemanticsNode().boundsInRoot
        val video = compose.onNodeWithTag("dashboard-video-editor").fetchSemanticsNode().boundsInRoot
        assertTrue(video.top >= image.bottom)
    }

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
