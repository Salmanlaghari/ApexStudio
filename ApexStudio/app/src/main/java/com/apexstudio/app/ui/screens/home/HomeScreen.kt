package com.apexstudio.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.repository.MediaRepository
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.components.NeonGradientBackground
import com.apexstudio.app.ui.components.NeonIconButton
import com.apexstudio.app.ui.components.NeonPrimaryButton
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat

@Composable
fun HomeScreen(
    onProjectOpen: (String) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val repo = remember { MediaRepository() }
    val projects = repo.loadProjects()
    var selectedFilter by remember { mutableStateOf("All") }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "APEX STUDIO",
                color = ApexPalette.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                modifier = Modifier.rotate(-90f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Transparent)
        ) {
            AppTopBar(
                title = "ApexStudio",
                subtitle = "PRO EDITOR • v1.0",
                canUndo = false, canRedo = false,
                extraRight = {
                    NeonIconButton(
                        icon = Icons.Default.Settings,
                        onClick = onOpenSettings,
                        size = 40.dp,
                        iconSize = 20.dp
                    )
                }
            )

        Spacer(Modifier.height(8.dp))

        // Hero
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 26.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(
                            listOf(ApexPalette.NeonPurple, ApexPalette.NeonCyan)
                        )),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, null,
                        tint = ApexPalette.BgDeep, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Create cinematic stories",
                        color = ApexPalette.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "AI-enhanced timeline • 8K HDR • 240 fps",
                        color = ApexPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                NeonPrimaryButton(
                    text = "New",
                    icon = Icons.Default.Add,
                    onClick = {
                        val id = projects.firstOrNull()?.id
                        if (id != null) onProjectOpen(id)
                    },
                    enabled = projects.isNotEmpty()
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf("All", "Recent", "Drafts", "Shared", "4K+", "AI Edited")) { f ->
                val sel = f == selectedFilter
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (sel) ApexPalette.NeonCyan.copy(alpha = 0.18f)
                            else ApexPalette.BgElevated
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        f,
                        color = if (sel) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable { selectedFilter = f }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "Your Projects",
            color = ApexPalette.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(projects) { p ->
                ProjectRow(p, onOpen = { onProjectOpen(p.id) })
            }
        }
        }
    }
}

@Composable
private fun ProjectRow(p: Project, onOpen: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(
                        listOf(ApexPalette.NeonPurple, ApexPalette.NeonCyan)
                    )),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Movie, null, tint = ApexPalette.BgDeep,
                    modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    p.name,
                    color = ApexPalette.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null,
                        tint = ApexPalette.TextTertiary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(TimeFormat.msToShort(p.durationMs),
                        color = ApexPalette.TextSecondary,
                        style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Default.Hd, null,
                        tint = ApexPalette.NeonCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${p.resolution} • ${p.fps}fps",
                        color = ApexPalette.NeonCyan,
                        style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ApexPalette.BgElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.7f)
                            .background(Brush.horizontalGradient(
                                listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                            ))
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            NeonIconButton(
                icon = Icons.Default.PlayArrow,
                onClick = onOpen,
                size = 46.dp,
                iconSize = 22.dp,
                background = Brush.linearGradient(
                    listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                ).let { ApexPalette.NeonCyan }
            )
        }
    }
}

