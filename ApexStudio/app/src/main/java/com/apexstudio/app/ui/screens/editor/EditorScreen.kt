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
    var playbackSpeed by remember { mutableStateOf(1f) }

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

    LaunchedEffect(exoPlayer, state.project?.clips, state.selectedClipId) {
        val player = exoPlayer ?: return@LaunchedEffect
        val clips = state.project?.clips ?: emptyList()
        val clip = clips.firstOrNull { it.id == state.selectedClipId } ?: clips.firstOrNull()
        if (clip != null) {
            val mediaItem = MediaItem.fromUri(Uri.parse(clip.uri))
            if (player.currentMediaItem?.mediaId != mediaItem.mediaId) {
                try {
                    Log.d("ApexTrace", "EditorScreen: preparing player for ${clip.uri}")
                    CrashMarker.mark(context, "EditorScreen: player.prepare() for ${clip.uri}")
                    // New media item — mark not ready until STATE_READY fires.
                    vm.setPlayerReady(false)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    Log.d("ApexTrace", "EditorScreen: player prepared")
                } catch (e: Exception) {
                    Log.e("EditorScreen", "player.prepare() failed", e)
                } finally {
                    CrashMarker.clear(context)
                }
                vm.setPlayerDuration(clip.durationMs)
            }
        }
    }

    LaunchedEffect(exoPlayer, state.isPlaying) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (state.isPlaying) {
            // If the previous play reached the end of the clip,
            // ExoPlayer is sitting at STATE_ENDED and play() alone is a
            // no-op. Rewind to 0 first so the next press actually
            // restarts playback from the beginning.
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        } else {
            player.pause()
        }
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer?.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // Apply the active LUT filter to the ExoPlayer GL surface. ExoPlayer
    // tears down and re-creates the GL pipeline on setVideoEffects, so
    // we only re-apply when the filter id or intensity actually changes.
    val activePreset = remember(state.activeFilterId, filterEngine) {
        val id = state.activeFilterId ?: return@remember null
        filterEngine.manifest.filters.firstOrNull { it.id == id }
    }
    LaunchedEffect(exoPlayer, activePreset?.id, state.filterIntensity) {
        val player = exoPlayer ?: return@LaunchedEffect
        val effects = if (activePreset != null && state.filterIntensity > 0f) {
            listOf<androidx.media3.common.Effect>(
                com.apexstudio.app.data.filter.LutFilterGlEffect(
                    context, activePreset, state.filterIntensity
                )
            )
        } else emptyList()
        try {
            player.setVideoEffects(effects)
        } catch (e: Exception) {
            Log.e("EditorScreen", "setVideoEffects failed", e)
        }
    }

    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (isActive) {
            val pos = player.currentPosition
            if (pos != state.playerPositionMs) {
                vm.setPlayerPosition(pos)
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
            onZoom = { vm.setZoom(it) },
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
            onSpeed = {
                playbackSpeed = when (playbackSpeed) {
                    1f -> 1.5f
                    1.5f -> 2f
                    else -> 1f
                }
            },
            onCrop = { vm.setCropMode(!state.cropMode) },
            cropActive = state.cropMode,
            cropAspect = state.cropAspect,
            onCropAspect = { vm.applyCropAspect(it) },
            onFilters = { vm.openFilterPanel() },
            filtersActive = state.activeFilterId != null || state.filterPanelOpen,
            onColor = onColor,
            onAudio = onAudio,
            onText = { /* placeholder */ },
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

            // The actual video surface. Only attached once the player is
            // built AND has reached STATE_READY. Gating on playerReady is
            // what avoids the native GL/EGL crash that previously forced
            // the PlayerView to be removed entirely.
            if (exoPlayer != null && playerReady) {
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
    val totalWidth = (state.durationMs * pxPerMs).toInt().coerceAtLeast(0)
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
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
                    onSelectClip = onSelectClip
                )
                VideoTrackRow(
                    label = "V2",
                    color = ApexPalette.TrackOverlay,
                    width = totalWidth,
                    pxPerMs = pxPerMs,
                    clips = clips.filter { it.type == com.apexstudio.app.domain.model.ClipType.OVERLAY },
                    selectedClipId = state.selectedClipId,
                    onSelectClip = onSelectClip
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
    onSelectClip: (String?) -> Unit
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
            Box(modifier = Modifier.width(widthDp)) {
                for (clip in clips) {
                    VideoClipBlock(
                        clip = clip,
                        pxPerMs = pxPerMs,
                        selected = selectedClipId == clip.id,
                        onSelect = { onSelectClip(clip.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoClipBlock(
    clip: MediaClip,
    pxPerMs: Float,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val w = ((clip.trimEndMs - clip.trimStartMs) * pxPerMs).toInt().coerceAtLeast(40)
    val x = (clip.trimStartMs * pxPerMs).toInt()
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(x, 0) }
            .width(with(density) { w.toDp() })
            .fillMaxHeight()
            .padding(1.dp)
            .clip(RoundedCornerShape(3.dp))
            .clickable(onClick = onSelect)
    ) {
        // Faux-frame-thumbnail background. Without an actual thumbnail
        // extraction pipeline we paint a repeating gradient + tile lines
        // that read as "video frames tiled across the clip" instead of
        // a flat purple block. The tile width is tuned to feel like a
        // ~0.5s filmstrip cell.
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
            // subtle vignette at the top + bottom
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
        // Border drawn on top of the canvas so it stays crisp.
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


