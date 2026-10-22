package com.apexstudio.app.data.animation

import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyframeAnimationEffectTest {
    @Test
    fun `visual keyframe properties use mapped project presentation time`() {
        val track = KeyframeTrack(
            listOf(
                Keyframe("start", 0L, opacity = 0.2f, saturation = 0.4f),
                Keyframe("end", 2_000L, opacity = 0.8f, saturation = 1.2f)
            )
        )

        val sample = KeyframeAnimationEffect.sampleAtPresentationTimeUs(
            track = track,
            presentationTimeUs = 1_000_000L,
            timeMsAtPresentationTimeUs = { it / 1_000L + 500L }
        )

        assertEquals(1_500L, sample.timeMs)
        assertEquals(0.65f, sample.transform.opacity, 0.0001f)
        assertEquals(1.0f, sample.saturation, 0.0001f)
    }

    @Test
    fun `shader transform matches Compose geometry in landscape`() {
        val transform = AnimatedTransform(
            translateX = 0.25f,
            translateY = -0.5f,
            scale = 2f,
            rotationDeg = 90f,
            opacity = 0.4f
        )
        val matrix = KeyframeAnimationEffect.transformToGlMatrix(transform, 1920, 1080)

        assertEquals(0f, matrix[0], 0.0001f)
        assertEquals(-2f * 1920f / 1080f, matrix[1], 0.0001f)
        assertEquals(2f * 1080f / 1920f, matrix[4], 0.0001f)
        assertEquals(0f, matrix[5], 0.0001f)
        assertEquals(0.5f, matrix[12], 0.0001f)
        assertEquals(1f, matrix[13], 0.0001f)
    }

    @Test
    fun `shader transform corrects aspect ratio in portrait`() {
        val transform = AnimatedTransform(
            translateX = 0.25f,
            translateY = -0.5f,
            scale = 2f,
            rotationDeg = 90f,
            opacity = 0.4f
        )
        val matrix = KeyframeAnimationEffect.transformToGlMatrix(transform, 1080, 1920)

        assertEquals(-2f * 1080f / 1920f, matrix[1], 0.0001f)
        assertEquals(2f * 1920f / 1080f, matrix[4], 0.0001f)
    }
}
