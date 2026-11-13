package com.apexstudio.app.data.template

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortsTemplateDataLayerTest {
    @Test fun `catalog has exactly ten unique requested recipes`() {
        val expected = setOf(
            "shorts_travel_adventure", "shorts_food_story", "shorts_fitness_challenge",
            "shorts_gaming_highlight", "shorts_fashion_lookbook", "shorts_nature_moment",
            "shorts_tech_showcase", "shorts_sports_hype", "shorts_daily_vlog", "shorts_comedy_reveal"
        )
        assertEquals(expected, ShortsTemplateCatalog.templates.map { it.id }.toSet())
        assertEquals(10, ShortsTemplateCatalog.templates.size)
        assertEquals(10, ShortsTemplateCatalog.templates.map { it.visualPresetId }.distinct().size)
        val bundledFontKeys = setOf("sans", "serif", "monospace", "cursive", "poppins", "montserrat", "bebas", "anton")
        assertTrue(ShortsTemplateCatalog.templates.all {
            it.titleFontFamily in bundledFontKeys && it.subtitleFontFamily in bundledFontKeys
        })
        val transmissionLooks = ShortsTemplateCatalog.asTransmissionTemplates()
        assertEquals(10, transmissionLooks.size)
        assertTrue(transmissionLooks.all { it.shortsOnly && it.royaltyTrackId.isBlank() && it.musicTitle.isBlank() })
    }

    @Test fun `recipes expose valid music ids and bounded animated overlays`() {
        val musicIds = setOf("travel-adventure", "food-and-cooking", "fitness-motivation", "gaming", "fashion-and-beauty", "nature-and-calm", "tech-product-showcase", "sports-action", "daily-creator-vlog", "comedy-and-fun")
        ShortsTemplateCatalog.templates.forEach { template ->
            assertTrue(template.musicAssetId in musicIds)
            val overlays = template.sampleOverlays(7_000L)
            assertEquals(2, overlays.size)
            overlays.forEach {
                assertTrue(it.text.isNotBlank())
                assertTrue(it.startMs >= 0L && it.endMs <= 7_000L && it.endMs > it.startMs)
                assertTrue(it.animationType in ShortsTemplateRecipeCompat.animations)
            }
        }
    }

    @Test fun `factory trims main clip and creates matching music track`() {
        val template = ShortsTemplateCatalog.byId("shorts_travel_adventure")!!
        val project = Project("p", "Draft", 80_000L, clips = listOf(MediaClip("v", "v.mp4", "file:///v.mp4", 80_000L, trimEndMs = 80_000L, type = ClipType.VIDEO)))
        val audioFile = File.createTempFile("shorts-template-test-", ".mp3")
        try {
            audioFile.writeBytes(byteArrayOf(1))
            val asset = ShortsMusicAsset(
                id = template.musicAssetId,
                templateName = template.name,
                trackTitle = "Verified Test Track",
                artist = "Test Artist",
                fileName = audioFile.name,
                downloadUrl = ShortsMusicRepository.ALLOWED_URL_PREFIX + audioFile.name,
                sourcePageUrl = "https://example.org/source",
                sourceDownloadUrl = "https://example.org/audio.mp3",
                mimeType = "audio/mpeg",
                bytes = 1L,
                durationMs = 90_000L,
                sha256 = sha256Hex(audioFile.readBytes()),
                license = "CC0",
                licenseUrl = "https://example.org/license",
                attributionRequired = false,
                attribution = "Verified test track by Test Artist",
                changes = "Trimmed to video duration and start, faded in/out (up to 0.5 s), and mixed at 35% volume."
            )
            val result = ShortsTemplateProjectFactory.apply(project, template, asset, audioFile)
            assertEquals(30_000L, result.durationMs)
            assertEquals(0L, result.clips.single().trimStartMs)
            assertEquals(30_000L, result.clips.single().trimEndMs)
            assertEquals(template.visualPresetId, result.lastTransmissionTemplateId)
            assertEquals(1, result.audioTracks.size)
            assertEquals(30_000L, result.audioTracks.single().trimEndMs)
            assertEquals(90_000L, result.audioTracks.single().sourceDurationMs)
            assertEquals("Verified Test Track", result.audioTracks.single().name)
            assertEquals(0.35f, result.audioTracks.single().volume, 0.0001f)
            assertEquals(com.apexstudio.app.domain.model.AudioTrack.Kind.MUSIC, result.audioTracks.single().kind)
            assertEquals(asset.creditText, result.audioTracks.single().attributionText)
            assertEquals(asset.changes, result.audioTracks.single().sourceChanges)
            assertEquals(2, result.clips.single().textOverlays.size)
        } finally {
            audioFile.delete()
        }
    }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private object ShortsTemplateRecipeCompat {
    val animations = setOf("FADE_IN", "SLIDE_UP", "SLIDE_BOUNCE", "BOUNCE")
}
