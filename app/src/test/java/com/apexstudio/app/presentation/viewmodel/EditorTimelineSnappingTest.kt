package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.SpeedPoint
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorTimelineSnappingTest {
    private fun clip(id: String, durationMs: Long) = MediaClip(
        id = id, name = id, uri = "file:///$id.mp4", type = ClipType.VIDEO,
        durationMs = durationMs, trimEndMs = durationMs
    )

    private fun editor(vararg clips: MediaClip) = EditorViewModel().apply {
        _state.update {
            it.copy(project = Project(id = "snap", name = "Snap", clips = clips.toList(), durationMs = 0L),
                magneticSnapping = true, snapToBeat = false)
        }
    }

    private fun speed(rate: Float) = listOf(SpeedPoint(0f, rate), SpeedPoint(1f, rate))

    @Test fun mainCutPointsUseTrimmedOutputDurationRatherThanSourceDuration() {
        val first = clip("first", 12_000L).copy(trimStartMs = 2_000L, speedPoints = speed(2f))
        val second = clip("second", 4_000L)
        val vm = editor(first, second)
        assertEquals(5_000L, vm.findMagneticSnapPoint(4_930L))
        assertEquals(9_000L, vm.findMagneticSnapPoint(9_060L))
        assertEquals(10_030L, vm.findMagneticSnapPoint(10_030L))
    }

    @Test fun interspersedOverlaysUseTheirPlacementWithoutShiftingLaterMainCuts() {
        val overlay = clip("overlay", 8_000L).copy(type = ClipType.OVERLAY,
            trackIndex = 1, timelineOffsetMs = 20_000L, speedPoints = speed(0.5f))
        val vm = editor(clip("first", 4_000L), overlay, clip("second", 6_000L))
        assertEquals(10_000L, vm.findMagneticSnapPoint(10_080L))
        assertEquals(20_000L, vm.findMagneticSnapPoint(19_920L))
        assertEquals(36_000L, vm.findMagneticSnapPoint(36_080L))
        assertEquals(12_020L, vm.findMagneticSnapPoint(12_020L))
    }

    @Test fun movingClipCanExcludeItsOwnBoundariesFromSnapCandidates() {
        val overlay = clip("overlay", 8_000L).copy(
            type = ClipType.OVERLAY,
            trackIndex = 1,
            timelineOffsetMs = 20_000L
        )
        val vm = editor(clip("main", 4_000L), overlay)

        assertEquals(20_080L, vm.findMagneticSnapPoint(20_080L, 150L, excludeClipId = "overlay"))
        assertEquals(20_000L, vm.findMagneticSnapPoint(20_080L))
    }

    @Test fun absoluteKeyframesAreNotOffsetAgainForLaterClips() {
        val second = clip("second", 10_000L).copy(keyframes = KeyframeTrack(listOf(
            Keyframe(id = "absolute", timeMs = 13_000L)
        )))
        val vm = editor(clip("first", 10_000L), second)
        assertEquals(13_000L, vm.findMagneticSnapPoint(13_080L))
        assertEquals(23_080L, vm.findMagneticSnapPoint(23_080L))
    }

    @Test fun sub100msMainClipsKeepTheirActualCutInsteadOfInventingDuration() {
        val vm = editor(clip("short", 40L), clip("next", 500L))
        assertEquals(40L, vm.findMagneticSnapPoint(42L, 5L))
        assertEquals(540L, vm.findMagneticSnapPoint(542L, 5L))
    }

    @Test fun beatOnlyModeAndDisabledModeRespectTheirToggles() {
        val vm = editor(clip("main", 1_000L))
        vm._state.update { it.copy(magneticSnapping = false, snapToBeat = true,
            beatMarkersMs = listOf(2_000L)) }
        assertEquals(2_000L, vm.findMagneticSnapPoint(2_080L))
        assertEquals(1_080L, vm.findMagneticSnapPoint(1_080L))
        vm._state.update { it.copy(snapToBeat = false) }
        assertEquals(2_080L, vm.findMagneticSnapPoint(2_080L))
        assertEquals(2_080L, vm.findNearestBeat(2_080L))
    }

    @Test fun invalidBeatMarkersAndExtremeDifferencesCannotOverflowNearestChoice() {
        val vm = editor()
        vm._state.update { it.copy(snapToBeat = true,
            beatMarkersMs = listOf(Long.MIN_VALUE, Long.MAX_VALUE, 1_000L)) }
        assertEquals(1_000L, vm.findNearestBeat(1_080L))
        assertEquals(Long.MAX_VALUE, vm.findNearestBeat(Long.MAX_VALUE - 1L))
        assertEquals(1_080L, vm.findNearestBeat(1_080L, -1L))
        assertEquals(-1L, vm.findNearestBeat(-1L))
    }

    @Test fun cumulativeAndOverlayEndsSaturateInsteadOfWrapping() {
        val overlay = clip("overlay", 1_000L).copy(type = ClipType.OVERLAY,
            trackIndex = 1, timelineOffsetMs = Long.MAX_VALUE - 100L)
        val vm = editor(clip("long", Long.MAX_VALUE - 1_000L), clip("last", 2_000L), overlay)
        assertEquals(Long.MAX_VALUE, vm.findMagneticSnapPoint(Long.MAX_VALUE - 1L, 5L))
        assertEquals(500L, vm.findMagneticSnapPoint(500L, 5L))
    }

    @Test fun equidistantSnapCandidatesPreferEarlierTimelinePoint() {
        val vm = editor()
        vm._state.update { it.copy(snapToBeat = true, beatMarkersMs = listOf(1_100L, 900L)) }
        assertEquals(900L, vm.findNearestBeat(1_000L))
        assertEquals(900L, vm.findMagneticSnapPoint(1_000L))
    }

    @Test fun sharedTimelineKeyframesParticipateInMagneticSnapping() {
        val vm = editor(clip("main", 10_000L))
        val sharedClip = com.apexstudio.app.domain.model.timeline.VideoClip(
            id = "main",
            uri = android.net.Uri.parse("file:///main.mp4"),
            inMs = 0L,
            outMs = 10_000L,
            opacityKeyframes = listOf(
                com.apexstudio.app.domain.model.timeline.Keyframe(6_200L, 0.5f)
            )
        )
        vm._state.update { state ->
            val project = state.project!!
            state.copy(project = project.copy(timeline = com.apexstudio.app.domain.model.timeline.Timeline(
                tracks = listOf(com.apexstudio.app.domain.model.timeline.VideoTrack(
                    id = "v1", lane = 0, clips = listOf(sharedClip)
                ))
            )))
        }

        assertEquals(6_200L, vm.findMagneticSnapPoint(6_250L, 100L))
    }

    @Test fun clipOffsetCommitAppliesMagneticToggleAndZoomScaledTolerance() {
        val overlay = clip("overlay", 2_000L).copy(
            type = ClipType.OVERLAY,
            trackIndex = 1,
            timelineOffsetMs = 12_000L
        )
        val vm = editor(clip("main", 4_000L), overlay)

        vm.setClipTimelineOffset("overlay", 4_080L)
        assertEquals(4_000L, vm._state.value.project!!.clips.single { it.id == "overlay" }.timelineOffsetMs)

        vm.toggleMagneticSnapping()
        vm.setClipTimelineOffset("overlay", 4_080L)
        assertEquals(4_080L, vm._state.value.project!!.clips.single { it.id == "overlay" }.timelineOffsetMs)
    }

    @Test fun clearKeyframesAlsoClearsMaskAndDrawingKeysWithoutRemovingTheirContent() {
        val base = clip("main", 1_000L)
        val typed = com.apexstudio.app.domain.model.timeline.VideoClip(
            id = "main",
            uri = android.net.Uri.parse("file:///main.mp4"),
            inMs = 0L,
            outMs = 1_000L,
            mask = com.apexstudio.app.domain.model.timeline.Mask.Shape(
                kind = com.apexstudio.app.domain.model.timeline.Mask.Kind.RECTANGLE,
                rect = android.graphics.RectF(0f, 0f, 1f, 1f),
                keyframes = listOf(com.apexstudio.app.domain.model.timeline.Keyframe(
                    300L, android.graphics.RectF(0f, 0f, 0.5f, 0.5f)
                ))
            ),
            drawingLayer = com.apexstudio.app.domain.model.timeline.DrawingLayer(
                globalAlphaKeyframes = listOf(com.apexstudio.app.domain.model.timeline.Keyframe(400L, 0.5f)),
                strokes = listOf(com.apexstudio.app.domain.model.timeline.Stroke(
                    points = listOf(androidx.compose.ui.geometry.Offset(0.1f, 0.2f)),
                    color = androidx.compose.ui.graphics.Color.White,
                    width = 2f,
                    tool = com.apexstudio.app.domain.model.timeline.Tool.PEN,
                    startMs = 0L,
                    alphaKeyframes = listOf(com.apexstudio.app.domain.model.timeline.Keyframe(500L, 0.7f))
                ))
            )
        )
        val vm = editor(base)
        vm._state.update { state ->
            val project = state.project!!
            state.copy(project = project.copy(timeline = com.apexstudio.app.domain.model.timeline.Timeline(
                tracks = listOf(com.apexstudio.app.domain.model.timeline.VideoTrack(
                    id = "v1", lane = 0, clips = listOf(typed)
                ))
            )))
        }

        vm.clearKeyframes("main")

        val result = vm._state.value.project!!.sharedVideoClip("main")!!
        assertTrue(result.transform.positionKeyframes.isEmpty())
        assertTrue(result.transform.scaleKeyframes.isEmpty())
        assertTrue(result.transform.rotationKeyframes.isEmpty())
        assertTrue(result.opacityKeyframes.isEmpty())
        assertTrue(result.volumeKeyframes.isEmpty())
        assertTrue(result.panKeyframes.isEmpty())
        assertTrue(result.filterKeyframes.isEmpty())
        assertTrue(result.effectIntensityKeyframes.isEmpty())
        assertTrue(result.lutKeyframes.isEmpty())
        assertTrue(result.saturationKeyframes.isEmpty())
        assertTrue(result.mask is com.apexstudio.app.domain.model.timeline.Mask.Shape)
        assertTrue((result.mask as com.apexstudio.app.domain.model.timeline.Mask.Shape).keyframes.isEmpty())
        assertEquals(1, result.drawingLayer!!.strokes.size)
        assertTrue(result.drawingLayer!!.globalAlphaKeyframes.isEmpty())
        assertTrue(result.drawingLayer!!.strokes.single().alphaKeyframes.isEmpty())
    }
}
