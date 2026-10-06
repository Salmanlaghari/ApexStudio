package com.apexstudio.app.data.music

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.apexstudio.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Orchestrates the royalty-free music library UI: catalog browsing/search,
 * streaming preview, and downloading tracks to local files for the timeline.
 *
 * Deliberately a plain class (not an Android ViewModel / not wired into
 * [com.apexstudio.app.presentation.viewmodel.EditorViewModel]) so this
 * workstream stays isolated: the sheet creates it, the sheet releases it,
 * and adding a track to the timeline flows through the existing
 * `onSelectSong(title, filePath, durationMs)` callback.
 */
class MusicLibraryController(
    context: Context,
    private val api: JamendoMusicApi = JamendoMusicApi(
        clientId = BuildConfig.JAMENDO_CLIENT_ID,
        cacheDir = File(context.cacheDir, "jamendo_meta")
    )
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(MusicLibraryUiState(apiConfigured = api.isConfigured))
    val state: StateFlow<MusicLibraryUiState> = _state.asStateFlow()

    private var previewPlayer: ExoPlayer? = null
    private var currentPage = 0
    private var currentQuery: String? = null
    private var currentTag: String? = null
    private var loadJob: Job? = null

    companion object {
        private const val TAG = "MusicLibraryController"
        /** Curated Jamendo tag searches offered as genre chips. */
        val GENRE_TAGS = listOf(
            "chill", "electronic", "cinematic", "hiphop",
            "rock", "ambient", "pop", "jazz", "acoustic", "upbeat"
        )
    }

    /** Initial load: most popular tracks. No-op when no API key is set. */
    fun load() {
        if (!api.isConfigured) return
        if (_state.value.tracks.isNotEmpty() || _state.value.isLoading) return
        currentQuery = null
        currentTag = null
        fetchPage(0, append = false)
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            browsePopular()
            return
        }
        currentQuery = q
        currentTag = null
        _state.update { it.copy(query = q, activeTag = null) }
        fetchPage(0, append = false)
    }

    fun browsePopular() {
        currentQuery = null
        currentTag = null
        _state.update { it.copy(query = "", activeTag = null) }
        fetchPage(0, append = false)
    }

    fun browseTag(tag: String) {
        currentQuery = null
        currentTag = tag
        _state.update { it.copy(query = "", activeTag = tag) }
        fetchPage(0, append = false)
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        fetchPage(currentPage + 1, append = true)
    }

    fun retry() {
        _state.update { it.copy(error = null, isOffline = false) }
        fetchPage(currentPage, append = currentPage > 0)
    }

    fun setTab(tab: MusicLibraryTab) {
        _state.update { it.copy(tab = tab) }
        if (tab == MusicLibraryTab.DISCOVER) load()
    }

    private fun fetchPage(page: Int, append: Boolean) {
        loadJob?.cancel()
        if (!api.isConfigured) return
        _state.update {
            it.copy(
                isLoading = !append,
                isLoadingMore = append,
                error = null,
                isOffline = false
            )
        }
        loadJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    when {
                        currentQuery != null -> api.search(currentQuery!!, page)
                        currentTag != null -> api.byTag(currentTag!!, page)
                        else -> api.popular(page)
                    }
                }
                currentPage = page
                _state.update {
                    it.copy(
                        tracks = if (append) it.tracks + result.tracks else result.tracks,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = result.hasMore,
                        error = null
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Music catalog fetch failed", e)
                val offline = (e as? java.io.IOException)?.message
                    ?.contains("internet", ignoreCase = true) == true
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = e.message ?: "Couldn't load tracks",
                        isOffline = offline
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Preview (streaming, never downloaded for preview)
    // ------------------------------------------------------------------

    fun togglePreview(track: RemoteMusicTrack) {
        if (_state.value.previewingId == track.id) {
            stopPreview()
            return
        }
        val url = track.streamUrl.ifBlank { track.downloadUrl }
        if (url.isBlank()) {
            _state.update { it.copy(error = "No preview available for this track") }
            return
        }
        try {
            stopPreview()
            val player = ExoPlayer.Builder(appContext).build().also { previewPlayer = it }
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) stopPreview()
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "Preview failed for ${track.title}", error)
                    stopPreview()
                    _state.update { s -> s.copy(error = "Preview failed — check your connection") }
                }
            })
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
            _state.update { it.copy(previewingId = track.id, error = null) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start preview", e)
            _state.update { it.copy(error = "Preview failed — check your connection") }
        }
    }

    fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {
        }
        previewPlayer = null
        _state.update { it.copy(previewingId = null) }
    }

    // ------------------------------------------------------------------
    // Download -> local file -> add to timeline
    // ------------------------------------------------------------------

    /**
     * Downloads [track] to app-private storage and invokes [onAdded] with the
     * local file path, title and duration so the caller can add it to the
     * timeline via the normal `addAudioTrack` path (preview + export pick it
     * up like any other audio track).
     */
    fun downloadAndAdd(
        track: RemoteMusicTrack,
        onAdded: (title: String, filePath: String, durationMs: Long) -> Unit
    ) {
        val url = track.downloadUrl.ifBlank { track.streamUrl }
        if (url.isBlank()) {
            _state.update { it.copy(error = "No downloadable file for this track") }
            return
        }
        if (track.id in _state.value.downloadingIds) return
        stopPreview()
        _state.update {
            it.copy(
                downloadingIds = it.downloadingIds + track.id,
                downloadProgress = it.downloadProgress + (track.id to 0f),
                error = null
            )
        }
        scope.launch {
            try {
                val dest = withContext(Dispatchers.IO) {
                    val dir = File(appContext.filesDir, "music").apply { mkdirs() }
                    val safeId = track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64)
                    val file = File(dir, "jamendo_$safeId.mp3")
                    if (!file.exists() || file.length() < 1024) {
                        downloadToFile(url, file) { progress ->
                            _state.update { s ->
                                s.copy(downloadProgress = s.downloadProgress + (track.id to progress))
                            }
                        }
                    }
                    file
                }
                _state.update {
                    it.copy(
                        downloadingIds = it.downloadingIds - track.id,
                        downloadProgress = it.downloadProgress - track.id
                    )
                }
                onAdded(track.title, dest.absolutePath, track.durationMs)
            } catch (e: Exception) {
                Log.w(TAG, "Track download failed: ${track.title}", e)
                _state.update {
                    it.copy(
                        downloadingIds = it.downloadingIds - track.id,
                        downloadProgress = it.downloadProgress - track.id,
                        error = "Download failed — check your connection and retry",
                        isOffline = (e as? java.io.IOException)?.message
                            ?.contains("internet", ignoreCase = true) == true
                    )
                }
            }
        }
    }

    private fun downloadToFile(url: String, dest: File, onProgress: (Float) -> Unit) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "ApexStudio/1.0 (Android)")
                instanceFollowRedirects = true
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw java.io.IOException("Download HTTP $code")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            // Write to a temp file first so a partial download never looks complete.
            val tmp = File(dest.parentFile, dest.name + ".part")
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (tmp.length() < 1024) {
                tmp.delete()
                throw java.io.IOException("Downloaded file is empty")
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
            onProgress(1f)
        } catch (e: java.net.UnknownHostException) {
            throw java.io.IOException("No internet connection", e)
        } finally {
            connection?.disconnect()
        }
    }

    fun release() {
        loadJob?.cancel()
        stopPreview()
        scope.cancel()
    }
}
