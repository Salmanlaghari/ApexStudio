package com.apexstudio.app.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeCurveMathTest {
    @Test
    fun `custom cubic Bezier has exact endpoints and linear controls reproduce identity`() {
        val controls = arrayOf(1f / 3f, 1f / 3f, 2f / 3f, 2f / 3f)
        assertEquals(0f, evaluate(0f, controls), 0f)
        assertEquals(1f, evaluate(1f, controls), 0f)
        for (t in listOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f)) {
            assertEquals(t, evaluate(t, controls), 0.00001f)
        }
    }

    @Test
    fun `asymmetric cubic Bezier solves x before evaluating y`() {
        val controls = arrayOf(0.1f, 0.9f, 0.3f, 1.2f)
        val expected = listOf(0.88327964f, 1.04371342f, 1.05260355f)
        listOf(0.25f, 0.5f, 0.75f).forEachIndexed { index, t ->
            assertEquals(expected[index], evaluate(t, controls), 0.00002f)
        }
    }

    @Test
    fun `flat monotonic x controls remain finite and monotonic near endpoints`() {
        val samples = listOf(0f, 0.0001f, 0.001f, 0.01f, 0.1f, 0.5f, 0.9f, 0.9999f, 1f)
            .map { evaluate(it, arrayOf(-1f, 0f, 0f, 1f)) }
        assertEquals(0f, samples.first(), 0f)
        assertEquals(1f, samples.last(), 0f)
        assertTrue(samples.all(Float::isFinite))
        assertTrue(samples.zipWithNext().all { (left, right) -> right + 1e-6f >= left })
    }

    @Test
    fun `custom controls survive keyframe JSON round trip`() {
        val keyframe = Keyframe(
            id = "custom",
            timeMs = 900L,
            curve = KeyframeCurve.BEZIER,
            bezierCp1X = 0.12f,
            bezierCp1Y = -0.2f,
            bezierCp2X = 0.86f,
            bezierCp2Y = 1.24f
        )
        val restored = Json.decodeFromString<Keyframe>(Json.encodeToString(keyframe))
        assertEquals(keyframe, restored)
    }

    @Test
    fun `old keyframe JSON without handles defaults to historical smoothstep`() {
        val legacy = Json.decodeFromString<Keyframe>(
            """{"id":"end","timeMs":1000,"curve":"BEZIER"}"""
        )
        assertNull(legacy.bezierCp1X)
        assertNull(legacy.bezierCp1Y)
        assertNull(legacy.bezierCp2X)
        assertNull(legacy.bezierCp2Y)
        val track = KeyframeTrack(listOf(Keyframe("start", 0L, scale = 0f), legacy))
        assertEquals(0.15625f, track.interpolateAt(250L).scale, 0.00001f)
    }

    @Test
    fun `each of the eight curves matches its documented sample shape`() {
        val expected = mapOf(
            KeyframeCurve.LINEAR to listOf(0.25f, 0.5f, 0.75f),
            KeyframeCurve.EASE_IN to listOf(0.0625f, 0.25f, 0.5625f),
            KeyframeCurve.EASE_OUT to listOf(0.4375f, 0.75f, 0.9375f),
            KeyframeCurve.EASE_IN_OUT to listOf(0.125f, 0.5f, 0.875f),
            KeyframeCurve.BEZIER to listOf(0.15625f, 0.5f, 0.84375f),
            KeyframeCurve.HOLD to listOf(0f, 0f, 0f),
            KeyframeCurve.SPRING to listOf(1.2062397f, 0.9647174f, 1.0042853f),
            KeyframeCurve.OVERSHOOT to listOf(0.8174097f, 1.0876975f, 1.0641366f)
        )
        val samples = listOf(0.25f, 0.5f, 0.75f)
        KeyframeCurve.values().forEach { curve ->
            samples.forEachIndexed { index, t ->
                assertEquals("$curve at $t", expected.getValue(curve)[index], KeyframeCurveMath.evaluate(t, curve), 0.00002f)
            }
        }
    }

    @Test
    fun `keyframe track applies custom controls from its incoming keyframe`() {
        val track = KeyframeTrack(
            listOf(
                Keyframe("start", 0L, scale = 0f),
                Keyframe(
                    "end", 1_000L, scale = 1f, curve = KeyframeCurve.BEZIER,
                    bezierCp1X = 0.1f, bezierCp1Y = 0.9f,
                    bezierCp2X = 0.3f, bezierCp2Y = 1.2f
                )
            )
        )
        assertEquals(0.88327964f, track.interpolateAt(250L).scale, 0.00002f)
    }

    private fun evaluate(t: Float, controls: Array<Float>): Float = KeyframeCurveMath.evaluate(
        progress = t,
        curve = KeyframeCurve.BEZIER,
        bezierCp1X = controls[0],
        bezierCp1Y = controls[1],
        bezierCp2X = controls[2],
        bezierCp2Y = controls[3]
    )
}
