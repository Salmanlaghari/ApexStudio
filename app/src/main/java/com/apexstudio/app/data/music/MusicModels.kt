package com.apexstudio.app.data.music

/**
 * One royalty-free track from a licensed remote catalog.
 *
 * The live catalog is served by the Jamendo API v3, whose entire repertoire
 * is published under Creative Commons licences (see [licenseName]/[licenseUrl]).
 * Nothing here may ever point at copyrighted commercial catalogues
 * (no PagalWorld, no Bollywood rips, no label content): if a source's
 * licensing is unclear it is not used, full stop.
 */
data class RemoteMusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    /** Streaming URL for in-app preview (may be a signed/short-lived CDN URL). */
    val streamUrl: String = "",
    /** Download URL for adding the track to the timeline (saved to a local file). */
    val downloadUrl: String = "",
    val artworkUrl: String = "",
    val licenseName: String = "CC",
    val licenseUrl: String = "",
    val tags: List<String> = emptyList(),
    val source: String = SOURCE_JAMENDO
) {
    companion object {
        const val SOURCE_JAMENDO = "Jamendo"
        const val SOURCE_BUILTIN = "Apex built-in"
    }

    val durationLabel: String
        get() {
            val totalSec = (durationMs / 1000).toInt().coerceAtLeast(0)
            return "%d:%02d".format(totalSec / 60, totalSec % 60)
        }

    /** True when the track can actually be previewed or downloaded. */
    val isPlayable: Boolean
        get() = streamUrl.isNotBlank() || downloadUrl.isNotBlank()
}

/** One page of catalog results. */
data class MusicSearchPage(
    val tracks: List<RemoteMusicTrack>,
    val total: Int = 0,
    val hasMore: Boolean = false
)

enum class MusicLibraryTab { DISCOVER, OFFLINE, IMPORT }

data class MusicLibraryUiState(
    val tab: MusicLibraryTab = MusicLibraryTab.DISCOVER,
    val query: String = "",
    val activeTag: String? = null,
    val tracks: List<RemoteMusicTrack> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    /** True when the failure looks like no connectivity (vs. an API error). */
    val isOffline: Boolean = false,
    val apiConfigured: Boolean = false,
    val previewingId: String? = null,
    val downloadingIds: Set<String> = emptySet(),
    val downloadProgress: Map<String, Float> = emptyMap()
)
