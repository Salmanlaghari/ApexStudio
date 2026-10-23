package com.apexstudio.app.ai.onnx

import android.graphics.Bitmap
import com.apexstudio.app.ai.AiFeatureId
import com.apexstudio.app.ai.AiFeatureRegistry
import com.apexstudio.app.ai.AiModelUnavailableException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BundledImagePackPolicyTest {
    @Test fun `Candy contract is exact byte-range RGB and does not grant artifact approval`() {
        val contract = CandyStyleTransferPack.contract
        assertEquals("input1", contract.inputName)
        assertEquals("output1", contract.outputName)
        assertEquals(listOf(1L, 3L, 224L, 224L), contract.inputShape.toList())
        assertEquals(FloatRange.BYTE_0_TO_255, contract.inputRange)
        assertEquals(FloatRange.BYTE_0_TO_255, contract.outputRange)
        assertTrue(CandyStyleTransferPack.ARTIFACT_SOURCE_URL.contains(CandyStyleTransferPack.REVISION))
        assertFalse(ImageModelReviewCatalog.isReviewed(CandyStyleTransferPack.artifact))
    }

    @Test fun `bundled ONNX requirements no longer advertise missing placeholder files`() {
        listOf(AiFeatureId.ONNX_STYLE_TRANSFER, AiFeatureId.ONNX_BACKGROUND_REMOVAL).forEach { id ->
            val requirement = AiFeatureRegistry.knownRequirements.getValue(id).single()
            assertTrue(requirement.id.startsWith("bundled:"))
            assertTrue(requirement.source.startsWith("https://"))
            assertFalse(requirement.license.contains("Not supplied"))
        }
    }

    @Test fun `MODNet is denied before model contents image access or progress callbacks`() = runBlocking {
        val file = File.createTempFile("untrusted-modnet", ".onnx")
        val source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        var progressed = false
        try {
            val adapter = ModNetBackgroundRemovalAdapter(file)
            source.recycle()
            try {
                adapter.inferMask(source) { progressed = true }
                fail("Expected protected inference to be denied")
            } catch (denied: AiModelUnavailableException) {
                assertTrue(denied.message.orEmpty().contains("ONNX background removal"))
                assertFalse(progressed)
            }
        } finally {
            if (!source.isRecycled) source.recycle()
            file.delete()
        }
    }
}
