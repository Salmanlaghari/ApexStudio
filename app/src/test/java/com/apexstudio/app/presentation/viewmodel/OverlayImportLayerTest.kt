package com.apexstudio.app.presentation.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayImportLayerTest {
    @Test
    fun defaultOverlayImportsAndMultiPickBatchesUseV2() {
        assertEquals(1, overlayImportLayer(null))
        assertEquals(listOf(1, 1, 1), List(3) { overlayImportLayer(null) })
    }

    @Test
    fun explicitOverlayTargetIsPreservedAndClampedToV2ThroughV10() {
        assertEquals(4, overlayImportLayer(4))
        assertEquals(1, overlayImportLayer(0))
        assertEquals(9, overlayImportLayer(12))
    }
}
