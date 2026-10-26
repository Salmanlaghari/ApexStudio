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

    @Test fun aiEnhanceShowsOnDevicePhotoActionsAndTruthfulAutoLevelLimits() {
        val settings = mutableStateOf(PhotoEditSettings())
        val autoEnhanceCalls = mutableStateOf(0)
        val upscaleCalls = mutableStateOf(0)
        val faceCropCalls = mutableStateOf(0)
        val stabilizeEnabled = mutableStateOf(false)
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
                onPhotoUpscale = { upscaleCalls.value++ },
                onFaceCrop = { faceCropCalls.value++ },
                onStabilize = { stabilizeEnabled.value = it },
                onResetAll = {},
                onClose = {}
            )
        }
        compose.onNodeWithText("AI Enhance", substring = false).assertExists()
        compose.onNodeWithTag("photo_ai_enhance_auto").assertExists().assertIsDisplayed()
            .performClick()
        compose.onNodeWithTag("photo_ai_enhance_upscale").assertExists().assertIsDisplayed().performClick()
        compose.onNodeWithTag("photo_ai_enhance_denoise").assertExists()
        compose.onNodeWithTag("photo_ai_enhance_sharpen").assertExists()
        compose.onNodeWithTag("photo_ai_enhance_stabilize").assertExists().assertIsDisplayed().performClick()
        compose.onNodeWithTag("photo_ai_enhance_face_crop").assertExists().assertIsDisplayed().performClick()
        compose.onNodeWithText("does not remove motion blur", substring = true).assertExists()
        compose.runOnIdle {
            assertEquals(1, autoEnhanceCalls.value)
            assertEquals(1, upscaleCalls.value)
            assertEquals(1, faceCropCalls.value)
            assertEquals(true, stabilizeEnabled.value)
        }
    }
}
