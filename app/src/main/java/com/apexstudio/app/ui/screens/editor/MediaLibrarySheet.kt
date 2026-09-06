package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.picker.MediaMetadata
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.launch

private data class SampleMediaItem(
    val id: String,
    val name: String,
    val durationMs: Long,
    val type: ClipType,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val SAMPLE_MEDIA_LIBRARY = listOf(
    SampleMediaItem("1", "Cinematic_Broll_01.mp4", 12_000L, ClipType.VIDEO, Icons.Default.Movie),
    SampleMediaItem("2", "Urban_Street_Night.mp4", 18_000L, ClipType.VIDEO, Icons.Default.Movie),
    SampleMediaItem("3", "Sunset_Beach_4K.mp4", 15_000L, ClipType.VIDEO, Icons.Default.Movie),
    SampleMediaItem("4", "Drone_Mountain_View.mp4", 22_000L, ClipType.VIDEO, Icons.Default.Movie),
    SampleMediaItem("5", "Portrait_Model_Shoot.jpg", 5_000L, ClipType.VIDEO, Icons.Default.Photo),
    SampleMediaItem("6", "Background_Music_Track.mp3", 120_000L, ClipType.AUDIO, Icons.Default.MusicNote),
    SampleMediaItem("7", "Cyberpunk_Synthwave.mp3", 180_000L, ClipType.AUDIO, Icons.Default.MusicNote),
    SampleMediaItem("8", "Transition_Whoosh_SFX.wav", 2_000L, ClipType.SFX, Icons.Default.MusicNote)
)

@Composable
fun MediaLibrarySheet(
    onImportMedia: (List<MediaMetadata>) -> Unit,
    onOpenSystemPicker: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("Videos") }
    var searchQuery by remember { mutableStateOf("") }
    val categories = listOf("Videos", "Photos", "Audio", "Recent", "Favorites")

    val filteredItems = remember(selectedCategory, searchQuery) {
        SAMPLE_MEDIA_LIBRARY.filter { item ->
            val catMatch = when (selectedCategory) {
                "Videos" -> item.type == ClipType.VIDEO
                "Photos" -> item.type == ClipType.OVERLAY
                "Audio" -> item.type == ClipType.AUDIO || item.type == ClipType.SFX
                else -> true
            }
            val searchMatch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
            catMatch && searchMatch
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Media Library",
                color = ApexPalette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
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

        Spacer(Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search media assets...", color = ApexPalette.TextTertiary, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(18.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
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

        Spacer(Modifier.height(10.dp))

        // Categories
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(categories) { cat ->
                val isSelected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated)
                        .border(1.dp, if (isSelected) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        cat,
                        color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Option to open system storage picker
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ApexPalette.NeonCyan.copy(alpha = 0.15f))
                .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(10.dp))
                .clickable {
                    onOpenSystemPicker()
                    onClose()
                }
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(16.dp))
                Text("Browse Device Files & Storage", color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Grid of Media Items
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredItems) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ApexPalette.BgBase)
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                        .clickable {
                            scope.launch {
                                val sampleUri = try {
                                    com.apexstudio.app.data.media.SampleVideoGenerator.getOrCreateSampleVideo(context)
                                } catch (_: Exception) { "" }
                                val meta = MediaMetadata(
                                    uri = sampleUri,
                                    name = item.name,
                                    durationMs = item.durationMs,
                                    width = 1920,
                                    height = 1080,
                                    fps = 30,
                                    type = item.type
                                )
                                onImportMedia(listOf(meta))
                            }
                            onClose()
                        }
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ApexPalette.BgElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                color = ApexPalette.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                "${item.durationMs / 1000}s",
                                color = ApexPalette.TextSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
