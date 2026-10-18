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

    @Test fun contractsRejectUnsupportedChannelsAndOversizedInputOrOutputTensors() {
        assertIllegalArgument {
            ImageModelContract("in", "out", 2, 2, outputChannels = 2)
        }
        assertIllegalArgument {
            ImageModelContract("in", "out", Int.MAX_VALUE, Int.MAX_VALUE)
        }
        assertIllegalArgument {
            ImageModelContract("in", "out", 1, 1, outputChannels = 4,
                outputWidth = Int.MAX_VALUE, outputHeight = Int.MAX_VALUE)
        }
        for (channels in listOf(1, 3, 4)) {
            assertIllegalArgument {
                ImageModelContract("in", "out", 1, 1, outputChannels = channels,
                    outputWidth = 2_049, outputHeight = 2_048)
            }
            assertEquals(2_048, ImageModelContract("in", "out", 1, 1, outputChannels = channels,
                outputWidth = 2_048, outputHeight = 2_048).outputWidth)
        }
        assertEquals(listOf(1L, 3L, 2L, 2L), ImageModelContract("in", "out", 2, 2).outputShape.toList())
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try { block(); throw AssertionError("Expected IllegalArgumentException") }
        catch (_: IllegalArgumentException) { }
    }

    @Test(expected = IllegalArgumentException::class)
    fun artifactRejectsUnpinnedOrMalformedIdentity() {
        ImageModelArtifact("model.onnx", "latest", "not-a-digest", "https://example.invalid/model", "MIT", ImageTask.STYLE_TRANSFER)
    }
}
