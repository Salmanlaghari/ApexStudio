package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexstudio.app.data.music.MusicLibraryController
import com.apexstudio.app.data.music.RemoteMusicTrack
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Phase 3: Free Music Library — proper browser UI over the existing
 * royalty-free catalog ([MusicLibraryController], Jamendo CC-licensed).
 *
 * Categories: Trending / Lofi / Energetic / Emotional (+ search).
 * Tap ▶ to preview, tap ⬇ to download + add to timeline.
 * [initialMood] pre-selects a category (used by Auto Clip).
 */

data class MusicMood(
    val id: String,
    val label: String,
    val emoji: String,
    /** Jamendo tag to browse. */
    val tag: String
)

val MUSIC_MOODS = listOf(
    MusicMood("trending", "Trending", "🔥", "upbeat"),
    MusicMood("lofi", "Lofi", "☕", "chill"),
    MusicMood("energetic", "Energetic", "⚡", "electronic"),
    MusicMood("emotional", "Emotional", "💧", "cinematic")
)

@Composable
fun MusicLibraryPanel(
    onAddTrack: (title: String, filePath: String, durationMs: Long) -> Unit,
    onClose: () -> Unit,
    initialMood: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember { MusicLibraryController(context.applicationContext) }
    val uiState by controller.state.collectAsStateWithLifecycle()
    var selectedMood by remember {
        mutableStateOf(MUSIC_MOODS.firstOrNull { it.id == initialMood } ?: MUSIC_MOODS.first())
    }
    var query by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    LaunchedEffect(selectedMood) {
        controller.browseTag(selectedMood.tag)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        ApexPalette.BgSurface.copy(alpha = 0.98f),
                        ApexPalette.BgBase.copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(ApexPalette.NeonCyan.copy(alpha = 0.4f), ApexPalette.NeonPink.copy(alpha = 0.4f))
                ),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.NeonPink.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = ApexPalette.NeonPink, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text(
                        "Free Music",
                        color = ApexPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Royalty-free • CC licensed",
                        color = ApexPalette.TextTertiary,
                        fontSize = 9.sp
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
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(15.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // Search
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search tracks…", color = ApexPalette.TextTertiary, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ApexPalette.TextTertiary, modifier = Modifier.size(16.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ApexPalette.BgElevated,
                unfocusedContainerColor = ApexPalette.BgElevated,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = ApexPalette.TextPrimary,
                unfocusedTextColor = ApexPalette.TextPrimary
            )
        )

        Spacer(Modifier.height(8.dp))

        // Mood categories
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MUSIC_MOODS) { mood ->
                val selected = mood.id == selectedMood.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) ApexPalette.NeonPink.copy(alpha = 0.2f) else ApexPalette.BgElevated)
                        .border(1.dp, if (selected) ApexPalette.NeonPink else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable {
                            selectedMood = mood
                            query = ""
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(mood.emoji, fontSize = 13.sp)
                        Text(
                            mood.label,
                            color = if (selected) ApexPalette.NeonPink else ApexPalette.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Track list
        if (!uiState.apiConfigured) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApexPalette.BgElevated)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Music catalog needs setup.\nImport audio from your device instead.",
                    color = ApexPalette.TextTertiary,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else if (uiState.isLoading && uiState.tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ApexPalette.NeonPink, modifier = Modifier.size(32.dp))
            }
        } else {
            val tracks = remember(uiState.tracks, query) {
                if (query.isBlank()) uiState.tracks
                else uiState.tracks.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true)
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isPreviewing = uiState.previewingId == track.id,
                        isDownloading = track.id in uiState.downloadingIds,
                        downloadProgress = uiState.downloadProgress[track.id] ?: 0f,
                        onPreview = { controller.togglePreview(track) },
                        onAdd = {
                            controller.downloadAndAdd(track) { title, path, dur ->
                                onAddTrack(title, path, dur)
                            }
                        }
                    )
                }
                if (tracks.isEmpty()) {
                    item {
                        Text(
                            "No tracks found.",
                            color = ApexPalette.TextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        uiState.error?.let { err ->
            Spacer(Modifier.height(6.dp))
            Text(err, color = ApexPalette.NeonPink, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TrackRow(
    track: RemoteMusicTrack,
    isPreviewing: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onPreview: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ApexPalette.BgElevated)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Artwork / icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(ApexPalette.NeonPink.copy(alpha = 0.3f), ApexPalette.NeonCyan.copy(alpha = 0.3f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("🎵", fontSize = 20.sp)
        }

        // Title / artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                color = ApexPalette.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${track.artist} • ${track.durationLabel}",
                color = ApexPalette.TextTertiary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isDownloading) {
                Text(
                    "Downloading ${(downloadProgress * 100).toInt()}%",
                    color = ApexPalette.NeonCyan,
                    fontSize = 9.sp
                )
            }
        }

        // Preview button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ApexPalette.NeonPink.copy(alpha = 0.15f))
                .clickable { onPreview() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPreviewing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPreviewing) "Pause preview" else "Preview",
                tint = ApexPalette.NeonPink,
                modifier = Modifier.size(18.dp)
            )
        }

        // Add button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isDownloading) ApexPalette.BgElevated
                    else ApexPalette.NeonCyan.copy(alpha = 0.15f)
                )
                .clickable(enabled = !isDownloading) { onAdd() },
            contentAlignment = Alignment.Center
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    color = ApexPalette.NeonCyan,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Add to timeline",
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
