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
            val speed = state.playbackSpeed.coerceIn(0.25f, 4.0f)
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
                ?: state.project?.clips?.firstOrNull()
            val stickers = (state.project?.stickers ?: emptyList()) + (selectedClip?.stickers ?: emptyList())
            val textOverlays = selectedClip?.textOverlays ?: emptyList()

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
                isPlaying = state.isPlaying,
                textOverlays = textOverlays,
                selectedTextOverlayId = state.selectedTextOverlayId,
                currentTimeMs = state.currentTimeMs,
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
                    onAudio = { vm.openAudioMixer() },
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

        TimelineTrackArea(
            state = state,
            onScrub = { seekPlayerAndState(it) },
            onSelectClip = { vm.selectClip(it) },
            onCover = { vm.openCoverPanel() },
            onAddMedia = { showAddMediaMenu = true },
            onSplitClip = { clipId, atMs -> vm.splitClip(clipId, atMs) },
            onDuplicateClip = { clipId -> vm.duplicateClip(clipId) },
            onDeleteClip = { clipId -> vm.deleteClip(clipId) },
            onOpenSpeed = { vm.openSpeedPanel() },
            onOpenAudio = { vm.openAudioMixer() },
            onOpenTrim = { vm.openTrimPanel() },
            onOpenText = { vm.openTextPanel() },
            onOpenFx = { vm.openFxPanel() },
            onOpenVoice = { vm.openVoiceRecorder() },
            onOpenAnimation = { vm.setKeyframePanelOpen(true) },
            onOpenTransition = { vm.openTransmissionTemplatesPanel() },
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

    if (state.speedPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeSpeedPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                SpeedControlSheet(
                    currentSpeed = state.playbackSpeed,
                    onSpeedChange = { vm.setPlaybackSpeed(it) },
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

        // Right side: Pro badge + 1080P dropdown + Export button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pro Crown Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF231B38))
                    .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onHelp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "Pro",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Pro",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // 1080P Resolution Tag
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1B1B26))
                    .clickable(onClick = onExport)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "1080P",
                        color = Color(0xFFD1D5DB),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Quality",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(14.dp)
                    )
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
    resolution: String = "1080P",
    activeFilterId: String? = null,
    filterIntensity: Float = 0f,
    adjustments: com.apexstudio.app.domain.model.VideoAdjustments = com.apexstudio.app.domain.model.VideoAdjustments(),
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
    onSelectResolution: (String) -> Unit = {},
    onFullscreenToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showResolutionDropdown by remember { mutableStateOf(false) }
    var isCoverMode by remember { mutableStateOf(true) }
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
            .border(1.dp, Color(0xFF1F1F2E), RoundedCornerShape(16.dp)),
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

        // Live Visual FX Overlay (VHS, Glitch, Scanlines, Grain, Light Leaks, Bloom, etc.)
        if (activeFxId != null && fxIntensity > 0f) {
            FxPreviewOverlay(
                fxId = activeFxId,
                intensity = fxIntensity,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize()
            )
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

        // Interactive Animated Text Overlays
        if (textOverlays.isNotEmpty()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val containerW = maxWidth
                val containerH = maxHeight

                for (overlay in textOverlays) {
                    val isSelected = overlay.id == selectedTextOverlayId
                    AnimatedTextOverlayView(
                        overlay = overlay,
                        isSelected = isSelected,
                        containerWidth = containerW,
                        containerHeight = containerH,
                        currentTimeMs = currentTimeMs,
                        isPlaying = isPlaying,
                        onSelect = { onSelectTextOverlay?.invoke(overlay.id) },
                        onPositionChange = { newX, newY ->
                            val dx = newX - overlay.x
                            val dy = newY - overlay.y
                            onMoveTextOverlay?.invoke(overlay.id, dx, dy)
                        },
                        onSizeScaleChange = { scale ->
                            onSizeScaleChange?.invoke(overlay.id, scale)
                        },
                        onEditText = {
                            onEditTextOverlay?.invoke(overlay.id)
                        },
                        onDelete = {
                            onDeleteTextOverlay?.invoke(overlay.id)
                        },
                        onDuplicate = {
                            onDuplicateTextOverlay?.invoke(overlay.id)
                        }
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

        // Top-Left Pill: Resolution Dropdown
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

        // Top-Right: Active filter / FX status indicator chip, Fit/Cover mode & Fullscreen toggle
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Fit / Cover Mode Toggle Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { isCoverMode = !isCoverMode }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isCoverMode) "COVER" else "FIT",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (activeFilterId != null || activeFxId != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (activeFxId != null) "FX: ${activeFxId.uppercase()}" else "FILTER",
                        color = Color(0xFF8B5CF6),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Box(
                modifier = Modifier
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

// === 4. CAPCUT-STYLE TIMELINE TRACK AREA ===
@Composable
fun TimelineTrackArea(
    state: com.apexstudio.app.presentation.state.EditorState,
    onScrub: (Long) -> Unit = {},
    onSelectClip: (String?) -> Unit = {},
    onCover: () -> Unit = {},
    onAddMedia: () -> Unit = {},
    onSplitClip: (clipId: String, atMs: Long) -> Unit = { _, _ -> },
    onDuplicateClip: (clipId: String) -> Unit = {},
    onDeleteClip: (clipId: String) -> Unit = {},
    onOpenSpeed: () -> Unit = {},
    onOpenAudio: () -> Unit = {},
    onOpenTrim: () -> Unit = {},
    onOpenText: () -> Unit = {},
    onOpenFx: () -> Unit = {},
    onOpenVoice: () -> Unit = {},
    onOpenAnimation: () -> Unit = {},
    onOpenTransition: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clips = state.project?.clips ?: emptyList()
    val activeClip = clips.firstOrNull { it.id == state.selectedClipId } ?: clips.firstOrNull()
    val durationMs = state.durationMs.coerceAtLeast(1000L)
    val playheadMs = state.playerPositionMs.coerceIn(0L, durationMs)

    // Real extracted video frames for filmstrip
    var extractedThumbnails by remember(activeClip?.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(activeClip?.id, activeClip?.uri, activeClip?.trimStartMs, activeClip?.trimEndMs) {
        val uri = activeClip?.uri
        if (uri != null) {
            try {
                val trimStart = activeClip.trimStartMs
                val trimEnd = if (activeClip.trimEndMs > trimStart) activeClip.trimEndMs else (activeClip.durationMs).coerceAtLeast(1000L)
                val frames = ThumbnailExtractor.extractFrames(
                    context = context,
                    uri = uri,
                    trimStartMs = trimStart,
                    trimEndMs = trimEnd,
                    frameWidthPx = 120,
                    frameHeightPx = 80,
                    frameCount = 6
                )
                if (frames.isNotEmpty()) {
                    extractedThumbnails = frames
                }
            } catch (e: Exception) {
                Log.w("TimelineTrackArea", "Thumbnail extraction fallback: ${e.message}")
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
            // --- 1. LEFT TRACK TYPE ICON SIDEBAR (Matches Reference Image) ---
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF0C0C14))
                    .border(width = 1.dp, color = Color(0xFF1B1B28)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top header / ruler height spacer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = Color(0xFF4B5563),
                        modifier = Modifier.size(12.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Video Track Icon
                TrackSidebarIcon(
                    icon = Icons.Default.Movie,
                    contentDesc = "Video Track",
                    tint = Color.White,
                    onClick = { activeClip?.let { onSelectClip(it.id) } }
                )

                Spacer(Modifier.height(8.dp))

                // Text Track Icon
                TrackSidebarIcon(
                    icon = Icons.Default.TextFields,
                    contentDesc = "Text Track",
                    tint = Color(0xFFA78BFA),
                    onClick = onOpenText
                )

                Spacer(Modifier.height(8.dp))

                // Effects Track Icon
                TrackSidebarIcon(
                    icon = Icons.Default.AutoAwesome,
                    contentDesc = "Effects Track",
                    tint = Color(0xFF60A5FA),
                    onClick = onOpenFx
                )

                Spacer(Modifier.height(8.dp))

                // Audio Track Icon
                TrackSidebarIcon(
                    icon = Icons.Default.MusicNote,
                    contentDesc = "Audio Track",
                    tint = Color(0xFF34D399),
                    onClick = onOpenAudio
                )
            }

            // --- 2. HORIZONTAL TRACK AREA WITH UNIFIED PLAYHEAD ---
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF0A0A10))
            ) {
                val containerWidth = maxWidth
                val progress = (playheadMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

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

                    Spacer(Modifier.height(4.dp))

                    // Track 1: Video Filmstrip with Transition buttons between clips + Add button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cover button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E1E2C))
                                .clickable(onClick = onCover)
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cover", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.width(6.dp))

                        // Clips Filmstrip Row
                        val displayClips = if (clips.isNotEmpty()) clips else listOf(
                            MediaClip(
                                id = "clip_default",
                                name = "Main Video",
                                uri = "",
                                durationMs = durationMs,
                                trimStartMs = 0L,
                                trimEndMs = durationMs
                            )
                        )

                        displayClips.forEachIndexed { index, clip ->
                            val isSelected = clip.id == activeClip?.id
                            // Video Clip Filmstrip Box
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A1A28))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF2A2A3C),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectClip(clip.id) }
                                    .padding(2.dp)
                            ) {
                                if (extractedThumbnails.isNotEmpty() && isSelected) {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        extractedThumbnails.forEach { bmp ->
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .padding(horizontal = 0.5.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                            )
                                        }
                                    }
                                } else {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        repeat(5) { idx ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .padding(horizontal = 0.5.dp)
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(
                                                                Color(0xFF1E1B4B + idx * 0x040306),
                                                                Color(0xFF311042 + idx * 0x030205)
                                                            )
                                                        ),
                                                        RoundedCornerShape(3.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Movie,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.2f),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
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

                                // Trim Handles when selected
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .width(4.dp)
                                            .fillMaxHeight(0.6f)
                                            .background(Color.White, RoundedCornerShape(2.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(4.dp)
                                            .fillMaxHeight(0.6f)
                                            .background(Color.White, RoundedCornerShape(2.dp))
                                    )
                                }
                            }

                            // Transition Button between adjacent clips [ ⟐ ]
                            if (index < displayClips.lastIndex) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
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
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        // Add Media "+" Button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(onClick = onAddMedia),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Media",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Track 2: Text Pill Track (Matches purple pill in reference image)
                    val textOverlays = activeClip?.textOverlays ?: emptyList()
                    val textLabel = textOverlays.firstOrNull()?.text ?: "Create Without Limits"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.85f))
                            .clickable(onClick = onOpenText)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = "Text",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = textLabel,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Track 3: Effects / FX Pill Track (Matches blue pill in reference image)
                    val fxName = state.activeFxId?.replace('_', ' ')?.capitalize()
                        ?: state.activeFilterId?.replace('_', ' ')?.capitalize()
                        ?: "Color Grading / Glow"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3B82F6).copy(alpha = 0.85f))
                            .clickable(onClick = onOpenFx)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Effect",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = fxName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Track 4: Audio Track with Real Waveform (Matches green pill in reference image)
                    val audioName = state.project?.audioTracks?.firstOrNull()?.name ?: "Background Music.mp3"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.85f))
                            .clickable(onClick = onOpenAudio)
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
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Audio",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = audioName,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            // Real Visual Waveform Bars inside Audio Pill
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                repeat(28) { i ->
                                    val hFrac = if (i % 5 == 0) 0.85f else if (i % 3 == 0) 0.6f else if (i % 2 == 0) 0.4f else 0.25f
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(24.dp * hFrac)
                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(1.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                // --- UNIFIED VERTICAL PLAYHEAD LINE WITH TIMESTAMP BADGE ---
                val headX = (containerWidth.value * progress).dp
                Box(
                    modifier = Modifier
                        .offset(x = headX - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                )

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

        Spacer(Modifier.height(4.dp))

        // --- 3. CAPCUT QUICK ACTION TOOLBAR (Row of 6 square cards from reference image) ---
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
private fun TrackSidebarIcon(
    icon: ImageVector,
    contentDesc: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF161624))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
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

// === 5. BOTTOM EDIT TOOLBAR (Matches 7 tabs in reference image) ===
@Composable
fun BottomEditToolbar(
    onEdit: () -> Unit = {},
    onAudio: () -> Unit = {},
    onText: () -> Unit = {},
    onStickers: () -> Unit = {},
    onEffects: () -> Unit = {},
    onFilters: () -> Unit = {},
    onAdjust: () -> Unit = {}
) {
    val items = listOf(
        EditToolItem("Edit", Icons.Default.ContentCut, isActive = true, onClick = onEdit),
        EditToolItem("Audio", Icons.Default.MusicNote, onClick = onAudio),
        EditToolItem("Text", Icons.Default.TextFields, onClick = onText),
        EditToolItem("Stickers", Icons.Default.EmojiEmotions, onClick = onStickers),
        EditToolItem("Effects", Icons.Default.AutoAwesome, onClick = onEffects),
        EditToolItem("Filters", Icons.Default.FilterAlt, onClick = onFilters),
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
