package com.apexstudio.app.ui.screens.colortools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorVideoExporterTest {
    @Test fun acceptsOnlyExplicitlyVerifiedSdrEightBitFormats() {
        assertTrue(ColorVideoExporter.isVerifiedSdr8Bit("yuv420p", "bt709"))
        assertTrue(ColorVideoExporter.isVerifiedSdr8Bit("NV12", "IEC61966-2-1"))
        assertFalse(ColorVideoExporter.isVerifiedSdr8Bit("yuv420p10le", "bt709"))
        assertFalse(ColorVideoExporter.isVerifiedSdr8Bit("yuv420p", "smpte2084"))
        assertFalse(ColorVideoExporter.isVerifiedSdr8Bit("yuv420p", "unknown"))
    }
}
