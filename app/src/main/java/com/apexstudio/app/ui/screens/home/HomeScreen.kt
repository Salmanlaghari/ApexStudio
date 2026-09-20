package com.apexstudio.app.ui.screens.home

import android.content.Context
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.data.media.MediaUriResolver
import com.apexstudio.app.data.picker.MediaMetadata
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.data.repository.MediaRepository
import com.apexstudio.app.data.repository.ProjectRepository
import com.apexstudio.app.data.template.TimelineTemplateManager
import com.apexstudio.app.data.template.TransmissionTemplate
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun HomeScreen(
    onProjectOpen: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    onExport: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { MediaRepository }
    val projectRepo = remember { ProjectRepository(context) }
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var templates by remember { mutableStateOf<List<TransmissionTemplate>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("All") }
    var pendingTemplateForVideo by remember { mutableStateOf<TransmissionTemplate?>(null) }

    LaunchedEffect(Unit) {
        val saved = projectRepo.loadAllNow()
        projects = if (saved.isNotEmpty()) saved else repo.loadProjects()

        val loadedTemplates = withContext(Dispatchers.IO) {
            val mgr = TimelineTemplateManager(context)
            mgr.loadTransmissionTemplates()
        }
        templates = loadedTemplates
    }

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
                val chosenTemplate = pendingTemplateForVideo
                val projectName = if (chosenTemplate != null) {
                    "${chosenTemplate.name} • ${metadataList.first().name.substringBeforeLast('.')}"
                } else {
                    metadataList.first().name.substringBeforeLast('.')
                }

                val newProject = repo.createProject(
                    name = projectName,
                    clips = clips
                ).let { p ->
                    if (chosenTemplate != null) {
                        p.copy(
                            lastTransmissionTemplateId = chosenTemplate.id,
                            lastTransitionType = chosenTemplate.transitionType,
                            lastTransitionDurationMs = chosenTemplate.transitionDurationMs
                        )
                    } else p
                }

                projectRepo.saveProject(newProject)
                projects = projectRepo.loadAllNow()
                pendingTemplateForVideo = null
                CrashMarker.mark(context, "HomeScreen: opening project ${newProject.id}")
                onProjectOpen(newProject.id)
            }
        }
    }

    fun launchVideoPickerForTemplate(template: TransmissionTemplate) {
        pendingTemplateForVideo = template
        mediaPicker.pickMultipleMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    fun applyTemplateToQuickDemo(template: TransmissionTemplate) {
        scope.launch {
            val sampleUri = withContext(Dispatchers.IO) {
                MediaUriResolver.getFallbackSampleUri(context)
            }
            val demoClip = MediaClip(
                id = UUID.randomUUID().toString(),
                name = "${template.name} Demo.mp4",
                uri = sampleUri.toString(),
                durationMs = 14000L,
                trimStartMs = 0L,
                trimEndMs = 14000L,
                thumbnail = null,
                trackIndex = 0,
                type = ClipType.VIDEO
            )
            val demoProject = repo.createProject(
                name = "${template.name} (Quick Demo)",
                clips = listOf(demoClip)
            ).copy(
                lastTransmissionTemplateId = template.id,
                lastTransitionType = template.transitionType,
                lastTransitionDurationMs = template.transitionDurationMs
            )
            projectRepo.saveProject(demoProject)
            projects = projectRepo.loadAllNow()
            CrashMarker.mark(context, "HomeScreen: opening quick demo project ${demoProject.id}")
            onProjectOpen(demoProject.id)
        }
    }

    val categories = listOf("All", "Viral Reels", "8K Cinema", "Cyberpunk", "Retro Film", "Speed & Drill", "Lo-Fi Cozy", "Vogue Glam")

    val filteredTemplates = remember(templates, selectedCategory) {
        if (selectedCategory == "All") {
            templates
        } else {
            templates.filter { t ->
                when (selectedCategory) {
                    "Viral Reels" -> t.aspectRatio == "9:16" || t.tags.contains("viral")
                    "8K Cinema" -> t.tags.contains("cinematic") || t.resolution == "4K" || t.id.contains("cinema")
                    "Cyberpunk" -> t.tags.contains("cyberpunk") || t.tags.contains("neon") || t.tags.contains("matrix")
                    "Retro Film" -> t.tags.contains("vintage") || t.tags.contains("film") || t.tags.contains("retro")
                    "Speed & Drill" -> t.tags.contains("fast") || t.tags.contains("drill") || t.tags.contains("action") || t.isSlowMotion
                    "Lo-Fi Cozy" -> t.tags.contains("lo-fi") || t.tags.contains("calm") || t.tags.contains("chillhop") || t.tags.contains("cozy")
                    "Vogue Glam" -> t.tags.contains("beauty") || t.tags.contains("glam") || t.tags.contains("vogue") || t.tags.contains("rose-gold")
                    else -> true
                }
            }
        }
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
                onCreate = {
                    pendingTemplateForVideo = null
                    mediaPicker.pickMultipleMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                onAddMedia = { /* handled via onCreate */ }
            )
        }

        // 30+ REAL TEMPLATES SECTION (CLICK-TO-READY)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "🔥 30+ Click-To-Ready Templates",
                                color = ApexPalette.TextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ApexPalette.NeonCyan.copy(alpha = 0.2f))
                                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("${templates.size} READY", color = ApexPalette.NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Text(
                            "Royalty-Free Music • Auto Filter & FX • Beat Sync • Apply in Seconds",
                            color = ApexPalette.TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Category filter pills
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.25f)
                                    else ApexPalette.BgElevated
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) ApexPalette.NeonCyan
                                    else ApexPalette.BorderGlass,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                cat,
                                color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Horizontal Carousel of rich ready-to-use template cards
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredTemplates) { tpl ->
                        DashboardTemplateCard(
                            template = tpl,
                            onApplyVideo = { launchVideoPickerForTemplate(tpl) },
                            onQuickDemo = { applyTemplateToQuickDemo(tpl) }
                        )
                    }
                }
            }
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
private fun DashboardTemplateCard(
    template: TransmissionTemplate,
    onApplyVideo: () -> Unit,
    onQuickDemo: () -> Unit
) {
    val accentColor = Color(template.previewAccentArgb.toULong().toLong())

    Box(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        accentColor.copy(alpha = 0.25f),
                        ApexPalette.BgSurface.copy(alpha = 0.95f),
                        ApexPalette.BgDeep
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(accentColor.copy(alpha = 0.6f), ApexPalette.BorderGlass)
                ),
                RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        Column {
            // Header: resolution + aspect ratio + speed badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${template.resolution} • ${template.fps}fps",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (template.playbackSpeed != 1.0f) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ApexPalette.NeonEmerald.copy(alpha = 0.85f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            template.speedLabel.ifBlank { "${template.playbackSpeed}x" },
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                } else {
                    Text(
                        template.aspectRatio,
                        color = ApexPalette.TextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Template Title
            Text(
                template.name,
                color = ApexPalette.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(Modifier.height(3.dp))

            // Short Description
            Text(
                template.description,
                color = ApexPalette.TextSecondary,
                fontSize = 9.sp,
                maxLines = 2,
                lineHeight = 12.sp
            )

            Spacer(Modifier.height(8.dp))

            // Background Royalty Music Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text("🎵", fontSize = 10.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        template.musicTitle.ifBlank { "Original Synth Beat" },
                        color = ApexPalette.NeonCyan,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "${template.musicGenre} • ${template.bpm} BPM Beat Drop",
                        color = ApexPalette.TextTertiary,
                        fontSize = 8.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Specs badges: Filter + FX + Transition
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(ApexPalette.BgElevated)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("🎨 ${template.filterId}", color = ApexPalette.TextSecondary, fontSize = 8.sp, maxLines = 1)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(ApexPalette.BgElevated)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("⚡ ${template.fxPresetId}", color = ApexPalette.TextSecondary, fontSize = 8.sp, maxLines = 1)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action Buttons: 1-Click Apply to User Video & Quick Demo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Primary: Add User Video (Instant Apply)
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(accentColor, ApexPalette.NeonCyan)
                            )
                        )
                        .clickable(onClick = onApplyVideo)
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.VideoCall, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                        Text(
                            "Add Video",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Secondary: Quick Demo (1-Second Instant Test)
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.BgElevated)
                        .border(0.8.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                        .clickable(onClick = onQuickDemo)
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Demo",
                        color = ApexPalette.TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NewProjectHero(enabled: Boolean, onCreate: () -> Unit, onAddMedia: () -> Unit) {
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
                    "Blank Cinematic Project",
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
                    "Start fresh or select 30+ ready-made templates below",
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
