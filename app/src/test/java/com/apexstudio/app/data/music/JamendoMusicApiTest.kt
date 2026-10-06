package com.apexstudio.app.data.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the royalty-free music library's Jamendo mapping.
 *
 * The whole "1000+ legal tracks" feature funnels through
 * [JamendoMusicApi.parseJamendoTracks]: if Jamendo ever changes a field name
 * or shape, these tests fail loudly instead of shipping an empty library.
 * Parsing never throws — malformed payloads degrade to an empty list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JamendoMusicApiTest {

    private val sampleResponse = """
        {
          "headers": {
            "status": "success",
            "code": 0,
            "results_count": { "total": 128400, "count": 2 }
          },
          "results": [
            {
              "id": "1815832",
              "name": "Midnight Drive",
              "duration": 187,
              "artist_name": "Neon Coast",
              "artist_id": "512003",
              "album_name": "Afterglow",
              "license_ccurl": "https://creativecommons.org/licenses/by-sa/3.0/",
              "album_image": "https://usercontent.jamendo.com/img.jpg",
              "audio": "https://prod-1.storage.jamendo.com/stream.mp3",
              "audiodownload": "https://prod-1.storage.jamendo.com/download.mp3",
              "musicinfo": {
                "tags": {
                  "genres": ["electronic", "synthwave"],
                  "vartags": ["energetic"]
                }
              }
            },
            {
              "id": "777",
              "name": "Quiet Piano",
              "duration": 95,
              "artist_name": "",
              "license_ccurl": "",
              "musicinfo": { "tags": {} }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parse maps a full track correctly`() {
        val tracks = JamendoMusicApi.parseJamendoTracks(sampleResponse)
        assertEquals(2, tracks.size)

        val t = tracks[0]
        assertEquals("1815832", t.id)
        assertEquals("Midnight Drive", t.title)
        assertEquals("Neon Coast", t.artist)
        assertEquals("Afterglow", t.album)
        assertEquals(187_000L, t.durationMs)
        assertEquals("1:27", t.durationLabel)
        assertEquals("https://prod-1.storage.jamendo.com/stream.mp3", t.streamUrl)
        assertEquals("https://prod-1.storage.jamendo.com/download.mp3", t.downloadUrl)
        assertEquals("https://usercontent.jamendo.com/img.jpg", t.artworkUrl)
        assertEquals("CC BY-SA", t.licenseName)
        assertEquals("https://creativecommons.org/licenses/by-sa/3.0/", t.licenseUrl)
        assertEquals(listOf("electronic", "synthwave", "energetic"), t.tags)
        assertTrue(t.isPlayable)
    }

    @Test
    fun `parse fills defaults for missing optional fields`() {
        val t = JamendoMusicApi.parseJamendoTracks(sampleResponse)[1]
        assertEquals("Quiet Piano", t.title)
        assertEquals("Unknown artist", t.artist)
        assertEquals(95_000L, t.durationMs)
        assertEquals("CC", t.licenseName)
        assertTrue(t.tags.isEmpty())
        assertFalse(t.isPlayable)
    }

    @Test
    fun `parse never throws on malformed payloads`() {
        assertTrue(JamendoMusicApi.parseJamendoTracks("").isEmpty())
        assertTrue(JamendoMusicApi.parseJamendoTracks("not json").isEmpty())
        assertTrue(JamendoMusicApi.parseJamendoTracks("""{"results": {}}""").isEmpty())
        assertTrue(JamendoMusicApi.parseJamendoTracks("""{"results": [null, 42]}""").isEmpty())
        // Track without an id is dropped, the valid one survives.
        val mixed = JamendoMusicApi.parseJamendoTracks(
            """{"results": [{"name": "No Id"}, {"id": "9", "name": "Ok"}]}"""
        )
        assertEquals(1, mixed.size)
        assertEquals("9", mixed[0].id)
    }

    @Test
    fun `parseTotal reads the header count`() {
        assertEquals(128400, JamendoMusicApi.parseTotal(sampleResponse))
        assertEquals(0, JamendoMusicApi.parseTotal("garbage"))
    }

    @Test
    fun `licenseShortName handles deed urls`() {
        assertEquals("CC BY-SA", JamendoMusicApi.licenseShortName("https://creativecommons.org/licenses/by-sa/3.0/"))
        assertEquals("CC BY-NC-ND", JamendoMusicApi.licenseShortName("http://creativecommons.org/licenses/by-nc-nd/2.0/"))
        assertEquals("CC BY", JamendoMusicApi.licenseShortName("https://creativecommons.org/licenses/by/4.0"))
        assertEquals("CC", JamendoMusicApi.licenseShortName(""))
        assertEquals("CC", JamendoMusicApi.licenseShortName("https://example.com/x"))
    }

    @Test
    fun `cache freshness uses the 24h ttl`() {
        val now = 1_700_000_000_000L
        assertTrue(JamendoMusicApi.isFresh(now - 1_000L, now))
        assertTrue(JamendoMusicApi.isFresh(now - 23 * 3_600_000L, now))
        assertFalse(JamendoMusicApi.isFresh(now - 25 * 3_600_000L, now))
        assertFalse(JamendoMusicApi.isFresh(0L, now))
        assertFalse(JamendoMusicApi.isFresh(now + 60_000L, now))
    }

    @Test
    fun `unconfigured api returns empty pages without network`() {
        val api = JamendoMusicApi(clientId = "")
        assertFalse(api.isConfigured)
        // Would throw on network if it tried — returns empty instead.
        val page = kotlinx.coroutines.runBlocking { api.popular() }
        assertTrue(page.tracks.isEmpty())
    }
}
