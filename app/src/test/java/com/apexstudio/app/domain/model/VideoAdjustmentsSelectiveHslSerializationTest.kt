package com.apexstudio.app.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAdjustmentsSelectiveHslSerializationTest {
    @Test
    fun selectiveHslRoundTripsAndTriggersTheShaderAdjustmentPath() {
        val grade = VideoAdjustments(
            selectiveHsl = SelectiveHslAdjustments(
                blue = HslBandAdjustment(hueShift = -24f, saturation = 0.3f, lightness = -0.12f),
                magenta = HslBandAdjustment(saturation = -0.4f)
            )
        )

        val decoded = Json.decodeFromString<VideoAdjustments>(Json.encodeToString(grade))

        assertEquals(grade, decoded)
        assertFalse(decoded.isDefault)
        assertTrue(decoded.hasNonMatrixAdjustments)
    }

    @Test
    fun adjustmentsJsonWrittenBeforeHslDefaultsToNoSelectiveGrade() {
        val legacy = Json.decodeFromString<VideoAdjustments>("""{"brightness":0.27,"contrast":1.1}""")

        assertEquals(SelectiveHslAdjustments(), legacy.selectiveHsl)
        assertTrue(legacy.selectiveHsl.isDefault)
    }
}
