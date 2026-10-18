package com.apexstudio.app.ai.onnx

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device smoke only; intentionally compiled in this task but not run without a device.
 * This is not image-quality, model-review, moderation, or safety certification.
 */
@RunWith(AndroidJUnit4::class)
class EspcnSuperResolutionInstrumentedTest {
    @Test fun bundledEspcnRunsLumaContractAndStitchesX3Tiles() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val adapter = EspcnSuperResolutionAdapter.fromBundledAssets(instrumentation.targetContext)
        val availability = adapter.availability()
        assertTrue("Expected pinned one-channel x3 ESPCN, got $availability", availability is ModelAvailability.Ready)
        val source = instrumentation.context.assets.open("super_resolution/espcn_fixture.png").use {
            BitmapFactory.decodeStream(it) ?: error("Fixture failed to decode")
        }
        try {
            val output = adapter.inferForModelValidation(source)
            try {
                assertEquals(source.width * EspcnSuperResolutionAdapter.SCALE, output.width)
                assertEquals(source.height * EspcnSuperResolutionAdapter.SCALE, output.height)
                assertTrue(output.getPixel(output.width / 2, output.height / 2) != source.getPixel(source.width / 2, source.height / 2))
            } finally { output.recycle() }
        } finally { source.recycle() }
    }
}
