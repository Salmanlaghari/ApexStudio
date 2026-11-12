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
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.text.TextDesignMotionPack
import com.apexstudio.app.data.text.TextDesignMotionPreset
import coil.compose.AsyncImage
import com.apexstudio.app.data.media.VideoThumbnailExtractor
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.ui.screens.settings.LocalCreatorProfile
import com.apexstudio.app.ui.screens.settings.SettingsPreferences
import com.apexstudio.app.ui.theme.ApexThemeTokens
import com.apexstudio.app.util.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    onShowAll: () -> Unit,
    onExploreAiTools: () -> Unit = onCreateVideo,
    onExploreShortsTemplates: () -> Unit = {},
    onExploreTextStyles: () -> Unit = {},
    localProfile: LocalCreatorProfile = LocalCreatorProfile(),
    onSaveLocalProfile: suspend (LocalCreatorProfile) -> Unit = {}
) {
    val theme = ApexThemeTokens.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    var showCreateChoices by remember { mutableStateOf(false) }
    var showLocalProfile by remember { mutableStateOf(false) }
    var profileNameDraft by remember { mutableStateOf(localProfile.displayName) }
    var profileAvatarDraft by remember { mutableStateOf(localProfile.avatarId) }
    var isSavingLocalProfile by remember { mutableStateOf(false) }
    var localProfileSaveError by remember { mutableStateOf<String?>(null) }
    val profileSaveScope = rememberCoroutineScope()
    val filteredProjects = remember(projects, searchText) {
        val query = searchText.trim()
        if (query.isEmpty()) projects else projects.filter { project ->
            project.name.contains(query, ignoreCase = true) ||
                project.clips.any { it.name.contains(query, ignoreCase = true) }
        }
    }
    val visibleProjects = if (showAllProjects || searchText.isNotBlank()) filteredProjects else filteredProjects.take(3)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(theme.bgBase)
    ) {
    val compactPhone = maxWidth < 600.dp
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = if (compactPhone) 12.dp else 24.dp),
            contentPadding = PaddingValues(top = if (compactPhone) 5.dp else 14.dp, bottom = if (compactPhone) 8.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactPhone) 7.dp else 15.dp)
    ) {
        item {
            ApexHomeHeader(
                onSearch = onSearchOpen,
                onNotifications = onNotifications,
                profile = localProfile,
                onProfile = {
                    // Clear dashboard focus before showing the dialog; the name field stays opt-in.
                    focusManager.clearFocus(force = true)
                    profileNameDraft = localProfile.displayName
                    profileAvatarDraft = localProfile.avatarId
                    localProfileSaveError = null
                    showLocalProfile = true
                }
            )
        }
        if (searchOpen) {
            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth().testTag("home-project-search"),
                    singleLine = true,
                    placeholder = { Text("Search projects", color = theme.textTertiary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = theme.primary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.secondary,
                        unfocusedBorderColor = theme.border,
                        cursorColor = theme.primary,
                        focusedContainerColor = theme.bgSurface,
                        unfocusedContainerColor = theme.bgSurface
                    )
                )
            }
        }
        item {
            CreativeWorkspaceHero(
                onCreate = { showCreateChoices = true },
                compactPhone = compactPhone
            )
        }
        item {
            DashboardQuickActions(
                onOpenShortsTemplates = onExploreShortsTemplates,
                onOpenTextStyles = onExploreTextStyles
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (searchText.isBlank()) "Recent Projects" else "Search results",
                        color = theme.textPrimary,
                        fontSize = if (compactPhone) 17.sp else 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (searchText.isNotBlank()) Text("${filteredProjects.size} matching projects", color = theme.textSecondary, fontSize = 12.sp)
                }
                if (filteredProjects.size > 3 && searchText.isBlank()) {
                    TextButton(onClick = onShowAll, modifier = Modifier.testTag("home-see-all")) {
                        Text(if (showAllProjects) "Show less" else "See all", color = theme.primary, fontWeight = FontWeight.SemiBold)
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
                    compactPhone = compactPhone,
                    onOpen = { onOpenProject(project) },
                    onRename = { onRenameProject(project) },
                    onDelete = { onDeleteProject(project) }
                )
            }
        }
        item {
            AiToolsBanner(compactPhone = compactPhone, onOpenAiTools = onExploreAiTools)
        }
    }
    }

    if (showLocalProfile) {
        LocalCreatorProfileDialog(
            displayName = profileNameDraft,
            onDisplayNameChange = { profileNameDraft = it.take(24) },
            avatarId = profileAvatarDraft,
            onAvatarSelected = { profileAvatarDraft = it },
            isSaving = isSavingLocalProfile,
            saveError = localProfileSaveError,
            onDismiss = { if (!isSavingLocalProfile) showLocalProfile = false },
            onSave = {
                if (!isSavingLocalProfile) {
                    isSavingLocalProfile = true
                    localProfileSaveError = null
                    val profile = localCreatorProfileForSave(profileNameDraft, profileAvatarDraft)
                    profileSaveScope.launch {
                        try {
                            if (trySaveLocalCreatorProfile(profile, onSaveLocalProfile)) {
                                showLocalProfile = false
                            } else {
                                localProfileSaveError = "Couldn't save profile. Please try again."
                            }
                        } finally {
                            isSavingLocalProfile = false
                        }
                    }
                }
            }
        )
    }

    if (showCreateChoices) {
        AlertDialog(
            onDismissRequest = { showCreateChoices = false },
            containerColor = theme.bgSurface,
            title = { Text("Create a new project", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Choose the media you want to start with.", color = theme.textSecondary) },
            confirmButton = {
                TextButton(onClick = { showCreateChoices = false; onCreateVideo() }) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = theme.secondary)
                    Spacer(Modifier.width(7.dp))
                    Text("Video project")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showCreateChoices = false; onCreatePhoto() }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = theme.primary)
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
private fun ApexHomeHeader(
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    profile: LocalCreatorProfile,
    onProfile: () -> Unit
) {
    val theme = ApexThemeTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().testTag("dashboard-header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Canvas(Modifier.size(40.dp).testTag("apex-home-mark")) {
            val w = size.width
            val h = size.height
            val mark = Path().apply {
                moveTo(w * .08f, h * .88f)
                lineTo(w * .48f, h * .10f)
                quadraticTo(w * .52f, h * .01f, w * .57f, h * .10f)
                lineTo(w * .96f, h * .88f)
            }
            drawPath(mark, Brush.linearGradient(listOf(theme.secondary, theme.secondaryGlow, theme.primary)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * .18f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawLine(theme.secondary, androidx.compose.ui.geometry.Offset(w * .28f, h * .62f), androidx.compose.ui.geometry.Offset(w * .75f, h * .62f), strokeWidth = w * .14f, cap = StrokeCap.Round)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = theme.textPrimary)) { append("Apex") }
                    withStyle(SpanStyle(color = theme.secondary)) { append("Studio") }
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text("AI-POWERED VIDEO EDITOR", color = theme.textTertiary, fontSize = 7.sp, letterSpacing = 1.7.sp, maxLines = 1)
        }
        HeaderAction(Icons.Default.Search, "Search projects", onSearch, "dashboard-search")
        HeaderAction(Icons.Default.NotificationsNone, "App notification settings", onNotifications, "dashboard-notifications")
        ProfileAvatarAction(profile = profile, onClick = onProfile)
    }
}

@Composable
private fun HeaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, tag: String) {
    val theme = ApexThemeTokens.current
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp).clip(CircleShape).testTag(tag)
    ) {
        Icon(icon, contentDescription = description, tint = theme.textPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CreativeWorkspaceHero(onCreate: () -> Unit, compactPhone: Boolean) {
    val theme = ApexThemeTokens.current
    Box(
        modifier = Modifier.fillMaxWidth().height(if (compactPhone) 156.dp else 232.dp)
            .clip(RoundedCornerShape(if (compactPhone) 20.dp else 24.dp))
            .background(Brush.linearGradient(listOf(theme.bgDeep, theme.secondaryGlow, theme.bgSurface)))
            .border(1.dp, Brush.linearGradient(listOf(theme.primaryGlow, theme.secondary, theme.primary)), RoundedCornerShape(if (compactPhone) 20.dp else 24.dp))
            .testTag("dashboard-new-project")
    ) {
        Image(
            painter = painterResource(com.apexstudio.app.R.drawable.apex_home_mountain_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(theme.bgDeep.copy(alpha = .91f), theme.bgDeep.copy(alpha = .57f), theme.bgDeep.copy(alpha = .22f)))))
        Text("IDEAS\nEDIT\nENHANCE\nSHARE", color = theme.textPrimary.copy(alpha = .60f), fontSize = 7.sp, lineHeight = 12.sp,
            letterSpacing = 1.4.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = if (compactPhone) 13.dp else 20.dp, end = 16.dp))
        Text("A HIGHER\nSTANDARD\nFOR CREATORS", color = theme.textPrimary.copy(alpha = .68f), fontSize = 7.sp,
            lineHeight = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (compactPhone) 12.dp else 18.dp))
        Column(
            modifier = Modifier.fillMaxWidth(if (compactPhone) .90f else .84f)
                .padding(horizontal = if (compactPhone) 12.dp else 20.dp, vertical = if (compactPhone) 10.dp else 19.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactPhone) 4.dp else 9.dp)
        ) {
            Text("Your creative\nworkspace", color = theme.textPrimary, fontSize = if (compactPhone) 21.sp else 27.sp,
                lineHeight = if (compactPhone) 23.sp else 31.sp, fontWeight = FontWeight.ExtraBold)
            Text("Turn ideas into incredible videos\nwith the power of AI.", color = theme.textSecondary,
                fontSize = if (compactPhone) 10.sp else 13.sp, lineHeight = if (compactPhone) 12.sp else 18.sp)
            Row(
                modifier = Modifier.padding(top = if (compactPhone) 1.dp else 5.dp).clip(RoundedCornerShape(30.dp))
                    .background(Brush.horizontalGradient(listOf(theme.primary, theme.secondary)))
                    .clickable(onClick = onCreate).testTag("home-create-project"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = theme.bgDeep, modifier = Modifier.padding(start = if (compactPhone) 10.dp else 15.dp).size(if (compactPhone) 17.dp else 23.dp))
                Text("Create new project", color = theme.bgDeep, fontSize = if (compactPhone) 13.sp else 15.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = if (compactPhone) 7.dp else 13.dp))
                Text("→", color = theme.bgDeep, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = if (compactPhone) 10.dp else 16.dp))
            }
        }
    }
}

@Composable
private fun HomeProjectCard(project: Project, canManage: Boolean, compactPhone: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val theme = ApexThemeTokens.current
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(if (compactPhone) 14.dp else 17.dp))
            .background(theme.bgSurface).border(1.dp, theme.border, RoundedCornerShape(if (compactPhone) 14.dp else 17.dp))
            .clickable(onClick = onOpen).padding(if (compactPhone) 8.dp else 10.dp).testTag("home-recent-project-${project.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(if (compactPhone) 96.dp else 118.dp).height(if (compactPhone) 58.dp else 70.dp)
            .clip(RoundedCornerShape(10.dp)).background(theme.bgElevated), contentAlignment = Alignment.Center) {
            when {
                extractedFrame != null -> androidx.compose.foundation.Image(extractedFrame!!, contentDescription = "Preview of ${project.name}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                imageModel != null -> AsyncImage(model = imageModel, contentDescription = "Preview of ${project.name}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else -> {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(theme.secondaryGlow, theme.primaryGlow))), contentAlignment = Alignment.Center) {
                        Icon(if (clip?.type == ClipType.IMAGE) Icons.Default.PhotoLibrary else Icons.Default.VideoLibrary, contentDescription = null, tint = theme.textPrimary.copy(alpha = .9f), modifier = Modifier.size(29.dp))
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(topStart = 7.dp)).background(theme.bgDeep.copy(alpha = .82f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                Text(TimeFormat.msToShort(project.durationMs), color = theme.textPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(project.name, color = theme.textPrimary, fontSize = if (compactPhone) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = theme.textTertiary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(homeEditedLabel(project.lastEditedMs), color = theme.textSecondary, fontSize = if (compactPhone) 9.sp else 10.sp, maxLines = 1)
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
                    Icon(Icons.Default.MoreVert, contentDescription = "More options for ${project.name}", tint = theme.textSecondary, modifier = Modifier.size(20.dp))
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
    val theme = ApexThemeTokens.current
    Text(label, color = theme.textSecondary, fontSize = 9.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(theme.bgElevated).padding(horizontal = 7.dp, vertical = 4.dp))
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
    val theme = ApexThemeTokens.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(theme.bgSurface)
            .border(1.dp, theme.border, RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(if (searching) Icons.Default.Search else Icons.Default.Folder, contentDescription = null, tint = theme.secondary)
        Text(if (searching) "No projects found" else "Your next story starts here", color = theme.textPrimary, fontWeight = FontWeight.Bold)
        Text(if (searching) "Try another project or clip name." else "Choose a video or photo to start editing.", color = theme.textSecondary, fontSize = 12.sp)
    }
}

@Composable
internal fun DashboardQuickActions(
    onOpenShortsTemplates: () -> Unit,
    onOpenTextStyles: () -> Unit
) {
    val theme = ApexThemeTokens.current
    val fontScale = LocalDensity.current.fontScale
    val featuredStyle = remember { TextDesignMotionPack.presets.firstOrNull() }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().testTag("home-quick-starts")) {
        val stackCards = maxWidth < 290.dp || fontScale >= 1.45f
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "TRY A STARTING POINT",
                color = theme.textTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            if (stackCards) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    DashboardQuickStartCard(
                        title = "Shorts templates",
                        sample = "Cuts · timing · music",
                        description = "Browse ready-made edits",
                        action = "Browse templates →",
                        icon = Icons.Default.Movie,
                        accent = theme.secondary,
                        tag = "home-shorts-templates-entry",
                        onClick = onOpenShortsTemplates,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DashboardQuickStartCard(
                        title = "Text styles",
                        sample = featuredStyle?.name ?: "Preview styles and motion",
                        description = "See a look before you apply it",
                        action = "Preview styles →",
                        icon = Icons.Default.TextFields,
                        accent = featuredStyle?.colorArgb?.let { Color(it.toInt()) } ?: theme.primary,
                        sampleBackground = featuredStyle?.bgArgb?.let { Color(it.toInt()) },
                        sampleStyle = featuredStyle,
                        tag = "home-text-styles-entry",
                        onClick = onOpenTextStyles,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardQuickStartCard(
                        title = "Shorts templates",
                        sample = "Cuts · timing · music",
                        description = "Browse ready-made edits",
                        action = "Browse templates →",
                        icon = Icons.Default.Movie,
                        accent = theme.secondary,
                        tag = "home-shorts-templates-entry",
                        onClick = onOpenShortsTemplates,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardQuickStartCard(
                        title = "Text styles",
                        sample = featuredStyle?.name ?: "Preview styles and motion",
                        description = "See a look before you apply it",
                        action = "Preview styles →",
                        icon = Icons.Default.TextFields,
                        accent = featuredStyle?.colorArgb?.let { Color(it.toInt()) } ?: theme.primary,
                        sampleBackground = featuredStyle?.bgArgb?.let { Color(it.toInt()) },
                        sampleStyle = featuredStyle,
                        tag = "home-text-styles-entry",
                        onClick = onOpenTextStyles,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardQuickStartCard(
    title: String,
    sample: String,
    description: String,
    action: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sampleBackground: Color? = null,
    sampleStyle: TextDesignMotionPreset? = null
) {
    val theme = ApexThemeTokens.current
    Column(
        modifier = modifier
            .heightIn(min = 94.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(theme.bgElevated, theme.bgSurface)))
            .border(1.dp, accent.copy(alpha = 0.48f), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(title, color = theme.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (sampleStyle == null) {
            Text(
                sample,
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(sampleBackground ?: Color.Transparent)
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )
        } else {
            Text(
                "Your title",
                color = Color(sampleStyle.colorArgb.toInt()),
                fontSize = (9f * sampleStyle.sizeScale.coerceIn(0.8f, 1.2f)).sp,
                fontWeight = if (sampleStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (sampleStyle.isItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = when (sampleStyle.fontFamily.lowercase()) {
                    "serif" -> FontFamily.Serif
                    "monospace" -> FontFamily.Monospace
                    else -> FontFamily.SansSerif
                },
                letterSpacing = (sampleStyle.letterSpacingEm * 9f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(sampleBackground ?: theme.bgElevated)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Text(sample, color = theme.textSecondary, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(description, color = theme.textSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(action, color = accent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun AiToolsBanner(compactPhone: Boolean, onOpenAiTools: () -> Unit) {
    val theme = ApexThemeTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(if (compactPhone) 16.dp else 19.dp))
            .background(Brush.linearGradient(listOf(theme.bgDeep, theme.secondaryGlow.copy(alpha = .76f), theme.bgSurface)))
            .border(1.dp, theme.secondary.copy(alpha = .58f), RoundedCornerShape(if (compactPhone) 16.dp else 19.dp))
            .padding(if (compactPhone) 10.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compactPhone) 3.dp else 5.dp)) {
            Text("✦  CREATE FASTER WITH AI", color = theme.secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Explore AI tools in your next edit", color = theme.textPrimary, fontSize = if (compactPhone) 13.sp else 17.sp,
                fontWeight = FontWeight.Bold, lineHeight = if (compactPhone) 16.sp else 20.sp)
            TextButton(onClick = onOpenAiTools, contentPadding = PaddingValues(0.dp), modifier = Modifier.testTag("home-ai-tools-entry")) {
                Text("Open AI tools  →", color = theme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
        Box(Modifier.size(if (compactPhone) 72.dp else 92.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(62.dp).align(Alignment.CenterStart).graphicsLayer { rotationZ = -9f }.clip(RoundedCornerShape(12.dp)).background(theme.bgElevated))
            Box(Modifier.size(if (compactPhone) 52.dp else 66.dp).align(Alignment.Center).graphicsLayer { rotationZ = -2f }.clip(RoundedCornerShape(12.dp)).background(theme.secondaryGlow))
            Image(painterResource(com.apexstudio.app.R.drawable.apex_home_mountain_hero), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(if (compactPhone) 46.dp else 58.dp).height(if (compactPhone) 58.dp else 72.dp).align(Alignment.CenterEnd).graphicsLayer { rotationZ = 7f }
                    .clip(RoundedCornerShape(12.dp)).border(1.dp, theme.primary, RoundedCornerShape(12.dp)))
        }
    }
}


@Composable
internal fun ProfileAvatarAction(profile: LocalCreatorProfile, onClick: () -> Unit) {
    val accent = profileAvatarColor(profile.avatarId)
    Box(
        modifier = Modifier.size(48.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.18f))
            .border(1.dp, accent.copy(alpha = 0.8f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Open local creator profile" }
            .testTag("dashboard-profile"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            profile.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C",
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
internal fun LocalCreatorProfileDialog(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    avatarId: String,
    onAvatarSelected: (String) -> Unit,
    isSaving: Boolean,
    saveError: String?,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val theme = ApexThemeTokens.current
    AlertDialog(
        modifier = Modifier.testTag("local-profile-dialog"),
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = theme.bgSurface,
        title = { Text("Creator profile", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LocalCreatorProfileForm(
                    displayName = displayName,
                    onDisplayNameChange = onDisplayNameChange,
                    avatarId = avatarId,
                    onAvatarSelected = onAvatarSelected
                )
                saveError?.let { message ->
                    Text(
                        message,
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("local-profile-save-error")
                    )
                }
            }
        },
        confirmButton = {
            LocalCreatorProfileSaveAction(
                enabled = !isSaving,
                label = if (isSaving) "Saving…" else "Save profile",
                onSave = onSave
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

@Composable
internal fun LocalCreatorProfileForm(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    avatarId: String,
    onAvatarSelected: (String) -> Unit
) {
    val theme = ApexThemeTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "This profile is stored locally and is not synced by ApexStudio. Android backup or transfer settings may move app data to another device.",
            color = theme.textSecondary,
            fontSize = 12.sp
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth().testTag("local-profile-name"),
            singleLine = true,
            label = { Text("Display name") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.textPrimary,
                unfocusedTextColor = theme.textPrimary,
                focusedBorderColor = theme.secondary,
                unfocusedBorderColor = theme.border,
                focusedLabelColor = theme.primary,
                unfocusedLabelColor = theme.textTertiary,
                cursorColor = theme.primary,
                focusedContainerColor = theme.bgElevated,
                unfocusedContainerColor = theme.bgElevated
            )
        )
        Text("Avatar color", color = theme.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("violet" to "Violet", "cyan" to "Cyan", "amber" to "Amber").forEach { (id, label) ->
                FilterChip(
                    selected = avatarId == id,
                    onClick = { onAvatarSelected(id) },
                    modifier = Modifier.testTag("profile-avatar-$id"),
                    label = { Text(label, fontSize = 10.sp) }
                )
            }
        }
    }
}

@Composable
internal fun LocalCreatorProfileSaveAction(
    enabled: Boolean = true,
    label: String = "Save profile",
    onSave: () -> Unit
) {
    TextButton(onClick = onSave, enabled = enabled) { Text(label) }
}

internal fun localCreatorProfileForSave(displayName: String, avatarId: String): LocalCreatorProfile =
    SettingsPreferences.normalizeLocalCreatorProfile(LocalCreatorProfile(displayName, avatarId))

internal suspend fun trySaveLocalCreatorProfile(
    profile: LocalCreatorProfile,
    onSave: suspend (LocalCreatorProfile) -> Unit
): Boolean = try {
    onSave(profile)
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}

private fun profileAvatarColor(avatarId: String): Color = when (avatarId) {
    "cyan" -> Color(0xFF38D9F5)
    "amber" -> Color(0xFFFFBB55)
    else -> Color(0xFFB692FF)
}
