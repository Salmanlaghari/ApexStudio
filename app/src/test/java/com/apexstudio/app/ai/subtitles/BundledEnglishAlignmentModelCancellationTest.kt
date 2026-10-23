package com.apexstudio.app.ai.subtitles

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import com.apexstudio.app.ai.models.SourceModelAssets
import java.io.File
import java.io.RandomAccessFile
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
class BundledEnglishAlignmentModelCancellationTest {
    @Test
    fun cancellationAfterAssetChunkReadPreservesIdentityAndRemovesStaging() {
        val filesDir = Files.createTempDirectory("alignment-copy-cancel").toFile()
        val context = TestContext(RuntimeEnvironment.getApplication(), filesDir)
        val expected = CancellationException("cancel after copied asset chunk")
        var checks = 0
        val modelRoot = File(filesDir, "models/alignment")

        try {
            val actual = try {
                BundledEnglishAlignmentModel.modelFile(context) {
                    checks++
                    // Entry, just before asset open, before the first read, then after that read.
                    if (checks == 4) throw expected
                }
                throw AssertionError("Expected cancellation during the asset copy")
            } catch (cancelled: CancellationException) {
                cancelled
            }

            assertSame(expected, actual)
            assertEquals("The callback must reach the post-read check", 4, checks)
            assertTrue("Staging root should have been created", modelRoot.isDirectory)
            assertTrue(
                "Cancelled installation must remove its staging directory",
                modelRoot.listFiles().orEmpty().none { it.name.startsWith(".staging-") },
            )
            assertFalse(File(modelRoot, BundledEnglishAlignmentModel.REVISION).exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun cancellationDuringCachedDigestPreservesIdentity() {
        val filesDir = Files.createTempDirectory("alignment-digest-cancel").toFile()
        val context = TestContext(RuntimeEnvironment.getApplication(), filesDir)
        val modelRoot = File(filesDir, "models/alignment")
        val targetDir = File(modelRoot, BundledEnglishAlignmentModel.REVISION)
        val target = File(targetDir, "wav2vec2-base-960h-int8.onnx")
        val expected = CancellationException("cancel during cached digest")
        var checks = 0

        try {
            assertTrue(targetDir.mkdirs())
            // A sparse, correctly sized file exercises the real chunked verifier without a huge fixture.
            RandomAccessFile(target, "rw").use { it.setLength(BundledEnglishAlignmentModel.SIZE_BYTES) }
            File(targetDir, ".verified").writeText(BundledEnglishAlignmentModel.REVISION)

            val actual = try {
                BundledEnglishAlignmentModel.modelFile(context) {
                    checks++
                    // Method entry, verifier entry, before first digest read, after first digest read.
                    if (checks == 4) throw expected
                }
                throw AssertionError("Expected cancellation during cached digest verification")
            } catch (cancelled: CancellationException) {
                cancelled
            }

            assertSame(expected, actual)
            assertEquals("The callback must reach the post-read check", 4, checks)
            assertTrue(target.isFile)
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
