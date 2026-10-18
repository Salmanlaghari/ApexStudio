package com.apexstudio.app.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Gesture regression coverage against the production VideoTimeline clip nodes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimelineClipGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun clip(id: String) = MediaClip(
        id = id,
        name = id,
        uri = "file:///test/$id.mp4",
        // Leave a real body between the trim grips' minimum touch targets.
        durationMs = 2_000L,
        trimEndMs = 2_000L,
        type = ClipType.VIDEO,
    )

    @androidx.compose.runtime.Composable
    private fun timeline(
        selectedClipId: String?,
        clips: List<MediaClip> = listOf(clip("clip-a")),
        onSelectClip: (String) -> Unit = {},
        onReorderClips: (Int, Int) -> Unit = { _, _ -> },
        onMoveClipOffset: (String, Long) -> Unit = { _, _ -> },
        scrollState: ScrollState? = null,
        onOpenSpeed: () -> Unit = {},
        onOpenTrim: () -> Unit = {},
    ) {
        VideoTimeline(
            clips = clips,
            totalDurationMs = clips.sumOf { it.durationMs }.coerceAtLeast(1_000L),
            playheadMs = 0L,
            selectedClipId = selectedClipId,
            onSelectClip = onSelectClip,
            onReorderClips = onReorderClips,
            onMoveClipOffset = onMoveClipOffset,
            scrollState = scrollState ?: rememberScrollState(),
            onOpenSpeed = onOpenSpeed,
            onOpenTrim = onOpenTrim,
            modifier = Modifier,
        )
    }

    @Test
    fun tapSelectsClip() {
        val selected = mutableStateOf<String?>(null)
        composeRule.setContent {
            timeline(selectedClipId = selected.value, onSelectClip = { selected.value = it })
        }

        composeRule.onNodeWithTag("timeline-clip-clip-a", useUnmergedTree = true).performTouchInput { click() }

        composeRule.runOnIdle { assertEquals("clip-a", selected.value) }
    }

    @Test
    fun longPressOpensMenuBeforeAndAfterSelectionRecompositionAndActionsReachCallbacks() {
        val selected = mutableStateOf<String?>(null)
        var speedOpens = 0
        var trimOpens = 0
        composeRule.setContent {
            timeline(
                selectedClipId = selected.value,
                onSelectClip = { selected.value = it },
                onOpenSpeed = { speedOpens++ },
                onOpenTrim = { trimOpens++ },
            )
        }

        // Long-press on an unselected clip selects it and opens its menu.
        composeRule.onNodeWithTag("timeline-clip-clip-a", useUnmergedTree = true).performTouchInput { longClick() }
        composeRule.onNodeWithText("Speed").assertExists().performClick()
        composeRule.runOnIdle {
            assertEquals("clip-a", selected.value)
            assertEquals(1, speedOpens)
        }

        // Selection has now recomposed the row; long-press must still own the
        // gesture and expose the same menu on an already-selected clip.
        composeRule.onNodeWithTag("timeline-clip-clip-a", useUnmergedTree = true).performTouchInput { longClick() }
        composeRule.onNodeWithText("Trim").assertExists().performClick()
        composeRule.runOnIdle { assertEquals(1, trimOpens) }
    }

    @Test
    fun selectedClipDirectDragReordersWithoutOpeningLongPressMenu() {
        var reorderedFrom: Int? = null
        var reorderedTo: Int? = null
        composeRule.setContent {
            timeline(
                selectedClipId = "clip-a",
                clips = listOf(clip("clip-a"), clip("clip-b")),
                onReorderClips = { from, to ->
                    reorderedFrom = from
                    reorderedTo = to
                },
            )
        }

        // Move the first selected clip past the second clip's center. This is
        // an ordinary drag (no long-press delay), and should use direct reorder.
        val first = composeRule.onNodeWithTag("timeline-clip-clip-a", useUnmergedTree = true)
        val firstBounds = first.fetchSemanticsNode().boundsInRoot
        val nextBounds = composeRule.onNodeWithTag("timeline-clip-clip-b", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val distancePx = nextBounds.center.x - firstBounds.center.x + firstBounds.width / 2f
        first.performTouchInput {
            swipe(start = center, end = center + Offset(distancePx, 0f), durationMillis = 300)
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("first=$firstBounds next=$nextBounds distance=$distancePx", 0, reorderedFrom)
            assertEquals(1, reorderedTo)
        }
        composeRule.onNodeWithText("Speed").assertDoesNotExist()
        composeRule.onNodeWithText("Trim").assertDoesNotExist()
    }

    @Test
    fun tapSelectsOverlayLayerClip() {
        val selected = mutableStateOf<String?>(null)
        val overlay = clip("overlay-a").copy(type = ClipType.OVERLAY, trackIndex = 1)
        composeRule.setContent {
            timeline(
                selectedClipId = selected.value,
                clips = listOf(overlay),
                onSelectClip = { selected.value = it },
            )
        }

        composeRule.onNodeWithTag("timeline-overlay-clip-overlay-a", useUnmergedTree = true)
            .performTouchInput { click() }

        composeRule.runOnIdle { assertEquals("overlay-a", selected.value) }
    }

    @Test
    fun ordinarySwipeOnOverlayPansTimeline() {
        val overlay = clip("overlay-a").copy(
            type = ClipType.OVERLAY,
            trackIndex = 1,
            durationMs = 31_000L,
            trimEndMs = 31_000L,
        )
        val scrollState = ScrollState(0)
        composeRule.setContent {
            timeline(
                selectedClipId = null,
                clips = listOf(overlay),
                scrollState = scrollState,
            )
        }
        val overlayNode = composeRule.onNodeWithTag("timeline-overlay-clip-overlay-a", useUnmergedTree = true)
        overlayNode.performTouchInput {
            swipe(start = center, end = center + Offset(-120f, 0f), durationMillis = 300)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { org.junit.Assert.assertTrue(scrollState.value > 0) }
    }

    @Test
    fun longPressDragMovesOverlayClip() {
        val overlay = clip("overlay-a").copy(
            type = ClipType.OVERLAY,
            trackIndex = 1,
            durationMs = 31_000L,
            trimEndMs = 31_000L,
        )
        var movedToMs: Long? = null
        composeRule.setContent {
            timeline(
                selectedClipId = "overlay-a",
                clips = listOf(overlay),
                scrollState = ScrollState(0),
                onMoveClipOffset = { _, offset -> movedToMs = offset },
            )
        }
        composeRule.onNodeWithTag("timeline-overlay-clip-overlay-a", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                advanceEventTime(600)
                moveBy(Offset(120f, 0f))
                advanceEventTime(100)
                up()
            }
        composeRule.waitForIdle()
        composeRule.runOnIdle { org.junit.Assert.assertTrue((movedToMs ?: 0L) > 0L) }
    }
}
