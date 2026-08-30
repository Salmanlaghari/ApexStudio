package com.apexstudio.app.ui.screens.editor

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
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
    vm: EditorViewModel = viewModel(factory = EditorViewModelFactory(projectId = projectId))
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mediaPicker = remember { MediaPickerHelper(context) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var playbackSpeed by remember { mutableStateOf(1f) }

    mediaPicker.registerLaunchers()

    LaunchedEffect(Unit) {
        mediaPicker.pickedMedia.collect { metadataList ->
            if (metadataList.isNotEmpty()) {
                vm.onMediaPicked(metadataList)
            }
        }
    }

    LaunchedEffect(state.isMediaPickerOpen) {
        if (state.isMediaPickerOpen) {
            mediaPicker.pickMultipleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
            vm.closeMediaPicker()
        }
    }

    LaunchedEffect(Unit) {
        exoPlayer = ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    LaunchedEffect(exoPlayer, state.project?.clips, state.selectedClipId) {
        val player = exoPlayer ?: return@LaunchedEffect
        val clips = state.project?.clips ?: emptyList()
        val clip = clips.firstOrNull { it.id == state.selectedClipId } ?: clips.firstOrNull()
        if (clip != null) {
            val mediaItem = MediaItem.fromUri(Uri.parse(clip.uri))
            if (player.currentMediaItem?.mediaId != mediaItem.mediaId) {
                player.setMediaItem(mediaItem)
                player.prepare()
                vm.setPlayerDuration(clip.durationMs)
            }
        }
    }

    LaunchedEffect(exoPlayer, state.isPlaying) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (state.isPlaying) {
            player.play()
        } else {
            player.pause()
        }
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer?.playbackParameters = PlaybackParameters(playbackSpeed)
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
            playerDurationMs = state.playerDurationMs,
            onTogglePlay = { vm.togglePlay() },
            onSeek = { vm.seekTo(it) },
            onPrev = { vm.seekTo((state.playerPositionMs - 5000).coerceAtLeast(0)) },
            onNext = { vm.seekTo((state.playerPositionMs + 5000).coerceAtMost(state.durationMs)) },
            onAddMedia = { vm.openMediaPicker() },
            exoPlayer = exoPlayer,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.40f)
        )

        TimelineSection(
            state = state,
            onScrub = { vm.seekTo(it) },
            onZoom = { vm.setZoom(it) },
            onSelectClip = { vm.selectClip(it) },
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
            onFilters = onColor,
            onColor = onColor,
            onAudio = onAudio,
            onText = { /* placeholder */ },
            onFx = { /* placeholder */ },
            onExport = {},
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.15f)
        )
    }
}

@Composable
private fun EditorTopBar(
    currentTimeMs: Long,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
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
    playerDurationMs: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onAddMedia: () -> Unit,
    exoPlayer: ExoPlayer?,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val previewHeight = (screenWidthDp * 9f / 16f).dp
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (exoPlayer != null) {
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).also { pv ->
                            pv.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            pv.player = exoPlayer
                            pv.useController = false
                        }
                    },
                    update = { pv ->
                        pv.player = exoPlayer
                    }
                )
            } else {
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
            }

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

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonIconButton(
                    icon = Icons.Default.Add,
                    onClick = onAddMedia,
                    size = 36.dp,
                    iconSize = 18.dp,
                    tint = ApexPalette.NeonEmerald
                )
                NeonIconButton(
                    icon = Icons.Default.SkipPrevious,
                    onClick = onPrev,
                    size = 36.dp,
                    iconSize = 18.dp
                )
                NeonIconButton(
                    icon = Icons.Default.FastRewind,
                    onClick = { onSeek((currentTimeMs - 5000).coerceAtLeast(0)) },
                    size = 36.dp,
                    iconSize = 16.dp
                )
                NeonIconButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    onClick = onTogglePlay,
                    size = 44.dp,
                    iconSize = 22.dp
                )
                NeonIconButton(
                    icon = Icons.Default.FastForward,
                    onClick = { onSeek((currentTimeMs + 5000).coerceAtMost(playerDurationMs)) },
                    size = 36.dp,
                    iconSize = 16.dp
                )
                NeonIconButton(
                    icon = Icons.Default.SkipNext,
                    onClick = onNext,
                    size = 36.dp,
                    iconSize = 18.dp
                )
            }
        }
    }
}

@Composable
private fun TimelineSection(
    state: com.apexstudio.app.presentation.state.EditorState,
    onScrub: (Long) -> Unit,
    onZoom: (Float) -> Unit,
    onSelectClip: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
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
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(scroll.value.pxToDp()))
                listOf(0, 30_000L, 60_000L, 90_000L, 120_000L, 150_000L,
                    180_000L, 210_000L).forEach { t ->
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
                clips.forEach { clip ->
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
            .background(
                Brush.horizontalGradient(
                    listOf(
                        ApexPalette.NeonPurple.copy(alpha = 0.85f),
                        ApexPalette.TrackVideo.copy(alpha = 0.85f)
                    )
                )
            )
            .border(
                if (selected) 1.5.dp else 0.5.dp,
                if (selected) ApexPalette.NeonCyan else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(3.dp)
            )
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }
        }
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
    onFilters: () -> Unit,
    onColor: () -> Unit,
    onAudio: () -> Unit,
    onText: () -> Unit,
    onFx: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        val items = listOf(
            ToolDef("Split", Icons.Default.ContentCut, onSplit),
            ToolDef("Cut", Icons.Default.ContentCut, onCut),
            ToolDef("Speed", Icons.Default.Speed, onSpeed),
            ToolDef("Filters", Icons.Default.FilterAlt, onFilters),
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
                ToolbarIcon(tool.label, tool.icon, tool.onClick)
            }
        }
    }
}

private data class ToolDef(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun ToolbarIcon(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
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
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                tint = ApexPalette.NeonCyan,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = ApexPalette.TextSecondary,
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
