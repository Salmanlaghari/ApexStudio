package com.apexstudio.app.domain.model.timeline

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KeyframeInterpolatorTest {
    @Test
    fun `linear curve interpolates midpoint and clamps at both ends`() {
        val keys = listOf(
            Keyframe(0L, 0f, Curve.LINEAR),
            Keyframe(100L, 10f)
        )

        assertEquals(0f, Interpolator.valueAt(keys, -1L, FloatType), 0f)
        assertEquals(5f, Interpolator.valueAt(keys, 50L, FloatType), 0.0001f)
        assertEquals(10f, Interpolator.valueAt(keys, 101L, FloatType), 0f)
    }

    @Test
    fun `curve on the previous keyframe controls the segment`() {
        val cases = listOf(
            Curve.LINEAR to 25f,
            Curve.SMOOTH to 15.625f,
            Curve.EASE_IN to 6.25f,
            Curve.EASE_OUT to 43.75f
        )

        cases.forEach { (curve, expected) ->
            val keys = listOf(Keyframe(0L, 0f, curve), Keyframe(100L, 100f))
            assertEquals(curve.name, expected, Interpolator.valueAt(keys, 25L, FloatType), 0.0001f)
        }
    }

    @Test
    fun `single keyframe is held and duplicate exact timestamps choose the last key`() {
        assertEquals(7f, Interpolator.valueAt(listOf(Keyframe(12L, 7f)), 100L, FloatType), 0f)

        val keys = listOf(
            Keyframe(0L, 1f, Curve.LINEAR),
            Keyframe(50L, 2f, Curve.LINEAR),
            Keyframe(50L, 3f, Curve.LINEAR),
            Keyframe(100L, 5f)
        )
        assertEquals(3f, Interpolator.valueAt(keys, 50L, FloatType), 0f)
        assertEquals(4f, Interpolator.valueAt(keys, 75L, FloatType), 0.0001f)
    }

    @Test
    fun `sampling a large track uses correct neighboring values`() {
        val keys = (0..10_000).map { index ->
            Keyframe(index * 10L, index.toFloat(), Curve.LINEAR)
        }

        assertEquals(1_234.5f, Interpolator.valueAt(keys, 12_345L, FloatType), 0.0001f)
    }

    @Test
    fun `xy pan color and enum types interpolate or step as appropriate`() {
        val vectorKeys = listOf(
            Keyframe(0L, XY(0f, 10f), Curve.LINEAR),
            Keyframe(100L, XY(20f, -10f))
        )
        assertEquals(XY(10f, 0f), Interpolator.valueAt(vectorKeys, 50L, XYType))

        val panKeys = listOf(Keyframe(0L, Pan(-1f), Curve.LINEAR), Keyframe(100L, Pan(1f)))
        assertEquals(Pan(0f), Interpolator.valueAt(panKeys, 50L, PanType))

        val colorKeys = listOf(
            Keyframe(0L, Color(1f, 0f, 0f, 0.2f), Curve.LINEAR),
            Keyframe(100L, Color(0f, 0f, 1f, 0.8f))
        )
        val midpoint = Interpolator.valueAt(colorKeys, 50L, ColorType)
        assertEquals(0.5f, midpoint.red, 0.003f)
        assertEquals(0f, midpoint.green, 0.0001f)
        assertEquals(0.5f, midpoint.blue, 0.003f)
        assertEquals(0.5f, midpoint.alpha, 0.003f)

        val modeKeys = listOf(
            Keyframe(0L, BlendMode.NORMAL, Curve.LINEAR),
            Keyframe(100L, BlendMode.SCREEN)
        )
        assertEquals(BlendMode.NORMAL, Interpolator.valueAt(modeKeys, 99L, BlendModeType))
        assertEquals(BlendMode.SCREEN, Interpolator.valueAt(modeKeys, 100L, BlendModeType))
    }

    @Test
    fun `empty keys fail with an explicit error`() {
        assertThrows(IllegalArgumentException::class.java) {
            Interpolator.valueAt(emptyList(), 10L, FloatType)
        }
    }

    @Test
    fun `upsert sorts and replaces exactly one time while delete keeps neighbors`() {
        val original = listOf(Keyframe(100L, 1f), Keyframe(0L, 0f), Keyframe(200L, 2f))
        val updated = original.upsertKeyframe(Keyframe(100L, 9f, Curve.LINEAR))

        assertEquals(listOf(0L, 100L, 200L), updated.map(Keyframe<Float>::timeMs))
        assertEquals(9f, updated[1].value, 0f)
        assertEquals(Curve.LINEAR, updated[1].curve)
        assertEquals(listOf(0L, 200L), updated.removeKeyframeAt(100L).map(Keyframe<Float>::timeMs))
    }
}
