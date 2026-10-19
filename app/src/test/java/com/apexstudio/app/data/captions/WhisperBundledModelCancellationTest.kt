package com.apexstudio.app.data.captions

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import com.apexstudio.app.ai.models.SourceModelAssets
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WhisperBundledModelCancellationTest {
    private val revision = "65176e2deb88badc814a94058666cadccc29b61c"

    @Test
    fun cancellationAfterAssetReadIsPropagatedAndStagingIsRemoved() {
        val filesDir = Files.createTempDirectory("whisper-copy-cancel").toFile()
        val context = TestContext(RuntimeEnvironment.getApplication(), filesDir)
        val expected = CancellationException("cancel after copied Whisper asset chunk")
        var checks = 0
        val modelRoot = File(filesDir, "models/whisper")

        try {
            val actual = try {
                WhisperCaptionEngine.installBundledModel(context) {
                    checks++
                    // Entry, cache inspection, before staging, asset boundary, before read, after read.
                    if (checks == 6) throw expected
                }
                throw AssertionError("Expected cancellation during bundled Whisper asset copy")
            } catch (cancelled: CancellationException) {
                cancelled
            }

            assertSame(expected, actual)
            assertEquals("Cancellation must be polled immediately after the asset read", 6, checks)
            assertTrue(modelRoot.isDirectory)
            assertTrue(
                "Cancelled install must remove its private staging directory",
                modelRoot.listFiles().orEmpty().none { it.name.startsWith(".staging-") },
            )
            assertFalse(File(modelRoot, revision).exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun cancellationWhileVerifyingCachedAssetPreservesTheExistingFile() {
        val filesDir = Files.createTempDirectory("whisper-digest-cancel").toFile()
        val context = TestContext(RuntimeEnvironment.getApplication(), filesDir)
        val modelRoot = File(filesDir, "models/whisper")
        val targetDir = File(modelRoot, revision)
        val encoder = File(targetDir, WhisperModel.ENCODER_FILE)
        val expected = CancellationException("cancel during cached Whisper digest")
        var checks = 0

        try {
            assertTrue(targetDir.mkdirs())
            // Sparse data gives the real chunked cache verifier its expected size without a model fixture.
            java.io.RandomAccessFile(encoder, "rw").use { it.setLength(12_937_772L) }
            File(targetDir, ".verified-revision").writeText(revision)

            val actual = try {
                WhisperCaptionEngine.installBundledModel(context) {
                    checks++
                    // Installer entry, cache inspection, verifier entry, before read, after first read.
                    if (checks == 5) throw expected
                }
                throw AssertionError("Expected cancellation during cached Whisper verification")
            } catch (cancelled: CancellationException) {
                cancelled
            }

            assertSame(expected, actual)
            assertEquals("Cancellation must be polled immediately after the digest read", 5, checks)
            assertTrue("Cancellation must not delete or replace the cached model", encoder.isFile)
            assertEquals(12_937_772L, encoder.length())
            assertTrue(File(targetDir, ".verified-revision").isFile)
            assertTrue(modelRoot.listFiles().orEmpty().none { it.name.startsWith(".staging-") })
        } finally {
            filesDir.deleteRecursively()
        }
    }

    private class TestContext(base: Context, private val testFilesDir: File) : ContextWrapper(base) {
        private val sourceAssets = SourceModelAssets.assetManager()
        override fun getApplicationContext(): Context = this
        override fun getAssets(): AssetManager = sourceAssets
        override fun getFilesDir(): File = testFilesDir
    }
}
