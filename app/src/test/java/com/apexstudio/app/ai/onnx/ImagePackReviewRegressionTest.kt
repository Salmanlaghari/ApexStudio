package com.apexstudio.app.ai.onnx

import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImagePackReviewRegressionTest {
    @Test fun `MODNet mask uses explicit row stride without reordering pixels`() {
        val pixels = intArrayOf(Color.BLACK, Color.WHITE, Color.GRAY, Color.DKGRAY, Color.LTGRAY, Color.RED)
        val bitmap = ModNetBackgroundRemovalAdapter.maskBitmap(pixels, 3, 2)
        try {
            assertEquals(3, bitmap.width)
            assertEquals(2, bitmap.height)
            for (y in 0 until 2) for (x in 0 until 3) assertEquals(pixels[y * 3 + x], bitmap.getPixel(x, y))
        } finally { bitmap.recycle() }
    }

    @Test fun `MODNet mask rejects missing rows and overflowing dimensions`() {
        for ((w, h) in listOf(3 to 2, 0 to 2, Int.MAX_VALUE to Int.MAX_VALUE)) {
            try { ModNetBackgroundRemovalAdapter.maskBitmap(intArrayOf(Color.BLACK), w, h); fail("Invalid shape accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun `Candy cache requires pinned size and digest and stripped pack never auto downloads`() {
        val context = RuntimeEnvironment.getApplication()
        val target = File(context.filesDir, "ai-models/style-transfer/candy-9.onnx")
        target.parentFile!!.mkdirs()
        try {
            target.writeBytes(byteArrayOf(1, 2, 3))
            assertFalse(CandyStyleTransferPack.isVerifiedFile(target))
            val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
            val fixture = File(root, "app/src/debug/assets/models/style_transfer/candy-9.onnx")
            assertTrue("Repository-only review fixture is missing", fixture.isFile)
            fixture.copyTo(target, overwrite = true)
            val installed = CandyStyleTransferPack.materialize(context)
            assertEquals(CandyStyleTransferPack.SIZE_BYTES, installed.length())
            assertTrue(CandyStyleTransferPack.isVerifiedFile(installed))
            assertEquals(CandyStyleTransferPack.SHA256, OnnxImageModels.sha256(installed))
            assertFalse(File(target.parentFile, "candy-9.onnx.part").exists())
            installed.outputStream().use { out -> out.write(ByteArray(CandyStyleTransferPack.SIZE_BYTES.toInt())) }
            assertFalse(CandyStyleTransferPack.isVerifiedFile(installed))
            try {
                CandyStyleTransferPack.materialize(context)
                fail("Unapproved stripped Candy pack should require explicit installation, never a network fallback")
            } catch (_: IOException) { }
            assertFalse(CandyStyleTransferPack.isVerifiedFile(installed))
            assertFalse(File(target.parentFile, "candy-9.onnx.part").exists())
        } finally {
            target.delete()
            File(target.parentFile, "candy-9.onnx.part").delete()
        }
    }
}
