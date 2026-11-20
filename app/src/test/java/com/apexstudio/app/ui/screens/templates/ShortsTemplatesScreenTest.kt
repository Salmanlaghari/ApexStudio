package com.apexstudio.app.ui.screens.templates

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortsTemplatesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `sample look and details are visible before choosing a video`() {
        var appliedTemplateId: String? = null
        compose.setContent {
            MaterialTheme {
                ShortsTemplatesScreen(onBack = {}, onUseTemplate = { appliedTemplateId = it })
            }
        }

        compose.onNodeWithText("10 editable video starters").assertIsDisplayed()
        compose.onNodeWithText("Travel Adventure").performScrollTo().performClick()
        compose.onNodeWithText("Sample title: CHASE THE HORIZON").assertIsDisplayed()
        compose.onNodeWithText("Title motion: Rise · subtitle: Fade in").assertIsDisplayed()
        compose.onNodeWithText("License").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose video").performClick()
        compose.runOnIdle { assertEquals("shorts_travel_adventure", appliedTemplateId) }
    }

    @Test
    fun `portrait width uses tall video preview`() {
        compose.setContent {
            MaterialTheme {
                ShortsTemplatesScreen(onBack = {}, onUseTemplate = {})
            }
        }

        val previewBounds = compose.onNodeWithTag("shorts-template-preview-shorts_travel_adventure").fetchSemanticsNode().boundsInRoot
        assertTrue("portrait template preview should be taller than it is wide", previewBounds.height > previewBounds.width)
    }
}
