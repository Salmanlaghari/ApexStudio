package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.apexstudio.app.data.text.TextDesignMotionPreset
import com.apexstudio.app.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextDesignMotionPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `user searches a design motion combination and selects actual preset`() {
        var chosen: TextDesignMotionPreset? = null
        val overlay = TextOverlay(id = "text", text = "My editable title")
        compose.setContent {
            TextPanel(
                overlays = listOf(overlay), selectedId = overlay.id,
                onAdd = {}, onSelect = {}, onTextChange = {}, onColorChange = {},
                onBgChange = {}, onSizeChange = {}, onDelete = {}, onClose = {},
                onApplyDesignMotion = { chosen = it }, initialTab = TextPanelTab.PRESETS
            )
        }
        compose.onNode(hasSetTextAction())
            .performScrollTo()
            .performTextInput("amber typewriter")
        compose.onNodeWithTag("text-design-motion-results")
            .performScrollTo()
            .performScrollToNode(hasText("Amber Frame · Typewriter"))
        compose.onNodeWithText("Amber Frame · Typewriter").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertNotNull(chosen)
            assertEquals("TYPEWRITER", chosen!!.animationType)
            assertEquals("My editable title", chosen!!.applyTo(overlay).text)
        }
    }

    @Test fun `empty text styles entry shows real preview cards and selects a preset`() {
        var chosen: TextDesignMotionPreset? = null
        compose.setContent {
            TextPanel(
                overlays = emptyList(), selectedId = null,
                onAdd = {}, onSelect = {}, onTextChange = {}, onColorChange = {},
                onBgChange = {}, onSizeChange = {}, onDelete = {}, onClose = {},
                onApplyDesignMotion = { chosen = it }, initialTab = TextPanelTab.PRESETS
            )
        }

        compose.onNodeWithTag("text-empty-style-gallery").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("text-empty-style-tdm_0_fade_in").performScrollTo().performClick()
        compose.runOnIdle {
            assertNotNull(chosen)
            assertEquals("Amber Frame", chosen!!.designName)
            assertEquals("Fade In", chosen!!.motionName)
        }
    }
}
