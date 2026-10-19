package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.SpeedPoint
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
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
}
