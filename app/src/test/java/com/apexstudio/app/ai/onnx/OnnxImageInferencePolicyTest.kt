package com.apexstudio.app.ai.onnx

import android.graphics.Bitmap
import com.apexstudio.app.ai.AiModelUnavailableException
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnnxImageInferencePolicyTest {
    @Test fun backgroundRemovalAndStyleTransferDenyBeforeModelInspectionOrProgress() = runBlocking {
        val cases = listOf(
            Triple(ImageTask.BACKGROUND_REMOVAL, ProtectedAiAction.ONNX_BACKGROUND_REMOVAL, 1),
            Triple(ImageTask.STYLE_TRANSFER, ProtectedAiAction.ONNX_STYLE_TRANSFER, 3),
        )
        for ((task, action, channels) in cases) {
            val model = File.createTempFile("unreviewed-image-model", ".onnx")
            val image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            var progress = false
            try {
                model.delete() // Policy must deny before opening the absent model.
                val contract = ImageModelContract("input", "output", 1, 1,
                    outputChannels = channels, task = task)
                try {
                    OnnxImageModels.runImage(model, image, contract, onProgress = { progress = true })
                    throw AssertionError("Unmoderated $task inference must be denied")
                } catch (blocked: AiModelUnavailableException) {
                    assertEquals(GlobalModerationEnforcement.statusMessage(action), blocked.message)
                }
                assertFalse(progress)
                assertFalse(image.isRecycled)
            } finally {
                image.recycle()
                model.delete()
            }
        }
    }

    @Test fun undeclaredTaskFailsClosedBeforeModelInspectionOrProgress() = runBlocking {
        val model = File.createTempFile("untyped-image-model", ".onnx")
        val image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        var progress = false
        try {
            model.delete()
            val contract = ImageModelContract("input", "output", 1, 1, task = null)
            try {
                OnnxImageModels.runImage(model, image, contract, onProgress = { progress = true })
                throw AssertionError("Inference without a declared task must be denied")
            } catch (failure: LocalModelException) {
                assertTrue(failure.message.orEmpty().contains("task is not declared"))
            }
            assertFalse(progress)
            assertFalse(image.isRecycled)
        } finally {
            image.recycle()
            model.delete()
        }
    }
}
