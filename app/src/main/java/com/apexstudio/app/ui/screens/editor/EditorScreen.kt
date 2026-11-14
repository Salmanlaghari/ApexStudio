package com.apexstudio.app.ui.screens.editor

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.CompositionPlayer
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.data.export.toProjectExportAdjustments
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.media.MediaUriResolver
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.data.picker.PreviewStartupLatency
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.VideoFitMode
import com.apexstudio.app.domain.model.resolveMaskAt
import com.apexstudio.app.domain.model.timeline.animationKeyframeTimes
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.components.compactTimelineHeight
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette
import com.apexstudio.app.util.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.apexstudio.app.domain.model.timeline.sharedVideoClip
/** Editing target may be any layer; do not confuse it with the V1 media source. */
internal fun resolveEditorSelectionClip(clips: List<MediaClip>, selectedClipId: String?): MediaClip? =
    if (selectedClipId != null) clips.firstOrNull { it.id == selectedClipId } else clips.firstOrNull()

/** Resolve the clip feeding the main preview; OVERLAY/AUDIO selections never replace V1. */
internal fun resolveMainPreviewClip(clips: List<MediaClip>, selectedClipId: String?): MediaClip? {
    val selected = selectedClipId?.let { id -> clips.firstOrNull { it.id == id } }
    return selected?.takeIf { it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE) }
        ?: clips.firstOrNull { it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE) }
}

/** Whether the editor chrome should remain visible around the expanded preview. */
internal fun editorChromeVisible(isFullscreenPreview: Boolean): Boolean = !isFullscreenPreview

/** The compact preview sizing control cycles between its two supported player modes. */
internal fun toggleCoverFitMode(isCoverMode: Boolean): Boolean = !isCoverMode

@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onColor: () -> Unit,
    onAudio: () -> Unit,
    entryWorkspace: String = "edit",
    onCloseWorkspace: () -> Unit = {},
    vm: EditorViewModel = viewModel(
        key = projectId,
        factory = EditorViewModelFactory(
            context = LocalContext.current.applicationContext,
            projectId = projectId
        )
    )
) {
    EditorScreenContent(projectId, onBack, onExport, onColor, onAudio, vm, entryWorkspace, onCloseWorkspace)
}

@Composable
private fun EditorScreenContent(
    projectId: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onColor: () -> Unit,
    onAudio: () -> Unit,
    vm: EditorViewModel,
    entryWorkspace: String,
    onCloseWorkspace: () -> Unit
) {
    var showLocalChallenge by remember(projectId) { mutableStateOf(false) }
    val savedState by vm.state.collectAsStateWithLifecycle()
    var workspaceIsFilters by remember(projectId) { mutableStateOf(false) }
    var workspaceTarget by remember(projectId) { mutableStateOf<MediaClip?>(null) }
    var workspacePreview by remember(projectId) { mutableStateOf<MediaClip?>(null) }
    var workspaceOpenTimeActions by remember(projectId) { mutableStateOf(false) }
    var initialReferenceWorkspaceOpened by remember(projectId) { mutableStateOf(false) }
    val state = workspacePreviewState(savedState, workspacePreview)
    val isColorWorkspace = entryWorkspace == "color"
    val isExportWorkspace = entryWorkspace == "export"
    val previewPlaybackSuspended = isColorWorkspace || isExportWorkspace
    var colorExportRequest by remember(projectId, isColorWorkspace) { mutableIntStateOf(0) }
    val colorSaveableState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val workspacePreviewActive by rememberUpdatedState(workspaceTarget != null)
    val dismissWorkspace: () -> Unit = {
        vm.setPlaying(false)
        workspaceTarget = null
        workspacePreview = null
        workspaceIsFilters = false
        workspaceOpenTimeActions = false
    }
    BackHandler(enabled = workspaceTarget != null, onBack = dismissWorkspace)
    LaunchedEffect(entryWorkspace) {
        if (entryWorkspace != "edit") {
            initialReferenceWorkspaceOpened = true
            dismissWorkspace()
            if (vm.state.value.isFullscreenPreview) vm.toggleFullscreenPreview()
        }
        if (entryWorkspace == "audio") vm.openAudioMixer()
    }
    val closeEntryWorkspace: () -> Unit = {
        if (entryWorkspace == "text-styles") vm.closeTextPanel()
        onCloseWorkspace()
    }
    BackHandler(enabled = entryWorkspace != "edit" && entryWorkspace != "ai") {
        if (entryWorkspace == "audio") vm.closeAudioMixer()
        closeEntryWorkspace()
    }
    val aiContentReportState by vm.aiContentReportState.collectAsStateWithLifecycle()
    val audioState by vm.audio.collectAsStateWithLifecycle()
    val transmissionTemplates by vm.transmissionTemplates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentProjectId by rememberUpdatedState(projectId)
    val scope = rememberCoroutineScope()
    CrashMarker.mark(context, "EditorScreen: composable start")
    val mediaPicker = remember { MediaPickerHelper(context) }
    val mediaImportProgress by mediaPicker.importProgress.collectAsStateWithLifecycle()
    val filterEngine = remember { LutFilterEngine(context) }
    val downloadedFilterRegistryVersion by com.apexstudio.app.data.filter.FilterManifest.downloadedRegistryVersion.collectAsStateWithLifecycle()
    val downloadedFxProfiles by com.apexstudio.app.data.fx.DownloadedEffectRepository.effects.collectAsStateWithLifecycle()
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var transitionPreviewPlayer by remember(projectId) { mutableStateOf<CompositionPlayer?>(null) }
    var transitionPreviewReady by remember(projectId) { mutableStateOf(false) }
    var transitionPreviewFailed by remember(projectId) { mutableStateOf(false) }
    val editorExportState by vm.export.collectAsStateWithLifecycle()
    val sourceAspectForLayout = previewContentAspect(
        videoWidth = state.videoWidth.takeIf { it > 0 } ?: exoPlayer?.videoSize?.width ?: 0,
        videoHeight = state.videoHeight.takeIf { it > 0 } ?: exoPlayer?.videoSize?.height ?: 0,
        pixelAspect = exoPlayer?.videoSize?.pixelWidthHeightRatio ?: 1f,
        rotationDegrees = exoPlayer?.videoSize?.unappliedRotationDegrees ?: 0
    )
    val editorLayoutAspectRatio = editorPreviewStageAspectRatio(
        sourceAspectRatio = sourceAspectForLayout,
        projectAspectRatio = state.project?.videoAspectRatio,
        fallbackAspectRatio = previewCompositionAspect(editorExportState.settings.aspectRatio),
    )
    val horizontalProjectLayout = usesHorizontalProjectLayout(editorLayoutAspectRatio)
    val windowConfiguration = LocalConfiguration.current
    // Source geometry wins for the phone shell: a landscape clip on a portrait
    // phone still gets the full-width 16:9 preview + dedicated timeline. The
    // side-by-side landscape workspace is reserved for an actually wide window
    // when the source itself did not already select that source-first shell.
    val useLandscapeWorkspace = windowConfiguration.screenWidthDp > windowConfiguration.screenHeightDp &&
        !horizontalProjectLayout
    // Keep the timeline in composition while changing only its available height;
    // this retains its remembered scroll position, selection and media bindings.
    var isTimelineFocusMode by remember(projectId) { mutableStateOf(false) }
    var manualTimelinePaneHeightDp by rememberSaveable(projectId) { mutableStateOf<Float?>(null) }
    val manualTimelinePaneHeight = manualTimelinePaneHeightDp?.dp
    val compactTimelinePaneHeight = EditorLayoutMetrics.PlaybackBarHeight +
        compactTimelineHeight(state) + EditorLayoutMetrics.TimelineToolbarHeight + 2.dp

    // Clean Voice preview: ONE shared DenoiseAudioProcessor for the life
    // of the editor. The player's audio sink (DenoiseRenderersFactory)
    // runs this instance, and the toggle effect below just flips its
    // runtime fields — no player rebuild, no seek. Starts disabled;
    // the effect syncs it to the selected clip's settings.
    val denoiseProcessor = remember {
        com.apexstudio.app.data.audio.DenoiseAudioProcessor(enabled = false)
    }
    // Dedicated muted player for the live video PiP overlay preview.
    // Bound to the active V2 video overlay clip by EditorPreviewSection.
    var overlayPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var showAddMediaMenu by remember { mutableStateOf(false) }
    var showRoyaltyFreeSheet by remember { mutableStateOf(false) }
    var activeEditorPanel by remember(projectId) { mutableStateOf<EditorPanelKey?>(null) }
    val activateLocalEditorPanel: (EditorPanelKey) -> Unit = { panel ->
        activeEditorPanel = panel
        vm.keepOnlyEditorPanel(null)
    }
    // Main-video framing belongs to the clip, not a transient preview-only switch.
    // Existing photo controls keep their prior preview behavior.
    var nonVideoCoverMode by remember(projectId) { mutableStateOf(DEFAULT_PREVIEW_COVER_MODE) }
    val projectTransitionPlan = remember(state.project?.clips, state.project?.transitions) {
        com.apexstudio.app.data.export.ProjectTransitionTimeline.build(
            state.project?.clips.orEmpty(), state.project?.transitions.orEmpty()
        )
    }
    val framingVideo = (projectTransitionPlan.entryAt(state.playerPositionMs)?.clip
        ?: timelinePreviewClip(state.project?.clips.orEmpty(), state.playerPositionMs))
        ?.takeIf { it.type == ClipType.VIDEO }
    val isCoverMode = framingVideo?.let { it.videoFitMode == VideoFitMode.COVER } ?: nonVideoCoverMode
    var showAiPanel by remember { mutableStateOf(false) }
    LaunchedEffect(entryWorkspace, savedState.project?.id) {
        if (entryWorkspace == "text-styles" && savedState.project != null) {
            activateLocalEditorPanel(EditorPanelKey.TEXT)
            vm.openTextPanel()
        }
    }
    LaunchedEffect(entryWorkspace) {
        if (entryWorkspace == "ai") {
            showAiPanel = true
            activateLocalEditorPanel(EditorPanelKey.AI_TOOLS)
        }
    }
    val closeAiPanel: () -> Unit = {
        showAiPanel = false
        if (entryWorkspace == "ai") onCloseWorkspace()
    }
    BackHandler(
        enabled = entryWorkspace == "ai" && showAiPanel && activeEditorPanel == EditorPanelKey.AI_TOOLS,
        onBack = closeAiPanel
    )
    var showVideoPresets by remember(projectId) { mutableStateOf(false) }
    var quickPresetSection by remember(projectId) { mutableStateOf<QuickVideoPresetSection?>(null) }
    var showVideoUpscale by remember(projectId) { mutableStateOf(false) }
    var showLocalAnalysis by remember { mutableStateOf(false) }
    var showCropPanel by remember { mutableStateOf(false) }
    var isDrawingMode by remember(projectId) { mutableStateOf(false) }
    var showVideoDrawingTools by remember(projectId) { mutableStateOf(false) }
    var drawingTool by remember(projectId) { mutableStateOf(com.apexstudio.app.domain.model.VideoDrawingTool.BRUSH) }
    var drawingColorArgb by remember(projectId) { mutableLongStateOf(0xFFFF3B82L) }
    var drawingBrushWidth by remember(projectId) { mutableFloatStateOf(com.apexstudio.app.domain.model.VideoDrawingStroke.DEFAULT_WIDTH) }
    // Phase 3: mood filter passed from Auto Clip -> Music Library.
    var musicMoodFilter by remember { mutableStateOf<String?>(null) }
    // Phase 3: saved projects for History/Drafts panel.
    var savedProjects by remember { mutableStateOf<List<com.apexstudio.app.domain.model.Project>>(emptyList()) }

    val stateEditorPanels = state.openEditorPanelKeys()
    var previousStateEditorPanels by remember(projectId) { mutableStateOf(emptySet<EditorPanelKey>()) }
    LaunchedEffect(stateEditorPanels) {
        val newlyOpened = stateEditorPanels - previousStateEditorPanels
        val selectedPanel = activeEditorPanelAfterStateChange(
            previous = previousStateEditorPanels,
            current = stateEditorPanels,
            active = activeEditorPanel
        )
        activeEditorPanel = selectedPanel
        if (newlyOpened.isNotEmpty() && selectedPanel != null) {
            vm.keepOnlyEditorPanel(selectedPanel)
        }
        previousStateEditorPanels = stateEditorPanels
    }

    // Phase 3: auto-save every 30s while a project is open.
    // LaunchedEffect is cancelled automatically when this composable leaves the
    // composition; `isActive` + rethrowing CancellationException keep the loop
    // cooperative (a blanket `catch (Exception)` would swallow cancellation and
    // spin forever), and the flush itself runs on IO off the main thread.
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(30_000)
            try {
                withContext(Dispatchers.IO) { vm.flushProject() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { }
        }
    }

    // Phase 3: refresh saved projects when History/Drafts opens.
    // Reads DataStore on IO via a proper suspend call instead of runBlocking,
    // so the main thread never blocks while the read completes.
    LaunchedEffect(state.historyDraftsPanelOpen) {
        if (state.historyDraftsPanelOpen) {
            try {
                savedProjects = withContext(Dispatchers.IO) {
                    vm.projectRepository?.loadAll()?.first() ?: emptyList()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { }
        }
    }

    var localAudioImporting by remember { mutableStateOf(false) }
    var audioImportStartMs by remember { mutableLongStateOf(0L) }
    val audioPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && !localAudioImporting) {
            val insertionTime = audioImportStartMs
            scope.launch {
                localAudioImporting = true
                try {
                    val imported = com.apexstudio.app.data.audio.importLocalAudio(context, uri)
                    vm.addAudioTrack(name = imported.displayName, uri = imported.uri,
                        kind = AudioTrack.Kind.MUSIC, sourceDurationMs = imported.durationMs,
                        startTimeMs = insertionTime)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    android.widget.Toast.makeText(context, error.message ?: "Audio import failed", android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    localAudioImporting = false
                }
            }
        }
    }
    val importPhoneMusic: () -> Unit = {
        if (!localAudioImporting) {
            vm.setPlaying(false)
            vm.setPendingAddAsAudio(false)
            vm.setPendingAddAsOverlay(false)
            vm.setPendingAddToLayer(null)
            audioImportStartMs = vm.playerPositionFlow.value
            audioPickerLauncher.launch("audio/*")
        }
    }

    // PR-G: replacement-image picker for the per-clip Background panel.
    // The picked image is stored on the panel state (pending Apply),
    // never applied immediately.
    val bgReplaceImagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { vm.setBackgroundReplaceImageUri(it.toString()) }
    }

    mediaPicker.registerLaunchers(onCancelled = { vm.clearPendingReplace() })

    // Make bundled OFL text fonts available to the preview + export renderers.
    LaunchedEffect(Unit) {
        com.apexstudio.app.data.text.TextFontRegistry.init(context)
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.combine(
            mediaPicker.pickedMedia,
            mediaPicker.pickGeneration
        ) { meta, gen -> meta to gen }
            .collect { (metadataList, _) ->
                if (metadataList.isNotEmpty()) {
                    val s = vm.state.value
                    if (s.signaturePanelOpen) {
                        vm.updateSignature { signature ->
                            signature.copy(logoUri = Uri.parse(metadataList.first().uri))
                        }
                        return@collect
                    }
                    when {
                        // CapCut-style Replace flow: swap the selected clip/track
                        // media instead of adding new media to the timeline.
                        s.pendingReplaceClipId != null ->
                            vm.replaceClipMedia(s.pendingReplaceClipId!!, metadataList.first())
                        s.pendingReplaceAudioTrackId != null ->
                            vm.replaceAudioTrackMedia(s.pendingReplaceAudioTrackId!!, metadataList.first())
                        else -> vm.onMediaPicked(metadataList, replace = false)
                    }
                }
            }
    }

    LaunchedEffect(previewPlaybackSuspended) {
        if (previewPlaybackSuspended) {
            vm.setPlaying(false)
            exoPlayer?.let { vm.unregisterMainPlayerForAudioEffects(it); it.release() }
            exoPlayer = null
            overlayPlayer?.release()
            overlayPlayer = null
            return@LaunchedEffect
        }
        try {
            // The RenderersFactory wires the shared denoise processor
            // into the player's audio sink (the supported hook for custom
            // AudioProcessor instances; Player has no setAudioEffects API).
            val renderersFactory =
                com.apexstudio.app.data.audio.DenoiseRenderersFactory(context) {
                    denoiseProcessor
                }
            val player = ExoPlayer.Builder(context, renderersFactory).build()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val ready = playbackState == Player.STATE_READY
                    if (ready) vm.setPlayerError(null)
                    vm.setPlayerReady(ready)
                    vm.setBuffering(playbackState == Player.STATE_BUFFERING)
                    if (playbackState == Player.STATE_ENDED) {
                        // A source ending is not the end of a multi-clip project.
                        // Advance the project clock; the timeline resolver binds the next item.
                        val live = vm.state.value
                        val clips = live.project?.clips.orEmpty()
                        val ended = clips.firstOrNull { it.id == player.currentMediaItem?.mediaId }
                        if (!workspacePreviewActive && ended != null) {
                            val end = com.apexstudio.app.domain.model.SpeedCurveMath.timelineStartMsForClip(clips, ended) + ended.effectiveDurationMs()
                            val next = timelinePreviewClip(clips, end)
                            if (next != null && next.id != ended.id) vm.setPlayerPosition(end, forceStateSync = true)
                            else {
                                vm.setPlayerPosition(end, forceStateSync = true)
                                if (end >= live.durationMs) vm.setPlaying(false)
                            }
                        } else vm.setPlaying(false)
                    }
                }
                override fun onRenderedFirstFrame() {
                    PreviewStartupLatency.recordFirstFrame(
                        projectId = currentProjectId,
                        mediaId = player.currentMediaItem?.mediaId,
                        renderedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
                    )?.let { latencyMs ->
                        Log.i("PreviewStartupLatency", "selection_to_first_rendered_video_frame_ms=$latencyMs")
                    }
                    vm.recordPreviewFrameRendered(player.currentMediaItem?.mediaId)
                }
                override fun onPlayerError(error: PlaybackException) {
                    val errorMsg = error.localizedMessage ?: error.errorCodeName
                    Log.e("EditorScreen", "ExoPlayer playback warning: $errorMsg", error)
                    vm.setPlayerError(errorMsg)
                    if (workspacePreviewActive) {
                        vm.setPlaying(false)
                        return
                    }
                    try {
                        player.prepare()
                        if (state.isPlaying) {
                            player.play()
                        }
                    } catch (ex: Exception) {
                        Log.e("EditorScreen", "Player recovery fallback: ${ex.message}")
                    }
                }
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    vm.setVideoSize(videoSize.width, videoSize.height)
                }
            })
            if (player.playbackState == Player.STATE_READY) {
                vm.setPlayerReady(true)
            }
            exoPlayer = player
            vm.registerMainPlayerForAudioEffects(player)
        } catch (e: Exception) {
            exoPlayer = null
        }
    }

    DisposableEffect(projectId) {
        onDispose { PreviewStartupLatency.clear(projectId) }
    }

    DisposableEffect(Unit) {
        onDispose {
            val player = exoPlayer
            // Clear only this player's registration before release. An older
            // disposal must not unregister a newer player attached to the VM.
            if (player != null) vm.unregisterMainPlayerForAudioEffects(player)
            player?.release()
            exoPlayer = null
            overlayPlayer?.release()
            overlayPlayer = null
        }
    }

    // Overlay PiP player: muted, no controller — the preview section binds
    // it to the active video overlay clip (trimmed range, looping).
    LaunchedEffect(previewPlaybackSuspended) {
        if (previewPlaybackSuspended) return@LaunchedEffect
        try {
            val player = ExoPlayer.Builder(context).build()
            player.volume = 0f
            player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            overlayPlayer = player
        } catch (e: Exception) {
            Log.e("EditorScreen", "Overlay player init failed: ${e.message}")
            overlayPlayer = null
        }
    }

    val currentSelectedClip = remember(state.project?.clips, state.selectedClipId) {
        resolveEditorSelectionClip(state.project?.clips.orEmpty(), state.selectedClipId)
    }
    // Overlay selection is an editing target, not a new source for the
    // background/V1 preview player (which has its own dedicated PiP player).
    val mainPreviewClip = projectTransitionPlan.entryAt(state.playerPositionMs)?.clip
        ?: timelinePreviewClip(state.project?.clips.orEmpty(), state.playerPositionMs)
    val aiTargetVideo = resolveAiTargetClip(currentSelectedClip, state.selectedClipId, mainPreviewClip)

    // Overlay media-kind probe — SINGLE source of truth for every
    // consumer (the player binding below, photoClip + PiP canvas in
    // EditorPreviewSection, which receives this map as a param). The
    // map is tri-state: true = video, false = image (positive
    // evidence), ABSENT = pending OR unanswerable (never defaulted
    // to image). One shared map means the binding and the preview
    // section can never disagree about a clip's kind (no lag, no
    // wrong-path flash): both read the same map value from the same
    // composition snapshot. Probes ALL overlay clips (hidden
    // layers included) so a selected clip always resolves.
    var overlayIsVideo by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val allOverlayClips = remember(state.project?.clips) {
        state.project?.clips?.filter { it.type == ClipType.OVERLAY } ?: emptyList()
    }
    LaunchedEffect(allOverlayClips.map { it.id to it.uri }) {
        allOverlayClips.forEach { clip ->
            if (!overlayIsVideo.containsKey(clip.id)) {
                val isVideo = com.apexstudio.app.data.media.OverlayMediaProbe.isVideo(
                    context, clip.uri
                )
                // Store only definite answers: null (unanswerable)
                // stays absent, so consumers treat it as unknown —
                // never as image — and a later re-probe can retry.
                if (isVideo != null) {
                    overlayIsVideo = overlayIsVideo + (clip.id to isVideo)
                }
            }
        }
    }
    LaunchedEffect(
        exoPlayer, mainPreviewClip?.id, mainPreviewClip?.uri, mainPreviewClip?.trimStartMs,
        mainPreviewClip?.trimEndMs, projectTransitionPlan, transitionPreviewReady
    ) {
        val player = exoPlayer ?: return@LaunchedEffect
        val clip = mainPreviewClip
        if (clip == null) {
            vm.setVideoSize(0, 0)
            try {
                player.stop()
                player.clearMediaItems()
            } catch (_: Exception) {}
            vm.setPlayerReady(true)
            return@LaunchedEffect
        }
        // Still-image clips are rendered by PhotoClipPreview (Compose),
        // not ExoPlayer — feeding a JPEG to the player would just error.
        val isPhotoClip = clip.type == ClipType.IMAGE
        if (isPhotoClip) {
            vm.setVideoSize(0, 0)
            try {
                player.stop()
                player.clearMediaItems()
            } catch (_: Exception) {}
            vm.setPlayerReady(true)
            vm.setPlayerDuration(clip.durationMs)
            return@LaunchedEffect
        }
        val playableUri = MediaUriResolver.resolvePlayableUri(context, clip.uri)
        // MediaItem.fromUri leaves mediaId at the shared default, so
        // comparing mediaIds can never distinguish two clips — after
        // the first clip, selecting/adding a different clip NEVER
        // replaced the player's media (playhead kept following the old
        // clip). Compare the resolved URIs instead, and tag the item
        // with the clip id so future comparisons are exact.
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(playableUri)
            .setMediaId(clip.id)
            .build()
        val currentUri = player.currentMediaItem?.localConfiguration?.uri
        if (currentUri != playableUri || player.currentMediaItem?.mediaId != clip.id) {
            try {
                vm.setPlayerReady(false)
                // Dimensions belong to the previously decoded item until the
                // new player's onVideoSizeChanged callback arrives.
                vm.setVideoSize(0, 0)
                // Rebinding after a cross-clip seek must retain the requested
                // project position, not jump back to the source trim head.
                bindEditorPreviewItem(player, mediaItem, clip, state.project?.clips.orEmpty(),
                    vm.playerPositionFlow.value, state.isPlaying && !transitionPreviewReady,
                    projectTransitionPlan)
            } catch (e: Exception) {
                Log.e("EditorScreen", "player.prepare() failed", e)
            }
            vm.setPlayerDuration(clip.durationMs)
        }
    }

    LaunchedEffect(exoPlayer, state.playbackSpeed, mainPreviewClip?.id, mainPreviewClip?.speedMultiplier) {
        val player = exoPlayer ?: return@LaunchedEffect
        try {
            val speed = com.apexstudio.app.domain.model.SpeedCurveMath.sanitizeConstantSpeed(
                mainPreviewClip?.speedMultiplier ?: state.playbackSpeed
            )
            player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
        } catch (e: Exception) {
            Log.e("EditorScreen", "Failed to set playback speed", e)
        }
    }

    // Editing a PiP layer must not change the background video's audio gain.
    LaunchedEffect(exoPlayer, mainPreviewClip?.id, mainPreviewClip?.volume) {
        val player = exoPlayer ?: return@LaunchedEffect
        try {
            player.volume = (mainPreviewClip?.volume ?: 1f).coerceIn(0f, 2f)
        } catch (e: Exception) {
            Log.e("EditorScreen", "Failed to set clip volume", e)
        }
    }

    // Clean Voice (per-clip voice noise cleanup): the preview runs the
    // SAME DenoiseAudioProcessor class the export uses, so what you hear
    // is exactly what bakes into the MP4. The shared processor already
    // sits in the player's audio chain (DenoiseRenderersFactory), so
    // toggling just flips its runtime fields — it takes effect on the
    // next queued audio buffer, no player rebuild, no seek.
    LaunchedEffect(
        mainPreviewClip?.id,
        mainPreviewClip?.denoiseEnabled,
        mainPreviewClip?.denoiseStrength
    ) {
        val clip = mainPreviewClip ?: return@LaunchedEffect
        denoiseProcessor.denoiseSettings =
            com.apexstudio.app.data.audio.DenoiseSettings(
                clip.denoiseEnabled,
                clip.denoiseStrength
            )
    }

    val activePreset: com.apexstudio.app.data.filter.FilterPreset? = remember(state.activeFilterId, state.customImportedLuts, downloadedFilterRegistryVersion) {
        state.activeFilterId?.let { id ->
            filterEngine.manifest.filters.firstOrNull { it.id == id }
                ?: state.customImportedLuts.firstOrNull { it.id == id }
        }
    }
    val activeDownloadedFx by produceState<com.apexstudio.app.data.fx.DownloadedFxEffect?>(
        initialValue = null,
        key1 = state.activeFxId,
        key2 = downloadedFxProfiles
    ) {
        // Explicit verified lookup: findVerified re-checks the installed
        // entry's digest on the IO dispatcher and invalidates stale packs;
        // composition itself never touches disk.
        value = null
        value = com.apexstudio.app.data.fx.DownloadedEffectRepository.findVerified(state.activeFxId)
    }
    val previewGpuFilterConfig = mainPreviewClip
        ?.takeIf { it.type == ClipType.VIDEO }
        ?.gpuFilterConfig
        ?: com.apexstudio.app.domain.model.GpuFilterConfig()
    val previewGpuFilterIntensity = remember {
        java.util.concurrent.atomic.AtomicReference(previewGpuFilterConfig.filterIntensity)
    }
    val globalFilterIntensity = remember {
        java.util.concurrent.atomic.AtomicReference(state.filterIntensity)
    }
    SideEffect {
        previewGpuFilterIntensity.set(previewGpuFilterConfig.filterIntensity)
        globalFilterIntensity.set(state.filterIntensity)
    }
    val previewGpuFilterStageConfig = gpuFilterConfigPreviewKey(previewGpuFilterConfig)
    val sharedPreviewClip = mainPreviewClip?.let { clip -> state.project?.sharedVideoClip(clip.id) }
    val selectedKeyframes = mainPreviewClip?.keyframes
    val globalFxBinding = remember(state.activeFxId, activeDownloadedFx, state.fxIntensity, state.fxSpeed) {
        globalFxPreviewBinding(state.activeFxId, activeDownloadedFx, state.fxIntensity, state.fxSpeed)
    }

    val currentEffects = remember(
        activePreset,
        downloadedFilterRegistryVersion,
        previewGpuFilterStageConfig,
        state.gpuFilterCompareMode,
        state.lutCompareMode,
        globalFxBinding,
        state.adjustments,
        selectedKeyframes,
        sharedPreviewClip
    ) {
        buildList<androidx.media3.common.Effect> {
            // Match project export's per-clip LUT/adjustment stages when the
            // config is entirely representable by Media3. Keep profile-only
            // and GPU-only stylists on their explicit Compose fallback path.
            if (!state.gpuFilterCompareMode && gpuFilterConfigUsesMedia3(previewGpuFilterConfig) &&
                !previewGpuFilterConfig.isDefault
            ) {
                previewGpuFilterConfig.filterPresetId
                    ?.let { com.apexstudio.app.data.filter.FilterManifest.presetById(it) }
                    ?.let { preset ->
                        add(com.apexstudio.app.data.filter.LutFilterGlEffect(
                            context, preset,
                            intensityProvider = { previewGpuFilterIntensity.get() }
                        ))
                    }
                val perClipAdjustments = previewGpuFilterConfig.toProjectExportAdjustments()
                if (!perClipAdjustments.isDefault) {
                    add(com.apexstudio.app.data.adjust.AdjustmentsGlEffect(perClipAdjustments))
                }
            }
            if (!state.lutCompareMode && activePreset != null) {
                add(
                    com.apexstudio.app.data.filter.LutFilterGlEffect(
                        context, activePreset, intensityProvider = { globalFilterIntensity.get() },
                        presentationTimeIntensityProvider = sharedPreviewClip?.let { typed ->
                            { timeUs ->
                                val projectMs = mainPreviewClip?.let { clip ->
                                    com.apexstudio.app.domain.model.ClipTimeMapping.projectAtSourceTime(
                                        state.project?.clips.orEmpty(), clip, timeUs / 1_000L
                                    )
                                } ?: timeUs / 1_000L
                                globalFilterIntensity.get() * typed.filterIntensityAt(projectMs) * typed.lutIntensityAt(projectMs)
                            }
                        }
                    )
                )
            }
            if (!state.lutCompareMode && !state.adjustments.isDefault) {
                val adjustMatrix = com.apexstudio.app.data.filter.FilterColorMatrix.getCombinedMatrix(
                    filterId = null,
                    intensity = 0f,
                    adjustments = state.adjustments
                )
                add(com.apexstudio.app.data.filter.ColorMatrixGlEffect(adjustMatrix))
            }
            // Video Adjustments v2: the non-matrix adjustments
            // (highlights/shadows/sharpness/fade/vignette/grain/lightness/
            // whites/blacks/hue/vibrance/clarity/denoise) can't be expressed
            // as a color matrix, so they run in the live GL shader below.
            // Base = all-default VideoAdjustments so the matrix-covered
            // knobs don't double-apply; only the v2 providers are live.
            if (!state.lutCompareMode) run {
                val v2 = state.adjustments
                if (v2.hasNonMatrixAdjustments) {
                    add(
                        com.apexstudio.app.data.adjust.AdjustmentsGlEffect(
                            adjustments = com.apexstudio.app.domain.model.VideoAdjustments(),
                            highlightsProvider = { state.adjustments.highlights },
                            shadowsProvider = { state.adjustments.shadows },
                            sharpnessProvider = { state.adjustments.sharpness },
                            fadeProvider = { state.adjustments.fade },
                            vignetteProvider = { state.adjustments.vignette },
                            grainProvider = { state.adjustments.grain },
                            lightnessProvider = { state.adjustments.lightness },
                            whitesProvider = { state.adjustments.whites },
                            blacksProvider = { state.adjustments.blacks },
                            hueProvider = { state.adjustments.hue },
                            vibranceProvider = { state.adjustments.vibrance },
                            clarityProvider = { state.adjustments.clarity },
                            denoiseProvider = { state.adjustments.denoise }
                        )
                    )
                }
            }
            globalFxBinding?.createEffect()?.let(::add)
            val hasSharedAnimation = sharedPreviewClip?.let {
                it.animationKeyframeTimes().isNotEmpty()
            } == true
            if ((selectedKeyframes != null && !selectedKeyframes.isEmpty()) || hasSharedAnimation) {
                val trackRef = arrayOf(selectedKeyframes)
                add(
                    com.apexstudio.app.data.animation.KeyframeAnimationEffect(
                        trackProvider = { trackRef[0] ?: com.apexstudio.app.domain.model.KeyframeTrack() },
                        timeMsAtPresentationTimeUs = { timeUs ->
                            mainPreviewClip?.let { clip ->
                                com.apexstudio.app.domain.model.ClipTimeMapping.projectAtSourceTime(
                                    state.project?.clips.orEmpty(), clip, timeUs / 1_000L)
                            } ?: timeUs / 1_000L
                        },
                        sharedVideoClipProvider = { sharedPreviewClip }
                    ).buildEffects().first()
                )
            }
        }
    }

    val transitionPreviewEffectsByClipId = remember(
        projectTransitionPlan,
        state.project?.clips,
        state.project?.videoAspectRatio,
        activePreset,
        globalFxBinding,
        state.filterIntensity,
        state.adjustments,
        state.gpuFilterCompareMode,
        state.lutCompareMode
    ) {
        val clips = state.project?.clips.orEmpty()
        val aspect = state.project?.videoAspectRatio?.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
        projectTransitionPlan.entries.associate { entry ->
            val clip = entry.clip
            val shared = state.project?.sharedVideoClip(clip.id)
            val clipGpuConfig = clip.gpuFilterConfig
            val projectTimeAtPresentationTimeUs: (Long) -> Long = { presentationTimeUs ->
                val itemLocalUs = com.apexstudio.app.data.animation.itemLocalPresentationTimeUs(
                    presentationTimeUs, entry.timelineStartMs
                )
                val localOutputMs = (itemLocalUs / 1_000L).coerceIn(0L, entry.durationMs)
                val sourceTimeMs = entry.sourceTimeAtOutputOffset(localOutputMs)
                projectTransitionPlan.projectTimeAtSourceTime(clips, clip, sourceTimeMs)
                    ?: (entry.timelineStartMs + localOutputMs)
            }
            val effects = buildList<androidx.media3.common.Effect> {
                clip.faceCropTrack?.let { track ->
                    val config = com.apexstudio.app.data.effect.FaceCropRenderConfig(
                        track = track,
                        trimStartMs = entry.sourceStartMs,
                        trimEndMs = entry.sourceEndMs
                    )
                    add(com.apexstudio.app.data.effect.FaceCropGlEffect.forPreview(config.previewConfig()))
                }
                clip.videoCrop.takeUnless { it.isFullFrame }
                    ?.let { com.apexstudio.app.data.effect.VideoCropGlEffect.fromSettings(it) }
                    ?.let(::add)
                com.apexstudio.app.data.template.ProjectVideoPresets.colorEffect(clip.projectVideoEffectId)
                    ?.let(::add)
                addAll(com.apexstudio.app.data.effect.WorkspaceEffectGlEffect.createEffects(
                    clip.workspaceEffects,
                    entry.sourceStartMs,
                    shared?.takeIf { it.effectIntensityKeyframes.isNotEmpty() }?.let { typed ->
                        { timeUs -> typed.effectIntensityAt(projectTimeAtPresentationTimeUs(timeUs)) }
                    }
                ))
                if (!state.gpuFilterCompareMode && gpuFilterConfigUsesMedia3(clipGpuConfig) &&
                    !clipGpuConfig.isDefault
                ) {
                    clipGpuConfig.filterPresetId
                        ?.let { com.apexstudio.app.data.filter.FilterManifest.presetById(it) }
                        ?.let { preset ->
                            add(com.apexstudio.app.data.filter.LutFilterGlEffect(
                                context, preset, clipGpuConfig.filterIntensity
                            ))
                        }
                    val perClipAdjustments = clipGpuConfig.toProjectExportAdjustments()
                    if (!perClipAdjustments.isDefault) {
                        add(com.apexstudio.app.data.adjust.AdjustmentsGlEffect(perClipAdjustments))
                    }
                }
                if (!state.lutCompareMode && activePreset != null) {
                    add(com.apexstudio.app.data.filter.LutFilterGlEffect(
                        context,
                        activePreset,
                        intensityProvider = { globalFilterIntensity.get() },
                        presentationTimeIntensityProvider = shared?.let { typed ->
                            { timeUs ->
                                globalFilterIntensity.get() * typed.filterIntensityAt(
                                    projectTimeAtPresentationTimeUs(timeUs)
                                ) * typed.lutIntensityAt(projectTimeAtPresentationTimeUs(timeUs))
                            }
                        }
                    ))
                }
                if (!state.lutCompareMode && !state.adjustments.isDefault) {
                    val matrix = com.apexstudio.app.data.filter.FilterColorMatrix.getCombinedMatrix(
                        filterId = null, intensity = 0f, adjustments = state.adjustments
                    )
                    add(com.apexstudio.app.data.filter.ColorMatrixGlEffect(matrix))
                    if (state.adjustments.hasNonMatrixAdjustments) {
                        add(com.apexstudio.app.data.adjust.AdjustmentsGlEffect(
                            adjustments = com.apexstudio.app.domain.model.VideoAdjustments(),
                            highlightsProvider = { state.adjustments.highlights },
                            shadowsProvider = { state.adjustments.shadows },
                            sharpnessProvider = { state.adjustments.sharpness },
                            fadeProvider = { state.adjustments.fade },
                            vignetteProvider = { state.adjustments.vignette },
                            grainProvider = { state.adjustments.grain },
                            lightnessProvider = { state.adjustments.lightness },
                            whitesProvider = { state.adjustments.whites },
                            blacksProvider = { state.adjustments.blacks },
                            hueProvider = { state.adjustments.hue },
                            vibranceProvider = { state.adjustments.vibrance },
                            clarityProvider = { state.adjustments.clarity },
                            denoiseProvider = { state.adjustments.denoise }
                        ))
                    }
                }
                globalFxBinding?.createEffect()?.let(::add)
                if (!clip.keyframes.isEmpty() || shared?.animationKeyframeTimes()?.isNotEmpty() == true) {
                    add(com.apexstudio.app.data.animation.KeyframeAnimationEffect(
                        trackProvider = { clip.keyframes },
                        timeMsAtPresentationTimeUs = projectTimeAtPresentationTimeUs,
                        sharedVideoClipProvider = { shared }
                    ).buildEffects().first())
                }
                com.apexstudio.app.data.effect.MaskGlEffect.fromMask(
                    clip.mask,
                    clip.mask?.let { mask ->
                        { timeUs ->
                            val projectMs = projectTimeAtPresentationTimeUs(timeUs)
                            mask.resolveMaskAt((projectMs - entry.timelineStartMs).coerceAtLeast(0L))
                        }
                    }
                )?.let(::add)
                val captions = clip.textOverlays.filter { it.text.isNotBlank() }
                if (captions.isNotEmpty()) {
                    add(com.apexstudio.app.data.effect.TextOverlayGlEffect(context, captions, aspect))
                }
                clip.stickers.forEach { sticker ->
                    if (sticker.isPngSticker()) {
                        add(com.apexstudio.app.data.effect.StickerGlEffect(context, sticker, aspect))
                    } else {
                        add(com.apexstudio.app.data.effect.TextOverlayGlEffect(
                            context,
                            com.apexstudio.app.data.export.projectEmojiStickerAsTextOverlay(sticker),
                            aspect
                        ))
                    }
                }
            }
            clip.id to effects
        }
    }

    val transitionPreviewEffectsRepresentable = projectTransitionPlan.entries.all { entry ->
        val clip = entry.clip
        clip.speedMultiplier == 1f && clip.speedPoints.isEmpty() &&
            (clip.gpuFilterConfig.isDefault || gpuFilterConfigUsesMedia3(clip.gpuFilterConfig))
    }
    val transitionPreviewEligible = projectTransitionPlan.transitions.isNotEmpty() &&
        projectTransitionPlan.entries.all { it.clip.type == ClipType.VIDEO } &&
        transitionPreviewEffectsRepresentable &&
        !state.gpuFilterCompareMode && !state.lutCompareMode &&
        state.activeArFilterId == null && state.coverText.isBlank() &&
        state.project?.signature == null && state.project?.stickers.orEmpty().isEmpty() &&
        state.project?.videoDrawings.orEmpty().isEmpty() &&
        projectTransitionPlan.transitions.all {
            com.apexstudio.app.data.gl.PackedTransitionGlEffect.supportsTransitionType(it.transition.type)
        }
    val transitionPreviewNotice = if (projectTransitionPlan.transitions.isNotEmpty() && !transitionPreviewEligible) {
        com.apexstudio.app.data.export.transitionPreviewBlocker(
            plan = projectTransitionPlan,
            effectsRepresentable = transitionPreviewEffectsRepresentable,
            gpuFilterCompareMode = state.gpuFilterCompareMode,
            lutCompareMode = state.lutCompareMode,
            activeArFilterId = state.activeArFilterId,
            coverText = state.coverText,
            hasSignature = state.project?.signature != null,
            hasGlobalStickers = state.project?.stickers.orEmpty().isNotEmpty(),
            hasGlobalDrawings = state.project?.videoDrawings.orEmpty().isNotEmpty(),
        )?.let { com.apexstudio.app.data.export.transitionPreviewBlockerMessage(it) }
            ?.plus(" Whole-project export still includes saved transitions.")
            ?: "The full transition preview is unavailable for this project. Whole-project export still includes saved transitions."
    } else null
    LaunchedEffect(projectId, previewPlaybackSuspended, transitionPreviewEligible) {
        if (previewPlaybackSuspended || !transitionPreviewEligible) {
            vm.setPreviewOnlyError(null)
            transitionPreviewPlayer = null
            transitionPreviewReady = false
            transitionPreviewFailed = false
        } else if (transitionPreviewPlayer == null && !transitionPreviewFailed) {
            vm.setPreviewOnlyError(null)
            try {
                val player = com.apexstudio.app.data.export.createProjectTransitionPreviewPlayer(context)
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (transitionPreviewPlayer !== player) return
                        if (playbackState == Player.STATE_ENDED &&
                            vm.state.value.durationMs <= projectTransitionPlan.durationMs
                        ) vm.setPlaying(false)
                    }

                    override fun onRenderedFirstFrame() {
                        if (transitionPreviewPlayer === player) {
                            transitionPreviewReady = true
                            vm.setPreviewOnlyError(null)
                        }
                    }
                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        if (transitionPreviewPlayer === player) vm.setVideoSize(videoSize.width, videoSize.height)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (transitionPreviewPlayer !== player) return
                        Log.e("EditorScreen", "Transition preview failed; using source player", error)
                        transitionPreviewReady = false
                        transitionPreviewFailed = true
                        transitionPreviewPlayer = null
                        vm.setPreviewOnlyError("Transition preview is unavailable on this device; export still renders the transition.")
                    }
                })
                transitionPreviewPlayer = player
                transitionPreviewReady = false
                transitionPreviewFailed = false
            } catch (error: Exception) {
                Log.e("EditorScreen", "Transition preview player init failed", error)
                transitionPreviewPlayer = null
                transitionPreviewReady = false
                transitionPreviewFailed = true
                vm.setPreviewOnlyError("Transition preview is unavailable on this device; export still renders the transition.")
            }
        }
    }

    LaunchedEffect(
        transitionPreviewPlayer,
        transitionPreviewEligible,
        projectTransitionPlan,
        state.project?.clips,
        state.project?.videoAspectRatio,
        transitionPreviewEffectsByClipId
    ) {
        val player = transitionPreviewPlayer ?: return@LaunchedEffect
        if (!transitionPreviewEligible || previewPlaybackSuspended) return@LaunchedEffect
        try {
            transitionPreviewReady = false
            player.pause()
            val aspect = state.project?.videoAspectRatio?.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
            val outputWidth = 960
            val outputHeight = (outputWidth / aspect).toInt().coerceIn(240, 2_160)
            val composition = com.apexstudio.app.data.export.buildProjectTransitionPreviewComposition(
                context = context,
                plan = projectTransitionPlan,
                outputWidth = outputWidth,
                outputHeight = outputHeight,
                previewEffectsByClipId = transitionPreviewEffectsByClipId
            )
            player.setComposition(composition)
            player.prepare()
            val lastFrameMs = (projectTransitionPlan.durationMs - 1L).coerceAtLeast(0L)
            player.seekTo(state.playerPositionMs.coerceIn(0L, lastFrameMs))
            if (state.isPlaying) player.play() else player.pause()
        } catch (error: Exception) {
            Log.e("EditorScreen", "Transition preview composition failed; using source player", error)
            runCatching { player.release() }
            transitionPreviewReady = false
            transitionPreviewFailed = true
            transitionPreviewPlayer = null
            vm.setPreviewOnlyError("Transition preview is unavailable on this device; export still renders the transition.")
        }
    }

    val activePreviewPlayer: Player? = if (transitionPreviewReady) transitionPreviewPlayer else exoPlayer

    DisposableEffect(transitionPreviewPlayer) {
        val player = transitionPreviewPlayer
        onDispose {
            if (player != null) runCatching {
                player.pause()
                player.release()
            }
        }
    }

    val audioPlaybackManager = remember { com.apexstudio.app.data.engine.AudioPlaybackManager(context) }
    DisposableEffect(Unit) {
        onDispose {
            audioPlaybackManager.release()
        }
    }

    LaunchedEffect(exoPlayer, state.isPlaying, state.project?.audioTracks, mainPreviewClip?.id,
        transitionPreviewReady, projectTransitionPlan) {
        val tracks = state.project?.audioTracks ?: emptyList()
        audioPlaybackManager.sync(state.isPlaying, vm.playerPositionFlow.value, tracks)
        val player = exoPlayer ?: return@LaunchedEffect
        if (transitionPreviewReady) {
            player.pause()
            return@LaunchedEffect
        }
        if (state.isPlaying) {
            resumeEditorPreview(
                player,
                state.project?.clips.orEmpty(),
                vm.playerPositionFlow.value,
                projectTransitionPlan
            )
        } else {
            player.pause()
        }
    }

    LaunchedEffect(transitionPreviewPlayer, transitionPreviewReady, state.isPlaying) {
        val player = transitionPreviewPlayer ?: return@LaunchedEffect
        if (!transitionPreviewReady) {
            player.pause()
        } else if (state.isPlaying) {
            player.play()
        } else {
            player.pause()
        }
    }

    // Single source of truth for playback position: player.currentPosition.
    // The old fallback extrapolated playerPositionMs + delta * speed
    // whenever player.isPlaying was momentarily false (buffering/seek) —
    // a second clock that drifted from the player and hid starvation.
    // Now: a player always reports its own position (frozen = honestly
    // stuck); extrapolation survives ONLY for player-less playback
    // (photo clips, where ExoPlayer holds no media item).
    // Audio sync is throttled to drift-correction cadence (250ms): the
    // old per-tick sync() did MediaPlayer IPC + conditional seekTo 30x/s
    // on the main thread — a real contributor to the Edit Pack stall
    // (counter crawling, TextureView frame frozen, isPlaying still true).
    // Play/pause/seek transitions still sync immediately (see the
    // isPlaying LaunchedEffect above + seekPlayerAndState).
    LaunchedEffect(
        state.isPlaying,
        state.durationMs,
        state.playbackSpeed,
        transitionPreviewPlayer,
        transitionPreviewReady,
        projectTransitionPlan,
        // Restart when the selected clip's curve changes so the loop
        // always reads the current points (not a stale snapshot).
        mainPreviewClip?.speedPoints,
        mainPreviewClip?.speedMultiplier,
        mainPreviewClip?.id
    ) {
        val totalDur = state.durationMs.coerceAtLeast(1000L)
        var lastTime = android.os.SystemClock.uptimeMillis()
        var lastAudioSyncAt = 0L
        var lastAudioSyncPos = -1L
        // Last curve speed pushed to ExoPlayer (null = constant-speed
        // mode, owned by the playbackSpeed LaunchedEffect above).
        var lastCurveSpeedApplied: Float? = null
        while (isActive) {
            if (state.isPlaying) {
                val now = android.os.SystemClock.uptimeMillis()
                val delta = now - lastTime
                lastTime = now

                val player = exoPlayer
                val timedClip = mainPreviewClip
                    ?.takeIf { it.type == com.apexstudio.app.domain.model.ClipType.VIDEO }
                val curveClip = timedClip
                    ?.takeIf { it.hasSpeedCurve() }
                // A cross-clip seek updates the project clock before the new
                // media item is bound. Never overwrite it with the old source.
                if (!transitionPreviewReady && timedClip != null &&
                    (player?.currentMediaItem?.mediaId != timedClip.id ||
                        (projectTransitionPlan.entryAt(vm.playerPositionFlow.value)?.clip
                            ?: timelinePreviewClip(state.project?.clips.orEmpty(), vm.playerPositionFlow.value))?.id != timedClip.id)) {
                    delay(33)
                    continue
                }
                val mainEnd = projectTransitionPlan.durationMs
                val inProjectTail = vm.playerPositionFlow.value >= mainEnd && totalDur > mainEnd
                val newPos = if (transitionPreviewReady && transitionPreviewPlayer != null && !inProjectTail) {
                    transitionPreviewPlayer!!.currentPosition.coerceAtLeast(0L)
                } else if (inProjectTail) {
                    // V1 is finished, but placed audio/overlays can keep the
                    // project alive. Do not read a frozen/ended source clock.
                    transitionPreviewPlayer?.pause()
                    if (player?.playWhenReady == true) {
                        player.pause()
                        timedClip?.let { player.seekTo((it.trimEndMs - 1L).coerceAtLeast(it.trimStartMs)) }
                    }
                    vm.playerPositionFlow.value + delta
                } else if (player != null && timedClip != null) {
                    val rawPos = player.currentPosition
                    if (curveClip != null) {
                        // Variable speed (PR-E): the player reports SOURCE
                        // time; the playhead shows OUTPUT (timeline) time.
                        // Also push the curve's instantaneous rate to
                        // ExoPlayer so the preview itself ramps.
                        val span = (curveClip.trimEndMs - curveClip.trimStartMs)
                            .coerceAtLeast(1L)
                        val srcInClip = (rawPos - curveClip.trimStartMs)
                            .coerceIn(0L, span)
                        val frac = srcInClip.toFloat() / span.toFloat()
                        val curveSpeed =
                            com.apexstudio.app.domain.model.SpeedCurveMath.speedAt(
                                curveClip.speedPoints, frac
                            )
                        if (lastCurveSpeedApplied == null ||
                            kotlin.math.abs(curveSpeed - lastCurveSpeedApplied!!) > 0.02f
                        ) {
                            try {
                                player.playbackParameters =
                                    androidx.media3.common.PlaybackParameters(curveSpeed)
                                lastCurveSpeedApplied = curveSpeed
                            } catch (_: Exception) {
                            }
                        }
                        val clips = state.project?.clips ?: emptyList()
                        projectTransitionPlan.projectTimeAtSourceTime(clips, curveClip, rawPos)
                            ?: com.apexstudio.app.domain.model.ClipTimeMapping.projectAtSourceTime(clips, curveClip, rawPos)
                    } else {
                        // Curve cleared mid-playback: hand the rate back
                        // to the constant-speed LaunchedEffect.
                        if (lastCurveSpeedApplied != null) {
                            try {
                                player.playbackParameters =
                                    androidx.media3.common.PlaybackParameters(
                                        com.apexstudio.app.domain.model.SpeedCurveMath.sanitizeConstantSpeed(
                                            timedClip?.speedMultiplier ?: state.playbackSpeed
                                        )
                                    )
                            } catch (_: Exception) {
                            }
                            lastCurveSpeedApplied = null
                        }
                        projectTransitionPlan.projectTimeAtSourceTime(
                            state.project?.clips ?: emptyList(), timedClip, rawPos
                        ) ?: com.apexstudio.app.domain.model.ClipTimeMapping.projectAtSourceTime(
                            state.project?.clips ?: emptyList(), timedClip, rawPos
                        )
                    }
                } else {
                    // Player-less playback (photo clips): extrapolate from
                    // the LIVE position, not the ~10Hz state mirror.
                    vm.playerPositionFlow.value + (delta * state.playbackSpeed).toLong()
                }

                if (newPos >= totalDur) {
                    vm.setPlayerPosition(totalDur, forceStateSync = true)
                    vm.setPlaying(false)
                    val tracks = state.project?.audioTracks ?: emptyList()
                    audioPlaybackManager.sync(false, totalDur, tracks)
                    lastAudioSyncAt = now
                    lastAudioSyncPos = totalDur
                } else {
                    val crossedBoundary = (projectTransitionPlan.entryAt(newPos)?.clip
                        ?: timelinePreviewClip(state.project?.clips.orEmpty(), newPos))?.id != mainPreviewClip?.id
                    vm.setPlayerPosition(newPos, forceStateSync = crossedBoundary)
                    // Drift-correction only: sync audio when 250ms elapsed
                    // or the position jumped (seek/loop), never every tick.
                    val tracks = state.project?.audioTracks ?: emptyList()
                    if (now - lastAudioSyncAt >= 250L || kotlin.math.abs(newPos - lastAudioSyncPos) > 500L) {
                        audioPlaybackManager.sync(true, newPos, tracks)
                        lastAudioSyncAt = now
                        lastAudioSyncPos = newPos
                    }
                }
            } else {
                lastTime = android.os.SystemClock.uptimeMillis()
            }
            delay(33)
        }
    }

    val seekPlayerAndState: (Long) -> Unit = remember(
        exoPlayer, transitionPreviewPlayer, transitionPreviewReady, state.durationMs, audioPlaybackManager,
        mainPreviewClip?.id, mainPreviewClip?.speedPoints,
        mainPreviewClip?.speedMultiplier,
        state.project?.clips, projectTransitionPlan
    ) {
        { targetMs ->
            val clamped = targetMs.coerceIn(0L, state.durationMs.coerceAtLeast(1L))
            val clips = state.project?.clips.orEmpty()
            val targetClip = projectTransitionPlan.entryAt(clamped)?.clip ?: timelinePreviewClip(clips, clamped)
            // Same-source seek is immediate; cross-source seeks are completed
            // by the binding effect using the exact requested project time.
            if (transitionPreviewReady) {
                transitionPreviewPlayer?.seekTo(clamped)
            } else if (targetClip?.type == ClipType.VIDEO && exoPlayer?.currentMediaItem?.mediaId == targetClip.id) {
                exoPlayer?.seekTo(
                    projectTransitionPlan.sourceTimeAt(clamped)
                        ?: com.apexstudio.app.domain.model.ClipTimeMapping.sourceAtProjectTime(clips, targetClip, clamped)
                )
            }
            audioPlaybackManager.seekTo(clamped)
            vm.seekTo(clamped)
        }
    }

    BackHandler(enabled = isTimelineFocusMode && !state.isFullscreenPreview) {
        isTimelineFocusMode = false
    }

    BackHandler(enabled = state.musicLibraryPanelOpen || state.sfxLibraryPanelOpen) {
        if (state.sfxLibraryPanelOpen) vm.closeSfxLibraryPanel() else vm.closeMusicLibraryPanel()
    }

    val openEffectsWorkspace: (Boolean) -> Unit = { openTimeActions ->
        val clip = selectedWorkspaceVideo(savedState.project, savedState.selectedClipId)
        if (clip != null) {
            vm.setPlaying(false)
            vm.selectClip(clip.id)
            workspaceTarget = clip
            workspacePreview = clip
            workspaceOpenTimeActions = openTimeActions
        } else {
            android.widget.Toast.makeText(context, "Select a main-track project video first", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(projectId, savedState.project?.clips, entryWorkspace, horizontalProjectLayout) {
        if (initialReferenceWorkspaceOpened || entryWorkspace != "edit") return@LaunchedEffect
        if (horizontalProjectLayout) {
            // Landscape projects open directly into the dedicated editing shell;
            // the legacy reference filter workspace would hide its two tool rows.
            initialReferenceWorkspaceOpened = true
            return@LaunchedEffect
        }
        val project = savedState.project ?: return@LaunchedEffect
        val clip = selectedWorkspaceVideo(project, savedState.selectedClipId)
            ?: project.clips.firstOrNull {
                it.type == ClipType.VIDEO && it.trackIndex == 0 && it.uri.isNotBlank() &&
                    it.trimStartMs >= 0L && it.trimEndMs > it.trimStartMs && it.trimEndMs <= it.durationMs
            }
        if (clip != null) {
            vm.selectClip(clip.id)
            workspaceTarget = clip
            workspacePreview = clip
            workspaceIsFilters = true
            workspaceOpenTimeActions = false
            initialReferenceWorkspaceOpened = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(ApexPalette.BgBase)
    ) {
        if (editorChromeVisible(state.isFullscreenPreview)) {
            TopAppBarSection(
                title = state.project?.name ?: "Untitled project",
                projectId = projectId,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                resolution = state.selectedResolution,
                onSelectResolution = { vm.setSelectedResolution(it) },
                isCoverMode = isCoverMode,
                onToggleCoverMode = {
                    val clip = framingVideo
                    if (clip != null) vm.setVideoFitMode(clip.id,
                        if (isCoverMode) VideoFitMode.FIT else VideoFitMode.COVER)
                    else nonVideoCoverMode = toggleCoverFitMode(nonVideoCoverMode)
                },
                onFullscreenToggle = { vm.toggleFullscreenPreview() },
                onBack = if (entryWorkspace == "edit") onBack else onCloseWorkspace,
                onUndo = { vm.undo() },
                onRedo = { vm.redo() },
                onHelp = { vm.openHelpDialog() },
                onExport = { if (isColorWorkspace) colorExportRequest++ else onExport() },
                exportEnabled = !isExportWorkspace,
                resolutionEnabled = !previewPlaybackSuspended
            )

        }

        if (editorChromeVisible(state.isFullscreenPreview) && workspaceTarget == null &&
            state.project?.hasLegacyAbsolutePlacementTimingRisk() == true
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color(0xFF382A12),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Legacy timing project: non-1x main clips now determine output duration. " +
                        "Overlay and music placements remain at their saved timeline positions and may no longer align with the main video. " +
                        "Review the timeline before export; nothing has been retimed.",
                    color = Color(0xFFFFD98A),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        if (isColorWorkspace) {
            colorSaveableState.SaveableStateProvider("color:$projectId") {
            com.apexstudio.app.ui.screens.colortools.ColorStudioScreen(
                projectId = projectId, onBack = onCloseWorkspace, onExport = onExport,
                embedded = true, modifier = Modifier.weight(1f), vm = vm,
                exportRequest = colorExportRequest
            )
            }
        } else if (isExportWorkspace) {
            EditorPausedSourcePreview(state.project?.clips.orEmpty(), mainPreviewClip, state.playerPositionMs)
        } else {
        AdaptiveEditorWorkspace(
            // PR169/170's preview-first stacked shell is the editor default;
            // adaptive landscape remains available to explicit workspace hosts.
            landscape = useLandscapeWorkspace,
            horizontalProjectMode = horizontalProjectLayout,
            focused = isTimelineFocusMode,
            fullscreen = state.isFullscreenPreview,
            compactTimelinePaneHeight = compactTimelinePaneHeight,
            manualTimelinePaneHeight = manualTimelinePaneHeight,
            onManualTimelinePaneHeightChange = { manualTimelinePaneHeightDp = it?.value },
            preview = {
        CompositionLocalProvider(LocalEditorPreviewEffects provides currentEffects) {
        EditorPreviewSection(
            state = state,
            vm = vm,
            exoPlayer = exoPlayer,
            previewPlayer = activePreviewPlayer,
            isTransitionPreview = transitionPreviewReady,
            transitionPreviewNotice = transitionPreviewNotice,
            isCoverMode = isCoverMode,
            isTimelineFocusMode = isTimelineFocusMode,
            isFullscreenPreview = state.isFullscreenPreview,
            onFullscreenToggle = { vm.toggleFullscreenPreview() },
            currentSelectedClip = currentSelectedClip,
            seekPlayerAndState = seekPlayerAndState,
            onOpenAddMediaMenu = {
                showAddMediaMenu = true
                activateLocalEditorPanel(EditorPanelKey.ADD_MEDIA_MENU)
            },
            overlayPlayer = overlayPlayer,
            overlayIsVideo = overlayIsVideo,
            mediaImportProgress = mediaImportProgress,
            onCancelMediaImport = mediaPicker::cancelImport,
            fitAvailableHeight = !horizontalProjectLayout,
            drawingEnabled = isDrawingMode,
            drawingTool = drawingTool,
            drawingColorArgb = drawingColorArgb,
            drawingBrushWidth = drawingBrushWidth
        )
        }
            },
            timeline = { timelinePaneResized ->
        if (editorChromeVisible(state.isFullscreenPreview) && !previewPlaybackSuspended) {
            EditorTransportSection(
                compactLandscape = false,
                state = state,
                vm = vm,
                seekPlayerAndState = seekPlayerAndState,
                isTimelineFocusMode = isTimelineFocusMode,
                onTimelineFocusModeChange = { isTimelineFocusMode = it },
                isFullscreenPreview = state.isFullscreenPreview,
                horizontalProjectLayout = horizontalProjectLayout,
                timelinePaneResized = timelinePaneResized,
                // V2 track "+" adds DIRECTLY as an overlay clip: the flag is
                // set here and the picker launches immediately, so the pick
                // can never be misrouted to V1 (the old bug: the ClipType was
                // dropped and the generic menu's "Video" entry reset the flag).
                onOpenAddMediaMenu = { type ->
                    if (type == ClipType.OVERLAY) {
                        vm.setPendingAddAsOverlay(true)
                        vm.setPendingAddAsAudio(false)
                        mediaPicker.pickMultipleMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    } else if (type == ClipType.AUDIO) {
                        // A1 lane "+" — audio goes straight to the audio
                        // picker and lands in project.audioTracks (A1 lane).
                        vm.setPendingAddAsOverlay(false)
                        vm.setPendingAddAsAudio(true)
                        importPhoneMusic()
                    } else {
                        vm.setPendingAddAsOverlay(false)
                        vm.setPendingAddAsAudio(false)
                        showAddMediaMenu = true
                        activateLocalEditorPanel(EditorPanelKey.ADD_MEDIA_MENU)
                    }
                }
            )
        }

            },
            inspector = { LandscapeVideoInspector(state, vm) }
        )
        }

        if (isExportWorkspace) {
            com.apexstudio.app.ui.screens.export.ExportScreen(
                projectId = projectId, onBack = onCloseWorkspace, onExport = onCloseWorkspace,
                vm = vm, embedded = true
            )
        }
        if (editorChromeVisible(state.isFullscreenPreview)) {
            // New contextual bottom toolbar — extracted to EditorBottomToolbarSection
            // to keep this composable under the JVM 64KB method limit.
            EditorBottomToolbarSection(
                state = state,
                vm = vm,
                mediaPicker = mediaPicker,
                horizontalProjectLayout = horizontalProjectLayout,
                onEffectsWorkspace = { workspaceIsFilters = false; openEffectsWorkspace(false) },
                onQuickEnhance = {
                    quickPresetSection = QuickVideoPresetSection.ENHANCE
                    activateLocalEditorPanel(EditorPanelKey.QUICK_PRESETS)
                },
                onQuickTemplates = {
                    quickPresetSection = QuickVideoPresetSection.TEMPLATES
                    activateLocalEditorPanel(EditorPanelKey.QUICK_PRESETS)
                },
                onQuickFilters = { workspaceIsFilters = true; openEffectsWorkspace(false) },
                onChallenge = {
                    showLocalChallenge = true
                    activateLocalEditorPanel(EditorPanelKey.LOCAL_CHALLENGE)
                },
                onNext = onExport,
                onColorWorkspace = onColor,
                referenceToolMode = workspaceTarget != null,
                referenceActiveTool = if (workspaceTarget != null) {
                    when {
                        workspaceIsFilters -> "Filters"
                        workspaceOpenTimeActions -> "Actions"
                        else -> "Effects"
                    }
                } else null,
                onReferenceTrim = {
                    val clip = mainPreviewClip ?: selectedWorkspaceVideo(state.project, state.selectedClipId)
                    if (clip != null) {
                        vm.selectClip(clip.id)
                        vm.openTrimPanel()
                    } else {
                        android.widget.Toast.makeText(context, "Select a main-track video before trimming", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onBeforeToolAction = {
                    activeEditorPanel = null
                    vm.keepOnlyEditorPanel(null)
                    if (workspaceTarget != null) dismissWorkspace()
                    if (entryWorkspace != "edit" && entryWorkspace != "ai") closeEntryWorkspace()
                },
                onAi = {
                    aiTargetVideo?.let { vm.selectClip(it.id) }
                    showAiPanel = true
                    activateLocalEditorPanel(EditorPanelKey.AI_TOOLS)
                },
                onDraw = {
                    if (mainPreviewClip != null) {
                        isDrawingMode = !isDrawingMode
                        showVideoDrawingTools = isDrawingMode
                        if (isDrawingMode) activateLocalEditorPanel(EditorPanelKey.VIDEO_DRAWING)
                    } else {
                        // Before: this did nothing silently when no clip was selected,
                        // which is exactly the "button doesn't work" users reported.
                        android.widget.Toast.makeText(
                            context,
                            "Draw: select a video clip on the timeline first",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onToggleKeyframe = {
                    val clip = selectedKeyframeVideo(state)
                    if (clip != null) {
                        vm.toggleKeyframeAtPlayheadFor(clip.id)
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Keyframe: select a visible video, photo, or overlay clip and place the playhead within it",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onOverlay = {
                    if ((state.project?.clips?.any {
                        it.type == ClipType.VIDEO || it.type == ClipType.IMAGE
                    } == true)) {
                        vm.setPendingAddAsOverlay(true)
                        vm.setPendingAddAsAudio(false)
                        prepareMainMediaPickerLaunch(vm)
                        mediaPicker.pickMultipleMedia.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts
                                    .PickVisualMedia.ImageAndVideo
                            )
                        )
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Overlay: add a video/image first, then add overlay on top",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onCrop = {
                    showCropPanel = true
                    activateLocalEditorPanel(EditorPanelKey.CROP)
                },
                onDeviceAudio = importPhoneMusic,
                onSignature = { vm.openSignaturePanel() }
            )
            if (localAudioImporting) {
                androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Importing phone music…", color = ApexPalette.TextSecondary, modifier = Modifier.padding(6.dp))
            }
        }
        workspaceTarget?.let { captured ->
            ClipEffectWorkspaceHost(
                filters = workspaceIsFilters,
                projectId = projectId,
                originalClip = captured,
                vm = vm,
                isPlaying = savedState.isPlaying,
                onPreviewClip = { workspacePreview = it },
                onSeekSource = { sourceMs ->
                    val clip = workspacePreview ?: captured
                    exoPlayer?.seekTo(sourceMs)
                    val outputMs = com.apexstudio.app.domain.model.ClipTimeMapping.projectAtSourceTime(
                        state.project?.clips.orEmpty(), clip, sourceMs
                    )
                    vm.setPlayerPosition(outputMs, forceStateSync = true)
                },
                onTogglePlayback = { vm.togglePlay() },
                onDismiss = dismissWorkspace,
                onReopenFilters = {
                    val restarted = restartFilterWorkspace(projectId, vm.state.value, dismissWorkspace) { clip ->
                        workspaceIsFilters = true
                        workspaceTarget = clip
                        workspacePreview = clip
                    }
                    if (!restarted) {
                        android.widget.Toast.makeText(context, "Select a main-track project video first", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                openTimeActions = workspaceOpenTimeActions
            )
        }
    }

    val prepareAiVideoTarget: () -> Boolean = {
        val targetId = aiTargetVideo?.id
        val current = vm.state.value
        val currentProject = current.project
        val currentClip = currentProject?.clips?.firstOrNull { it.id == targetId }
        val validClip = currentClip?.takeIf {
            isCurrentProjectAiVideoTarget(projectId, currentProject?.id, it)
        }
        if (validClip != null) {
            vm.selectClip(validClip.id)
            true
        } else {
            android.widget.Toast.makeText(context,
                "Select a main-track video in this project first.", android.widget.Toast.LENGTH_SHORT).show()
            false
        }
    }
    val aiReportTarget = remember(projectId, state.project, aiTargetVideo?.id, state.selectedTextOverlayId) {
        resolveAiVideoReportTarget(projectId, state.project, aiTargetVideo, state.selectedTextOverlayId)
    }
    if (showLocalChallenge && activeEditorPanel == EditorPanelKey.LOCAL_CHALLENGE) {
        LocalChallengeOverlay(vm, onClose = { showLocalChallenge = false })
    }
    if (activeEditorPanel == EditorPanelKey.QUICK_PRESETS) quickPresetSection?.let { section ->
        QuickVideoPresetsOverlay(vm, section, onClose = { quickPresetSection = null })
    }
    if (showAiPanel && activeEditorPanel == EditorPanelKey.AI_TOOLS) AiToolsOverlay(
        onClose = closeAiPanel,
        targetVideoName = aiTargetVideo?.name?.takeIf { it.isNotBlank() } ?: aiTargetVideo?.id,
        onOpenLocalAnalysis = {
            if (prepareAiVideoTarget()) {
                closeAiPanel()
                showLocalAnalysis = true
                activateLocalEditorPanel(EditorPanelKey.LOCAL_ANALYSIS)
            }
        },
        onOpenVideoUpscale = {
            if (prepareAiVideoTarget()) {
                closeAiPanel()
                showVideoUpscale = true
                activateLocalEditorPanel(EditorPanelKey.VIDEO_UPSCALE)
            }
        },
        onOpenBackgroundReplace = {
            if (prepareAiVideoTarget()) {
                closeAiPanel()
                vm.openBackgroundPanel()
            }
        },
        onOpenCaptions = {
            if (prepareAiVideoTarget()) {
                closeAiPanel()
                vm.openCaptionsPanel()
            }
        },
        reportTarget = aiReportTarget, reportState = aiContentReportState,
        onSubmitReport = { target, reason ->
            val currentProject = vm.state.value.project
            if (isAiReportTargetCurrent(projectId, currentProject, target)) {
                vm.submitAiContentReport(target, reason)
                true
            } else false
        },
        isReportTargetCurrent = { target ->
            isAiReportTargetCurrent(projectId, vm.state.value.project, target)
        },
        onClearReportFeedback = vm::clearAiContentReportFeedback)
    if (showVideoPresets && activeEditorPanel == EditorPanelKey.VIDEO_PRESETS) {
        ProjectVideoPresetsOverlay(vm, onClose = { showVideoPresets = false },
            onLearnedUpscale = { _, _ ->
                showVideoPresets = false
                showVideoUpscale = true
                activateLocalEditorPanel(EditorPanelKey.VIDEO_UPSCALE)
            })
    }
    if (showVideoUpscale && activeEditorPanel == EditorPanelKey.VIDEO_UPSCALE) {
        VideoUpscaleOverlay(vm, onClose = { showVideoUpscale = false })
    }
    if (showLocalAnalysis && activeEditorPanel == EditorPanelKey.LOCAL_ANALYSIS) LocalAnalysisOverlay(vm = vm, onClose = { showLocalAnalysis = false },
        onPreviewSourceTime = { analyzedClipId, sourceMs ->
            val clips = vm.state.value.project?.clips.orEmpty()
            val analyzedClip = clips.firstOrNull { it.id == analyzedClipId }
            if (analyzedClip != null) {
                val projectMs = com.apexstudio.app.domain.model.ClipTimeMapping
                    .projectAtSourceTime(clips, analyzedClip, sourceMs)
                seekPlayerAndState(projectMs)
            }
        })
    if (showCropPanel && activeEditorPanel == EditorPanelKey.CROP) {
        Box(Modifier.fillMaxSize().clickable { showCropPanel = false }, contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                CropToolsOverlay(state, vm, onClose = { showCropPanel = false })
            }
        }
    }

    // Modal overlays — each extracted to its own composable (see
    // EditorScreenOverlays.kt) to keep EditorScreen under the JVM 64KB
    // method limit. Pure extraction, behavior unchanged.
    if (activeEditorPanel == EditorPanelKey.TRIM) {
        TrimPanelOverlay(state = state, vm = vm, exoPlayer = exoPlayer, onExport = onExport)
    }
    if (activeEditorPanel == EditorPanelKey.VIDEO_DRAWING) VideoDrawingToolsOverlay(
        isOpen = showVideoDrawingTools,
        tool = drawingTool,
        colorArgb = drawingColorArgb,
        brushWidth = drawingBrushWidth,
        strokeCount = state.project?.videoDrawings.orEmpty()
            .filter { it.clipId == mainPreviewClip?.id }.sumOf { it.strokes.size },
        onToolChange = { drawingTool = it },
        onColorChange = { drawingColorArgb = it },
        onBrushWidthChange = { drawingBrushWidth = it },
        onUndo = { vm.undoVideoDrawingStroke(mainPreviewClip?.id) },
        onClear = { vm.clearVideoDrawings(mainPreviewClip?.id) },
        onClose = { showVideoDrawingTools = false }
    )
    if (activeEditorPanel == EditorPanelKey.GPU_FILTER) {
        GpuFilterPanelOverlay(state = state, vm = vm, filterEngine = filterEngine)
    }
    if (activeEditorPanel == EditorPanelKey.COLOR_GRADING) {
        ColorGradingPanelOverlay(state = state, vm = vm, filterEngine = filterEngine)
    }
    if (activeEditorPanel == EditorPanelKey.ADJUSTMENTS) AdjustmentsPanelOverlay(state = state, vm = vm)

    // Photo-editing sheet (PR F): only for the selected IMAGE clip.
    val photoEditClip = currentSelectedClip?.takeIf { it.type == ClipType.IMAGE }
    if (activeEditorPanel == EditorPanelKey.PHOTO_EDIT && state.photoEditPanelOpen && photoEditClip != null) {
        PhotoEditSheet(
            photoEditClip = photoEditClip,
            tab = state.photoEditTab,
            vm = vm
        )
    }

    if (activeEditorPanel == EditorPanelKey.FX) FxPanelOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.TRANSMISSION) {
        TransmissionTemplatesOverlay(state = state, vm = vm, templates = transmissionTemplates)
    }
    if (activeEditorPanel == EditorPanelKey.TRANSITION) TransitionPickerOverlay(state = state, vm = vm)
        if (activeEditorPanel == EditorPanelKey.TEXT) TextPanelOverlay(
            state = state,
            vm = vm,
            initialTab = if (entryWorkspace == "text-styles") TextPanelTab.PRESETS else TextPanelTab.EDIT,
            onCloseWorkspace = { if (entryWorkspace == "text-styles") onCloseWorkspace() }
        )
    if (activeEditorPanel == EditorPanelKey.AUDIO_MIXER) AudioMixerPanelOverlay(
        state = state,
        audioState = audioState,
        vm = vm,
        onImportLocalAudio = importPhoneMusic,
        onOpenRoyaltyFreeSheet = {
            showRoyaltyFreeSheet = true
            activateLocalEditorPanel(EditorPanelKey.ROYALTY_MUSIC_SHEET)
        },
        // Phase 4: live visualizer needs the player audio session.
        audioSessionId = exoPlayer?.audioSessionId ?: 0
    )

    // Volume + Fade sheets — extracted to overlay composables to keep this
    // composable under the JVM 64KB method limit.
    if (activeEditorPanel == EditorPanelKey.CLIP_VOLUME) ClipVolumeSheetOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.AUDIO_FADE) AudioFadeSheetOverlay(state = state, vm = vm)

    if (activeEditorPanel == EditorPanelKey.ROYALTY_MUSIC_SHEET ||
        activeEditorPanel == EditorPanelKey.ROYALTY_MUSIC_DIALOG) {
        RoyaltyMusicSheetOverlay(
            showRoyaltyFreeSheet = showRoyaltyFreeSheet && activeEditorPanel == EditorPanelKey.ROYALTY_MUSIC_SHEET,
            onCloseRoyaltyFreeSheet = { showRoyaltyFreeSheet = false },
            state = state,
            vm = vm,
            onImportLocalAudio = importPhoneMusic
        )
    }
    if (activeEditorPanel == EditorPanelKey.SPEED) SpeedPanelOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.STICKER) StickerPanelOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.SIGNATURE) SignaturePanelOverlay(state = state, vm = vm, mediaPicker = mediaPicker)
    if (activeEditorPanel == EditorPanelKey.VOICE_RECORDER) VoiceRecorderOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.AR_FILTER) ArFilterPanelOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.COVER) CoverPanelOverlay(state = state, vm = vm, mediaPicker = mediaPicker)
    if (activeEditorPanel == EditorPanelKey.CHROMA_KEY) ChromaKeyPanelOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.MASK) MaskPanelOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.TEXT_MASK) TextMaskPanelOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.OVERLAY_PROPERTIES) OverlayPropertiesPanelOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.HELP) HelpDialogOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.KEYFRAME) KeyframePanelOverlay(state = state, vm = vm)
    if (showAddMediaMenu && activeEditorPanel == EditorPanelKey.ADD_MEDIA_MENU) AddMediaMenuSheetOverlay(
        show = showAddMediaMenu,
        vm = vm,
        mediaPicker = mediaPicker,
        onImportLocalAudio = importPhoneMusic,
        onDismiss = { showAddMediaMenu = false }
    )

    // ---- Phase 3 overlays ----
    if (activeEditorPanel == EditorPanelKey.EDIT_PACK) EditPackPanelOverlay(state = state, vm = vm, onOpenTimeActions = {
        vm.closeEditPackPanel()
        activeEditorPanel = null
        workspaceIsFilters = false
        openEffectsWorkspace(true)
    })
    if (activeEditorPanel == EditorPanelKey.ADJUST_PACK) AdjustPackPanelOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.COLOR_SCOPES) ColorScopesPanelOverlay(state = state, vm = vm)
    if (activeEditorPanel == EditorPanelKey.BG_REMOVER) BgRemoverPanelOverlay(state = state, vm = vm)
    // PR-G: per-clip Background replace (MediaPipe segmentation).
    if (activeEditorPanel == EditorPanelKey.BACKGROUND_REPLACE) BackgroundReplacePanelOverlay(
        state = state,
        vm = vm,
        onPickBackgroundImage = {
            bgReplaceImagePickerLauncher.launch("image/*")
        }
    )
    if (activeEditorPanel == EditorPanelKey.AUTO_CLIP) AutoClipPanelOverlay(
        state = state,
        vm = vm,
        onOpenMusicForMood = { mood ->
            musicMoodFilter = mood
            vm.openMusicLibraryPanel()
        }
    )
    if (activeEditorPanel == EditorPanelKey.MUSIC_LIBRARY) MusicLibraryPanelOverlay(
        state = state,
        vm = vm,
        initialMood = musicMoodFilter,
        onAddTrack = { title, filePath, durationMs ->
            vm.addAudioTrack(
                name = title,
                uri = filePath,
                kind = AudioTrack.Kind.MUSIC,
                sourceDurationMs = durationMs
            )
            musicMoodFilter = null
        }
    )
    if (activeEditorPanel == EditorPanelKey.SFX_LIBRARY) SfxLibraryPanelOverlay(
        state = state,
        vm = vm,
        onAddEffect = { effect, filePath, durationMs, intensity ->
            vm.addAudioTrack(
                name = effect.displayName,
                uri = filePath,
                kind = AudioTrack.Kind.SFX,
                sourceDurationMs = durationMs,
                sfxEffectId = effect.id,
                sfxIntensity = intensity
            )
        }
    )
    if (activeEditorPanel == EditorPanelKey.HISTORY_DRAFTS) HistoryDraftsPanelOverlay(
        state = state,
        vm = vm,
        projects = savedProjects
    )
}


/**
 * Photo-editing bottom sheet (PR F). Extracted from [EditorScreen] as a
 * top-level composable to keep EditorScreen under the 64KB JVM method limit.
 */
@Composable
private fun PhotoEditSheet(
    photoEditClip: com.apexstudio.app.domain.model.MediaClip,
    tab: com.apexstudio.app.presentation.state.PhotoEditTab,
    vm: com.apexstudio.app.presentation.viewmodel.EditorViewModel
) {
    val photoAiState by vm.photoAiEnhanceUi.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Pro Phase 1: locked preview stays visible
            .clickable { vm.closePhotoEditPanel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
        ) {
            PhotoEditPanel(
                photoUri = photoEditClip.uri,
                settings = photoEditClip.photoEdit,
                tab = tab,
                onTabChange = { vm.setPhotoEditTab(it) },
                onCropAspectPreset = { vm.setPhotoCropAspectPreset(photoEditClip.id, it) },
                onAdjust = { transform -> vm.updatePhotoAdjustments(photoEditClip.id, transform) },
                onResetAdjust = { vm.resetPhotoAdjustments(photoEditClip.id) },
                onFilterSelect = { vm.setPhotoFilter(photoEditClip.id, it) },
                onFilterIntensity = { vm.setPhotoFilterIntensity(photoEditClip.id, it) },
                onRotate90 = { vm.rotatePhotoClockwise(photoEditClip.id) },
                onFlipH = { vm.togglePhotoFlipHorizontal(photoEditClip.id) },
                onFlipV = { vm.togglePhotoFlipVertical(photoEditClip.id) },
                onKenBurns = { vm.setPhotoKenBurns(photoEditClip.id, it) },
                onCanvasChange = { canvas -> vm.setPhotoCanvas(photoEditClip.id, canvas) },
                onAutoEnhance = { vm.applyPhotoAutoEnhance(photoEditClip.id) },
                onDenoise = { vm.applyPhotoDenoise(photoEditClip.id) },
                onSharpen = { vm.applyPhotoSharpen(photoEditClip.id) },
                onPhotoUpscale = { vm.applyPhotoAiUpscale(photoEditClip.id) },
                onFaceCrop = { vm.applyPhotoFaceCrop(photoEditClip.id) },
                onStabilize = { enabled -> vm.setPhotoStabilize(photoEditClip.id, enabled) },
                photoAiState = photoAiState,
                onCancelPhotoAi = { vm.cancelPhotoAiEnhance() },
                onResetAll = { vm.resetPhotoEdits(photoEditClip.id) },
                onClose = { vm.closePhotoEditPanel() }
            )
        }
    }
}
