package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeParam
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimelineFrameMathTest {
    @Test
    fun `frame timestamps stay on grid for supported integer project rates`() {
        val expected = mapOf(
            24 to listOf(0L, 42L, 83L, 125L, 167L),
            25 to listOf(0L, 40L, 80L, 120L, 160L),
            30 to listOf(0L, 33L, 67L, 100L, 133L),
            60 to listOf(0L, 17L, 33L, 50L, 67L)
        )

        expected.forEach { (fps, timestamps) ->
            timestamps.forEachIndexed { frame, timestamp ->
                assertEquals("frame $frame at $fps fps", timestamp, TimelineFrameMath.frameTimeMs(frame.toLong(), fps))
            }
        }
    }

    @Test
    fun `consecutive steps use absolute frame indices without accumulating rounded duration`() {
        for (fps in listOf(24, 25, 30, 60)) {
            var position = 0L
            repeat(fps * 10) { frame ->
                position = TimelineFrameMath.step(position, forward = true, fps = fps, durationMs = 20_000L)
                assertEquals("after ${frame + 1} steps at $fps fps", TimelineFrameMath.frameTimeMs((frame + 1).toLong(), fps), position)
            }
        }
    }

    @Test
    fun `off-grid navigation finds adjacent frame while exact frame advances one whole frame`() {
        assertEquals(125L, TimelineFrameMath.step(100L, forward = true, fps = 24, durationMs = 1_000L))
        assertEquals(83L, TimelineFrameMath.step(100L, forward = false, fps = 24, durationMs = 1_000L))
        assertEquals(83L, TimelineFrameMath.step(42L, forward = true, fps = 24, durationMs = 1_000L))
        assertEquals(0L, TimelineFrameMath.step(42L, forward = false, fps = 24, durationMs = 1_000L))
    }

    @Test
    fun `steps clamp to timeline start and end`() {
        assertEquals(0L, TimelineFrameMath.step(0L, forward = false, fps = 30, durationMs = 1_000L))
        assertEquals(1_000L, TimelineFrameMath.step(1_000L, forward = true, fps = 30, durationMs = 1_000L))
        assertEquals(999L, TimelineFrameMath.step(999L, forward = true, fps = 30, durationMs = 999L))
        assertEquals(0L, TimelineFrameMath.step(42L, forward = true, fps = 24, durationMs = 0L))
    }

    @Test
    fun `controller steps from live position on project FPS and synchronizes both mirrors`() {
        val vm = EditorViewModel()
        vm._state.value = vm.state.value.copy(
            project = Project("p", "Project", 1_000L, fps = 24),
            durationMs = 1_000L,
            playerPositionMs = 900L,
            currentTimeMs = 900L
        )
        vm._playerPosition.value = 42L

        vm.stepFrame(forward = true)

        assertEquals(83L, vm.playerPositionFlow.value)
        assertEquals(83L, vm.state.value.playerPositionMs)
        assertEquals(83L, vm.state.value.currentTimeMs)
    }

    @Test
    fun `pause clamps live position to duration and flushes state mirror`() {
        val vm = EditorViewModel()
        vm._state.value = vm.state.value.copy(
            project = Project("p", "Project", 1_000L),
            durationMs = 1_000L,
            isPlaying = true,
            playerPositionMs = 200L,
            currentTimeMs = 200L
        )
        vm._playerPosition.value = 1_500L

        vm.setPlaying(false)

        assertEquals(1_000L, vm.playerPositionFlow.value)
        assertEquals(1_000L, vm.state.value.playerPositionMs)
        assertEquals(1_000L, vm.state.value.currentTimeMs)
        assertEquals(false, vm.state.value.isPlaying)
    }

    @Test
    fun `keyframe gesture snap window remains 150 milliseconds and preserves frame identity`() {
        val vm = EditorViewModel()
        val original = Keyframe.identity(1_000L, "original")
        val clip = MediaClip(
            "clip", "Clip", "file:///clip.mp4", 5_000L,
            trimEndMs = 5_000L,
            keyframes = KeyframeTrack(listOf(original))
        )
        vm._state.value = vm.state.value.copy(
            project = Project("p", "Project", 5_000L, fps = 60, clips = listOf(clip)),
            durationMs = 5_000L
        )

        assertTrue(vm.upsertKeyframeParam("clip", 1_150L, KeyframeParam.SCALE, 2f))
        val updated = vm.state.value.project!!.clips.single().keyframes.keyframes
        assertEquals(1, updated.size)
        assertEquals("original", updated.single().id)
        assertEquals(1_000L, updated.single().timeMs)
        assertEquals(2f, updated.single().scale, 0f)

        assertTrue(vm.upsertKeyframeParam("clip", 1_151L, KeyframeParam.SCALE, 3f))
        val inserted = vm.state.value.project!!.clips.single().keyframes.keyframes
        assertEquals(2, inserted.size)
        assertEquals(1_151L, inserted.maxByOrNull { it.timeMs }!!.timeMs)
        assertEquals(3f, inserted.maxByOrNull { it.timeMs }!!.scale, 0f)
    }
}
