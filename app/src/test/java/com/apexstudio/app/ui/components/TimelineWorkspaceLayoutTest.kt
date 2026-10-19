package com.apexstudio.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Layout and workspace-action coverage against the production timeline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-mdpi")
class TimelineWorkspaceLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun clip(
        id: String,
        type: ClipType = ClipType.VIDEO,
        trackIndex: Int = 0,
        offsetMs: Long = 0L,
        durationMs: Long = 30_000L,
    ) = MediaClip(
        id = id,
        name = id,
        uri = "file:///test/$id.mp4",
        durationMs = durationMs,
        trimEndMs = durationMs,
        type = type,
        trackIndex = trackIndex,
        timelineOffsetMs = offsetMs,
    )

    @androidx.compose.runtime.Composable
    private fun workspace(
        widthDp: Int = 360,
        heightDp: Int = 500,
        vScrollState: ScrollState = androidx.compose.foundation.rememberScrollState(),
        onScrub: (Long) -> Unit = {},
        clips: List<MediaClip> = listOf(clip("main")),
        playheadMs: Long = 0L,
        scrollState: ScrollState = androidx.compose.foundation.rememberScrollState(),
        textOverlays: List<TextOverlay> = emptyList(),
        onAddMedia: (ClipType) -> Unit = {},
        onDeleteClips: (Set<String>) -> Unit = {},
    ) {
        Box(Modifier.requiredSize(widthDp.dp, heightDp.dp).testTag("timeline-test-root")) {
            VideoTimeline(
                clips = clips,
                textOverlays = textOverlays,
                totalDurationMs = 120_000L,
                playheadMs = playheadMs,
                onOpenSpeed = {},
                onOpenTrim = {},
                onAddMedia = onAddMedia,
                onDeleteClips = onDeleteClips,
                scrollState = scrollState,
                vScrollState = vScrollState,
                onScrub = onScrub,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test
    fun headerAddActionRequestsVideoMedia() {
        var requested: ClipType? = null
        composeRule.setContent { workspace(onAddMedia = { requested = it }) }

        composeRule.onNodeWithTag("timeline-add-media").performClick()

        composeRule.runOnIdle { assertEquals(ClipType.VIDEO, requested) }
    }

    @Test
    fun compact320DpHeaderKeepsActionsInsideAndClearOfTimelineTitle() {
        composeRule.setContent { workspace(widthDp = 320) }

        val root = composeRule.onNodeWithTag("timeline-test-root").fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithText("TIMELINE").fetchSemanticsNode().boundsInRoot
        val duration = composeRule.onNodeWithTag("timeline-duration").fetchSemanticsNode().boundsInRoot
        val reveal = composeRule.onNodeWithTag("timeline-reveal-playhead").fetchSemanticsNode().boundsInRoot
        val multiSelect = composeRule.onNodeWithTag("timeline-multiselect").fetchSemanticsNode().boundsInRoot
        val add = composeRule.onNodeWithTag("timeline-add-media").fetchSemanticsNode().boundsInRoot
        val density = root.width / 320f

        assertTrue("Title overlaps the first action: title=$title reveal=$reveal", title.right <= reveal.left)
        assertTrue("Duration overlaps the first action: duration=$duration reveal=$reveal", duration.right <= reveal.left)
        assertTrue("Header actions overlap", reveal.right <= multiSelect.left && multiSelect.right <= add.left + 1f)
        listOf(reveal, multiSelect, add).forEach { bounds ->
            assertTrue("Header action is outside the compact workspace: $bounds", bounds.left >= root.left && bounds.right <= root.right)
            assertTrue("Header action is outside the compact workspace: $bounds", bounds.top >= root.top && bounds.bottom <= root.top + 44f * density)
        }
    }

    @Test
    fun multiselectDescriptionTogglesAndBulkDeleteReceivesSelectedClipIds() {
        val deleted = mutableStateOf<Set<String>?>(null)
        composeRule.setContent {
            workspace(
                clips = listOf(
                    clip("main-a", durationMs = 2_000L),
                    clip("main-b", offsetMs = 2_000L, durationMs = 2_000L),
                ),
                onDeleteClips = { deleted.value = it },
            )
        }

        composeRule.onNodeWithContentDescription("Select multiple clips").assertExists().performClick()
        composeRule.onNodeWithContentDescription("Exit multi-select").assertExists()
        composeRule.onNodeWithTag("timeline-clip-main-a", useUnmergedTree = true).performTouchInput { click() }
        composeRule.onNodeWithTag("timeline-clip-main-b", useUnmergedTree = true).performTouchInput { click() }
        composeRule.onNodeWithText("2 selected").assertExists()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle { assertEquals(setOf("main-a", "main-b"), deleted.value) }
        composeRule.onNodeWithContentDescription("Exit multi-select").performClick()
        composeRule.onNodeWithContentDescription("Select multiple clips").assertExists()
    }

    @Test
    fun revealPlayheadRecentersAVisiblePlayheadAfterManualHorizontalPan() {
        val scrollState = ScrollState(0)
        val playhead = mutableIntStateOf(0)
        composeRule.setContent {
            workspace(
                playheadMs = playhead.intValue.toLong(),
                scrollState = scrollState,
            )
        }
        composeRule.waitForIdle()

        // Put the playhead in the left portion of a scrolled viewport. It is
        // still visible, so ordinary paused-playhead handling should not pan;
        // the explicit reveal action should move it back to the viewport center.
        composeRule.runOnIdle {
            val density = composeRule.density.density
            scrollState.dispatchRawDelta(1_000f)
            val visiblePlayheadPx = scrollState.value + scrollState.viewportSize / 4
            playhead.intValue = (visiblePlayheadPx / (54f * density) * 1_000f).toInt()
        }
        composeRule.waitForIdle()
        val before = scrollState.value

        composeRule.onNodeWithTag("timeline-reveal-playhead").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertTrue("Reveal should adjust the manually panned timeline", scrollState.value < before)
            val playheadPx = playhead.intValue * 0.054f * composeRule.density.density
            val playheadInViewport = playheadPx - scrollState.value
            assertTrue(
                "Playhead should be recentered: viewport=${scrollState.viewportSize}, x=$playheadInViewport",
                kotlin.math.abs(playheadInViewport - scrollState.viewportSize / 2f) < 3f,
            )
        }
    }

    @Test
    fun accessibleMainRailStaysFixedAndAlignedWithItsClipDuringHorizontalScroll() {
        val scrollState = ScrollState(0)
        composeRule.setContent {
            workspace(
                clips = listOf(
                    clip("earlier", durationMs = 2_000L),
                    clip("main", offsetMs = 2_000L, durationMs = 2_000L),
                ),
                scrollState = scrollState,
            )
        }
        composeRule.waitForIdle()
        val rail = composeRule.onNodeWithContentDescription("Main video track")
        val mainClip = composeRule.onNodeWithTag("timeline-clip-main", useUnmergedTree = true)
        val railBefore = rail.fetchSemanticsNode().boundsInRoot
        val clipBefore = mainClip.fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle { scrollState.dispatchRawDelta(30f) }
        composeRule.waitForIdle()
        val railAfter = rail.fetchSemanticsNode().boundsInRoot
        val clipAfter = mainClip.fetchSemanticsNode().boundsInRoot

        assertEquals(railBefore.center.x, railAfter.center.x, 0.5f)
        assertEquals(clipBefore.center.x - 30f, clipAfter.center.x, 3f)
        assertTrue(
            "Accessible main-track rail and clip should share the same lane: rail=$railAfter clip=$clipAfter",
            kotlin.math.abs(railAfter.center.y - clipAfter.center.y) < 18f,
        )
    }

    @Test
    fun onlyOccupiedOverlayAndTextLanesAppearWithoutEmptyFakeTracks() {
        composeRule.setContent {
            workspace(
                clips = listOf(
                    clip("main"),
                    clip("overlay", type = ClipType.OVERLAY, trackIndex = 1),
                ),
                textOverlays = listOf(TextOverlay(id = "title", text = "Hello")),
            )
        }

        composeRule.onNodeWithContentDescription("Main video track").assertExists()
        composeRule.onNodeWithText("V1").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("V2 overlay").assertExists()
        composeRule.onNodeWithContentDescription("Text").assertExists()
        composeRule.onNodeWithContentDescription("V3 overlay").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Stickers").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Effects and filters").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Music").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Voice and sound effects").assertDoesNotExist()
    }
    @Test
    fun rulerSeekUsesTimeCanvasOriginNotPinnedRailWidth() {
        var scrubbedMs = -1L
        composeRule.setContent { workspace(onScrub = { scrubbedMs = it }) }
        val x = 108f * composeRule.density.density
        composeRule.onNodeWithTag("timeline-ruler", useUnmergedTree = true).performTouchInput {
            click(Offset(x, center.y))
        }
        composeRule.runOnIdle { assertTrue("108dp at 54dp/s must seek 2s: $scrubbedMs", kotlin.math.abs(scrubbedMs - 2_000L) <= 2L) }
    }

    @Test
    fun compactHeightLetsSupportingLanesScrollIntoViewWithTheirRail() {
        val vertical = ScrollState(0)
        composeRule.setContent {
            workspace(heightDp = 180, vScrollState = vertical,
                clips = listOf(clip("main")) + (1..4).map { layer ->
                    clip("overlay-$layer", ClipType.OVERLAY, layer, durationMs = 2_000L)
                })
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue("Compact timeline must retain a vertical scroll range", vertical.maxValue > 0)
            vertical.dispatchRawDelta(vertical.maxValue.toFloat())
        }
        composeRule.waitForIdle()
        val rail = composeRule.onNodeWithContentDescription("V5 overlay").fetchSemanticsNode().boundsInRoot
        val chip = composeRule.onNodeWithTag("timeline-overlay-clip-overlay-4", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Supporting clip must be visible after vertical scroll", chip.height > 0f)
        assertTrue("Lane identity must follow compact scrolling: rail=$rail chip=$chip", kotlin.math.abs(rail.center.y - chip.center.y) < 12f * composeRule.density.density)
    }

}
