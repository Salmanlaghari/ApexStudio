package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.apexstudio.app.domain.model.PhotoCanvasSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoCanvasPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun canvasControlsChangeRealSettingsAndOriginalResetsDimensions() {
        val settings = mutableStateOf(PhotoCanvasSettings())
        compose.setContent { PhotoCanvasPanel(settings.value) { settings.value = it } }
        compose.onNodeWithTag("photo-canvas-ratio-1:1").performClick()
        compose.onNodeWithText("Fill", substring = false).performClick()
        compose.onNodeWithText("White", substring = false).performClick()
        compose.runOnIdle {
            assertEquals("1:1", settings.value.aspectPreset)
            assertTrue(settings.value.fill)
            assertEquals(0xFFFFFFFFL, settings.value.backgroundArgb)
        }
        compose.onNodeWithTag("photo-canvas-ratio-original").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(settings.value.isOriginal) }
        compose.onNodeWithText("Background", substring = false).assertDoesNotExist()
    }
}
