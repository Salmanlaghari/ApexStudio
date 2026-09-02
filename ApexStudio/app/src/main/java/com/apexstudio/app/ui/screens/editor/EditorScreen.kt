package com.apexstudio.app.ui.screens.editor

import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.apexstudio.app.data.crashlog.CrashMarker
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.media.ClipMedia
import com.apexstudio.app.data.media.TimelineMediaCache
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.components.NeonIconButton
import com.apexstudio.app.ui.components.RealAudioWaveform
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    // Filter engine: reads the 70+ .cube LUTs and the filter_manifest.json
    // from assets. Created once per EditorScreen entry.
    val filterEngine = remember { LutFilterEngine(context) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    mediaPicker.registerLaunchers()

    // Collect picks. We combine the metadata list with a monotonic
    // generation counter so re-picking the SAME file still triggers
    // a re-emit (StateFlow conflates equal List values otherwise).
    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.combine(
            mediaPicker.pickedMedia,
            mediaPicker.pickGeneration
        ) { meta, gen -> meta to gen }
            .collect { (metadataList, _) ->
                if (metadataList.isNotEmpty()) {
                    // The + button always APPENDS the freshly picked media
                    // to the V1 timeline, so users can stack multiple clips
                    // sequentially. The mockup shows V1 with several
                    // thumbnail strips back-to-back.
                    vm.onMediaPicked(metadataList, replace = false)
                }
            }
    }

    // Build the player for audio/playback + video preview. Wrapped in try/catch
    // so a codec/init failure degrades gracefully instead of taking down the
    // process. The PlayerView is shown as soon as the player exists — ExoPlayer
    // handles its own surface lifecycle internally, so we don't need to gate
    // on STATE_READY (which made the previous attempt never reach the preview).
    LaunchedEffect(Unit) {
        try {
            Log.d("ApexTrace", "EditorScreen: building ExoPlayer")
            CrashMarker.mark(context, "EditorScreen: ExoPlayer.Builder.build()")
            val player = ExoPlayer.Builder(context).build()
            Log.d("ApexTrace", "EditorScreen: ExoPlayer built")
            CrashMarker.mark(context, "EditorScreen: ExoPlayer built")
            // Track readiness + video size in the ViewModel so the UI can
            // react. The video size is what lets the preview container pick
            // the right aspect ratio (16:9 vs 9:16 vs 1:1) instead of
            // letterboxing every clip into a 16:9 frame.
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val ready = playbackState == Player.STATE_READY
                    Log.d("ApexTrace", "EditorScreen: onPlaybackStateChanged=$playbackState ready=$ready")
                    vm.setPlayerReady(ready)
                    // When the video finishes playing, ExoPlayer parks at
                    // STATE_ENDED. The app's own isPlaying flag never
                    // flipped, so the play button kept showing a "Pause"
                    // icon and tapping it called play() on a player that
                    // was already at the end — which is a no-op. Flip
                    // isPlaying off here so the UI shows a fresh "Play"
                    // icon, and the play effect below will seekTo(0)
                    // before play() to actually restart from frame zero.
                    if (playbackState == Player.STATE_ENDED) {
                        vm.setPlaying(false)
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("EditorScreen", "Player error: ${error.errorCodeName}", error)
                    vm.setPlayerReady(false)
                }
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    Log.d("ApexTrace", "EditorScreen: onVideoSizeChanged=${videoSize.width}x${videoSize.height}")
                    vm.setVideoSize(videoSize.width, videoSize.height)
                }
            })
            // If the player is already in a terminal state (unlikely but safe),
            // sync the flag immediately.
            if (player.playbackState == Player.STATE_READY) {
                vm.setPlayerReady(true)
            }
            exoPlayer = player
        } catch (e: Exception) {
            Log.e("EditorScreen", "ExoPlayer build failed", e)
            CrashMarker.clear(context)
            exoPlayer = null
        } finally {
            CrashMarker.clear(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // Safety net: if STATE_READY never fires (e.g. listener not installed in
    // time, or the player is already in a terminal state we don't catch),
    // still flip isPlayerReady true once a clip is loaded so the preview
    // surface is mounted. ExoPlayer will simply show whatever it has.
    LaunchedEffect(exoPlayer, state.isPlayerReady) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (state.isPlayerReady) return@LaunchedEffect
        kotlinx.coroutines.delay(1500)
        if (player.playbackState != Player.STATE_IDLE) {
            Log.w("ApexTrace", "EditorScreen: forcing playerReady after timeout (state=${player.playbackState})")
            vm.setPlayerReady(true)
        }
    }

    // Compute the active LUT preset + selected keyframe track ONCE
    // so both the filter-effect and the media-prep effects can
    // share the same values without duplicating the lookup.
    val activePreset = remember(state.activeFilterId, filterEngine) {
        val id = state.activeFilterId ?: return@remember null
        filterEngine.manifest.filters.firstOrNull { it.id == id }
    }
    val selectedClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
    val selectedKeyframes = selectedClip?.keyframes

    // The current Effect list, memoised as a stable value (not a
    // local function — those can't be captured by a LaunchedEffect's
    // coroutine because the function reference isn't stable across
    // recompositions). Re-evaluated only when the active filter /
    // intensity / selected clip's keyframes actually change.
    val currentEffects = remember(activePreset, state.filterIntensity, selectedKeyframes) {
        buildList<androidx.media3.common.Effect> {
            if (activePreset != null && state.filterIntensity > 0f) {
                add(
                    com.apexstudio.app.data.filter.LutFilterGlEffect(
                        context, activePreset, state.filterIntensity
                    )
                )
            }
            val kf = selectedKeyframes
            if (kf != null && !kf.isEmpty()) {
                val trackRef = arrayOf(kf)
                add(
                    com.apexstudio.app.data.animation.KeyframeAnimationEffect(
                        trackProvider = { trackRef[0] }
                    ).buildEffects().first()
                )
            }
        }
    }

    LaunchedEffect(exoPlayer, state.selectedClipId) {
        val player = exoPlayer ?: return@LaunchedEffect
        val clipId = state.selectedClipId ?: return@LaunchedEffect
        val clip = state.project?.clips?.firstOrNull { it.id == clipId } ?: return@LaunchedEffect
        val mediaItem = MediaItem.fromUri(Uri.parse(clip.uri))
        if (player.currentMediaItem?.mediaId != mediaItem.mediaId) {
            try {
                Log.d("ApexTrace", "EditorScreen: preparing player for ${clip.uri}")
                CrashMarker.mark(context, "EditorScreen: player.prepare() for ${clip.uri}")
                // Drop the PlayerView's surface so the new media
                // item gets a clean EGL surface to draw on. Without
                // this, the recycled surface occasionally fails to
                // produce frames for the freshly queued media.
                vm.setPlayerReady(false)
                player.setMediaItem(mediaItem)
                player.prepare()
                // Re-assert the current Effect list AFTER prepare()
                // so the GL pipeline has both the media and the
                // LUT/keyframes attached when the first frame is
                // produced.
                try {
                    player.setVideoEffects(currentEffects)
                } catch (e: Exception) {
                    Log.e("EditorScreen", "setVideoEffects (after prepare) failed", e)
                }
                Log.d("ApexTrace", "EditorScreen: player prepared")
            } catch (e: Exception) {
                Log.e("EditorScreen", "player.prepare() failed", e)
            } finally {
                CrashMarker.clear(context)
            }
            vm.setPlayerDuration(clip.durationMs)
        }
    }

    // External play/pause control (e.g. user tapping the play
    // button on the timeline). Kept separate from the auto-play path
    // above so manual toggles don't get clobbered when the selected
    // clip changes.
    LaunchedEffect(exoPlayer, state.isPlaying, state.selectedClipId) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (state.selectedClipId == null) return@LaunchedEffect
        if (state.isPlaying) {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        } else {
            player.pause()
        }
    }

    LaunchedEffect(state.playbackSpeed) {
        exoPlayer?.playbackParameters = PlaybackParameters(state.playbackSpeed)
    }

    LaunchedEffect(exoPlayer, currentEffects) {
        val player = exoPlayer ?: return@LaunchedEffect
        try {
            player.setVideoEffects(currentEffects)
        } catch (e: Exception) {
            Log.e("EditorScreen", "setVideoEffects (filter) failed", e)
        }
    }

    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (isActive) {
            val pos = player.currentPosition
            if (pos != state.playerPositionMs) {
                vm.setPlayerPosition(pos)
            }
            // Forward seek requests from the timeline scrubber /
            // ±5s buttons / etc. into ExoPlayer. The VM only updates
            // state.playerPositionMs; without this hop the player
            // would never actually seek and the playhead indicator
            // would drift away from the frame ExoPlayer is rendering.
            //
            // We poll instead of a separate LaunchedEffect because
            // (a) the polling loop already runs every 100ms so we
            // don't need a redundant effect, and (b) the dead-band
            // check here naturally folds "user-initiated seek" and
            // "polling tick" into the same code path without the
            // risk of two effects fighting each other.
            val target = state.playerPositionMs
            val current = player.currentPosition
            if (player.currentMediaItem != null &&
                kotlin.math.abs(current - target) > 80
            ) {
                player.seekTo(target)
            }
            delay(100)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(ApexPalette.BgBase)
    ) {
        EditorTopBar(
            currentTimeMs = state.playerPositionMs,
            onBack = onBack,
            onExport = onExport
        )

        VideoPreviewSection(
            isPlaying = state.isPlaying,
            currentTimeMs = state.playerPositionMs,
            onTogglePlay = { vm.togglePlay() },
            onPrev = { vm.seekTo((state.playerPositionMs - 5000).coerceAtLeast(0)) },
            onNext = { vm.seekTo((state.playerPositionMs + 5000).coerceAtMost(state.durationMs)) },
            exoPlayer = exoPlayer,
            playerReady = state.isPlayerReady,
            cropMode = state.cropMode,
            cropRect = state.cropRect,
            cropAspect = state.cropAspect,
            onCropRectChange = { vm.setCropRect(it) },
            onResetCrop = { vm.resetCrop() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.35f)
        )

        TimelineSection(
            state = state,
            onScrub = { vm.seekTo(it) },
            onZoom = { vm.multiplyZoom(it) },
            onSelectClip = { vm.selectClip(it) },
            onAddMedia = {
                // The + button lives on the empty timeline now (see
                // TimelineSection). It launches the multi-video gallery
                // directly — going through isMediaPickerOpen caused the
                // launcher to be cancelled mid-flight.
                mediaPicker.pickMultipleMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
        )

        HorizontalToolBar(
            onSplit = {
                state.selectedClipId?.let { vm.splitClip(it, state.playerPositionMs) }
            },
            onCut = { vm.cutClipAtPlayhead() },
            onSpeed = { vm.openSpeedPanel() },
            onCrop = { vm.setCropMode(!state.cropMode) },
            cropActive = state.cropMode,
            cropAspect = state.cropAspect,
            onCropAspect = { vm.applyCropAspect(it) },
            onFilters = { vm.openFilterPanel() },
            filtersActive = state.activeFilterId != null || state.filterPanelOpen,
            onColor = onColor,
            onAudio = onAudio,
            onText = { vm.setKeyframePanelOpen(true) },
            onFx = { /* placeholder */ },
            onExport = {},
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.20f)
        )
    }

    // Filter panel — bottom-sheet style overlay. Only mounted while
    // state.filterPanelOpen is true so it doesn't take up layout space
    // when hidden.
    if (state.filterPanelOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.closeFilterPanel() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) { /* eat clicks */ }
            ) {
                FilterPanel(
                    manifest = filterEngine.manifest,
                    activeFilterId = state.activeFilterId,
                    intensity = state.filterIntensity,
                    activeCategory = state.filterCategory,
                    onCategoryChange = { vm.setFilterCategory(it) },
                    onFilterSelected = { vm.setActiveFilter(it) },
                    onIntensityChange = { vm.setFilterIntensity(it) },
                    onClose = { vm.closeFilterPanel() }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) { }
            ) {
                SpeedRampPanel(
                    selectedClipId = state.selectedClipId,
                    currentSpeed = state.playbackSpeed,
                    activeClipSpeed = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }?.speedMultiplier ?: 1f,
                    onSelectPreset = { preset ->
                        state.selectedClipId?.let { vm.applySpeedPreset(it, preset) }
                        vm.setPlaybackSpeed(preset.multiplier)
                    },
                    onCustomSpeed = { v ->
                        state.selectedClipId?.let { vm.setClipSpeed(it, v) }
                        vm.setPlaybackSpeed(v)
                    },
                    onClose = { vm.closeSpeedPanel() }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) { }
            ) {
                AudioMixerPanel(
                    state = vm.audio.collectAsStateWithLifecycle().value,
                    muteOriginalVideo = vm.audio.collectAsStateWithLifecycle().value.isMuted,
                    onMuteOriginal = { vm.setMuteOriginalVideo(it) },
                    onAddTrack = { name, uri, kind ->
                        vm.addAudioTrack(name, uri, kind)
                    },
                    onRemoveTrack = { vm.removeAudioTrack(it) },
                    onVolume = { id, v -> vm.setAudioTrackVolume(id, v) },
                    onMute = { vm.toggleAudioTrackMute(it) },
                    onSolo = { vm.toggleAudioTrackSolo(it) },
                    onTrim = { id, s, e -> vm.setAudioTrackTrim(id, s, e) },
                    onFadeIn = { id, ms -> vm.setAudioTrackFadeIn(id, ms) },
                    onFadeOut = { id, ms -> vm.setAudioTrackFadeOut(id, ms) },
                    onClose = { vm.closeAudioMixer() }
                )
            }
        }
    }

    if (state.keyframePanelOpen) {
        val selectedClip = state.project?.clips?.firstOrNull { it.id == state.selectedClipId }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { vm.setKeyframePanelOpen(false) },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) { }
            ) {
                KeyframePanel(
                    track = selectedClip?.keyframes ?: com.apexstudio.app.domain.model.KeyframeTrack(),
                    playheadMs = state.playerPositionMs,
                    canAdd = selectedClip != null,
                    onAdd = { ms -> selectedClip?.let { vm.addKeyframe(it.id, ms) } },
                    onUpdate = { kf -> selectedClip?.let { vm.updateKeyframe(it.id, kf.id) { kf } } },
                    onRemove = { id -> selectedClip?.let { vm.removeKeyframe(it.id, id) } },
                    onClear = { selectedClip?.let { vm.clearKeyframes(it.id) } },
                    onClose = { vm.setKeyframePanelOpen(false) }
                )
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    currentTimeMs: Long,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    CrashMarker.mark(LocalContext.current, "EditorScreen: EditorTopBar")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeonIconButton(
            icon = Icons.Default.ChevronLeft,
            onClick = onBack,
            size = 40.dp,
            iconSize = 22.dp
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ApexPalette.BgGlass)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    TimeFormat.msToTimecode(currentTimeMs, includeFrames = true),
                    color = ApexPalette.NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            ApexPalette.NeonCyan.copy(alpha = 0.6f),
                            ApexPalette.NeonPurple.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
                .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onExport),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.IosShare,
                null,
                tint = ApexPalette.NeonCyan,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun VideoPreviewSection(
    isPlaying: Boolean,
    currentTimeMs: Long,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    exoPlayer: ExoPlayer?,
    playerReady: Boolean,
    cropMode: Boolean,
    cropRect: com.apexstudio.app.presentation.state.CropRect,
    cropAspect: com.apexstudio.app.presentation.state.CropAspect,
    onCropRectChange: (com.apexstudio.app.presentation.state.CropRect) -> Unit,
    onResetCrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    CrashMarker.mark(LocalContext.current, "EditorScreen: VideoPreviewSection")
    val context = LocalContext.current

    // The outer Box no longer adds vertical padding around the video
    // surface. A previous 4.dp vertical padding combined with the
    // weight(0.35f) slot produced a thin strip of background bleeding
    // through above the rounded preview corners. Padding is now 0;
    // the weight slot controls the height and the inner Box fills it
    // edge-to-edge.
    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Tap-to-toggle: split the surface into three zones so the user
        // can also seek ±5s by tapping the left/right thirds, while
        // tapping the centre toggles play/pause.
        //
        // Sizing: we fill the full weight-slot width and height
        // (fillMaxSize) and let the PlayerView's RESIZE_MODE_FIT
        // letterbox the actual video frames inside. Previously the
        // inner box was .height(previewHeight) where previewHeight was
        // the aspect-ratio-derived height — for 16:9 that came out
        // noticeably shorter than the 0.35f weight slot, leaving a
        // visible strip of background between the top bar and the
        // rounded video corners. fillMaxSize + a top-aligned outer
        // Box closes that gap.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(16.dp))
                .clickable { onTogglePlay() },
            contentAlignment = Alignment.Center
        ) {
            // Background placeholder: gradient + play-icon overlay while the
            // player is not yet ready.
            Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF0F1A2D),
                                Color(0xFF1B2A4E),
                                Color(0xFF3A1B5E),
                                Color(0xFF0E2B3F)
                            )
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(w, h)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ApexPalette.NeonCyan.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        ),
                        radius = 60f,
                        center = Offset(w - 80f, 80f)
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF080A0F))
                        ),
                        topLeft = Offset(0f, h * 0.6f),
                        size = Size(w, h * 0.4f)
                    )
                    if (!isPlaying) {
                        val cx = w / 2f
                        val cy = h / 2f
                        val r = 28f
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(cx - r * 0.6f, cy - r)
                            lineTo(cx + r, cy)
                            lineTo(cx - r * 0.6f, cy + r)
                            close()
                        }
                        drawPath(path = path, color = Color.White.copy(alpha = 0.7f))
                    }
                }

            // The actual video surface. Attached as soon as ExoPlayer is
            // created — ExoPlayer handles its own surface lifecycle and
            // will render frames as they become available. The old gating
            // on playerReady caused the preview to stay black because
            // STATE_READY never fired on some devices.
            if (exoPlayer != null) {
                CrashMarker.mark(LocalContext.current, "EditorScreen: attaching PlayerView")
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        try {
                            PlayerView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                useController = false
                                // FIT preserves the video's aspect ratio inside
                                // the container; the container itself is sized
                                // to the video's ratio above, so together they
                                // give us a full-bleed preview at the right
                                // shape (16:9, 9:16, 1:1, …).
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                player = exoPlayer
                            }
                        } catch (e: Throwable) {
                            Log.e("EditorScreen", "PlayerView factory failed", e)
                            android.view.View(ctx)
                        }
                    },
                    update = { view ->
                        runCatching {
                            (view as? PlayerView)?.let { pv ->
                                pv.player = exoPlayer
                                pv.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }
                    }
                )
            }

            // Crop overlay: only mounted when cropMode is on. Darkens
            // the area outside the crop rectangle and renders draggable
            // handles on the four edges + four corners. The
            // normalised rect (0..1) is mapped to the actual container
            // size in pixels for the drag math.
            if (cropMode) {
                CropOverlay(
                    rect = cropRect,
                    aspect = cropAspect,
                    onRectChange = onCropRectChange,
                    onReset = onResetCrop
                )
            }

            // Timecode chip (top-end). The previous bottom control row
            // (add / prev / rewind / play / forward / next) has been
            // removed entirely — the user now toggles play/pause by
            // tapping anywhere on the video surface, and the + add
            // media button lives on the empty timeline (see
            // TimelineSection).
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ApexPalette.BgGlass)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    TimeFormat.msToTimecode(currentTimeMs, includeFrames = true),
                    color = ApexPalette.NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        // Left/right seek-5s zones overlaid on the video. The main
        // clickable on the video Box still toggles play/pause, but
        // these pointer-input handlers sit on top and consume taps in
        // the outer thirds so the user can scrub ±5s without hunting
        // for on-screen buttons.
        Row(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures { onPrev() }
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures { onNext() }
                    }
            )
        }
    }
}

@Composable
private fun CropOverlay(
    rect: com.apexstudio.app.presentation.state.CropRect,
    aspect: com.apexstudio.app.presentation.state.CropAspect,
    onRectChange: (com.apexstudio.app.presentation.state.CropRect) -> Unit,
    onReset: () -> Unit
) {
    val handleSize = 18.dp
    val edgeThickness = 4.dp
    val darkenColor = Color.Black.copy(alpha = 0.55f)
    val edgeColor = ApexPalette.NeonCyan
    val handleColor = ApexPalette.NeonEmerald

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val widthPx = with(LocalDensity.current) { w.toPx() }
        val heightPx = with(LocalDensity.current) { h.toPx() }

        // Helper to convert a normalised rect to pixel offsets/sizes.
        fun toPixel(r: com.apexstudio.app.presentation.state.CropRect): androidx.compose.ui.geometry.Rect {
            return androidx.compose.ui.geometry.Rect(
                left = r.left * widthPx,
                top = r.top * heightPx,
                right = r.right * widthPx,
                bottom = r.bottom * heightPx
            )
        }
        val pix = toPixel(rect)

        // Darken the four regions OUTSIDE the crop rect. Drawn as four
        // semi-transparent black rectangles around the crop.
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Top
            drawRect(darkenColor, topLeft = Offset(0f, 0f), size = Size(size.width, pix.top))
            // Bottom
            drawRect(
                darkenColor,
                topLeft = Offset(0f, pix.bottom),
                size = Size(size.width, size.height - pix.bottom)
            )
            // Left
            drawRect(
                darkenColor,
                topLeft = Offset(0f, pix.top),
                size = Size(pix.left, pix.height)
            )
            // Right
            drawRect(
                darkenColor,
                topLeft = Offset(pix.right, pix.top),
                size = Size(size.width - pix.right, pix.height)
            )
            // Crop border
            drawRect(
                color = edgeColor,
                topLeft = Offset(pix.left, pix.top),
                size = Size(pix.width, pix.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
            // Rule-of-thirds grid inside the crop
            val x1 = pix.left + pix.width / 3f
            val x2 = pix.left + pix.width * 2f / 3f
            val y1 = pix.top + pix.height / 3f
            val y2 = pix.top + pix.height * 2f / 3f
            val gridColor = Color.White.copy(alpha = 0.25f)
            listOf(x1, x2).forEach { gx ->
                drawLine(gridColor, Offset(gx, pix.top), Offset(gx, pix.bottom), strokeWidth = 1f)
            }
            listOf(y1, y2).forEach { gy ->
                drawLine(gridColor, Offset(pix.left, gy), Offset(pix.right, gy), strokeWidth = 1f)
            }
        }

        // Edge handles (top/left/right/bottom) — these move one edge at
        // a time and preserve the aspect ratio if one is locked.
        // Corner handles (TL/TR/BL/BR) — move two edges simultaneously.
        fun updateByEdge(
            current: com.apexstudio.app.presentation.state.CropRect,
            edge: String,
            dxNorm: Float,
            dyNorm: Float
        ): com.apexstudio.app.presentation.state.CropRect {
            val target = aspect.ratio
            var l = current.left
            var t = current.top
            var r = current.right
            var b = current.bottom
            when (edge) {
                "L" -> l = (l + dxNorm).coerceIn(0f, r - 0.05f)
                "R" -> r = (r + dxNorm).coerceIn(l + 0.05f, 1f)
                "T" -> t = (t + dyNorm).coerceIn(0f, b - 0.05f)
                "B" -> b = (b + dyNorm).coerceIn(t + 0.05f, 1f)
            }
            // Apply aspect lock by deriving the opposite axis from the
            // primary one (use the larger axis movement as the driver).
            if (target != null) {
                val cx = (l + r) / 2f
                val cy = (t + b) / 2f
                when (edge) {
                    "L", "R" -> {
                        val newW = r - l
                        val newH = (newW / target).coerceAtMost(1f)
                        t = (cy - newH / 2f).coerceIn(0f, 1f - newH)
                        b = t + newH
                    }
                    "T", "B" -> {
                        val newH = b - t
                        val newW = (newH * target).coerceAtMost(1f)
                        l = (cx - newW / 2f).coerceIn(0f, 1f - newW)
                        r = l + newW
                    }
                }
            }
            return com.apexstudio.app.presentation.state.CropRect(l, t, r, b)
        }

        fun updateByCorner(
            current: com.apexstudio.app.presentation.state.CropRect,
            corner: String,
            dxNorm: Float,
            dyNorm: Float
        ): com.apexstudio.app.presentation.state.CropRect {
            var l = current.left
            var t = current.top
            var r = current.right
            var b = current.bottom
            when (corner) {
                "TL" -> { l = (l + dxNorm); t = (t + dyNorm) }
                "TR" -> { r = (r + dxNorm); t = (t + dyNorm) }
                "BL" -> { l = (l + dxNorm); b = (b + dyNorm) }
                "BR" -> { r = (r + dxNorm); b = (b + dyNorm) }
            }
            l = l.coerceIn(0f, r - 0.05f)
            t = t.coerceIn(0f, b - 0.05f)
            r = r.coerceIn(l + 0.05f, 1f)
            b = b.coerceIn(t + 0.05f, 1f)
            // Aspect lock: derive the perpendicular axis from the one
            // the user moved. For TL/TR/BL/BR we treat horizontal as
            // primary and derive height.
            val target = aspect.ratio
            if (target != null) {
                val newW = r - l
                val newH = (newW / target).coerceAtMost(1f)
                val cy = (t + b) / 2f
                t = (cy - newH / 2f).coerceIn(0f, 1f - newH)
                b = t + newH
            }
            return com.apexstudio.app.presentation.state.CropRect(l, t, r, b)
        }

        // Corner handles
        HandleDot(
            x = pix.left,
            y = pix.top,
            size = handleSize,
            color = handleColor
        ) { dx, dy ->
            val nw = widthPx.coerceAtLeast(1f)
            val nh = heightPx.coerceAtLeast(1f)
            onRectChange(updateByCorner(rect, "TL", dx / nw, dy / nh))
        }
        HandleDot(
            x = pix.right,
            y = pix.top,
            size = handleSize,
            color = handleColor
        ) { dx, dy ->
            val nw = widthPx.coerceAtLeast(1f)
            val nh = heightPx.coerceAtLeast(1f)
            onRectChange(updateByCorner(rect, "TR", dx / nw, dy / nh))
        }
        HandleDot(
            x = pix.left,
            y = pix.bottom,
            size = handleSize,
            color = handleColor
        ) { dx, dy ->
            val nw = widthPx.coerceAtLeast(1f)
            val nh = heightPx.coerceAtLeast(1f)
            onRectChange(updateByCorner(rect, "BL", dx / nw, dy / nh))
        }
        HandleDot(
            x = pix.right,
            y = pix.bottom,
            size = handleSize,
            color = handleColor
        ) { dx, dy ->
            val nw = widthPx.coerceAtLeast(1f)
            val nh = heightPx.coerceAtLeast(1f)
            onRectChange(updateByCorner(rect, "BR", dx / nw, dy / nh))
        }

        // Edge handles (thinner, mid-edge)
        HandleDot(
            x = (pix.left + pix.right) / 2f,
            y = pix.top,
            size = edgeThickness,
            color = edgeColor
        ) { _, dy ->
            val nh = heightPx.coerceAtLeast(1f)
            onRectChange(updateByEdge(rect, "T", 0f, dy / nh))
        }
        HandleDot(
            x = (pix.left + pix.right) / 2f,
            y = pix.bottom,
            size = edgeThickness,
            color = edgeColor
        ) { _, dy ->
            val nh = heightPx.coerceAtLeast(1f)
            onRectChange(updateByEdge(rect, "B", 0f, dy / nh))
        }
        HandleDot(
            x = pix.left,
            y = (pix.top + pix.bottom) / 2f,
            size = edgeThickness,
            color = edgeColor
        ) { dx, _ ->
            val nw = widthPx.coerceAtLeast(1f)
            onRectChange(updateByEdge(rect, "L", dx / nw, 0f))
        }
        HandleDot(
            x = pix.right,
            y = (pix.top + pix.bottom) / 2f,
            size = edgeThickness,
            color = edgeColor
        ) { dx, _ ->
            val nw = widthPx.coerceAtLeast(1f)
            onRectChange(updateByEdge(rect, "R", dx / nw, 0f))
        }

        // Small "Reset" pill at the top-start while crop mode is on.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ApexPalette.BgGlass)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(6.dp))
                .clickable { onReset() }
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                "Reset",
                color = ApexPalette.NeonCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HandleDot(
    x: Float,
    y: Float,
    size: androidx.compose.ui.unit.Dp,
    color: Color,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset((x - size.toPx() / 2f).toInt(), (y - size.toPx() / 2f).toInt()) }
            .size(size + 8.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color.copy(alpha = 0.9f))
                 .border(1.5f.dp, Color.White, CircleShape)
        )
    }
}

/**
 * Compact +/- button used in the timeline zoom row. Calls [onClick]
 * with no arguments so the caller decides the step size (the
 * timeline wires it to `multiplyZoom(1.25f)` / `multiplyZoom(1/1.25f)`).
 */
@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = ApexPalette.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun TimelineSection(
    state: com.apexstudio.app.presentation.state.EditorState,
    onScrub: (Long) -> Unit,
    onZoom: (Float) -> Unit,
    onSelectClip: (String?) -> Unit,
    onAddMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    CrashMarker.mark(LocalContext.current, "EditorScreen: TimelineSection")
    val clips = state.project?.clips ?: emptyList()
    val density = LocalDensity.current
    val basePxPerMs = with(density) { 0.16f.dp.toPx() }
    val pxPerMs = basePxPerMs * state.zoomLevel
    // Total on-track width is the SUM of every clip's track length
    // (sequential timeline), not just the longest single clip. If we
    // used state.durationMs here the user would see a single-clip
    // filmstrip that's the same length regardless of how many
    // clips they actually added.
    val totalTrackMs = clips.sumOf { (it.trimEndMs - it.trimStartMs).coerceAtLeast(1000L) }
    val totalWidth = (totalTrackMs * pxPerMs).toInt().coerceAtLeast(0)
    val scroll = rememberScrollState()
    val context = LocalContext.current

    // Per-clip timeline media cache. The key is (uri, trackLengthMs,
    // rendered frame width) so re-zoom or re-trim invalidates the
    // cache. Loading is fire-and-forget: the ClipBlock shows a
    // gradient background immediately, then swaps in the real
    // frames / waveform once extraction finishes — so the timeline
    // never blocks on a slow MediaMetadataRetriever.
    val timelineCache = remember { TimelineMediaCache(context) }
    val mediaByClipId by timelineCache.state.collectAsStateWithLifecycle()
    // Kick off (or re-kick on zoom / clip-list change) the
    // background extraction jobs. observe() is idempotent: it
    // only spawns a new job for keys that aren't already in
    // flight or already cached.
    LaunchedEffect(clips, pxPerMs) {
        timelineCache.observe(clips, pxPerMs)
    }
    // Release the cache's background scope when the editor screen
    // leaves the composition so we don't leak the IO dispatcher.
    DisposableEffect(Unit) {
        onDispose { timelineCache.release() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Zoom readout + buttons. The pinch gesture lives on the
        // scrollable track area below; these buttons give the same
        // effect for users on devices / emulators without a
        // multi-touch screen. Both paths go through
        // vm.multiplyZoom so the level is accumulated correctly.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Zoom ${"%.1f".format(state.zoomLevel)}x",
                color = ApexPalette.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            ZoomButton(label = "−", onClick = { onZoom(1f / 1.25f) })
            Spacer(Modifier.width(4.dp))
            ZoomButton(label = "+", onClick = { onZoom(1.25f) })
        }
        // Empty-state: when the project has no clips yet, show a
        // prominent "+ Add media" call-to-action inside the timeline
        // slot. The preview still plays its placeholder gradient, and
        // the user gets one obvious path to start.
        if (clips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApexPalette.BgGlass)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
                    .clickable(onClick = onAddMedia)
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = ApexPalette.NeonEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Add your first video",
                        color = ApexPalette.NeonCyan,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Compact + button shown above the timecode ruler when clips
        // already exist. The big "Add your first video" CTA above
        // covers the empty-state case; this is the always-available
        // "append another clip" affordance.
        if (clips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.BgGlass)
                        .border(1.dp, ApexPalette.NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onAddMedia)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add media",
                            tint = ApexPalette.NeonEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Add",
                            color = ApexPalette.NeonEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ApexPalette.BgSurface)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(6.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val tickEvery = 10_000L
                var t = 0L
                while (t <= 240_000L) {
                    val x = (t * pxPerMs).toFloat() - scroll.value
                    if (x in 0f..w) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(x, size.height * 0.4f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f
                        )
                    }
                    t += tickEvery
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(scroll.value.pxToDp()))
                for (t in listOf(0, 30_000L, 60_000L, 90_000L, 120_000L, 150_000L,
                    180_000L, 210_000L)) {
                    val labelLeft = (t * pxPerMs - scroll.value - 16).coerceAtLeast(0f)
                    Spacer(Modifier.width(labelLeft.toDp()))
                    Text(
                        TimeFormat.msToShort(t),
                        color = ApexPalette.TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ApexPalette.BgSurface)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    // Pinch-to-zoom. The previous version passed
                    // detectTransformGestures' relative `zoom` factor
                    // straight to setZoom(absolute) — so each
                    // gesture frame was setting zoom to 1.0x-ish and
                    // the level never accumulated past one frame.
                    // The relative factor (1.0x = no change, 1.05x
                    // = 5% in) is now multiplied into the current
                    // level so a sustained pinch actually zooms.
                    detectTransformGestures { _, _, zoom, _ ->
                        onZoom(zoom)
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scroll)
            ) {
                VideoTrackRow(
                    label = "V1",
                    color = ApexPalette.TrackVideo,
                    width = totalWidth,
                    pxPerMs = pxPerMs,
                    clips = clips.filter { it.type == com.apexstudio.app.domain.model.ClipType.VIDEO },
                    selectedClipId = state.selectedClipId,
                    onSelectClip = onSelectClip,
                    mediaByClipId = mediaByClipId
                )
                VideoTrackRow(
                    label = "V2",
                    color = ApexPalette.TrackOverlay,
                    width = totalWidth,
                    pxPerMs = pxPerMs,
                    clips = clips.filter { it.type == com.apexstudio.app.domain.model.ClipType.OVERLAY },
                    selectedClipId = state.selectedClipId,
                    onSelectClip = onSelectClip,
                    mediaByClipId = mediaByClipId
                )
                RealWaveformTrackRow(
                    label = "A1",
                    color = ApexPalette.NeonEmerald,
                    width = totalWidth,
                    pxPerMs = pxPerMs,
                    progress = state.playerPositionMs.toFloat() / state.durationMs.coerceAtLeast(1).toFloat(),
                    samples = state.audioWaveform
                )
                RealWaveformTrackRow(
                    label = "FX",
                    color = ApexPalette.TrackAudio,
                    width = totalWidth,
                    pxPerMs = pxPerMs,
                    progress = state.playerPositionMs.toFloat() / state.durationMs.coerceAtLeast(1).toFloat(),
                    samples = FloatArray(0)
                )
            }

            val playheadX = (state.playerPositionMs * pxPerMs).toFloat() - scroll.value
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = playheadX
                if (x in 0f..size.width) {
                    drawLine(
                        brush = Brush.verticalGradient(
                            listOf(
                                ApexPalette.NeonCyan.copy(alpha = 0.0f),
                                ApexPalette.NeonCyan.copy(alpha = 0.5f),
                                ApexPalette.NeonCyan.copy(alpha = 0.5f),
                                ApexPalette.NeonCyan.copy(alpha = 0.0f)
                            )
                        ),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 8f
                    )
                    drawLine(
                        color = ApexPalette.NeonCyan,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2f
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.durationMs, pxPerMs) {
                        detectTapGestures { off ->
                            val t = ((off.x + scroll.value) / pxPerMs).toLong()
                            onScrub(t.coerceIn(0, state.durationMs))
                        }
                    }
            )
        }
    }
}

@Composable
private fun VideoTrackRow(
    label: String,
    color: Color,
    width: Int,
    pxPerMs: Float,
    clips: List<MediaClip>,
    selectedClipId: String?,
    onSelectClip: (String?) -> Unit,
    mediaByClipId: Map<String, ClipMedia> = emptyMap()
) {
    val density = LocalDensity.current
    val trackHeightDp = 42.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeightDp + 4.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
        }
        Spacer(Modifier.width(3.dp))
        Box(
            modifier = Modifier
                .height(trackHeightDp)
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(4.dp))
        ) {
            val widthDp = with(density) { width.toDp() }
            // Lay the clips out sequentially on the track. Each
            // clip's on-track start is the running sum of every
            // previous clip's duration — that way the user sees a
            // proper filmstrip, not a stack of zero-offset blocks.
            // We deliberately ignore clip.trimStartMs here because
            // trim is a *source* offset (where in the original file
            // playback starts), not a *track* offset.
            var runningMs = 0L
            val blocks = clips.map { clip ->
                val trackStart = runningMs
                val trackLen = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(1000L)
                runningMs += trackLen
                Triple(clip, trackStart, trackLen)
            }
            Box(modifier = Modifier.width(widthDp)) {
                for ((clip, trackStart, trackLen) in blocks) {
                    VideoClipBlock(
                        clip = clip,
                        trackStartMs = trackStart,
                        trackLengthMs = trackLen,
                        pxPerMs = pxPerMs,
                        selected = selectedClipId == clip.id,
                        onSelect = { onSelectClip(clip.id) },
                        media = mediaByClipId[clip.id]
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoClipBlock(
    clip: MediaClip,
    trackStartMs: Long,
    trackLengthMs: Long,
    pxPerMs: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    media: ClipMedia? = null
) {
    val w = (trackLengthMs * pxPerMs).toInt().coerceAtLeast(40)
    val x = (trackStartMs * pxPerMs).toInt()
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(x, 0) }
            .width(with(density) { w.toDp() })
            .fillMaxHeight()
            .padding(1.dp)
            .clip(RoundedCornerShape(3.dp))
            .clickable(onClick = onSelect)
    ) {        // 1) Faux-tile background — drawn first so the real
        //    thumbnails / waveform can layer on top the moment
        //    TimelineMediaCache finishes extraction. The tile
        //    pattern reads as "filmstrip" while the data loads.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tileW = (size.width / 8f).coerceAtLeast(8f)
            val grad = Brush.horizontalGradient(
                listOf(
                    ApexPalette.NeonPurple.copy(alpha = 0.85f),
                    ApexPalette.TrackVideo.copy(alpha = 0.85f),
                    ApexPalette.NeonCyan.copy(alpha = 0.4f)
                )
            )
            drawRect(brush = grad, size = size)
            var i = 0f
            while (i < size.width) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.35f),
                    start = Offset(i, 0f),
                    end = Offset(i, size.height),
                    strokeWidth = 1.2f
                )
                i += tileW
            }
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)
                ),
                size = Size(size.width, size.height * 0.3f)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                ),
                topLeft = Offset(0f, size.height * 0.7f),
                size = Size(size.width, size.height * 0.3f)
            )
        }
        // 2) Real clip content overlays — drawn on top of the
        //    faux background. The block is wide enough that we
        //    only render the frames/waveform that intersect the
        //    visible region, but for a 17-frame filmstrip we just
        //    draw all of them since they're cheap Canvas blits.
        if (media != null) {
            if (clip.type == com.apexstudio.app.domain.model.ClipType.AUDIO ||
                clip.type == com.apexstudio.app.domain.model.ClipType.SFX
            ) {
                // Audio track: render the downsampled waveform
                // as a vertical bar chart.
                if (media.waveform.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        val mid = size.height / 2f
                        val step = size.width / media.waveform.size
                        val barWidth = (step * 0.6f).coerceAtLeast(1f)
                        for (i in media.waveform.indices) {
                            val v = media.waveform[i].coerceIn(0f, 1f)
                            val barH = (v * size.height * 0.85f).coerceAtLeast(2f)
                            drawLine(
                                color = Color.White.copy(alpha = 0.85f),
                                start = Offset(i * step, mid - barH / 2f),
                                end = Offset(i * step, mid + barH / 2f),
                                strokeWidth = barWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                }
            } else {
                // Video / overlay track: render the real thumbnail
                // filmstrip. Each frame is drawn at its slice of the
                // block width; empty cells (frames not yet
                // extracted) fall through to the faux background.
                if (media.frames.isNotEmpty()) {
                    val frames = media.frames
                    Row(modifier = Modifier.fillMaxSize()) {
                        for (frame in frames) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = frame.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    // Soft dark gradient so the clip label stays
                    // legible over (potentially bright) frames.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                            ),
                            size = Size(size.width, size.height * 0.35f)
                        )
                    }
                }
            }
        }
        // 3) Border + label are always on top of the media.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    if (selected) 1.5.dp else 0.5.dp,
                    if (selected) ApexPalette.NeonCyan else Color.White.copy(alpha = 0.15f),
                    RoundedCornerShape(3.dp)
                )
        )
        Text(
            clip.name,
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(3.dp)
        )
    }
}

@Composable
private fun RealWaveformTrackRow(
    label: String,
    color: Color,
    width: Int,
    pxPerMs: Float,
    progress: Float,
    samples: FloatArray
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
        }
        Spacer(Modifier.width(3.dp))
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(4.dp))
                .padding(2.dp)
        ) {
            val widthDp = with(density) { width.toDp() }
            RealAudioWaveform(
                samples = samples,
                modifier = Modifier.width(widthDp).fillMaxHeight(),
                color = color,
                progress = progress
            )
        }
    }
}

@Composable
private fun HorizontalToolBar(
    onSplit: () -> Unit,
    onCut: () -> Unit,
    onSpeed: () -> Unit,
    onCrop: () -> Unit,
    cropActive: Boolean,
    cropAspect: com.apexstudio.app.presentation.state.CropAspect,
    onCropAspect: (com.apexstudio.app.presentation.state.CropAspect) -> Unit,
    onFilters: () -> Unit,
    filtersActive: Boolean,
    onColor: () -> Unit,
    onAudio: () -> Unit,
    onText: () -> Unit,
    onFx: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    CrashMarker.mark(LocalContext.current, "EditorScreen: HorizontalToolBar")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // When Crop mode is active, the toolbar shows an extra row of
        // aspect-ratio presets right above the main icon row. Tapping a
        // preset calls vm.applyCropAspect(...) which keeps the crop
        // centred and just adjusts width/height to match the new ratio.
        if (cropActive) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexPalette.BgGlass)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(com.apexstudio.app.presentation.state.CropAspect.values().toList()) { aspect ->
                    val selected = aspect == cropAspect
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (selected) ApexPalette.NeonCyan
                                else ApexPalette.BorderGlass,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onCropAspect(aspect) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            aspect.label,
                            color = if (selected) ApexPalette.NeonCyan
                                    else ApexPalette.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        val items = listOf(
            ToolDef("Split", Icons.Default.ContentCut, onSplit),
            ToolDef("Cut", Icons.Default.ContentCut, onCut),
            ToolDef("Speed", Icons.Default.Speed, onSpeed),
            ToolDef("Crop", Icons.Default.Crop, onCrop, highlight = cropActive),
            ToolDef("Filters", Icons.Default.FilterAlt, onFilters, highlight = filtersActive),
            ToolDef("Color", Icons.Default.Palette, onColor),
            ToolDef("Audio", Icons.Default.GraphicEq, onAudio),
            ToolDef("Text", Icons.Default.TextFields, onText),
            ToolDef("FX", Icons.Default.AutoAwesome, onFx)
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ApexPalette.BgGlass)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(items) { tool ->
                ToolbarIcon(tool.label, tool.icon, tool.onClick, highlight = tool.highlight)
            }
        }
    }
}

private data class ToolDef(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val highlight: Boolean = false
)

@Composable
private fun ToolbarIcon(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    highlight: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(60.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (highlight) ApexPalette.NeonCyan.copy(alpha = 0.25f)
                    else ApexPalette.BgElevated
                )
                .border(
                    1.dp,
                    if (highlight) ApexPalette.NeonCyan
                    else ApexPalette.BorderGlass,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                tint = if (highlight) ApexPalette.NeonCyan else ApexPalette.NeonCyan.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (highlight) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Float.toDp() = androidx.compose.ui.unit.Dp(this /
    androidx.compose.ui.platform.LocalDensity.current.density)

@Composable
private fun Int.pxToDp(): androidx.compose.ui.unit.Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return androidx.compose.ui.unit.Dp(this / density.density)
}


