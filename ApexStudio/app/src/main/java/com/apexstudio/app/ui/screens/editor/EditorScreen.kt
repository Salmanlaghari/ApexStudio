package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val previewHeight = (screenWidthDp * 9f / 16f).dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexPalette.BgBase)
    ) {
        VideoPreviewCanvas(
            isPlaying = state.isPlaying,
            currentTimeMs = state.currentTimeMs,
            durationMs = state.durationMs,
            onTogglePlay = { vm.togglePlay() },
            onSeek = { vm.seekTo(it) },
            onPrev = { vm.seekTo((state.currentTimeMs - 5000).coerceAtLeast(0)) },
            onNext = { vm.seekTo((state.currentTimeMs + 5000).coerceAtMost(state.durationMs)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
        )

        MultiTrackTimeline(
            state = state,
            onScrub = { vm.seekTo(it) },
            onZoom = { vm.setZoom(it) },
            onSelectClip = { vm.selectClip(it) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        EditorBottomToolbar(
            onSplit = { vm.selectClip(state.selectedClipId) },
            onCut = { vm.selectClip(state.selectedClipId) },
            onFilters = onColor,
            onSpeed = { /* placeholder */ },
            onText = { /* placeholder */ },
            onFx = onAudio,
            onExport = onExport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun VideoPreviewCanvas(
    isPlaying: Boolean,
    currentTimeMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0B0E14), Color(0xFF121824))
                )
            )
            .border(
                width = 1.dp,
                color = ApexPalette.BorderGlass,
                shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
            )
    ) {
        // Mock cinematic gradient scene
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
        // Ground gradient
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF0B0E14))
                    )
                )
        )
        // Sun/moon
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(ApexPalette.NeonCyan.copy(alpha = 0.9f), Color.Transparent)
                    )
                )
        )

        // 8K badge top-left
        GlassBadge(
            label = "8K • 60fps • HDR",
            accent = ApexPalette.NeonCyan,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        )

        // Timecode top-right
        GlassBadge(
            label = TimeFormat.msToTimecode(currentTimeMs, includeFrames = true),
            accent = ApexPalette.NeonPurple,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        )

        // Tap to toggle play (covers whole surface but center row draws on top)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .clickable(onClick = onTogglePlay)
        )

        // Center playback controls
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonIconButton(
                icon = Icons.Default.SkipPrevious,
                onClick = onPrev,
                size = 42.dp,
                iconSize = 22.dp
            )
            NeonIconButton(
                icon = Icons.Default.FastRewind,
                onClick = { onSeek((currentTimeMs - 5000).coerceAtLeast(0)) },
                size = 42.dp,
                iconSize = 20.dp
            )
            PulsingPlayButton(isPlaying = isPlaying, onToggle = onTogglePlay)
            NeonIconButton(
                icon = Icons.Default.FastForward,
                onClick = { onSeek((currentTimeMs + 5000).coerceAtMost(durationMs)) },
                size = 42.dp,
                iconSize = 20.dp
            )
            NeonIconButton(
                icon = Icons.Default.SkipNext,
                onClick = onNext,
                size = 42.dp,
                iconSize = 22.dp
            )
        }
    }
}

@Composable
private fun GlassBadge(label: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ApexPalette.BgGlass)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MultiTrackTimeline(
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
            .background(ApexPalette.BgBase)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Ruler
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ApexPalette.BgSurface)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
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
                    val labelLeft = (t * pxPerMs - scroll.value - 18).coerceAtLeast(0f)
                    Spacer(Modifier.width(labelLeft.toDp()))
                    Text(
                        TimeFormat.msToShort(t),
                        color = ApexPalette.TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Tracks
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
                OverlayTrackRow(
                    label = "V2",
                    color = ApexPalette.TrackOverlay,
                    width = totalWidth,
                    pxPerMs = pxPerMs,
                    clips = clips.filter { it.type == com.apexstudio.app.domain.model.ClipType.OVERLAY }
                )
                WaveformTrackRow(
                    label = "A1",
                    color = ApexPalette.TrackAudio,
                    width = totalWidth,
                    pxPerMs = pxPerMs,
                    progress = state.currentTimeMs.toFloat() / state.durationMs.coerceAtLeast(1).toFloat(),
                    seed = 17L
                )
                WaveformTrackRow(
                    label = "FX",
                    color = ApexPalette.TrackSfx,
                    width = totalWidth,
                    pxPerMs = pxPerMs,
                    progress = state.currentTimeMs.toFloat() / state.durationMs.coerceAtLeast(1).toFloat(),
                    seed = 33L
                )
            }

            // Playhead
            val playheadX = (state.currentTimeMs * pxPerMs).toFloat() - scroll.value
            Column(
                modifier = Modifier
                    .offset(x = playheadX.toDp())
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .offset(x = (-5).dp)
                        .clip(CircleShape)
                        .background(ApexPalette.NeonCyan)
                )
            }

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

@Composable
private fun VideoTrackRow(
    label: String,
    color: Color,
    width: Int,
    pxPerMs: Float,
    clips: List<com.apexstudio.app.domain.model.MediaClip>,
    selectedClipId: String?,
    onSelectClip: (String?) -> Unit
) {
    val density = LocalDensity.current
    val trackHeightDp = 64.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeightDp + 8.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .height(trackHeightDp)
                .clip(RoundedCornerShape(8.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
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
    clip: com.apexstudio.app.domain.model.MediaClip,
    pxPerMs: Float,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val w = ((clip.trimEndMs - clip.trimStartMs) * pxPerMs).toInt().coerceAtLeast(40)
    val x = (clip.trimStartMs * pxPerMs).toInt()
    val borderColor = if (selected) ApexPalette.NeonCyan else Color.Transparent
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(x, 0) }
            .width(with(density) { w.toDp() })
            .fillMaxHeight()
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(ApexPalette.TrackVideo, ApexPalette.NeonPurple.copy(alpha = 0.7f))
                )
            )
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
    ) {
        // Thumbnails
        Row(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            repeat(6) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }
        }
        // Keyframe dots row
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.NeonCyan)
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
        // Left trim handle
        TrimHandle(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(8.dp)
                .fillMaxHeight()
        )
        // Right trim handle
        TrimHandle(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(8.dp)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun TrimHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(ApexPalette.NeonCyan.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(vertical = 4.dp)
                .width(2.dp)
                .height(20.dp)
                .background(ApexPalette.NeonCyan)
        )
    }
}

@Composable
private fun OverlayTrackRow(
    label: String,
    color: Color,
    width: Int,
    pxPerMs: Float,
    clips: List<com.apexstudio.app.domain.model.MediaClip>
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
        ) {
            val widthDp = with(density) { width.toDp() }
            Box(modifier = Modifier.width(widthDp)) {
                clips.forEach { clip ->
                    val w = ((clip.trimEndMs - clip.trimStartMs) * pxPerMs).toInt().coerceAtLeast(40)
                    val x = (clip.trimStartMs * pxPerMs).toInt()
                    Box(
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(x, 0) }
                            .width(with(density) { w.toDp() })
                            .fillMaxHeight()
                            .padding(2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(ApexPalette.TrackOverlay, ApexPalette.NeonPurple.copy(alpha = 0.5f))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    ) {
                        Text(
                            clip.name,
                            color = Color.White,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveformTrackRow(
    label: String,
    color: Color,
    width: Int,
    pxPerMs: Float,
    progress: Float,
    seed: Long
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(2.dp)
        ) {
            val widthDp = with(density) { width.toDp() }
            AudioWaveform(
                seed = seed,
                modifier = Modifier.width(widthDp).fillMaxHeight(),
                color = color,
                secondaryColor = color.copy(alpha = 0.3f),
                progress = progress,
                samples = (width / 4).coerceIn(80, 400)
            )
        }
    }
}

@Composable
private fun EditorBottomToolbar(
    onSplit: () -> Unit,
    onCut: () -> Unit,
    onFilters: () -> Unit,
    onSpeed: () -> Unit,
    onText: () -> Unit,
    onFx: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        ToolItem("Split", Icons.Default.ContentCut),
        ToolItem("Cut", Icons.Default.ContentCut),
        ToolItem("Filters", Icons.Default.FilterAlt),
        ToolItem("Speed", Icons.Default.Speed),
        ToolItem("Text", Icons.Default.TextFields),
        ToolItem("FX", Icons.Default.AutoAwesome)
    )
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ApexPalette.BgGlass)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { ti ->
                ToolbarButton(
                    label = ti.label,
                    icon = ti.icon,
                    onClick = when (ti.label) {
                        "Filters" -> onFilters
                        "FX" -> onFx
                        else -> onSplit
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Primary export CTA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                    )
                )
                .clickable(onClick = onExport),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.IosShare,
                    null,
                    tint = ApexPalette.BgDeep,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Export",
                    color = ApexPalette.BgDeep,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private data class ToolItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun ToolbarButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                tint = ApexPalette.NeonCyan,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = ApexPalette.TextSecondary,
            fontSize = 9.sp,
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
