package com.apexstudio.app.ui.screens.templates

import androidx.compose.material3.MaterialTheme
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
        compose.onNodeWithText("Travel Adventure").performClick()
        compose.onNodeWithText("Sample title: CHASE THE HORIZON").assertIsDisplayed()
        compose.onNodeWithText("Title motion: Rise · subtitle: Fade in").assertIsDisplayed()
        compose.onNodeWithText("License").assertIsDisplayed()
        compose.onNodeWithText("Choose video").performClick()
        compose.runOnIdle { assertEquals("shorts_travel_adventure", appliedTemplateId) }
    }
}
