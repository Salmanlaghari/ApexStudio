package com.apexstudio.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportVideoEffectLayersTest {
    @Test
    fun mainVideoTransformsRunBeforePipAndForegroundRunsAbovePip() {
        val effects = composeVideoEffectLayers(
            mainVideoEffects = listOf("crop", "rotation", "mask", "grade", "speed", "keyframes"),
            pipOverlayEffects = listOf("pip-1", "pip-2"),
            foregroundEffects = listOf("caption", "sticker")
        )

        assertEquals(
            listOf("crop", "rotation", "mask", "grade", "speed", "keyframes", "pip-1", "pip-2", "caption", "sticker"),
            effects
        )
    }

    @Test
    fun emptyOverlayLayersPreserveMainAndForegroundOrdering() {
        assertEquals(
            listOf("crop", "caption"),
            composeVideoEffectLayers(listOf("crop"), emptyList(), listOf("caption"))
        )
    }
}
