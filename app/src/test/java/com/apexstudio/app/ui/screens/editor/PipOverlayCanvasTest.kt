package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import androidx.media3.exoplayer.ExoPlayer
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
    fun imageOverlayMountsTheImageRendererInThePipCanvas() {
        composeRule.setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                PipOverlayCanvas(
                    overlayClips = listOf(overlay()),
                    selectedOverlayClipId = null,
                    currentTimeMs = 1_000L,
                    overlayPlayer = null,
                    overlayPlayerClipId = null,
                    onSelectOverlayClip = {},
                    onMoveOverlayClip = { _, _, _, _ -> },
                    onScaleOverlayClip = { _, _, _ -> },
                    onRotateOverlayClip = { _, _, _ -> },
                    onOverlayGestureEnd = {},
                    onRemoveOverlayClip = {},
                    overlayIsVideo = mapOf("pip-image" to false),
                )
            }
        }

        composeRule.onNodeWithTag("pip-overlay-image-pip-image", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun laterOverlayUsesProjectPlayheadTimeInsteadOfMainClipLocalZero() {
        val later = overlay().copy(
            timelineOffsetMs = 2_000L,
            trimStartMs = 0L,
            trimEndMs = 2_000L,
        )
        composeRule.setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                PipOverlayCanvas(
                    overlayClips = listOf(later),
                    selectedOverlayClipId = null,
                    currentTimeMs = 2_500L,
                    overlayPlayer = null,
                    overlayPlayerClipId = null,
                    onSelectOverlayClip = {},
                    onMoveOverlayClip = { _, _, _, _ -> },
                    onScaleOverlayClip = { _, _, _ -> },
                    onRotateOverlayClip = { _, _, _ -> },
                    onOverlayGestureEnd = {},
                    onRemoveOverlayClip = {},
                    overlayIsVideo = mapOf(later.id to false),
                )
            }
        }

        composeRule.onNodeWithTag("pip-overlay-image-${later.id}", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun videoOverlayMountsTheDedicatedPlayerViewInThePipCanvas() {
        composeRule.setContent {
            val context = LocalContext.current
            val player = remember(context) { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            Box(Modifier.size(400.dp, 300.dp)) {
                PipOverlayCanvas(
                    overlayClips = listOf(overlay()),
                    selectedOverlayClipId = null,
                    currentTimeMs = 1_000L,
                    overlayPlayer = player,
                    overlayPlayerClipId = "pip-image",
                    onSelectOverlayClip = {},
                    onMoveOverlayClip = { _, _, _, _ -> },
                    onScaleOverlayClip = { _, _, _ -> },
                    onRotateOverlayClip = { _, _, _ -> },
                    onOverlayGestureEnd = {},
                    onRemoveOverlayClip = {},
                    overlayIsVideo = mapOf("pip-image" to true),
                )
            }
        }

        composeRule.onNodeWithTag("pip-overlay-video-pip-image", useUnmergedTree = true)
            .assertIsDisplayed()
    }

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
    fun draggingVideoOverlayWorksWithNestedNativePlayerView() {
        var moveDx = 0f
        var moveDy = 0f
        var gestureEndCount = 0
        composeRule.setContent {
            val context = LocalContext.current
            val player = remember(context) { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) {
                onDispose { player.release() }
            }
            Box(Modifier.size(400.dp, 300.dp)) {
                PipOverlayCanvas(
                    overlayClips = listOf(overlay()),
                    selectedOverlayClipId = "pip-image",
                    currentTimeMs = 1_000L,
                    overlayPlayer = player,
                    overlayPlayerClipId = "pip-image",
                    onSelectOverlayClip = {},
                    onMoveOverlayClip = { _, dx, dy, _ ->
                        moveDx += dx
                        moveDy += dy
                    },
                    onScaleOverlayClip = { _, _, _ -> },
                    onRotateOverlayClip = { _, _, _ -> },
                    onOverlayGestureEnd = { gestureEndCount++ },
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
            assertTrue("video overlay drag must reach transform callback", moveDx > 0.01f)
            assertTrue("video overlay drag must reach transform callback", moveDy > 0.01f)
            assertEquals("native-view drag must finish one undoable gesture", 1, gestureEndCount)
        }
    }

    @Test
    fun tapOutsideEveryOverlayPassesThroughToPreviewGesture() {
        var previewTapCount = 0
        composeRule.setContent {
            Box(
                Modifier
                    .size(400.dp, 300.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { previewTapCount++ }
                    }
                    .testTag("preview-container")
            ) {
                PipOverlayCanvas(
                    overlayClips = listOf(overlay()),
                    selectedOverlayClipId = null,
                    currentTimeMs = 1_000L,
                    overlayPlayer = null,
                    overlayPlayerClipId = null,
                    onSelectOverlayClip = {},
                    onMoveOverlayClip = { _, _, _, _ -> },
                    onScaleOverlayClip = { _, _, _ -> },
                    onRotateOverlayClip = { _, _, _ -> },
                    onOverlayGestureEnd = {},
                    onRemoveOverlayClip = {},
                    overlayIsVideo = mapOf("pip-image" to false),
                )
            }
        }

        composeRule.onNodeWithTag("preview-container").performTouchInput {
            click(position = Offset(20f, 20f))
        }
        composeRule.runOnIdle { assertEquals(1, previewTapCount) }
    }

    @Test
    fun draggingRotateHandleChangesRotationAndEndsOneGesture() {
        val rotations = mutableListOf<Float>()
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
                    onScaleOverlayClip = { _, _, _ -> },
                    onRotateOverlayClip = { _, delta, persist ->
                        assertEquals(false, persist)
                        rotations += delta
                    },
                    onOverlayGestureEnd = { gestureEndCount++ },
                    onRemoveOverlayClip = {},
                    overlayIsVideo = mapOf("pip-image" to false),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Rotate overlay", useUnmergedTree = true)
            .performTouchInput {
                swipe(start = center, end = center + Offset(14f, 10f), durationMillis = 300)
            }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertTrue("rotate handle must emit a non-zero angle", rotations.any { kotlin.math.abs(it) > 0.1f })
            assertEquals("release must finish one rotate gesture", 1, gestureEndCount)
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
    @Test
    fun draggingAfterPreviewResizeUsesCurrentCanvasDimensions() {
        val width = mutableStateOf(280.dp)
        val height = mutableStateOf(300.dp)
        var dx = 0f
        var dy = 0f
        composeRule.setContent {
            Box(Modifier.size(width.value, height.value)) {
                PipOverlayCanvas(
                    overlayClips = listOf(overlay().copy(overlayAspect = 1f)), selectedOverlayClipId = "pip-image",
                    currentTimeMs = 1_000L, overlayPlayer = null, overlayPlayerClipId = null,
                    onSelectOverlayClip = {},
                    onMoveOverlayClip = { _, x, y, _ -> dx += x; dy += y },
                    onScaleOverlayClip = { _, _, _ -> }, onRotateOverlayClip = { _, _, _ -> },
                    onOverlayGestureEnd = {}, onRemoveOverlayClip = {},
                    overlayIsVideo = mapOf("pip-image" to true)
                )
            }
        }
        val item = composeRule.onNodeWithTag("pip-overlay-item-pip-image", useUnmergedTree = true)
        item.performTouchInput { swipe(center, center + Offset(30f, 20f), 300) }
        var oldDx = 0f
        var oldDy = 0f
        composeRule.runOnIdle {
            oldDx = dx; oldDy = dy; dx = 0f; dy = 0f
            width.value = 200.dp; height.value = 240.dp
        }
        composeRule.waitForIdle()
        item.performTouchInput { swipe(center, center + Offset(30f, 20f), 300) }
        composeRule.runOnIdle {
            assertTrue(oldDx > 0f && oldDy > 0f)
            assertEquals(oldDx * 280f / 200f, dx, 0.002f)
            assertEquals(oldDy * 300f / 240f, dy, 0.002f)
        }
    }

    @Test
    fun resizingAfterPreviewResizeUsesCurrentCanvasWidth() {
        val width = mutableStateOf(280.dp)
        var scale = 0.5f
        composeRule.setContent {
            Box(Modifier.size(width.value, 300.dp)) {
                PipOverlayCanvas(
                    overlayClips = listOf(overlay().copy(overlayAspect = 1f)), selectedOverlayClipId = "pip-image",
                    currentTimeMs = 1_000L, overlayPlayer = null, overlayPlayerClipId = null,
                    onSelectOverlayClip = {}, onMoveOverlayClip = { _, _, _, _ -> },
                    onScaleOverlayClip = { _, value, _ -> scale = value },
                    onRotateOverlayClip = { _, _, _ -> }, onOverlayGestureEnd = {},
                    onRemoveOverlayClip = {}, overlayIsVideo = mapOf("pip-image" to true)
                )
            }
        }
        val handle = composeRule.onNodeWithTag("pip-overlay-resize-pip-image", useUnmergedTree = true)
        handle.performTouchInput { swipe(center, center + Offset(25f, 25f), 300) }
        var oldChange = 0f
        composeRule.runOnIdle {
            oldChange = scale - 0.5f; scale = 0.5f; width.value = 200.dp
        }
        composeRule.waitForIdle()
        handle.performTouchInput { swipe(center, center + Offset(25f, 25f), 300) }
        composeRule.runOnIdle {
            assertTrue(oldChange > 0f)
            assertEquals(oldChange * 280f / 200f, scale - 0.5f, 0.002f)
        }
    }

}
