package com.apexstudio.app.ui.screens.home

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.data.picker.MediaMetadata
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.data.repository.MediaRepository
import com.apexstudio.app.data.repository.ProjectRepository
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
    val context = LocalContext.current
    val repo = remember { MediaRepository }
    val projectRepo = remember { ProjectRepository(context) }
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }

    LaunchedEffect(Unit) {
        val saved = projectRepo.loadAllNow()
        projects = if (saved.isNotEmpty()) saved else repo.loadProjects()

    }

    val mediaPicker = remember { MediaPickerHelper(context) }
    mediaPicker.registerLaunchers()

    LaunchedEffect(Unit) {
        mediaPicker.pickedMedia.collect { metadataList ->
            if (metadataList.isNotEmpty()) {
                val clips = metadataList.map { meta ->
                    MediaClip(
                        id = UUID.randomUUID().toString(),
                        name = meta.name,
                        uri = meta.uri,
                        durationMs = meta.durationMs,
                        trimStartMs = 0L,
                        trimEndMs = meta.durationMs,
                        thumbnail = null,
                        trackIndex = if (meta.type == ClipType.VIDEO || meta.type == ClipType.IMAGE) 0 else 1,
                        type = meta.type
                    )
                }
                val projectName = metadataList.first().name.substringBeforeLast('.')
                val newProject = repo.createProject(name = projectName, clips = clips)

                projectRepo.saveProject(newProject)
                projects = projectRepo.loadAllNow()
                CrashMarker.mark(context, "HomeScreen: opening project ${newProject.id}")
                onProjectOpen(newProject.id)
            }
        }
    }

    fun openEditorFor(mediaType: ClipType) {
        val pickerType = if (mediaType == ClipType.IMAGE) {
            ActivityResultContracts.PickVisualMedia.ImageOnly
        } else {
            ActivityResultContracts.PickVisualMedia.VideoOnly
        }
        mediaPicker.pickMultipleMedia.launch(PickVisualMediaRequest(pickerType))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexPalette.BgBase)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            AppTopBar(
                title = "ApexStudio",
                subtitle = "Cinematic Editor • 8K HDR NLE",
                onHome = { /* already on home */ },
                onExport = onExport,
                onSettings = onOpenSettings
            )
        }

        item {
            NewProjectHero(
                enabled = true,
                onCreate = { openEditorFor(ClipType.VIDEO) }
            )
        }
        item {
            DashboardEditorEntries(
                onImageEditing = { openEditorFor(ClipType.IMAGE) },
                onVideoEditor = { openEditorFor(ClipType.VIDEO) }
            )
        }

        item {
            SectionLabel("Your Projects (${projects.size})")
        }

        items(projects) { p ->
            ProjectRow(p, onOpen = { onProjectOpen(p.id) })
        }
    }
}

@Composable
internal fun DashboardEditorEntries(
    onImageEditing: () -> Unit,
    onVideoEditor: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DashboardEditorEntry(
            title = "Image Editing Session",
            subtitle = "Choose a photo to edit",
            icon = Icons.Default.Image,
            testTag = "dashboard-image-editing-session",
            modifier = Modifier.weight(1f),
            onClick = onImageEditing
        )
        DashboardEditorEntry(
            title = "Video Editor",
            subtitle = "Choose a video to edit",
            icon = Icons.Default.Movie,
            testTag = "dashboard-video-editor",
            modifier = Modifier.weight(1f),
            onClick = onVideoEditor
        )
    }
}

@Composable
private fun DashboardEditorEntry(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(22.dp))
            Text(title, color = ApexPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(subtitle, color = ApexPalette.TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun NewProjectHero(enabled: Boolean, onCreate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
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
                    .size(52.dp)
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
                        .size(42.dp)
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
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "New Video Project",
                    color = ApexPalette.TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "8K • HDR • 240 fps NLE Engine",
                    color = ApexPalette.NeonCyan,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Start a new video editing project",
                    color = ApexPalette.TextSecondary,
                    fontSize = 9.5.sp
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
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
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
                    .size(54.dp)
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
                    modifier = Modifier.size(24.dp)
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
