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
}
