package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.apexstudio.app.data.picker.MediaMetadata
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

private val MEDIA_LIBRARY_CATEGORIES = listOf("All", "Videos", "Photos", "Audio")

internal enum class MediaLibraryBrowseSource { VIDEO, PHOTOS, OVERLAY, AUDIO }

/** Filter only actual media supplied by the caller; never synthesize sample URIs. */
internal fun filterMediaLibraryItems(
    items: List<MediaMetadata>,
    category: String,
    query: String
): List<MediaMetadata> = items.filter { item ->
    val categoryMatches = when (category) {
        "Videos" -> item.type == ClipType.VIDEO || item.type == ClipType.OVERLAY
        "Photos" -> item.type == ClipType.IMAGE
        "Audio" -> item.type == ClipType.AUDIO || item.type == ClipType.SFX
        else -> true
    }
    categoryMatches && (query.isBlank() || item.name.contains(query.trim(), ignoreCase = true) ||
        item.uri.contains(query.trim(), ignoreCase = true))
}

internal fun dispatchMediaLibraryBrowse(
    source: MediaLibraryBrowseSource,
    onBrowseVideo: () -> Unit,
    onBrowsePhotos: () -> Unit,
    onBrowseOverlay: () -> Unit,
    onBrowseAudio: () -> Unit
) {
    when (source) {
        MediaLibraryBrowseSource.VIDEO -> onBrowseVideo()
        MediaLibraryBrowseSource.PHOTOS -> onBrowsePhotos()
        MediaLibraryBrowseSource.OVERLAY -> onBrowseOverlay()
        MediaLibraryBrowseSource.AUDIO -> onBrowseAudio()
    }
}

internal fun importMediaLibraryItem(
    item: MediaMetadata,
    onImportMedia: (List<MediaMetadata>) -> Unit,
    onClose: () -> Unit
) {
    onImportMedia(listOf(item))
    onClose()
}

/**
 * Compact project-media browser. Header, search, categories and source actions stay pinned;
 * the actual project assets scroll horizontally below them. The legacy system-picker callback
 * remains as the default for every source for callers that have not supplied specialized actions.
 */
@Composable
fun MediaLibrarySheet(
    onImportMedia: (List<MediaMetadata>) -> Unit,
    onOpenSystemPicker: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Actual indexed/project media supplied by the host. Empty by default; no demo files are shown. */
    libraryItems: List<MediaMetadata> = emptyList(),
    onBrowseVideo: () -> Unit = onOpenSystemPicker,
    onBrowsePhotos: () -> Unit = onOpenSystemPicker,
    onBrowseOverlay: () -> Unit = onOpenSystemPicker,
    onBrowseAudio: () -> Unit = onOpenSystemPicker
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(libraryItems, selectedCategory, searchQuery) {
        filterMediaLibraryItems(libraryItems, selectedCategory, searchQuery)
    }
    val browseActions = listOf(
        "Video" to MediaLibraryBrowseSource.VIDEO,
        "Photos" to MediaLibraryBrowseSource.PHOTOS,
        "Overlay" to MediaLibraryBrowseSource.OVERLAY,
        "Audio" to MediaLibraryBrowseSource.AUDIO
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .defaultMinSize(minHeight = 250.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Media Library", color = ApexPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(5.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search media...", color = ApexPalette.TextTertiary, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ApexPalette.NeonCyan,
                unfocusedBorderColor = ApexPalette.BorderGlass,
                focusedContainerColor = ApexPalette.BgBase,
                unfocusedContainerColor = ApexPalette.BgBase,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )

        Spacer(Modifier.height(5.dp))
        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(MEDIA_LIBRARY_CATEGORIES, key = { it }) { category ->
                val selected = category == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated)
                        .border(1.dp, if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(category, color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(5.dp))
        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(browseActions, key = { it.second.name }) { (label, source) ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.NeonCyan.copy(alpha = 0.12f))
                        .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable {
                            dispatchMediaLibraryBrowse(source, onBrowseVideo, onBrowsePhotos, onBrowseOverlay, onBrowseAudio)
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(14.dp))
                    Text("Browse $label", color = ApexPalette.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        if (filteredItems.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (libraryItems.isEmpty()) "No media in this project yet" else "No matching media",
                    color = ApexPalette.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(3.dp))
                Text("Browse your device to select real media.", color = ApexPalette.TextSecondary, fontSize = 11.sp)
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 2.dp)
            ) {
                items(filteredItems, key = { it.uri }) { item ->
                    MediaLibraryCard(item = item, onClick = { importMediaLibraryItem(item, onImportMedia, onClose) })
                }
            }
        }
    }
}

@Composable
private fun MediaLibraryCard(item: MediaMetadata, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(7.dp))
                .background(ApexPalette.BgElevated),
            contentAlignment = Alignment.Center
        ) {
            if (item.type == ClipType.IMAGE) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val icon = if (item.type == ClipType.AUDIO || item.type == ClipType.SFX) Icons.Default.MusicNote else Icons.Default.VideoLibrary
                Icon(icon, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(27.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(item.name, color = ApexPalette.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            when (item.type) {
                ClipType.VIDEO -> "Video · ${item.durationMs / 1000}s"
                ClipType.OVERLAY -> "Overlay · ${item.durationMs / 1000}s"
                ClipType.IMAGE -> "Photo"
                ClipType.AUDIO, ClipType.SFX -> "Audio · ${item.durationMs / 1000}s"
            },
            color = ApexPalette.TextSecondary,
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}
