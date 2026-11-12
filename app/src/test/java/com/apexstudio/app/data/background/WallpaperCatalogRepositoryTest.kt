package com.apexstudio.app.data.background

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperCatalogRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `bundled catalog is serializable with unique stable ids and pinned dimensions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = WallpaperCatalogRepository.bundledCatalog(context)

        assertEquals(1, catalog.schemaVersion)
        assertEquals(WallpaperCatalogRepository.PACK_ID, catalog.packId)
        assertEquals(10, catalog.wallpapers.size)
        assertEquals(catalog.wallpapers.size, catalog.wallpapers.map { it.id }.distinct().size)
        assertTrue(catalog.wallpapers.all {
            it.width == 1920 && it.height == 1080 && it.sha256.matches(Regex("[0-9a-f]{64}"))
        })
        assertEquals(catalog, json.decodeFromString<WallpaperCatalog>(json.encodeToString(catalog)))
    }

    @Test
    fun `download archive and remote manifest match bundled catalog and every asset pin`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bundled = WallpaperCatalogRepository.bundledCatalog(context)
        val root = repositoryRoot()
        val archive = File(root, "remote-assets/backgrounds/apexstudio-wallpapers-vol-01.zip")
        val remoteManifestFile = File(root, "packs/manifest.json")
        assertTrue("wallpaper ZIP missing: $archive", archive.isFile)
        assertTrue("pack manifest missing: $remoteManifestFile", remoteManifestFile.isFile)

        val remoteManifest = json.decodeFromString<PackManifestRepo.PackManifestFile>(remoteManifestFile.readText())
        val pack = remoteManifest.packs.single { it.id == WallpaperCatalogRepository.PACK_ID }
        assertEquals("backgrounds", pack.category)
        assertEquals(WallpaperCatalogRepository.PAYLOAD_TYPE, pack.payloadType)
        assertEquals(bundled.wallpapers.size, pack.itemCount)
        assertEquals(archive.length(), pack.sizeBytes)
        assertEquals(sha256(archive.readBytes()), pack.sha256)

        ZipFile(archive).use { zip ->
            val embeddedEntry = zip.getEntry(pack.entry)
            assertNotNull("embedded catalog missing from wallpaper ZIP", embeddedEntry)
            val embeddedBytes = zip.getInputStream(embeddedEntry).use { it.readBytes() }
            assertEquals(pack.entrySha256, sha256(embeddedBytes))
            val embedded = json.decodeFromString<WallpaperCatalog>(String(embeddedBytes, Charsets.UTF_8))
            assertEquals(bundled, embedded)

            bundled.wallpapers.forEach { asset ->
                val entry = zip.getEntry(asset.assetPath)
                assertNotNull("missing wallpaper ${asset.id}", entry)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                assertEquals("wrong size for ${asset.id}", asset.sizeBytes, bytes.size.toLong())
                assertEquals("wrong SHA-256 for ${asset.id}", asset.sha256, sha256(bytes))
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                assertEquals("wrong width for ${asset.id}", asset.width, bounds.outWidth)
                assertEquals("wrong height for ${asset.id}", asset.height, bounds.outHeight)
            }
        }
    }

    @Test
    fun `installed pack validates all assets and rejects a changed image`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = repositoryRoot()
        val archive = File(root, "remote-assets/backgrounds/apexstudio-wallpapers-vol-01.zip")
        val remoteManifest = json.decodeFromString<PackManifestRepo.PackManifestFile>(
            File(root, "packs/manifest.json").readText()
        )
        val pack = remoteManifest.packs.single { it.id == WallpaperCatalogRepository.PACK_ID }
        val installDir = PackManager.packDir(context, pack.id)
        installDir.deleteRecursively()
        check(installDir.mkdirs())

        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val destination = File(installDir, entry.name).canonicalFile
                    check(destination.path.startsWith(installDir.canonicalPath + File.separator))
                    if (entry.isDirectory) {
                        check(destination.mkdirs() || destination.isDirectory)
                    } else {
                        val parent = destination.parentFile
                        check(parent != null && (parent.mkdirs() || parent.isDirectory))
                        zip.getInputStream(entry).use { input ->
                            destination.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
            assertTrue(WallpaperCatalogRepository.validateInstalledPack(context, pack))
            val firstAsset = WallpaperCatalogRepository.bundledCatalog(context).wallpapers.first()
            File(installDir, firstAsset.assetPath).appendBytes(byteArrayOf(0))
            assertFalse(WallpaperCatalogRepository.validateInstalledPack(context, pack))
        } finally {
            installDir.deleteRecursively()
        }
    }

    @Test
    fun `legacy wallpaper names resolve to stable catalog ids`() {
        assertEquals("cyber-portal", WallpaperCatalogRepository.resolveLegacyId("studio_neon"))
        assertEquals("neon-city", WallpaperCatalogRepository.resolveLegacyId("neon_city"))
        assertEquals("deep-space", WallpaperCatalogRepository.resolveLegacyId("deep-space"))
        assertEquals(null, WallpaperCatalogRepository.resolveLegacyId("user-picked-file.jpg"))
    }

    private fun repositoryRoot(): File {
        val userDir = System.getProperty("user.dir") ?: "."
        var current = File(userDir).canonicalFile
        while (true) {
            if (File(current, "remote-assets/backgrounds/apexstudio-wallpapers-vol-01.zip").isFile) return current
            current = current.parentFile ?: error("Could not locate repository root from $userDir")
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
