package com.apexstudio.app.ui.screens.editor

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexstudio.app.presentation.state.EditorTool
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.components.*
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat

@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onColor: () -> Unit,
    onAudio: () -> Unit,
    vm: EditorViewModel = viewModel(factory = EditorViewModelFactory())
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        AppTopBar(
            title = state.project?.name ?: "ApexStudio",
            subtitle = "4K • 60fps • HDR",
            onBack = onBack,
            onUndo = { vm.undo() },
            onRedo = { vm.redo() },
            onExport = onExport,
            canUndo = state.canUndo,
            canRedo = state.canRedo
        )

        // Player
        PlayerSurface(
            isPlaying = state.isPlaying,
            currentTimeMs = state.currentTimeMs,
            onTogglePlay = { vm.togglePlay() },
            onPrev = { vm.seekTo(state.currentTimeMs - 5000) },
            onNext = { vm.seekTo(state.currentTimeMs + 5000) },
            onStepBack = { vm.stepFrame(false) },
            onStepFwd = { vm.stepFrame(true) },
            onTap = { vm.togglePlay() },
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(220.dp)
        )

        Spacer(Modifier.height(10.dp))

        // Tool bar
        EditorToolBar(
            current = state.selectedTool,
            onSelect = { vm.selectTool(it) },
            onColor = onColor,
            onAudio = onAudio,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Timeline
        Timeline(
            state = state,
            onScrub = { vm.seekTo(it) },
            onZoom = { vm.setZoom(it) },
            onTrim = { id, s, e -> vm.trimClip(id, s, e) },
            onSplit = { id, at -> vm.splitClip(id, at) },
            onSelectClip = { vm.selectClip(it) },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        )

        BottomNavBar(
            current = "Edit",
            items = listOf(
                BottomNavItem("Project", Icons.Default.Folder, false),
                BottomNavItem("Media", Icons.Default.Movie, false),
                BottomNavItem("Edit", Icons.Default.Tune, true),
                BottomNavItem("Export", Icons.Default.IosShare, false)
            ),
            onSelect = { /* TODO */ }
        )
    }
}

@Composable
private fun PlayerSurface(
    isPlaying: Boolean,
    currentTimeMs: Long,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onStepBack: () -> Unit,
    onStepFwd: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(
                listOf(Color(0xFF0F1A2D), Color(0xFF1B0F2D))
            ))
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(22.dp))
    ) {
        // Mock preview gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1B2A4E),
                            Color(0xFF3A1B5E),
                            Color(0xFF0E2B3F)
                        )
                    )
                )
        )
        // Mock landscape scene
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF0A1224))
                    )
                )
        )
        // Sun/moon
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(ApexPalette.NeonCyan.copy(alpha = 0.9f), Color.Transparent)
                    )
                )
        )

        // Timecode top-left
        GlassCard(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            cornerRadius = 10.dp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                TimeFormat.msToTimecode(currentTimeMs, includeFrames = true),
                color = ApexPalette.NeonCyan,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 4K badge top-right
        GlassCard(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            cornerRadius = 10.dp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                "4K 60",
                color = ApexPalette.TextPrimary,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Center controls
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonIconButton(
                icon = Icons.Default.SkipPrevious,
                onClick = onPrev,
                size = 40.dp,
                iconSize = 22.dp
            )
            NeonIconButton(
                icon = Icons.Default.FastRewind,
                onClick = onStepBack,
                size = 40.dp,
                iconSize = 18.dp
            )
            PulsingPlayButton(isPlaying = isPlaying, onToggle = onTogglePlay)
            NeonIconButton(
                icon = Icons.Default.FastForward,
                onClick = onStepFwd,
                size = 40.dp,
                iconSize = 18.dp
            )
            NeonIconButton(
                icon = Icons.Default.SkipNext,
                onClick = onNext,
                size = 40.dp,
                iconSize = 22.dp
            )
        }
    }
}

@Composable
private fun EditorToolBar(
    current: EditorTool,
    onSelect: (EditorTool) -> Unit,
    onColor: () -> Unit,
    onAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = listOf(
        EditorTool.SPLIT to Icons.Default.ContentCut,
        EditorTool.TRIM to Icons.Default.ContentCut,
        EditorTool.KEYFRAME to Icons.Default.Timeline,
        EditorTool.TRANSITION to Icons.Default.CompareArrows,
        EditorTool.EFFECTS to Icons.Default.AutoAwesome,
        EditorTool.AUDIO to Icons.Default.GraphicEq,
        EditorTool.TEXT to Icons.Default.TextFields,
        EditorTool.COLOR to Icons.Default.Palette
    )
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ApexPalette.BgGlass)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tools) { (tool, icon) ->
            val sel = tool == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (sel) ApexPalette.NeonCyan.copy(alpha = 0.2f) else Color.Transparent
                    )
                    .border(
                        1.dp,
                        if (sel) ApexPalette.NeonCyan else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        onSelect(tool)
                        if (tool == EditorTool.COLOR) onColor()
                        if (tool == EditorTool.AUDIO) onAudio()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(icon, null,
                    tint = if (sel) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    tool.label,
                    color = if (sel) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun Timeline(
    state: com.apexstudio.app.presentation.state.EditorState,
    onScrub: (Long) -> Unit,
    onZoom: (Float) -> Unit,
    onTrim: (String, Long, Long) -> Unit,
    onSplit: (String, Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val clips = state.project?.clips ?: emptyList()
    val density = LocalDensity.current
    val basePxPerMs = with(density) { 0.18f.dp.toPx() }
    val pxPerMs = basePxPerMs * state.zoomLevel
    val totalWidth = (state.durationMs * pxPerMs).toInt().coerceAtLeast(0)
    val scroll = rememberScrollState()

    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartTime by remember { mutableStateOf(0L) }
    var dragMode by remember { mutableStateOf<DragMode?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Ruler
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(ApexPalette.BgElevated)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val tickEvery = 10_000L
                var t = 0L
                while (t <= 240_000L) {
                    val x = (t * pxPerMs).toFloat() - scroll.value
                    if (x in 0f..w) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.18f),
                            start = Offset(x, size.height * 0.5f),
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
                listOf(0, 30_000L, 60_000L, 90_000L, 120_000L, 150_000L,
                    180_000L, 210_000L).forEach { t ->
                    Text(
                        TimeFormat.msToShort(t),
                        color = ApexPalette.TextTertiary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = (t * pxPerMs - scroll.value - 20).toDp())
                    )
                }
            }
        }

        // Tracks
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ApexPalette.BgSurface)
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
                // Video track
                TrackRow(
                    label = "V1",
                    color = ApexPalette.TrackVideo,
                    width = totalWidth,
                    pxPerMs = pxPerMs,
                    height = 80.dp
                ) {
                    clips.filter { it.type == com.apexstudio.app.domain.model.ClipType.VIDEO }
                        .forEach { clip ->
                            ClipBlock(
                                clip = clip,
                                pxPerMs = pxPerMs,
                                selected = state.selectedClipId == clip.id,
                                onSelect = { onSelectClip(clip.id) },
                                onDragStart = { mode, x, t ->
                                    draggingClipId = clip.id
                                    dragStartX = x
                                    dragStartTime = t
                                    dragMode = mode
                                },
                                onDragUpdate = { mode, delta, _ ->
                                    val dms = (delta / pxPerMs).toLong()
                                    when (mode) {
                                        DragMode.LEFT -> onTrim(
                                            clip.id,
                                            (clip.trimStartMs + dms).coerceAtLeast(0),
                                            clip.trimEndMs
                                        )
                                        DragMode.RIGHT -> onTrim(
                                            clip.id,
                                            clip.trimStartMs,
                                            (clip.trimEndMs + dms).coerceAtMost(clip.durationMs)
                                        )
                                        DragMode.MOVE -> {}
                                    }
                                }
                            )
                        }
                }

                // Overlay track
                TrackRow(
                    label = "V2",
                    color = ApexPalette.TrackOverlay,
                    width = totalWidth,
                    pxPerMs = pxPerMs,
                    height = 56.dp
                ) {
                    clips.filter { it.type == com.apexstudio.app.domain.model.ClipType.OVERLAY }
                        .forEach { clip ->
                            OverlayClipBlock(
                                clip = clip,
                                pxPerMs = pxPerMs
                            )
                        }
                }

                // Audio track
                AudioTrackRow(
                    label = "A1",
                    width = totalWidth,
                    pxPerMs = pxPerMs,
                    seed = 17L,
                    progress = state.currentTimeMs.toFloat() /
                        state.durationMs.coerceAtLeast(1).toFloat()
                )

                // SFX
                AudioTrackRow(
                    label = "FX",
                    width = totalWidth,
                    pxPerMs = pxPerMs,
                    seed = 33L,
                    color = ApexPalette.TrackSfx,
                    progress = state.currentTimeMs.toFloat() /
                        state.durationMs.coerceAtLeast(1).toFloat()
                )
            }

            // Playhead
            val playheadX = (state.currentTimeMs * pxPerMs).toFloat() - scroll.value
            Column(
                modifier = Modifier
                    .offset(x = playheadX.toDp())
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(ApexPalette.NeonCyan)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .offset(x = (-6).dp)
                        .clip(CircleShape)
                        .background(ApexPalette.NeonCyan)
                )
            }

            // Tap to scrub
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.durationMs, pxPerMs) {
                        detectTapGestures { off ->
                            val t = ((off.x + scroll.value) / pxPerMs).toLong()
                            onScrub(t)
                        }
                    }
            )
        }
    }
}

private enum class DragMode { LEFT, RIGHT, MOVE }

@Composable
private fun Float.toDp() = androidx.compose.ui.unit.Dp(this /
    androidx.compose.ui.platform.LocalDensity.current.density)

@Composable
private fun TrackRow(
    label: String,
    color: Color,
    width: Int,
    pxPerMs: Float,
    height: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height + 6.dp)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
                .background(ApexPalette.BgElevated)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .height(height)
                .background(ApexPalette.BgBase)
                .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
        ) {
            Box(modifier = Modifier.width(width.pxToDp())) { content() }
        }
    }
}

@Composable
private fun Int.pxToDp(): androidx.compose.ui.unit.Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return androidx.compose.ui.unit.Dp(this / density.density)
}

@Composable
private fun ClipBlock(
    clip: com.apexstudio.app.domain.model.MediaClip,
    pxPerMs: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onDragStart: (DragMode, Float, Long) -> Unit,
    onDragUpdate: (DragMode, Float, Long) -> Unit
) {
    val w = ((clip.trimEndMs - clip.trimStartMs) * pxPerMs).toInt().coerceAtLeast(40)
    val x = (clip.trimStartMs * pxPerMs).toInt()
    val borderColor = if (selected) ApexPalette.NeonCyan else Color.Transparent
    Box(
        modifier = Modifier
            .offset(x = x.pxToDp())
            .width(w.pxToDp())
            .fillMaxHeight()
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(
                listOf(ApexPalette.TrackVideo, ApexPalette.NeonPurple)
            ))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .pointerInput(clip.id) {
                detectDragGestures(
                    onDragStart = { off -> onDragStart(DragMode.MOVE, off.x, 0L) },
                    onDrag = { change, drag -> onDragUpdate(DragMode.MOVE, drag.x, 0L) }
                )
            }
    ) {
        // thumbnails row
        Row(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            repeat(6) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                )
            }
        }
        // Title
        Text(
            clip.name,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
        )
        // Left handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(8.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.3f))
                .pointerInput(clip.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart(DragMode.LEFT, it.x, 0L) },
                        onDrag = { _, drag -> onDragUpdate(DragMode.LEFT, drag.x, 0L) }
                    )
                }
        )
        // Right handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(8.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.3f))
                .pointerInput(clip.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart(DragMode.RIGHT, it.x, 0L) },
                        onDrag = { _, drag -> onDragUpdate(DragMode.RIGHT, drag.x, 0L) }
                    )
                }
        )
    }
}

@Composable
private fun OverlayClipBlock(
    clip: com.apexstudio.app.domain.model.MediaClip,
    pxPerMs: Float
) {
    val w = ((clip.trimEndMs - clip.trimStartMs) * pxPerMs).toInt().coerceAtLeast(40)
    val x = (clip.trimStartMs * pxPerMs).toInt()
    Box(
        modifier = Modifier
            .offset(x = x.pxToDp())
            .width(w.pxToDp())
            .fillMaxHeight()
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(
                listOf(ApexPalette.TrackOverlay, ApexPalette.NeonPurple.copy(alpha = 0.6f))
            ))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
    ) {
        Text(clip.name, color = Color.White, fontSize = 9.sp,
            modifier = Modifier.padding(4.dp))
    }
}

@Composable
private fun AudioTrackRow(
    label: String,
    width: Int,
    pxPerMs: Float,
    seed: Long,
    color: Color = ApexPalette.TrackAudio,
    progress: Float = 0.5f
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
                .background(ApexPalette.BgElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .height(56.dp)
                .background(ApexPalette.BgBase)
                .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(2.dp)
        ) {
            AudioWaveform(
                seed = seed,
                modifier = Modifier.width(width.pxToDp()).fillMaxHeight(),
                color = color,
                secondaryColor = color.copy(alpha = 0.4f),
                progress = progress,
                samples = (width / 4).coerceIn(80, 400)
            )
        }
    }
}
