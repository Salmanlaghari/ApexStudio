package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.data.fx.DownloadedFxEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FxSearchCatalogTest {
    @Test
    fun `suggested searches resolve only to existing effect catalog entries`() {
        FxSearchCatalog.suggestedQueries.forEach { query ->
            assertTrue("$query should return a real effect", FxSearchCatalog.search(query, emptyList()).isNotEmpty())
        }
        assertTrue(FxSearchCatalog.search("explosion", emptyList()).any { it.id == "crash_impact" })
        assertTrue(FxSearchCatalog.search("god rays", emptyList()).any { it.id == "sun_beam" })
        assertTrue(FxSearchCatalog.search("flash", emptyList()).any { it.id == "strobe" })
        assertFalse("Unknown terms must not invent placeholder results", FxSearchCatalog.search("butterfly", emptyList()).isNotEmpty())
    }

    @Test
    fun `search matches real built in effects by name category and aliases`() {
        assertTrue(FxSearchCatalog.search("CAMERA", emptyList()).any { it.id == "camera_wobble" })
        assertTrue(FxSearchCatalog.search("soft", emptyList()).any { it.id == "soft_blur" })
        assertTrue(FxSearchCatalog.search("glitch", emptyList()).any { it.id == "glitch" })
        assertTrue(FxSearchCatalog.search("creator pack", emptyList()).any { it.category == "Creator Pack" })
        assertEquals(emptyList<FxSearchResult>(), FxSearchCatalog.search("   ", emptyList()))
    }

    @Test
    fun `downloaded results are searchable and duplicate IDs are ignored`() {
        val downloaded = DownloadedFxEffect(
            id = "dl_apex-fx-vol-01_creator_coral",
            label = "Coral Glow",
            category = "Creator Pack",
            family = "HALATION",
            a = 0.5f,
            b = 4f,
            c = 0.3f,
            tintR = 1f,
            tintG = 0.4f,
            tintB = 0.6f,
            isAnimated = false,
            defaultSpeed = 1f,
        )
        val results = FxSearchCatalog.search("coral", listOf(downloaded, downloaded.copy(label = "Duplicate")))
        val downloadedResults = results.filter { it.downloaded }
        assertEquals(1, downloadedResults.size)
        assertEquals(downloaded.id, downloadedResults.single().id)
        assertTrue(downloadedResults.single().downloaded)
    }

    @Test
    fun `recent queries are newest first case insensitive unique and bounded`() {
        val first = FxSearchCatalog.addRecent(emptyList(), "  God   Rays ")
        val second = FxSearchCatalog.addRecent(first, "glitch")
        val third = FxSearchCatalog.addRecent(second, "god rays")
        assertEquals(listOf("god rays", "glitch"), third)
        assertEquals(third, FxSearchCatalog.addRecent(third, "   "))

        val many = (1..FxSearchCatalog.RECENT_LIMIT + 2).fold(emptyList<String>()) { history, index ->
            FxSearchCatalog.addRecent(history, "effect $index")
        }
        assertEquals(FxSearchCatalog.RECENT_LIMIT, many.size)
        assertEquals("effect ${FxSearchCatalog.RECENT_LIMIT + 2}", many.first())
    }
}
