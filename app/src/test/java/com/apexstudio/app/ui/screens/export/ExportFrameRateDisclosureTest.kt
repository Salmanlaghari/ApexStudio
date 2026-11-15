package com.apexstudio.app.ui.screens.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFrameRateDisclosureTest {
    @Test
    fun `60 fps copy states that encoded output is verified`() {
        assertEquals(
            "60 fps output is verified after encoding. No motion interpolation is used.",
            exportFrameRateDisclosure(60)
        )
    }

    @Test
    fun `other rates are described as output targets`() {
        assertEquals(
            "The selected fps is an output target. No motion interpolation is used.",
            exportFrameRateDisclosure(30)
        )
    }

    @Test
    fun `frame rate options keep high rates for selected clips but omit them for whole projects`() {
        assertEquals(listOf(24, 30, 60, 90, 120), exportFrameRateOptionsForScope(wholeProject = false))
        assertEquals(listOf(24, 30, 60), exportFrameRateOptionsForScope(wholeProject = true))
    }

    @Test
    fun `unsupported stored project rate falls back to 60 without changing selected clip support`() {
        assertEquals(60, exportFrameRateForScope(90, wholeProject = true))
        assertEquals(60, exportFrameRateForScope(120, wholeProject = true))
        assertEquals(60, exportFrameRateForScope(60, wholeProject = true))
        assertEquals(120, exportFrameRateForScope(120, wholeProject = false))
    }
}
