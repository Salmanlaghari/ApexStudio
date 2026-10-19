package com.apexstudio.app.data.effect

import com.apexstudio.app.domain.model.VideoCropSettings
import org.junit.Assert.*
import org.junit.Test

/** Factory geometry only: these tests do not create a GL context or certify GPU rendering. */
class VideoCropGlEffectTest {
    @Test fun everyNonFiniteCoordinateIsRejectedBeforeShaderCreation() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (index in 0..3) {
                val bounds = floatArrayOf(.1f, .2f, .8f, .9f)
                bounds[index] = invalid
                assertNull("coordinate $index = $invalid", VideoCropGlEffect.fromRect(bounds[0], bounds[1], bounds[2], bounds[3]))
            }
        }
    }

    @Test fun reversedAndCollapsedWindowsDoNotCreateAnEffect() {
        assertNull(VideoCropGlEffect.fromRect(.8f, .2f, .1f, .9f))
        assertNull(VideoCropGlEffect.fromRect(.1f, .9f, .8f, .2f))
        assertNull(VideoCropGlEffect.fromRect(.2f, .2f, .2f, .8f))
        assertNull(VideoCropGlEffect.fromRect(.2f, .2f, .8f, .2f))
        assertNull(VideoCropGlEffect.fromRect(.2f, .2f, .205f, .8f))
    }

    @Test fun fullFrameAndNullPersistedSettingsNeedNoShader() {
        assertNull(VideoCropGlEffect.fromRect(0f, 0f, 1f, 1f))
        assertNull(VideoCropGlEffect.fromRect(-1f, -1f, 2f, 2f))
        assertNull(VideoCropGlEffect.fromSettings(null))
        assertNull(VideoCropGlEffect.fromSettings(VideoCropSettings()))
    }

    @Test fun finiteOutOfBoundsCoordinatesStillClampToUsableWindows() {
        assertNotNull(VideoCropGlEffect.fromRect(-.5f, .2f, .8f, 2f))
        assertNotNull(VideoCropGlEffect.fromRect(.1f, -.2f, 2f, .9f))
    }

    @Test fun validAsymmetricAndPresetWindowsRemainUsable() {
        assertNotNull(VideoCropGlEffect.fromRect(.1f, .2f, .8f, .9f))
        for (preset in VideoCropPreset.all.filter { it.aspectRatio != null }) {
            val crop = preset.centeredWindow(16f / 9f)
            if (!crop.isFullFrame) assertNotNull(preset.id, VideoCropGlEffect.fromSettings(crop))
        }
    }

    @Test fun malformedPersistedCoordinatesRestoreTheUncroppedFrame() {
        assertNull(VideoCropGlEffect.fromSettings(VideoCropSettings(left = Float.NaN)))
        assertNull(VideoCropGlEffect.fromSettings(VideoCropSettings(bottom = Float.NEGATIVE_INFINITY)))
    }
}
