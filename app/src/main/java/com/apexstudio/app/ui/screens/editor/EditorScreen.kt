package com.apexstudio.app.ui.screens.editor

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.media.MediaUriResolver
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

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
    val transmissionTemplates by vm.transmissionTemplates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CrashMarker.mark(context, "EditorScreen: composable start")
    val mediaPicker = remember { MediaPickerHelper(context) }
    val filterEngine = remember { LutFilterEngine(context) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var showAddMediaMenu by remember { mutableStateOf(false) }

    // Floating rails auto-hide state: hidden by default, tap video preview to reveal for 2s
    var railsVisible by remember { mutableStateOf(false) }
    var lastRailInteraction by remember { mutableLongStateOf(0L) }

    LaunchedEffect(railsVisible, lastRailInteraction) {
        if (railsVisible) {
            delay(2000)
            railsVisible = false
        }
    }

    mediaPicker.registerLaunchers()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.combine(
            mediaPicker.pickedMedia,
            mediaPicker.pickGeneration
        ) { meta, gen -> meta to gen }
            .collect { (metadataList, _) ->
                if (metadataList.isNotEmpty()) {
                    vm.onMediaPicked(metadataList, replace = false)
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
                    vm.setPlayerReady(false)
                    val errorMsg = error.localizedMessage ?: error.errorCodeName
                    vm.setPlayerError("Video error: $errorMsg")
                    try {
                        val fallbackUri = MediaUriResolver.resolvePlayableUri(context, null)
                        player.setMediaItem(MediaItem.fromUri(fallbackUri))
                        player.prepare()
                        player.play()
                        vm.setPlayerReady(true)
                        vm.setPlayerError(null)
                    } catch (ex: Exception) {
                        vm.setPlayerError("Failed to load video ($errorMsg)")
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

    LaunchedEffect(exoPlayer, state.selectedClipId, state.project?.clips) {
        val player = exoPlayer ?: return@LaunchedEffect
        val clipId = state.selectedClipId ?: state.project?.clips?.firstOrNull()?.id ?: return@LaunchedEffect
        val clip = state.project?.clips?.firstOrNull { it.id == clipId } ?: return@LaunchedEffect
        val playableUri = MediaUriResolver.resolvePlayableUri(context, clip.uri)
        val mediaItem = MediaItem.fromUri(playableUri)
        if (player.currentMediaItem?.mediaId != mediaItem.mediaId) {
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

    val activePreset: com.apexstudio.app.data.filter.FilterPreset? = remember(state.activeFilterId) {
        state.activeFilterId?.let { id -> filterEngine.manifest.filters.firstOrNull { it.id == id } }
    }
    val activeFx: com.apexstudio.app.data.fx.FxPreset? = remember(state.activeFxId) {
        state.activeFxId?.let { id -> com.apexstudio.app.data.fx.FxPreset.byId(id) }
    }
    val currentSelectedClip = remember(state.project?.clips, state.selectedClipId) {
        val cid = state.selectedClipId
        if (cid != null) state.project?.clips?.firstOrNull { it.id == cid }
        else state.project?.clips?.firstOrNull()
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

    LaunchedEffect(exoPlayer, currentEffects) {
        val player = exoPlayer ?: return@LaunchedEffect
        try {
            player.setVideoEffects(currentEffects)
        } catch (e: Exception) {
            Log.e("EditorScreen", "player.setVideoEffects failed", e)
        }
        if (!player.isPlaying && player.playbackState == Player.STATE_READY) {
            try {
                player.seekTo(player.currentPosition.coerceAtLeast(0L))
            } catch (e: Exception) {
                Log.w("EditorScreen", "seekTo re-render failed", e)
            }
        }
    }

    LaunchedEffect(exoPlayer, state.isPlaying) {
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

    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (isActive) {
            if (player.isPlaying) {
                val pos = player.currentPosition
                vm.setPlayerPosition(pos)
            }
            delay(33)
        }
    }

    val seekPlayerAndState: (Long) -> Unit = remember(exoPlayer, state.durationMs) {
        { targetMs ->
            val clamped = targetMs.coerceIn(0L, state.durationMs.coerceAtLeast(1L))
            exoPlayer?.seekTo(clamped)
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
                .clickable {
                    railsVisible = true
                    lastRailInteraction = System.currentTimeMillis()
                }
        ) {
            val selectedClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
            val stickers = (state.project?.stickers ?: emptyList()) + (selectedClip?.stickers ?: emptyList())

            VideoPreviewArea(
                exoPlayer = exoPlayer,
                resolution = state.selectedResolution,
                activeFilterId = state.activeFilterId,
                filterIntensity = state.filterIntensity,
                adjustments = state.adjustments,
                playerError = state.playerError,
                stickers = stickers,
                activeFxId = state.activeFxId,
                fxIntensity = state.fxIntensity,
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
                onSelectResolution = { vm.setSelectedResolution(it) },
                onFullscreenToggle = { vm.toggleFullscreenPreview() },
                modifier = Modifier.fillMaxSize()
            )

            if (railsVisible) {
                LeftToolRail(
                    onEffects = { vm.openFxPanel() },
                    onFilters = { vm.openFilterPanel() },
                    onAdjust = { vm.openAdjustmentsPanel() },
                    onText = { vm.openTextPanel() },
                    onSticker = { vm.openStickerPanel() },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                )

                RightToolRail(
                    onAdd = { showAddMediaMenu = true },
                    onAudio = onAudio,
                    onRecord = { vm.openVoiceRecorder() },
                    onCamera = { vm.openCameraCapture() },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                )
            }
        }

        PlaybackControlBar(
            currentTimeMs = state.playerPositionMs,
            totalDurationMs = state.durationMs,
            isPlaying = state.isPlaying,
            onTogglePlay = { vm.togglePlay() },
            onPrev = { seekPlayerAndState((state.playerPositionMs - 5000L).coerceAtLeast(0L)) },
            onNext = { seekPlayerAndState((state.playerPositionMs + 5000L).coerceAtMost(state.durationMs)) },
            onFullscreenToggle = { vm.toggleFullscreenPreview() }
        )

        TimelineRuler(
            currentTimeMs = state.playerPositionMs,
            totalDurationMs = state.durationMs,
            onScrub = { seekPlayerAndState(it) }
        )

        TimelineTrackArea(
            state = state,
            onScrub = { seekPlayerAndState(it) },
            onSelectClip = { vm.selectClip(it) },
            onCover = { vm.openCoverPanel() },
            onAddMedia = { showAddMediaMenu = true },
            modifier = Modifier
                .fillMaxWidth()
                // Reference image shows all 4 layer rows (purple
                // text, blue fx, green Dreamscape waveform, purple
                // Voice Over waveform) fully visible. 135dp clipped
                // the bottom one to half-height. Bumped to 200dp so
                // the Cover row + 4 layer rows all fit comfortably.
                .height(200.dp)
        )

        BottomEditToolbar(
            onEdit = { vm.openTrimPanel() },
            onAudio = onAudio,
            onText = { vm.openTextPanel() },
            onEffects = { vm.openFxPanel() },
            onOverlay = { showAddMediaMenu = true },
            onTransition = { vm.openTransmissionTemplatesPanel() },
            onFilters = { vm.openFilterPanel() }
        )

        BottomNavBar(
            onMedia = { vm.openMediaLibrary() },
            onElements = { vm.openStickerPanel() },
            onTools = { /* Open tools panel */ },
            onSettings = { /* Open settings */ }
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
                    onExport = onExport,
                    onClose = { vm.closeTrimPanel() }
                )
            }
        }
    }

    if (state.filterPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable { vm.closeFilterPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                FilterPanel(
                    manifest = filterEngine.manifest,
                    activeFilterId = state.activeFilterId,
                    intensity = state.filterIntensity,
                    activeCategory = state.filterCategory,
                    thumbnails = state.filterThumbnails,
                    onCategoryChange = { vm.setFilterCategory(it) },
                    onFilterSelected = { vm.setActiveFilter(it) },
                    onIntensityChange = { vm.setFilterIntensity(it) },
                    onClose = { vm.closeFilterPanel() }
                )
            }
        }
    }

    if (state.adjustmentsPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable { vm.closeAdjustmentsPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
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

    if (state.textPanelOpen) {
        val textClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
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
                    onDelete = { overlayId ->
                        textClip?.let { vm.removeTextOverlay(it.id, overlayId) }
                    },
                    onApplyPreset = { preset ->
                        if (textClip != null && activeOverlayId != null) {
                            vm.applyTextPreset(textClip.id, activeOverlayId, preset)
                        }
                    },
                    onClose = { vm.closeTextPanel() }
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
                    onClose = { vm.closeCameraCapture() }
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

    if (state.helpDialogOpen) {
        HelpDialog(onDismiss = { vm.closeHelpDialog() })
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
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
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

// === 1. TOP APP BAR ===
@Composable
fun TopAppBarSection(
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onBack: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onHelp: () -> Unit = {},
    onExport: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Hamburger menu + Two-line title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onBack)
            )

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Apex",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Studio",
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = "Pro Video Editor",
                    color = Color(0xFF9CA3AF),
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Right side icons + Export button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) Color.White else Color(0xFF6B7280),
                modifier = Modifier
                    .size(18.dp)
                    .clickable(enabled = canUndo, onClick = onUndo)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) Color.White else Color(0xFF4B5563),
                modifier = Modifier
                    .size(18.dp)
                    .clickable(enabled = canRedo, onClick = onRedo)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = "Help",
                tint = Color.White,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onHelp)
            )

            // Compact Gradient Export Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                        )
                    )
                    .clickable(onClick = onExport)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Export",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Export",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

// === 2. VIDEO PREVIEW AREA ===
@Composable
fun VideoPreviewArea(
    exoPlayer: ExoPlayer? = null,
    resolution: String = "1080P",
    activeFilterId: String? = null,
    filterIntensity: Float = 0f,
    adjustments: com.apexstudio.app.domain.model.VideoAdjustments = com.apexstudio.app.domain.model.VideoAdjustments(),
    playerError: String? = null,
    stickers: List<StickerOverlay> = emptyList(),
    activeFxId: String? = null,
    fxIntensity: Float = 0f,
    onRetryLoad: (() -> Unit)? = null,
    onSelectResolution: (String) -> Unit = {},
    onFullscreenToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showResolutionDropdown by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF12121A))
            .border(1.dp, Color(0xFF1F1F2E), RoundedCornerShape(16.dp))
            // Phase Live Filter: renderEffect on the OUTER Box instead
            // of inside the AndroidView's graphicsLayer. The AndroidView
            // modifier chain is rebuilt only when AndroidView itself
            // composes; param changes (activeFilterId, filterIntensity,
            // adjustments) don't reliably re-trigger the inner
            // graphicsLayer's lambda because Compose caches modifiers
            // between recompositions. Putting the graphicsLayer on the
            // outer Box guarantees re-evaluation whenever VideoPreviewArea
            // recomposes with new params.
            .graphicsLayer {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val hasFilter = activeFilterId != null && filterIntensity > 0f
                    val hasAdjust = !adjustments.isDefault
                    val hasFx = activeFxId != null && fxIntensity > 0f
                    if (hasFilter || hasAdjust || hasFx) {
                        val cm = com.apexstudio.app.data.filter.FilterColorMatrix
                            .getCombinedMatrix(
                                filterId = activeFilterId,
                                intensity = filterIntensity,
                                adjustments = adjustments,
                                fxId = activeFxId,
                                fxIntensity = fxIntensity
                            )
                        val filter = android.graphics.ColorMatrixColorFilter(cm)
                        renderEffect = android.graphics.RenderEffect
                            .createColorFilterEffect(filter)
                            .asComposeRenderEffect()
                    } else {
                        renderEffect = null
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    val pv = android.view.LayoutInflater.from(ctx)
                        .inflate(com.apexstudio.app.R.layout.view_player, null) as androidx.media3.ui.PlayerView
                    pv.apply {
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = exoPlayer
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder video frame thumbnail render
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
                    )
                )
            }
        }

        // Draggable & Resizable Interactive Stickers
        if (stickers.isNotEmpty()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val containerW = maxWidth
                val containerH = maxHeight

                for (sticker in stickers) {
                    var offsetX by remember(sticker.id) { mutableStateOf(sticker.x) }
                    var offsetY by remember(sticker.id) { mutableStateOf(sticker.y) }
                    var scale by remember(sticker.id) { mutableStateOf(sticker.sizeScale) }

                    Box(
                        modifier = Modifier
                            .offset(
                                x = containerW * offsetX - 20.dp,
                                y = containerH * offsetY - 20.dp
                            )
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                rotationZ = sticker.rotationDeg
                                alpha = sticker.opacity
                            }
                            .pointerInput(sticker.id) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.3f, 4f)
                                    val newX = (offsetX + pan.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val newY = (offsetY + pan.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    offsetX = newX
                                    offsetY = newY
                                }
                            }
                    ) {
                        Text(
                            sticker.symbolOrUri,
                            fontSize = 32.sp
                        )
                    }
                }
            }
        }

        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Video Error",
                        tint = ApexPalette.NeonPink,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Video Load Error",
                        color = ApexPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = playerError,
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (onRetryLoad != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ApexPalette.NeonCyan.copy(alpha = 0.2f))
                                .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(8.dp))
                                .clickable { onRetryLoad() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Reload Sample Video",
                                color = ApexPalette.NeonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Top-Left Pill: "1080P" Dropdown
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showResolutionDropdown = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = resolution,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            androidx.compose.material3.DropdownMenu(
                expanded = showResolutionDropdown,
                onDismissRequest = { showResolutionDropdown = false },
                modifier = Modifier.background(ApexPalette.BgElevated)
            ) {
                listOf("720P", "1080P", "1440P", "4K").forEach { res ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                text = res,
                                color = if (res == resolution) Color(0xFF8B5CF6) else Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        onClick = {
                            onSelectResolution(res)
                            showResolutionDropdown = false
                        }
                    )
                }
            }
        }

        // Top-Right: Fullscreen / Expand icon
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onFullscreenToggle)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Expand",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // Top-Right: "fx" filter chip — reference image shows a small
        // pill above the fullscreen icon labelled "fx" in purple to
        // mirror the fx badge on the Cinematic Glow track row. Tapping
        // it opens the filter panel for quick access.
        if (activeFilterId != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "fx",
                    color = Color(0xFF8B5CF6),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

// Left Tool Rail floating over video preview
@Composable
fun LeftToolRail(
    onEffects: () -> Unit = {},
    onFilters: () -> Unit = {},
    onAdjust: () -> Unit = {},
    onText: () -> Unit = {},
    onSticker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RailItem(Icons.Default.AutoAwesome, "Effects", onEffects)
        RailItem(Icons.Default.FilterAlt, "Filters", onFilters)
        RailItem(Icons.Default.Tune, "Adjust", onAdjust)
        RailItem(Icons.Default.TextFields, "Text", onText)
        RailItem(Icons.Default.EmojiEmotions, "Sticker", onSticker)
    }
}

// Right Tool Rail floating over video preview
@Composable
fun RightToolRail(
    onAdd: () -> Unit = {},
    onAudio: () -> Unit = {},
    onRecord: () -> Unit = {},
    onCamera: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RailItem(Icons.Default.Add, "Add", onAdd)
        RailItem(Icons.Default.MusicNote, "Audio", onAudio)
        RailItem(Icons.Default.Mic, "Record", onRecord)
        RailItem(Icons.Default.CameraAlt, "Camera", onCamera)
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(44.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = Color(0xFFD1D5DB),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// === 3. PLAYBACK CONTROL BAR ===
@Composable
fun PlaybackControlBar(
    currentTimeMs: Long = 4370L,
    totalDurationMs: Long = 18690L,
    isPlaying: Boolean = false,
    onTogglePlay: () -> Unit = {},
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    onFullscreenToggle: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Time counter
        Text(
            text = "${TimeFormat.formatMs(currentTimeMs)} / ${TimeFormat.formatMs(totalDurationMs)}",
            color = Color(0xFF9CA3AF),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )

        // Center Playback Icons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous Clip",
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onPrev)
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next Clip",
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onNext)
            )
        }

        // Right Icons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onFullscreenToggle)
            )

            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onSettings)
            )
        }
    }
}

// === 4. TIMELINE RULER ===
@Composable
fun TimelineRuler(
    currentTimeMs: Long = 4000L,
    totalDurationMs: Long = 18690L,
    onScrub: (Long) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(Color(0xFF0F0F17))
            .pointerInput(totalDurationMs) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    onScrub((fraction * totalDurationMs).toLong())
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("00:00", "00:02", "00:04", "00:06", "00:08", "00:10", "00:12", "00:14", "00:16", "00:18").forEach { time ->
                Text(
                    text = time,
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // Vertical Playhead Line
        val progress = if (totalDurationMs > 0) currentTimeMs.toFloat() / totalDurationMs else 0f
        Canvas(modifier = Modifier.fillMaxSize()) {
            val headX = size.width * progress
            drawLine(
                color = Color.White,
                start = Offset(headX, 0f),
                end = Offset(headX, size.height),
                strokeWidth = 2f
            )
        }
    }
}

// === 5. TIMELINE TRACK AREA ===
@Composable
fun TimelineTrackArea(
    state: com.apexstudio.app.presentation.state.EditorState,
    onScrub: (Long) -> Unit = {},
    onSelectClip: (String?) -> Unit = {},
    onCover: () -> Unit = {},
    onAddMedia: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0F))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // First Row: Video Thumbnail Strip + Cover button (icon-only) + Add button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Cover" button (pencil icon + label) — reference image shows
            // both, so the previous icon-only rendering from PR #71 is
            // restored here. The spec for PR #71 said icon-only but the
            // actual reference UI keeps the "Cover" label.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1F1F2E))
                    .clickable(onClick = onCover)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Cover",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Cover",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(Modifier.width(6.dp))

            // Main Video Filmstrip Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1B2E))
                    .border(1.5.dp, Color(0xFF8B5CF6), RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                // Repeated thumbnail frames
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(6) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF2E1065), Color(0xFF3B0764))
                                    )
                                )
                        )
                    }
                }

                // Speed Indicator Chip "1.0x"
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Speed",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Text("1.0x", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                // Trim handles (white vertical bars)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(4.dp)
                        .fillMaxHeight(0.7f)
                        .background(Color.White, RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(4.dp)
                        .fillMaxHeight(0.7f)
                        .background(Color.White, RoundedCornerShape(2.dp))
                )
            }

            Spacer(Modifier.width(8.dp))

            // White Circular "+" Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onAddMedia),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Media",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        val clips = state.project?.clips ?: emptyList()
        val textClip = clips.firstOrNull { it.textOverlays.isNotEmpty() }
        val textLabel = textClip?.textOverlays?.firstOrNull()?.text ?: "ApexStudio  Pro Video Editor"

        val activeFxId = state.activeFxId
        val fxLabel = if (activeFxId != null) activeFxId.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } else "Cinematic Glow"

        val audioClip = clips.firstOrNull { it.type == ClipType.AUDIO || it.type == ClipType.SFX }
        val audioLabel = audioClip?.name ?: "Dreamscape"

        val voiceClip = clips.firstOrNull { it.name.contains("Voice", ignoreCase = true) || it.name.contains("Mic", ignoreCase = true) }
        val voiceLabel = voiceClip?.name ?: "Voice Over"

        // Stacked Horizontal Layer Rows (4 Rows - Lock icon removed, only Eye icon remains)
        TrackLayerRow(
            barColor = Color(0xFF8B5CF6),
            icon = Icons.Default.TextFields,
            label = textLabel,
            badgeText = null
        )

        TrackLayerRow(
            barColor = Color(0xFF3B82F6),
            icon = Icons.Default.AutoAwesome,
            label = fxLabel,
            badgeText = "fx"
        )

        TrackLayerRow(
            barColor = Color(0xFF10B981),
            icon = Icons.Default.MusicNote,
            label = audioLabel,
            isWaveform = true
        )

        TrackLayerRow(
            barColor = Color(0xFF7C3AED),
            icon = Icons.Default.Mic,
            label = voiceLabel,
            isWaveform = true
        )

        Divider(color = Color(0xFF1F1F2E), thickness = 1.dp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun TrackLayerRow(
    barColor: Color,
    icon: ImageVector,
    label: String,
    badgeText: String? = null,
    isWaveform: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Reference image rows are ~50dp tall — was 24dp which
            // collapsed the waveform bars and made the coloured bars
            // look like thin strips.
            .height(50.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Action Icon [eye] only (lock icon removed)
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .clickable { }
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Toggle Visibility",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(16.dp)
            )
        }

        // Colored Rounded Rectangle Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(barColor.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isWaveform) {
                    // Simulated waveform lines — reference image shows
                    // ~32 bars filling the row width; was 16 bars at
                    // 20dp height. Bumped to match the new 50dp row.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        repeat(32) { index ->
                            val heightFraction = if (index % 3 == 0) 0.8f else if (index % 2 == 0) 0.5f else 0.3f
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(34.dp * heightFraction)
                                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }

                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// === 6. BOTTOM EDIT TOOLBAR ===
@Composable
fun BottomEditToolbar(
    onEdit: () -> Unit = {},
    onAudio: () -> Unit = {},
    onText: () -> Unit = {},
    onEffects: () -> Unit = {},
    onOverlay: () -> Unit = {},
    onTransition: () -> Unit = {},
    onFilters: () -> Unit = {}
) {
    val items = listOf(
        EditToolItem("Edit", Icons.Default.ContentCut, isActive = true, onClick = onEdit),
        EditToolItem("Audio", Icons.Default.MusicNote, onClick = onAudio),
        EditToolItem("Text", Icons.Default.TextFields, onClick = onText),
        EditToolItem("Effects", Icons.Default.AutoAwesome, onClick = onEffects),
        EditToolItem("Overlay", Icons.Default.Layers, onClick = onOverlay),
        EditToolItem("Transition", Icons.Default.Transform, onClick = onTransition),
        EditToolItem("Filters", Icons.Default.FilterAlt, onClick = onFilters)
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFF0F0F17)),
        verticalAlignment = Alignment.CenterVertically,
        // Evenly distribute items across the row width so all 7 fit
        // without clipping. Earlier used `Arrangement.SpaceBetween`
        // which doesn't apply inside LazyRow + fixed width per item,
        // causing the first item's "Edit" label to be clipped to "it"
        // when total item width exceeded viewport width.
        //
        // Note: `Modifier.weight(1f)` is NOT allowed inside
        // LazyRow.items {} (LazyItemScope doesn't provide RowScope),
        // so each item uses a fixed but compact width and the
        // outer spacedBy distributes the leftover space.
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items) { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    // 7 items at 44dp + 6 gaps at 4dp + 24dp horizontal
                    // padding = 308 + 24 + 24 = 356dp, fits a 360dp
                    // viewport with 4dp headroom. No horizontal scroll.
                    .width(44.dp)
                    .clickable(onClick = item.onClick)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (item.isActive) Color(0xFF8B5CF6) else Color(0xFF9CA3AF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.label,
                    color = if (item.isActive) Color(0xFF8B5CF6) else Color(0xFF9CA3AF),
                    fontSize = 10.sp,
                    fontWeight = if (item.isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

private data class EditToolItem(
    val label: String,
    val icon: ImageVector,
    val isActive: Boolean = false,
    val onClick: () -> Unit
)

// === 7. BOTTOM NAVIGATION BAR ===
@Composable
fun BottomNavBar(
    onMedia: () -> Unit = {},
    onElements: () -> Unit = {},
    onTools: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color(0xFF0A0A0F))
            .border(0.5.dp, Color(0xFF1F1F2E), RectangleShape)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("Media", Icons.Default.Movie, onMedia)
        NavItem("Elements", Icons.Default.Extension, onElements)
        NavItem("Tools", Icons.Default.Build, onTools)
        NavItem("Settings", Icons.Default.Settings, onSettings)
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFD1D5DB),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = Color(0xFFD1D5DB),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// Add Media Menu Sheet
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddMediaMenuSheet(
    onPickVideo: () -> Unit,
    onPickOverlay: () -> Unit,
    onPickAudio: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ApexPalette.BgElevated,
        scrimColor = Color.Black.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Add to project",
                color = ApexPalette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            AddMediaRow(
                icon = Icons.Default.VideoLibrary,
                title = "Video clip",
                subtitle = "Add to the V1 timeline lane",
                tint = ApexPalette.NeonCyan,
                onClick = {
                    onPickVideo()
                    onDismiss()
                }
            )
            AddMediaRow(
                icon = Icons.Default.GraphicEq,
                title = "Audio",
                subtitle = "Add to the A1 audio lane",
                tint = ApexPalette.NeonEmerald,
                onClick = {
                    onPickAudio()
                    onDismiss()
                }
            )
            AddMediaRow(
                icon = Icons.Default.Layers,
                title = "Overlay clip",
                subtitle = "Add as a picture-in-picture overlay (V2)",
                tint = ApexPalette.NeonPurple,
                onClick = {
                    onPickOverlay()
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AddMediaRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ApexPalette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = ApexPalette.TextSecondary,
                fontSize = 11.sp
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ApexPalette.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
