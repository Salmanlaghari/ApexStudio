package com.apexstudio.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.filter.GpuColorProfiles
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.state.TIMELINE_MIN_ZOOM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoTimelineHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun cleanTimelineStartsWithRulerAndKeepsMainVideoAndMusicLaneRoles() {
        val scrollState = ScrollState(0)
        compose.setContent {
            VideoTimeline(
                clips = listOf(MediaClip(
                    id = "main", name = "Main", uri = "file:///main.mp4",
                    durationMs = 10_000L, trimEndMs = 10_000L, type = ClipType.VIDEO
                )),
                audioTracks = listOf(AudioTrack(id = "music-1", name = "Track music", uri = "file:///music")),
                totalDurationMs = 10_000L,
                playheadMs = 0L,
                onOpenSpeed = {},
                onOpenTrim = {},
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize()
            )
        }

        compose.onNodeWithText("Timeline").assertDoesNotExist()
        compose.onNodeWithTag("timeline-header").assertDoesNotExist()
        compose.onNodeWithTag("timeline-header-add-media").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add media").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add video media").assertDoesNotExist()
        compose.onNodeWithTag("timeline-add-media").assertDoesNotExist()
        compose.onNodeWithTag("timeline-add-at-end").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add Clip").assertDoesNotExist()
        compose.onNodeWithTag("timeline-overflow").assertDoesNotExist()
        compose.onNodeWithText("Timeline options").assertDoesNotExist()
        compose.onNodeWithText("Reveal playhead").assertDoesNotExist()
        compose.onNodeWithTag("timeline-ruler", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Main video · V1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Music (A1)").assertIsDisplayed()
        compose.onNodeWithTag("timeline-add-overlay", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("♫").assertDoesNotExist()
        compose.waitForIdle()
        compose.runOnIdle { scrollState.dispatchRawDelta(scrollState.maxValue.toFloat()) }
    }

    @Test
    fun reservedOverlayLanePlusInvokesTheOverlayPickerAction() {
        var addedType: ClipType? = null
        val state = EditorState(
            project = Project(id = "empty", name = "Empty", durationMs = 10_000L, clips = emptyList()),
            durationMs = 10_000L
        )
        compose.setContent {
            VideoTimeline(
                state = state,
                onScrub = {},
                onSelectClip = {},
                onReorderClips = {},
                onAddMedia = { addedType = it },
                onSplitClip = { _, _ -> },
                onDuplicateClip = {},
                onDeleteClip = {},
                onZoomChange = {},
                onOpenSpeed = {},
                onOpenTrim = {},
                modifier = Modifier.requiredSize(320.dp, 240.dp)
            )
        }

        compose.onNodeWithTag("timeline-add-overlay", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertEquals(ClipType.OVERLAY, addedType) }
    }

    @Test
    fun overlaysFromDifferentTracksShareOneRowAndKeepTheirActualLayerSemantics() {
        val lower = MediaClip(
            id = "overlay-v2", name = "lower", uri = "file:///lower.jpg",
            durationMs = 10_000L, trimEndMs = 10_000L, type = ClipType.OVERLAY,
            trackIndex = 1, timelineOffsetMs = 1_000L
        )
        val upper = MediaClip(
            id = "overlay-v5", name = "upper", uri = "file:///upper.jpg",
            durationMs = 10_000L, trimEndMs = 10_000L, type = ClipType.OVERLAY,
            trackIndex = 4, timelineOffsetMs = 3_000L
        )
        compose.setContent {
            VideoTimeline(
                clips = listOf(lower, upper),
                totalDurationMs = 10_000L,
                playheadMs = 0L,
                lockedVideoLayers = setOf(4),
                onOpenSpeed = {},
                onOpenTrim = {},
                scrollState = ScrollState(0),
                modifier = Modifier.requiredSize(320.dp, 300.dp)
            )
        }

        val lowerNode = compose.onNodeWithTag("timeline-overlay-clip-overlay-v2", useUnmergedTree = true)
            .assertIsDisplayed()
        val upperNode = compose.onNodeWithTag("timeline-overlay-clip-overlay-v5", useUnmergedTree = true)
            .assertIsDisplayed()
        val lowerBounds = lowerNode.fetchSemanticsNode().boundsInRoot
        val upperBounds = upperNode.fetchSemanticsNode().boundsInRoot
        assertEquals("Grouped overlay clips must share one visible row", lowerBounds.top, upperBounds.top, 0.5f)
        compose.onNodeWithContentDescription("Overlay clip lower, track V2", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Overlay clip upper, track V5", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("timeline-overlay-trim-start-overlay-v2", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("timeline-overlay-trim-start-overlay-v5", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun overlappingGroupedOverlaysCanBeSelectedFromTheStackMenu() {
        var selectedClipId: String? = null
        val lower = MediaClip(
            id = "lower-overlay", name = "lower", uri = "file:///lower.jpg",
            durationMs = 10_000L, trimEndMs = 10_000L, type = ClipType.OVERLAY,
            trackIndex = 1, timelineOffsetMs = 1_000L
        )
        val upper = MediaClip(
            id = "upper-overlay", name = "upper", uri = "file:///upper.jpg",
            durationMs = 10_000L, trimEndMs = 10_000L, type = ClipType.OVERLAY,
            trackIndex = 4, timelineOffsetMs = 1_000L
        )
        compose.setContent {
            VideoTimeline(
                clips = listOf(lower, upper),
                totalDurationMs = 10_000L,
                playheadMs = 0L,
                onSelectClip = { selectedClipId = it },
                onOpenSpeed = {},
                onOpenTrim = {},
                scrollState = ScrollState(0),
                modifier = Modifier.requiredSize(320.dp, 300.dp)
            )
        }

        compose.onNodeWithTag("timeline-overlay-stack-upper-overlay", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("lower · V2", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals("lower-overlay", selectedClipId) }
    }

    @Test
    fun hidingOneOriginalOverlayLayerDoesNotHideOtherGroupedClips() {
        val visible = MediaClip(
            id = "visible-overlay", name = "visible", uri = "file:///visible.jpg",
            durationMs = 10_000L, trimEndMs = 10_000L, type = ClipType.OVERLAY,
            trackIndex = 1, timelineOffsetMs = 1_000L
        )
        val hidden = MediaClip(
            id = "hidden-overlay", name = "hidden", uri = "file:///hidden.jpg",
            durationMs = 10_000L, trimEndMs = 10_000L, type = ClipType.OVERLAY,
            trackIndex = 4, timelineOffsetMs = 3_000L
        )
        compose.setContent {
            VideoTimeline(
                clips = listOf(visible, hidden),
                totalDurationMs = 10_000L,
                playheadMs = 0L,
                hiddenVideoLayers = setOf(4),
                onOpenSpeed = {},
                onOpenTrim = {},
                scrollState = ScrollState(0),
                modifier = Modifier.requiredSize(320.dp, 300.dp)
            )
        }

        compose.onNodeWithTag("timeline-overlay-clip-visible-overlay", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("timeline-overlay-clip-hidden-overlay", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun rulerLabelsDoNotOverlapAtMinimumZoom() {
        val durationMs = 120_000L
        compose.setContent {
            VideoTimeline(
                clips = listOf(MediaClip(
                    id = "main", name = "Main", uri = "file:///main.mp4",
                    durationMs = durationMs, trimEndMs = durationMs, type = ClipType.VIDEO
                )),
                totalDurationMs = durationMs,
                playheadMs = 0L,
                timelineZoom = TIMELINE_MIN_ZOOM,
                onOpenSpeed = {},
                onOpenTrim = {},
                scrollState = ScrollState(0),
                modifier = Modifier.requiredSize(320.dp, 220.dp)
            )
        }
        compose.waitForIdle()

        val firstLabel = compose.onNodeWithTag("timeline-ruler-label-0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val nextLabel = compose.onNodeWithTag("timeline-ruler-label-120", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertTrue("Ruler labels overlap at minimum zoom: first=$firstLabel next=$nextLabel", nextLabel.left >= firstLabel.right)
    }

    @Test
    fun stateDrivenTimelineForwardsEveryMultiSelectMutation() {
        var duplicated = false
        var grouped = false
        var speedApplied = false
        var filterApplied = false
        var lutApplied = false
        val clip = MediaClip("main", "Main", "file:///main.mp4", 10_000L, trimEndMs = 10_000L)
        val state = EditorState(
            project = Project(id = "p", name = "Project", durationMs = 10_000L, clips = listOf(clip)),
            durationMs = 10_000L,
            multiSelectMode = true,
            selectedClipIds = setOf("main")
        )
        compose.setContent {
            VideoTimeline(
                state = state,
                onScrub = {},
                onSelectClip = {},
                onReorderClips = {},
                onAddMedia = {},
                onSplitClip = { _, _ -> },
                onDuplicateClip = {},
                onDeleteClip = {},
                onZoomChange = {},
                onOpenSpeed = {},
                onOpenTrim = {},
                onDuplicateSelectedClips = { duplicated = true },
                onGroupSelectedClips = { grouped = true },
                onApplySpeedToSelectedClips = { speedApplied = true },
                onApplyFilterToSelectedClips = { filterApplied = true },
                onApplyLutToSelectedClips = { _, _ -> lutApplied = true },
                modifier = Modifier.fillMaxSize()
            )
        }

        fun invokeAction(tag: String) {
            compose.onNodeWithTag("timeline-multiselect-actions").performClick()
            compose.onNodeWithTag(tag).performScrollTo().performClick()
        }
        invokeAction("timeline-action-duplicate")
        invokeAction("timeline-action-group")
        invokeAction("timeline-action-speed-1.5x")
        invokeAction("timeline-action-filter-vignette")
        invokeAction("timeline-action-lut-${GpuColorProfiles.ALL.first().id}")

        compose.runOnIdle {
            assertTrue(duplicated)
            assertTrue(grouped)
            assertTrue(speedApplied)
            assertTrue(filterApplied)
            assertTrue(lutApplied)
        }
    }

    @Test
    fun multiSelectActionsAreReachableAtCompactWidthAndInvokeCallbacks() {
        var duplicated = false
        compose.setContent {
            VideoTimeline(
                clips = listOf(MediaClip("main", "Main", "file:///main.mp4", 10_000L, trimEndMs = 10_000L)),
                totalDurationMs = 10_000L,
                playheadMs = 0L,
                multiSelectMode = true,
                selectedClipIds = setOf("main"),
                onDuplicateSelectedClips = { duplicated = true },
                onOpenSpeed = {}, onOpenTrim = {},
                scrollState = ScrollState(0), modifier = Modifier.fillMaxSize()
            )
        }
        compose.onNodeWithTag("timeline-multiselect-actions").performClick()
        compose.onNodeWithTag("timeline-action-duplicate").performClick()
        compose.runOnIdle { assertEquals(true, duplicated) }
    }

}
