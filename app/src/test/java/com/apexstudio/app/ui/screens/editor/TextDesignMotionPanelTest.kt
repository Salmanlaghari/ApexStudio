package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        compose.onNodeWithText("Amber Frame").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertNotNull(chosen)
            assertEquals("TYPEWRITER", chosen!!.animationType)
            assertEquals("My editable title", chosen!!.applyTo(overlay).text)
        }
    }
}
