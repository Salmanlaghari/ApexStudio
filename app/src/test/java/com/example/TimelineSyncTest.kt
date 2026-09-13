package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.EditorScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class TimelineSyncTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `verify timeline tracks render and Cover label is completely absent`() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        EditorScreen()
      }
    }

    // 1. Verify "Cover" label does NOT exist anywhere in the timeline or UI
    composeTestRule.onAllNodesWithText("Cover", substring = false).assertCountEquals(0)

    // 2. Verify all 4 synchronized timeline tracks are present
    composeTestRule.onNodeWithTag("track_video").assertIsDisplayed()
    composeTestRule.onNodeWithTag("track_fx").assertIsDisplayed()
    composeTestRule.onNodeWithTag("track_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("track_audio").assertIsDisplayed()

    // 3. Verify Playhead and Transport controls
    composeTestRule.onNodeWithTag("timeline_playhead").assertIsDisplayed()
    composeTestRule.onNodeWithTag("play_pause_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("preview_monitor").assertIsDisplayed()

    // 4. Test clicking play/pause
    composeTestRule.onNodeWithTag("play_pause_button").performClick()
  }

  @Test
  fun `verify switching tools displays authentic panels`() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        EditorScreen()
      }
    }

    // Tab 1: 3D Chroma Key
    composeTestRule.onNodeWithTag("tab_chroma_key").performClick()
    composeTestRule.onNodeWithTag("chroma_similarity_slider").assertIsDisplayed()

    // Tab 2: Effects
    composeTestRule.onNodeWithTag("tab_effects").performClick()
    composeTestRule.onNodeWithTag("fx_card_rgb_jitter").assertIsDisplayed()
    composeTestRule.onNodeWithTag("fx_card_pixel_sort").assertIsDisplayed()
    composeTestRule.onNodeWithTag("fx_card_datamosh").assertIsDisplayed()

    // Tab 3: Adjust (12-factor Color Grading)
    composeTestRule.onNodeWithTag("tab_adjust").performClick()
    composeTestRule.onNodeWithTag("adjust_presets_row").assertExists()
    composeTestRule.onNodeWithTag("preset_preset_teal_orange").assertExists()
    composeTestRule.onNodeWithTag("adjust_brightness_slider").assertExists()
    composeTestRule.onNodeWithTag("adjust_contrast_slider").assertExists()

    // Tab 4: Audio Workstation
    composeTestRule.onNodeWithTag("tab_audio").performClick()
    composeTestRule.onNodeWithTag("upload_audio_button").assertExists()
    composeTestRule.onNodeWithTag("audio_volume_slider").assertExists()
    composeTestRule.onNodeWithTag("track_card_rf_neon_horizon").assertExists()
  }
}
