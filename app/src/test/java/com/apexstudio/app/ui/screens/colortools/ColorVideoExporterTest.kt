package com.apexstudio.app.ui.screens.colortools

import com.apexstudio.app.data.color.ColorStudioGrade
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorVideoExporterTest {
    @Test fun sharedLutAndSharpnessAreComposedIntoTheStandaloneVideoFilter() {
        assertEquals(
            "lut3d=file=/tmp/grade.cube:interp=trilinear",
            ColorVideoExporter.buildVideoFilter("/tmp/grade.cube", ColorStudioGrade())
        )
        assertEquals(
            "lut3d=file=/tmp/grade.cube:interp=trilinear,format=gbrp," +
                "convolution=0m='0 -0.240 0 -0.240 1.960 -0.240 0 -0.240 0':" +
                "1m='0 -0.240 0 -0.240 1.960 -0.240 0 -0.240 0':" +
                "2m='0 -0.240 0 -0.240 1.960 -0.240 0 -0.240 0':" +
                "0rdiv=1:1rdiv=1:2rdiv=1,format=yuv420p",
            ColorVideoExporter.buildVideoFilter("/tmp/grade.cube", ColorStudioGrade(sharpness = 1f))
        )
    }

    @Test fun acceptsOnlyExplicitlyVerifiedSdrEightBitFormats() {
        assertTrue(ColorVideoExporter.isVerifiedSdr8Bit("yuv420p", "bt709"))
        assertTrue(ColorVideoExporter.isVerifiedSdr8Bit("NV12", "IEC61966-2-1"))
        assertFalse(ColorVideoExporter.isVerifiedSdr8Bit("yuv420p10le", "bt709"))
        assertFalse(ColorVideoExporter.isVerifiedSdr8Bit("yuv420p", "smpte2084"))
        assertFalse(ColorVideoExporter.isVerifiedSdr8Bit("yuv420p", "unknown"))
    }
}
