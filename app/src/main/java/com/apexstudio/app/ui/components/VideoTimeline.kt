package com.apexstudio.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Modern Multi-Layer Video Timeline Component for ApexStudio.
 *
 * Features:
 * 1. Horizontal Scrolling with synchronized time ruler, playhead, and zoom scaling.
 * 2. Multi-layer Track Visualization:
 *    - Overlay Track (V2: PIP Video, Text Titles, Stickers)
 *    - Main Video Track (V1: Multi-clip Filmstrip, Trim Indicators, Speed Badges)
 *    - FX Track (Visual Effects / LUT Presets)
 *    - Audio Track (A1: Precision Waveforms, Beat Markers, Volume Badges)
 * 3. Drag-and-Drop Reordering for Clips with live elevation, preview drop slots, and animated guide lines.
 * 4. Pinned Left Track Headers for instant track identification, mute/solo, and zoom controls.
 */
@Composable
fun VideoTimeline(
    clips: List<MediaClip>,
    audioTracks: List<AudioTrack> = emptyList(),
    textOverlays: List<TextOverlay> = emptyList(),
    stickers: List<StickerOverlay> = emptyList(),
    activeFxId: String? = null,
    totalDurationMs: Long,
    playheadMs: Long,
    timelineZoom: Float = 1.0f,
    selectedClipId: String? = null,
    isPlaying: Boolean = false,
    audioWaveform: FloatArray = FloatArray(0),
    beatMarkersMs: List<Long> = emptyList(),
    snapToBeat: Boolean = false,
    perSecondThumbnails: Map<Int, Bitmap> = emptyMap(),
    onScrub: (Long) -> Unit = {},
    onSelectClip: (String) -> Unit = {},
    onReorderClips: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onAddMedia: (ClipType) -> Unit = {},
    onSplitClip: (clipId: String, atMs: Long) -> Unit = { _, _ -> },
    onDuplicateClip: (clipId: String) -> Unit = {},
    onDeleteClip: (clipId: String) -> Unit = {},
    onZoomChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val safeDurationMs = totalDurationMs.coerceAtLeast(1000L)
    val safePlayheadMs = playheadMs.coerceIn(0L, safeDurationMs)
    val effectiveZoom = timelineZoom.coerceIn(0.5f, 5.0f)

    // Scaling: dp per second based on zoom factor
    val secondWidthDp = (54.dp * effectiveZoom).coerceIn(24.dp, 240.dp)
    val msToDp = secondWidthDp.value / 1000f

    // Total content width calculation
    val timelineWidthDp = maxOf(
        (safeDurationMs * msToDp).dp + 280.dp,
        800.dp
    )
    val timelineWidthPx = with(density) { timelineWidthDp.toPx() }

    // Auto-scroll when playing
    LaunchedEffect(isPlaying, safePlayheadMs) {
        if (isPlaying) {
            val playheadX = safePlayheadMs * msToDp
            val playheadPx = with(density) { playheadX.dp.toPx() }.roundToInt()
            val viewportWidth = scrollState.viewportSize
            val targetScroll = (playheadPx - viewportWidth / 2).coerceAtLeast(0)
            if (kotlin.math.abs(scrollState.value - targetScroll) > 120) {
                scrollState.animateScrollTo(targetScroll)
            }
        }
    }

    // Drag-and-drop state for video clips
    val videoClips = remember(clips) { clips.filter { it.type == ClipType.VIDEO } }
    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragAccumulatedOffsetPx by remember { mutableFloatStateOf(0f) }
    var targetDropIndex by remember { mutableIntStateOf(-1) }

    // Measured clip bounds in horizontal track space (index -> Pair(leftPx, widthPx))
    val clipLayoutBounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }

    // Tracks Mute / Active states
    var isAudioMuted by remember { mutableStateOf(false) }
    var isOverlayVisible by remember { mutableStateOf(true) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(Color(0xFF090B10))
            .border(1.dp, Color(0xFF171B26))
    ) {
        // --- 1. PINNED LEFT TRACK HEADER SIDEBAR ---
        TimelineLeftSidebar(
            zoomFactor = effectiveZoom,
            isAudioMuted = isAudioMuted,
            isOverlayVisible = isOverlayVisible,
            onZoomIn = { onZoomChange((effectiveZoom + 0.5f).coerceAtMost(5.0f)) },
            onZoomOut = { onZoomChange((effectiveZoom - 0.5f).coerceAtLeast(0.5f)) },
            onToggleAudioMute = { isAudioMuted = !isAudioMuted },
            onToggleOverlayVisible = { isOverlayVisible = !isOverlayVisible },
            onAddVideo = { onAddMedia(ClipType.VIDEO) },
            onAddOverlay = { onAddMedia(ClipType.OVERLAY) }
        )

        // --- 2. HORIZONTALLY SCROLLABLE TIMELINE CANVAS ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier
                    .width(timelineWidthDp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // TRACK 0: Time Ruler & Beat Markers
                    TimelineTimeRuler(
                        durationMs = safeDurationMs,
                        secondWidthDp = secondWidthDp,
                        msToDp = msToDp,
                        beatMarkersMs = beatMarkersMs,
                        snapToBeat = snapToBeat,
                        onScrub = onScrub,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )

                    // TRACK 1: V2 (Overlay / PIP / Text / Stickers)
                    TimelineOverlayTrack(
                        clips = clips.filter { it.type == ClipType.OVERLAY },
                        textOverlays = textOverlays,
                        stickers = stickers,
                        selectedClipId = selectedClipId,
                        msToDp = msToDp,
                        isVisible = isOverlayVisible,
                        onSelectClip = onSelectClip,
                        onAddOverlay = { onAddMedia(ClipType.OVERLAY) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    )

                    // TRACK 2: V1 (Main Video Track with Drag-and-Drop Reordering)
                    TimelineVideoTrack(
                        videoClips = videoClips,
                        selectedClipId = selectedClipId,
                        msToDp = msToDp,
                        playheadMs = safePlayheadMs,
                        perSecondThumbnails = perSecondThumbnails,
                        draggingClipId = draggingClipId,
                        dragAccumulatedOffsetPx = dragAccumulatedOffsetPx,
                        targetDropIndex = targetDropIndex,
                        onSelectClip = onSelectClip,
                        onClipBoundsMeasured = { idx, leftPx, widthPx ->
                            clipLayoutBounds[idx] = Pair(leftPx, widthPx)
                        },
                        onDragStart = { clip ->
                            draggingClipId = clip.id
                            dragAccumulatedOffsetPx = 0f
                            val origIdx = videoClips.indexOfFirst { it.id == clip.id }
                            targetDropIndex = origIdx
                        },
                        onDrag = { deltaX ->
                            dragAccumulatedOffsetPx += deltaX
                            val origIdx = videoClips.indexOfFirst { it.id == draggingClipId }
                            if (origIdx != -1) {
                                val currentBound = clipLayoutBounds[origIdx]
                                if (currentBound != null) {
                                    val currentCenterX = currentBound.first + (currentBound.second / 2f) + dragAccumulatedOffsetPx
                                    // Find new target drop slot
                                    var newIdx = origIdx
                                    clipLayoutBounds.forEach { (idx, bounds) ->
                                        val clipCenter = bounds.first + (bounds.second / 2f)
                                        if (deltaX > 0 && currentCenterX > clipCenter && idx > newIdx) {
                                            newIdx = idx
                                        } else if (deltaX < 0 && currentCenterX < clipCenter && idx < newIdx) {
                                            newIdx = idx
                                        }
                                    }
                                    targetDropIndex = newIdx.coerceIn(0, videoClips.size - 1)
                                }
                            }
                        },
                        onDragEnd = {
                            val origIdx = videoClips.indexOfFirst { it.id == draggingClipId }
                            if (origIdx != -1 && targetDropIndex != -1 && origIdx != targetDropIndex) {
                                onReorderClips(origIdx, targetDropIndex)
                            }
                            draggingClipId = null
                            dragAccumulatedOffsetPx = 0f
                            targetDropIndex = -1
                        },
                        onAddClip = { onAddMedia(ClipType.VIDEO) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    )

                    // TRACK 3: FX / Shader Track
                    TimelineFxTrack(
                        activeFxId = activeFxId,
                        durationMs = safeDurationMs,
                        msToDp = msToDp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )

                    // TRACK 4: A1 (Audio Waveform Track)
                    TimelineAudioTrack(
                        audioTracks = audioTracks,
                        durationMs = safeDurationMs,
                        msToDp = msToDp,
                        isMuted = isAudioMuted,
                        audioWaveform = audioWaveform,
                        beatMarkersMs = beatMarkersMs,
                        snapToBeat = snapToBeat,
                        playheadMs = safePlayheadMs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    )
                }

                // --- 3. UNIFIED VERTICAL PLAYHEAD LINE & TIMESTAMP BADGE ---
                val playheadXDp = (safePlayheadMs * msToDp).dp + 16.dp
                TimelinePlayhead(
                    playheadX = playheadXDp,
                    playheadMs = safePlayheadMs,
                    onScrub = onScrub,
                    msToDp = msToDp
                )
            }
        }
    }
}

/**
 * State-driven convenience overload for easy drop-in usage across screens.
 */
@Composable
fun VideoTimeline(
    state: EditorState,
    onScrub: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onReorderClips: (fromIndex: Int, toIndex: Int) -> Unit,
    onAddMedia: (ClipType) -> Unit,
    onSplitClip: (clipId: String, atMs: Long) -> Unit,
    onDuplicateClip: (clipId: String) -> Unit,
    onDeleteClip: (clipId: String) -> Unit,
    onZoomChange: (Float) -> Unit,
    perSecondThumbnails: Map<Int, Bitmap> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val clips = state.project?.clips ?: emptyList()
    val audioTracks = state.project?.audioTracks ?: emptyList()
    val textOverlays = clips.flatMap { it.textOverlays }
    val stickers = clips.flatMap { it.stickers }

    VideoTimeline(
        clips = clips,
        audioTracks = audioTracks,
        textOverlays = textOverlays,
        stickers = stickers,
        activeFxId = state.activeFxId,
        totalDurationMs = state.durationMs,
        playheadMs = state.playerPositionMs,
        timelineZoom = state.timelineZoom,
        selectedClipId = state.selectedClipId,
        isPlaying = state.isPlaying,
        audioWaveform = state.audioWaveform,
        beatMarkersMs = state.beatMarkersMs,
        snapToBeat = state.snapToBeat,
        perSecondThumbnails = perSecondThumbnails,
        onScrub = onScrub,
        onSelectClip = { onSelectClip(it) },
        onReorderClips = onReorderClips,
        onAddMedia = onAddMedia,
        onSplitClip = onSplitClip,
        onDuplicateClip = onDuplicateClip,
        onDeleteClip = onDeleteClip,
        onZoomChange = onZoomChange,
        modifier = modifier
    )
}

// ==========================================
// SUB-COMPONENTS
// ==========================================

/**
 * Pinned Left Header showing Track names, icons, mute/solo states, and zoom tools.
 */
@Composable
private fun TimelineLeftSidebar(
    zoomFactor: Float,
    isAudioMuted: Boolean,
    isOverlayVisible: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleAudioMute: () -> Unit,
    onToggleOverlayVisible: () -> Unit,
    onAddVideo: () -> Unit,
    onAddOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(Color(0xFF0C0E14))
            .border(width = 1.dp, color = Color(0xFF1E2230))
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Zoom toolbar at header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF141822))
                .border(0.5.dp, Color(0xFF262E40), RoundedCornerShape(6.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onZoomOut),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
            }
            Text(
                text = "${String.format(java.util.Locale.US, "%.1f", zoomFactor)}x",
                color = ApexPalette.NeonCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onZoomIn),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
            }
        }

        // Track V2: Overlay Header
        TrackHeaderBadge(
            trackLabel = "V2",
            trackName = "Overlay",
            icon = Icons.Default.Layers,
            accentColor = ApexPalette.NeonCyan,
            height = 38.dp,
            actionIcon = Icons.Default.Add,
            onAction = onAddOverlay
        )

        // Track V1: Main Video Header
        TrackHeaderBadge(
            trackLabel = "V1",
            trackName = "Video",
            icon = Icons.Default.Movie,
            accentColor = ApexPalette.TrackVideo,
            height = 64.dp,
            actionIcon = Icons.Default.Add,
            onAction = onAddVideo
        )

        // Track FX: Effects Header
        TrackHeaderBadge(
            trackLabel = "FX",
            trackName = "Filter",
            icon = Icons.Default.AutoAwesome,
            accentColor = ApexPalette.NeonAmber,
            height = 28.dp
        )

        // Track A1: Audio Header
        TrackHeaderBadge(
            trackLabel = "A1",
            trackName = "Audio",
            icon = Icons.Default.GraphicEq,
            accentColor = ApexPalette.NeonEmerald,
            height = 44.dp,
            actionIcon = if (isAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            actionActive = isAudioMuted,
            onAction = onToggleAudioMute
        )
    }
}

@Composable
private fun TrackHeaderBadge(
    trackLabel: String,
    trackName: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    height: Dp,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    actionActive: Boolean = false,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF10141E))
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(trackLabel, color = accentColor, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
                Icon(icon, contentDescription = trackName, tint = accentColor, modifier = Modifier.size(12.dp))
            }
            Text(
                trackName,
                color = Color(0xFF94A3B8),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }

        if (actionIcon != null && onAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (actionActive) ApexPalette.NeonPink.copy(alpha = 0.3f) else Color(0xFF1E2433))
                    .clickable(onClick = onAction),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    actionIcon,
                    contentDescription = null,
                    tint = if (actionActive) ApexPalette.NeonPink else Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

/**
 * Time Ruler with scale tick marks and beat diamond markers.
 */
@Composable
private fun TimelineTimeRuler(
    durationMs: Long,
    secondWidthDp: Dp,
    msToDp: Float,
    beatMarkersMs: List<Long>,
    snapToBeat: Boolean,
    onScrub: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0D1018))
            .pointerInput(durationMs, msToDp) {
                detectTapGestures { offset ->
                    val scrubMs = ((offset.x / density) / msToDp).toLong().coerceIn(0L, durationMs)
                    onScrub(scrubMs)
                }
            }
            .pointerInput(durationMs, msToDp) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val scrubMs = ((change.position.x / density) / msToDp).toLong().coerceIn(0L, durationMs)
                    onScrub(scrubMs)
                }
            }
    ) {
        val totalSec = (durationMs / 1000L).toInt().coerceAtLeast(1)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height

            // Render sub-second and second tick marks
            for (sec in 0..totalSec + 2) {
                val secXPx = (sec * 1000L * msToDp).dp.toPx()

                // Half-second tick
                val halfXPx = ((sec * 1000L + 500L) * msToDp).dp.toPx()
                drawLine(
                    color = Color(0xFF263042),
                    start = Offset(halfXPx, h - 6f),
                    end = Offset(halfXPx, h),
                    strokeWidth = 1f
                )

                // Full second tick
                drawLine(
                    color = Color(0xFF475569),
                    start = Offset(secXPx, h - 12f),
                    end = Offset(secXPx, h),
                    strokeWidth = 1.5f
                )
            }

            // Beat Markers (Cyan diamonds on ruler)
            if (snapToBeat && beatMarkersMs.isNotEmpty()) {
                beatMarkersMs.forEach { beatMs ->
                    val beatXPx = (beatMs * msToDp).dp.toPx()
                    val diamond = Path().apply {
                        moveTo(beatXPx, h - 10f)
                        lineTo(beatXPx + 3.5f, h - 6f)
                        lineTo(beatXPx, h - 2f)
                        lineTo(beatXPx - 3.5f, h - 6f)
                        close()
                    }
                    drawPath(diamond, color = ApexPalette.NeonCyan)
                }
            }
        }

        // Time labels every few seconds based on zoom
        val stepSec = when {
            secondWidthDp > 80.dp -> 1
            secondWidthDp > 40.dp -> 2
            else -> 5
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) {
            for (sec in 0..totalSec step stepSec) {
                val secOffsetDp = (sec * 1000L * msToDp).dp
                Box(
                    modifier = Modifier
                        .offset(x = secOffsetDp)
                        .padding(start = 2.dp, top = 2.dp)
                ) {
                    Text(
                        text = TimeFormat.msToShort(sec * 1000L),
                        color = Color(0xFF64748B),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Overlay Track (V2: Picture-in-Picture videos, text titles, and stickers).
 */
@Composable
private fun TimelineOverlayTrack(
    clips: List<MediaClip>,
    textOverlays: List<TextOverlay>,
    stickers: List<StickerOverlay>,
    selectedClipId: String?,
    msToDp: Float,
    isVisible: Boolean,
    onSelectClip: (String) -> Unit,
    onAddOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0F1420))
            .border(1.dp, Color(0xFF1E2838), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (!isVisible) {
            Text(
                "Overlay Track Hidden",
                color = Color(0xFF475569),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
            return@Box
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Overlay Video Clips
            clips.forEach { clip ->
                val isSelected = clip.id == selectedClipId
                val clipDur = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(500L)
                val clipWidth = maxOf((clipDur * msToDp).dp, 48.dp)

                Box(
                    modifier = Modifier
                        .width(clipWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFF0B3B48) else Color(0xFF0C2B38))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.NeonCyan.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onSelectClip(clip.id) }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(12.dp))
                        Text(
                            clip.name.ifEmpty { "PIP Video" },
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Text Overlays
            textOverlays.forEach { textOverlay ->
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2A1538))
                        .border(1.dp, ApexPalette.NeonPink.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Default.Title, contentDescription = null, tint = ApexPalette.NeonPink, modifier = Modifier.size(11.dp))
                        Text(
                            textOverlay.text.ifEmpty { "Title" },
                            color = Color.White,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Empty state placeholder
            if (clips.isEmpty() && textOverlays.isEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onAddOverlay)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(12.dp))
                    Text(
                        "+ Add PIP / Text Overlay (V2)",
                        color = Color(0xFF67E8F9),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Main Video Track (V1) supporting Drag-and-Drop Reordering, multi-clip filmstrip slices, and trim handles.
 */
@Composable
private fun TimelineVideoTrack(
    videoClips: List<MediaClip>,
    selectedClipId: String?,
    msToDp: Float,
    playheadMs: Long,
    perSecondThumbnails: Map<Int, Bitmap>,
    draggingClipId: String?,
    dragAccumulatedOffsetPx: Float,
    targetDropIndex: Int,
    onSelectClip: (String) -> Unit,
    onClipBoundsMeasured: (index: Int, leftPx: Float, widthPx: Float) -> Unit,
    onDragStart: (MediaClip) -> Unit,
    onDrag: (deltaX: Float) -> Unit,
    onDragEnd: () -> Unit,
    onAddClip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F121C))
            .border(1.dp, Color(0xFF22293A), RoundedCornerShape(8.dp))
            .padding(vertical = 3.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            videoClips.forEachIndexed { index, clip ->
                val isSelected = clip.id == selectedClipId
                val isBeingDragged = clip.id == draggingClipId
                val clipDur = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(500L)
                val clipWidth = maxOf((clipDur * msToDp).dp, 64.dp)

                // Drop target indicator before this clip
                if (draggingClipId != null && targetDropIndex == index && targetDropIndex != videoClips.indexOfFirst { it.id == draggingClipId }) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(ApexPalette.NeonCyan)
                    )
                }

                // Video Clip Item with Drag-and-Drop gestures
                Box(
                    modifier = Modifier
                        .width(clipWidth)
                        .fillMaxHeight()
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInParent()
                            val width = coords.size.width.toFloat()
                            onClipBoundsMeasured(index, pos.x, width)
                        }
                        .zIndex(if (isBeingDragged) 100f else 1f)
                        .graphicsLayer {
                            if (isBeingDragged) {
                                translationX = dragAccumulatedOffsetPx
                                scaleX = 1.05f
                                scaleY = 1.05f
                                shadowElevation = 16f
                                alpha = 0.92f
                            }
                        }
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color(0xFF1E1A33) else Color(0xFF151824))
                        .border(
                            width = if (isBeingDragged) 2.5.dp else if (isSelected) 2.dp else 1.dp,
                            color = if (isBeingDragged) ApexPalette.NeonCyan else if (isSelected) Color(0xFFFFD700) else Color(0xFF2E384D),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .pointerInput(clip.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart(clip) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.x)
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() }
                            )
                        }
                        .clickable { onSelectClip(clip.id) }
                ) {
                    // Synchronized Filmstrip frames
                    val totalSec = (clipDur / 1000L).toInt().coerceIn(1, 8)
                    Row(modifier = Modifier.fillMaxSize()) {
                        for (sec in 0 until totalSec) {
                            val frameBmp = perSecondThumbnails[sec]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 0.5.dp)
                                    .background(Color(0xFF181C2B))
                            ) {
                                if (frameBmp != null) {
                                    Image(
                                        bitmap = frameBmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Clip Info Header: Title + Duration + Speed
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(10.dp))
                                Text(
                                    clip.name.ifEmpty { "Clip ${index + 1}" },
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            Text(
                                "${String.format(java.util.Locale.US, "%.1f", clipDur / 1000f)}s",
                                color = Color(0xFFCBD5E1),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Trim Grips on active clip
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(width = 4.dp, height = 32.dp)
                                .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(width = 4.dp, height = 32.dp)
                                .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                        )
                    }

                    // Drag Indicator Glow Pill when dragging
                    if (isBeingDragged) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(4.dp))
                                .background(ApexPalette.NeonCyan.copy(alpha = 0.9f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("REORDER", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // Drop target indicator at the very end of list
            if (draggingClipId != null && targetDropIndex >= videoClips.size) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(ApexPalette.NeonCyan)
                )
            }

            // Add clip button at end of track
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF141926))
                    .border(1.dp, Color(0xFF26334A), RoundedCornerShape(6.dp))
                    .clickable(onClick = onAddClip),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, contentDescription = "Add Clip", tint = ApexPalette.NeonCyan, modifier = Modifier.size(16.dp))
                    Text("Add", color = Color(0xFF94A3B8), fontSize = 8.sp)
                }
            }
        }
    }
}

/**
 * FX / Filter Shader Track.
 */
@Composable
private fun TimelineFxTrack(
    activeFxId: String?,
    durationMs: Long,
    msToDp: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF121018))
            .border(1.dp, Color(0xFF262033), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (activeFxId != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(ApexPalette.NeonAmber.copy(alpha = 0.35f), ApexPalette.NeonPurple.copy(alpha = 0.35f))
                        )
                    )
                    .border(1.dp, ApexPalette.NeonAmber, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ApexPalette.NeonAmber, modifier = Modifier.size(11.dp))
                    Text(
                        "FX: ${activeFxId.replace("_", " ").uppercase()}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Text(
                "No Master FX Active",
                color = Color(0xFF475569),
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/**
 * Audio Track (A1) with dynamic waveform bars and beat-sync indicators.
 */
@Composable
private fun TimelineAudioTrack(
    audioTracks: List<AudioTrack>,
    durationMs: Long,
    msToDp: Float,
    isMuted: Boolean,
    audioWaveform: FloatArray,
    beatMarkersMs: List<Long>,
    snapToBeat: Boolean,
    playheadMs: Long,
    modifier: Modifier = Modifier
) {
    val activeTrack = audioTracks.firstOrNull()
    val audioName = activeTrack?.name ?: "Master Audio"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isMuted) Color(0xFF141A18) else Color(0xFF0D1C18))
            .border(1.dp, if (isMuted) Color(0xFF1E2E28) else ApexPalette.NeonEmerald.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Audio Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(ApexPalette.NeonEmerald.copy(alpha = 0.25f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("A1", color = ApexPalette.NeonEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.GraphicEq,
                    contentDescription = "Audio",
                    tint = if (isMuted) Color(0xFFEF4444) else ApexPalette.NeonEmerald,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isMuted) "$audioName (Muted)" else audioName,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // Real-time Waveform Bars
            val barCount = 48
            val progress = (playheadMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                repeat(barCount) { i ->
                    val barFrac = i.toFloat() / barCount.toFloat()
                    val isPlayed = barFrac <= progress
                    val sampleIdx = if (audioWaveform.isNotEmpty()) (barFrac * (audioWaveform.size - 1)).toInt() else 0
                    val amp = if (audioWaveform.isNotEmpty() && sampleIdx in audioWaveform.indices) {
                        audioWaveform[sampleIdx].coerceIn(0.12f, 1.0f)
                    } else {
                        when {
                            i % 8 == 0 -> 0.9f
                            i % 4 == 0 -> 0.65f
                            i % 2 == 0 -> 0.4f
                            else -> 0.2f
                        }
                    }

                    val isBeat = snapToBeat && beatMarkersMs.any { beatMs ->
                        val beatFrac = beatMs.toFloat() / durationMs.toFloat()
                        kotlin.math.abs(beatFrac - barFrac) < (1.2f / barCount)
                    }

                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .height(28.dp * amp)
                            .background(
                                color = when {
                                    isMuted -> Color(0xFF475569)
                                    isBeat -> ApexPalette.NeonCyan
                                    isPlayed -> Color.White
                                    else -> ApexPalette.NeonEmerald.copy(alpha = 0.7f)
                                },
                                shape = RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Unified Vertical Playhead line with Floating Timecode Badge.
 */
@Composable
private fun TimelinePlayhead(
    playheadX: Dp,
    playheadMs: Long,
    onScrub: (Long) -> Unit,
    msToDp: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .offset(x = playheadX - 1.dp)
            .width(2.dp)
            .fillMaxHeight()
            .zIndex(200f)
            .background(Color.White)
    )

    // Floating Playhead Timestamp Badge at top
    Box(
        modifier = Modifier
            .offset(x = (playheadX - 22.dp).coerceAtLeast(0.dp), y = 0.dp)
            .zIndex(210f)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White)
            .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
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
