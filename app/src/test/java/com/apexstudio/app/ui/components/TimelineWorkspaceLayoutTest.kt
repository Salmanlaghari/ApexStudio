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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
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
        onDeselect: () -> Unit = {},
        clips: List<MediaClip> = listOf(clip("main")),
        audioTracks: List<AudioTrack> = emptyList(),
        audioWaveform: FloatArray = FloatArray(0),
        selectedAudioTrackId: String? = null,
        playheadMs: Long = 0L,
        scrollState: ScrollState = androidx.compose.foundation.rememberScrollState(),
        textOverlays: List<TextOverlay> = emptyList(),
        onAddMedia: (ClipType) -> Unit = {},
        onDeleteClips: (Set<String>) -> Unit = {},
        onSelectAudioTrack: (String) -> Unit = {},
    ) {
        Box(Modifier.requiredSize(widthDp.dp, heightDp.dp).testTag("timeline-test-root")) {
            VideoTimeline(
                clips = clips,
                audioTracks = audioTracks,
                selectedAudioTrackId = selectedAudioTrackId,
                textOverlays = textOverlays,
                audioWaveform = audioWaveform,
                totalDurationMs = 120_000L,
                playheadMs = playheadMs,
                onOpenSpeed = {},
                onOpenTrim = {},
                onAddMedia = onAddMedia,
                onDeleteClips = onDeleteClips,
                onSelectAudioTrack = onSelectAudioTrack,
                scrollState = scrollState,
                vScrollState = vScrollState,
                onScrub = onScrub,
                onDeselect = onDeselect,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test
    fun mainVideoLaneStaysFixedAt52DpRegardlessOfViewportAndSupportingRows() {
        assertEquals(52.dp, mainVideoLaneHeight(720f, hasMainVideo = true))
        assertEquals(52.dp, mainVideoLaneHeight(720f, hasMainVideo = true, supportingLanesHeightDp = 720f))
        assertEquals(52.dp, mainVideoLaneHeight(720f, hasMainVideo = true, hasWaveformStrip = true))
        assertEquals(52.dp, mainVideoLaneHeight(400f, hasMainVideo = true, hasWaveformStrip = true))
        assertEquals(52.dp, mainVideoLaneHeight(300f, hasMainVideo = true, hasWaveformStrip = true))
        assertEquals(52.dp, mainVideoLaneHeight(400f, hasMainVideo = true, supportingLanesHeightDp = 70f, hasWaveformStrip = true))
        assertEquals(52.dp, mainVideoLaneHeight(200f, hasMainVideo = true))
        assertEquals(0.dp, mainVideoLaneHeight(720f, hasMainVideo = false))
    }

    @Test
    fun compactHeightCountsOnlyPopulatedRowsForTheParentEditorLayout() {
        val empty = EditorState()
        val singleMain = EditorState(
            project = Project(id = "p", name = "Project", durationMs = 30_000L, clips = listOf(clip("main"))),
        )
        val mainAndOverlay = EditorState(
            project = Project(
                id = "p", name = "Project", durationMs = 30_000L,
                clips = listOf(clip("main"), clip("overlay", type = ClipType.OVERLAY, trackIndex = 1)),
            ),
        )

        assertEquals(68.dp, compactTimelineHeight(empty)) // 40dp header + 28dp ruler
        assertEquals(130.dp, compactTimelineHeight(singleMain))
        assertEquals(210.dp, compactTimelineHeight(mainAndOverlay))
    }

    @Test
    fun waveformDoesNotExpandTheFixedHeightMainLane() {
        composeRule.setContent {
            workspace(heightDp = 400, audioWaveform = floatArrayOf(0.1f, 0.7f, 0.3f))
        }
        composeRule.waitForIdle()

        val root = composeRule.onNodeWithTag("timeline-test-root").fetchSemanticsNode().boundsInRoot
        val main = composeRule.onNodeWithContentDescription("Main video track").fetchSemanticsNode().boundsInRoot
        val waveform = composeRule.onNodeWithContentDescription("Source audio waveform").fetchSemanticsNode().boundsInRoot

        assertTrue("The source waveform should remain visible: $waveform", waveform.height > 0f)
        assertEquals("V1 remains the fixed-height 52dp filmstrip lane", 52f, main.height, 1f)
        assertEquals("The waveform remains a compact 12dp strip", 12f, waveform.height, 1f)
        assertTrue("The compact timeline leaves unused viewport space below its lanes", root.bottom - waveform.bottom > 150f)
    }

    @Test
    fun populatedSupportingLaneIsAccountedForBeforeMainVideoUsesRemainingSpace() {
        composeRule.setContent {
            workspace(
                heightDp = 400,
                clips = listOf(clip("main"), clip("overlay", type = ClipType.OVERLAY, trackIndex = 1)),
                audioWaveform = floatArrayOf(0.1f, 0.7f, 0.3f),
            )
        }
        composeRule.waitForIdle()

        val main = composeRule.onNodeWithContentDescription("Main video track").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithContentDescription("Source audio waveform").assertExists()
        composeRule.onNodeWithContentDescription("V2 overlay").assertExists()
        assertEquals("V1 retains its fixed 52dp filmstrip height beside its supporting row", 52f, main.height, 1f)
        val overlay = composeRule.onNodeWithContentDescription("V2 overlay").fetchSemanticsNode().boundsInRoot
        assertEquals("Populated overlay layers retain the historical 60dp row height", 60f, overlay.height, 1f)
        composeRule.onNodeWithTag("timeline-overlay-clip-overlay", useUnmergedTree = true).assertExists()
    }

    @Test
    fun headerAddActionRequestsVideoMedia() {
        var requested: ClipType? = null
        val scrubbed = mutableListOf<Long>()
        var deselected = 0
        val scrollState = ScrollState(0)
        composeRule.setContent { workspace(onAddMedia = { requested = it }, onScrub = { scrubbed.add(it) }, onDeselect = { deselected++ }, scrollState = scrollState) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { scrollState.dispatchRawDelta(scrollState.maxValue.toFloat()) }

        composeRule.onNodeWithContentDescription("Add clip").performClick()

        composeRule.runOnIdle {
            assertEquals(ClipType.VIDEO, requested)
            assertTrue("Add must not scrub the ruler underneath its touch target", scrubbed.isEmpty())
            assertEquals("Add must not deselect the current layer", 0, deselected)
        }
    }

    @Test
    fun restoredTimelineHeaderShowsActionsAndKeepsRowUtilitiesInOverflow() {
        composeRule.setContent { workspace(widthDp = 320) }

        composeRule.onNodeWithText("Timeline").assertExists()
        val header = composeRule.onNodeWithTag("timeline-header").fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithText("Timeline").fetchSemanticsNode().boundsInRoot
        val duration = composeRule.onNodeWithTag("timeline-duration").fetchSemanticsNode().boundsInRoot
        assertEquals("Timeline header uses the compact 40dp height", 40f, header.height, 1f)
        assertTrue("Gradient title and duration share one header row", kotlin.math.abs(title.center.y - duration.center.y) < 1f)
        composeRule.onNodeWithTag("timeline-duration").assertExists()
        composeRule.onNodeWithTag("timeline-overflow").assertExists()
        composeRule.onNodeWithTag("timeline-reveal-playhead").assertExists()
        composeRule.onNodeWithTag("timeline-multiselect").assertExists()

        composeRule.onNodeWithTag("timeline-overflow").performClick()
        composeRule.onNodeWithText("Reveal playhead").assertExists()
        composeRule.onNodeWithText("Select multiple clips").assertExists()
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

        composeRule.onNodeWithTag("timeline-overflow").performClick()
        composeRule.onNodeWithText("Select multiple clips").performClick()
        composeRule.onNodeWithText("0 selected").assertExists()
        composeRule.onNodeWithTag("timeline-clip-main-a", useUnmergedTree = true).performTouchInput { click() }
        composeRule.onNodeWithTag("timeline-clip-main-b", useUnmergedTree = true).performTouchInput { click() }
        composeRule.onNodeWithText("2 selected").assertExists()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle { assertEquals(setOf("main-a", "main-b"), deleted.value) }
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithTag("timeline-overflow").performClick()
        composeRule.onNodeWithText("Select multiple clips").assertExists()
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

        composeRule.onNodeWithTag("timeline-overflow").performClick()
        composeRule.onNodeWithContentDescription("Reveal playhead").performClick()
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
    fun mainVideoTrackScrollsWithItsClipDuringHorizontalScroll() {
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

        // The accessible lane spans the complete timeline content width and
        // is clipped by the viewport, so its reported horizontal center is
        // not a scrolling position. The clip tag is the actual content item.
        assertEquals(clipBefore.center.x - 30f, clipAfter.center.x, 3f)
        assertEquals("V1 semantics stay bounded to the fixed 52dp filmstrip row", 52f, railAfter.height, 1f)
        assertTrue(
            "Accessible V1 row should contain its actual clip: row=$railAfter clip=$clipAfter",
            clipAfter.center.y in railAfter.top..railAfter.bottom,
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
    fun emptyMainLaneIsHiddenButOverlayGapAndPlacementRemainInCompactWorkspace() {
        val overlay = clip("overlay", type = ClipType.OVERLAY, trackIndex = 1, offsetMs = 3_000L)
        composeRule.setContent {
            workspace(heightDp = 180, clips = listOf(overlay))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Main video track").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("V2 overlay").assertExists()
        val chip = composeRule.onNodeWithTag("timeline-overlay-clip-overlay", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        // There is no identity gutter: V2 remains at its original 3s
        // project offset from the shared timeline origin (54dp/s).
        assertTrue("Overlay's intentional 3s gap should remain visible: $chip", chip.left >= 3_000f * 0.054f - 2f)
        assertTrue("Overlay chip should remain touchable in the compact viewport", chip.width > 0f && chip.height > 0f)
    }

    @Test
    fun emptyMainLaneAndSupportingLayerStayAlignedInFullHeightWorkspace() {
        composeRule.setContent {
            workspace(
                heightDp = 500,
                clips = listOf(clip("overlay", type = ClipType.OVERLAY, trackIndex = 1, offsetMs = 3_000L)),
            )
        }
        composeRule.onNodeWithContentDescription("Main video track").assertDoesNotExist()
        val rail = composeRule.onNodeWithContentDescription("V2 overlay").fetchSemanticsNode().boundsInRoot
        val chip = composeRule.onNodeWithTag("timeline-overlay-clip-overlay", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("V2 label must align with its visible layer: rail=$rail chip=$chip", kotlin.math.abs(rail.center.y - chip.center.y) < 12f)
    }

    @Test
    fun audioSelectionStartsEmptyAndSelectingAnyLaneCallsTheRealSelectionAction() {
        val selectedId = mutableStateOf<String?>(null)
        composeRule.setContent {
            workspace(
                clips = emptyList(),
                selectedAudioTrackId = selectedId.value,
                audioTracks = listOf(
                    AudioTrack("music-a", "Music A", "file:///music-a.wav", trimEndMs = 4_000L),
                    AudioTrack("music-b", "Music B", "file:///music-b.wav", trimEndMs = 4_000L),
                ),
                onSelectAudioTrack = { selectedId.value = it },
            )
        }
        val secondTrack = composeRule.onNodeWithTag("timeline-audio-track-music-b", useUnmergedTree = true)
        secondTrack.assertIsNotSelected()
        secondTrack
            .performClick()
        composeRule.runOnIdle { assertEquals("music-b", selectedId.value) }
        secondTrack.assertIsSelected()
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
    fun compactRulerHasNoSecGutterAndStartsAtTheSameOriginAsTheVideoLane() {
        composeRule.setContent { workspace() }
        composeRule.onNodeWithText("SEC").assertDoesNotExist()

        val ruler = composeRule.onNodeWithTag("timeline-ruler", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val main = composeRule.onNodeWithContentDescription("Main video track")
            .fetchSemanticsNode().boundsInRoot
        assertEquals("Ruler uses the compact 28dp height", 28f, ruler.height, 1f)
        assertEquals("Ruler and V1 share the same 00:00 origin", ruler.left, main.left, 0.5f)
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
