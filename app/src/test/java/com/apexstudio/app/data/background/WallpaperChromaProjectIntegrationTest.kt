package com.apexstudio.app.data.background

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.export.projectCompositionUnsupportedReason
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import com.apexstudio.app.data.repository.ProjectRepository
import com.apexstudio.app.domain.model.ChromaKeySettings
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.FaceCropKeyframe
import com.apexstudio.app.domain.model.FaceCropTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Contract test for the fragile wallpaper identity lifecycle. It stages the real,
 * pinned repository pack locally rather than using the fixed remote manifest URL,
 * and uses a synthetic processed URI so no media codec or segmentation model runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperChromaProjectIntegrationTest {
    @Test
    fun `verified installed wallpaper survives chroma apply project reopen and export preflight`() = runBlocking {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val isolatedRoot = File(baseContext.cacheDir, "wallpaper-chroma-${UUID.randomUUID()}").apply { mkdirs() }
        val packContext = IsolatedPackContext(baseContext, isolatedRoot)
        val projectId = "wallpaper-chroma-${UUID.randomUUID()}"
        val repository = ProjectRepository(baseContext)
        var processedFile: File? = null

        try {
            val repoRoot = repositoryRoot()
            val manifest = Json { ignoreUnknownKeys = true }.decodeFromString<PackManifestRepo.PackManifestFile>(
                File(repoRoot, "packs/manifest.json").readText()
            )
            val pack = manifest.packs.single { it.id == WallpaperCatalogRepository.PACK_ID }
            val archive = File(repoRoot, "remote-assets/backgrounds/${pack.id}.zip")
            assertTrue("bundled source archive is missing: $archive", archive.isFile)

            installVerifiedArchive(packContext, pack, archive)
            assertTrue(PackManager.isDownloaded(packContext, pack.id))
            assertTrue(WallpaperCatalogRepository.validateInstalledPack(packContext, pack))
            val installed = WallpaperCatalogRepository.installedWallpapers(packContext)
            assertEquals(10, installed.size)

            val wallpaper = WallpaperCatalogRepository.resolveInstalledWallpaper(packContext, "cyber-portal")
            assertNotNull(wallpaper)
            wallpaper!!
            assertEquals("cyber-portal", wallpaper.asset.id)
            assertTrue(wallpaper.file.isFile)

            val originalUri = "file:///source/green-screen.mp4"
            val pendingKey = ChromaKeySettings(enabled = true, backgroundType = "studio_neon")
            val sourceClip = MediaClip(
                id = "main-video",
                name = "Green screen source",
                uri = originalUri,
                durationMs = 5_000L,
                trimEndMs = 5_000L,
                type = ClipType.VIDEO,
                faceCropTrack = FaceCropTrack(
                    sourceWidth = 1920,
                    sourceHeight = 1080,
                    outputAspectRatio = 9f / 16f,
                    keyframes = listOf(FaceCropKeyframe(
                        sourceTimestampMs = 0L,
                        left = 0.2f,
                        top = 0f,
                        right = 0.2f + (9f / 16f) * 1080f / 1920f,
                        bottom = 1f,
                        faceFound = true
                    ))
                ),
                chromaKeySettings = pendingKey
            )
            repository.saveProject(Project(projectId, "Wallpaper Apply", 5_000L, clips = listOf(sourceClip)))
            val pendingReopened = ProjectRepository(baseContext).findById(projectId)!!
            val pendingClip = pendingReopened.clips.single()
            assertEquals(pendingKey, pendingClip.chromaKeySettings)
            assertTrue(
                projectCompositionUnsupportedReason(listOf(pendingClip), ExportEngine.ExportConfig())
                    ?.contains("Apply the key and wallpaper before exporting") == true
            )

            // Use the same production helpers as Apply: stable legacy ID mapping,
            // verified asset URI/hash pinning, and the baked-clip commit transform.
            val applySettings = chromaWallpaperBackgroundSettings(pendingKey)
            assertNotNull(applySettings)
            assertEquals("cyber-portal", applySettings!!.imageAssetId)
            val effectiveSettings = applySettings.copy(sourceUri = originalUri)
                .withVerifiedWallpaper(wallpaper)
                .sanitized()
            assertEquals(wallpaper.uri.toString(), effectiveSettings.imageUri)
            assertEquals(wallpaper.asset.sha256, effectiveSettings.imageAssetSha256)
            assertEquals("cyber-portal", effectiveSettings.chromaKeySettings?.backgroundType)

            processedFile = File.createTempFile("wallpaper-chroma-baked-", ".mp4", baseContext.cacheDir)
                .apply { writeBytes(byteArrayOf(0, 0, 0, 1)) }
            val bakedClip = bakedBackgroundReplacementClip(
                pendingClip,
                Uri.fromFile(processedFile!!).toString(),
                effectiveSettings
            )
            assertEquals(processedFile!!.absolutePath, Uri.parse(bakedClip.uri).path)
            assertEquals(originalUri, bakedClip.backgroundReplace?.sourceUri)
            assertEquals("cyber-portal", bakedClip.backgroundReplace?.imageAssetId)
            assertEquals(wallpaper.asset.sha256, bakedClip.backgroundReplace?.imageAssetSha256)
            assertNull(bakedClip.chromaKeySettings)
            assertNull(bakedClip.faceCropTrack)

            repository.saveProject(pendingReopened.copy(clips = listOf(bakedClip)))
            val reopenedAppliedClip = ProjectRepository(baseContext).findById(projectId)!!.clips.single()
            assertEquals(bakedClip.uri, reopenedAppliedClip.uri)
            assertEquals(originalUri, reopenedAppliedClip.backgroundReplace?.sourceUri)
            assertEquals(wallpaper.uri.toString(), reopenedAppliedClip.backgroundReplace?.imageUri)
            assertEquals("cyber-portal", reopenedAppliedClip.backgroundReplace?.imageAssetId)
            assertEquals(wallpaper.asset.sha256, reopenedAppliedClip.backgroundReplace?.imageAssetSha256)
            assertEquals(pendingKey.copy(backgroundType = "cyber-portal"), reopenedAppliedClip.backgroundReplace?.chromaKeySettings)
            assertNull(reopenedAppliedClip.chromaKeySettings)
            assertNull(reopenedAppliedClip.faceCropTrack)
            assertFalse(reopenedAppliedClip.uri == originalUri)
            assertNull(projectCompositionUnsupportedReason(listOf(reopenedAppliedClip), ExportEngine.ExportConfig()))
        } finally {
            repository.deleteProject(projectId)
            processedFile?.delete()
            isolatedRoot.deleteRecursively()
        }
    }

    private fun installVerifiedArchive(
        context: Context,
        pack: PackManifestRepo.RemotePack,
        archive: File
    ) {
        val installDir = PackManager.packDir(context, pack.id)
        installDir.deleteRecursively()
        check(installDir.mkdirs())
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
        val registryFile = File(PackManager.packsRoot(context), "installed.json")
        registryFile.writeText(
            """{"packs":{"${pack.id}":{"version":${pack.version},"sha256":"${pack.sha256}"}}}"""
        )
    }

    private fun repositoryRoot(): File {
        var current = File(System.getProperty("user.dir") ?: ".").canonicalFile
        while (true) {
            if (File(current, "remote-assets/backgrounds/${WallpaperCatalogRepository.PACK_ID}.zip").isFile) return current
            current = current.parentFile ?: error("Could not locate repository root for wallpaper archive")
        }
    }

    private class IsolatedPackContext(
        base: Context,
        private val root: File
    ) : ContextWrapper(base) {
        override fun getExternalFilesDir(type: String?): File =
            File(root, type ?: "external").apply { mkdirs() }
    }
}
