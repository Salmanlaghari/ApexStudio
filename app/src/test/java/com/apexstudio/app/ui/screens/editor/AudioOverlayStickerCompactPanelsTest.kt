package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.OverlayBlend
import com.apexstudio.app.presentation.state.AudioStudioState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
class AudioOverlayStickerCompactPanelsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun audioMixerKeepsClosePinnedAndQuickActionsWorkInCompactPanel() {
        var imported = false
        var closed = false
        var soloedTrackId: String? = null
        var densityScale = 1f
        compose.setContent {
            densityScale = LocalDensity.current.density
            Box(Modifier.width(375.dp).height(830.dp)) {
                AudioMixerPanel(
                    state = AudioStudioState(tracks = listOf(AudioTrack(id = "music-1", name = "Music", uri = "file://music"))),
                    muteOriginalVideo = false,
                    onMuteOriginal = {},
                    onAddTrack = { _, _, _ -> },
                    onRemoveTrack = {},
                    onVolume = { _, _ -> },
                    onMute = {},
                    onSolo = { soloedTrackId = it },
                    onTrim = { _, _, _ -> },
                    onFadeIn = { _, _ -> },
                    onFadeOut = { _, _ -> },
                    onImportLocalAudio = { imported = true },
                    onOpenRoyaltyFreeMusic = {},
                    onClose = { closed = true }
                )
            }
        }

        compose.onNodeWithTag("audio-mixer-panel-root").assertHeightAtMost280Dp(densityScale)
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onNodeWithTag("audio-mixer-panel-cards").assertIsDisplayed()
        compose.onNodeWithText("Local Audio").performClick()
        compose.runOnIdle { assertTrue(imported) }
        compose.onNodeWithTag("audio-mixer-panel-cards").performScrollToKey("music-1")
        compose.onNodeWithTag("audio-track-solo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("music-1", soloedTrackId) }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test
    fun overlayPropertiesKeepClosePinnedAndTransformControlsAccessibleInCards() {
        var closed = false
        var blend: OverlayBlend? = null
        var densityScale = 1f
        compose.setContent {
            densityScale = LocalDensity.current.density
            Box(Modifier.width(375.dp).height(830.dp)) {
                OverlayPropertiesPanel(
                    clip = MediaClip(id = "overlay-1", name = "Picture-in-picture", uri = "file://overlay", durationMs = 10_000L, trimEndMs = 10_000L),
                    isVideo = true,
                    playheadMs = 0L,
                    onOpacity = {},
                    onLayout = { _, _, _, _ -> },
                    onTrim = { _, _ -> },
                    onBlend = { blend = it },
                    onMaskShape = {},
                    onBorder = { _, _ -> },
                    onShadow = {},
                    onFlip = { _, _ -> },
                    onVolume = {},
                    onTrack = {},
                    onAddKeyframe = {},
                    onSeekKeyframe = {},
                    onRemoveKeyframe = {},
                    onClose = { closed = true }
                )
            }
        }

        compose.onNodeWithTag("overlay-properties-panel-root").assertHeightAtMost280Dp(densityScale)
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onNodeWithTag("overlay-properties-panel-cards").assertIsDisplayed()
        compose.onNodeWithText("Position X").assertIsDisplayed()
        compose.onNodeWithText("Screen").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(OverlayBlend.SCREEN, blend) }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test
    fun stickerPickerIsCompactAndHidesPropertiesUntilAStickerIsSelected() {
        var closed = false
        var densityScale = 1f
        compose.setContent {
            densityScale = LocalDensity.current.density
            Box(Modifier.width(375.dp).height(830.dp)) {
                StickerPanel(onAddSticker = { _, _, _ -> }, onClose = { closed = true })
            }
        }

        compose.onNodeWithTag("sticker-panel-root").assertHeightAtMost280Dp(densityScale)
        compose.onNodeWithText("Sticker Library").assertIsDisplayed()
        compose.onNodeWithTag("sticker-properties-strip").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertHeightAtMost280Dp(density: Float) {
    val measuredHeight = fetchSemanticsNode().boundsInRoot.height
    assertTrue("Expected panel height <= 280dp, was ${measuredHeight / density}dp", measuredHeight <= 280f * density + 1f)
}
