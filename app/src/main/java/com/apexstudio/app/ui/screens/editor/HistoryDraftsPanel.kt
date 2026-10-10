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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.HistoryDraftsTab
import com.apexstudio.app.ui.theme.ApexPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 3: History + Drafts panel.
 *
 * - HISTORY: all saved projects sorted by last edit, tap to reopen.
 * - DRAFTS: projects flagged as drafts + "Save draft" button.
 * Auto-save runs every 30s while editing (see EditorScreen); the
 * last auto-save time is shown in the header.
 *
 * No dead buttons: open/delete/save all hit ProjectRepository.
 */

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "—"
    val now = System.currentTimeMillis()
    val diffMin = (now - ms) / 60000
    return when {
        diffMin < 1 -> "just now"
        diffMin < 60 -> "$diffMin min ago"
        diffMin < 1440 -> "${diffMin / 60}h ago"
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ms))
    }
}

@Composable
fun HistoryDraftsPanel(
    projects: List<Project>,
    currentTab: HistoryDraftsTab,
    onTabChange: (HistoryDraftsTab) -> Unit,
    lastAutoSaveMs: Long,
    onOpenProject: (Project) -> Unit,
    onDeleteProject: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val history = rememberProjects(projects, HistoryDraftsTab.HISTORY)
    val drafts = rememberProjects(projects, HistoryDraftsTab.DRAFTS)
    val visible = if (currentTab == HistoryDraftsTab.HISTORY) history else drafts

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
                    listOf(ApexPalette.NeonCyan.copy(alpha = 0.4f), ApexPalette.NeonAmber.copy(alpha = 0.4f))
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
                        .background(ApexPalette.NeonCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text(
                        "History & Drafts",
                        color = ApexPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (lastAutoSaveMs > 0) "Auto-saved ${formatTime(lastAutoSaveMs)}"
                        else "Auto-save every 30s",
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

        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ApexPalette.BgElevated)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HistoryDraftsTab.entries.forEach { tab ->
                val selected = tab == currentTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onTabChange(tab) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (tab) {
                            HistoryDraftsTab.HISTORY -> "📜 History (${history.size})"
                            HistoryDraftsTab.DRAFTS -> "💾 Drafts (${drafts.size})"
                        },
                        color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Save draft button (drafts tab)
        if (currentTab == HistoryDraftsTab.DRAFTS) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(ApexPalette.NeonCyan.copy(alpha = 0.25f), ApexPalette.NeonPink.copy(alpha = 0.25f))
                        )
                    )
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable { onSaveDraft() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(16.dp))
                    Text("Save current as draft", color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Project list
        if (visible.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApexPalette.BgElevated),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (currentTab == HistoryDraftsTab.HISTORY) "📭" else "💾",
                        fontSize = 32.sp
                    )
                    Text(
                        if (currentTab == HistoryDraftsTab.HISTORY) "No edit history yet"
                        else "No drafts yet",
                        color = ApexPalette.TextTertiary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visible, key = { it.id }) { project ->
                    ProjectRow(
                        project = project,
                        onOpen = { onOpenProject(project) },
                        onDelete = { onDeleteProject(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberProjects(
    projects: List<Project>,
    tab: HistoryDraftsTab
): List<Project> {
    return remember(projects, tab) {
        val sorted = projects.sortedByDescending { it.lastEditedMs }
        when (tab) {
            HistoryDraftsTab.HISTORY -> sorted
            HistoryDraftsTab.DRAFTS -> sorted.filter { it.isDraft }
        }
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ApexPalette.BgElevated)
            .border(0.5.dp, ApexPalette.NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clickable { onOpen() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thumbnail placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            ApexPalette.NeonCyan.copy(alpha = 0.3f),
                            ApexPalette.NeonPink.copy(alpha = 0.3f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("🎬", fontSize = 20.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    project.name.ifBlank { "Untitled" },
                    color = ApexPalette.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (project.isDraft) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ApexPalette.NeonAmber.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("DRAFT", color = ApexPalette.NeonAmber, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Text(
                "${project.clips.size} clips • ${project.audioTracks.size} audio • ${formatTime(project.lastEditedMs)}",
                color = ApexPalette.TextTertiary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Delete
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF3A1A1A))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ApexPalette.NeonPink, modifier = Modifier.size(15.dp))
        }
    }
}
