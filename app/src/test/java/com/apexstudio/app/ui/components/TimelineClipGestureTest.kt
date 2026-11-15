package com.apexstudio.app.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.swipe
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.SpeedPoint
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.trimClip
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
        totalDurationMs: Long = clips.sumOf { it.durationMs }.coerceAtLeast(1_000L),
        onSelectClip: (String) -> Unit = {},
        onReorderClips: (List<String>) -> Unit = {},
        onMoveClipOffset: (String, Long) -> Unit = { _, _ -> },
        scrollState: ScrollState? = null,
        onOpenSpeed: () -> Unit = {},
        onOpenTrim: () -> Unit = {},
        onTrimClip: (String, Long, Long) -> Unit = { _, _, _ -> },
    ) {
        VideoTimeline(
            clips = clips,
            totalDurationMs = totalDurationMs,
            playheadMs = 0L,
            selectedClipId = selectedClipId,
            onSelectClip = onSelectClip,
            onReorderClips = onReorderClips,
            onMoveClipOffset = onMoveClipOffset,
            scrollState = scrollState ?: rememberScrollState(),
            onOpenSpeed = onOpenSpeed,
            onOpenTrim = onOpenTrim,
            onTrimClip = onTrimClip,
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
        var reorderedIds: List<String>? = null
        composeRule.setContent {
            timeline(
                selectedClipId = "clip-a",
                clips = listOf(clip("clip-a"), clip("clip-b")),
                onReorderClips = { ids -> reorderedIds = ids },
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
            assertEquals("first=$firstBounds next=$nextBounds distance=$distancePx", listOf("clip-b", "clip-a"), reorderedIds)
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

    @Test
    fun speedRampedOverlayHeadGripUsesOutputTimeGeometry() =
        checkRampedOverlayTrim(head = true, dragDeltaPx = 54f)

    @Test
    fun speedRampedOverlayHeadGripExpandsAndPreservesRightEdge() =
        checkRampedOverlayTrim(head = true, dragDeltaPx = -54f)

    @Test
    fun speedRampedOverlayTailGripUsesOutputTimeGeometry() =
        checkRampedOverlayTrim(head = false, dragDeltaPx = 54f)

    @Test
    fun speedRampedOverlayTailGripCanContract() =
        checkRampedOverlayTrim(head = false, dragDeltaPx = -54f)

    private fun checkRampedOverlayTrim(head: Boolean, dragDeltaPx: Float) {
        val overlay = clip("ramped-overlay").copy(
            durationMs = 10_000L,
            trimStartMs = 2_000L,
            trimEndMs = 8_000L,
            timelineOffsetMs = 6_000L,
            type = ClipType.OVERLAY,
            trackIndex = 1,
            speedPoints = listOf(
                SpeedPoint(0f, 0.5f),
                SpeedPoint(0.5f, 0.5f),
                SpeedPoint(1f, 2f),
            ),
        )
        val projectDurationMs = 20_000L
        val project = Project(
            id = "overlay-gesture-project",
            name = "Overlay gesture project",
            durationMs = projectDurationMs,
            clips = listOf(overlay),
        )
        val vm = EditorViewModel().also { viewModel ->
            viewModel._state.value = viewModel.state.value.copy(
                project = project,
                durationMs = projectDurationMs,
            )
        }
        val timelineClips = mutableStateOf(listOf(overlay))
        val timelineScrollState = ScrollState(if (head) 0 else 1_000)
        composeRule.setContent {
            val configuration = LocalViewConfiguration.current
            CompositionLocalProvider(
                LocalDensity provides Density(2f),
                LocalViewConfiguration provides object : androidx.compose.ui.platform.ViewConfiguration by configuration {
                    override val touchSlop = 0f
                },
            ) {
                timeline(
                    selectedClipId = overlay.id,
                    clips = timelineClips.value,
                    totalDurationMs = projectDurationMs,
                    scrollState = timelineScrollState,
                    onTrimClip = { clipId, start, end ->
                        vm.trimClip(clipId, start, end)
                        timelineClips.value = vm.state.value.project?.clips.orEmpty()
                    },
                )
            }
        }
        composeRule.waitForIdle()

        val grip = composeRule.onNodeWithTag(
            "timeline-overlay-trim-${if (head) "start" else "end"}-${overlay.id}",
            useUnmergedTree = true,
        )
        val before = vm.state.value.project!!.clips.single()
        grip.performTouchInput {
            down(center)
            moveBy(Offset(dragDeltaPx, 0f)) // 500 output ms at 2x density.
            up()
        }
        composeRule.waitForIdle()

        val after = vm.state.value.project!!.clips.single()
        composeRule.runOnIdle {
            val expectedDurationChange =
                if ((head && dragDeltaPx > 0f) || (!head && dragDeltaPx < 0f)) -500.0 else 500.0
            assertEquals(
                expectedDurationChange,
                (after.effectiveDurationMs() - before.effectiveDurationMs()).toDouble(),
                2.0,
            )
            org.junit.Assert.assertTrue(after.trimStartMs >= 0L)
            org.junit.Assert.assertTrue(after.trimEndMs - after.trimStartMs >= 100L)
            org.junit.Assert.assertTrue(after.trimEndMs <= after.durationMs)

            val beforeBoundary = if (head) before.trimStartMs else before.trimEndMs
            val afterBoundary = if (head) after.trimStartMs else after.trimEndMs
            if (dragDeltaPx > 0f) org.junit.Assert.assertTrue(afterBoundary > beforeBoundary)
            else org.junit.Assert.assertTrue(afterBoundary < beforeBoundary)
            val outputEquivalentSourceDelta = if (dragDeltaPx > 0f) 500L else -500L
            org.junit.Assert.assertNotEquals(beforeBoundary + outputEquivalentSourceDelta, afterBoundary)

            if (head) {
                assertEquals(
                    before.timelineOffsetMs + before.effectiveDurationMs(),
                    after.timelineOffsetMs + after.effectiveDurationMs(),
                )
            } else {
                assertEquals(before.timelineOffsetMs, after.timelineOffsetMs)
            }
        }
    }
}
