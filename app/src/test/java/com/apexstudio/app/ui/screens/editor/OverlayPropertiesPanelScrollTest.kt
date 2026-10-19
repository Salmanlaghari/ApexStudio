package com.apexstudio.app.ui.screens.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayPropertiesPanelScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun lowerOverlayControlsRemainReachableInCappedSheet() {
        compose.setContent {
            OverlayPropertiesPanel(
                clip = MediaClip(
                    id = "pip", name = "overlay.mp4", uri = "content://overlay",
                    durationMs = 10_000L, type = ClipType.OVERLAY, trackIndex = 1,
                    trimEndMs = 10_000L,
                ),
                isVideo = false,
                playheadMs = 0L,
                onOpacity = {},
                onLayout = { _, _, _, _ -> },
                onTrim = { _, _ -> },
                onBlend = {},
                onMaskShape = {},
                onBorder = { _, _ -> },
                onShadow = {},
                onFlip = { _, _ -> },
                onVolume = {},
                onTrack = {},
                onAddKeyframe = {},
                onSeekKeyframe = {},
                onRemoveKeyframe = {},
                onClose = {},
            )
        }

        compose.onNodeWithText("OVL9").performScrollTo().assertExists()
    }
}
