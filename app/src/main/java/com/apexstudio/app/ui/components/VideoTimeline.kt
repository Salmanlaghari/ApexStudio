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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.PointerEventPass
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
import com.apexstudio.app.domain.model.ClipTransition
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.TransitionLibrary
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
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
    transitions: List<ClipTransition> = emptyList(),
    onOpenTransition: (fromClipId: String, toClipId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    // Pro-timeline interactions (all optional; default no-op keeps legacy behavior).
    onTrimClip: (clipId: String, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> },
    onMoveClipOffset: (clipId: String, offsetMs: Long) -> Unit = { _, _ -> },
    onDeleteClips: (Set<String>) -> Unit = {},
    // Keyframe layer interactions.
    onToggleKeyframeAtPlayhead: (clipId: String) -> Unit = {},
    onMoveKeyframe: (clipId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _ -> },
    onToggleTextKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onMoveTextKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _, _ -> },
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val safeDurationMs = totalDurationMs.coerceAtLeast(1000L)
    val safePlayheadMs = playheadMs.coerceIn(0L, safeDurationMs)
    val effectiveZoom = timelineZoom.coerceIn(0.5f, 10.0f)

    // Scaling: dp per second based on zoom factor (scales up to 540dp/sec for frame-level editing)
    val secondWidthDp = (54.dp * effectiveZoom).coerceIn(24.dp, 540.dp)
    val msToDp = secondWidthDp.value / 1000f

    // Pinch-to-zoom interactive state
    var isPinchZooming by remember { mutableStateOf(false) }
    var activeZoomDisplay by remember { mutableFloatStateOf(effectiveZoom) }
    var showZoomHud by remember { mutableStateOf(false) }
    var hudDismissJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(timelineZoom) {
        if (!isPinchZooming) {
            activeZoomDisplay = effectiveZoom
        }
    }

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

    // Multi-select state (pro timeline): select several clips, then move/delete together.
    var multiSelectMode by remember { mutableStateOf(false) }
    var multiSelectedIds by remember { mutableStateOf(setOf<String>()) }

    // Snap indicator: true when the playhead sits on (or within threshold of) a beat marker
    // while snap-to-beat is enabled — shown as a badge on the playhead.
    val snapThresholdMs = 200L
    val isPlayheadSnapped = snapToBeat &&
        beatMarkersMs.any { kotlin.math.abs(it - safePlayheadMs) <= snapThresholdMs }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color(0xFF090B10))
            .border(1.dp, Color(0xFF171B26))
    ) {
        // --- 1. PINNED LEFT TRACK HEADER SIDEBAR ---
        TimelineLeftSidebar(
            zoomFactor = activeZoomDisplay,
            isAudioMuted = isAudioMuted,
            isOverlayVisible = isOverlayVisible,
            onZoomIn = {
                val next = (activeZoomDisplay + 0.5f).coerceAtMost(10.0f)
                activeZoomDisplay = next
                onZoomChange(next)
                showZoomHud = true
                hudDismissJob?.cancel()
                hudDismissJob = coroutineScope.launch { delay(1200); showZoomHud = false }
            },
            onZoomOut = {
                val prev = (activeZoomDisplay - 0.5f).coerceAtLeast(0.5f)
                activeZoomDisplay = prev
                onZoomChange(prev)
                showZoomHud = true
                hudDismissJob?.cancel()
                hudDismissJob = coroutineScope.launch { delay(1200); showZoomHud = false }
            },
            onResetZoom = {
                activeZoomDisplay = 1.0f
                onZoomChange(1.0f)
                showZoomHud = true
                hudDismissJob?.cancel()
                hudDismissJob = coroutineScope.launch { delay(1200); showZoomHud = false }
            },
            onToggleAudioMute = { isAudioMuted = !isAudioMuted },
            onToggleOverlayVisible = { isOverlayVisible = !isOverlayVisible },
            onAddVideo = { onAddMedia(ClipType.VIDEO) },
            onAddOverlay = { onAddMedia(ClipType.OVERLAY) }
        )

        // --- 2. TIMELINE VIEWPORT WITH PINCH-TO-ZOOM GESTURE DETECTOR ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(effectiveZoom) {
                    detectTimelinePinchZoom(
                        onPinchStart = {
                            isPinchZooming = true
                            activeZoomDisplay = effectiveZoom
                            hudDismissJob?.cancel()
                            showZoomHud = true
                        },
                        onPinchZoom = { centroidX, zoomRatio ->
                            val oldZoom = activeZoomDisplay
                            val targetZoom = (oldZoom * zoomRatio).coerceIn(0.5f, 10.0f)
                            if (abs(targetZoom - oldZoom) > 0.003f) {
                                activeZoomDisplay = targetZoom
                                onZoomChange(targetZoom)

                                // Maintain anchor point under pinch centroid
                                val oldMsToPx = (54f * oldZoom) / 1000f * density.density
                                if (oldMsToPx > 0f) {
                                    val timeAtCentroidMs = ((scrollState.value + centroidX) / oldMsToPx).coerceAtLeast(0f)
                                    val newMsToPx = (54f * targetZoom) / 1000f * density.density
                                    val desiredScroll = (timeAtCentroidMs * newMsToPx - centroidX).roundToInt().coerceAtLeast(0)
                                    coroutineScope.launch {
                                        scrollState.scrollTo(desiredScroll)
                                    }
                                }
                            }
                        },
                        onPinchEnd = {
                            isPinchZooming = false
                            hudDismissJob?.cancel()
                            hudDismissJob = coroutineScope.launch {
                                delay(1200)
                                showZoomHud = false
                            }
                        }
                    )
                }
        ) {
            // Horizontally Scrollable Timeline Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
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

                    // TRACK 1: V2 (Overlay / PIP / Text / Stickers) — two lanes
                    TimelineOverlayTrack(
                        clips = clips.filter { it.type == ClipType.OVERLAY },
                        textOverlays = textOverlays,
                        stickers = stickers,
                        selectedClipId = selectedClipId,
                        msToDp = msToDp,
                        isVisible = isOverlayVisible,
                        onSelectClip = onSelectClip,
                        onAddOverlay = { onAddMedia(ClipType.OVERLAY) },
                        onMoveClipOffset = onMoveClipOffset,
                        onSeekToKeyframe = onScrub,
                        onToggleKeyframeAtPlayhead = onToggleKeyframeAtPlayhead,
                        onMoveKeyframe = onMoveKeyframe,
                        onToggleTextKeyframeAtPlayhead = onToggleTextKeyframeAtPlayhead,
                        onMoveTextKeyframe = onMoveTextKeyframe,
                        allClips = clips,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
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
                        transitions = transitions,
                        onOpenTransition = onOpenTransition,
                        onSelectClip = onSelectClip,
                        onSeekToKeyframe = onScrub,
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
                        multiSelectMode = multiSelectMode,
                        multiSelectedIds = multiSelectedIds,
                        onToggleMultiSelect = { clipId ->
                            multiSelectedIds = if (clipId in multiSelectedIds) {
                                multiSelectedIds - clipId
                            } else {
                                multiSelectedIds + clipId
                            }
                        },
                        onTrimClip = onTrimClip,
                        onReorderByDrag = { clipId, deltaX ->
                            // Direct finger-drag reorder for the selected clip (no long-press needed).
                            if (draggingClipId == null) {
                                draggingClipId = clipId
                                dragAccumulatedOffsetPx = 0f
                                targetDropIndex = videoClips.indexOfFirst { it.id == clipId }
                            }
                            dragAccumulatedOffsetPx += deltaX
                            val origIdx = videoClips.indexOfFirst { it.id == clipId }
                            if (origIdx != -1) {
                                val currentBound = clipLayoutBounds[origIdx]
                                if (currentBound != null) {
                                    val currentCenterX = currentBound.first + (currentBound.second / 2f) + dragAccumulatedOffsetPx
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
                        onReorderByDragEnd = {
                            val clipId = draggingClipId
                            val origIdx = videoClips.indexOfFirst { it.id == clipId }
                            if (clipId != null && origIdx != -1 && targetDropIndex != -1 && origIdx != targetDropIndex) {
                                onReorderClips(origIdx, targetDropIndex)
                            }
                            draggingClipId = null
                            dragAccumulatedOffsetPx = 0f
                            targetDropIndex = -1
                        },
                        onMoveKeyframe = onMoveKeyframe,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    )

                    // TRACK 3: FX / Shader Track
                    TimelineFxTrack(
                        activeFxId = activeFxId,
                        durationMs = safeDurationMs,
                        msToDp = msToDp,
                        fxKeyframes = videoClips.firstOrNull { it.id == selectedClipId }?.keyframes?.keyframes
                            ?: emptyList(),
                        onSeekToKeyframe = onScrub,
                        onToggleKeyframeAtPlayhead = {
                            selectedClipId?.let { onToggleKeyframeAtPlayhead(it) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
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
                    msToDp = msToDp,
                    isSnapped = isPlayheadSnapped
                )
            }
        }

        // Floating Zoom HUD & Frame Precision indicator overlay
        if (showZoomHud || isPinchZooming) {
            TimelineZoomHud(
                zoom = activeZoomDisplay,
                onResetZoom = {
                    activeZoomDisplay = 1.0f
                    onZoomChange(1.0f)
                    showZoomHud = true
                    hudDismissJob?.cancel()
                    hudDismissJob = coroutineScope.launch { delay(1200); showZoomHud = false }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .zIndex(30f)
            )
        }

        // Multi-select toggle (pro timeline): tap to enter/exit multi-select mode.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 8.dp)
                .zIndex(30f)
                .size(32.dp)
                .clip(CircleShape)
                .background(if (multiSelectMode) Color(0xFFFFB300) else Color(0xFF1A1E2A))
                .border(1.dp, Color(0xFF3A415A), CircleShape)
                .clickable {
                    multiSelectMode = !multiSelectMode
                    if (!multiSelectMode) multiSelectedIds = emptySet()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (multiSelectMode) "✓" else "▣",
                color = if (multiSelectMode) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Multi-select action bar: bulk delete + done.
        if (multiSelectMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .zIndex(30f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141824).copy(alpha = 0.95f))
                    .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${multiSelectedIds.size} selected",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (multiSelectedIds.isNotEmpty()) {
                    Text(
                        text = "Delete",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            onDeleteClips(multiSelectedIds)
                            multiSelectedIds = emptySet()
                        }
                    )
                }
                Text(
                    text = "Done",
                    color = Color(0xFFFFB300),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        multiSelectMode = false
                        multiSelectedIds = emptySet()
                    }
                )
            }
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
    transitions: List<ClipTransition> = state.project?.transitions ?: emptyList(),
    onOpenTransition: (fromClipId: String, toClipId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    onTrimClip: (clipId: String, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> },
    onMoveClipOffset: (clipId: String, offsetMs: Long) -> Unit = { _, _ -> },
    onDeleteClips: (Set<String>) -> Unit = {},
    onToggleKeyframeAtPlayhead: (clipId: String) -> Unit = {},
    onMoveKeyframe: (clipId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _ -> },
    onToggleTextKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onMoveTextKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _, _ -> },
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
        transitions = transitions,
        onOpenTransition = onOpenTransition,
        modifier = modifier,
        onTrimClip = onTrimClip,
        onMoveClipOffset = onMoveClipOffset,
        onDeleteClips = onDeleteClips,
        onToggleKeyframeAtPlayhead = onToggleKeyframeAtPlayhead,
        onMoveKeyframe = onMoveKeyframe,
        onToggleTextKeyframeAtPlayhead = onToggleTextKeyframeAtPlayhead,
        onMoveTextKeyframe = onMoveTextKeyframe
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
    onResetZoom: () -> Unit = {},
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
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable(onClick = onResetZoom)
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

            // Render frame-level, sub-second and second tick marks
            for (sec in 0..totalSec + 2) {
                val secStartMs = sec * 1000L
                val secXPx = (secStartMs * msToDp).dp.toPx()

                // High zoom: 30fps individual frame ticks (every ~33.3ms)
                if (secondWidthDp >= 320.dp) {
                    for (f in 1..29) {
                        if (f % 5 != 0) {
                            val fMs = secStartMs + (f * 33.33f).toLong()
                            val fXPx = (fMs * msToDp).dp.toPx()
                            drawLine(
                                color = Color(0xFF1E2638),
                                start = Offset(fXPx, h - 3f),
                                end = Offset(fXPx, h),
                                strokeWidth = 1f
                            )
                        }
                    }
                }

                // Medium-high zoom: 5-frame ticks (every ~166.7ms)
                if (secondWidthDp >= 140.dp) {
                    for (f5 in 1..5) {
                        if (f5 != 3) { // 3 is half-second (15 frames)
                            val fMs = secStartMs + (f5 * 166.7f).toLong()
                            val fXPx = (fMs * msToDp).dp.toPx()
                            drawLine(
                                color = Color(0xFF2C384E),
                                start = Offset(fXPx, h - 5.5f),
                                end = Offset(fXPx, h),
                                strokeWidth = 1f
                            )
                        }
                    }
                }

                // Half-second tick (15 frames at 30fps)
                val halfXPx = ((secStartMs + 500L) * msToDp).dp.toPx()
                drawLine(
                    color = if (secondWidthDp >= 180.dp) ApexPalette.NeonCyan.copy(alpha = 0.6f) else Color(0xFF263042),
                    start = Offset(halfXPx, h - if (secondWidthDp >= 180.dp) 8f else 6f),
                    end = Offset(halfXPx, h),
                    strokeWidth = if (secondWidthDp >= 180.dp) 1.5f else 1f
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

        // Time labels based on zoom factor
        val stepSec = when {
            secondWidthDp > 60.dp -> 1
            secondWidthDp > 35.dp -> 2
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
                        // Pro timecode (HH:MM:SS:FF) at high zoom; compact short form otherwise.
                        text = if (secondWidthDp >= 180.dp) TimeFormat.msToTimecode(sec * 1000L)
                        else TimeFormat.msToShort(sec * 1000L),
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // If zoomed in to frame level, show half-second / 15-frame label
                if (secondWidthDp >= 240.dp) {
                    val halfOffsetDp = ((sec * 1000L + 500L) * msToDp).dp
                    Box(
                        modifier = Modifier
                            .offset(x = halfOffsetDp)
                            .padding(start = 2.dp, top = 3.dp)
                    ) {
                        Text(
                            text = TimeFormat.msToTimecode(sec * 1000L + 500L).takeLast(3),
                            color = ApexPalette.NeonCyan.copy(alpha = 0.7f),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Overlay Track (V2: Picture-in-Picture videos, text titles, and stickers).
 *
 * Pro layout with two lanes:
 * - Lane 1: overlay clips positioned by timelineOffsetMs (absolute), finger-drag to move,
 *   each with a keyframe diamond strip (tap = seek, drag = move, "+" = toggle at playhead).
 * - Lane 2: text overlays positioned by startMs, each with its own keyframe lane.
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
    onMoveClipOffset: (clipId: String, offsetMs: Long) -> Unit = { _, _ -> },
    onSeekToKeyframe: (Long) -> Unit = {},
    onToggleKeyframeAtPlayhead: (clipId: String) -> Unit = {},
    onMoveKeyframe: (clipId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _ -> },
    onToggleTextKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onMoveTextKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _, _ -> },
    allClips: List<MediaClip> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0F1420))
            .border(1.dp, Color(0xFF1E2838), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        if (!isVisible) {
            Text(
                "Overlay Track Hidden",
                color = Color(0xFF475569),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
            return@Column
        }

        // Lane 1: Overlay Video Clips — absolutely positioned by timelineOffsetMs, draggable.
        Box(modifier = Modifier.fillMaxWidth().height(52.dp)) {
            clips.forEach { clip ->
                val isSelected = clip.id == selectedClipId
                val clipDur = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(500L)
                val clipWidthDp = maxOf((clipDur * msToDp).dp, 48.dp)
                val clipOffsetDp = (clip.timelineOffsetMs * msToDp).dp
                var dragDx by remember(clip.id) { mutableFloatStateOf(0f) }
                var isDragging by remember(clip.id) { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .offset(x = clipOffsetDp)
                        .width(clipWidthDp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(clipWidthDp)
                            .height(32.dp)
                            .graphicsLayer { translationX = dragDx }
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) Color(0xFF0B3B48)
                                else if (isDragging) Color(0xFF134A5C)
                                else Color(0xFF0C2B38)
                            )
                            .border(
                                width = if (isSelected || isDragging) 1.5.dp else 1.dp,
                                color = if (isSelected || isDragging) ApexPalette.NeonCyan
                                else ApexPalette.NeonCyan.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .pointerInput(clip.id) {
                                detectDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragDx += dragAmount.x
                                    },
                                    onDragEnd = {
                                        val deltaMs = (dragDx / msToDp).toLong()
                                        val newOffset = (clip.timelineOffsetMs + deltaMs).coerceAtLeast(0L)
                                        dragDx = 0f
                                        isDragging = false
                                        if (newOffset != clip.timelineOffsetMs) {
                                            onMoveClipOffset(clip.id, newOffset)
                                        }
                                    },
                                    onDragCancel = { dragDx = 0f; isDragging = false }
                                )
                            }
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

                    // Keyframe diamond strip for this overlay clip (timeline-global keyframes,
                    // positioned relative to the clip's offset).
                    KeyframeDiamondStrip(
                        keyframes = clip.keyframes.keyframes,
                        spanStartMs = clip.timelineOffsetMs,
                        spanDurationMs = clipDur,
                        stripWidthDp = clipWidthDp,
                        msToDp = msToDp,
                        onSeekToKeyframe = onSeekToKeyframe,
                        onMoveKeyframe = { kfId, newTimeMs -> onMoveKeyframe(clip.id, kfId, newTimeMs) },
                        onAddAtPlayhead = { onToggleKeyframeAtPlayhead(clip.id) },
                        accentColor = ApexPalette.NeonCyan,
                        modifier = Modifier.height(18.dp)
                    )
                }
            }

            // Empty state placeholder for the clip lane.
            if (clips.isEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onAddOverlay)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(12.dp))
                    Text(
                        "+ Add PIP (V2)",
                        color = Color(0xFF67E8F9),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Lane 2: Text Overlays — positioned by startMs, with their own keyframe lane.
        if (textOverlays.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                textOverlays.forEach { textOverlay ->
                    val ownerClipId = allClips.firstOrNull { c -> c.textOverlays.any { it.id == textOverlay.id } }?.id
                    val ovStartMs = textOverlay.startMs.coerceAtLeast(0L)
                    val ovEndMs = if (textOverlay.endMs == Long.MAX_VALUE) ovStartMs + 3000L else textOverlay.endMs
                    val ovDur = (ovEndMs - ovStartMs).coerceAtLeast(500L)
                    val ovWidthDp = maxOf((ovDur * msToDp).dp, 40.dp)
                    val ovOffsetDp = (ovStartMs * msToDp).dp

                    Column(
                        modifier = Modifier
                            .offset(x = ovOffsetDp)
                            .width(ovWidthDp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(ovWidthDp)
                                .height(20.dp)
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
                        KeyframeDiamondStrip(
                            keyframes = textOverlay.keyframes.keyframes,
                            spanStartMs = ovStartMs,
                            spanDurationMs = ovDur,
                            stripWidthDp = ovWidthDp,
                            msToDp = msToDp,
                            onSeekToKeyframe = onSeekToKeyframe,
                            onMoveKeyframe = { kfId, newTimeMs ->
                                if (ownerClipId != null) onMoveTextKeyframe(ownerClipId, textOverlay.id, kfId, newTimeMs)
                            },
                            onAddAtPlayhead = {
                                if (ownerClipId != null) onToggleTextKeyframeAtPlayhead(ownerClipId, textOverlay.id)
                            },
                            accentColor = ApexPalette.NeonPink,
                            modifier = Modifier.height(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shared keyframe diamond strip: shows diamond markers for [keyframes] positioned
 * within [spanStartMs, spanStartMs + spanDurationMs].
 * - Tap a diamond: seek to that keyframe.
 * - Drag a diamond: move the keyframe (committed on release via [onMoveKeyframe]).
 * - Tap the "+" chip: toggle a keyframe at the current playhead position.
 */
@Composable
private fun KeyframeDiamondStrip(
    keyframes: List<Keyframe>,
    spanStartMs: Long,
    spanDurationMs: Long,
    stripWidthDp: Dp,
    msToDp: Float,
    onSeekToKeyframe: (Long) -> Unit,
    onMoveKeyframe: (keyframeId: String, newTimeMs: Long) -> Unit,
    onAddAtPlayhead: () -> Unit,
    accentColor: Color,
    showAddButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(stripWidthDp)
            .background(Color(0xFF080A10).copy(alpha = 0.6f))
    ) {
        val spanDur = spanDurationMs.coerceAtLeast(1L)
        keyframes.forEach { kf ->
            val frac = ((kf.timeMs - spanStartMs).toFloat() / spanDur.toFloat()).coerceIn(0f, 1f)
            var dragDx by remember(kf.id) { mutableFloatStateOf(0f) }
            var isDragging by remember(kf.id) { mutableStateOf(false) }
            val diamondXdp = (frac * stripWidthDp.value).dp - 5.dp

            Box(
                modifier = Modifier
                    .offset(x = diamondXdp)
                    .size(10.dp)
                    .graphicsLayer {
                        translationX = dragDx
                        rotationZ = 45f
                    }
                    .background(
                        if (isDragging) Color.White else accentColor,
                        RoundedCornerShape(2.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                    .pointerInput(kf.id) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragDx += dragAmount.x
                            },
                            onDragEnd = {
                                val deltaMs = (dragDx / msToDp).toLong()
                                val newTimeMs = (kf.timeMs + deltaMs).coerceIn(spanStartMs, spanStartMs + spanDur)
                                dragDx = 0f
                                isDragging = false
                                if (newTimeMs != kf.timeMs) onMoveKeyframe(kf.id, newTimeMs)
                            },
                            onDragCancel = { dragDx = 0f; isDragging = false }
                        )
                    }
                    .clickable { onSeekToKeyframe(kf.timeMs) }
            ) {}
        }

        // "+" chip to toggle a keyframe at the playhead.
        if (showAddButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.25f))
                    .border(1.dp, accentColor, CircleShape)
                    .clickable(onClick = onAddAtPlayhead),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
    transitions: List<ClipTransition> = emptyList(),
    onOpenTransition: (fromClipId: String, toClipId: String) -> Unit = { _, _ -> },
    onSelectClip: (String) -> Unit,
    onSeekToKeyframe: ((Long) -> Unit)? = null,
    onClipBoundsMeasured: (index: Int, leftPx: Float, widthPx: Float) -> Unit,
    onDragStart: (MediaClip) -> Unit,
    onDrag: (deltaX: Float) -> Unit,
    onDragEnd: () -> Unit,
    onAddClip: () -> Unit,
    multiSelectMode: Boolean = false,
    multiSelectedIds: Set<String> = emptySet(),
    onToggleMultiSelect: (String) -> Unit = {},
    onTrimClip: (clipId: String, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> },
    onReorderByDrag: (clipId: String, deltaX: Float) -> Unit = { _, _ -> },
    onReorderByDragEnd: () -> Unit = {},
    onMoveKeyframe: (clipId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    // Timeline-global start offset for each V1 clip (keyframes are timeline-global).
    val clipTimelineStarts = remember(videoClips) {
        val starts = mutableListOf<Long>()
        var acc = 0L
        videoClips.forEach { c ->
            starts.add(acc)
            acc += (c.trimEndMs - c.trimStartMs).coerceAtLeast(0L)
        }
        starts
    }

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
                val isMultiSelected = clip.id in multiSelectedIds
                // Live trim preview (updated during grip drag, committed on release).
                var trimPreviewStart by remember(clip.id) { mutableStateOf<Long?>(null) }
                var trimPreviewEnd by remember(clip.id) { mutableStateOf<Long?>(null) }
                val effTrimStart = trimPreviewStart ?: clip.trimStartMs
                val effTrimEnd = trimPreviewEnd ?: clip.trimEndMs
                val clipDur = (effTrimEnd - effTrimStart).coerceAtLeast(500L)
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
                        .background(
                            if (isMultiSelected) Color(0xFF3A2E00)
                            else if (isSelected) Color(0xFF1E1A33)
                            else Color(0xFF151824)
                        )
                        .border(
                            width = if (isBeingDragged) 2.5.dp else if (isSelected || isMultiSelected) 2.dp else 1.dp,
                            color = if (isBeingDragged) ApexPalette.NeonCyan
                            else if (isMultiSelected) Color(0xFFFFB300)
                            else if (isSelected) Color(0xFFFFD700)
                            else Color(0xFF2E384D),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .pointerInput(clip.id, isSelected) {
                            // Direct finger-drag reorder for the selected clip (no long-press needed).
                            // Long-press drag (below) remains as the discoverable alternative.
                            if (isSelected && !multiSelectMode) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onReorderByDrag(clip.id, dragAmount.x)
                                    },
                                    onDragEnd = { onReorderByDragEnd() },
                                    onDragCancel = { onReorderByDragEnd() }
                                )
                            }
                        }
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
                        .clickable {
                            if (multiSelectMode) onToggleMultiSelect(clip.id)
                            else onSelectClip(clip.id)
                        }
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

                    // Trim Grips on active clip — draggable to trim start/end.
                    if (isSelected && !multiSelectMode) {
                        var trimDragAccumX by remember(clip.id) { mutableFloatStateOf(0f) }
                        // Left grip: trim start
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(width = 16.dp, height = 44.dp)
                                .pointerInput(clip.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            trimDragAccumX = 0f
                                            trimPreviewStart = clip.trimStartMs
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            trimDragAccumX += dragAmount.x
                                            val deltaMs = (trimDragAccumX / msToDp).toLong()
                                            val end = trimPreviewEnd ?: clip.trimEndMs
                                            trimPreviewStart = (clip.trimStartMs + deltaMs)
                                                .coerceIn(0L, (end - 100L).coerceAtLeast(0L))
                                        },
                                        onDragEnd = {
                                            val s = trimPreviewStart ?: clip.trimStartMs
                                            val e = trimPreviewEnd ?: clip.trimEndMs
                                            trimPreviewStart = null
                                            trimPreviewEnd = null
                                            if (s != clip.trimStartMs || e != clip.trimEndMs) {
                                                onTrimClip(clip.id, s, e)
                                            }
                                        },
                                        onDragCancel = {
                                            trimPreviewStart = null
                                            trimPreviewEnd = null
                                        }
                                    )
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 5.dp, height = 36.dp)
                                    .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                            )
                        }
                        // Right grip: trim end
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(width = 16.dp, height = 44.dp)
                                .pointerInput(clip.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            trimDragAccumX = 0f
                                            trimPreviewEnd = clip.trimEndMs
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            trimDragAccumX += dragAmount.x
                                            val deltaMs = (trimDragAccumX / msToDp).toLong()
                                            val start = trimPreviewStart ?: clip.trimStartMs
                                            trimPreviewEnd = (clip.trimEndMs + deltaMs)
                                                .coerceIn(start + 100L, Long.MAX_VALUE)
                                        },
                                        onDragEnd = {
                                            val s = trimPreviewStart ?: clip.trimStartMs
                                            val e = trimPreviewEnd ?: clip.trimEndMs
                                            trimPreviewStart = null
                                            trimPreviewEnd = null
                                            if (s != clip.trimStartMs || e != clip.trimEndMs) {
                                                onTrimClip(clip.id, s, e)
                                            }
                                        },
                                        onDragCancel = {
                                            trimPreviewStart = null
                                            trimPreviewEnd = null
                                        }
                                    )
                                },
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 5.dp, height = 36.dp)
                                    .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                            )
                        }
                    }

                    // Multi-select checkmark badge
                    if (isMultiSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFB300)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
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

                    // Keyframe Diamond Markers Strip along bottom of clip.
                    // Keyframes are timeline-global; position relative to this clip's timeline start.
                    val clipKeyframes = clip.keyframes.keyframes
                    if (clipKeyframes.isNotEmpty()) {
                        val clipStartMs = clipTimelineStarts.getOrElse(index) { 0L }
                        KeyframeDiamondStrip(
                            keyframes = clipKeyframes,
                            spanStartMs = clipStartMs,
                            spanDurationMs = clipDur,
                            stripWidthDp = clipWidth,
                            msToDp = msToDp,
                            onSeekToKeyframe = { ms -> onSeekToKeyframe?.invoke(ms) },
                            onMoveKeyframe = { kfId, newTimeMs -> onMoveKeyframe(clip.id, kfId, newTimeMs) },
                            onAddAtPlayhead = {},
                            accentColor = Color(0xFFFFD700),
                            showAddButton = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .align(Alignment.BottomCenter)
                        )
                    }
                }

                // Transition Junction between this clip and the next clip
                if (index < videoClips.size - 1) {
                    val nextClip = videoClips[index + 1]
                    val junctionTransition = transitions.firstOrNull {
                        it.fromClipId == clip.id && it.toClipId == nextClip.id
                    }
                    TimelineTransitionJunctionBadge(
                        transition = junctionTransition,
                        onClick = { onOpenTransition(clip.id, nextClip.id) }
                    )
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
    fxKeyframes: List<Keyframe> = emptyList(),
    onSeekToKeyframe: (Long) -> Unit = {},
    onToggleKeyframeAtPlayhead: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF121018))
            .border(1.dp, Color(0xFF262033), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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

            // Keyframe lane for the selected clip (drives filter/effect intensity).
            // Tap "+" to toggle a keyframe at the playhead; tap a diamond to seek.
            if (fxKeyframes.isNotEmpty()) {
                val trackWidthDp = (durationMs * msToDp).dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(Color(0xFF080A10).copy(alpha = 0.6f))
                ) {
                    fxKeyframes.forEach { kf ->
                        val frac = (kf.timeMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                        val diamondXdp = (frac * trackWidthDp.value).dp - 4.dp
                        Box(
                            modifier = Modifier
                                .offset(x = diamondXdp)
                                .size(8.dp)
                                .graphicsLayer { rotationZ = 45f }
                                .background(ApexPalette.NeonAmber, RoundedCornerShape(1.dp))
                                .border(0.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                                .clickable { onSeekToKeyframe(kf.timeMs) }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(ApexPalette.NeonAmber.copy(alpha = 0.25f))
                            .border(1.dp, ApexPalette.NeonAmber, CircleShape)
                            .clickable(onClick = onToggleKeyframeAtPlayhead),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
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
    isSnapped: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .offset(x = playheadX - 1.dp)
            .width(2.dp)
            .fillMaxHeight()
            .zIndex(200f)
            .background(if (isSnapped) ApexPalette.NeonCyan else Color.White)
    )

    // Floating Playhead Timestamp Badge at top (cyan + snap glyph when snapped to a beat)
    Box(
        modifier = Modifier
            .offset(x = (playheadX - 22.dp).coerceAtLeast(0.dp), y = 0.dp)
            .zIndex(210f)
            .clip(RoundedCornerShape(5.dp))
            .background(if (isSnapped) ApexPalette.NeonCyan else Color.White)
            .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = (if (isSnapped) "◈ " else "") + TimeFormat.msToShort(playheadMs),
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Dedicated Pinch-to-Zoom gesture detector for the VideoTimeline viewport.
 *
 * Uses [PointerEventPass.Initial] to detect 2-pointer multi-touch pinch gestures before
 * single-finger child gestures (such as clip dragging, trimming, or ruler scrubbing) consume them.
 *
 * Automatically computes centroid X position and pinch zoom ratio, consuming the multi-touch
 * pointers to guarantee smooth scaling and anchoring without triggering unwanted child taps/drags.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTimelinePinchZoom(
    onPinchStart: () -> Unit,
    onPinchZoom: (centroidX: Float, zoomRatio: Float) -> Unit,
    onPinchEnd: () -> Unit
) {
    awaitEachGesture {
        var isPinching = false
        var previousDistance = 0f

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val activePointers = event.changes.filter { it.pressed }

            if (activePointers.size >= 2) {
                val p1 = activePointers[0].position
                val p2 = activePointers[1].position
                val currentDistance = hypot(p1.x - p2.x, p1.y - p2.y)
                val centroidX = (p1.x + p2.x) / 2f

                if (!isPinching) {
                    if (currentDistance > 15f) {
                        isPinching = true
                        previousDistance = currentDistance
                        onPinchStart()
                        activePointers.forEach { it.consume() }
                    }
                } else {
                    if (previousDistance > 0f) {
                        val zoomRatio = currentDistance / previousDistance
                        if (abs(zoomRatio - 1f) > 0.001f) {
                            onPinchZoom(centroidX, zoomRatio)
                        }
                    }
                    previousDistance = currentDistance
                    activePointers.forEach { it.consume() }
                }
            } else {
                if (isPinching) {
                    isPinching = false
                    previousDistance = 0f
                    onPinchEnd()
                }
            }

            if (activePointers.isEmpty()) {
                if (isPinching) {
                    isPinching = false
                    previousDistance = 0f
                    onPinchEnd()
                }
                break
            }
        }
    }
}

/**
 * Floating Zoom HUD badge displaying current scale and frame editing precision level.
 */
@Composable
private fun TimelineZoomHud(
    zoom: Float,
    onResetZoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val precisionLabel = when {
        zoom >= 6.0f -> "Frame Precision (30fps)"
        zoom >= 3.0f -> "Fine Sub-Second"
        zoom >= 1.5f -> "Detailed View"
        zoom <= 0.8f -> "Overview"
        else -> "Standard View"
    }

    Surface(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        color = Color(0xF00F131D),
        border = BorderStroke(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.NeonCyan)
            )

            Text(
                text = "${String.format(java.util.Locale.US, "%.1f", zoom)}x",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = precisionLabel,
                color = ApexPalette.NeonCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )

            if (abs(zoom - 1.0f) > 0.05f) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E2536))
                        .clickable(onClick = onResetZoom)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "1.0x",
                        color = Color(0xFFCBD5E1),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Interactive Transition Junction Button between adjacent video clips on the timeline.
 */
@Composable
private fun TimelineTransitionJunctionBadge(
    transition: ClipTransition?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasTransition = transition != null
    val transDef = remember(transition?.type) {
        TransitionLibrary.getById(transition?.type)
    }

    Box(
        modifier = modifier
            .width(if (hasTransition) 36.dp else 26.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (hasTransition) Color(0xFF1E1A33)
                else Color(0xFF141824)
            )
            .border(
                width = if (hasTransition) 1.5.dp else 1.dp,
                color = if (hasTransition) ApexPalette.NeonCyan else Color(0xFF2E384D),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (hasTransition) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = transDef?.icon ?: Icons.Default.Transform,
                    contentDescription = transDef?.name ?: "Transition",
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = transDef?.badgeText?.take(4) ?: "TRN",
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Transform,
                    contentDescription = "Add Transition",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    "+",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

