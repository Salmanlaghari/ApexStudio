package com.apexstudio.app.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.TextMaskKeyframe
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.VideoMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regression coverage for keyframe placement and the production marker pointer handlers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1200dp-mdpi")
class TimelineKeyframeLaneTest {
    @get:Rule val composeRule = createComposeRule()

    private val mainKeyframe = Keyframe(id = "main-kf", timeMs = 1_000L)
    private val overlayKeyframe = Keyframe(id = "overlay-kf", timeMs = 1_000L)
    private val textKeyframe = Keyframe(id = "text-kf", timeMs = 1_000L)
    private val stickerKeyframe = Keyframe(id = "sticker-kf", timeMs = 1_000L)
    private val maskKeyframe = TextMaskKeyframe(id = "mask-kf", timeMs = 1_000L)

    private fun fixtures(): Triple<List<MediaClip>, List<TextOverlay>, List<StickerOverlay>> {
        val title = TextOverlay(
            id = "title",
            text = "Title",
            endMs = 10_000L,
            keyframes = KeyframeTrack(listOf(textKeyframe)),
            mask = VideoMask(),
            maskKeyframes = listOf(maskKeyframe),
        )
        val main = MediaClip(
            id = "main",
            name = "main",
            uri = "file:///main.mp4",
            durationMs = 10_000L,
            trimEndMs = 10_000L,
            type = ClipType.VIDEO,
            keyframes = KeyframeTrack(listOf(mainKeyframe)),
            textOverlays = listOf(title),
        )
        val overlay = MediaClip(
            id = "overlay",
            name = "overlay",
            uri = "file:///overlay.mp4",
            durationMs = 10_000L,
            trimEndMs = 10_000L,
            type = ClipType.OVERLAY,
            trackIndex = 1,
            keyframes = KeyframeTrack(listOf(overlayKeyframe)),
        )
        val sticker = StickerOverlay(
            id = "sticker",
            name = "Sticker",
            endMs = 10_000L,
            keyframes = KeyframeTrack(listOf(stickerKeyframe)),
        )
        return Triple(listOf(main, overlay), listOf(title), listOf(sticker))
    }

    private fun setTimeline(
        onScrub: (Long) -> Unit = {},
        onMoveKeyframe: (String, String, Long) -> Unit = { _, _, _ -> },
        onMoveTextKeyframe: (String, String, String, Long) -> Unit = { _, _, _, _ -> },
        onMoveStickerKeyframe: (String, String, Long) -> Unit = { _, _, _ -> },
        onMoveTextMaskKeyframe: (String, String, String, Long) -> Unit = { _, _, _, _ -> },
    ) {
        val (clips, textOverlays, stickers) = fixtures()
        composeRule.setContent {
            val configuration = LocalViewConfiguration.current
            CompositionLocalProvider(
                LocalDensity provides Density(2f),
                LocalViewConfiguration provides object : androidx.compose.ui.platform.ViewConfiguration by configuration {
                    override val touchSlop = 0f
                },
            ) {
                VideoTimeline(
                    clips = clips,
                    textOverlays = textOverlays,
                    stickers = stickers,
                    totalDurationMs = 10_000L,
                    playheadMs = 0L,
                    onScrub = onScrub,
                    onMoveKeyframe = onMoveKeyframe,
                    onMoveTextKeyframe = onMoveTextKeyframe,
                    onMoveStickerKeyframe = onMoveStickerKeyframe,
                    onMoveTextMaskKeyframe = onMoveTextMaskKeyframe,
                    onOpenSpeed = {},
                    onOpenTrim = {},
                    modifier = Modifier,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun everyDiamondIsVerticallyCenteredInItsOwnTimelineLayerIncludingMaskStrip() {
        setTimeline()

        assertCentered("timeline-keyframe-marker-main-main-main-kf", "timeline-keyframe-layer-main-main")
        assertCentered("timeline-keyframe-marker-overlay-overlay-overlay-kf", "timeline-keyframe-layer-overlay-overlay")
        assertCentered("timeline-keyframe-marker-text-title-text-kf", "timeline-keyframe-layer-text-title")
        assertCentered("timeline-keyframe-marker-sticker-sticker-sticker-kf", "timeline-keyframe-layer-sticker-sticker")
        // The mask has a distinct 12dp sub-lane at the bottom of the text clip;
        // its diamond must be centered in that mask lane, not in the text markers' lane.
        assertCentered("timeline-keyframe-marker-text-mask-title-mask-kf", "timeline-keyframe-layer-text-mask-title")
        listOf(
            "timeline-keyframe-marker-main-main-main-kf" to "timeline-keyframe-layer-main-main",
            "timeline-keyframe-marker-overlay-overlay-overlay-kf" to "timeline-keyframe-layer-overlay-overlay",
            "timeline-keyframe-marker-text-title-text-kf" to "timeline-keyframe-layer-text-title",
            "timeline-keyframe-marker-sticker-sticker-sticker-kf" to "timeline-keyframe-layer-sticker-sticker",
            "timeline-keyframe-marker-text-mask-title-mask-kf" to "timeline-keyframe-layer-text-mask-title",
        ).forEach { (marker, layer) -> assertAtOneSecond(marker, layer) }
    }

    private fun assertCentered(markerTag: String, layerTag: String) {
        val marker = composeRule.onNodeWithTag(markerTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val layer = composeRule.onNodeWithTag(layerTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("$markerTag must be centered in $layerTag: marker=$marker layer=$layer", kotlin.math.abs(marker.center.y - layer.center.y) <= 1f)
    }

    private fun assertAtOneSecond(markerTag: String, layerTag: String) {
        val marker = composeRule.onNodeWithTag(markerTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val layer = composeRule.onNodeWithTag(layerTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // 1,000 ms × 54 dp/s × density 2 = 108 physical px from the layer origin.
        assertTrue("$markerTag must map 1s from $layerTag's origin: marker=$marker layer=$layer", kotlin.math.abs(marker.center.x - (layer.left + 108f)) <= 2f)
    }

    @Test
    fun markerTapSeeksAndPhysicalDragMovesEachKeyframeByTimelineTimeAtDensityTwo() {
        val seeks = mutableListOf<Long>()
        val movedClipKeyframes = mutableListOf<Triple<String, String, Long>>()
        val movedText = mutableListOf<List<Any>>()
        val movedSticker = mutableListOf<Triple<String, String, Long>>()
        val movedMask = mutableListOf<List<Any>>()
        setTimeline(
            onScrub = { seeks += it },
            onMoveKeyframe = { clipId, keyframeId, time -> movedClipKeyframes += Triple(clipId, keyframeId, time) },
            onMoveTextKeyframe = { clipId, overlayId, keyframeId, time -> movedText += listOf(clipId, overlayId, keyframeId, time) },
            onMoveStickerKeyframe = { stickerId, keyframeId, time -> movedSticker += Triple(stickerId, keyframeId, time) },
            onMoveTextMaskKeyframe = { clipId, overlayId, keyframeId, time -> movedMask += listOf(clipId, overlayId, keyframeId, time) },
        )
        val markers = listOf(
            "timeline-keyframe-marker-main-main-main-kf",
            "timeline-keyframe-marker-overlay-overlay-overlay-kf",
            "timeline-keyframe-marker-text-title-text-kf",
            "timeline-keyframe-marker-sticker-sticker-sticker-kf",
            "timeline-keyframe-marker-text-mask-title-mask-kf",
        )

        markers.forEach { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click() }
        }
        markers.forEach { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
                down(center)
                moveBy(Offset(54f, 0f)) // 500 ms at 54 dp/s and 2 physical px/dp.
                up()
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("Each visible marker tap must seek; actual callbacks=$seeks", listOf(1_000L, 1_000L, 1_000L, 1_000L, 1_000L), seeks)
            assertEquals(
                listOf(Triple("main", "main-kf", 1_500L), Triple("overlay", "overlay-kf", 1_500L)),
                movedClipKeyframes,
            )
            assertEquals(listOf(listOf("main", "title", "text-kf", 1_500L)), movedText)
            assertEquals(listOf(Triple("sticker", "sticker-kf", 1_500L)), movedSticker)
            assertEquals(listOf(listOf("main", "title", "mask-kf", 1_500L)), movedMask)
        }
    }

    @Test
    fun sameMarkerCanBeDraggedAgainUsingItsUpdatedTimeAndLatestCallback() {
        val (initialClips, textOverlays, stickers) = fixtures()
        val moves = mutableListOf<Pair<Int, Long>>()
        composeRule.setContent {
            val configuration = LocalViewConfiguration.current
            CompositionLocalProvider(
                LocalDensity provides Density(2f),
                LocalViewConfiguration provides object : androidx.compose.ui.platform.ViewConfiguration by configuration {
                    override val touchSlop = 0f
                },
            ) {
                var clips by remember { mutableStateOf(initialClips) }
                var callbackRevision by remember { mutableIntStateOf(0) }
                VideoTimeline(
                    clips = clips,
                    textOverlays = textOverlays,
                    stickers = stickers,
                    totalDurationMs = 10_000L,
                    playheadMs = 0L,
                    onMoveKeyframe = { clipId, keyframeId, newTimeMs ->
                        moves += callbackRevision to newTimeMs
                        clips = clips.map { clip ->
                            if (clip.id != clipId) clip else clip.copy(
                                keyframes = clip.keyframes.copy(
                                    keyframes = clip.keyframes.keyframes.map { keyframe ->
                                        if (keyframe.id == keyframeId) keyframe.copy(timeMs = newTimeMs) else keyframe
                                    },
                                ),
                            )
                        }
                        callbackRevision++
                    },
                    onOpenSpeed = {},
                    onOpenTrim = {},
                )
            }
        }
        composeRule.waitForIdle()
        val marker = composeRule.onNodeWithTag(
            "timeline-keyframe-marker-main-main-main-kf",
            useUnmergedTree = true,
        )
        marker.performTouchInput {
            down(center)
            moveBy(Offset(54f, 0f)) // 500 ms at the test's 2 px/dp density.
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-keyframe-marker-main-main-main-kf", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(54f, 0f))
                up()
            }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("The second drag must start from 1,500ms and use the recomposed callback", listOf(1_500L, 2_000L), moves.map { it.second })
            assertEquals("Each recomposition must supply the callback used by that gesture", listOf(0, 1), moves.map { it.first })
        }
    }
}
