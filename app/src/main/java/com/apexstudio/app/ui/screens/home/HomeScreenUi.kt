package com.apexstudio.app.ui.screens.home

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.apexstudio.app.data.media.VideoThumbnailExtractor
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat

@Composable
internal fun ApexHomeContent(
    projects: List<Project>,
    savedProjectIds: Set<String>,
    searchOpen: Boolean,
    searchText: String,
    showAllProjects: Boolean,
    onSearchOpen: () -> Unit,
    onSearchChange: (String) -> Unit,
    onNotifications: () -> Unit,
    onCreateVideo: () -> Unit,
    onCreatePhoto: () -> Unit,
    onOpenProject: (Project) -> Unit,
    onRenameProject: (Project) -> Unit,
    onDeleteProject: (Project) -> Unit,
    onShowAll: () -> Unit
) {
    val listState = rememberLazyListState()
    var showCreateChoices by remember { mutableStateOf(false) }
    val filteredProjects = remember(projects, searchText) {
        val query = searchText.trim()
        if (query.isEmpty()) projects else projects.filter { project ->
            project.name.contains(query, ignoreCase = true) ||
                project.clips.any { it.name.contains(query, ignoreCase = true) }
        }
    }
    val visibleProjects = if (showAllProjects || searchText.isNotBlank()) filteredProjects else filteredProjects.take(3)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(ApexPalette.BgBase)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            ApexHomeHeader(
                onSearch = onSearchOpen,
                onNotifications = onNotifications
            )
        }
        if (searchOpen) {
            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth().testTag("home-project-search"),
                    singleLine = true,
                    placeholder = { Text("Search projects", color = ApexPalette.TextTertiary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ApexPalette.NeonCyan) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ApexPalette.TextPrimary,
                        unfocusedTextColor = ApexPalette.TextPrimary,
                        focusedBorderColor = ApexPalette.NeonPurple,
                        unfocusedBorderColor = ApexPalette.BorderGlass,
                        cursorColor = ApexPalette.NeonCyan,
                        focusedContainerColor = ApexPalette.BgSurface,
                        unfocusedContainerColor = ApexPalette.BgSurface
                    )
                )
            }
        }
        item {
            CreativeWorkspaceHero(
                onCreate = { showCreateChoices = true }
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (searchText.isBlank()) "Recent Projects" else "Search results",
                        color = ApexPalette.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (searchText.isNotBlank()) Text("${filteredProjects.size} matching projects", color = ApexPalette.TextSecondary, fontSize = 12.sp)
                }
                if (filteredProjects.size > 3 && searchText.isBlank()) {
                    TextButton(onClick = onShowAll, modifier = Modifier.testTag("home-see-all")) {
                        Text(if (showAllProjects) "Show less" else "See all", color = ApexPalette.NeonCyan, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (visibleProjects.isEmpty()) {
            item {
                HomeEmptyProjects(searching = searchText.isNotBlank())
            }
        } else {
            items(visibleProjects, key = { it.id }) { project ->
                HomeProjectCard(
                    project = project,
                    canManage = project.id in savedProjectIds,
                    onOpen = { onOpenProject(project) },
                    onRename = { onRenameProject(project) },
                    onDelete = { onDeleteProject(project) }
                )
            }
        }
        item {
            AiToolsBanner(onChooseVideo = onCreateVideo)
        }
    }

    if (showCreateChoices) {
        AlertDialog(
            onDismissRequest = { showCreateChoices = false },
            containerColor = ApexPalette.BgSurface,
            title = { Text("Create a new project", color = ApexPalette.TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Choose the media you want to start with.", color = ApexPalette.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showCreateChoices = false; onCreateVideo() }) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = ApexPalette.NeonPurple)
                    Spacer(Modifier.width(7.dp))
                    Text("Video project")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showCreateChoices = false; onCreatePhoto() }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = ApexPalette.NeonCyan)
                        Spacer(Modifier.width(5.dp))
                        Text("Photo project")
                    }
                    TextButton(onClick = { showCreateChoices = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun ApexHomeHeader(onSearch: () -> Unit, onNotifications: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("dashboard-header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Canvas(Modifier.size(46.dp).testTag("apex-home-mark")) {
            val w = size.width
            val h = size.height
            val mark = Path().apply {
                moveTo(w * .08f, h * .88f)
                lineTo(w * .48f, h * .10f)
                quadraticTo(w * .52f, h * .01f, w * .57f, h * .10f)
                lineTo(w * .96f, h * .88f)
            }
            drawPath(mark, Brush.linearGradient(listOf(ApexPalette.NeonPurple, Color(0xFF547BFF), ApexPalette.NeonCyan)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * .18f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawLine(Color(0xFF8A70FF), androidx.compose.ui.geometry.Offset(w * .28f, h * .62f), androidx.compose.ui.geometry.Offset(w * .75f, h * .62f), strokeWidth = w * .14f, cap = StrokeCap.Round)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ApexPalette.TextPrimary)) { append("Apex") }
                    withStyle(SpanStyle(color = ApexPalette.NeonPurple)) { append("Studio") }
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text("AI-POWERED VIDEO EDITOR", color = ApexPalette.TextTertiary, fontSize = 8.sp, letterSpacing = 2.sp, maxLines = 1)
        }
        HeaderAction(Icons.Default.Search, "Search projects", onSearch, "dashboard-search")
        HeaderAction(Icons.Default.NotificationsNone, "App notification settings", onNotifications, "dashboard-notifications")
    }
}

@Composable
private fun HeaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, tag: String) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).clip(CircleShape).testTag(tag)
    ) {
        Icon(icon, contentDescription = description, tint = ApexPalette.TextPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CreativeWorkspaceHero(onCreate: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 224.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0D1D34), Color(0xFF251047), Color(0xFF07314B))))
            .border(1.dp, Brush.linearGradient(listOf(Color(0xFF4C92FF), ApexPalette.NeonPurple, ApexPalette.NeonCyan)), RoundedCornerShape(24.dp))
            .testTag("dashboard-new-project")
    ) {
        Image(
            painter = painterResource(com.apexstudio.app.R.drawable.apex_home_mountain_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xE80A1931), Color(0x7D16163B), Color(0x4421345E)))))
        Text("IDEAS\nEDIT\nENHANCE\nSHARE", color = Color.White.copy(alpha = .60f), fontSize = 7.sp, lineHeight = 14.sp,
            letterSpacing = 1.4.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 16.dp))
        Text("A HIGHER\nSTANDARD\nFOR CREATORS", color = Color.White.copy(alpha = .68f), fontSize = 7.sp,
            lineHeight = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 18.dp))
        Column(
            modifier = Modifier.fillMaxWidth(.84f).padding(horizontal = 20.dp, vertical = 19.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Your creative\nworkspace", color = Color.White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold)
            Text("Turn ideas into incredible videos\nwith the power of AI.", color = Color(0xFFC4D0F0), fontSize = 13.sp, lineHeight = 18.sp)
            Row(
                modifier = Modifier.padding(top = 5.dp).clip(RoundedCornerShape(30.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFF0E7FF), Color(0xFFD9F8FF))))
                    .clickable(onClick = onCreate).testTag("home-create-project"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF17162B), modifier = Modifier.padding(start = 15.dp).size(23.dp))
                Text("Create new project", color = Color(0xFF17162B), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 13.dp))
                Text("→", color = Color(0xFF17162B), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 16.dp))
            }
        }
    }
}

@Composable
private fun HomeProjectCard(project: Project, canManage: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val clip = remember(project.clips) {
        project.clips.firstOrNull { it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE) }
            ?: project.clips.firstOrNull { it.type == ClipType.VIDEO || it.type == ClipType.IMAGE }
    }
    var extractedFrame by remember(clip?.id, clip?.uri, clip?.trimStartMs) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(clip?.id, clip?.uri, clip?.trimStartMs, clip?.type) {
        extractedFrame = if (clip?.type == ClipType.VIDEO && clip.thumbnail.isNullOrBlank()) {
            VideoThumbnailExtractor.extractFrame(context, clip.uri, clip.trimStartMs.coerceAtLeast(0L))?.asImageBitmap()
        } else null
    }
    val imageModel: Any? = when {
        clip == null -> null
        !clip.thumbnail.isNullOrBlank() -> clip.thumbnail
        clip.type == ClipType.IMAGE -> Uri.parse(clip.uri)
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(Color(0xFF141923)).border(1.dp, Color(0xFF252D3B), RoundedCornerShape(17.dp))
            .clickable(onClick = onOpen).padding(10.dp).testTag("home-recent-project-${project.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(118.dp).height(70.dp).clip(RoundedCornerShape(11.dp)).background(ApexPalette.BgElevated), contentAlignment = Alignment.Center) {
            when {
                extractedFrame != null -> androidx.compose.foundation.Image(extractedFrame!!, contentDescription = "Preview of ${project.name}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                imageModel != null -> AsyncImage(model = imageModel, contentDescription = "Preview of ${project.name}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else -> {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF251047), Color(0xFF105077)))), contentAlignment = Alignment.Center) {
                        Icon(if (clip?.type == ClipType.IMAGE) Icons.Default.PhotoLibrary else Icons.Default.VideoLibrary, contentDescription = null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(29.dp))
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(topStart = 7.dp)).background(Color.Black.copy(alpha = .72f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                Text(TimeFormat.msToShort(project.durationMs), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(project.name, color = ApexPalette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = ApexPalette.TextTertiary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(homeEditedLabel(project.lastEditedMs), color = ApexPalette.TextSecondary, fontSize = 10.sp, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ProjectMetaChip(project.resolution.uppercase())
                ProjectMetaChip("${project.fps} FPS")
            }
        }
        if (canManage) {
            var showMenu by remember(project.id) { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(34.dp).testTag("home-project-menu-${project.id}")) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options for ${project.name}", tint = ApexPalette.TextSecondary, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() }, modifier = Modifier.testTag("home-project-rename-${project.id}"))
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() }, modifier = Modifier.testTag("home-project-delete-${project.id}"))
                }
            }
        }
    }
}

@Composable
private fun ProjectMetaChip(label: String) {
    Text(label, color = Color(0xFFC4CBE0), fontSize = 9.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF252B3B)).padding(horizontal = 7.dp, vertical = 4.dp))
}

private fun homeEditedLabel(lastEditedMs: Long): String {
    if (lastEditedMs <= 0L) return "Saved project"
    val elapsed = (System.currentTimeMillis() - lastEditedMs).coerceAtLeast(0L)
    return when {
        elapsed < 60_000L -> "Edited just now"
        elapsed < 3_600_000L -> "Edited ${elapsed / 60_000L} min ago"
        elapsed < 86_400_000L -> "Edited ${elapsed / 3_600_000L} hr ago"
        else -> "Edited ${elapsed / 86_400_000L} days ago"
    }
}

@Composable
private fun HomeEmptyProjects(searching: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(if (searching) Icons.Default.Search else Icons.Default.Folder, contentDescription = null, tint = ApexPalette.NeonPurple)
        Text(if (searching) "No projects found" else "Your next story starts here", color = ApexPalette.TextPrimary, fontWeight = FontWeight.Bold)
        Text(if (searching) "Try another project or clip name." else "Choose a video or photo to start editing.", color = ApexPalette.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun AiToolsBanner(onChooseVideo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF171542), Color(0xFF24205B), Color(0xFF172E56))))
            .border(1.dp, ApexPalette.NeonPurple.copy(alpha = .48f), RoundedCornerShape(19.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("✦  CREATE FASTER WITH AI", color = Color(0xFFB9B4FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Explore AI tools in your next edit", color = ApexPalette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
            TextButton(onClick = onChooseVideo, contentPadding = PaddingValues(0.dp)) {
                Text("Choose a video  →", color = ApexPalette.NeonCyan, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(62.dp).align(Alignment.CenterStart).graphicsLayer { rotationZ = -9f }.clip(RoundedCornerShape(12.dp)).background(Color(0xFF211A56)))
            Box(Modifier.size(66.dp).align(Alignment.Center).graphicsLayer { rotationZ = -2f }.clip(RoundedCornerShape(12.dp)).background(Color(0xFF242D76)))
            Image(painterResource(com.apexstudio.app.R.drawable.apex_home_mountain_hero), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(58.dp).height(72.dp).align(Alignment.CenterEnd).graphicsLayer { rotationZ = 7f }
                    .clip(RoundedCornerShape(12.dp)).border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(12.dp)))
        }
    }
}
