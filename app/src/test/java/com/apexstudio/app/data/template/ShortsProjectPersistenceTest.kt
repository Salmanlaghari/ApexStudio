package com.apexstudio.app.data.template

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.repository.ProjectRepository
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortsProjectPersistenceTest {
    @Test
    fun `saved Shorts project reopens with its local music and complete attribution`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProjectRepository(context)
        val id = "shorts-reopen-${UUID.randomUUID()}"
        val musicBytes = byteArrayOf(5, 8, 13, 21, 34, 55)
        val musicFile = File.createTempFile("shorts-reopen-", ".mp3", context.cacheDir).apply { writeBytes(musicBytes) }
        val template = ShortsTemplateCatalog.byId("shorts_travel_adventure")!!
        val asset = ShortsMusicAsset(
            id = template.musicAssetId,
            templateName = template.name,
            trackTitle = "Verified Test Track",
            artist = "Test Artist",
            fileName = musicFile.name,
            downloadUrl = ShortsMusicRepository.ALLOWED_URL_PREFIX + musicFile.name,
            sourcePageUrl = "https://example.org/source",
            sourceDownloadUrl = "https://example.org/audio.mp3",
            mimeType = "audio/mpeg",
            bytes = musicBytes.size.toLong(),
            durationMs = 90_000L,
            sha256 = sha256Hex(musicBytes),
            license = "CC0 1.0",
            licenseUrl = "https://example.org/license",
            attributionRequired = true,
            attribution = "Test Artist — Verified Test Track",
            changes = "Trimmed to video duration and start, faded in/out (up to 0.5 s), and mixed at 35% volume."
        )
        val baseProject = Project(
            id = id,
            name = "Shorts test project",
            durationMs = 80_000L,
            clips = listOf(
                MediaClip(
                    id = "video-$id",
                    name = "video.mp4",
                    uri = "file:///video.mp4",
                    durationMs = 80_000L,
                    trimEndMs = 80_000L,
                    type = ClipType.VIDEO
                )
            )
        )
        val starter = ShortsTemplateProjectFactory.apply(baseProject, template, asset, musicFile)

        try {
            repository.saveProject(starter)
            val reopened = ProjectRepository(context).findById(id)
            assertNotNull(reopened)
            val savedProject = reopened!!
            val savedTrack = savedProject.audioTracks.single()
            assertEquals(Uri.fromFile(musicFile).toString(), savedTrack.uri)
            assertTrue(File(URI(savedTrack.uri)).isFile)
            assertEquals(asset.license, savedTrack.sourceLicense)
            assertEquals(asset.licenseUrl, savedTrack.sourceLicenseUrl)
            assertEquals(asset.creditText, savedTrack.attributionText)
            assertEquals(asset.changes, savedTrack.sourceChanges)
            assertEquals(asset.sourcePageUrl, savedTrack.sourcePageUrl)
            assertEquals(template.visualPresetId, savedProject.lastTransmissionTemplateId)
            assertEquals(template.titleAnimationId, savedProject.clips.single().textOverlays[0].animationType)
            assertEquals(template.subtitleAnimationId, savedProject.clips.single().textOverlays[1].animationType)
        } finally {
            repository.deleteProject(id)
            musicFile.delete()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
