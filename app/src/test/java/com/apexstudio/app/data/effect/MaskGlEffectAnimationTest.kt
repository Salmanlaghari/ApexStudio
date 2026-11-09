package com.apexstudio.app.data.effect

import com.apexstudio.app.domain.model.MaskKeyframe
import com.apexstudio.app.domain.model.VideoMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MaskGlEffectAnimationTest {
    private fun animatedMask() = VideoMask(
        centerX = 0.1f,
        maskKeyframes = listOf(
            MaskKeyframe(id = "start", timeMs = 0L, centerX = 0.2f, sizeX = 0.3f),
            MaskKeyframe(id = "end", timeMs = 1_000L, centerX = 0.8f, sizeX = 0.9f)
        )
    )

    @Test
    fun frameResolverReadsCurrentProviderForEveryFrame() {
        val mask = animatedMask()
        var playheadMs = 0L
        val timeProvider = { playheadMs }

        val first = MaskGlEffect.resolveMaskAtFrame(mask, 50_000L, timeProvider)
        playheadMs = 500L
        val next = MaskGlEffect.resolveMaskAtFrame(mask, 50_000L, timeProvider)

        assertEquals(0.2f, first.centerX, 0.0001f)
        assertEquals(0.3f, first.sizeX, 0.0001f)
        assertEquals(0.5f, next.centerX, 0.0001f)
        assertEquals(0.6f, next.sizeX, 0.0001f)
    }

    @Test
    fun absentProviderUsesFramePresentationTimestamp() {
        val resolved = MaskGlEffect.resolveMaskAtFrame(animatedMask(), 500_000L)

        assertEquals(0.5f, resolved.centerX, 0.0001f)
        assertEquals(0.6f, resolved.sizeX, 0.0001f)
    }

    @Test
    fun exportClockMapsSourcePresentationTimeIntoProjectAnimationTime() {
        val resolved = MaskGlEffect.resolveMaskAtFrame(
            animatedMask(),
            presentationTimeUs = 2_500_000L,
            timeMsAtPresentationTimeUs = { sourceUs -> sourceUs / 1_000L - 2_000L }
        )
        assertEquals(0.5f, resolved.centerX, 0.0001f)
        assertEquals(0.6f, resolved.sizeX, 0.0001f)
    }

    @Test
    fun animatedNoOpBaseIsKeptBecauseLaterKeyframesCanChangeGeometry() {
        val animated = VideoMask(
            centerX = 0.5f,
            sizeX = 1f,
            sizeY = 1f,
            maskKeyframes = listOf(
                MaskKeyframe(id = "start", timeMs = 0L, sizeX = 1f, sizeY = 1f),
                MaskKeyframe(id = "end", timeMs = 1_000L, sizeX = 0.4f, sizeY = 0.4f)
            )
        )

        assertNotNull(MaskGlEffect.fromMask(animated))
    }
}
