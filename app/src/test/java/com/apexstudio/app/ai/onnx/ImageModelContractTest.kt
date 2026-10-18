package com.apexstudio.app.ai.onnx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageModelContractTest {
    @Test fun fixedImageContractChecksNchwShapes() {
        val contract = ImageModelContracts.superResolution("image", "upscaled", 32, 24, 4)
        assertNull(contract.validateShapes(longArrayOf(1, 3, 24, 32), longArrayOf(1, 3, 96, 128)))
        assertEquals("input dimension 1 is 1; expected 3",
            contract.validateShapes(longArrayOf(1, 1, 24, 32), longArrayOf(1, 3, 96, 128)))
    }

    @Test fun dynamicModelDimensionsAreAllowedButRankIsNot() {
        val contract = ImageModelContracts.styleTransfer("x", "y", 16, 16)
        assertNull(contract.validateShapes(longArrayOf(1, 3, -1, -1), longArrayOf(1, 3, -1, -1)))
        assertEquals("output rank 3; expected 4 (NCHW)",
            contract.validateShapes(longArrayOf(1, 3, 16, 16), longArrayOf(1, 3, 16)))
    }
}
