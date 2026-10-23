package com.apexstudio.app.ai.mediapipe

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import com.apexstudio.app.ai.AiModelUnavailableException
import com.apexstudio.app.ai.models.OnDemandModelPacks
import com.apexstudio.app.ai.models.SourceModelAssets
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BundledMediaPipeModelsCancellationTest {
    private val model = BundledMediaPipeModels.Model.SELFIE_SEGMENTER

    @Test fun cancelledEntryDoesNotCreateStorage() = withContext { context, root ->
        val expected = CancellationException("entry")
        assertSame(expected, cancellation { BundledMediaPipeModels.modelFile(context, model) { throw expected } })
        assertFalse(File(root, "mediapipe").exists())
    }

    @Test fun cancelAfterAssetReadCleansStagingAndRetryInstallsVerifiedModel() = withContext { context, root ->
        val expected = CancellationException("asset chunk")
        var checks = 0
        assertSame(expected, cancellation {
            BundledMediaPipeModels.modelFile(context, model) { if (++checks == 4) throw expected }
        })
        assertEquals(4, checks)
        val directory = File(root, "mediapipe/models")
        assertTrue(directory.isDirectory)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        val installed = BundledMediaPipeModels.modelFile(context, model)
        assertTrue(installed.length() > 0)
        assertFalse(File(directory, "${model.assetName}.tmp").exists())
        assertEquals(installed, BundledMediaPipeModels.modelFile(context, model))
    }

    @Test fun cancelDuringCacheDigestLeavesCacheUntouched() = withContext { context, root ->
        val directory = File(root, "mediapipe/models").apply { mkdirs() }
        val cached = File(directory, model.assetName).apply { writeBytes(ByteArray(16384) { 7 }) }
        val original = cached.readBytes()
        val expected = CancellationException("cache chunk")
        var checks = 0
        assertSame(expected, cancellation {
            BundledMediaPipeModels.modelFile(context, model) { if (++checks == 4) throw expected }
        })
        assertEquals(4, checks)
        assertArrayEquals(original, cached.readBytes())
        assertFalse(File(directory, "${model.assetName}.tmp").exists())
    }

    @Test fun verifiedOnDemandMediaPipePackIsUsedDirectlyWithoutBundledAssetCopy() = withContext { context, root ->
        val pack = OnDemandModelPacks.catalog.getValue("mediapipe-vision-models")
        val installedDirectory = File(File(File(root, "ai-model-packs"), pack.id), pack.revision).apply { mkdirs() }
        context.assets.open(pack.licenseAssetPath).use { source ->
            File(installedDirectory, "LICENSE.txt").outputStream().use { source.copyTo(it) }
        }
        pack.artifacts.forEach { artifact ->
            val output = File(installedDirectory, artifact.path).apply { parentFile?.mkdirs() }
            context.assets.open("models/${artifact.path}").use { source -> output.outputStream().use { source.copyTo(it) } }
        }

        val installed = BundledMediaPipeModels.modelFile(context, BundledMediaPipeModels.Model.FACE)

        assertEquals(File(installedDirectory, "mediapipe/mediapipe-face-landmarker.task").canonicalFile, installed.canonicalFile)
        assertFalse("verified pack should be used directly, not recopied to bundled cache", File(root, "mediapipe").exists())
    }

    @Test fun cancellationBeforeNativeFaceOpenAvoidsInference() = withContext { context, root ->
        val expected = CancellationException("face entry")
        assertSame(expected, cancellation { BundledMediaPipeModels.openFace(context) { throw expected } })
        assertFalse(File(root, "mediapipe").exists())
    }

    @Test fun moderationDeniesBundledPortraitOpenBeforeModelMaterialization() = withContext { context, root ->
        val failure = try {
            BundledMediaPipeModels.openPortraitEffects(context)
            throw AssertionError("Portrait segmentation must remain blocked")
        } catch (expected: AiModelUnavailableException) {
            expected
        }
        assertTrue(failure.message.orEmpty().contains("MediaPipe portrait background effects"))
        assertFalse("Blocked open must not materialize the bundled model", File(root, "mediapipe").exists())
    }

    @Test fun moderationDeniesDirectPortraitOpenBeforeInspectingModelFile() = withContext { context, root ->
        val model = File(root, "not-present.tflite")
        val failure = try {
            MediaPipePortraitEffects.open(context, model)
            throw AssertionError("Direct portrait segmentation open must remain blocked")
        } catch (expected: AiModelUnavailableException) {
            expected
        }
        assertTrue(failure.message.orEmpty().contains("MediaPipe portrait background effects"))
        assertFalse(model.exists())
    }

    private fun cancellation(block: () -> Unit): CancellationException {
        try { block(); throw AssertionError("Expected cancellation") }
        catch (cancelled: CancellationException) { return cancelled }
    }

    private fun withContext(block: (Context, File) -> Unit) {
        val root = Files.createTempDirectory("mediapipe-cancel").toFile()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            private val sourceAssets = SourceModelAssets.assetManager()
            override fun getApplicationContext(): Context = this
            override fun getAssets(): AssetManager = sourceAssets
            override fun getFilesDir(): File = root
            override fun getNoBackupFilesDir(): File = root
        }
        try { block(context, root) } finally { root.deleteRecursively() }
    }
}
