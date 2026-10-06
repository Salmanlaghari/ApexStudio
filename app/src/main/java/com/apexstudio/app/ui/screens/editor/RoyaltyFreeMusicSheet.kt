package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.apexstudio.app.data.engine.AudioSynthesizer
import com.apexstudio.app.data.music.MusicLibraryController
import com.apexstudio.app.data.music.MusicLibraryTab
import com.apexstudio.app.data.music.MusicLibraryUiState
import com.apexstudio.app.data.music.RemoteMusicTrack
import com.apexstudio.app.ui.theme.ApexPalette
import java.io.File

// ---------------------------------------------------------------------------
// Built-in offline catalog: tracks are synthesized on-device (see
// AudioSynthesizer), so they are always available — no network, no key,
// and unambiguously royalty-free (Apex-generated, no third-party audio).
// ---------------------------------------------------------------------------

data class RoyaltyFreeSong(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val bpm: Int,
    val durationSeconds: Int,
    val description: String
)

val ROYALTY_FREE_LIBRARY = listOf(
    RoyaltyFreeSong("cyber_pulse", "Cybernetic Pulse", "Apex Soundworks", "Synthwave", 120, 30, "High-energy retro synth arpeggios & deep 808 bass"),
    RoyaltyFreeSong("neon_horizon", "Neon Horizon", "Future Drift", "Cyberpunk", 110, 30, "Atmospheric cyber-noir pads with driving mid-tempo beat"),
    RoyaltyFreeSong("sunset_drive", "Sunset Drive", "LoFi Chill Collective", "Lo-Fi", 85, 30, "Warm mellow chords, gentle vinyl and soothing vibes"),
    RoyaltyFreeSong("ambient_chill", "Ambient Chill", "Zenith", "Ambient", 70, 30, "Deep relaxing harmonic chords for vlog & cinematic visuals"),
    RoyaltyFreeSong("cinematic_drama", "Cinematic Drama", "Orchestral Noir", "Cinematic", 90, 30, "Building orchestral suspense brass & dramatic swells"),
    RoyaltyFreeSong("urban_groove", "Urban Groove", "Metro Beats", "Hip-Hop", 95, 30, "Crisp hi-hats, punchy kick and sliding 808 sub")
)

/**
 * Royalty-free music library sheet with three tabs:
 * - **Discover**: 500k+ real Creative-Commons tracks via the Jamendo API
 *   (search, genre tags, streaming preview, download → timeline).
 * - **Offline**: built-in on-device synthesized tracks (no network needed).
 * - **Import**: pick an audio file from the device into the audio track.
 *
 * Added tracks flow through [onSelectSong] into the normal `addAudioTrack`
 * path, so they play in the live preview and are mixed into the export.
 */
@Composable
fun RoyaltyFreeMusicSheet(
    onSelectSong: (title: String, filePath: String, durationMs: Long) -> Unit,
    onClose: () -> Unit,
    onImportAudio: () -> Unit = {}
) {
    val context = LocalContext.current
    val controller = remember {
        MusicLibraryController(context.applicationContext)
    }
    val uiState by controller.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        "Royalty-Free Music",
                        color = ApexPalette.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Creative-Commons licensed • free for your videos",
                        color = ApexPalette.TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = ApexPalette.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ApexPalette.BgBase)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MusicLibraryTab.entries.forEach { tab ->
                val selected = uiState.tab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { controller.setTab(tab) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (tab) {
                            MusicLibraryTab.DISCOVER -> "Discover"
                            MusicLibraryTab.OFFLINE -> "Offline"
                            MusicLibraryTab.IMPORT -> "Import"
                        },
                        color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (uiState.tab) {
                MusicLibraryTab.DISCOVER -> DiscoverTab(
                    controller = controller,
                    uiState = uiState,
                    onAddTrack = { track ->
                        controller.downloadAndAdd(track) { title, path, durationMs ->
                            onSelectSong(title, path, durationMs)
                            onClose()
                        }
                    }
                )
                MusicLibraryTab.OFFLINE -> OfflineTab(
                    onSelectSong = { title, filePath, durationMs ->
                        onSelectSong(title, filePath, durationMs)
                        onClose()
                    }
                )
                MusicLibraryTab.IMPORT -> ImportTab(onImportAudio = onImportAudio)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Discover tab — Jamendo catalog
// ---------------------------------------------------------------------------

@Composable
private fun DiscoverTab(
    controller: MusicLibraryController,
    uiState: MusicLibraryUiState,
    onAddTrack: (RemoteMusicTrack) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!uiState.apiConfigured) {
            ApiKeySetupCard()
            return
        }

        var query by remember { mutableStateOf(uiState.query) }
        val keyboard = LocalSoftwareKeyboardController.current

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search 500k+ royalty-free tracks…", fontSize = 12.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                controller.search(query)
                keyboard?.hide()
            }),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = ApexPalette.NeonCyan,
                focusedLeadingIconColor = ApexPalette.NeonCyan,
                unfocusedLeadingIconColor = ApexPalette.TextSecondary,
                focusedContainerColor = ApexPalette.BgBase,
                unfocusedContainerColor = ApexPalette.BgBase,
                focusedIndicatorColor = ApexPalette.NeonCyan,
                unfocusedIndicatorColor = Color(0xFF262638)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(8.dp))

        // Genre chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                GenreChip(
                    label = "Popular",
                    selected = uiState.activeTag == null && uiState.query.isBlank(),
                    onClick = { controller.browsePopular() }
                )
            }
            items(MusicLibraryController.GENRE_TAGS) { tag ->
                GenreChip(
                    label = tag.replaceFirstChar { it.uppercase() },
                    selected = uiState.activeTag == tag,
                    onClick = { controller.browseTag(tag) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = ApexPalette.NeonCyan, modifier = Modifier.size(32.dp)) }

            uiState.error != null && uiState.tracks.isEmpty() -> ErrorCard(
                message = uiState.error ?: "Couldn't load tracks",
                isOffline = uiState.isOffline,
                onRetry = { controller.retry() }
            )

            uiState.tracks.isEmpty() -> EmptyCard("No tracks found — try another search.")

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.tracks, key = { it.id }) { track ->
                    DiscoverTrackRow(
                        track = track,
                        isPreviewing = uiState.previewingId == track.id,
                        isDownloading = track.id in uiState.downloadingIds,
                        downloadProgress = uiState.downloadProgress[track.id] ?: 0f,
                        onPreview = { controller.togglePreview(track) },
                        onAdd = { onAddTrack(track) }
                    )
                }
                if (uiState.hasMore || uiState.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isLoadingMore) {
                                CircularProgressIndicator(
                                    color = ApexPalette.NeonCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ApexPalette.BgElevated)
                                        .clickable { controller.loadMore() }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        "Load more",
                                        color = ApexPalette.NeonCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ApexPalette.NeonCyan else ApexPalette.BgElevated)
            .border(
                1.dp,
                if (selected) ApexPalette.NeonCyan else Color(0xFF262638),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else ApexPalette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun DiscoverTrackRow(
    track: RemoteMusicTrack,
    isPreviewing: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onPreview: () -> Unit,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPreviewing) Color(0xFF1F1F35) else Color(0xFF141420))
            .border(
                1.dp,
                if (isPreviewing) ApexPalette.NeonCyan else Color(0xFF262638),
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Artwork
            if (track.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF28283E))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF28283E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = ApexPalette.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Text(
                    text = "${track.artist} • ${track.durationLabel}",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // License badge — CC attribution is required by the licence.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2E2452))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = track.licenseName,
                            color = Color(0xFFA78BFA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (track.tags.isNotEmpty()) {
                        Text(
                            text = track.tags.take(2).joinToString(" • "),
                            color = Color(0xFF6B7280),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // Preview
            IconButton(onClick = onPreview, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPreviewing) "Stop preview" else "Preview",
                    tint = if (isPreviewing) ApexPalette.NeonCyan else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Add to timeline
            if (isDownloading) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = ApexPalette.NeonCyan,
                        trackColor = Color(0xFF28283E)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF3B82F6)))
                        )
                        .clickable { onAdd() }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            "Use",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeySetupCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141420))
            .border(1.dp, Color(0xFF262638), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Unlock 500,000+ royalty-free tracks",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            "The Discover library is powered by the Jamendo API — every track is " +
                "Creative-Commons licensed and free to use in your videos " +
                "(attribution required by the licence).",
            color = Color(0xFF9CA3AF),
            fontSize = 12.sp
        )
        listOf(
            "1. Create a free account at developer.jamendo.com",
            "2. Register an app to get a client ID",
            "3. Set JAMENDO_CLIENT_ID as an env var or in local.properties",
            "4. Rebuild — this tab lights up automatically"
        ).forEach { step ->
            Text(step, color = ApexPalette.TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Meanwhile, the Offline tab and device import work without any key.",
            color = ApexPalette.NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ErrorCard(message: String, isOffline: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141420))
            .border(1.dp, Color(0xFF262638), RoundedCornerShape(12.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (isOffline) Icons.Default.CloudOff else Icons.Default.Refresh,
            contentDescription = null,
            tint = ApexPalette.TextSecondary,
            modifier = Modifier.size(32.dp)
        )
        Text(
            if (isOffline) "You're offline" else "Couldn't load tracks",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
        Text(
            message,
            color = Color(0xFF9CA3AF),
            fontSize = 11.sp
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ApexPalette.NeonCyan.copy(alpha = 0.15f))
                .clickable { onRetry() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Retry",
                color = ApexPalette.NeonCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141420))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = Color(0xFF9CA3AF), fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------------------
// Offline tab — built-in synthesized tracks
// ---------------------------------------------------------------------------

@Composable
private fun OfflineTab(onSelectSong: (String, String, Long) -> Unit) {
    val context = LocalContext.current
    var previewingSongId by remember { mutableStateOf<String?>(null) }
    var previewPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                previewPlayer?.stop()
                previewPlayer?.release()
            } catch (_: Exception) {
            }
        }
    }

    Column {
        Text(
            "BUILT-IN TRACKS — GENERATED ON-DEVICE, ALWAYS AVAILABLE OFFLINE",
            color = ApexPalette.NeonCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ROYALTY_FREE_LIBRARY, key = { it.id }) { song ->
                val isPlayingThis = previewingSongId == song.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isPlayingThis) Color(0xFF1F1F35) else Color(0xFF141420))
                        .border(
                            1.dp,
                            if (isPlayingThis) ApexPalette.NeonCyan else Color(0xFF262638),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isPlayingThis) ApexPalette.NeonCyan else Color(0xFF28283E))
                            .clickable {
                                try {
                                    previewPlayer?.stop()
                                    previewPlayer?.release()
                                    previewPlayer = null
                                } catch (_: Exception) {
                                }
                                if (isPlayingThis) {
                                    previewingSongId = null
                                } else {
                                    val file = File(context.cacheDir, "preview_${song.id}.wav")
                                    if (!file.exists() || file.length() < 1000L) {
                                        AudioSynthesizer.generateRoyaltyFreeTrack(
                                            file, song.id, song.durationSeconds
                                        )
                                    }
                                    try {
                                        val mp = android.media.MediaPlayer().apply {
                                            setDataSource(file.absolutePath)
                                            prepare()
                                            start()
                                        }
                                        previewPlayer = mp
                                        previewingSongId = song.id
                                        mp.setOnCompletionListener { previewingSongId = null }
                                    } catch (_: Exception) {
                                        previewingSongId = null
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Preview",
                            tint = if (isPlayingThis) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF2E2452))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${song.bpm} BPM",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "${song.genre} • ${song.durationSeconds}s",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp
                        )
                        Text(
                            text = song.description,
                            color = Color(0xFF6B7280),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF00E5FF), Color(0xFF3B82F6))
                                )
                            )
                            .clickable {
                                try {
                                    previewPlayer?.stop()
                                    previewPlayer?.release()
                                } catch (_: Exception) {
                                }
                                val destFile = File(
                                    context.cacheDir,
                                    "rf_${song.id}_${System.currentTimeMillis()}.wav"
                                )
                                AudioSynthesizer.generateRoyaltyFreeTrack(
                                    destFile, song.id, song.durationSeconds
                                )
                                onSelectSong(
                                    song.title,
                                    destFile.absolutePath,
                                    song.durationSeconds * 1000L
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                "Use",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Import tab — device audio picker
// ---------------------------------------------------------------------------

@Composable
private fun ImportTab(onImportAudio: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ApexPalette.NeonPurple.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                tint = ApexPalette.NeonPurple,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Import audio from your device",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "MP3, M4A, WAV or OGG — added straight to the\naudio track, synced with the preview.",
            color = Color(0xFF9CA3AF),
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(ApexPalette.NeonPurple, Color(0xFF7C3AED))
                    )
                )
                .clickable { onImportAudio() }
                .padding(horizontal = 28.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Choose audio file",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
