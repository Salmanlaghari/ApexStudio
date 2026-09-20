package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.apexstudio.app.data.media.ThumbnailExtractor
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.domain.model.AudioTrack
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
import kotlinx.coroutines.launch
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

    val activePreset: com.apexstudio.app.data.filter.FilterPreset? = remember(state.activeFilterId) {
        state.activeFilterId?.let { id -> filterEngine.manifest.filters.firstOrNull { it.id == id } }
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
            val overlayClip = state.project?.clips?.firstOrNull { it.type == ClipType.OVERLAY }
            val stickers = (state.project?.stickers ?: emptyList()) + (selectedClip?.stickers ?: emptyList())
            val textOverlays = selectedClip?.textOverlays ?: emptyList()
            var showUpcomingPreview by remember { mutableStateOf(false) }

            VideoPreviewArea(
                exoPlayer = exoPlayer,
                chromaKeySettings = state.chromaKeySettings,
                overlayClip = overlayClip,
                isCoverMode = isCoverMode,
                adjustments = state.adjustments,
                activeFilterId = state.activeFilterId,
                filterIntensity = state.filterIntensity,
                activeArFilterId = state.activeArFilterId,
                arFilterIntensity = state.arFilterIntensity,
                arFilterCustomText = state.arFilterCustomText,
                playerError = state.playerError,
                activeFxId = state.activeFxId,
                fxIntensity = state.fxIntensity,
                isPlaying = state.isPlaying,
                onTapVideo = {
                    showUpcomingPreview = true
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

            // Floating Left Quick Tool Rail (AR Face, Effects, Filters, 3D Chroma, Adjust, Text)
            LeftToolRail(
                onArFilters = { vm.openArFilterPanel() },
                onEffects = { vm.openFxPanel() },
                onFilters = { vm.openFilterPanel() },
                onAdjust = { vm.openAdjustmentsPanel() },
                onChromaKey = { vm.openChromaKeyPanel() },
                onText = { vm.openTextPanel() },
                onSticker = { vm.openStickerPanel() },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
            )

            // Floating Right Quick Tool Rail (Add Media, Audio Mixer, Voice Record, Camera)
            RightToolRail(
                onAdd = { showAddMediaMenu = true },
                onAudio = { vm.openAudioMixer() },
                onRecord = { vm.openVoiceRecorder() },
                onCamera = { vm.openCameraCapture() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
            )

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

        PlaybackControlBar(
            currentTimeMs = state.playerPositionMs,
            totalDurationMs = state.durationMs,
            isPlaying = state.isPlaying,
            canUndo = state.canUndo,
            canRedo = state.canRedo,
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
            onFullscreenToggle = { vm.toggleFullscreenPreview() }
        )

        val activeClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId } ?: state.project?.clips?.firstOrNull()
        val hasKeyframeAtPlayhead = activeClip?.keyframes?.keyframes?.any { kotlin.math.abs(it.timeMs - state.playerPositionMs) <= 150L } == true
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
            onMoveClipLeft = { clipId -> vm.moveClipLeft(clipId) },
            onMoveClipRight = { clipId -> vm.moveClipRight(clipId) },
            onOpenSpeed = { vm.openSpeedPanel() },
            onOpenAudio = { vm.openAudioMixer() },
            onOpenTrim = { vm.openTrimPanel() },
            onOpenText = { vm.openTextPanel() },
            onOpenStickers = { vm.openStickerPanel() },
            onOpenFx = { vm.openFxPanel() },
            onOpenVoice = { vm.openVoiceRecorder() },
            onOpenAnimation = { vm.setKeyframePanelOpen(true) },
            onOpenTransition = { vm.openTransmissionTemplatesPanel() },
            onOpenChromaKey = { vm.openChromaKeyPanel() },
            onOpenArFilters = { vm.openArFilterPanel() },
            onOpenRoyaltyMusic = { vm.openRoyaltyMusicDialog() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        BottomEditToolbar(
            onEdit = { vm.openTrimPanel() },
            onAudio = { vm.openAudioMixer() },
            onText = { vm.openTextPanel() },
            onStickers = { vm.openStickerPanel() },
            onEffects = { vm.openFxPanel() },
            onFilters = { vm.openFilterPanel() },
            onArFilters = { vm.openArFilterPanel() },
            onAdjust = { vm.openAdjustmentsPanel() }
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

// === 1. TOP APP BAR ===
@Composable
fun TopAppBarSection(
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    resolution: String = "1080P",
    onSelectResolution: (String) -> Unit = {},
    isCoverMode: Boolean = true,
    onToggleCoverMode: () -> Unit = {},
    onFullscreenToggle: () -> Unit = {},
    onBack: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onHelp: () -> Unit = {},
    onExport: () -> Unit = {}
) {
    var showResolutionDropdown by remember { mutableStateOf(false) }

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

        // Right side: Fit/Cover toggle + Fullscreen + Resolution dropdown + Export button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Fit / Cover Mode Toggle Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1B1B26))
                    .border(1.dp, Color(0xFF2E2E40), RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleCoverMode)
                    .padding(horizontal = 7.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isCoverMode) "COVER" else "FIT",
                    color = Color(0xFFD1D5DB),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Fullscreen Preview Toggle
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF1B1B26))
                    .border(1.dp, Color(0xFF2E2E40), CircleShape)
                    .clickable(onClick = onFullscreenToggle)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Resolution Dropdown
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1B1B26))
                        .border(1.dp, Color(0xFF2E2E40), RoundedCornerShape(8.dp))
                        .clickable { showResolutionDropdown = true }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = resolution,
                            color = Color(0xFFD1D5DB),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Resolution",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(14.dp)
                        )
                    }
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

            // Prominent Gradient Export Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF2563EB), Color(0xFF3B82F6))
                        )
                    )
                    .clickable(onClick = onExport)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
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
    chromaKeySettings: com.apexstudio.app.domain.model.ChromaKeySettings = com.apexstudio.app.domain.model.ChromaKeySettings(),
    overlayClip: MediaClip? = null,
    isCoverMode: Boolean = true,
    adjustments: com.apexstudio.app.domain.model.VideoAdjustments = com.apexstudio.app.domain.model.VideoAdjustments(),
    activeFilterId: String? = null,
    filterIntensity: Float = 1.0f,
    activeArFilterId: String? = null,
    arFilterIntensity: Float = 0.85f,
    arFilterCustomText: String = "",
    playerError: String? = null,
    stickers: List<StickerOverlay> = emptyList(),
    activeFxId: String? = null,
    fxIntensity: Float = 0f,
    isPlaying: Boolean = false,
    textOverlays: List<com.apexstudio.app.domain.model.TextOverlay> = emptyList(),
    selectedTextOverlayId: String? = null,
    currentTimeMs: Long = 0L,
    onSelectTextOverlay: ((String) -> Unit)? = null,
    onMoveTextOverlay: ((id: String, dx: Float, dy: Float) -> Unit)? = null,
    onDeleteTextOverlay: ((String) -> Unit)? = null,
    onDuplicateTextOverlay: ((String) -> Unit)? = null,
    onEditTextOverlay: ((String) -> Unit)? = null,
    onSizeScaleChange: ((String, Float) -> Unit)? = null,
    onRetryLoad: (() -> Unit)? = null,
    onTapVideo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentResizeMode = if (isCoverMode) {
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } else {
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF12121A))
            .border(1.dp, Color(0xFF1F1F2E), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTapVideo() }
                )
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
                        resizeMode = currentResizeMode
                        player = exoPlayer
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = currentResizeMode
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

        // Live Filter Grading & Color Adjustments Overlay (Hardware accelerated Compose canvas, 0ms lag, zero video disappearances)
        FilterPreviewOverlay(
            filterId = activeFilterId,
            intensity = filterIntensity,
            adjustments = adjustments,
            modifier = Modifier.fillMaxSize()
        )

        // Real-time 3D Chroma Key Live Overlay
        if (chromaKeySettings.enabled) {
            ChromaKeyPreviewOverlay(
                settings = chromaKeySettings,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Live Visual FX Overlay (VHS, Glitch, Scanlines, Grain, Light Leaks, Bloom, etc.)
        if (activeFxId != null && fxIntensity > 0f) {
            FxPreviewOverlay(
                fxId = activeFxId,
                intensity = fxIntensity,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Live AR/AI Face & Festival Filter Overlay
        if (activeArFilterId != null && arFilterIntensity > 0f) {
            ArFaceFilterOverlay(
                filterId = activeArFilterId,
                intensity = arFilterIntensity,
                customText = arFilterCustomText,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Picture-in-Picture Overlay Media Preview (Video / Image Overlay)
        if (overlayClip != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
                    .size(130.dp, 75.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.5.dp, Color(0xFF00F0FF), RoundedCornerShape(8.dp))
            ) {
                coil.compose.AsyncImage(
                    model = overlayClip.uri,
                    contentDescription = "Overlay Media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "PIP OVERLAY",
                        color = Color(0xFF00F0FF),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
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
    }
}

// Left Tool Rail floating over video preview
@Composable
fun LeftToolRail(
    onArFilters: () -> Unit = {},
    onEffects: () -> Unit = {},
    onFilters: () -> Unit = {},
    onAdjust: () -> Unit = {},
    onChromaKey: () -> Unit = {},
    onText: () -> Unit = {},
    onSticker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RailItem(Icons.Default.FaceRetouchingNatural, "AR Face", onArFilters)
        RailItem(Icons.Default.AutoAwesome, "Effects", onEffects)
        RailItem(Icons.Default.FilterAlt, "Filters", onFilters)
        RailItem(Icons.Default.Tune, "Adjust", onAdjust)
        RailItem(Icons.Default.Layers, "3D Chroma", onChromaKey)
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
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onTogglePlay: () -> Unit = {},
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onFullscreenToggle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Time counter matching reference image (e.g. 00:12 / 00:28)
        Text(
            text = "${TimeFormat.msToShort(currentTimeMs)} / ${TimeFormat.msToShort(totalDurationMs)}",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )

        // Center Playback Icons: Previous, Play/Pause, Next
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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

        // Right Icons: Undo, Redo, Fullscreen
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) Color.White else Color(0xFF4B5563),
                modifier = Modifier
                    .size(20.dp)
                    .clickable(enabled = canUndo, onClick = onUndo)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) Color.White else Color(0xFF4B5563),
                modifier = Modifier
                    .size(20.dp)
                    .clickable(enabled = canRedo, onClick = onRedo)
            )

            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onFullscreenToggle)
            )
        }
    }
}

enum class SelectedLayerType {
    NONE,
    OVERLAY_V2,
    VIDEO_V1,
    TEXT_TXT,
    FX_LAYER,
    AUDIO_A1
}

// === 4. CAPCUT-STYLE TIMELINE TRACK AREA ===
@Composable
fun TimelineTrackArea(
    state: com.apexstudio.app.presentation.state.EditorState,
    onScrub: (Long) -> Unit = {},
    onToggleSnapToBeat: () -> Unit = {},
    onToggleMagneticSnapping: () -> Unit = {},
    onToggleRippleEdit: () -> Unit = {},
    onSelectClip: (String?) -> Unit = {},
    onSelectFx: (String) -> Unit = {},
    onSelectFilter: (String) -> Unit = {},
    onAddMedia: () -> Unit = {},
    onSplitClip: (clipId: String, atMs: Long) -> Unit = { _, _ -> },
    onDuplicateClip: (clipId: String) -> Unit = {},
    onDeleteClip: (clipId: String) -> Unit = {},
    onMoveClipLeft: (clipId: String) -> Unit = {},
    onMoveClipRight: (clipId: String) -> Unit = {},
    onOpenSpeed: () -> Unit = {},
    onOpenAudio: () -> Unit = {},
    onOpenTrim: () -> Unit = {},
    onOpenText: () -> Unit = {},
    onOpenStickers: () -> Unit = {},
    onOpenFx: () -> Unit = {},
    onOpenVoice: () -> Unit = {},
    onOpenAnimation: () -> Unit = {},
    onOpenTransition: () -> Unit = {},
    onOpenChromaKey: () -> Unit = {},
    onOpenArFilters: () -> Unit = {},
    onOpenRoyaltyMusic: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clips = state.project?.clips ?: emptyList()
    val activeClip = clips.firstOrNull { it.id == state.selectedClipId } ?: clips.firstOrNull()
    val durationMs = state.durationMs.coerceAtLeast(1000L)
    val playheadMs = state.playerPositionMs.coerceIn(0L, durationMs)

    var selectedLayer by remember { mutableStateOf(SelectedLayerType.NONE) }
    val timelineScrollState = rememberScrollState()
    val zoomFactor = state.timelineZoom.coerceIn(0.5f, 6.0f)

    // Per-second extracted video frames for synchronized scrubbing
    var perSecondThumbnails by remember(activeClip?.id, activeClip?.uri) {
        mutableStateOf<Map<Int, Bitmap>>(emptyMap())
    }
    var extractedThumbnails by remember(activeClip?.id) { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(activeClip?.id, activeClip?.uri, activeClip?.trimStartMs, activeClip?.trimEndMs) {
        val uri = activeClip?.uri
        if (uri != null) {
            try {
                val trimStart = activeClip.trimStartMs
                val trimEnd = if (activeClip.trimEndMs > trimStart) activeClip.trimEndMs else (activeClip.durationMs).coerceAtLeast(1000L)
                val map = ThumbnailExtractor.extractPerSecondFrames(
                    context = context,
                    uri = uri,
                    trimStartMs = trimStart,
                    trimEndMs = trimEnd,
                    frameWidthPx = 120,
                    frameHeightPx = 80,
                    maxSeconds = 20
                )
                perSecondThumbnails = map
                if (map.isNotEmpty()) {
                    extractedThumbnails = map.values.toList()
                } else {
                    val frames = ThumbnailExtractor.extractFrames(
                        context = context,
                        uri = uri,
                        trimStartMs = trimStart,
                        trimEndMs = trimEnd,
                        frameWidthPx = 120,
                        frameHeightPx = 80,
                        frameCount = 6
                    )
                    extractedThumbnails = frames
                }
            } catch (e: Exception) {
                Log.w("TimelineTrackArea", "Thumbnail extraction fallback: ${e.message}")
            }
        }
    }

    // Dynamic Scrub Thumbnail synced to active playhead second
    val currentSecond = ((playheadMs - (activeClip?.trimStartMs ?: 0L)).coerceAtLeast(0L) / 1000L).toInt()
    var activeScrubThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(currentSecond, activeClip?.uri) {
        val thumb = perSecondThumbnails[currentSecond]
        if (thumb != null) {
            activeScrubThumbnail = thumb
        } else {
            val uri = activeClip?.uri
            if (uri != null) {
                try {
                    val frame = com.apexstudio.app.data.media.VideoThumbnailExtractor.extractFrame(
                        context,
                        uri,
                        (activeClip.trimStartMs + currentSecond * 1000L).coerceIn(0L, durationMs)
                    )
                    if (frame != null) activeScrubThumbnail = frame
                } catch (_: Exception) {}
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF08080E))
            .padding(top = 2.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Multi-Track Timeline Container: Pinned Icon Sidebar on Left + Horizontal Scrollable Tracks on Right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // --- 1. LEFT TRACK TYPE ICON SIDEBAR (Clear Distinct Icon + Label + Badge for V2, V1, TXT, FX, A1) ---
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF0C0C14))
                    .border(width = 1.dp, color = Color(0xFF1B1B28)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top header / ruler height spacer: Magnetic Snapping & Ripple Edit Toggle Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (state.magneticSnapping || state.snapToBeat) ApexPalette.NeonCyan.copy(alpha = 0.25f) else Color(0xFF151520))
                            .clickable(onClick = {
                                onToggleMagneticSnapping()
                                onToggleSnapToBeat()
                            }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SNAP",
                            color = if (state.magneticSnapping || state.snapToBeat) ApexPalette.NeonCyan else Color(0xFF6B7280),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (state.rippleEditEnabled) Color(0xFFF59E0B).copy(alpha = 0.25f) else Color(0xFF151520))
                            .clickable(onClick = onToggleRippleEdit),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "RPL",
                            color = if (state.rippleEditEnabled) Color(0xFFFBBF24) else Color(0xFF6B7280),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))

                // Track 1: V2 (Overlay Video) Icon & Label
                TrackSidebarCell(
                    badge = "V2",
                    label = "Overlay",
                    icon = Icons.Default.Layers,
                    tint = Color(0xFF00E5FF),
                    height = 44.dp,
                    isSelected = selectedLayer == SelectedLayerType.OVERLAY_V2,
                    onClick = {
                        selectedLayer = if (selectedLayer == SelectedLayerType.OVERLAY_V2) SelectedLayerType.NONE else SelectedLayerType.OVERLAY_V2
                    }
                )

                Spacer(Modifier.height(3.dp))

                // Track 2: V1 (Main Video) Icon & Label
                TrackSidebarCell(
                    badge = "V1",
                    label = "Video",
                    icon = Icons.Default.Movie,
                    tint = Color(0xFF38BDF8),
                    height = 58.dp,
                    isSelected = selectedLayer == SelectedLayerType.VIDEO_V1,
                    onClick = {
                        selectedLayer = if (selectedLayer == SelectedLayerType.VIDEO_V1) SelectedLayerType.NONE else SelectedLayerType.VIDEO_V1
                        activeClip?.let { onSelectClip(it.id) }
                    }
                )

                Spacer(Modifier.height(3.dp))

                // Track 3: TXT (Text & Stickers) Icon & Label
                TrackSidebarCell(
                    badge = "TXT",
                    label = "Text",
                    icon = Icons.Default.TextFields,
                    tint = Color(0xFFA855F7),
                    height = 42.dp,
                    isSelected = selectedLayer == SelectedLayerType.TEXT_TXT,
                    onClick = {
                        selectedLayer = if (selectedLayer == SelectedLayerType.TEXT_TXT) SelectedLayerType.NONE else SelectedLayerType.TEXT_TXT
                    }
                )

                Spacer(Modifier.height(3.dp))

                // Track 4: FX (Effects & Filters) Icon & Label
                TrackSidebarCell(
                    badge = "FX",
                    label = "Effects",
                    icon = Icons.Default.AutoAwesome,
                    tint = Color(0xFFF59E0B),
                    height = 42.dp,
                    isSelected = selectedLayer == SelectedLayerType.FX_LAYER,
                    onClick = {
                        selectedLayer = if (selectedLayer == SelectedLayerType.FX_LAYER) SelectedLayerType.NONE else SelectedLayerType.FX_LAYER
                    }
                )

                Spacer(Modifier.height(3.dp))

                // Track 5: A1 (Audio Track) Icon & Label
                TrackSidebarCell(
                    badge = "A1",
                    label = "Audio",
                    icon = Icons.Default.GraphicEq,
                    tint = Color(0xFF10B981),
                    height = 44.dp,
                    isSelected = selectedLayer == SelectedLayerType.AUDIO_A1,
                    onClick = {
                        selectedLayer = if (selectedLayer == SelectedLayerType.AUDIO_A1) SelectedLayerType.NONE else SelectedLayerType.AUDIO_A1
                    }
                )
            }

            // --- 2. HORIZONTAL TRACK AREA WITH UNIFIED PLAYHEAD ---
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF0A0A10))
                    .pointerInput(durationMs) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val frac = (offset.x / size.width).coerceIn(0f, 1f)
                                onScrub((frac * durationMs).toLong())
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                                onScrub((frac * durationMs).toLong())
                            }
                        )
                    }
                    .pointerInput(durationMs) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            onScrub((frac * durationMs).toLong())
                        }
                    }
            ) {
                val containerWidth = maxWidth
                val progress = (playheadMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

                // Beat-Sync Vertical Alignment Guide Lines
                if (state.snapToBeat && state.beatMarkersMs.isNotEmpty()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                    ) {
                        for (beatMs in state.beatMarkersMs) {
                            if (beatMs in 0L..durationMs) {
                                val beatX = (beatMs.toFloat() / durationMs.toFloat()) * size.width
                                drawLine(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.22f),
                                    start = Offset(beatX, 24.dp.toPx()),
                                    end = Offset(beatX, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    // Time Ruler
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .pointerInput(durationMs) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                                    onScrub((frac * durationMs).toLong())
                                }
                            }
                    ) {
                        // Visual Diamond Beat Markers on Ruler
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (state.beatMarkersMs.isNotEmpty()) {
                                for (beatMs in state.beatMarkersMs) {
                                    if (beatMs in 0L..durationMs) {
                                        val beatX = (beatMs.toFloat() / durationMs.toFloat()) * size.width
                                        val diamondPath = Path().apply {
                                            moveTo(beatX, size.height - 11f)
                                            lineTo(beatX + 4.5f, size.height - 6f)
                                            lineTo(beatX, size.height - 1f)
                                            lineTo(beatX - 4.5f, size.height - 6f)
                                            close()
                                        }
                                        drawPath(
                                            path = diamondPath,
                                            color = if (state.snapToBeat) ApexPalette.NeonCyan else Color(0xFFF59E0B)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val stepCount = 5
                            for (i in 0..stepCount) {
                                val timeAtStep = (durationMs * i / stepCount)
                                Text(
                                    text = TimeFormat.msToShort(timeAtStep),
                                    color = Color(0xFF6B7280),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    // Track 1: V2 (Second Video / Overlay / PIP Track)
                    val overlayClip = state.project?.clips?.firstOrNull { it.type == ClipType.OVERLAY }
                    val isV2Selected = selectedLayer == SelectedLayerType.OVERLAY_V2
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isV2Selected) Color(0xFF0F2633) else if (overlayClip != null) Color(0xFF0C2B38) else Color(0xFF0E141D))
                            .border(
                                width = if (isV2Selected) 2.dp else 1.dp,
                                color = if (isV2Selected) Color(0xFFFFD700) else if (overlayClip != null) Color(0xFF00E5FF).copy(alpha = 0.7f) else Color(0xFF00E5FF).copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedLayer = if (isV2Selected) SelectedLayerType.NONE else SelectedLayerType.OVERLAY_V2
                                if (overlayClip != null) onSelectClip(overlayClip.id) else onAddMedia()
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (overlayClip != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.3f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("V2", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.Default.Layers, contentDescription = "Overlay", tint = Color(0xFF00E5FF), modifier = Modifier.size(15.dp))
                                Text(
                                    text = overlayClip.name.ifEmpty { "PIP Video Overlay" },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${overlayClip.durationMs / 1000L}s",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                                Text(
                                    text = "+ Add Video / PIP Overlay (V2)",
                                    color = Color(0xFF67E8F9),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Keyframe Diamonds on V2 Overlay Track
                        if (overlayClip != null && overlayClip.keyframes.keyframes.isNotEmpty()) {
                            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                                val boxWidth = maxWidth
                                val effDur = (overlayClip.trimEndMs - overlayClip.trimStartMs).coerceAtLeast(1000L)
                                overlayClip.keyframes.keyframes.forEach { kf ->
                                    val frac = (kf.timeMs.toFloat() / effDur.toFloat()).coerceIn(0f, 1f)
                                    val kfX = (boxWidth.value * frac).dp
                                    Box(
                                        modifier = Modifier
                                            .offset(x = (kfX - 4.dp).coerceAtLeast(0.dp), y = 18.dp)
                                            .size(8.dp)
                                            .graphicsLayer { rotationZ = 45f }
                                            .background(Color(0xFF00E5FF), RoundedCornerShape(2.dp))
                                            .border(1.dp, Color.White, RoundedCornerShape(2.dp))
                                            .clickable { onScrub(overlayClip.trimStartMs + kf.timeMs) }
                                    )
                                }
                            }
                        }

                        if (isV2Selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(width = 4.dp, height = 28.dp)
                                    .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(width = 4.dp, height = 28.dp)
                                    .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                            )
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    // Track 2: V1 Main Video Filmstrip Track with Per-Second Scrubbing Thumbnails
                    val isV1LayerSelected = selectedLayer == SelectedLayerType.VIDEO_V1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayClips = clips.filter { it.type == ClipType.VIDEO }.ifEmpty {
                            activeClip?.let { listOf(it) } ?: listOf(
                                MediaClip(
                                    id = "default_clip",
                                    uri = "",
                                    name = "Sample Video",
                                    durationMs = durationMs,
                                    trimEndMs = durationMs
                                )
                            )
                        }

                        displayClips.forEachIndexed { index, clip ->
                            val isClipActive = clip.id == activeClip?.id
                            val clipDur = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1000L)
                            val totalSec = (clipDur / 1000L).toInt().coerceIn(1, 14)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A1A28))
                                    .border(
                                        width = if (isV1LayerSelected || isClipActive) 2.dp else 1.dp,
                                        color = if (isV1LayerSelected) Color(0xFFFFD700) else if (isClipActive) Color(0xFF38BDF8) else Color(0xFF2A2A3C),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedLayer = if (isV1LayerSelected) SelectedLayerType.NONE else SelectedLayerType.VIDEO_V1
                                        onSelectClip(clip.id)
                                    }
                                    .padding(2.dp)
                            ) {
                                // Filmstrip divided into synchronized per-second slices
                                Row(modifier = Modifier.fillMaxSize()) {
                                    for (sec in 0 until totalSec) {
                                        val isActiveSec = isClipActive && sec == currentSecond
                                        val frameBmp = perSecondThumbnails[sec]
                                            ?: extractedThumbnails.getOrNull(sec % extractedThumbnails.size.coerceAtLeast(1))

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .padding(horizontal = 0.5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(
                                                    if (isActiveSec) Color(0xFF0284C7).copy(alpha = 0.35f)
                                                    else Color(0xFF1E1B4B + (sec % 4) * 0x050408)
                                                )
                                                .border(
                                                    width = if (isActiveSec) 1.5.dp else 0.dp,
                                                    color = if (isActiveSec) Color(0xFF38BDF8) else Color.Transparent,
                                                    shape = RoundedCornerShape(3.dp)
                                                )
                                        ) {
                                            if (frameBmp != null) {
                                                Image(
                                                    bitmap = frameBmp.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Movie,
                                                        contentDescription = null,
                                                        tint = Color.White.copy(alpha = 0.2f),
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }

                                            // Second indicator tag at bottom of frame slice
                                            Text(
                                                text = "${sec}s",
                                                color = if (isActiveSec) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.6f),
                                                fontSize = 7.sp,
                                                fontWeight = if (isActiveSec) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(1.dp)
                                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 2.dp, vertical = 0.5.dp)
                                            )
                                        }
                                    }
                                }

                                // Selection Grip Handles on active V1 layer
                                if (isV1LayerSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .size(width = 4.dp, height = 36.dp)
                                            .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .size(width = 4.dp, height = 36.dp)
                                            .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                                    )
                                }

                                // Speed chip at top-left
                                val speedDisplay = String.format(java.util.Locale.US, "%.1fx", state.playbackSpeed)
                                Text(
                                    text = speedDisplay,
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(2.dp)
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                )

                                // Keyframe Diamonds on Clip
                                if (clip.keyframes.keyframes.isNotEmpty()) {
                                    BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                                        val boxWidth = maxWidth
                                        val effDur = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1000L)
                                        clip.keyframes.keyframes.forEach { kf ->
                                            val frac = (kf.timeMs.toFloat() / effDur.toFloat()).coerceIn(0f, 1f)
                                            val kfX = (boxWidth.value * frac).dp
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = (kfX - 4.dp).coerceAtLeast(0.dp), y = 24.dp)
                                                .size(8.dp)
                                                .graphicsLayer { rotationZ = 45f }
                                                .background(ApexPalette.NeonCyan, RoundedCornerShape(2.dp))
                                                .border(1.dp, Color.White, RoundedCornerShape(2.dp))
                                                .clickable { onScrub(clip.trimStartMs + kf.timeMs) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Transition Button between adjacent clips
                            if (index < displayClips.lastIndex) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF262638))
                                        .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(6.dp))
                                        .clickable(onClick = onOpenTransition),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Transition",
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        // Add Media "+" Button
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(onClick = onAddMedia),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Media",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    // Track 3: Text & Stickers Overlay Track (TXT / STK)
                    val textOverlays = activeClip?.textOverlays ?: emptyList()
                    val allStickers = (state.project?.stickers ?: emptyList()) + (activeClip?.stickers ?: emptyList())
                    val hasTextOrSticker = textOverlays.isNotEmpty() || allStickers.isNotEmpty()
                    val isTxtSelected = selectedLayer == SelectedLayerType.TEXT_TXT

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isTxtSelected) Color(0xFF3B0764) else if (hasTextOrSticker) Color(0xFF581C87).copy(alpha = 0.85f) else Color(0xFF140E24))
                            .border(
                                width = if (isTxtSelected) 2.dp else 1.dp,
                                color = if (isTxtSelected) Color(0xFFFFD700) else if (hasTextOrSticker) Color(0xFFA855F7).copy(alpha = 0.7f) else Color(0xFFA855F7).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedLayer = if (isTxtSelected) SelectedLayerType.NONE else SelectedLayerType.TEXT_TXT
                                onOpenText()
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (hasTextOrSticker) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("TXT", color = Color(0xFFE9D5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Icon(
                                    imageVector = Icons.Default.TextFields,
                                    contentDescription = "Text",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                val label = textOverlays.firstOrNull()?.text ?: "Text Overlay"
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                if (allStickers.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        allStickers.take(4).forEach { sticker ->
                                            Text(text = sticker.symbolOrUri, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(14.dp))
                                Text(
                                    text = "+ Add Text or Stickers (TXT)",
                                    color = Color(0xFFD8B4FE),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (isTxtSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(width = 4.dp, height = 26.dp)
                                    .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(width = 4.dp, height = 26.dp)
                                    .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                            )
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    // Track 4: FX (Effects & Filters Track)
                    val hasFx = state.activeFxId != null || state.activeFilterId != null || state.chromaKeySettings.enabled
                    val isFxSelected = selectedLayer == SelectedLayerType.FX_LAYER

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFxSelected) Color(0xFF451A03) else if (hasFx) Color(0xFF78350F).copy(alpha = 0.85f) else Color(0xFF1E1408))
                            .border(
                                width = if (isFxSelected) 2.dp else 1.dp,
                                color = if (isFxSelected) Color(0xFFFFD700) else if (hasFx) Color(0xFFF59E0B).copy(alpha = 0.7f) else Color(0xFFF59E0B).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedLayer = if (isFxSelected) SelectedLayerType.NONE else SelectedLayerType.FX_LAYER
                                onOpenFx()
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (hasFx) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("FX", color = Color(0xFFFDE68A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "FX",
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(15.dp)
                                )
                                val fxLabel = when {
                                    state.activeFxId != null -> "FX: ${state.activeFxId!!.uppercase()}"
                                    state.activeFilterId != null -> "FILTER: ${state.activeFilterId!!.uppercase()}"
                                    else -> "CHROMA KEY (3D)"
                                }
                                Text(
                                    text = fxLabel,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.Black.copy(alpha = 0.4f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${(state.fxIntensity * 100).toInt()}%",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                                Text(
                                    text = "+ Add Visual FX & Filters (FX)",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (isFxSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(width = 4.dp, height = 26.dp)
                                    .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(width = 4.dp, height = 26.dp)
                                    .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                            )
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    // Track 5: A1 (Audio Track)
                    val audioTrack = state.project?.audioTracks?.firstOrNull()
                    val audioName = audioTrack?.name ?: "Background Music.mp3"
                    val audioDurSec = ((audioTrack?.trimEndMs ?: state.durationMs) / 1000L).coerceAtLeast(1L)
                    val isAudioSelected = selectedLayer == SelectedLayerType.AUDIO_A1

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    if (isAudioSelected) listOf(Color(0xFF065F46), Color(0xFF047857))
                                    else listOf(Color(0xFF047857), Color(0xFF059669))
                                )
                            )
                            .border(
                                width = if (isAudioSelected) 2.dp else 1.dp,
                                color = if (isAudioSelected) Color(0xFFFFD700) else Color(0xFF10B981).copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedLayer = if (isAudioSelected) SelectedLayerType.NONE else SelectedLayerType.AUDIO_A1
                                onOpenAudio()
                            }
                            .padding(horizontal = 8.dp),
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
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "A1",
                                        color = Color(0xFFA7F3D0),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Audio",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = audioName,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${audioDurSec}s",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }

                            // Precision Waveform Rendering with Dynamic Amplitude & Beat Sync
                            val waveform = state.audioWaveform
                            val barCount = 48
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                repeat(barCount) { i ->
                                    val barFrac = i.toFloat() / barCount.toFloat()
                                    val isPlayed = barFrac <= progress
                                    val sampleIdx = if (waveform.isNotEmpty()) (barFrac * (waveform.size - 1)).toInt() else 0
                                    val rawAmp = if (waveform.isNotEmpty() && sampleIdx in waveform.indices) {
                                        waveform[sampleIdx].coerceIn(0.12f, 1.0f)
                                    } else {
                                        when {
                                            i % 8 == 0 -> 0.95f
                                            i % 6 == 0 -> 0.8f
                                            i % 4 == 0 -> 0.65f
                                            i % 3 == 0 -> 0.45f
                                            i % 2 == 0 -> 0.35f
                                            else -> 0.2f
                                        }
                                    }
                                    val isBeat = state.snapToBeat && state.beatMarkersMs.any { beatMs ->
                                        val beatFrac = beatMs.toFloat() / durationMs.toFloat()
                                        kotlin.math.abs(beatFrac - barFrac) < (1.2f / barCount)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .height(28.dp * rawAmp)
                                            .background(
                                                color = when {
                                                    isBeat -> Color(0xFF00E5FF)
                                                    isPlayed -> Color.White
                                                    else -> Color(0xFF34D399).copy(alpha = 0.65f)
                                                },
                                                shape = RoundedCornerShape(1.dp)
                                            )
                                    )
                                }
                            }
                        }

                        if (isAudioSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(width = 4.dp, height = 28.dp)
                                    .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(width = 4.dp, height = 28.dp)
                                    .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }

                // --- UNIFIED VERTICAL PLAYHEAD LINE WITH TIMESTAMP BADGE & DYNAMIC PER-SECOND SCRUB THUMBNAIL ---
                val headX = (containerWidth.value * progress).dp
                Box(
                    modifier = Modifier
                        .offset(x = headX - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                )

                // Floating Active Scrub Thumbnail Card right above playhead badge
                if (activeScrubThumbnail != null) {
                    Box(
                        modifier = Modifier
                            .offset(x = (headX - 26.dp).coerceIn(0.dp, (containerWidth.value - 54).dp), y = 0.dp)
                            .size(52.dp, 30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                            .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                    ) {
                        Image(
                            bitmap = activeScrubThumbnail!!.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.8f))
                                .padding(vertical = 0.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = TimeFormat.msToShort(playheadMs),
                                color = Color(0xFF38BDF8),
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else {
                    // Timestamp Badge at the top of the playhead (Matches reference image, e.g. 00:15)
                    Box(
                        modifier = Modifier
                            .offset(x = (headX - 18.dp).coerceAtLeast(0.dp), y = 0.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = TimeFormat.msToShort(playheadMs),
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // --- 3. CONTEXTUAL INLINE LAYER EDITING TOOLBAR (Adapts on Layer Tap) ---
        if (selectedLayer != SelectedLayerType.NONE) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                // Layer Info & Deselect Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700))
                        )
                        Text(
                            text = "LAYER SELECTED: ${selectedLayer.name.replace('_', ' ')}",
                            color = Color(0xFFFFD700),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2A2A3C))
                            .clickable { selectedLayer = SelectedLayerType.NONE }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✕ Deselect",
                            color = Color(0xFFE5E7EB),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Layer-Specific Contextual Action Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (selectedLayer) {
                        SelectedLayerType.VIDEO_V1 -> {
                            QuickActionSquareCard(
                                icon = Icons.Default.ContentCut,
                                label = "Split",
                                onClick = { activeClip?.let { onSplitClip(it.id, playheadMs) } }
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.Tune,
                                label = "Trim",
                                onClick = onOpenTrim
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.Speed,
                                label = "Speed",
                                onClick = onOpenSpeed
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.VolumeUp,
                                label = "Volume",
                                onClick = onOpenAudio
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.AutoAwesome,
                                label = "Effects",
                                onClick = onOpenFx
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.Layers,
                                label = "Animation",
                                onClick = onOpenAnimation
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.ContentCopy,
                                label = "Duplicate",
                                onClick = { activeClip?.let { onDuplicateClip(it.id) } }
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.DeleteOutline,
                                label = "Delete",
                                tint = ApexPalette.NeonPink,
                                onClick = { activeClip?.let { onDeleteClip(it.id) } }
                            )
                        }

                        SelectedLayerType.OVERLAY_V2 -> {
                            QuickActionSquareCard(
                                icon = Icons.Default.ContentCut,
                                label = "Split",
                                onClick = { activeClip?.let { onSplitClip(it.id, playheadMs) } }
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.Layers,
                                label = "3D Chroma",
                                tint = Color(0xFF00E5FF),
                                onClick = onOpenChromaKey
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.AutoAwesome,
                                label = "Animation",
                                onClick = onOpenAnimation
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.AddPhotoAlternate,
                                label = "Replace",
                                onClick = onAddMedia
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.DeleteOutline,
                                label = "Delete",
                                tint = ApexPalette.NeonPink,
                                onClick = { activeClip?.let { onDeleteClip(it.id) } }
                            )
                        }

                        SelectedLayerType.TEXT_TXT -> {
                            QuickActionSquareCard(
                                icon = Icons.Default.TextFields,
                                label = "Edit Text",
                                tint = Color(0xFFA855F7),
                                onClick = onOpenText
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.Palette,
                                label = "Styles",
                                tint = Color(0xFFA855F7),
                                onClick = onOpenText
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.EmojiEmotions,
                                label = "Stickers",
                                tint = Color(0xFFEC4899),
                                onClick = onOpenStickers
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.ContentCopy,
                                label = "Duplicate",
                                onClick = { activeClip?.let { onDuplicateClip(it.id) } }
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.DeleteOutline,
                                label = "Delete",
                                tint = ApexPalette.NeonPink,
                                onClick = { activeClip?.let { onDeleteClip(it.id) } }
                            )
                        }

                        SelectedLayerType.FX_LAYER -> {
                            QuickActionSquareCard(
                                icon = Icons.Default.AutoAwesome,
                                label = "Visual FX",
                                tint = Color(0xFFF59E0B),
                                onClick = onOpenFx
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.FaceRetouchingNatural,
                                label = "AR Face",
                                tint = Color(0xFF10B981),
                                onClick = onOpenArFilters
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.Refresh,
                                label = "Reset FX",
                                onClick = { onSelectFx("") }
                            )
                        }

                        SelectedLayerType.AUDIO_A1 -> {
                            QuickActionSquareCard(
                                icon = Icons.Default.LibraryMusic,
                                label = "Audio Mix",
                                tint = Color(0xFF10B981),
                                onClick = onOpenAudio
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.MusicNote,
                                label = "Royalty Music",
                                tint = Color(0xFF34D399),
                                onClick = onOpenRoyaltyMusic
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.VolumeUp,
                                label = "Volume",
                                onClick = onOpenAudio
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.Mic,
                                label = "Record",
                                onClick = onOpenVoice
                            )
                            QuickActionSquareCard(
                                icon = Icons.Default.DeleteOutline,
                                label = "Delete",
                                tint = ApexPalette.NeonPink,
                                onClick = { activeClip?.let { onDeleteClip(it.id) } }
                            )
                        }

                        SelectedLayerType.NONE -> {}
                    }
                }
            }
        } else {
            // Default CapCut quick action toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionSquareCard(
                    icon = Icons.Default.ContentCut,
                    label = "Split",
                    onClick = { activeClip?.let { onSplitClip(it.id, playheadMs) } }
                )

                QuickActionSquareCard(
                    icon = Icons.Default.Tune,
                    label = "Trim",
                    onClick = onOpenTrim
                )

                QuickActionSquareCard(
                    icon = Icons.Default.Speed,
                    label = "Speed",
                    onClick = onOpenSpeed
                )

                QuickActionSquareCard(
                    icon = Icons.Default.VolumeUp,
                    label = "Volume",
                    onClick = onOpenAudio
                )

                QuickActionSquareCard(
                    icon = Icons.Default.Layers,
                    label = "Animation",
                    onClick = onOpenAnimation
                )

                QuickActionSquareCard(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete",
                    tint = ApexPalette.NeonPink,
                    onClick = { activeClip?.let { onDeleteClip(it.id) } }
                )
            }
        }
    }
}

@Composable
fun TimelineQuickActionBar(
    hasKeyframeAtPlayhead: Boolean,
    timelineZoom: Float,
    canSplit: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onToggleKeyframe: () -> Unit,
    onPrevKeyframe: () -> Unit,
    onNextKeyframe: () -> Unit,
    onSplit: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onOpenKeyframes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F0F1A))
            .border(1.dp, Color(0xFF232336), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Keyframe jump & toggle group
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Jump Prev Keyframe
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A1A28))
                    .clickable(onClick = onPrevKeyframe),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Prev Keyframe",
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Keyframe Diamond Add/Remove
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (hasKeyframeAtPlayhead) ApexPalette.NeonCyan.copy(alpha = 0.25f) else Color(0xFF222234))
                    .border(
                        1.dp,
                        if (hasKeyframeAtPlayhead) ApexPalette.NeonCyan else Color(0xFF383852),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(onClick = onToggleKeyframe)
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .graphicsLayer { rotationZ = 45f }
                            .background(if (hasKeyframeAtPlayhead) ApexPalette.NeonCyan else Color.White)
                    )
                    Text(
                        text = if (hasKeyframeAtPlayhead) "- KF" else "+ KF",
                        color = if (hasKeyframeAtPlayhead) ApexPalette.NeonCyan else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Jump Next Keyframe
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A1A28))
                    .clickable(onClick = onNextKeyframe),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Next Keyframe",
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Center: Split & Reorder buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Split button
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (canSplit) Color(0xFF242438) else Color(0xFF141420))
                    .border(1.dp, if (canSplit) Color(0xFF42425E) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable(enabled = canSplit, onClick = onSplit)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Split",
                        tint = if (canSplit) Color.White else Color(0xFF555566),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Split",
                        color = if (canSplit) Color.White else Color(0xFF555566),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (canMoveLeft) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1A1A28))
                        .clickable(onClick = onMoveLeft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Move Clip Left",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (canMoveRight) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1A1A28))
                        .clickable(onClick = onMoveRight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Move Clip Right",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Right: Timeline Zoom Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1A1A28))
                    .clickable(onClick = onZoomOut),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Zoom Out",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }

            Text(
                text = String.format(java.util.Locale.US, "%.1fx", timelineZoom),
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 3.dp)
            )

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1A1A28))
                    .clickable(onClick = onZoomIn),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Zoom In",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickActionSquareCard(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 50.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141420))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TrackSidebarCell(
    badge: String,
    label: String,
    icon: ImageVector,
    tint: Color,
    height: androidx.compose.ui.unit.Dp,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) tint.copy(alpha = 0.25f) else Color(0xFF141420))
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) tint else tint.copy(alpha = 0.45f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) tint else tint.copy(alpha = 0.25f))
                        .padding(horizontal = 2.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        text = badge,
                        color = if (isSelected) Color.Black else tint,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) tint else tint.copy(alpha = 0.9f),
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = label,
                color = if (isSelected) tint else Color.White.copy(alpha = 0.85f),
                fontSize = 7.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SpeedControlSheet(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    androidx.compose.material3.Surface(
        color = Color(0xFF14141E),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = ApexPalette.NeonCyan)
                    Text("Speed Control", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                androidx.compose.material3.IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Speed Presets Row
            val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 4.0f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                presets.forEach { speed ->
                    val isSelected = kotlin.math.abs(currentSpeed - speed) < 0.05f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) ApexPalette.NeonCyan else Color(0xFF222232))
                            .clickable { onSpeedChange(speed) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${speed}x",
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Custom Speed", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    Text(String.format(java.util.Locale.US, "%.2fx", currentSpeed), color = ApexPalette.NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.Slider(
                    value = currentSpeed,
                    onValueChange = { onSpeedChange(it) },
                    valueRange = 0.25f..8.0f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = ApexPalette.NeonCyan,
                        activeTrackColor = ApexPalette.NeonCyan,
                        inactiveTrackColor = Color(0xFF2A2A3C)
                    )
                )
            }
        }
    }
}

// === 5. BOTTOM EDIT TOOLBAR ===
@Composable
fun BottomEditToolbar(
    onEdit: () -> Unit = {},
    onAudio: () -> Unit = {},
    onText: () -> Unit = {},
    onStickers: () -> Unit = {},
    onEffects: () -> Unit = {},
    onFilters: () -> Unit = {},
    onArFilters: () -> Unit = {},
    onAdjust: () -> Unit = {}
) {
    val items = listOf(
        EditToolItem("Edit", Icons.Default.ContentCut, isActive = true, onClick = onEdit),
        EditToolItem("Audio", Icons.Default.MusicNote, onClick = onAudio),
        EditToolItem("Text", Icons.Default.TextFields, onClick = onText),
        EditToolItem("Stickers", Icons.Default.EmojiEmotions, onClick = onStickers),
        EditToolItem("Effects", Icons.Default.AutoAwesome, onClick = onEffects),
        EditToolItem("Filters", Icons.Default.FilterAlt, onClick = onFilters),
        EditToolItem("AR Face", Icons.Default.FaceRetouchingNatural, onClick = onArFilters),
        EditToolItem("Adjust", Icons.Default.Tune, onClick = onAdjust)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color(0xFF0C0C14))
            .border(width = 1.dp, color = Color(0xFF1F1F2E)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = item.onClick)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (item.isActive) Color(0xFF38BDF8) else Color(0xFF9CA3AF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.label,
                    color = if (item.isActive) Color(0xFF38BDF8) else Color(0xFF9CA3AF),
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
