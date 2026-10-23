package com.apexstudio.app.data.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationPresetOptionsTest {
    @Test
    fun inModeScalesThePresetAndUsesRequestedDuration() {
        val track = AnimationPresetOptions.createTrack(
            type = AnimationPresetType.ZOOM_IN,
            mode = AnimationPresetMode.IN,
            durationMs = 1_600L,
            intensity = 0.5f,
            startMs = 250L
        )

        assertEquals(250L, track.keyframes.first().timeMs)
        assertEquals(1_850L, track.keyframes.last().timeMs)
        assertEquals(0.7f, track.keyframes.first().scale, 0.0001f)
        assertEquals(1f, track.keyframes.last().scale, 0.0001f)
    }

    @Test
    fun outModeReversesAnEntranceTrackIntoAnExit() {
        val track = AnimationPresetOptions.createTrack(
            type = AnimationPresetType.ZOOM_IN,
            mode = AnimationPresetMode.OUT,
            durationMs = 800L,
            intensity = 1f
        )

        assertEquals(1f, track.keyframes.first().scale, 0.0001f)
        assertEquals(0.4f, track.keyframes.last().scale, 0.0001f)
        assertEquals(0L, track.keyframes.first().timeMs)
        assertEquals(800L, track.keyframes.last().timeMs)
    }

    @Test
    fun naturalExitPresetStaysAnExitAndIntensityScalesItsOpacityChange() {
        val track = AnimationPresetOptions.createTrack(
            type = AnimationPresetType.FADE_OUT,
            mode = AnimationPresetMode.OUT,
            durationMs = 600L,
            intensity = 0.5f
        )

        assertEquals(1f, track.keyframes.first().opacity, 0.0001f)
        assertEquals(0.5f, track.keyframes.last().opacity, 0.0001f)
        assertEquals(600L, track.keyframes.last().timeMs)
    }

    @Test
    fun loopModeTilesCyclicPresetIntoMultipleRealKeyframes() {
        val track = AnimationPresetOptions.createTrack(
            type = AnimationPresetType.PULSE,
            mode = AnimationPresetMode.LOOP,
            durationMs = 1_200L,
            intensity = 1f,
            startMs = 100L
        )

        assertTrue("loop should contain more than one pulse", track.keyframes.size > 3)
        assertEquals(100L, track.keyframes.first().timeMs)
        assertEquals(1_300L, track.keyframes.last().timeMs)
        assertTrue(track.keyframes.count { it.scale > 1f } >= 3)
    }

    @Test
    fun durationAndIntensityAreClampedToSupportedRanges() {
        val track = AnimationPresetOptions.createTrack(
            type = AnimationPresetType.ZOOM_IN,
            mode = AnimationPresetMode.IN,
            durationMs = 1L,
            intensity = 99f
        )

        assertEquals(AnimationPresetOptions.MIN_DURATION_MS, track.keyframes.last().timeMs)
        assertEquals(0.1f, track.keyframes.first().scale, 0.0001f)
    }
}
