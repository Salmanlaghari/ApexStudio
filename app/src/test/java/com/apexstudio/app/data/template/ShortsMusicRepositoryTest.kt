package com.apexstudio.app.data.template

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.packs.download.DownloadEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortsMusicRepositoryTest {
    @Test
    fun `verified asset is installed and reports progress`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6)
        val manifest = testManifest(bytes.size.toLong(), sha256Hex(bytes), uniquePrefix())
        val asset = manifest.assets.first()
        val destination = File(File(context.filesDir, ShortsMusicRepository.MUSIC_DIRECTORY), asset.fileName)
        val repository = ShortsMusicRepository(context, manifest, ShortsMusicTransfer { url, file, expectedSha, onProgress ->
            assertEquals(asset.downloadUrl, url)
            assertEquals(asset.sha256, expectedSha)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            onProgress(bytes.size.toLong(), bytes.size.toLong())
            DownloadEngine.Result.Success(file)
        })
        var progress: ShortsMusicProgress? = null

        try {
            val result = repository.download(asset.id) { progress = it }
            assertTrue(result is ShortsMusicDownloadResult.Success)
            val success = result as ShortsMusicDownloadResult.Success
            assertEquals(asset, success.asset)
            assertEquals(destination, success.file)
            assertEquals(bytes.size.toLong(), success.file.length())
            assertTrue(DownloadEngine.verifySha256(success.file, asset.sha256))
            assertEquals(ShortsMusicProgress(asset.id, bytes.size.toLong(), bytes.size.toLong()), progress)
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `wrong hash deletes the file and remains retryable`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = byteArrayOf(8, 7, 6, 5)
        val delivered = byteArrayOf(8, 7, 6, 4)
        val manifest = testManifest(expected.size.toLong(), sha256Hex(expected), uniquePrefix())
        val asset = manifest.assets.first()
        val destination = File(File(context.filesDir, ShortsMusicRepository.MUSIC_DIRECTORY), asset.fileName)
        val repository = ShortsMusicRepository(context, manifest, ShortsMusicTransfer { _, file, _, _ ->
            file.parentFile?.mkdirs()
            file.writeBytes(delivered)
            DownloadEngine.Result.Success(file)
        })

        val result = repository.download(asset.id)

        assertTrue(result is ShortsMusicDownloadResult.Failed)
        assertTrue((result as ShortsMusicDownloadResult.Failed).reason.contains("Integrity failure"))
        assertTrue(result.canRetry)
        assertFalse(destination.exists())
    }

    @Test
    fun `wrong byte count is rejected even if the delivered bytes match the declared hash`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val delivered = byteArrayOf(3, 1, 4, 1, 5)
        val manifest = testManifest(delivered.size.toLong() + 1L, sha256Hex(delivered), uniquePrefix())
        val asset = manifest.assets.first()
        val destination = File(File(context.filesDir, ShortsMusicRepository.MUSIC_DIRECTORY), asset.fileName)
        val repository = ShortsMusicRepository(context, manifest, ShortsMusicTransfer { _, file, _, _ ->
            file.parentFile?.mkdirs()
            file.writeBytes(delivered)
            DownloadEngine.Result.Success(file)
        })

        val result = repository.download(asset.id)

        assertTrue(result is ShortsMusicDownloadResult.Failed)
        assertTrue((result as ShortsMusicDownloadResult.Failed).reason.contains("Integrity failure"))
        assertTrue(result.canRetry)
        assertFalse(destination.exists())
    }

    @Test
    fun `unknown asset is not sent to downloader`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifest = testManifest(4L, sha256Hex(byteArrayOf(1, 2, 3, 4)), uniquePrefix())
        val repository = ShortsMusicRepository(context, manifest, ShortsMusicTransfer { _, _, _, _ ->
            error("Unknown asset must not start a download")
        })

        val result = repository.download("not-in-catalog")

        assertTrue(result is ShortsMusicDownloadResult.Failed)
        assertFalse((result as ShortsMusicDownloadResult.Failed).canRetry)
    }

    private fun testManifest(byteCount: Long, sha256: String, prefix: String): ShortsMusicManifest =
        ShortsMusicManifest(
            version = 1,
            assets = (0 until 10).map { index ->
                val fileName = "$prefix-$index.mp3"
                ShortsMusicAsset(
                    id = "$prefix-$index",
                    templateName = "Test Template $index",
                    trackTitle = "Test Track $index",
                    artist = "Test Artist",
                    fileName = fileName,
                    downloadUrl = ShortsMusicRepository.ALLOWED_URL_PREFIX + fileName,
                    sourcePageUrl = "https://example.org/source/$index",
                    sourceDownloadUrl = "https://example.org/audio/$index.mp3",
                    mimeType = "audio/mpeg",
                    bytes = byteCount,
                    durationMs = 30_000L,
                    sha256 = sha256,
                    license = "CC0 1.0",
                    licenseUrl = "https://example.org/license",
                    attributionRequired = true,
                    attribution = "Test Artist — Test Track $index",
                    changes = "Trimmed to video duration and start, faded in/out (up to 0.5 s), and mixed at 35% volume."
                )
            }
        )

    private fun uniquePrefix(): String = "test${UUID.randomUUID().toString().replace("-", "")}"

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
