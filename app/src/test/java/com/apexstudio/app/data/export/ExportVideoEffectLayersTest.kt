package com.apexstudio.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportVideoEffectLayersTest {
    @Test fun frameArtworkIsAfterMainGeometryButUnderPipLikePreview() {
        assertEquals(listOf("crop", "output-size", "drawing", "pip", "caption"),
            composeVideoEffectLayers(listOf("crop", "output-size"), listOf("pip"),
                listOf("caption"), mainArtworkEffects = listOf("drawing")))
    }

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
