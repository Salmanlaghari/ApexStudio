package com.apexstudio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportResolutionTest {
    @Test fun `new export settings use editor default resolution`() {
        assertEquals("1080P", ExportSettings().resolution)
    }

    @Test fun `supported labels and historical aliases normalize to canonical values`() {
        assertEquals(listOf("720P", "1080P", "1440P", "4K"), ExportResolution.supported)
        assertEquals("720P", ExportResolution.canonical("720p"))
        assertEquals("1080P", ExportResolution.canonical("1080p"))
        assertEquals("1440P", ExportResolution.canonical("1440p"))
        assertEquals("4K", ExportResolution.canonical("2160p"))
        assertEquals("4K", ExportResolution.canonical("4K UHD"))
        assertEquals("4K", ExportResolution.canonical("8K Ultra HD"))
        assertEquals("1080P", ExportResolution.canonical("unrecognized legacy value"))
    }
}
