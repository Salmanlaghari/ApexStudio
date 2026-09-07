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
import com.apexstudio.app.data.media.TimelineMediaCache
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
    val context = LocalContext.current
    CrashMarker.mark(context, "EditorScreen: composable start")
    val mediaPicker = remember { MediaPickerHelper(context) }
    val filterEngine = remember { LutFilterEngine(context) }
    // TimelineMediaCache is a process-lifetime LRU cache of per-clip
    // filmstrip frames (Video/OVERLAY clips) and waveform samples
    // (AUDIO/SFX clips). It runs MediaMetadataRetriever extractions on
    // Dispatchers.IO and publishes results through a StateFlow. The
    // Composable reads the flow via collectAsStateWithLifecycle so
    // frames appear progressively without blocking the timeline.
    // PR B (Filmstrip Thumbnails) wires this in to replace the
    // gradient-box placeholders in TimelineTrackArea.
    val timelineMediaCache = remember { TimelineMediaCache(context) }
    DisposableEffect(Unit) {
        onDispose { timelineMediaCache.release() }
    }
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

    // ----------------------------------------------------------------
    // LIVE VIDEO EFFECTS for the preview ExoPlayer.
    //
    // The ExoPlayer preview must render the same LUT / FX / Adjustments
    // the user has selected, otherwise the editor shows stale pixels and
    // only the export "sees" the effect.
    //
    // We install the real Media3 GlEffect chain on the player via
    // `setVideoEffects(...)`. Each effect's shader runs on every decoded
    // frame in the Media3 GL pipeline — which is the only place that can
    // touch the actual decoded video pixels, because PlayerView's
    // internal SurfaceView is composited by SurfaceFlinger and is
    // outside any Compose RenderEffect / graphicsLayer / draw modifier.
    //
    // Slider drag needs to be smooth without rebuilding the GL chain on
    // every event. We side-channel the per-frame values through
    // MutableStateFlow holders — the lambdas in the effect constructor
    // read the current holder value, so the per-frame uniform is always
    // the latest, but the effect itself is only rebuilt when the chain
    // composition actually changes (filter id, fx id, or any adjustment
    // crosses the "default" threshold).
    // ----------------------------------------------------------------
    val filterIntensityFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(state.filterIntensity) }
    val fxIntensityFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(state.fxIntensity) }
    val brightnessFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(state.adjustments.brightness) }
    val contrastFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(state.adjustments.contrast) }
    val saturationFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(state.adjustments.saturation) }
    val exposureFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(state.adjustments.exposure) }
    val temperatureFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(state.adjustments.temperature) }
    val tintFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(state.adjustments.tint) }
    val highlightsFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(state.adjustments.highlights) }
    val shadowsFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(state.adjustments.shadows) }

    // Sync the flows from state on every recomposition. Cheap (a
    // single .value = ... assignment each), and ensures the next
    // drawFrame picks up the new slider value without rebuilding
    // the effect chain.
    filterIntensityFlow.value = state.filterIntensity
    fxIntensityFlow.value = state.fxIntensity
    brightnessFlow.value = state.adjustments.brightness
    contrastFlow.value = state.adjustments.contrast
    saturationFlow.value = state.adjustments.saturation
    exposureFlow.value = state.adjustments.exposure
    temperatureFlow.value = state.adjustments.temperature
    tintFlow.value = state.adjustments.tint
    highlightsFlow.value = state.adjustments.highlights
    shadowsFlow.value = state.adjustments.shadows

    // Build (or rebuild) the effect chain only when the chain
    // composition changes — filter id, fx id, the boolean "any
    // adjustment non-default" flag, or the FX intensity (FxGlEffect
    // does not expose an intensityProvider, so the chain must be
    // rebuilt on every FX slider tick). Filter / Adjustment
    // intensities are routed through the flows above, NOT through
    // this LaunchedEffect, so dragging those sliders doesn't
    // rebuild the chain (and re-upload the LUT texture) 60×/s.
    LaunchedEffect(
        exoPlayer,
        state.activeFilterId,
        state.activeFxId,
        state.fxIntensity,
        state.adjustments.isDefault
    ) {
        val player = exoPlayer ?: return@LaunchedEffect
        val effects = mutableListOf<androidx.media3.common.Effect>()

        // 1. Adjustments (Brightness / Contrast / Saturation / etc.) —
        //    ColorMatrix-equivalent on the GPU. Always installed so the
        //    preview reacts to every adjustment slider in real time.
        if (!state.adjustments.isDefault) {
            effects.add(
                com.apexstudio.app.data.adjust.AdjustmentsGlEffect(
                    adjustments = state.adjustments,
                    brightnessProvider = { brightnessFlow.value },
                    contrastProvider = { contrastFlow.value },
                    saturationProvider = { saturationFlow.value },
                    exposureProvider = { exposureFlow.value },
                    temperatureProvider = { temperatureFlow.value },
                    tintProvider = { tintFlow.value },
                    highlightsProvider = { highlightsFlow.value },
                    shadowsProvider = { shadowsFlow.value }
                )
            )
        }

        // 2. 3D LUT filter. The GL effect accepts a null preset and
        //    uploads an identity LUT, which is a no-op on the frame.
        //    When a filter is selected we resolve the preset from the
        //    manifest and pass an intensityProvider so the slider
        //    drag is smooth.
        val activeFilterId = state.activeFilterId
        val filterPreset: com.apexstudio.app.data.filter.FilterPreset? =
            if (activeFilterId != null)
                filterEngine.manifest.presetById(activeFilterId)
            else null
        if (filterPreset != null && state.filterIntensity > 0f) {
            effects.add(
                com.apexstudio.app.data.filter.LutFilterGlEffect(
                    context = context,
                    preset = filterPreset,
                    intensity = state.filterIntensity.coerceIn(0f, 1f),
                    intensityProvider = { filterIntensityFlow.value.coerceIn(0f, 1f) }
                )
            )
        }

        // 3. Dynamic FX (Vignette, VHS, Glitch, Chromatic, Bloom, …).
        //    FxGlEffect requires a non-null preset, so only install
        //    when a valid id is active. FxGlEffect does not expose an
        //    intensityProvider today, so a slider drag does trigger a
        //    chain rebuild (acceptable for FX — at most ~60×/s, and
        //    FxGlEffect is cheap to construct: no GL texture upload,
        //    just a shader-program re-resolution on the next frame).
        val fxPreset = com.apexstudio.app.data.fx.FxPreset.byId(state.activeFxId)
        if (fxPreset != null && state.fxIntensity > 0f) {
            effects.add(
                com.apexstudio.app.data.fx.FxGlEffect(
                    preset = fxPreset,
                    intensity = fxIntensityFlow.value.coerceIn(0f, 1f)
                )
            )
        }

        try {
            player.setVideoEffects(effects)
        } catch (e: Exception) {
            Log.e("EditorScreen", "setVideoEffects failed", e)
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

    // Phase H: apply slow-motion speed to the ExoPlayer when the
    // slider is non-1.0. ExoPlayer supports any speed 0.1..2.0 via
    // setPlaybackSpeed; below 0.1 it falls back to step-frame on the
    // pre-recorded media which is the closest a normal player gets
    // to extreme slow-mo. We re-apply on every change so the slider
    // has live feedback.
    LaunchedEffect(exoPlayer, state.slowMotionSpeed, state.playbackDirection) {
        val player = exoPlayer ?: return@LaunchedEffect
        val direction = if (state.playbackDirection < 0) -1 else 1
        // ExoPlayer.setPlaybackSpeed accepts a positive value; for
        // reverse direction we simulate by re-seeking on every tick
        // (see the 33ms loop above + the per-frame reverse step in
        // the same effect). For forward direction we just apply the
        // slow-motion factor.
        if (direction > 0) {
            val speed = state.slowMotionSpeed.coerceIn(0.1f, 2.0f)
            player.setPlaybackSpeed(speed)
        } else {
            // Reverse preview: keep speed at 1.0 in the player; the
            // play loop (combined with playbackDirection = -1) seeks
            // backwards by 33ms per frame to simulate reverse.
            player.setPlaybackSpeed(1.0f)
        }
    }

    // Phase H: reverse-mode simulation. While playbackDirection = -1
    // and the user is "playing", we step the player backwards by 33ms
    // per frame instead of playing forwards. A real reverse is
    // export-only (TODO in ExportEngine).
    LaunchedEffect(exoPlayer, state.isPlaying, state.playbackDirection) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (state.playbackDirection >= 0 || !state.isPlaying) return@LaunchedEffect
        while (isActive) {
            if (player.isPlaying) {
                val newPos = (player.currentPosition - 33L).coerceAtLeast(0L)
                if (newPos <= 0L) {
                    player.pause()
                    vm.setPlaying(false)
                } else {
                    player.seekTo(newPos)
                    vm.setPlayerPosition(newPos)
                }
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
                    onSlowMotion = { vm.openSlowMotionPanel() },
                    onReverse = { vm.openReversePanel() },
                    onIntro = { vm.openIntroPanel() },
                    onOutro = { vm.openOutroPanel() },
                    onColorCombo = { vm.openColorComboPanel() },
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
            timelineMediaCache = timelineMediaCache,
            onScrub = { seekPlayerAndState(it) },
            onSelectClip = { vm.selectClipAndRefresh(it) },
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

    // Phase H: Slow-motion panel
    if (state.slowMotionPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeSlowMotionPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                SlowMotionPanel(
                    currentSpeed = state.slowMotionSpeed,
                    onSpeedChange = { vm.setSlowMotionSpeed(it) },
                    onClose = { vm.closeSlowMotionPanel() }
                )
            }
        }
    }

    // Phase H: Reverse panel
    if (state.reversePanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeReversePanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                ReversePanel(
                    direction = state.playbackDirection,
                    onToggle = {
                        vm.setPlaybackDirection(if (state.playbackDirection < 0) 1 else -1)
                    },
                    onClose = { vm.closeReversePanel() }
                )
            }
        }
    }

    // Phase H: Intro picker
    if (state.introPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeIntroPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                ClipPickerPanel(
                    title = "Set Intro",
                    introOrOutro = "intro",
                    selectedClipId = state.introClipId,
                    clips = state.project?.clips ?: emptyList(),
                    onSelect = { vm.setIntroClip(it) },
                    onClose = { vm.closeIntroPanel() }
                )
            }
        }
    }

    // Phase H: Outro picker
    if (state.outroPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeOutroPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                ClipPickerPanel(
                    title = "Set Outro",
                    introOrOutro = "outro",
                    selectedClipId = state.outroClipId,
                    clips = state.project?.clips ?: emptyList(),
                    onSelect = { vm.setOutroClip(it) },
                    onClose = { vm.closeOutroPanel() }
                )
            }
        }
    }

    // Phase H: Colour Combo panel
    if (state.colorComboPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeColorComboPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                ColorComboPanel(
                    activeComboId = state.activeColorComboId,
                    onApply = { vm.applyColourCombo(it) },
                    onClear = { vm.clearColorCombo() },
                    onClose = { vm.closeColorComboPanel() }
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
    activeFilterId: String? = null,
    filterIntensity: Float = 0f,
    adjustments: com.apexstudio.app.domain.model.VideoAdjustments = com.apexstudio.app.domain.model.VideoAdjustments(),
    playerError: String? = null,
    stickers: List<StickerOverlay> = emptyList(),
    activeFxId: String? = null,
    fxIntensity: Float = 0f,
    onRetryLoad: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // The live filter, FX, and adjustments are NOT applied here in
    // Compose. PlayerView's internal SurfaceView is composited by
    // SurfaceFlinger (a separate hardware overlay) and sits outside
    // Compose's graphicsLayer / RenderEffect pipeline, so any
    // Modifier.graphicsLayer { renderEffect = ... } on the wrapping
    // AndroidView can never reach the decoded video pixels — at
    // best it would tint sibling Compose composables (the original
    // "1080P badge" bug).
    //
    // The real-time render path is in EditorScreen itself: a
    // LaunchedEffect installs LutFilterGlEffect + FxGlEffect +
    // AdjustmentsGlEffect on the preview ExoPlayer via
    // `player.setVideoEffects(...)`, so the GL pipeline processes
    // every decoded frame and the pixels you see on screen match
    // what the export pipeline will bake into the MP4.
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
                    PlayerView(ctx).apply {
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

        // No top overlay on the video preview — the "1080P" badge,
        // fullscreen icon, and fx chip were removed in an earlier fix
        // because (a) they obscured the preview and (b) they used to be
        // the visible target of the broken Compose RenderEffect
        // graphicsLayer, which couldn't reach the PlayerView's actual
        // video pixels. The video preview is now a clean surface.
        // Resolution and fullscreen controls are still available via
        // PlaybackControlBar (and will be relocated to Settings later).
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
    onSlowMotion: () -> Unit = {},
    onReverse: () -> Unit = {},
    onIntro: () -> Unit = {},
    onOutro: () -> Unit = {},
    onColorCombo: () -> Unit = {},
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
        // Phase H: speed / direction / bookend / one-tap colour
        RailItem(Icons.Default.SlowMotionVideo, "Slow", onSlowMotion)
        RailItem(Icons.Default.FastForward, "Reverse", onReverse)
        RailItem(Icons.Default.Start, "Intro", onIntro)
        RailItem(Icons.Default.Stop, "Outro", onOutro)
        RailItem(Icons.Default.Palette, "Combo", onColorCombo)
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
    timelineMediaCache: com.apexstudio.app.data.media.TimelineMediaCache,
    onScrub: (Long) -> Unit = {},
    onSelectClip: (String?) -> Unit = {},
    onCover: () -> Unit = {},
    onAddMedia: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clips = state.project?.clips ?: emptyList()

    // Kick off filmstrip + waveform extractions for the current set of
    // clips. The cache is content-keyed on (uri, trim, frame width), so
    // a no-op when the inputs haven't changed. Returns the synchronous
    // snapshot so the first paint can show whatever frames are already
    // cached; the StateFlow below provides updates as new extractions
    // finish.
    //
    // pxPerMs is derived from the editor's zoom level so a user who
    // zooms in to inspect a region gets sharper per-second frames,
    // matching the playback speed they see in the timeline ruler.
    val pxPerMs = state.zoomLevel.coerceAtLeast(0.1f) * 0.12f
    timelineMediaCache.observe(clips, pxPerMs)

    // Subscribe to the cache so that newly-extracted frames re-render
    // the filmstrip automatically. collectAsStateWithLifecycle is
    // lifecycle-aware so we don't keep observing when the screen is
    // off-screen.
    val cacheMap by timelineMediaCache.state.collectAsStateWithLifecycle()

    val firstVideoClip = clips.firstOrNull {
        it.type == ClipType.VIDEO || it.type == ClipType.OVERLAY
    }
    val firstVideoMedia = firstVideoClip?.let { cacheMap[it.id] }

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

            // Main Video Filmstrip Container.
            //
            // PR B (Filmstrip Thumbnails): the previous gradient
            // placeholder boxes were a "dummy UI" — they did not show
            // any real video content. We now read the per-clip
            // [ClipMedia] from the TimelineMediaCache and render the
            // extracted Bitmaps via [Image]. The cache runs
            // MediaMetadataRetriever on Dispatchers.IO, content-keys
            // results by (uri, trim, frame width), and evicts on a
            // 64MB LRU ceiling.
            //
            // Honest fallback (no fake render): when there is no clip,
            // no media entry yet, or the cache returned an empty
            // frames list (extraction still in progress OR the source
            // couldn't be decoded), we draw a single thin progress
            // line + the literal text "Loading thumbnails…". This
            // matches the Honesty rule (no fake UI success state).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1B2E))
                    .border(1.5.dp, Color(0xFF8B5CF6), RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                val frames = firstVideoMedia?.frames.orEmpty()
                if (frames.isNotEmpty()) {
                    // Real filmstrip: each Bitmap is one second of
                    // the clip. We tile them with weight(1f) so they
                    // share the available width equally — the user
                    // sees the same number of cells regardless of how
                    // wide the viewport is.
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        frames.forEach { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                    }
                } else {
                    // Honest loading state. We deliberately do NOT
                    // render the previous purple gradient here — that
                    // was a fake "this looks like content" placeholder
                    // and violated the no-dummy-UI rule.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Thin progress line: a single 2dp tall bar that
                        // grows with the cache's current extraction
                        // progress. We can't easily get per-clip
                        // progress out of MediaMetadataRetriever, so
                        // this is a passive "we know the cache is
                        // working" pulse driven by a Compose state.
                        FilmstripLoadingBar(isActive = firstVideoClip != null)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (firstVideoClip == null) "No clip selected" else "Loading thumbnails…",
                            color = Color(0xFF9CA3AF),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
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

        val textClip = clips.firstOrNull { it.textOverlays.isNotEmpty() }
        val textLabel = textClip?.textOverlays?.firstOrNull()?.text ?: "ApexStudio  Pro Video Editor"

        val activeFxId = state.activeFxId
        val fxLabel = if (activeFxId != null) activeFxId.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } else "Cinematic Glow"

        val audioClip = clips.firstOrNull { it.type == ClipType.AUDIO || it.type == ClipType.SFX }
        val audioLabel = audioClip?.name ?: "Dreamscape"
        // PR B: the audio track row now reads the real waveform
        // samples from the cache (or the state-level audioWaveform as
        // a fallback for the legacy decode path). If neither is
        // available, we render the same "Loading…" text we use for
        // the video filmstrip.
        val audioWaveform: FloatArray = audioClip?.let { cacheMap[it.id]?.waveform }
            ?.takeIf { it.isNotEmpty() }
            ?: state.audioWaveform

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
            isWaveform = true,
            waveform = audioWaveform
        )

        TrackLayerRow(
            barColor = Color(0xFF7C3AED),
            icon = Icons.Default.Mic,
            label = voiceLabel,
            isWaveform = true,
            // Voice over row reads from the same waveform cache. If
            // no voice clip exists, pass an empty array so the row
            // falls through to the legacy static bars (clearly
            // distinguishable from a real waveform because no
            // amplitude variation is shown).
            waveform = voiceClip?.let { cacheMap[it.id]?.waveform }
                ?.takeIf { it.isNotEmpty() }
                ?: FloatArray(0)
        )

        Divider(color = Color(0xFF1F1F2E), thickness = 1.dp, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Small "pulse" loading indicator for the filmstrip row. Shows a
 * thin horizontal bar whose brightness animates 0.4..1.0 so the user
 * knows the cache is working. Deliberately tiny (2dp tall, 30% wide)
 * so it doesn't visually compete with the eventual filmstrip.
 */
@Composable
private fun FilmstripLoadingBar(isActive: Boolean) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isActive) 1f else 0.4f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "filmstrip-loading-pulse"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(0.3f)
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(Color(0xFF8B5CF6).copy(alpha = alpha * 0.7f))
    )
}

@Composable
private fun TrackLayerRow(
    barColor: Color,
    icon: ImageVector,
    label: String,
    badgeText: String? = null,
    isWaveform: Boolean = false,
    waveform: FloatArray = FloatArray(0)
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
                    // PR B: when a real waveform is available, render
                    // each sample as a vertical bar with height
                    // proportional to the sample amplitude (0..1). If
                    // no waveform data has been extracted yet, fall
                    // back to a small "Loading…" label — same honest
                    // fallback as the filmstrip row, no fake bars.
                    if (waveform.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            // Cap the rendered bar count to the row
                            // width so we don't draw more bars than
                            // fit visually. MediaMetadataRetriever
                            // returns ~200 samples by default; the
                            // timeline is ~280dp wide, so 32 bars at
                            // ~8dp each is the natural density.
                            val maxBars = 48
                            val stride = (waveform.size / maxBars).coerceAtLeast(1)
                            val indices = (0 until waveform.size step stride)
                                .take(maxBars)
                            indices.forEach { i ->
                                val amp = waveform[i].coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height((2f + 32f * amp).dp)
                                        .background(
                                            Color.White.copy(alpha = 0.85f),
                                            RoundedCornerShape(1.dp)
                                        )
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Loading…",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
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

// ====================================================================
// Phase H: Slow-Motion / Reverse / Intro / Outro / Colour Combo panels
// ====================================================================

@Composable
private fun SlowMotionPanel(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Slow Motion", color = ApexPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = ApexPalette.TextSecondary,
                modifier = Modifier.size(20.dp).clickable(onClick = onClose)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Speed: ${"%.2f".format(currentSpeed)}x",
            color = ApexPalette.NeonCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        androidx.compose.material3.Slider(
            value = currentSpeed,
            onValueChange = onSpeedChange,
            valueRange = 0.1f..2.0f,
            steps = 38 // 0.05 increments across 1.9 range
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0.1x", color = ApexPalette.TextTertiary, fontSize = 10.sp)
            Text("1.0x", color = ApexPalette.TextTertiary, fontSize = 10.sp)
            Text("2.0x", color = ApexPalette.TextTertiary, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0.25f, 0.5f, 1.0f).forEach { preset ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.BgElevated)
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                        .clickable { onSpeedChange(preset) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${preset}x", color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ReversePanel(
    direction: Int,
    onToggle: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Reverse Playback", color = ApexPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = ApexPalette.TextSecondary,
                modifier = Modifier.size(20.dp).clickable(onClick = onClose)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Preview reverses by stepping the playhead 33ms per frame. " +
                    "Final export uses true reverse-frame rendering (see TODO in ExportEngine).",
            color = ApexPalette.TextSecondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (direction < 0) ApexPalette.NeonCyan.copy(alpha = 0.18f) else ApexPalette.BgElevated)
                .border(
                    1.dp,
                    if (direction < 0) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                    RoundedCornerShape(12.dp)
                )
                .clickable { onToggle() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (direction < 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                    contentDescription = null,
                    tint = if (direction < 0) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (direction < 0) "Reverse ON" else "Reverse OFF",
                    color = if (direction < 0) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ClipPickerPanel(
    title: String,
    introOrOutro: String, // "intro" or "outro"
    selectedClipId: String?,
    clips: List<com.apexstudio.app.domain.model.MediaClip>,
    onSelect: (String?) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = ApexPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = ApexPalette.TextSecondary,
                modifier = Modifier.size(20.dp).clickable(onClick = onClose)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Plays this clip at the ${introOrOutro} of the project.",
            color = ApexPalette.TextSecondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                .clickable { onSelect(null) }
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No ${introOrOutro}", color = ApexPalette.TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        clips.forEach { clip ->
            val isSelected = clip.id == selectedClipId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.18f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) ApexPalette.NeonCyan else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(clip.id) }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (introOrOutro == "intro") Icons.Default.PlayArrow else Icons.Default.Stop,
                        contentDescription = null,
                        tint = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        clip.name,
                        color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorComboPanel(
    activeComboId: String?,
    onApply: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val combos = com.apexstudio.app.data.preset.ColourComboPreset.BUILTIN
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Colour Combo", color = ApexPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = ApexPalette.TextSecondary,
                modifier = Modifier.size(20.dp).clickable(onClick = onClose)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "One-tap filter + adjustments + FX. Live preview.",
            color = ApexPalette.TextSecondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        combos.forEach { combo ->
            val isActive = combo.id == activeComboId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) combo.previewAccentArgb.toColor().copy(alpha = 0.18f) else ApexPalette.BgElevated)
                    .border(
                        1.dp,
                        if (isActive) combo.previewAccentArgb.toColor() else ApexPalette.BorderGlass,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onApply(combo.id) }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(combo.previewAccentArgb.toColor())
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            combo.name,
                            color = if (isActive) combo.previewAccentArgb.toColor() else ApexPalette.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            combo.description,
                            color = ApexPalette.TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
        if (activeComboId != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexPalette.BgElevated)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                    .clickable(onClick = onClear)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Clear combo", color = ApexPalette.NeonPink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun Long.toColor(): Color {
    val r = ((this shr 16) and 0xFF).toInt()
    val g = ((this shr 8) and 0xFF).toInt()
    val b = (this and 0xFF).toInt()
    val a = ((this shr 24) and 0xFF).toInt()
    return Color(r, g, b, a)
}
