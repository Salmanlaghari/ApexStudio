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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.components.AudioWaveform
import com.apexstudio.app.ui.components.NeonIconButton
import com.apexstudio.app.ui.components.PulsingPlayButton
import com.apexstudio.app.ui.components.ScreenTopBar
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
        ScreenTopBar(
            title = "MY PROJECT",
            onBack = onBack,
            onExport = onExport
        )

        // Video preview locked at top
        Box(
            modifier = Modifier
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
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(16.dp))
                    .clickable(onClick = { vm.togglePlay() }),
                contentAlignment = Alignment.Center
            ) {
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
                    if (!state.isPlaying) {
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
                // Timecode top-right
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
                        "${TimeFormat.msToShort(state.currentTimeMs)} / ${TimeFormat.msToShort(state.durationMs)}",
                        color = ApexPalette.NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
                // Center playback controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeonIconButton(
                        icon = Icons.Default.SkipPrevious,
                        onClick = { vm.seekTo((state.currentTimeMs - 5000).coerceAtLeast(0)) },
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                    NeonIconButton(
                        icon = Icons.Default.FastRewind,
                        onClick = { vm.seekTo((state.currentTimeMs - 5000).coerceAtLeast(0)) },
                        size = 36.dp,
                        iconSize = 16.dp
                    )
                    PulsingPlayButton(isPlaying = state.isPlaying, onToggle = { vm.togglePlay() })
                    NeonIconButton(
                        icon = Icons.Default.FastForward,
                        onClick = { vm.seekTo((state.currentTimeMs + 5000).coerceAtMost(state.durationMs)) },
                        size = 36.dp,
                        iconSize = 16.dp
                    )
                    NeonIconButton(
                        icon = Icons.Default.SkipNext,
                        onClick = { vm.seekTo((state.currentTimeMs + 5000).coerceAtMost(state.durationMs)) },
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                }
            }
        }

        // Ruler + Tracks + Tool bar — share remaining height
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Ruler row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .padding(horizontal = 12.dp)
            ) {
                TimelineRuler(
                    state = state,
                    scroll = rememberScrollState(),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Tracks
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexPalette.BgSurface)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            vm.setZoom(zoom)
                        }
                    }
            ) {
                val scroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scroll)
                ) {
                    VideoTrackRow(
                        label = "V1",
                        color = ApexPalette.TrackVideo,
                        width = 1400,
                        pxPerMs = 0.16f * state.zoomLevel,
                        clips = state.project?.clips?.filter { it.type == com.apexstudio.app.domain.model.ClipType.VIDEO }.orEmpty(),
                        selectedClipId = state.selectedClipId,
                        onSelectClip = { vm.selectClip(it) }
                    )
                    VideoTrackRow(
                        label = "V2",
                        color = ApexPalette.TrackOverlay,
                        width = 1400,
                        pxPerMs = 0.16f * state.zoomLevel,
                        clips = state.project?.clips?.filter { it.type == com.apexstudio.app.domain.model.ClipType.OVERLAY }.orEmpty(),
                        selectedClipId = state.selectedClipId,
                        onSelectClip = { vm.selectClip(it) }
                    )
                    WaveformTrackRow(
                        label = "A1",
                        color = ApexPalette.NeonEmerald,
                        width = 1400,
                        pxPerMs = 0.16f * state.zoomLevel,
                        progress = state.currentTimeMs.toFloat() / state.durationMs.coerceAtLeast(1).toFloat(),
                        seed = 17L
                    )
                    WaveformTrackRow(
                        label = "FX",
                        color = ApexPalette.TrackAudio,
                        width = 1400,
                        pxPerMs = 0.16f * state.zoomLevel,
                        progress = state.currentTimeMs.toFloat() / state.durationMs.coerceAtLeast(1).toFloat(),
                        seed = 33L
                    )
                }

                // Playhead overlay
                val pxPerMs = 0.16f * state.zoomLevel
                val playheadX = (state.currentTimeMs * pxPerMs).toFloat() - scroll.value
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val x = playheadX
                    if (x in 0f..size.width) {
                        drawLine(
                            brush = Brush.verticalGradient(
                                listOf(
                                    ApexPalette.NeonCyan.copy(alpha = 0.0f),
                                    ApexPalette.NeonCyan.copy(alpha = 0.6f),
                                    ApexPalette.NeonCyan.copy(alpha = 0.6f),
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
                                vm.seekTo(t)
                            }
                        }
                )
            }

            // Horizontal tool bar at the bottom of editor body
            val toolItems = listOf(
                ToolDef("Edit", Icons.Default.Tune, true),
                ToolDef("Audio", Icons.Default.GraphicEq, false),
                ToolDef("FX", Icons.Default.AutoAwesome, false),
                ToolDef("Text", Icons.Default.TextFields, false),
                ToolDef("Stickers", Icons.Default.EmojiEmotions, false),
                ToolDef("Overlay", Icons.Default.Layers, false),
                ToolDef("Crop", Icons.Default.Crop, false),
                ToolDef("Transition", Icons.Default.CompareArrows, false)
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(toolItems) { tool ->
                    ToolChip(tool = tool, onClick = {
                        if (tool.label == "Audio") onAudio()
                        if (tool.label == "Edit") { /* already here */ }
                    })
                }
            }
        }
    }
}

@Composable
private fun TimelineRuler(
    state: com.apexstudio.app.presentation.state.EditorState,
    scroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier
) {
    val pxPerMs = 0.16f * state.zoomLevel
    Box(modifier = modifier) {
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
    val trackHeightDp = 40.dp
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
    clip: com.apexstudio.app.domain.model.MediaClip,
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
            .clip(RoundedCornerShape(4.dp))
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
                RoundedCornerShape(4.dp)
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
            .height(36.dp)
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
                .height(30.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(4.dp))
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

private data class ToolDef(
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val onClick: () -> Unit = {}
)

@Composable
private fun ToolChip(tool: ToolDef, onClick: () -> Unit) {
    val bg = if (tool.active) ApexPalette.BgGlass else Color.Transparent
    val border = if (tool.active) ApexPalette.NeonCyan else ApexPalette.BorderGlass
    val tint = if (tool.active) ApexPalette.NeonCyan else ApexPalette.TextSecondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                tool.icon, null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            tool.label,
            color = tint,
            fontSize = 8.sp,
            fontWeight = if (tool.active) FontWeight.Bold else FontWeight.Medium
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
