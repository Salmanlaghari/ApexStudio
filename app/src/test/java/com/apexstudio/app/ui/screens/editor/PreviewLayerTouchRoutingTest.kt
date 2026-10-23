package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreviewLayerTouchRoutingTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = MediaClip("pip", "Overlay", "file:///missing.png", 5000,
        trimEndMs = 5000, type = ClipType.OVERLAY, trackIndex = 1,
        pipX = 0.7f, pipY = 0.65f, pipScale = 0.5f)

    private fun assertOverlayCanMove(stickers: List<StickerOverlay>) {
        var current by mutableStateOf(overlay)
        var completed = 0
        compose.setContent {
            Box(Modifier.size(320.dp, 180.dp)) {
                PipOverlayCanvas(
                    overlayClips = listOf(current), selectedOverlayClipId = current.id,
                    currentTimeMs = 0, overlayPlayer = null, overlayPlayerClipId = null,
                    overlayIsVideo = mapOf(current.id to false),
                    onSelectOverlayClip = {},
                    onMoveOverlayClip = { _, dx, dy, _ -> current = current.copy(pipX = current.pipX + dx, pipY = current.pipY + dy) },
                    onScaleOverlayClip = { _, _, _ -> }, onRotateOverlayClip = { _, _, _ -> },
                    onOverlayGestureEnd = { completed++ }, onRemoveOverlayClip = {},
                    modifier = Modifier.fillMaxSize()
                )
                ScreenWordingObjects(
                    stickers = stickers, textOverlays = emptyList(), coverText = null, coverTextStyle = null,
                    selectedTextOverlayId = null, currentTimeMs = 0, isPlaying = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        compose.onNodeWithTag("pip-overlay-item-pip").performTouchInput {
            swipe(center, center + Offset(-50f, -20f), 400)
        }
        compose.runOnIdle {
            assertTrue("Transparent wording/sticker canvas blocked PiP drag", current.pipX < overlay.pipX)
            assertTrue(current.pipY < overlay.pipY)
            assertEquals(1, completed)
        }
    }

    @Test fun emptyStickerCanvasDoesNotBlockOverlayDrag() = assertOverlayCanMove(emptyList())

    @Test fun activeStickerElsewhereDoesNotBlockOverlayDrag() = assertOverlayCanMove(listOf(
        StickerOverlay(id = "sticker", x = 0.1f, y = 0.1f, startMs = 0, endMs = 5000)
    ))

    @Test fun inactiveStickerDoesNotBlockOverlayDrag() = assertOverlayCanMove(listOf(
        StickerOverlay(id = "sticker", startMs = 1000, endMs = 5000)
    ))

    @Test fun individualStickerStillReceivesSelectionWithoutFullscreenShield() {
        var selected: String? = null
        compose.setContent {
            ScreenWordingObjects(
                stickers = listOf(StickerOverlay(id = "sticker", x = 0.5f, y = 0.5f, endMs = 5000)),
                textOverlays = emptyList(), coverText = null, coverTextStyle = null,
                selectedTextOverlayId = null, currentTimeMs = 0, isPlaying = false,
                onSelectSticker = { selected = it }, modifier = Modifier.size(320.dp, 180.dp)
            )
        }
        compose.onNodeWithTag("sticker-item-sticker").performTouchInput { click() }
        compose.runOnIdle { assertEquals("sticker", selected) }
    }
}
