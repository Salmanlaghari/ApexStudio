package com.apexstudio.app.ai.onnx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

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
        assertNotNull(contract.validateShapes(longArrayOf(1, 3, 0, 16), longArrayOf(1, 3, 16, 16)))
        assertNotNull(contract.validateShapes(longArrayOf(1, 3, -2, 16), longArrayOf(1, 3, 16, 16)))
    }

    @Test fun sha256IsComputedFromBytesNotFilename() {
        val file = File.createTempFile("model", ".onnx")
        try {
            file.writeText("not an onnx model")
            assertEquals("5deba1316db96de611093d678d58229ef5ea550acbeb5ab681cccbcbe3e7d1b7", OnnxImageModels.sha256(file))
        } finally { file.delete() }
    }

    @Test fun plausibleMetadataDoesNotSelfApproveAnImageModel() {
        val supplied = ImageModelArtifact("made-up", "1", "a".repeat(64), "https://example.invalid/model", "MIT", ImageTask.STYLE_TRANSFER)
        assertFalse(ImageModelReviewCatalog.isReviewed(supplied))
    }

    @Test(expected = IllegalArgumentException::class)
    fun artifactRejectsUnpinnedOrMalformedIdentity() {
        ImageModelArtifact("model.onnx", "latest", "not-a-digest", "https://example.invalid/model", "MIT", ImageTask.STYLE_TRANSFER)
    }
}
