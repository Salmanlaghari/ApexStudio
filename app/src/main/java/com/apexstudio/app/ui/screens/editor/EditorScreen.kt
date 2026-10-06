package com.apexstudio.app.ui.screens.editor

import android.net.Uri
import android.util.Log
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.media.MediaUriResolver
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onColor: () -> Unit,
    onAudio: () -> Unit,
    vm: EditorViewModel = viewModel(
        key = projectId,
        factory = EditorViewModelFactory(projectId = projectId)
    )
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val audioState by vm.audio.collectAsStateWithLifecycle()
    val transmissionTemplates by vm.transmissionTemplates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    CrashMarker.mark(context, "EditorScreen: composable start")
    val mediaPicker = remember { MediaPickerHelper(context) }
    val filterEngine = remember { LutFilterEngine(context) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var showAddMediaMenu by remember { mutableStateOf(false) }
    var showRoyaltyFreeSheet by remember { mutableStateOf(false) }
    var isCoverMode by remember { mutableStateOf(true) }

    val audioPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment?.substringAfterLast('/') ?: "Imported Audio"
            vm.addAudioTrack(name = name, uri = it.toString(), kind = AudioTrack.Kind.MUSIC)
        }
    }

    mediaPicker.registerLaunchers()

    // Load the persisted editor layout choice (New contextual vs Classic backup).
    LaunchedEffect(Unit) {
        vm.loadEditorLayoutPref()
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.combine(
            mediaPicker.pickedMedia,
            mediaPicker.pickGeneration
        ) { meta, gen -> meta to gen }
            .collect { (metadataList, _) ->
                if (metadataList.isNotEmpty()) {
                    val s = vm.state.value
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

    LaunchedEffect(Unit) {
        try {
            val player = ExoPlayer.Builder(context).build()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val ready = playbackState == Player.STATE_READY
                    vm.setPlayerReady(ready)
                    vm.setBuffering(playbackState == Player.STATE_BUFFERING)
                    if (playbackState == Player.STATE_ENDED) {
                        vm.setPlaying(false)
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    val errorMsg = error.localizedMessage ?: error.errorCodeName
                    Log.e("EditorScreen", "ExoPlayer playback warning: $errorMsg", error)
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

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    val currentSelectedClip = remember(state.project?.clips, state.selectedClipId) {
        val cid = state.selectedClipId
        if (cid != null) state.project?.clips?.firstOrNull { it.id == cid }
        else state.project?.clips?.firstOrNull()
    }
    val currentClipUri = currentSelectedClip?.uri

    LaunchedEffect(exoPlayer, state.selectedClipId, currentClipUri) {
        val player = exoPlayer ?: return@LaunchedEffect
        val clip = currentSelectedClip ?: return@LaunchedEffect
        val playableUri = MediaUriResolver.resolvePlayableUri(context, clip.uri)
        val mediaItem = MediaItem.fromUri(playableUri)
        val currentUri = player.currentMediaItem?.localConfiguration?.uri
        if (player.currentMediaItem?.mediaId != mediaItem.mediaId && currentUri != playableUri) {
            try {
                vm.setPlayerReady(false)
                player.setMediaItem(mediaItem)
                player.prepare()
            } catch (e: Exception) {
                Log.e("EditorScreen", "player.prepare() failed", e)
            }
            vm.setPlayerDuration(clip.durationMs)
        }
    }

    LaunchedEffect(exoPlayer, state.playbackSpeed) {
        val player = exoPlayer ?: return@LaunchedEffect
        try {
            val speed = state.playbackSpeed.coerceIn(0.1f, 10.0f)
            player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
        } catch (e: Exception) {
            Log.e("EditorScreen", "Failed to set playback speed", e)
        }
    }

    // Per-clip Volume tool: live preview gain follows the selected clip's volume.
    LaunchedEffect(exoPlayer, currentSelectedClip?.id, currentSelectedClip?.volume) {
        val player = exoPlayer ?: return@LaunchedEffect
        try {
            player.volume = (currentSelectedClip?.volume ?: 1f).coerceIn(0f, 2f)
        } catch (e: Exception) {
            Log.e("EditorScreen", "Failed to set clip volume", e)
        }
    }

    val activePreset: com.apexstudio.app.data.filter.FilterPreset? = remember(state.activeFilterId, state.customImportedLuts) {
        state.activeFilterId?.let { id ->
            filterEngine.manifest.filters.firstOrNull { it.id == id }
                ?: state.customImportedLuts.firstOrNull { it.id == id }
        }
    }
    val activeFx: com.apexstudio.app.data.fx.FxPreset? = remember(state.activeFxId) {
        state.activeFxId?.let { id -> com.apexstudio.app.data.fx.FxPreset.byId(id) }
    }
    val selectedKeyframes = currentSelectedClip?.keyframes

    val currentEffects = remember(
        activePreset,
        state.filterIntensity,
        activeFx,
        state.fxIntensity,
        state.adjustments,
        selectedKeyframes
    ) {
        buildList<androidx.media3.common.Effect> {
            if (activePreset != null && state.filterIntensity > 0f) {
                add(
                    com.apexstudio.app.data.filter.LutFilterGlEffect(
                        context, activePreset, state.filterIntensity,
                        intensityProvider = { state.filterIntensity }
                    )
                )
            }
            if (!state.adjustments.isDefault) {
                val adjustMatrix = com.apexstudio.app.data.filter.FilterColorMatrix.getCombinedMatrix(
                    filterId = null,
                    intensity = 0f,
                    adjustments = state.adjustments
                )
                add(com.apexstudio.app.data.filter.ColorMatrixGlEffect(adjustMatrix))
            }
            if (activeFx != null && state.fxIntensity > 0f) {
                add(
                    com.apexstudio.app.data.fx.FxGlEffect(
                        activeFx, state.fxIntensity
                    )
                )
            }
            if (selectedKeyframes != null && !selectedKeyframes.isEmpty()) {
                val trackRef = arrayOf(selectedKeyframes)
                add(
                    com.apexstudio.app.data.animation.KeyframeAnimationEffect(
                        trackProvider = { trackRef[0] }
                    ).buildEffects().first()
                )
            }
        }
    }

    // Live preview effects and filters are rendered in real-time by FilterPreviewOverlay
    // and FxPreviewOverlay with Compose hardware acceleration, preventing ExoPlayer
    // GL pipeline crashes and surface detachment on Android devices.

    val audioPlaybackManager = remember { com.apexstudio.app.data.engine.AudioPlaybackManager(context) }
    DisposableEffect(Unit) {
        onDispose {
            audioPlaybackManager.release()
        }
    }

    LaunchedEffect(exoPlayer, state.isPlaying, state.project?.audioTracks) {
        val tracks = state.project?.audioTracks ?: emptyList()
        audioPlaybackManager.sync(state.isPlaying, state.playerPositionMs, tracks)
        val player = exoPlayer ?: return@LaunchedEffect
        if (state.isPlaying) {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        } else {
            player.pause()
        }
    }

    LaunchedEffect(state.isPlaying, state.durationMs, state.playbackSpeed) {
        val totalDur = state.durationMs.coerceAtLeast(1000L)
        var lastTime = android.os.SystemClock.uptimeMillis()
        while (isActive) {
            if (state.isPlaying) {
                val now = android.os.SystemClock.uptimeMillis()
                val delta = now - lastTime
                lastTime = now

                val player = exoPlayer
                val newPos = if (player != null && player.isPlaying) {
                    player.currentPosition
                } else {
                    state.playerPositionMs + (delta * state.playbackSpeed).toLong()
                }

                if (newPos >= totalDur) {
                    // Seamless loop back to 0 for continuous professional timeline playback
                    vm.setPlayerPosition(0L)
                    exoPlayer?.seekTo(0L)
                    val tracks = state.project?.audioTracks ?: emptyList()
                    audioPlaybackManager.sync(true, 0L, tracks)
                } else {
                    vm.setPlayerPosition(newPos)
                    val tracks = state.project?.audioTracks ?: emptyList()
                    audioPlaybackManager.sync(true, newPos, tracks)
                }
            } else {
                lastTime = android.os.SystemClock.uptimeMillis()
            }
            delay(33)
        }
    }

    val seekPlayerAndState: (Long) -> Unit = remember(exoPlayer, state.durationMs, audioPlaybackManager) {
        { targetMs ->
            val clamped = targetMs.coerceIn(0L, state.durationMs.coerceAtLeast(1L))
            exoPlayer?.seekTo(clamped)
            audioPlaybackManager.seekTo(clamped)
            vm.seekTo(clamped)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(Color(0xFF0A0A0F))
    ) {
        TopAppBarSection(
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            resolution = state.selectedResolution,
            onSelectResolution = { vm.setSelectedResolution(it) },
            isCoverMode = isCoverMode,
            onToggleCoverMode = { isCoverMode = !isCoverMode },
            onFullscreenToggle = { vm.toggleFullscreenPreview() },
            onBack = onBack,
            onUndo = { vm.undo() },
            onRedo = { vm.redo() },
            onHelp = { vm.openHelpDialog() },
            onExport = onExport
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            val selectedClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
                ?: state.project?.clips?.firstOrNull()
            val animatedTransform = remember(selectedClip, state.playerPositionMs) {
                selectedClip?.keyframes?.interpolateAt(state.playerPositionMs)
                    ?: com.apexstudio.app.domain.model.AnimatedTransform.Identity
            }
            val overlayClip = state.project?.clips?.firstOrNull { it.type == ClipType.OVERLAY }
            val stickers = (state.project?.stickers ?: emptyList()) + (selectedClip?.stickers ?: emptyList())
            val textOverlays = selectedClip?.textOverlays ?: emptyList()
            var showUpcomingPreview by remember { mutableStateOf(false) }

            // Auto-hide screen controls: hidden by default, visible for 2s on video screen tap
            var screenControlsVisible by remember { mutableStateOf(false) }
            var hideControlsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

            fun triggerScreenControls() {
                screenControlsVisible = true
                showUpcomingPreview = true
                hideControlsJob?.cancel()
                hideControlsJob = scope.launch {
                    kotlinx.coroutines.delay(2000L)
                    screenControlsVisible = false
                    showUpcomingPreview = false
                }
            }

            VideoPreviewArea(
                exoPlayer = exoPlayer,
                chromaKeySettings = state.chromaKeySettings,
                overlayClip = overlayClip,
                isCoverMode = isCoverMode,
                adjustments = state.adjustments,
                activeFilterId = state.activeFilterId,
                filterIntensity = state.filterIntensity,
                gpuFilterConfig = state.activeGpuFilterConfig,
                gpuFilterCompareMode = state.gpuFilterCompareMode,
                gpuFilterSplitPosition = state.gpuFilterSplitPosition,
                lutCompareMode = state.lutCompareMode,
                lutCompareSplitPosition = state.lutCompareSplitPosition,
                activeArFilterId = state.activeArFilterId,
                arFilterIntensity = state.arFilterIntensity,
                arFilterCustomText = state.arFilterCustomText,
                playerError = state.playerError,
                activeFxId = state.activeFxId,
                fxIntensity = state.fxIntensity,
                isPlaying = state.isPlaying,
                animatedTransform = animatedTransform,
                onTapVideo = {
                    triggerScreenControls()
                },
                onRetryLoad = {
                    exoPlayer?.let { player ->
                        vm.setPlayerError(null)
                        try {
                            val fallbackUri = MediaUriResolver.resolvePlayableUri(context, null)
                            player.setMediaItem(MediaItem.fromUri(fallbackUri))
                            player.prepare()
                            player.play()
                        } catch (e: Exception) {
                            vm.setPlayerError("Error reloading video: ${e.message}")
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            val controlsAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (screenControlsVisible) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(250),
                label = "controlsAlpha"
            )

            // Auto-hiding Floating Left Quick Tool Rail (AR Face, Effects, Filters, 3D Chroma, Adjust, Text)
            if (controlsAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 6.dp)
                        .graphicsLayer { alpha = controlsAlpha }
                ) {
                    LeftToolRail(
                        onArFilters = { vm.openArFilterPanel() },
                        onEffects = { vm.openFxPanel() },
                        onFilters = { vm.openFilterPanel() },
                        onAdjust = { vm.openAdjustmentsPanel() },
                        onChromaKey = { vm.openChromaKeyPanel() },
                        onText = { vm.openTextPanel() },
                        onSticker = { vm.openStickerPanel() }
                    )
                }
            }

            // Auto-hiding Floating Right Quick Tool Rail (Add Media, Audio Mixer, Voice Record, Camera)
            if (controlsAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
                        .graphicsLayer { alpha = controlsAlpha }
                ) {
                    RightToolRail(
                        onAdd = { showAddMediaMenu = true },
                        onAudio = { vm.openAudioMixer() },
                        onRecord = { vm.openVoiceRecorder() },
                        onCamera = { vm.openCameraCapture() }
                    )
                }
            }

            // Screen Controls Auto-hide Indicator Badge
            if (controlsAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .graphicsLayer { alpha = controlsAlpha }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "⚡ Tap screen to show controls • Auto-hiding in 2s",
                        color = ApexPalette.NeonCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Screen-Level Wording & Object Overlays (Positioned relative to Screen, NOT clipped by video view)
            ScreenWordingObjects(
                stickers = stickers,
                textOverlays = textOverlays,
                coverText = state.coverText,
                coverTextStyle = state.coverTextStyle,
                selectedTextOverlayId = state.selectedTextOverlayId,
                currentTimeMs = state.currentTimeMs,
                isPlaying = state.isPlaying,
                onSelectTextOverlay = { id -> vm.selectTextOverlay(id) },
                onMoveTextOverlay = { id, dx, dy ->
                    val clipId = selectedClip?.id ?: state.project?.clips?.firstOrNull()?.id
                    if (clipId != null) {
                        vm.updateTextOverlay(clipId, id) { it.copy(x = it.x + dx, y = it.y + dy) }
                    }
                },
                onDeleteTextOverlay = { id ->
                    selectedClip?.let { vm.removeTextOverlay(it.id, id) }
                },
                onDuplicateTextOverlay = { id ->
                    selectedClip?.let { vm.duplicateTextOverlay(it.id, id) }
                },
                onEditTextOverlay = { id ->
                    vm.selectTextOverlay(id)
                    vm.openTextPanel()
                },
                onSizeScaleChange = { id, scale ->
                    selectedClip?.let { vm.setTextOverlaySize(it.id, id, scale) }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Upcoming frames preview overlay triggered on screen tap
            UpcomingFramesPreviewOverlay(
                visible = showUpcomingPreview,
                videoUri = selectedClip?.uri,
                currentTimeMs = state.playerPositionMs,
                durationMs = state.durationMs,
                onSeekTo = { seekPlayerAndState(it) },
                onPlayPreviewSnippet = {
                    scope.launch {
                        if (!state.isPlaying) vm.togglePlay()
                        delay(3000L)
                        if (state.isPlaying) vm.togglePlay()
                    }
                },
                onDismiss = { showUpcomingPreview = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }

        // CapCut transport row: the keyframe diamond appears next to the
        // undo/redo arrows only while a clip is explicitly selected.
        val kfClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
        val hasKeyframeAtPlayhead =
            kfClip?.keyframes?.keyframes?.any { kotlin.math.abs(it.timeMs - state.playerPositionMs) <= 150L } == true

        PlaybackControlBar(
            currentTimeMs = state.playerPositionMs,
            totalDurationMs = state.durationMs,
            isPlaying = state.isPlaying,
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            showKeyframeButton = !state.useClassicEditorLayout && kfClip != null,
            hasKeyframeAtPlayhead = hasKeyframeAtPlayhead,
            onTogglePlay = { vm.togglePlay() },
            onPrev = {
                val clips = state.project?.clips ?: emptyList()
                val current = state.playerPositionMs
                val prevClip = clips.lastOrNull { it.trimStartMs < current - 500L }
                seekPlayerAndState(prevClip?.trimStartMs ?: 0L)
            },
            onNext = {
                val clips = state.project?.clips ?: emptyList()
                val current = state.playerPositionMs
                val nextClip = clips.firstOrNull { it.trimStartMs > current + 500L }
                seekPlayerAndState(nextClip?.trimStartMs ?: state.durationMs)
            },
            onUndo = { vm.undo() },
            onRedo = { vm.redo() },
            onToggleKeyframe = {
                kfClip?.let { vm.toggleKeyframeAtPlayheadFor(it.id) }
            },
            onFullscreenToggle = { vm.toggleFullscreenPreview() }
        )

        // Elevate all 14 Adjust features to the top right below the video player preview
        TopAdjustBar(
            adjustments = state.adjustments,
            onOpenAdjustPanel = { vm.openAdjustmentsPanel() },
            onUpdate = { vm.updateAdjustments(it) },
            onResetAll = { vm.resetAllAdjustments() },
            modifier = Modifier.fillMaxWidth()
        )

        val activeClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId } ?: state.project?.clips?.firstOrNull()
        val clipList = state.project?.clips ?: emptyList()
        val currentClipIdx = clipList.indexOfFirst { it.id == activeClip?.id }

        TimelineQuickActionBar(
            hasKeyframeAtPlayhead = hasKeyframeAtPlayhead,
            timelineZoom = state.timelineZoom,
            canSplit = activeClip != null,
            canMoveLeft = currentClipIdx > 0,
            canMoveRight = currentClipIdx in 0 until (clipList.size - 1),
            onToggleKeyframe = { vm.toggleKeyframeAtPlayhead() },
            onPrevKeyframe = { vm.jumpToPrevKeyframe() },
            onNextKeyframe = { vm.jumpToNextKeyframe() },
            onSplit = {
                activeClip?.let { vm.splitClip(it.id, state.playerPositionMs) }
            },
            onMoveLeft = {
                activeClip?.let { vm.moveClipLeft(it.id) }
            },
            onMoveRight = {
                activeClip?.let { vm.moveClipRight(it.id) }
            },
            onZoomIn = { vm.zoomInTimeline() },
            onZoomOut = { vm.zoomOutTimeline() },
            onOpenKeyframes = { vm.setKeyframePanelOpen(true) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )

        TimelineTrackArea(
            state = state,
            onScrub = { targetMs ->
                val snapped = vm.findMagneticSnapPoint(targetMs)
                seekPlayerAndState(snapped)
            },
            onToggleSnapToBeat = { vm.toggleSnapToBeat() },
            onToggleMagneticSnapping = { vm.toggleMagneticSnapping() },
            onToggleRippleEdit = { vm.toggleRippleEdit() },
            onSelectClip = { vm.selectClip(it) },
            onSelectFx = { fxId -> vm.selectFx(fxId, intensity = 0.85f) },
            onSelectFilter = { filterId -> vm.selectFilter(filterId, intensity = 0.85f) },
            onAddMedia = { showAddMediaMenu = true },
            onSplitClip = { clipId, atMs -> vm.splitClip(clipId, atMs) },
            onDuplicateClip = { clipId -> vm.duplicateClip(clipId) },
            onDeleteClip = { clipId -> vm.deleteClip(clipId) },
            onDeleteClips = { ids -> vm.deleteClips(ids) },
            onMoveClipLeft = { clipId -> vm.moveClipLeft(clipId) },
            onMoveClipRight = { clipId -> vm.moveClipRight(clipId) },
            onReorderClips = { fromIndex, toIndex -> vm.reorderClips(fromIndex, toIndex) },
            onShiftClipOffset = { clipId, deltaMs -> vm.shiftClipTimelineOffset(clipId, deltaMs) },
            onSetClipOffset = { clipId, offsetMs -> vm.setClipTimelineOffset(clipId, offsetMs) },
            onTrimClip = { clipId, startMs, endMs -> vm.trimClip(clipId, startMs, endMs) },
            onToggleKeyframeAtPlayhead = { clipId -> vm.toggleKeyframeAtPlayheadFor(clipId) },
            onMoveKeyframe = { clipId, kfId, newTimeMs -> vm.moveKeyframe(clipId, kfId, newTimeMs) },
            onToggleTextKeyframeAtPlayhead = { clipId, overlayId ->
                vm.toggleTextKeyframeAtPlayhead(clipId, overlayId)
            },
            onMoveTextKeyframe = { clipId, overlayId, kfId, newTimeMs ->
                vm.moveTextKeyframe(clipId, overlayId, kfId, newTimeMs)
            },
            onZoomIn = { vm.zoomInTimeline() },
            onZoomOut = { vm.zoomOutTimeline() },
            onResetZoom = { vm.setTimelineZoom(1.0f) },
            onSetZoom = { zoom -> vm.setTimelineZoom(zoom) },
            onOpenSpeed = { vm.openSpeedPanel() },
            onOpenAudio = { vm.openAudioMixer() },
            onOpenTrim = { vm.openTrimPanel() },
            onOpenText = { vm.openTextPanel() },
            onOpenStickers = { vm.openStickerPanel() },
            onOpenFx = { vm.openFxPanel() },
            onOpenVoice = { vm.openVoiceRecorder() },
            onOpenAnimation = { vm.setKeyframePanelOpen(true) },
            onOpenTransition = {
                val clips = state.project?.clips ?: emptyList()
                if (clips.size >= 2) {
                    val selIndex = clips.indexOfFirst { it.id == state.selectedClipId }
                    val fromIdx = if (selIndex in 0 until clips.size - 1) selIndex else 0
                    vm.openTransitionPicker(clips[fromIdx].id, clips[fromIdx + 1].id)
                } else {
                    vm.openTransmissionTemplatesPanel()
                }
            },
            onOpenClipTransition = { fromId, toId ->
                vm.openTransitionPicker(fromId, toId)
            },
            onOpenChromaKey = { vm.openChromaKeyPanel() },
            onOpenArFilters = { vm.openArFilterPanel() },
            onOpenRoyaltyMusic = { vm.openRoyaltyMusicDialog() },
            onSelectAudioTrack = { vm.selectAudioTrack(it) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Editor layout: New contextual toolbar (default) vs Classic legacy
        // backup — extracted to EditorBottomToolbarSection to keep this
        // composable under the JVM 64KB method limit.
        EditorBottomToolbarSection(
            state = state,
            vm = vm,
            mediaPicker = mediaPicker
        )
    }

    // Modal Overlays
    if (state.trimPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { vm.closeTrimPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                val clipToTrim = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
                    ?: state.project?.clips?.firstOrNull()
                TrimPanel(
                    clip = clipToTrim,
                    currentPlayheadMs = state.playerPositionMs,
                    isTransformerTrimming = state.isTransformerTrimming,
                    transformerTrimProgress = state.transformerTrimProgress,
                    transformerTrimMessage = state.transformerTrimMessage,
                    transformerTrimError = state.transformerTrimError,
                    onTrimChange = { start, end ->
                        clipToTrim?.let {
                            vm.trimClip(it.id, start, end)
                            exoPlayer?.seekTo(start)
                        }
                    },
                    onSetStartAtPlayhead = {
                        clipToTrim?.let {
                            vm.setTrimStartAtPlayhead(it.id)
                            exoPlayer?.seekTo(state.playerPositionMs)
                        }
                    },
                    onSetEndAtPlayhead = {
                        clipToTrim?.let {
                            vm.setTrimEndAtPlayhead(it.id)
                            exoPlayer?.seekTo(state.playerPositionMs)
                        }
                    },
                    onResetTrim = { clipToTrim?.let { vm.resetTrim(it.id) } },
                    onPreviewTrimmed = {
                        clipToTrim?.let {
                            exoPlayer?.seekTo(it.trimStartMs)
                            if (!state.isPlaying) vm.togglePlay()
                        }
                    },
                    onTrimWithTransformer = { clipId, startMs, endMs, replaceInTimeline ->
                        vm.trimClipWithMedia3Transformer(clipId, startMs, endMs, replaceInTimeline) { _ ->
                            exoPlayer?.seekTo(0L)
                        }
                    },
                    onCancelTransformerTrim = { vm.cancelMedia3Trim() },
                    onClearTransformerStatus = { vm.clearTransformerTrimStatus() },
                    onExport = onExport,
                    onClose = { vm.closeTrimPanel() }
                )
            }
        }
    }

    if (state.gpuFilterPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { vm.closeGpuFilterPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                val activeClip = state.selectedClipId?.let { id ->
                    state.project?.clips?.firstOrNull { it.id == id }
                } ?: state.project?.clips?.firstOrNull()

                GpuVideoFilterPanel(
                    selectedClip = activeClip,
                    config = state.activeGpuFilterConfig,
                    manifest = filterEngine.manifest,
                    selectedTab = state.gpuFilterSelectedTab,
                    compareMode = state.gpuFilterCompareMode,
                    splitPosition = state.gpuFilterSplitPosition,
                    lutThumbnails = state.filterThumbnails,
                    onTabSelected = { vm.setGpuFilterSelectedTab(it) },
                    onConfigChange = { vm.setGpuFilterConfig(it) },
                    onToggleCompare = { vm.toggleGpuFilterCompareMode() },
                    onSplitPositionChange = { vm.setGpuFilterSplitPosition(it) },
                    onApplyToClip = { vm.applyGpuFilterToSelectedClip() },
                    onApplyToAllClips = { vm.applyGpuFilterToAllClips() },
                    onReset = { vm.resetGpuFilter() },
                    onClose = { vm.closeGpuFilterPanel() }
                )
            }
        }
    }

    if (state.filterPanelOpen || state.colorGradingLutPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable {
                    vm.closeFilterPanel()
                    vm.closeColorGradingLutPanel()
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                ColorGradingLutPanel(
                    manifest = filterEngine.manifest,
                    activeFilterId = state.activeFilterId,
                    intensity = state.filterIntensity,
                    targetTrack = state.lutTargetTrack,
                    compareMode = state.lutCompareMode,
                    splitPosition = state.lutCompareSplitPosition,
                    favoriteIds = state.lutFavoriteIds,
                    customLuts = state.customImportedLuts,
                    contrast = state.lutContrast,
                    saturation = state.lutSaturation,
                    temperatureK = state.lutTemperature,
                    tint = state.lutTint,
                    thumbnails = state.filterThumbnails,
                    galleryViewMode = state.lutGalleryViewMode,
                    isLoadingThumbnails = state.filterThumbnailsLoading,
                    onGalleryViewModeChange = { vm.setLutGalleryViewMode(it) },
                    onRefreshThumbnails = { vm.refreshFilterThumbnailsFromVideo(force = true) },
                    onSelectFilter = { vm.setActiveFilter(it) },
                    onIntensityChange = { vm.setFilterIntensity(it) },
                    onTargetTrackChange = { vm.setLutTargetTrack(it) },
                    onToggleFavorite = { vm.toggleLutFavorite(it) },
                    onToggleCompare = { vm.toggleLutCompareMode() },
                    onSplitPositionChange = { vm.setLutCompareSplitPosition(it) },
                    onContrastChange = { vm.setLutContrast(it) },
                    onSaturationChange = { vm.setLutSaturation(it) },
                    onTemperatureChange = { vm.setLutTemperature(it) },
                    onTintChange = { vm.setLutTint(it) },
                    onReset = { vm.resetLutGrading() },
                    onClose = {
                        vm.closeFilterPanel()
                        vm.closeColorGradingLutPanel()
                    }
                )
            }
        }
    }

    if (state.adjustmentsPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { vm.closeAdjustmentsPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
            ) {
                AdjustPanel(
                    adjustments = state.adjustments,
                    onUpdate = { vm.updateAdjustments(it) },
                    onReset = { vm.resetAdjustments() },
                    onResetAll = { vm.resetAllAdjustments() },
                    onClose = { vm.closeAdjustmentsPanel() }
                )
            }
        }
    }

    if (state.fxPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeFxPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                FxPanel(
                    activeFxId = state.activeFxId,
                    intensity = state.fxIntensity,
                    onFxSelected = { vm.setActiveFx(it) },
                    onIntensityChange = { vm.setFxIntensity(it) },
                    onKeyframesClick = {
                        vm.setKeyframePanelOpen(true)
                        vm.closeFxPanel()
                    },
                    onClose = { vm.closeFxPanel() }
                )
            }
        }
    }

    if (state.transmissionPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeTransmissionTemplatesPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                TransmissionTemplatesPanel(
                    templates = transmissionTemplates,
                    activeTemplateId = state.project?.lastTransmissionTemplateId,
                    onTemplateApplied = { vm.applyTransmissionTemplate(it) },
                    onClose = { vm.closeTransmissionTemplatesPanel() }
                )
            }
        }
    }

    if (state.transitionPickerOpen) {
        val fromClip = state.project?.clips?.firstOrNull { it.id == state.transitionPickerFromClipId }
        val toClip = state.project?.clips?.firstOrNull { it.id == state.transitionPickerToClipId }
        val currentTransition = state.project?.transitions?.firstOrNull {
            it.fromClipId == state.transitionPickerFromClipId && it.toClipId == state.transitionPickerToClipId
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { vm.closeTransitionPicker() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                TransitionPickerSheet(
                    fromClip = fromClip,
                    toClip = toClip,
                    currentTransition = currentTransition,
                    onApplyTransition = { type, durMs ->
                        val fId = state.transitionPickerFromClipId
                        val tId = state.transitionPickerToClipId
                        if (fId != null && tId != null) {
                            vm.applyTransition(fId, tId, type, durMs)
                        }
                    },
                    onApplyToAll = { type, durMs ->
                        vm.applyTransitionToAll(type, durMs)
                    },
                    onRemoveTransition = {
                        val fId = state.transitionPickerFromClipId
                        val tId = state.transitionPickerToClipId
                        if (fId != null && tId != null) {
                            vm.removeTransition(fId, tId)
                        }
                    },
                    onClose = { vm.closeTransitionPicker() }
                )
            }
        }
    }

    if (state.textPanelOpen) {
        val textClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
            ?: state.project?.clips?.firstOrNull()
        val textOverlays = textClip?.textOverlays ?: emptyList()
        val activeOverlayId = state.selectedTextOverlayId
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeTextPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                TextPanel(
                    overlays = textOverlays,
                    selectedId = activeOverlayId,
                    onAdd = { textClip?.let { vm.addTextOverlay(it.id) } },
                    onSelect = { vm.selectTextOverlay(it) },
                    onTextChange = { text ->
                        if (textClip != null && activeOverlayId != null) {
                            vm.setTextOverlayText(textClip.id, activeOverlayId, text)
                        }
                    },
                    onColorChange = { argb ->
                        if (textClip != null && activeOverlayId != null) {
                            vm.setTextOverlayColor(textClip.id, activeOverlayId, argb)
                        }
                    },
                    onBgChange = { argb ->
                        if (textClip != null && activeOverlayId != null) {
                            vm.setTextOverlayBg(textClip.id, activeOverlayId, argb)
                        }
                    },
                    onSizeChange = { scale ->
                        if (textClip != null && activeOverlayId != null) {
                            vm.setTextOverlaySize(textClip.id, activeOverlayId, scale)
                        }
                    },
                    onFontFamilyChange = { font ->
                        if (textClip != null && activeOverlayId != null) {
                            vm.setTextOverlayFont(textClip.id, activeOverlayId, font)
                        }
                    },
                    onStyleChange = { bold, italic ->
                        if (textClip != null && activeOverlayId != null) {
                            vm.setTextOverlayStyle(textClip.id, activeOverlayId, bold, italic)
                        }
                    },
                    onShadowChange = { shadow ->
                        if (textClip != null && activeOverlayId != null) {
                            vm.setTextOverlayShadow(textClip.id, activeOverlayId, shadow)
                        }
                    },
                    onAnimDurationChange = { dur ->
                        if (textClip != null && activeOverlayId != null) {
                            vm.setTextOverlayAnimDuration(textClip.id, activeOverlayId, dur)
                        }
                    },
                    onDuplicate = { overlayId ->
                        textClip?.let { vm.duplicateTextOverlay(it.id, overlayId) }
                    },
                    onDelete = { overlayId ->
                        textClip?.let { vm.removeTextOverlay(it.id, overlayId) }
                    },
                    onApplyPreset = { preset ->
                        if (textClip != null && activeOverlayId != null) {
                            vm.applyTextPreset(textClip.id, activeOverlayId, preset)
                        }
                    },
                    onAnimationChange = { anim ->
                        if (textClip != null && activeOverlayId != null) {
                            vm.setTextOverlayAnimation(textClip.id, activeOverlayId, anim)
                        }
                    },
                    onClose = { vm.closeTextPanel() }
                )
            }
        }
    }

    if (state.audioMixerOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeAudioMixer() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                AudioMixerPanel(
                    state = audioState,
                    muteOriginalVideo = audioState.isMuted,
                    onMuteOriginal = { vm.setMuteOriginalVideo(it) },
                    onAddTrack = { name, uri, kind -> vm.addAudioTrack(name, uri, kind) },
                    onImportLocalAudio = {
                        audioPickerLauncher.launch("audio/*")
                    },
                    onOpenRoyaltyFreeMusic = {
                        showRoyaltyFreeSheet = true
                    },
                    onRemoveTrack = { vm.removeAudioTrack(it) },
                    onVolume = { trackId, vol -> vm.setAudioTrackVolume(trackId, vol) },
                    onMute = { trackId -> vm.toggleAudioTrackMute(trackId) },
                    onSolo = { trackId -> vm.toggleAudioTrackSolo(trackId) },
                    onTrim = { trackId, start, end -> vm.setAudioTrackTrim(trackId, start, end) },
                    onFadeIn = { trackId, ms -> vm.setAudioTrackFadeIn(trackId, ms) },
                    onFadeOut = { trackId, ms -> vm.setAudioTrackFadeOut(trackId, ms) },
                    onClose = { vm.closeAudioMixer() }
                )
            }
        }
    }

    // Volume + Fade sheets — extracted to overlay composables to keep this
    // composable under the JVM 64KB method limit.
    ClipVolumeSheetOverlay(state = state, vm = vm)
    AudioFadeSheetOverlay(state = state, vm = vm)


    if (showRoyaltyFreeSheet) {
        RoyaltyFreeMusicSheet(
            onClose = { showRoyaltyFreeSheet = false },
            onSelectSong = { title, filePath, durationMs ->
                vm.addAudioTrack(
                    name = title,
                    uri = filePath,
                    kind = AudioTrack.Kind.MUSIC,
                    sourceDurationMs = durationMs
                )
                showRoyaltyFreeSheet = false
            }
        )
    }

    if (state.speedPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeSpeedPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                val currentClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId } ?: state.project?.clips?.firstOrNull()
                SpeedRampPanel(
                    selectedClipId = state.selectedClipId,
                    currentSpeed = state.playbackSpeed,
                    activeClipSpeed = currentClip?.speedMultiplier ?: state.playbackSpeed,
                    opticalFlowEnabled = state.opticalFlowMotionBlur,
                    opticalFlowIntensity = state.opticalFlowBlurIntensity,
                    onSelectPreset = { preset ->
                        vm.setPlaybackSpeed(preset.multiplier)
                        state.selectedClipId?.let { cid -> vm.setClipSpeed(cid, preset.multiplier) }
                    },
                    onCustomSpeed = { speed ->
                        vm.setPlaybackSpeed(speed)
                        state.selectedClipId?.let { cid -> vm.setClipSpeed(cid, speed) }
                    },
                    onToggleOpticalFlow = { enabled ->
                        vm.setOpticalFlowMotionBlur(enabled)
                    },
                    onChangeOpticalFlowIntensity = { intensity ->
                        vm.setOpticalFlowMotionBlur(state.opticalFlowMotionBlur, intensity)
                    },
                    onClose = { vm.closeSpeedPanel() }
                )
            }
        }
    }

    if (state.stickerPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeStickerPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                StickerPanel(
                    onAddSticker = { symbol, cat, name -> vm.addStickerOverlay(symbol, cat, name) },
                    onClose = { vm.closeStickerPanel() }
                )
            }
        }
    }

    if (state.voiceRecorderOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { vm.closeVoiceRecorder() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                VoiceRecorderPanel(
                    onAddRecording = { uri, dur, name -> vm.addVoiceOverTrack(uri, dur, name) },
                    onClose = { vm.closeVoiceRecorder() }
                )
            }
        }
    }

    if (state.cameraCaptureOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { vm.closeCameraCapture() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                CameraCapturePanel(
                    onCapturePicked = { meta -> vm.onMediaPicked(meta) },
                    selectedArFilterId = state.activeArFilterId,
                    onSelectArFilter = { vm.selectArFilter(it) },
                    onClose = { vm.closeCameraCapture() }
                )
            }
        }
    }

    if (state.arFilterPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { vm.closeArFilterPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                ArFilterPanel(
                    activeFilterId = state.activeArFilterId,
                    intensity = state.arFilterIntensity,
                    customText = state.arFilterCustomText,
                    onFilterSelected = { vm.selectArFilter(it) },
                    onIntensityChange = { vm.setArFilterIntensity(it) },
                    onCustomTextChange = { vm.setArFilterCustomText(it) },
                    onClose = { vm.closeArFilterPanel() }
                )
            }
        }
    }

    if (state.coverPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { vm.closeCoverPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                CoverPanel(
                    currentPlayheadMs = state.playerPositionMs,
                    coverFrameMs = state.project?.coverFrameMs,
                    customCoverUri = state.project?.coverCustomUri,
                    onSelectFrameAtPlayhead = { vm.setCoverFrame(it) },
                    onSelectCustomCover = {
                        mediaPicker.pickSingleMedia.launch("image/*")
                        vm.closeCoverPanel()
                    },
                    onClose = { vm.closeCoverPanel() }
                )
            }
        }
    }

    if (state.chromaKeyPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { vm.closeChromaKeyPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                ChromaKeyPanel(
                    settings = state.chromaKeySettings,
                    onUpdate = { vm.updateChromaKeySettings(it) },
                    onPickCustomBg = {
                        mediaPicker.pickSingleMedia.launch("image/*")
                    },
                    onClose = { vm.closeChromaKeyPanel() }
                )
            }
        }
    }

    if (state.royaltyMusicDialogOpen) {
        RoyaltyFreeMusicSheet(
            onClose = { vm.closeRoyaltyMusicDialog() },
            onSelectSong = { title, filePath, durationMs ->
                vm.addRoyaltyTrack(title, filePath, durationMs)
                vm.closeRoyaltyMusicDialog()
            },
            // Device import: reuse the existing MediaStore audio picker; the
            // picked file lands on the audio track via audioPickerLauncher.
            onImportAudio = {
                vm.closeRoyaltyMusicDialog()
                audioPickerLauncher.launch("audio/*")
            }
        )
    }

    if (state.helpDialogOpen) {
        HelpDialog(onDismiss = { vm.closeHelpDialog() })
    }

    if (state.keyframePanelOpen) {
        val selectedClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
            ?: state.project?.clips?.firstOrNull()
        val track = selectedClip?.keyframes ?: com.apexstudio.app.domain.model.KeyframeTrack()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { vm.setKeyframePanelOpen(false) },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                KeyframePanel(
                    track = track,
                    playheadMs = state.playerPositionMs,
                    clipDurationMs = selectedClip?.durationMs ?: state.durationMs,
                    canAdd = selectedClip != null,
                    onAdd = { atMs ->
                        selectedClip?.let {
                            vm.addKeyframe(it.id, atMs)
                        }
                    },
                    onUpdate = { kf ->
                        selectedClip?.let {
                            vm.updateKeyframe(it.id, kf.id) { _ -> kf }
                        }
                    },
                    onRemove = { kfId ->
                        selectedClip?.let {
                            vm.removeKeyframe(it.id, kfId)
                        }
                    },
                    onClear = {
                        selectedClip?.let {
                            vm.clearKeyframes(it.id)
                        }
                    },
                    onApplyPreset = { preset ->
                        vm.applyAnimationPreset(preset)
                    },
                    onClose = { vm.setKeyframePanelOpen(false) }
                )
            }
        }
    }

    if (showAddMediaMenu) {
        AddMediaMenuSheet(
            onPickVideo = {
                vm.setPendingAddAsOverlay(false)
                vm.setPendingAddAsAudio(false)
                mediaPicker.pickMultipleMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onPickOverlay = {
                vm.setPendingAddAsOverlay(true)
                vm.setPendingAddAsAudio(false)
                mediaPicker.pickMultipleMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onPickAudio = {
                vm.setPendingAddAsOverlay(false)
                vm.setPendingAddAsAudio(true)
                mediaPicker.pickAudioMedia.launch("audio/*")
            },
            onDismiss = { showAddMediaMenu = false }
        )
    }
}

