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
    // Dedicated muted player for the live video PiP overlay preview.
    // Bound to the active V2 video overlay clip by EditorPreviewSection.
    var overlayPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
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
            overlayPlayer?.release()
            overlayPlayer = null
        }
    }

    // Overlay PiP player: muted, no controller — the preview section binds
    // it to the active video overlay clip (trimmed range, looping).
    LaunchedEffect(Unit) {
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
        val cid = state.selectedClipId
        if (cid != null) state.project?.clips?.firstOrNull { it.id == cid }
        else state.project?.clips?.firstOrNull()
    }
    val currentClipUri = currentSelectedClip?.uri

    LaunchedEffect(exoPlayer, state.selectedClipId, currentClipUri) {
        val player = exoPlayer ?: return@LaunchedEffect
        val clip = currentSelectedClip ?: return@LaunchedEffect
        // Still-image clips are rendered by PhotoClipPreview (Compose),
        // not ExoPlayer — feeding a JPEG to the player would just error.
        if (clip.type == ClipType.IMAGE) {
            try {
                player.stop()
                player.clearMediaItems()
            } catch (_: Exception) {}
            vm.setPlayerReady(true)
            vm.setPlayerDuration(clip.durationMs)
            return@LaunchedEffect
        }
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
        state.fxSpeed,
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
                        activeFx, state.fxIntensity, state.fxSpeed
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

        EditorPreviewSection(
            state = state,
            vm = vm,
            exoPlayer = exoPlayer,
            isCoverMode = isCoverMode,
            currentSelectedClip = currentSelectedClip,
            seekPlayerAndState = seekPlayerAndState,
            onOpenAddMediaMenu = { showAddMediaMenu = true },
            overlayPlayer = overlayPlayer
        )

        EditorTransportSection(
            state = state,
            vm = vm,
            seekPlayerAndState = seekPlayerAndState,
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
                    mediaPicker.pickAudioMedia.launch("audio/*")
                } else {
                    vm.setPendingAddAsOverlay(false)
                    vm.setPendingAddAsAudio(false)
                    showAddMediaMenu = true
                }
            }
        )

        // New contextual bottom toolbar — extracted to EditorBottomToolbarSection
        // to keep this composable under the JVM 64KB method limit.
        EditorBottomToolbarSection(
            state = state,
            vm = vm,
            mediaPicker = mediaPicker
        )
    }

    // Modal overlays — each extracted to its own composable (see
    // EditorScreenOverlays.kt) to keep EditorScreen under the JVM 64KB
    // method limit. Pure extraction, behavior unchanged.
    TrimPanelOverlay(state = state, vm = vm, exoPlayer = exoPlayer, onExport = onExport)
    GpuFilterPanelOverlay(state = state, vm = vm, filterEngine = filterEngine)
    ColorGradingPanelOverlay(state = state, vm = vm, filterEngine = filterEngine)
    AdjustmentsPanelOverlay(state = state, vm = vm)

    // Photo-editing sheet (PR F): only for the selected IMAGE clip.
    val photoEditClip = currentSelectedClip?.takeIf { it.type == ClipType.IMAGE }
    if (state.photoEditPanelOpen && photoEditClip != null) {
        PhotoEditSheet(
            photoEditClip = photoEditClip,
            tab = state.photoEditTab,
            vm = vm
        )
    }

    FxPanelOverlay(state = state, vm = vm)
    TransmissionTemplatesOverlay(state = state, vm = vm, templates = transmissionTemplates)
    TransitionPickerOverlay(state = state, vm = vm)
    TextPanelOverlay(state = state, vm = vm)
    AudioMixerPanelOverlay(
        state = state,
        audioState = audioState,
        vm = vm,
        audioPickerLauncher = audioPickerLauncher,
        onOpenRoyaltyFreeSheet = { showRoyaltyFreeSheet = true }
    )

    // Volume + Fade sheets — extracted to overlay composables to keep this
    // composable under the JVM 64KB method limit.
    ClipVolumeSheetOverlay(state = state, vm = vm)
    AudioFadeSheetOverlay(state = state, vm = vm)

    RoyaltyMusicSheetOverlay(
        showRoyaltyFreeSheet = showRoyaltyFreeSheet,
        onCloseRoyaltyFreeSheet = { showRoyaltyFreeSheet = false },
        state = state,
        vm = vm,
        audioPickerLauncher = audioPickerLauncher
    )
    SpeedPanelOverlay(state = state, vm = vm)
    StickerPanelOverlay(state = state, vm = vm)
    VoiceRecorderOverlay(state = state, vm = vm)
    CameraCaptureOverlay(state = state, vm = vm)
    ArFilterPanelOverlay(state = state, vm = vm)
    LensesPanelOverlay(state = state, vm = vm)
    CoverPanelOverlay(state = state, vm = vm, mediaPicker = mediaPicker)
    ChromaKeyPanelOverlay(state = state, vm = vm, mediaPicker = mediaPicker)
    HelpDialogOverlay(state = state, vm = vm)
    KeyframePanelOverlay(state = state, vm = vm)
    AddMediaMenuSheetOverlay(
        show = showAddMediaMenu,
        vm = vm,
        mediaPicker = mediaPicker,
        onDismiss = { showAddMediaMenu = false }
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
                onResetAll = { vm.resetPhotoEdits(photoEditClip.id) },
                onClose = { vm.closePhotoEditPanel() }
            )
        }
    }
}
