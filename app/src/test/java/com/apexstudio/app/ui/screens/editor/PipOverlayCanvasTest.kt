package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises pointer hit-testing and transform callbacks on real PiP overlay nodes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PipOverlayCanvasTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun overlay() = MediaClip(
        id = "pip-image",
        name = "pip-image",
        uri = "file:///missing/pip-image.jpg",
        durationMs = 10_000L,
        trimEndMs = 10_000L,
        type = ClipType.OVERLAY,
        trackIndex = 2,
        pipX = 0.5f,
        pipY = 0.5f,
        pipScale = 0.5f,
    )

    @Test
    fun tapOnOverlayItemSelectsItsId() {
        val selectedId = mutableStateOf<String?>(null)
        composeRule.setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                PipOverlayCanvas(
                    overlayClips = listOf(overlay()),
                    selectedOverlayClipId = selectedId.value,
                    currentTimeMs = 1_000L,
                    overlayPlayer = null,
                    overlayPlayerClipId = null,
                    onSelectOverlayClip = { selectedId.value = it },
                    onMoveOverlayClip = { _, _, _, _ -> },
                    onScaleOverlayClip = { _, _, _ -> },
                    onRotateOverlayClip = { _, _, _ -> },
                    onOverlayGestureEnd = {},
                    onRemoveOverlayClip = {},
                    overlayIsVideo = mapOf("pip-image" to true),
                )
            }
        }

        composeRule.onNodeWithTag("pip-overlay-item-pip-image", useUnmergedTree = true)
            .performTouchInput { click() }

        composeRule.runOnIdle { assertEquals("pip-image", selectedId.value) }
    }

    @Test
    fun tapOnOverlappingOverlaysSelectsTheVisuallyTopmostClip() {
        val selectedId = mutableStateOf<String?>(null)
        val bottom = overlay().copy(id = "bottom", name = "bottom")
        val top = overlay().copy(id = "top", name = "top")
        composeRule.setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                PipOverlayCanvas(
                    overlayClips = listOf(bottom, top),
                    selectedOverlayClipId = selectedId.value,
                    currentTimeMs = 1_000L,
                    overlayPlayer = null,
                    overlayPlayerClipId = null,
                    onSelectOverlayClip = { selectedId.value = it },
                    onMoveOverlayClip = { _, _, _, _ -> },
                    onScaleOverlayClip = { _, _, _ -> },
                    onRotateOverlayClip = { _, _, _ -> },
                    onOverlayGestureEnd = {},
                    onRemoveOverlayClip = {},
                    overlayIsVideo = mapOf("bottom" to true, "top" to true),
                )
            }
        }

        composeRule.onNodeWithTag("pip-overlay-item-top", useUnmergedTree = true)
            .performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals("top", selectedId.value)
        }
    }

    @Test
    fun draggingOverlayItemInvokesMoveAndGestureEndCallbacks() {
        var moveId: String? = null
        var moveDx = 0f
        var moveDy = 0f
        var movePersist: Boolean? = null
        var gestureEndId: String? = null
        var gestureEndCount = 0
        composeRule.setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                PipOverlayCanvas(
                    overlayClips = listOf(overlay()),
                    selectedOverlayClipId = "pip-image",
                    currentTimeMs = 1_000L,
                    overlayPlayer = null,
                    overlayPlayerClipId = null,
                    onSelectOverlayClip = {},
                    onMoveOverlayClip = { id, dx, dy, persist ->
                        moveId = id
                        moveDx += dx
                        moveDy += dy
                        movePersist = persist
                    },
                    onScaleOverlayClip = { _, _, _ -> },
                    onRotateOverlayClip = { _, _, _ -> },
                    onOverlayGestureEnd = {
                        gestureEndId = it
                        gestureEndCount++
                    },
                    onRemoveOverlayClip = {},
                    overlayIsVideo = mapOf("pip-image" to true),
                )
            }
        }

        composeRule.onNodeWithTag("pip-overlay-item-pip-image", useUnmergedTree = true)
            .performTouchInput {
                swipe(start = center, end = center + Offset(35f, 18f), durationMillis = 300)
            }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("pip-image", moveId)
            assertTrue("expected a non-zero horizontal normalized drag", kotlin.math.abs(moveDx) > 0.01f)
            assertTrue("expected a non-zero vertical normalized drag", kotlin.math.abs(moveDy) > 0.01f)
            assertEquals(false, movePersist)
            assertEquals("pip-image", gestureEndId)
            assertEquals("gesture end must fire exactly once when the drag ends", 1, gestureEndCount)
        }
    }

    @Test
    fun draggingResizeHandleChangesScaleAndFinishesOnePersistableGesture() {
        val scales = mutableListOf<Float>()
        val persistFlags = mutableListOf<Boolean>()
        var gestureEndCount = 0
        composeRule.setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                PipOverlayCanvas(
                    overlayClips = listOf(overlay()),
                    selectedOverlayClipId = "pip-image",
                    currentTimeMs = 1_000L,
                    overlayPlayer = null,
                    overlayPlayerClipId = null,
                    onSelectOverlayClip = {},
                    onMoveOverlayClip = { _, _, _, _ -> },
                    onScaleOverlayClip = { _, scale, persist ->
                        scales += scale
                        persistFlags += persist
                    },
                    onRotateOverlayClip = { _, _, _ -> },
                    onOverlayGestureEnd = { gestureEndCount++ },
                    onRemoveOverlayClip = {},
                    overlayIsVideo = mapOf("pip-image" to true),
                )
            }
        }

        composeRule.onNodeWithTag("pip-overlay-resize-pip-image", useUnmergedTree = true)
            .performTouchInput {
                swipe(start = center, end = center + Offset(35f, 35f), durationMillis = 300)
            }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertTrue("resize handle must invoke scale updates", scales.isNotEmpty())
            assertTrue("dragging outward must increase scale", scales.any { it > 0.5f })
            assertTrue("intermediate resize updates must defer persistence", persistFlags.all { !it })
            assertEquals("release must persist one completed gesture", 1, gestureEndCount)
        }
    }
}
