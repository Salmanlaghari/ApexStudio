package com.apexstudio.app.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.SpeedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual production trim grips, with deterministic touch slop; not physical-device acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainTimelineTrimGestureTest {
    @get:Rule val composeRule = createComposeRule()

    private val source = MediaClip(
        id = "main", name = "main", uri = "file:///main.mp4", type = ClipType.VIDEO,
        durationMs = 10_000L, trimStartMs = 1_000L, trimEndMs = 3_000L,
    )

    private fun dragGrip(head: Boolean, deltaPx: Float) {
        composeRule.onNodeWithTag("timeline-trim-${if (head) "start" else "end"}-main", true)
            .performTouchInput {
                down(center)
                moveBy(Offset(deltaPx / 2f, 0f))
                moveBy(Offset(deltaPx / 2f, 0f))
                up()
            }
        composeRule.waitForIdle()
    }

    @Test fun headGripUsesPhysicalDensity() = checkDensity(head = true)
    @Test fun tailGripUsesPhysicalDensity() = checkDensity(head = false)

    @Test fun headGripCanLengthenTrimmedV1ClipTowardEarlierSourceFrames() = checkLengthening(head = true)
    @Test fun tailGripCanLengthenTrimmedV1ClipTowardLaterSourceFrames() = checkLengthening(head = false)

    @Test fun speedRampedHeadGripUsesOutputTimeGeometry() {
        val ramped = rampedClip()
        var committed: Pair<Long, Long>? = null
        composeRule.setContent {
            val configuration = LocalViewConfiguration.current
            CompositionLocalProvider(
                LocalDensity provides Density(2f),
                LocalViewConfiguration provides object : androidx.compose.ui.platform.ViewConfiguration by configuration {
                    override val touchSlop = 0f
                },
            ) {
                VideoTimeline(
                    clips = listOf(ramped), totalDurationMs = 8_000L, playheadMs = 0L,
                    selectedClipId = ramped.id, onOpenSpeed = {}, onOpenTrim = {},
                    onTrimClip = { _, start, end -> committed = start to end },
                )
            }
        }

        dragGrip(head = true, deltaPx = 54f) // 500 output ms at 2x density.
        composeRule.runOnIdle {
            val (start, end) = requireNotNull(committed)
            assertEquals(500.0, (ramped.effectiveDurationMs() -
                ramped.copy(trimStartMs = start).effectiveDurationMs()).toDouble(), 2.0)
            assertEquals(ramped.trimEndMs, end)
            org.junit.Assert.assertNotEquals(ramped.trimStartMs + 500L, start)
        }
    }

    private fun rampedClip() = source.copy(
        durationMs = 4_000L,
        speedPoints = listOf(
            SpeedPoint(0f, 0.5f),
            SpeedPoint(0.5f, 0.5f),
            SpeedPoint(1f, 2f),
        ),
    )

    private fun checkDensity(head: Boolean) {
        var committed: Pair<Long, Long>? = null
        composeRule.setContent {
            val configuration = LocalViewConfiguration.current
            CompositionLocalProvider(
                LocalDensity provides Density(2f),
                LocalViewConfiguration provides object : androidx.compose.ui.platform.ViewConfiguration by configuration {
                    override val touchSlop = 0f
                },
            ) {
                VideoTimeline(
                    clips = listOf(source), totalDurationMs = 4_000L, playheadMs = 0L,
                    selectedClipId = "main", onOpenSpeed = {}, onOpenTrim = {},
                    onTrimClip = { _, start, end -> committed = start to end },
                )
            }
        }
        // Default scale is 54 dp/second; at density 2, 54 physical px represents 500 ms.
        // Do not use boundsInRoot width: semantics clips it to the visible scroll viewport.
        dragGrip(head, 54f * if (head) 1f else -1f)
        val expected = if (head) 1_500L to 3_000L else 1_000L to 2_500L
        composeRule.runOnIdle {
            assertEquals(expected.first.toDouble(), requireNotNull(committed).first.toDouble(), 2.0)
            assertEquals(expected.second.toDouble(), requireNotNull(committed).second.toDouble(), 2.0)
        }
    }

    private fun checkLengthening(head: Boolean) {
        var committed: Pair<Long, Long>? = null
        composeRule.setContent {
            val configuration = LocalViewConfiguration.current
            CompositionLocalProvider(
                LocalDensity provides Density(2f),
                LocalViewConfiguration provides object : androidx.compose.ui.platform.ViewConfiguration by configuration {
                    override val touchSlop = 0f
                },
            ) {
                VideoTimeline(
                    clips = listOf(source), totalDurationMs = 4_000L, playheadMs = 0L,
                    selectedClipId = source.id, onOpenSpeed = {}, onOpenTrim = {},
                    onTrimClip = { _, start, end -> committed = start to end },
                )
            }
        }

        // At the production 54dp/s scale and density 2, 54px extends the
        // source boundary by 500ms. Outward drags must increase V1 output length.
        dragGrip(head = head, deltaPx = if (head) -54f else 54f)
        composeRule.runOnIdle {
            val (start, end) = requireNotNull(committed)
            assertEquals(if (head) 500L else source.trimStartMs, start)
            assertEquals(if (head) source.trimEndMs else 3_500L, end)
            val extendedDuration = source.copy(trimStartMs = start, trimEndMs = end).effectiveDurationMs()
            assertTrue("outward V1 edge drag must lengthen the clip", extendedDuration > source.effectiveDurationMs())
            assertEquals(2_500.0, extendedDuration.toDouble(), 2.0)
        }
    }

    @Test fun subsequentHeadDragUsesUpdatedClipWithSameId() {
        val current = mutableStateOf(source)
        val edits = mutableListOf<Pair<Long, Long>>()
        composeRule.setContent {
            val configuration = LocalViewConfiguration.current
            CompositionLocalProvider(
                LocalDensity provides Density(2f),
                LocalViewConfiguration provides object : androidx.compose.ui.platform.ViewConfiguration by configuration {
                    override val touchSlop = 0f
                },
            ) {
                VideoTimeline(
                    clips = listOf(current.value), totalDurationMs = 10_000L, playheadMs = 0L,
                    selectedClipId = "main", onOpenSpeed = {}, onOpenTrim = {},
                    onTrimClip = { _, start, end ->
                        edits.add(start to end)
                        current.value = current.value.copy(trimStartMs = start, trimEndMs = end)
                    },
                )
            }
        }
        dragGrip(true, 54f)
        dragGrip(true, 54f)
        composeRule.runOnIdle {
            assertEquals(2, edits.size)
            assertEquals(2_000.0, edits.last().first.toDouble(), 4.0)
            assertEquals(3_000L, edits.last().second)
        }
    }

    @Test fun zoomChangeRefreshesConversionWithoutChangingClipIdentity() {
        val zoom = mutableStateOf(1f)
        val edits = mutableListOf<Pair<Long, Long>>()
        composeRule.setContent {
            val configuration = LocalViewConfiguration.current
            CompositionLocalProvider(
                LocalDensity provides Density(2f),
                LocalViewConfiguration provides object : androidx.compose.ui.platform.ViewConfiguration by configuration {
                    override val touchSlop = 0f
                },
            ) {
                VideoTimeline(
                    clips = listOf(source), totalDurationMs = 10_000L, playheadMs = 0L,
                    timelineZoom = zoom.value, selectedClipId = "main",
                    onOpenSpeed = {}, onOpenTrim = {},
                    onTrimClip = { _, start, end -> edits.add(start to end) },
                )
            }
        }
        dragGrip(true, 54f)
        composeRule.runOnIdle { zoom.value = 2f }
        composeRule.waitForIdle()
        dragGrip(true, 54f)
        composeRule.runOnIdle {
            assertEquals(2, edits.size)
            assertEquals(1_500.0, edits.first().first.toDouble(), 2.0)
            assertEquals(1_250.0, edits.last().first.toDouble(), 2.0)
        }
    }
}
