package com.apexstudio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bug 5 — keyframes must animate every property the task lists, both in
 * preview and on export. Both of those consume the SAME number: the
 * preview GL effect and `ExportEngine` each call
 * [KeyframeTrack.interpolateAt] per frame, so this test pins that
 * function rather than either renderer.
 *
 * Required properties: position (translateX/Y), scale, rotation,
 * opacity, volume, effectIntensity, filterIntensity, saturation.
 */
class KeyframeAnimationTest {

    private fun kf(
        id: String,
        timeMs: Long,
        tx: Float = 0f,
        ty: Float = 0f,
        scale: Float = 1f,
        rot: Float = 0f,
        opacity: Float = 1f,
        volume: Float = 1f,
        effect: Float = 1f,
        filter: Float = 1f,
        saturation: Float? = null
    ) = Keyframe(
        id = id, timeMs = timeMs, translateX = tx, translateY = ty,
        scale = scale, rotationDeg = rot, opacity = opacity, volume = volume,
        effectIntensity = effect, filterIntensity = filter, saturation = saturation
    )

    /** Two keyframes spanning 10 s with EVERY animated property changed. */
    private val fullTrack = KeyframeTrack(
        listOf(
            kf("a", 0L, tx = 0f, ty = 0f, scale = 1f, rot = 0f,
                opacity = 1f, volume = 1f, effect = 1f, filter = 1f),
            kf("b", 10_000L, tx = 100f, ty = -50f, scale = 2f, rot = 180f,
                opacity = 0f, volume = 0f, effect = 0.5f, filter = 0f)
        )
    )

    @Test
    fun `empty track renders the identity transform`() {
        val t = KeyframeTrack().interpolateAt(1_234L)
        assertEquals(0f, t.translateX, 0.0001f)
        assertEquals(0f, t.translateY, 0.0001f)
        assertEquals(1f, t.scale, 0.0001f)
        assertEquals(0f, t.rotationDeg, 0.0001f)
        assertEquals(1f, t.opacity, 0.0001f)
        assertEquals(1f, t.volume, 0.0001f)
        assertEquals(1f, t.effectIntensity, 0.0001f)
        assertEquals(1f, t.filterIntensity, 0.0001f)
    }

    @Test
    fun `midpoint interpolates all eight required properties`() {
        val t = fullTrack.interpolateAt(5_000L)
        assertEquals("position X", 50f, t.translateX, 0.01f)
        assertEquals("position Y", -25f, t.translateY, 0.01f)
        assertEquals("scale", 1.5f, t.scale, 0.01f)
        assertEquals("rotation", 90f, t.rotationDeg, 0.01f)
        assertEquals("opacity", 0.5f, t.opacity, 0.01f)
        assertEquals("volume", 0.5f, t.volume, 0.01f)
        assertEquals("effect intensity", 0.75f, t.effectIntensity, 0.01f)
        assertEquals("filter intensity", 0.5f, t.filterIntensity, 0.01f)
    }

    @Test
    fun `before the first keyframe clamps to first values`() {
        val t = fullTrack.interpolateAt(-9_999L)
        assertEquals(0f, t.translateX, 0.0001f)
        assertEquals(1f, t.scale, 0.0001f)
        assertEquals(1f, t.opacity, 0.0001f)
        assertEquals(1f, t.volume, 0.0001f)
        assertEquals(1f, t.effectIntensity, 0.0001f)
        assertEquals(1f, t.filterIntensity, 0.0001f)
    }

    @Test
    fun `after the last keyframe clamps to last values`() {
        val t = fullTrack.interpolateAt(999_999L)
        assertEquals(100f, t.translateX, 0.0001f)
        assertEquals(-50f, t.translateY, 0.0001f)
        assertEquals(2f, t.scale, 0.0001f)
        assertEquals(180f, t.rotationDeg, 0.0001f)
        assertEquals(0f, t.opacity, 0.0001f)
        assertEquals(0f, t.volume, 0.0001f)
        assertEquals(0.5f, t.effectIntensity, 0.0001f)
        assertEquals(0f, t.filterIntensity, 0.0001f)
    }

    @Test
    fun `unsorted keyframes are read in time order`() {
        val track = KeyframeTrack(
            listOf(
                kf("late", 8_000L, scale = 2f),
                kf("early", 2_000L, scale = 1f)
            )
        )
        assertEquals(1.5f, track.interpolateAt(5_000L).scale, 0.01f)
    }

    @Test
    fun `saturation interpolates over the pinned subsequence only`() {
        // The middle keyframe pins nothing; a hole must not clamp the ramp.
        val track = KeyframeTrack(
            listOf(
                kf("s0", 0L, saturation = 0f),
                kf("h", 5_000L, saturation = null),
                kf("s1", 10_000L, saturation = 2f)
            )
        )
        assertEquals(1f, requireNotNull(track.interpolateAt(5_000L).saturation), 0.01f)
        assertEquals(0.5f, requireNotNull(track.interpolateAt(2_500L).saturation), 0.01f)
        assertEquals(1.5f, requireNotNull(track.interpolateAt(7_500L).saturation), 0.01f)
    }

    @Test
    fun `saturation ramp survives side-effect properties`() {
        // A scale-only keyframe must not reset the saturation ramp.
        val track = KeyframeTrack(
            listOf(
                kf("s0", 0L, saturation = 0f),
                kf("scale", 5_000L, scale = 3f),
                kf("s1", 10_000L, saturation = 1f)
            )
        )
        // The keyframe AT 5000 is itself a scale keyframe, so the track
        // reads its own value rather than an average of it.
        assertEquals(3f, track.interpolateAt(5_000L).scale, 0.01f)
        assertEquals(0.5f, requireNotNull(track.interpolateAt(5_000L).saturation), 0.01f)
    }

    @Test
    fun `linearity is exact for every property at the quarter point`() {
        val t = fullTrack.interpolateAt(2_500L)
        assertEquals(25f, t.translateX, 0.01f)
        assertEquals(1.25f, t.scale, 0.01f)
        assertEquals(45f, t.rotationDeg, 0.01f)
        assertEquals(0.75f, t.opacity, 0.01f)
        assertEquals(0.75f, t.volume, 0.01f)
        assertEquals(0.875f, t.effectIntensity, 0.01f)
        assertEquals(0.75f, t.filterIntensity, 0.01f)
    }

    @Test
    fun `legacy adapter preserves incoming hold curve behavior on exact key time`() {
        val track = KeyframeTrack(
            listOf(
                Keyframe(id = "a", timeMs = 0L, translateX = 0f),
                Keyframe(id = "b", timeMs = 1_000L, translateX = 10f, curve = KeyframeCurve.HOLD),
                Keyframe(id = "c", timeMs = 2_000L, translateX = 20f, curve = KeyframeCurve.LINEAR)
            )
        )

        assertEquals(0f, track.interpolateAt(999L).translateX, 0f)
        assertEquals(0f, track.interpolateAt(1_000L).translateX, 0f)
        assertEquals(10f, track.interpolateAt(1_001L).translateX, 0.02f)
        assertEquals(15f, track.interpolateAt(1_500L).translateX, 0.02f)
    }

    @Test
    fun `legacy adapter retains stable first duplicate at exact time and last after it`() {
        val track = KeyframeTrack(
            listOf(
                Keyframe(id = "a", timeMs = 0L, translateX = 0f),
                Keyframe(id = "first", timeMs = 1_000L, translateX = 10f),
                Keyframe(id = "last", timeMs = 1_000L, translateX = 20f),
                Keyframe(id = "z", timeMs = 2_000L, translateX = 30f)
            )
        )

        assertEquals(10f, track.interpolateAt(1_000L).translateX, 0f)
        assertEquals(20.01f, track.interpolateAt(1_001L).translateX, 0.02f)
    }
}
