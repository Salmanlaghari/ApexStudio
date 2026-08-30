package com.apexstudio.app.ui.screens.home

import android.content.Context
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.data.picker.MediaMetadata
import com.apexstudio.app.data.repository.MediaRepository
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat
import java.util.UUID

@Composable
fun HomeScreen(
    onProjectOpen: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    onExport: () -> Unit = {}
) {
    val repo = remember { MediaRepository }
    val projects = repo.loadProjects()
    val context = LocalContext.current
    val mediaPicker = remember { MediaPickerHelper(context) }
    var pickedMedia by remember { mutableStateOf<List<MediaMetadata>>(emptyList()) }

    mediaPicker.registerLaunchers()

    LaunchedEffect(Unit) {
        mediaPicker.pickedMedia.collect { metadataList ->
            if (metadataList.isNotEmpty()) {
                pickedMedia = metadataList
                val clips = metadataList.map { meta ->
                    MediaClip(
                        id = UUID.randomUUID().toString(),
                        name = meta.name,
                        uri = meta.uri,
                        durationMs = meta.durationMs,
                        trimStartMs = 0L,
                        trimEndMs = meta.durationMs,
                        thumbnail = null,
                        trackIndex = if (meta.type == ClipType.VIDEO) 0 else 1,
                        type = meta.type
                    )
                }
                val newProject = repo.createProject(
                    name = metadataList.first().name.substringBeforeLast('.'),
                    clips = clips
                )
                onProjectOpen(newProject.id)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexPalette.BgBase)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppTopBar(
            title = "ApexStudio",
            subtitle = "Cinematic Editor • 8K HDR",
            onHome = { /* already on home */ },
            onExport = onExport,
            onSettings = onOpenSettings
        )
        Spacer(Modifier.height(4.dp))
        NewProjectHero(
            enabled = true,
            onCreate = {
                mediaPicker.pickMultipleMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onAddMedia = { /* handled via onCreate */ }
        )
        SectionLabel("Your Projects")
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(projects) { p ->
                ProjectRow(p, onOpen = { onProjectOpen(p.id) })
            }
        }
    }
}

@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "A",
                color = ApexPalette.BgDeep,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ApexStudio",
                    color = ApexPalette.TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    ApexPalette.NeonCyan.copy(alpha = 0.18f),
                                    ApexPalette.NeonPurple.copy(alpha = 0.18f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            ApexPalette.NeonCyan.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "PRO",
                        color = ApexPalette.NeonCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Text(
                "Cinematic Editor • 8K HDR",
                color = ApexPalette.TextTertiary,
                fontSize = 10.sp
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ApexPalette.BgGlass)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Settings,
                null,
                tint = ApexPalette.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NewProjectHero(enabled: Boolean, onCreate: () -> Unit, onAddMedia: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0F1F2E),
                        Color(0xFF1A0F2E),
                        Color(0xFF0F2A22)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = ApexPalette.NeonCyan.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled, onClick = onCreate)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ApexPalette.NeonCyan.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                        tint = ApexPalette.BgDeep,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "New Project",
                    color = ApexPalette.TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "8K • HDR • 240 fps",
                    color = ApexPalette.NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Start a cinematic timeline",
                    color = ApexPalette.TextSecondary,
                    fontSize = 10.sp
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = ApexPalette.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = ApexPalette.TextSecondary,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun ProjectRow(p: Project, onOpen: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(ApexPalette.NeonPurple, ApexPalette.NeonCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Movie,
                    null,
                    tint = ApexPalette.BgDeep,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    p.name,
                    color = ApexPalette.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        null,
                        tint = ApexPalette.TextTertiary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        TimeFormat.msToShort(p.durationMs),
                        color = ApexPalette.TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ApexPalette.BgElevated)
                        .border(
                            1.dp,
                            ApexPalette.NeonCyan.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        p.resolution,
                        color = ApexPalette.NeonCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${p.fps}fps",
                    color = ApexPalette.NeonEmerald,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
