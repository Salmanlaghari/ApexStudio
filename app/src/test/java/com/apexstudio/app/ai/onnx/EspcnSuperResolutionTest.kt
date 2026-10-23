package com.apexstudio.app.ai.onnx

import android.graphics.Bitmap
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
class EspcnSuperResolutionTest {
    @Test fun overlapOwnershipCoversEverySourcePixelExactlyOnceIncludingTinyTails() {
        listOf(1, 223, 224, 225, 230, 416, 417, 1280, 10_000).forEach { length ->
            val starts = EspcnSuperResolutionAdapter.tileStarts(length)
            assertEquals(0, starts.first())
            assertTrue(starts.asList().zipWithNext().all { it.first < it.second })
            val covered = IntArray(length)
            starts.indices.forEach { index ->
                EspcnSuperResolutionAdapter.ownedInterval(length, starts, index).forEach { pixel ->
                    assertTrue(pixel in 0 until length)
                    assertTrue(pixel - starts[index] in 0 until EspcnSuperResolutionAdapter.TILE_SIZE)
                    covered[pixel]++
                }
            }
            assertTrue("Each source pixel must be stitched once at length $length", covered.all { it == 1 })
        }
    }

    @Test fun exactX3DimensionsPreserveAspectWithoutPretendingToProduce1080p() {
        assertEquals(230 * 3 to 227 * 3, EspcnSuperResolutionAdapter.outputDimensions(230, 227))
        assertEquals(1 * 3 to 1 * 3, EspcnSuperResolutionAdapter.outputDimensions(1, 1))
    }

    @Test fun oversizedAndOverflowingInputsAreRejectedBeforeTensorAllocation() {
        listOf(0 to 1, 1 to 0, -1 to 2, 1280 to 720, Int.MAX_VALUE to 1).forEach { (width, height) ->
            try {
                EspcnSuperResolutionAdapter.outputDimensions(width, height)
                fail("Unsafe dimensions must fail: $width x $height")
            } catch (_: IllegalArgumentException) {
                // Safe pixel limit or invalid dimensions.
            } catch (_: ArithmeticException) {
                // Checked integer dimension multiplication.
            }
        }
    }

    @Test fun arbitraryModelFileNeverBecomesReadyFromFilenameOrShape() = runBlocking {
        val model = File.createTempFile("fake-super-resolution", ".onnx")
        try {
            model.writeText("not the pinned learned network")
            val result = EspcnSuperResolutionAdapter(model).availability()
            assertTrue(result is ModelAvailability.Unavailable)
            assertTrue((result as ModelAvailability.Unavailable).reason.contains("SHA-256"))
        } finally { model.delete() }
    }

    @Test fun standaloneUpscaleDeniesBeforeModelReadOrProgressOutsideVideoScope() = runBlocking {
        val model = File.createTempFile("fake-super-resolution", ".onnx")
        val image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        var progress = false
        try {
            val adapter = EspcnSuperResolutionAdapter(model)
            model.delete() // If the model is touched before policy, this test fails with the wrong error.
            try {
                adapter.upscale(image) { progress = true }
                fail("Standalone still-image upscaling must remain unavailable")
            } catch (blocked: AiModelUnavailableException) {
                assertTrue(blocked.message!!.contains("Standalone still-image inference is unavailable"))
            }
            assertFalse(progress)
            assertFalse(image.isRecycled)
        } finally { image.recycle(); model.delete() }
    }

    @Test fun onnxSuperResolutionAdapterCannotBypassProtectedActionGate() = runBlocking {
        val model = File.createTempFile("fake-onnx-super-resolution", ".onnx")
        val image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val artifact = ImageModelArtifact(
            "unreviewed", "1", "a".repeat(64), "https://example.invalid/model.onnx", "MIT", ImageTask.SUPER_RESOLUTION
        )
        val contract = ImageModelContract("input", "output", 1, 1, task = ImageTask.SUPER_RESOLUTION)
        var progress = false
        try {
            val adapter = SuperResolutionAdapter(model, artifact, contract)
            model.delete() // Gate must win over model availability/inspection.
            try {
                adapter.upscale(image) { progress = true }
                fail("Standalone ONNX still-image upscaling must remain unavailable")
            } catch (blocked: AiModelUnavailableException) {
                assertTrue(blocked.message.orEmpty().contains("Standalone still-image inference is unavailable"))
            }
            assertFalse(progress)
            assertFalse(image.isRecycled)
        } finally { image.recycle(); model.delete() }
    }
}
