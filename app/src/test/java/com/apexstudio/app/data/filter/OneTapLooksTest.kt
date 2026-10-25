package com.apexstudio.app.data.filter

import com.apexstudio.app.domain.model.VideoAdjustments
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneTapLooksTest {
    @Test
    fun `Viral HDR full intensity stores the exact requested recipe`() {
        val full = OneTapLooks.VIRAL_HDR.applyWithIntensity(1f)
        val expected = GpuFilterConfig(
            filterPresetId = "hdr_punch",
            filterIntensity = 0.85f,
            contrast = 1.12f,
            saturation = 1.35f,
            highlights = 0.85f,
            shadows = 0.05f,
            vibrance = 0.35f,
            clarity = 0.45f,
            sharpness = 0.35f,
            activeProfileId = "viral_hdr"
        )

        assertEquals(expected, full)
        assertEquals("viral_hdr", full.activeProfileId)
        assertEquals("hdr_punch", full.filterPresetId)
        assertEquals(0.85f, full.filterIntensity, 0f)
        assertEquals(1.35f, full.saturation, 0f)
        assertEquals(0.35f, full.vibrance, 0f)
        assertEquals(1.12f, full.contrast, 0f)
        assertEquals(0.45f, full.clarity, 0f)
        assertEquals(0.35f, full.sharpness, 0f)
        assertEquals(0.85f, full.highlights, 0f)
        assertEquals(0.05f, full.shadows, 0f)
        assertEquals(0f, full.brightness, 0f)
        assertEquals(5000f, full.temperature, 0f)
        assertEquals(1f, full.gamma, 0f)
    }

    @Test
    fun `Viral HDR intensity interpolates every recipe field from neutral`() {
        val zero = OneTapLooks.VIRAL_HDR.applyWithIntensity(0f)
        assertEquals(0f, zero.filterIntensity, 0f)
        assertEquals(1f, zero.contrast, 0f)
        assertEquals(1f, zero.saturation, 0f)
        assertEquals(0f, zero.vibrance, 0f)
        assertEquals(0f, zero.clarity, 0f)
        assertEquals(0f, zero.sharpness, 0f)
        assertEquals(1f, zero.highlights, 0f)
        assertEquals(0f, zero.shadows, 0f)

        val half = OneTapLooks.VIRAL_HDR.applyWithIntensity(0.5f)
        assertEquals(0.425f, half.filterIntensity, 0.000001f)
        assertEquals(1.06f, half.contrast, 0.000001f)
        assertEquals(1.175f, half.saturation, 0.000001f)
        assertEquals(0.175f, half.vibrance, 0.000001f)
        assertEquals(0.225f, half.clarity, 0.000001f)
        assertEquals(0.175f, half.sharpness, 0.000001f)
        assertEquals(0.925f, half.highlights, 0.000001f)
        assertEquals(0.025f, half.shadows, 0.000001f)

        assertEquals(OneTapLooks.VIRAL_HDR.config, OneTapLooks.VIRAL_HDR.applyWithIntensity(1f))
    }

    @Test
    fun `export mapping preserves target effects with renderer neutral offsets`() {
        val atZero = OneTapLooks.VIRAL_HDR.applyWithIntensity(0f).toVideoAdjustments()
        assertEquals(VideoAdjustments(), atZero)

        val atFull = OneTapLooks.VIRAL_HDR.applyWithIntensity(1f).toVideoAdjustments()
        assertEquals(1.12f, atFull.contrast, 0f)
        assertEquals(1.35f, atFull.saturation, 0f)
        assertEquals(0.35f, atFull.vibrance, 0f)
        assertEquals(0.45f, atFull.clarity, 0f)
        assertEquals(0.35f, atFull.sharpness, 0f)
        assertEquals(-0.15f, atFull.highlights, 0.000001f)
        assertEquals(0.05f, atFull.shadows, 0f)
        assertTrue(OneTapLooks.isValidRenderConfig(OneTapLooks.VIRAL_HDR.applyWithIntensity(0.5f)))
    }

    @Test
    fun `saved recipe config round trips with the stable preset id`() {
        val config = OneTapLooks.VIRAL_HDR.applyWithIntensity(0.5f)
        val json = Json { encodeDefaults = true }
        val restored = json.decodeFromString<GpuFilterConfig>(json.encodeToString(config))

        assertEquals(config, restored)
        assertEquals("viral_hdr", restored.activeProfileId)
        assertEquals(0.5f, restored.profileIntensity, 0f)
    }
}
