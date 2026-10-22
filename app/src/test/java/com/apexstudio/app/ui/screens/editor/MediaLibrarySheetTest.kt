package com.apexstudio.app.ui.screens.editor

import com.apexstudio.app.data.picker.MediaMetadata
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLibrarySheetTest {
    private val media = listOf(
        item("video", "Canyon.mp4", ClipType.VIDEO),
        item("overlay", "Dust.mp4", ClipType.OVERLAY),
        item("photo", "Portrait.jpg", ClipType.IMAGE),
        item("music", "Score.mp3", ClipType.AUDIO),
        item("sfx", "Whoosh.wav", ClipType.SFX)
    )

    @Test
    fun categoryFiltersOnlyActualMediaOfThatType() {
        assertEquals(listOf("video", "overlay"), filterMediaLibraryItems(media, "Videos", "").map { it.uri })
        assertEquals(listOf("photo"), filterMediaLibraryItems(media, "Photos", "").map { it.uri })
        assertEquals(listOf("music", "sfx"), filterMediaLibraryItems(media, "Audio", "").map { it.uri })
        assertEquals(media, filterMediaLibraryItems(media, "All", ""))
    }

    @Test
    fun searchIsCaseInsensitiveAndComposesWithCategory() {
        assertEquals(listOf("overlay"), filterMediaLibraryItems(media, "Videos", "DUST").map { it.uri })
        assertEquals(emptyList<MediaMetadata>(), filterMediaLibraryItems(media, "Photos", "mp3"))
        assertEquals(listOf("photo"), filterMediaLibraryItems(media, "All", "photo").map { it.uri })
    }

    @Test
    fun emptyInputNeverInventsDemoAssets() {
        assertEquals(emptyList<MediaMetadata>(), filterMediaLibraryItems(emptyList(), "All", ""))
        assertEquals(emptyList<MediaMetadata>(), projectMediaLibraryItems(null))
    }

    @Test
    fun projectMappingPreservesRealUrisAndDeduplicatesClipAndAudioUris() {
        val project = Project(
            id = "project",
            name = "Demo project",
            durationMs = 9_000L,
            clips = listOf(
                MediaClip(id = "clip-v", name = "Canyon", uri = "content://video/1", durationMs = 7_000L, trimEndMs = 7_000L, type = ClipType.VIDEO),
                MediaClip(id = "clip-photo", name = "Portrait", uri = "content://image/2", durationMs = 3_000L, trimEndMs = 3_000L, type = ClipType.IMAGE)
            ),
            audioTracks = listOf(
                AudioTrack(id = "music", name = "Score", uri = "content://audio/3", sourceDurationMs = 12_000L),
                AudioTrack(id = "sfx", name = "Whoosh", uri = "content://audio/4", trimEndMs = 2_000L, kind = AudioTrack.Kind.SFX),
                AudioTrack(id = "duplicate", name = "Duplicate", uri = "content://video/1", sourceDurationMs = 8_000L),
                AudioTrack(id = "empty", name = "No URI", uri = "  ")
            )
        )

        val mapped = projectMediaLibraryItems(project)
        assertEquals(listOf("content://video/1", "content://image/2", "content://audio/3", "content://audio/4"), mapped.map { it.uri })
        assertEquals(listOf(ClipType.VIDEO, ClipType.IMAGE, ClipType.AUDIO, ClipType.SFX), mapped.map { it.type })
        assertEquals(listOf(7_000L, 3_000L, 12_000L, 2_000L), mapped.map { it.durationMs })
        assertEquals(0, mapped[0].width)
        assertEquals(0, mapped[0].fps)
    }

    @Test
    fun browseActionsRouteToOnlyTheirMatchingCallback() {
        val calls = mutableListOf<String>()
        val callbacks = {
            source: MediaLibraryBrowseSource ->
            dispatchMediaLibraryBrowse(
                source,
                onBrowseVideo = { calls += "video" },
                onBrowsePhotos = { calls += "photos" },
                onBrowseOverlay = { calls += "overlay" },
                onBrowseAudio = { calls += "audio" }
            )
        }
        MediaLibraryBrowseSource.values().forEach(callbacks)
        assertEquals(listOf("video", "photos", "overlay", "audio"), calls)
    }

    @Test
    fun selectingLibraryItemImportsItsActualUriThenCloses() {
        var imported: List<MediaMetadata>? = null
        var closed = false
        val item = item("content://media/actual-asset", "Actual photo", ClipType.IMAGE)
        importMediaLibraryItem(item, onImportMedia = { imported = it }, onClose = { closed = true })
        assertEquals(listOf(item), imported)
        assertEquals("content://media/actual-asset", imported?.single()?.uri)
        assertEquals(true, closed)
    }

    private fun item(uri: String, name: String, type: ClipType) = MediaMetadata(
        uri = uri,
        name = name,
        durationMs = 1_000L,
        width = 1920,
        height = 1080,
        fps = 30,
        type = type
    )
}
