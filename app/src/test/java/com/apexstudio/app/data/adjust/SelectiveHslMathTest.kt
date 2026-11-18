package com.apexstudio.app.data.adjust

import com.apexstudio.app.domain.model.HslBandAdjustment
import com.apexstudio.app.domain.model.SelectiveHslAdjustments
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SelectiveHslMathTest {
    private val output = FloatArray(3)

    @Test
    fun neutralHslPreservesTheInputExactly() {
        val input = floatArrayOf(0.12f, 0.47f, 0.91f)
        SelectiveHslMath.apply(input[0], input[1], input[2], SelectiveHslAdjustments(), output)
        assertArrayEquals(input, output, 0f)
    }

    @Test
    fun redHueShiftMovesRedToYellowAndLeavesGreenOutsideTheSelectedRange() {
        val settings = SelectiveHslAdjustments(red = HslBandAdjustment(hueShift = 60f))
        SelectiveHslMath.apply(1f, 0f, 0f, settings, output)
        assertArrayEquals(floatArrayOf(1f, 1f, 0f), output, 0.0001f)

        SelectiveHslMath.apply(0f, 1f, 0f, settings, output)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), output, 0.0001f)
    }

    @Test
    fun saturationAndLightnessAreClampedAndHueWrapsAroundTheWheel() {
        SelectiveHslMath.apply(
            1f, 0f, 0f,
            SelectiveHslAdjustments(red = HslBandAdjustment(saturation = -1f)),
            output
        )
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0.5f), output, 0.0001f)

        SelectiveHslMath.apply(
            1f, 0f, 0f,
            SelectiveHslAdjustments(red = HslBandAdjustment(lightness = 0.25f)),
            output
        )
        assertArrayEquals(floatArrayOf(1f, 0.5f, 0.5f), output, 0.0001f)

        SelectiveHslMath.apply(
            1f, 0f, 0f,
            SelectiveHslAdjustments(red = HslBandAdjustment(hueShift = -30f)),
            output
        )
        assertArrayEquals(floatArrayOf(1f, 0f, 0.5f), output, 0.0001f)
    }
}
