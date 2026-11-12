package com.apexstudio.app.ui.screens.home

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.data.picker.MediaMetadata
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.data.picker.PreviewStartupLatency
import com.apexstudio.app.data.picker.persistPickedMediaToAppStorage
import com.apexstudio.app.data.repository.MediaRepository
import com.apexstudio.app.data.repository.ProjectRepository
import com.apexstudio.app.data.template.ShortsMusicDownloadResult
import com.apexstudio.app.data.template.ShortsMusicProgress
import com.apexstudio.app.data.template.ShortsMusicRepository
import com.apexstudio.app.data.template.ShortsTemplateCatalog
import com.apexstudio.app.data.template.ShortsTemplateProjectFactory
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.screens.settings.LocalCreatorProfile
import com.apexstudio.app.ui.screens.settings.SettingsPreferences
import com.apexstudio.app.util.TimeFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun HomeScreen(
    onProjectOpen: (String) -> Unit,
    onOpenAiTools: (String) -> Unit = onProjectOpen,
    onOpenSettings: () -> Unit = {},
    onExport: () -> Unit = {},
    shortsTemplateRequestId: String? = null,
    onShortsTemplateRequestConsumed: () -> Unit = {},
    onOpenShortsTemplates: () -> Unit = {},
    onOpenTextStyles: (String) -> Unit = onProjectOpen
) {
    val context = LocalContext.current
    val repo = remember { MediaRepository }
    val projectRepo = remember { ProjectRepository(context) }
    val localProfile by SettingsPreferences.localCreatorProfile(context).collectAsState(initial = LocalCreatorProfile())
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var savedProjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var projectToRename by remember { mutableStateOf<Project?>(null) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var renameText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var showAllProjects by remember { mutableStateOf(false) }
    var openAiToolsAfterCreate by remember { mutableStateOf(false) }
    var openTextStylesAfterCreate by remember { mutableStateOf(false) }
    var activeShortsTemplateId by remember { mutableStateOf<String?>(null) }
    var pendingShortsTemplateId by remember { mutableStateOf<String?>(null) }
    var pendingShortsVideo by remember { mutableStateOf<MediaMetadata?>(null) }
    var isApplyingShortsTemplate by remember { mutableStateOf(false) }
    var shortsDownloadProgress by remember { mutableStateOf<ShortsMusicProgress?>(null) }
    var shortsApplyError by remember { mutableStateOf<String?>(null) }
    var shortsApplyCanRetry by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // DataStore read/JSON decoding must not block the first mobile frame.
        // flowOn only moves upstream work; collect remains on the UI dispatcher,
        // so keep the synchronous legacy fallback off that callback as well.
        projectRepo.loadAll().flowOn(Dispatchers.IO).collect { saved ->
            savedProjectIds = saved.mapTo(mutableSetOf()) { it.id }
            projects = resolveHomeProjects(saved, loadFallback = repo::loadProjects)
        }
    }

    val mediaPicker = remember { MediaPickerHelper(context) }
    mediaPicker.registerLaunchers(onCancelled = {
        openAiToolsAfterCreate = false
        openTextStylesAfterCreate = false
        if (activeShortsTemplateId != null) {
            activeShortsTemplateId = null
            onShortsTemplateRequestConsumed()
        }
    })

    fun applyShortsTemplateToVideo(templateId: String, metadata: MediaMetadata) {
        val template = ShortsTemplateCatalog.byId(templateId)
        if (template == null) {
            shortsApplyError = "This Shorts template is no longer available."
            shortsApplyCanRetry = false
            return
        }
        if (isApplyingShortsTemplate) return
        pendingShortsTemplateId = templateId
        pendingShortsVideo = metadata
        shortsApplyError = null
        shortsDownloadProgress = null
        isApplyingShortsTemplate = true
        scope.launch {
            try {
                when (val result = ShortsMusicRepository.download(context, template.musicAssetId) { progress ->
                    scope.launch { shortsDownloadProgress = progress }
                }) {
                    is ShortsMusicDownloadResult.Success -> {
                        val durableVideoUri = persistPickedMediaToAppStorage(context, metadata.uri, metadata.name)
                        if (metadata.uri.startsWith("content:", ignoreCase = true) && durableVideoUri == metadata.uri) {
                            shortsApplyError = "ApexStudio couldn't make a permanent copy of this video. Choose a locally available video under 500 MB and try again."
                            shortsApplyCanRetry = false
                            return@launch
                        }
                        val videoClip = MediaClip(
                            id = UUID.randomUUID().toString(),
                            name = metadata.name,
                            uri = durableVideoUri,
                            durationMs = metadata.durationMs,
                            trimStartMs = 0L,
                            trimEndMs = metadata.durationMs,
                            thumbnail = null,
                            trackIndex = 0,
                            type = ClipType.VIDEO
                        )
                        val sourceName = metadata.name.substringBeforeLast('.')
                        val baseProject = repo.createProject(
                            name = "${template.name} · $sourceName",
                            clips = listOf(videoClip),
                            videoAspectRatio = metadata.sourceVideoAspectRatio
                        )
                        val readyProject = ShortsTemplateProjectFactory.apply(
                            baseProject,
                            template,
                            result.asset,
                            result.file
                        )
                        withContext(Dispatchers.IO) { projectRepo.saveProject(readyProject) }
                        metadata.selectionResultElapsedRealtimeMs?.let { selectedAt ->
                            PreviewStartupLatency.begin(readyProject.id, videoClip.id, selectedAt)
                        }
                        CrashMarker.mark(context, "HomeScreen: opening Shorts template ${readyProject.id}")
                        pendingShortsTemplateId = null
                        pendingShortsVideo = null
                        onProjectOpen(readyProject.id)
                    }
                    is ShortsMusicDownloadResult.Failed -> {
                        shortsApplyError = result.reason
                        shortsApplyCanRetry = result.canRetry
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                shortsApplyError = error.localizedMessage ?: "Music could not be verified."
                shortsApplyCanRetry = true
            } finally {
                isApplyingShortsTemplate = false
                shortsDownloadProgress = null
            }
        }
    }

    LaunchedEffect(shortsTemplateRequestId) {
        val requestedId = shortsTemplateRequestId ?: return@LaunchedEffect
        if (ShortsTemplateCatalog.byId(requestedId) == null) {
            shortsApplyError = "This Shorts template is no longer available."
            shortsApplyCanRetry = false
        } else {
            activeShortsTemplateId = requestedId
            mediaPicker.pickSingleVisualMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }
        onShortsTemplateRequestConsumed()
    }

    LaunchedEffect(Unit) {
        mediaPicker.pickedMedia.collect { metadataList ->
            if (metadataList.isEmpty()) {
                openAiToolsAfterCreate = false
                openTextStylesAfterCreate = false
                if (activeShortsTemplateId != null) {
                    activeShortsTemplateId = null
                    onShortsTemplateRequestConsumed()
                }
            } else {
                val requestedTemplateId = activeShortsTemplateId
                if (requestedTemplateId != null) {
                    activeShortsTemplateId = null
                    onShortsTemplateRequestConsumed()
                    val selectedVideo = metadataList.singleOrNull()?.takeIf { it.type == ClipType.VIDEO }
                    if (selectedVideo == null) {
                        shortsApplyError = "Choose exactly one video to use this Shorts template."
                        shortsApplyCanRetry = false
                    } else {
                        applyShortsTemplateToVideo(requestedTemplateId, selectedVideo)
                    }
                    return@collect
                }
                val pickedClips = metadataList.map { meta ->
                    meta to MediaClip(
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
                val clips = pickedClips.map { it.second }
                val firstSelectedVideo = pickedClips.firstOrNull { it.first.type == ClipType.VIDEO }
                val projectName = metadataList.first().name.substringBeforeLast('.')
                // Normal Add Video and AI Add Video share this project-creation path.
                // Persist source geometry before the editor's player has decoded a frame.
                val sourceAspect = firstSelectedVideo?.first?.sourceVideoAspectRatio
                val newProject = repo.createProject(
                    name = projectName,
                    clips = clips,
                    videoAspectRatio = sourceAspect
                )

                projectRepo.saveProject(newProject)
                val selectionTimeMs = firstSelectedVideo?.first?.selectionResultElapsedRealtimeMs
                val firstVideoClip = firstSelectedVideo?.second
                if (selectionTimeMs != null && firstVideoClip != null) {
                    PreviewStartupLatency.begin(newProject.id, firstVideoClip.id, selectionTimeMs)
                }
                CrashMarker.mark(context, "HomeScreen: opening project ${newProject.id}")
                if (openTextStylesAfterCreate) {
                    openTextStylesAfterCreate = false
                    onOpenTextStyles(newProject.id)
                } else if (openAiToolsAfterCreate) {
                    openAiToolsAfterCreate = false
                    onOpenAiTools(newProject.id)
                } else {
                    onProjectOpen(newProject.id)
                }
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

    ApexHomeContent(
        projects = projects,
        savedProjectIds = savedProjectIds,
        searchOpen = searchOpen,
        searchText = searchText,
        showAllProjects = showAllProjects,
        onSearchOpen = { searchOpen = !searchOpen },
        onSearchChange = { searchText = it; showAllProjects = it.isNotBlank() },
        onNotifications = {
            val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            runCatching { context.startActivity(settingsIntent) }.onFailure {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.fromParts("package", context.packageName, null))
                )
            }
        },
        onCreateVideo = { openEditorFor(ClipType.VIDEO) },
        onCreatePhoto = { openEditorFor(ClipType.IMAGE) },
        onExploreAiTools = {
            val existingProjectId = latestSavedVideoProjectId(projects, savedProjectIds)
            if (existingProjectId != null) {
                onOpenAiTools(existingProjectId)
            } else {
                openAiToolsAfterCreate = true
                openEditorFor(ClipType.VIDEO)
            }
        },
        onExploreShortsTemplates = onOpenShortsTemplates,
        onExploreTextStyles = {
            val existingProjectId = latestSavedVideoProjectId(projects, savedProjectIds)
            if (existingProjectId != null) {
                onOpenTextStyles(existingProjectId)
            } else {
                openAiToolsAfterCreate = false
                openTextStylesAfterCreate = true
                openEditorFor(ClipType.VIDEO)
            }
        },
        onOpenProject = { onProjectOpen(it.id) },
        onRenameProject = { project ->
            renameText = project.name
            projectToRename = project
        },
        onDeleteProject = { projectToDelete = it },
        onShowAll = { showAllProjects = !showAllProjects },
        localProfile = localProfile,
        onSaveLocalProfile = { profile ->
            scope.launch { SettingsPreferences.setLocalCreatorProfile(context, profile) }
        }
    )

    if (isApplyingShortsTemplate) {
        val progress = shortsDownloadProgress
        val fraction = progress?.takeIf { it.totalBytes > 0L }
            ?.let { (it.doneBytes.toFloat() / it.totalBytes.toFloat()).coerceIn(0f, 1f) }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Preparing your Short") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Downloading and verifying the licensed music before adding it to your timeline.")
                    if (fraction == null) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
                        Text(
                            "${String.format(java.util.Locale.US, "%.1f", progress!!.doneBytes / (1024f * 1024f))} / " +
                                "${String.format(java.util.Locale.US, "%.1f", progress.totalBytes / (1024f * 1024f))} MB"
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    shortsApplyError?.let { errorMessage ->
        val retryTemplateId = pendingShortsTemplateId
        val retryVideo = pendingShortsVideo
        AlertDialog(
            onDismissRequest = {
                shortsApplyError = null
                pendingShortsTemplateId = null
                pendingShortsVideo = null
            },
            title = { Text("Could not prepare template") },
            text = { Text(errorMessage) },
            confirmButton = {
                if (shortsApplyCanRetry && retryTemplateId != null && retryVideo != null) {
                    TextButton(onClick = {
                        shortsApplyError = null
                        applyShortsTemplateToVideo(retryTemplateId, retryVideo)
                    }) { Text("Retry download") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    shortsApplyError = null
                    pendingShortsTemplateId = null
                    pendingShortsVideo = null
                }) { Text("Cancel") }
            }
        )
    }

    projectToRename?.let { project ->
        RenameProjectDialog(
            projectName = renameText,
            onNameChange = { renameText = it },
            onDismiss = { projectToRename = null },
            onConfirm = { enteredName ->
                val newName = renameHomeProject(project, enteredName)?.name
                if (newName != null) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            projectRepo.findById(project.id)?.let { stored ->
                                projectRepo.saveProject(stored.copy(name = newName))
                            }
                        }
                        projectToRename = null
                    }
                }
            }
        )
    }
    projectToDelete?.let { project ->
        DeleteProjectDialog(
            projectName = project.name,
            onDismiss = { projectToDelete = null },
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { projectRepo.deleteProject(project.id) }
                    projectToDelete = null
                }
            }
        )
    }
}

private const val PROJECTS_SECTION_INDEX = 4

@Composable
internal fun DashboardHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("dashboard-header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("YOUR CREATIVE WORKSPACE", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("ApexStudio", color = MaterialTheme.colorScheme.onSurface, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Make every frame yours.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).testTag("dashboard-settings")
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Prefer persisted projects, falling back to bundled/demo projects for first run.
 * [loadFallback] is synchronous and may do work, so always execute it off the
 * caller (HomeScreen's collector) dispatcher.
 */
internal suspend fun resolveHomeProjects(
    savedProjects: List<Project>,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    loadFallback: () -> List<Project>,
): List<Project> = if (savedProjects.isNotEmpty()) {
    savedProjects
} else {
    withContext(ioDispatcher) { loadFallback() }
}

/** Choose the most recently edited saved main-track video project for Home's AI entry. */
internal fun latestSavedVideoProjectId(projects: List<Project>, savedProjectIds: Set<String>): String? =
    projects.asSequence()
        .filter { it.id in savedProjectIds }
        .filter { project -> project.clips.any { clip ->
            clip.type == ClipType.VIDEO && clip.trackIndex == 0 && clip.uri.isNotBlank()
        } }
        .maxByOrNull { it.lastEditedMs }
        ?.id

@Composable
internal fun DashboardEditorEntries(
    onImageEditing: () -> Unit,
    onVideoEditor: () -> Unit
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 380.dp * fontScale) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardEditorEntry(
                    title = "Image Editing Session",
                    subtitle = "Choose a photo to edit",
                    icon = Icons.Default.Image,
                    testTag = "dashboard-image-editing-session",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onImageEditing
                )
                DashboardEditorEntry(
                    title = "Video Editor",
                    subtitle = "Choose a video to edit",
                    icon = Icons.Default.Movie,
                    testTag = "dashboard-video-editor",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onVideoEditor
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun NewProjectHero(enabled: Boolean, onCreate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 164.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.58f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(enabled = enabled, onClick = onCreate)
            .testTag("dashboard-new-project")
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
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
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Create New Project",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "CREATE • EDIT • SHARE",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Start with video, photos, or both.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
internal fun ProjectsShortcut(projectCount: Int, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("dashboard-projects-shortcut"),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Your Projects", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("$projectCount projects available", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Text("View", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

internal fun renameHomeProject(project: Project, enteredName: String): Project? =
    enteredName.trim().takeIf(String::isNotEmpty)?.let { project.copy(name = it) }

@Composable
internal fun RenameProjectDialog(
    projectName: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("home-rename-project-dialog"),
        onDismissRequest = onDismiss,
        title = { Text("Rename project") },
        text = {
            OutlinedTextField(
                value = TextFieldValue(
                    text = projectName,
                    selection = TextRange(projectName.length)
                ),
                onValueChange = { onNameChange(it.text) },
                label = { Text("Project name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(projectName) }, enabled = projectName.trim().isNotEmpty()) {
                Text("Save name")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun DeleteProjectDialog(
    projectName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("home-delete-project-dialog"),
        onDismissRequest = onDismiss,
        title = { Text("Delete project?") },
        text = { Text("\"$projectName\" and its saved editing data will be deleted. This cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete project") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun ProjectRow(
    project: Project,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    canManage: Boolean = true
) {
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
                            listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Movie,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    project.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        TimeFormat.msToShort(project.durationMs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        project.resolution,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${project.fps}fps",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                if (canManage) {
                    Row {
                        IconButton(
                            onClick = onRename,
                            modifier = Modifier.size(40.dp).testTag("home-project-rename-${project.id}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename ${project.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(40.dp).testTag("home-project-delete-${project.id}")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${project.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
