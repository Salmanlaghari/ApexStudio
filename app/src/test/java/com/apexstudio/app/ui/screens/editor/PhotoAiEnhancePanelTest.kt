package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apexstudio.app.domain.model.PhotoEditSettings
import com.apexstudio.app.presentation.state.PhotoEditTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w375dp-h830dp-mdpi")
class PhotoAiEnhancePanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aiEnhanceShowsRealActionsAndHonestUnavailableOperations() {
        val settings = mutableStateOf(PhotoEditSettings())
        val autoEnhanceCalls = mutableStateOf(0)
        compose.setContent {
            PhotoEditPanel(
                photoUri = "file:///photo.jpg",
                settings = settings.value,
                tab = PhotoEditTab.AI_ENHANCE,
                onTabChange = {},
                onCropAspectPreset = {},
                onAdjust = {},
                onResetAdjust = {},
                onFilterSelect = {},
                onFilterIntensity = {},
                onRotate90 = {},
                onFlipH = {},
                onFlipV = {},
                onAutoEnhance = { autoEnhanceCalls.value++ },
                onDenoise = {},
                onSharpen = {},
                onResetAll = {},
                onClose = {}
            )
        }
        compose.onNodeWithText("AI Enhance", substring = false).assertExists()
        compose.onNodeWithTag("photo_ai_enhance_auto").assertExists().assertIsDisplayed()
            .performClick()
        compose.onNodeWithTag("photo_ai_enhance_upscale_unavailable").assertExists()
        compose.onNodeWithTag("photo_ai_enhance_denoise").assertExists()
        compose.onNodeWithTag("photo_ai_enhance_sharpen").assertExists()
        compose.onNodeWithTag("photo_ai_enhance_stabilize_unavailable").assertExists()
        compose.onNodeWithTag("photo_ai_enhance_face_crop_unavailable").assertExists()
        compose.onNodeWithText("Stabilize · Unavailable", substring = false).assertExists()
        compose.runOnIdle { assertEquals(1, autoEnhanceCalls.value) }
    }
}
