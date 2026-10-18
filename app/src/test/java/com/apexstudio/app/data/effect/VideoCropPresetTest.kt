package com.apexstudio.app.data.effect

import com.apexstudio.app.domain.model.VideoCropSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCropPresetTest {
    @Test fun `all ten presets preserve target output aspect on portrait and landscape sources`() {
        assertEquals(10, VideoCropPreset.all.size)
        for (sourceAspect in listOf(16f / 9f, 9f / 16f, 4f / 3f, 1f)) {
            for (preset in VideoCropPreset.all) {
                val rect = preset.centeredWindow(sourceAspect)
                assertTrue(rect.left >= 0f && rect.top >= 0f && rect.right <= 1f && rect.bottom <= 1f)
                assertTrue(rect.width in 0.01f..1f && rect.height in 0.01f..1f)
                preset.aspectRatio?.let { target ->
                    val actual = rect.width * sourceAspect / rect.height
                    assertEquals("${preset.id} for source $sourceAspect", target, actual, 0.0001f)
                } ?: assertEquals(VideoCropSettings(presetId = "free"), rect)
            }
        }
    }

    @Test fun `clip crop settings serialize and restore as project-persistent state`() {
        val expected = VideoCropPreset.PORTRAIT_9_16.centeredWindow(16f / 9f)
        val encoded = Json.encodeToString(expected)
        assertEquals(expected.sanitized(), Json.decodeFromString<VideoCropSettings>(encoded))
        assertEquals("9:16", Json.decodeFromString<VideoCropSettings>(encoded).presetId)
    }
}
