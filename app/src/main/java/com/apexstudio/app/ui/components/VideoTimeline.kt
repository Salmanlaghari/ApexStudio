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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VolumeOff
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.apexstudio.app.data.media.SyntheticWaveform
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.ClipTransition
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.TransitionLibrary
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.sample
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
    selectedAudioTrackId: String? = null,
    onSelectAudioTrack: (String) -> Unit = {},
    isPlaying: Boolean = false,
    audioWaveform: FloatArray = FloatArray(0),
    beatMarkersMs: List<Long> = emptyList(),
    snapToBeat: Boolean = false,
    thumbnailsByClip: Map<String, Map<Int, Bitmap>> = emptyMap(),
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
    // Multi-layer video tracks (CapCut-style, up to 10). Layer 0 = main (V1),
    // layers 1..9 = overlay layers (V2..V10) composited PiP above the main.
    hiddenVideoLayers: Set<Int> = emptySet(),
    lockedVideoLayers: Set<Int> = emptySet(),
    // Explicitly-added empty layers (rendered as empty rows).
    extraVideoLayers: Set<Int> = emptySet(),
    onToggleLayerVisibility: (Int) -> Unit = {},
    onToggleLayerLock: (Int) -> Unit = {},
    onDeleteLayer: (Int) -> Unit = {},
    onAddLayer: () -> Unit = {},
    onMoveClipToLayer: (String, Int) -> Unit = { _, _ -> },
    /** User tapped "+ Add clip" on overlay layer [layer]: arm the picker routing, then open it. */
    onAddClipToLayer: (Int) -> Unit = {},
    /**
     * 60fps-safe playhead stream. When provided, the playhead + ruler
     * collect it in isolated composables so playback ticks do NOT
     * recompose the whole timeline. Falls back to [playheadMs] when null.
     */
    playerPositionFlow: kotlinx.coroutines.flow.StateFlow<Long>? = null,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val safeDurationMs = totalDurationMs.coerceAtLeast(1000L)
    // 60fps playhead isolation: the raw flow drives ONLY the playhead overlay
    // (tiny composable). Everything else uses a 10Hz-sampled snapshot so
    // playback ticks don't recompose tracks/clips/thumbnails every frame.
    var uiPlayheadMs by remember { mutableStateOf(playheadMs.coerceIn(0L, safeDurationMs)) }
    LaunchedEffect(playerPositionFlow) {
        val flow = playerPositionFlow
        if (flow != null) {
            flow.sample(100).collect { uiPlayheadMs = it.coerceIn(0L, safeDurationMs) }
        }
    }
    // Keep the snapshot in sync for scrubbing (non-flow path / user seeks).
    LaunchedEffect(playheadMs) {
        if (playerPositionFlow == null) uiPlayheadMs = playheadMs.coerceIn(0L, safeDurationMs)
        else if (kotlin.math.abs(playheadMs - uiPlayheadMs) > 500L) uiPlayheadMs = playheadMs.coerceIn(0L, safeDurationMs)
    }
    val safePlayheadMs = uiPlayheadMs
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

    // Auto-scroll when playing — edge-triggered (not every tick) so it never
    // fights the user's finger. Uses instant scrollTo: animateScrollTo on
    // every playhead update caused visible stutter during playback.
    LaunchedEffect(isPlaying, safePlayheadMs) {
        if (isPlaying) {
            val playheadX = safePlayheadMs * msToDp
            val playheadPx = with(density) { playheadX.dp.toPx() }.roundToInt()
            val viewportWidth = scrollState.viewportSize
            val edge = (viewportWidth * 0.85f).toInt()
            // Only recenter when the playhead is about to leave the viewport.
            if (playheadPx > scrollState.value + edge || playheadPx < scrollState.value) {
                val targetScroll = (playheadPx - viewportWidth / 2).coerceAtLeast(0)
                scrollState.scrollTo(targetScroll)
            }
        }
    }

    // Drag-and-drop state for video clips
    // V1 lane: main video clips + photo (IMAGE) clips on layer 0 share the
    // filmstrip lane (photo clips previously rendered nowhere -> empty V1
    // track). Overlay layers (1..9) render in their own rows below.
    val videoClips = remember(clips) {
        clips.filter {
            (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE) && it.trackIndex == 0
        }
    }
    // Multi-layer video tracks: distinct trackIndex values across all video
    // clips (0..9). Layer 0 is always present (main V1); overlay layers
    // 1..9 appear when they hold clips. Hidden layers still render a muted
    // placeholder row so the layer structure stays visible.
    val videoLayers = remember(clips, extraVideoLayers) {
        val used = clips.filter {
            it.type == ClipType.VIDEO || it.type == ClipType.IMAGE || it.type == ClipType.OVERLAY
        }.map { it.trackIndex.coerceIn(0, 9) }.toSortedSet()
        // Extra (empty) layers that still hold clips merge into `used`.
        ((used + 0 + extraVideoLayers).toSortedSet()).toList().take(10)
    }
    val overlayLayerHeight = 58.dp
    val videoLayersHeight = v1Height + overlayLayerHeight * videoLayers.count { it > 0 }
    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragAccumulatedOffsetPx by remember { mutableFloatStateOf(0f) }
    var targetDropIndex by remember { mutableIntStateOf(-1) }

    // Measured clip bounds in horizontal track space (index -> Pair(leftPx, widthPx))
    val clipLayoutBounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }

    // Tracks Mute / Active states
    var isAudioMuted by remember { mutableStateOf(false) }
    // (Per-layer visibility replaced the old single overlay toggle.)

    // Multi-select state (pro timeline): select several clips, then move/delete together.
    var multiSelectMode by remember { mutableStateOf(false) }
    var multiSelectedIds by remember { mutableStateOf(setOf<String>()) }

    // Snap indicator: true when the playhead sits on (or within threshold of) a beat marker
    // while snap-to-beat is enabled — shown as a badge on the playhead.
    val snapThresholdMs = 200L
    val isPlayheadSnapped = snapToBeat &&
        beatMarkersMs.any { kotlin.math.abs(it - safePlayheadMs) <= snapThresholdMs }

    // Mockup track geometry (CapCut/VN style): thin elegant tracks (~44dp,
    // FX slimmer) with generous dark spacing between rows. The sidebar pill
    // cells use the same heights so pills stay centered on their rows.
    // A1 grows with the audio track count (34dp per extra row, capped) so
    // every track stays visible and tappable — never collapsed or hidden.
    val rulerHeight = 30.dp
    val v1Height = 44.dp
    val v2Height = 44.dp
    val fxHeight = 36.dp
    // A1 audio space (item 2 fix, adapted to the thin mockup geometry):
    // the lane grows 34dp per extra audio row (capped) so every track stays
    // visible and tappable — never collapsed or hidden. Single-audio default
    // keeps item 5's exact mockup match (228dp total).
    val a1ExtraDp = ((audioTracks.size - 1).coerceAtLeast(0) * 34).coerceAtMost(102)
    val a1Height = (44 + a1ExtraDp).dp
    // Dynamic timeline height: ruler + V1 + N overlay layers + optional text
    // lane + FX + A1, with 10dp spacing between rows (matches spacedBy).
    val overlayLayerCount = videoLayers.count { it in 1..9 }
    val hasTextLane = textOverlays.isNotEmpty()
    val timelineTotalDp = 30 + 44 +
        overlayLayerCount * (58 + 10) +
        (if (hasTextLane) (32 + 10) else 0) +
        36 + 44 + a1ExtraDp + 10 * 4

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(timelineTotalDp.dp)
            .background(Color(0xFF090B10))
            .border(1.dp, Color(0xFF171B26))
    ) {
        // --- 1. PINNED LEFT TRACK HEADER SIDEBAR ---
        TimelineLeftSidebar(
            zoomFactor = activeZoomDisplay,
            rulerHeight = rulerHeight,
            v1Height = v1Height,
            overlayLayerHeight = overlayLayerHeight,
            videoLayers = videoLayers,
            hiddenVideoLayers = hiddenVideoLayers,
            lockedVideoLayers = lockedVideoLayers,
            hasTextLane = hasTextLane,
            fxHeight = fxHeight,
            a1Height = a1Height,
            onToggleLayerVisibility = onToggleLayerVisibility,
            onToggleLayerLock = onToggleLayerLock,
            onDeleteLayer = onDeleteLayer,
            onAddLayer = onAddLayer,
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
            }
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            .height(rulerHeight)
                    )

                    // TRACK 1: V1 (Main Video + Photo Filmstrip Track, layer 0 only)
                    TimelineVideoTrack(
                        videoClips = videoClips,
                        selectedClipId = selectedClipId,
                        msToDp = msToDp,
                        playheadMs = safePlayheadMs,
                        thumbnailsByClip = thumbnailsByClip,
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
                            .height(v1Height)
                    )

                    // TRACKS 2..N: Overlay video layers V2..V10 (CapCut-style).
                    // Each used layer 1..9 gets its own PiP row, absolutely
                    // positioned by timelineOffsetMs, with per-layer show/hide
                    // + lock driven from the sidebar. Hidden layers render a
                    // muted placeholder so the structure stays visible.
                    videoLayers.filter { it in 1..9 }.forEach { layer ->
                        val layerClips = remember(clips, layer) {
                            clips.filter {
                                it.trackIndex == layer &&
                                    (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE || it.type == ClipType.OVERLAY)
                            }
                        }
                        TimelineVideoLayerRow(
                            layerIndex = layer,
                            clips = layerClips,
                            thumbnailsByClip = thumbnailsByClip,
                            selectedClipId = selectedClipId,
                            msToDp = msToDp,
                            isHidden = layer in hiddenVideoLayers,
                            isLocked = layer in lockedVideoLayers,
                            onSelectClip = onSelectClip,
                            onAddClip = { onAddClipToLayer(layer) },
                            onMoveClipOffset = onMoveClipOffset,
                            onDeleteClip = onDeleteClip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(overlayLayerHeight)
                        )
                    }

                    // Text overlays lane (kept below the video layers).
                    TimelineTextLane(
                        textOverlays = textOverlays,
                        allClips = clips,
                        msToDp = msToDp,
                        onSeekToKeyframe = onScrub,
                        onMoveTextKeyframe = onMoveTextKeyframe,
                        onToggleTextKeyframeAtPlayhead = onToggleTextKeyframeAtPlayhead
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
                            .height(fxHeight)
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
                        selectedAudioTrackId = selectedAudioTrackId,
                        onSelectAudioTrack = onSelectAudioTrack,
                        onAddAudio = { onAddMedia(ClipType.AUDIO) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(a1Height)
                    )
                }

                // --- 3. UNIFIED VERTICAL PLAYHEAD LINE & TIMESTAMP BADGE ---
                // Isolated: collects the raw position flow directly so the
                // playhead glides at full rate without recomposing tracks.
                TimelinePlayheadOverlay(
                    playerPositionFlow = playerPositionFlow,
                    fallbackMs = safePlayheadMs,
                    durationMs = safeDurationMs,
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
    thumbnailsByClip: Map<String, Map<Int, Bitmap>> = emptyMap(),
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
    onSelectAudioTrack: (String) -> Unit = {},
    // Multi-layer controls (wired by the editor screen).
    hiddenVideoLayers: Set<Int> = state.hiddenVideoLayers,
    lockedVideoLayers: Set<Int> = state.lockedVideoLayers,
    extraVideoLayers: Set<Int> = state.extraVideoLayers,
    onToggleLayerVisibility: (Int) -> Unit = {},
    onToggleLayerLock: (Int) -> Unit = {},
    onDeleteLayer: (Int) -> Unit = {},
    onAddLayer: () -> Unit = {},
    onMoveClipToLayer: (String, Int) -> Unit = { _, _ -> },
    onAddClipToLayer: (Int) -> Unit = {},
    playerPositionFlow: kotlinx.coroutines.flow.StateFlow<Long>? = null,
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
        thumbnailsByClip = thumbnailsByClip,
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
        onMoveTextKeyframe = onMoveTextKeyframe,
        selectedAudioTrackId = state.selectedAudioTrackId,
        onSelectAudioTrack = onSelectAudioTrack,
        hiddenVideoLayers = hiddenVideoLayers,
        lockedVideoLayers = lockedVideoLayers,
        extraVideoLayers = extraVideoLayers,
        onToggleLayerVisibility = onToggleLayerVisibility,
        onToggleLayerLock = onToggleLayerLock,
        onDeleteLayer = onDeleteLayer,
        onAddLayer = onAddLayer,
        onMoveClipToLayer = onMoveClipToLayer,
        onAddClipToLayer = onAddClipToLayer,
        playerPositionFlow = playerPositionFlow
    )
}

// ==========================================
// SUB-COMPONENTS
// ==========================================

/**
 * Pinned left rail: compact zoom cell (aligned with the ruler) + mockup
 * track pills (V1 cyan, V2 purple, FX purple, A1 blue), each vertically
 * centered on its track row.
 */
@Composable
private fun TimelineLeftSidebar(
    zoomFactor: Float,
    rulerHeight: Dp,
    v1Height: Dp,
    overlayLayerHeight: Dp,
    videoLayers: List<Int>,
    hiddenVideoLayers: Set<Int>,
    lockedVideoLayers: Set<Int>,
    hasTextLane: Boolean,
    fxHeight: Dp,
    a1Height: Dp,
    onToggleLayerVisibility: (Int) -> Unit,
    onToggleLayerLock: (Int) -> Unit,
    onDeleteLayer: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(64.dp)
            .fillMaxHeight()
            .background(Color(0xFF0C0E14))
            .border(width = 1.dp, color = Color(0xFF1E2230))
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Zoom cell (aligned with the ruler row)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rulerHeight),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onZoomOut),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                }
                Text(
                    text = "${String.format(java.util.Locale.US, "%.1f", zoomFactor)}x",
                    color = ApexPalette.NeonCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .clickable(onClick = onResetZoom)
                        .padding(horizontal = 1.dp)
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onZoomIn),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                }
            }
        }

        // V1 main layer pill (layer 0 can never be hidden/locked/deleted).
        TrackPill(label = "V1", accent = ApexPalette.NeonCyan, height = v1Height)

        // Overlay layer pills V2..V10 with eye / lock / delete controls.
        videoLayers.filter { it in 1..9 }.forEach { layer ->
            VideoLayerPill(
                layerIndex = layer,
                height = overlayLayerHeight,
                isHidden = layer in hiddenVideoLayers,
                isLocked = layer in lockedVideoLayers,
                onToggleVisibility = { onToggleLayerVisibility(layer) },
                onToggleLock = { onToggleLayerLock(layer) },
                onDelete = { onDeleteLayer(layer) }
            )
        }

        // Add-layer button (up to 10 layers total).
        if (videoLayers.size < 10) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141824))
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onAddLayer),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add layer", tint = ApexPalette.NeonCyan, modifier = Modifier.size(12.dp))
                    Text("V${videoLayers.size + 1}", color = ApexPalette.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (hasTextLane) {
            TrackPill(label = "TXT", accent = ApexPalette.NeonPink, height = 32.dp)
        }
        TrackPill(label = "FX", accent = ApexPalette.NeonPurple, height = fxHeight)
        TrackPill(label = "A1", accent = ApexPalette.TrackBlue, height = a1Height)
    }
}

/**
 * Overlay layer pill (V2..V10) with show/hide (eye), lock and delete.
 */
@Composable
private fun VideoLayerPill(
    layerIndex: Int,
    height: Dp,
    isHidden: Boolean,
    isLocked: Boolean,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isHidden) Color(0xFF2A2F3A) else ApexPalette.NeonPurple)
                .alpha(if (isHidden) 0.6f else 1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "V${layerIndex + 1}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (isHidden) "Show layer" else "Hide layer",
                tint = if (isHidden) Color(0xFF64748B) else Color.White,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onToggleVisibility)
            )
            Icon(
                if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (isLocked) "Unlock layer" else "Lock layer",
                tint = if (isLocked) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onToggleLock)
            )
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "Delete layer",
                tint = Color(0xFFF87171),
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onDelete)
            )
        }
    }
}

@Composable
private fun TrackPill(
    label: String,
    accent: Color,
    height: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
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
                        text = if (secondWidthDp >= 180.dp) TimeFormat.msToTimecode(sec * 1000L, includeFrames = true)
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
                            text = TimeFormat.msToTimecode(sec * 1000L + 500L, includeFrames = true).takeLast(3),
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
/**
 * One overlay video layer row (V2..V10, CapCut-style multi-layer).
 *
 * Clips are absolutely positioned by [MediaClip.timelineOffsetMs] and render
 * as PiP blocks with a filmstrip thumbnail strip, matching the main V1
 * track's look. Locked layers ignore gestures; hidden layers show a muted
 * placeholder row.
 */
@Composable
private fun TimelineVideoLayerRow(
    layerIndex: Int,
    clips: List<MediaClip>,
    thumbnailsByClip: Map<String, Map<Int, Bitmap>>,
    selectedClipId: String?,
    msToDp: Float,
    isHidden: Boolean,
    isLocked: Boolean,
    onSelectClip: (String) -> Unit,
    onAddClip: () -> Unit,
    onMoveClipOffset: (clipId: String, offsetMs: Long) -> Unit,
    onDeleteClip: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHidden) Color(0xFF0B0D14) else Color(0xFF0F1420))
            .border(1.dp, Color(0xFF1E2838), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        if (isHidden) {
            Text(
                "Layer V${layerIndex + 1} hidden",
                color = Color(0xFF475569),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
            return@Column
        }

        Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            clips.forEach { clip ->
                val isSelected = clip.id == selectedClipId
                val clipDur = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(500L)
                val clipWidthDp = maxOf((clipDur * msToDp).dp, 56.dp)
                val clipOffsetDp = (clip.timelineOffsetMs * msToDp).dp
                var dragDx by remember(clip.id) { mutableFloatStateOf(0f) }
                var isDragging by remember(clip.id) { mutableStateOf(false) }
                val clipThumbs = thumbnailsByClip[clip.id] ?: emptyMap()

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
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (isSelected) Color(0xFF0B3B48)
                                else if (isDragging) Color(0xFF134A5C)
                                else Color(0xFF0C2B38)
                            )
                            .border(
                                width = if (isSelected || isDragging) 1.5.dp else 1.dp,
                                color = if (isSelected) Color.White
                                else if (isDragging) ApexPalette.NeonCyan
                                else ApexPalette.NeonCyan.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(5.dp)
                            )
                            .pointerInput(clip.id, isLocked) {
                                if (!isLocked) {
                                    detectDragGestures(
                                        onDragStart = { isDragging = true },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragDx += dragAmount.x
                                        },
                                        onDragEnd = {
                                            val deltaMs = (dragDx / msToDp).toLong()
                                            val newOffset =
                                                (clip.timelineOffsetMs + deltaMs).coerceAtLeast(0L)
                                            dragDx = 0f
                                            isDragging = false
                                            if (newOffset != clip.timelineOffsetMs) {
                                                onMoveClipOffset(clip.id, newOffset)
                                            }
                                        },
                                        onDragCancel = { dragDx = 0f; isDragging = false }
                                    )
                                }
                            }
                            .clickable { onSelectClip(clip.id) }
                    ) {
                        // Filmstrip thumbnails inside the PiP block.
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            val totalSec = (clipDur / 1000L).toInt().coerceIn(1, 6)
                            for (sec in 0 until totalSec) {
                                val frameBmp = clipThumbs[sec] ?: clipThumbs[0]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF16202F))
                                ) {
                                    if (frameBmp != null) {
                                        Image(
                                            bitmap = frameBmp.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                        // Clip name badge (bottom-left).
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                clip.name.ifEmpty { "V${layerIndex + 1}" },
                                color = Color.White,
                                fontSize = 8.sp,
                                maxLines = 1
                            )
                        }
                        if (isLocked) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(10.dp)
                            )
                        }
                    }

                    // Keyframe diamond strip for this layer clip.
                    KeyframeDiamondStrip(
                        keyframes = clip.keyframes.keyframes,
                        spanStartMs = clip.timelineOffsetMs,
                        spanDurationMs = clipDur,
                        stripWidthDp = clipWidthDp,
                        msToDp = msToDp,
                        onSeekToKeyframe = {},
                        onMoveKeyframe = { _, _ -> },
                        onAddAtPlayhead = {},
                        accentColor = ApexPalette.NeonCyan,
                        modifier = Modifier.height(10.dp)
                    )
                }
            }

            if (clips.isEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onAddClip)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = ApexPalette.NeonCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        "+ Add clip (V${layerIndex + 1})",
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
 * Text overlays lane — positioned by startMs, each with its own keyframe
 * diamond strip. Rendered below the video layers (was Lane 2 of the old
 * single V2 overlay track).
 */
@Composable
private fun TimelineTextLane(
    textOverlays: List<TextOverlay>,
    allClips: List<MediaClip>,
    msToDp: Float,
    onSeekToKeyframe: (Long) -> Unit = {},
    onMoveTextKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _, _ -> },
    onToggleTextKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (textOverlays.isEmpty()) return
    Box(modifier = modifier.fillMaxWidth().height(32.dp)) {
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
                        .height(18.dp)
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
                    modifier = Modifier.height(12.dp)
                )
            }
        }
    }
}

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
        Box(modifier = Modifier.fillMaxWidth().height(38.dp)) {
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
                            .height(26.dp)
                            .graphicsLayer { translationX = dragDx }
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) Color(0xFF0B3B48)
                                else if (isDragging) Color(0xFF134A5C)
                                else Color(0xFF0C2B38)
                            )
                            .border(
                                width = if (isSelected || isDragging) 1.5.dp else 1.dp,
                                // CapCut-style: selected clip gets a white border.
                                color = if (isSelected) Color.White
                                else if (isDragging) ApexPalette.NeonCyan
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
                        modifier = Modifier.height(12.dp)
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
            Box(modifier = Modifier.fillMaxWidth().height(32.dp)) {
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
                                .height(18.dp)
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
                            modifier = Modifier.height(12.dp)
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
    thumbnailsByClip: Map<String, Map<Int, Bitmap>>,
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isMultiSelected) Color(0xFF3A2E00)
                            else if (isSelected) Color(0xFF101623)
                            else Color(0xFF151824)
                        )
                        .border(
                            width = if (isBeingDragged) 2.5.dp else if (isSelected || isMultiSelected) 2.dp else 1.dp,
                            color = if (isBeingDragged) ApexPalette.NeonCyan
                            else if (isMultiSelected) Color(0xFFFFB300)
                            // Mockup: selected clip gets a teal border highlight.
                            else if (isSelected) ApexPalette.NeonCyan
                            else Color(0xFF2E384D),
                            shape = RoundedCornerShape(10.dp)
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
                    // Mockup filmstrip: per-clip thumbnails, perforation strip,
                    // teal selection band. Photo clips reuse their single
                    // frame across cells.
                    val clipThumbs = thumbnailsByClip[clip.id] ?: emptyMap()
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(3.dp)
                        ) {
                            val totalSec = (clipDur / 1000L).toInt().coerceIn(1, 8)
                            for (sec in 0 until totalSec) {
                                val frameBmp = clipThumbs[sec] ?: clipThumbs[0]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = 1.dp)
                                        .clip(RoundedCornerShape(3.dp))
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
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Movie,
                                                contentDescription = null,
                                                tint = Color(0xFF334155),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Film perforations (mockup detail).
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .padding(horizontal = 5.dp)
                        ) {
                            val dotW = 2.5.dp.toPx()
                            val dotH = 4.dp.toPx()
                            val gap = 5.dp.toPx()
                            var x = 0f
                            val cy = size.height / 2f
                            while (x + dotW <= size.width) {
                                drawRoundRect(
                                    color = ApexPalette.NeonCyan.copy(alpha = 0.75f),
                                    topLeft = Offset(x, cy - dotH / 2f),
                                    size = Size(dotW, dotH),
                                    cornerRadius = CornerRadius(1.dp.toPx())
                                )
                                x += dotW + gap
                            }
                        }

                    }

                    // Trim Grips on active clip — draggable to trim start/end.
                    if (isSelected && !multiSelectMode) {
                        var trimDragAccumX by remember(clip.id) { mutableFloatStateOf(0f) }
                        // Left grip: trim start
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(16.dp).fillMaxHeight()
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
                                    .size(width = 5.dp, height = 28.dp)
                                    .background(Color(0xFFFFD700), RoundedCornerShape(2.dp))
                            )
                        }
                        // Right grip: trim end
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(16.dp).fillMaxHeight()
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
                                    .size(width = 5.dp, height = 28.dp)
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
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF12101C))
            .border(1.dp, Color(0xFF2A2440), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Mockup: full-width purple gradient FX bar, always visible.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                ApexPalette.NeonPurple.copy(alpha = 0.8f),
                                ApexPalette.NeonPurple.copy(alpha = 0.35f)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    if (activeFxId != null) {
                        Text(
                            "FX: ${activeFxId.replace("_", " ").uppercase()}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
 * A1 audio lane. The lane is ALWAYS rendered — never collapsed or hidden —
 * with a visible waveform (real analysis when available, deterministic
 * synthetic fallback otherwise). Every audio track gets its own row so
 * added audio is actually present and manageable in the timeline.
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
    selectedAudioTrackId: String? = null,
    onSelectAudioTrack: (String) -> Unit = {},
    onAddAudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val samples = remember(audioWaveform, audioTracks.map { it.id }) {
        if (audioWaveform.isNotEmpty() && audioWaveform.any { it > 0.01f }) audioWaveform
        else SyntheticWaveform.generate(
            (audioTracks.firstOrNull()?.id ?: "master").hashCode().toLong(), 160
        )
    }
    val effectiveSelectedId = selectedAudioTrackId ?: audioTracks.firstOrNull()?.id

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0C1322))
            .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .verticalScroll(rememberScrollState())
    ) {
        if (audioTracks.isEmpty()) {
            // Empty state: visible waveform + add affordance.
            // Empty state: visible waveform + add affordance. Height matches the
            // thin mockup A1 lane (44dp base) so nothing clips.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                AudioWaveformBars(
                    samples = samples,
                    durationMs = durationMs,
                    playheadMs = playheadMs,
                    beatMarkersMs = beatMarkersMs,
                    isMuted = isMuted,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onAddAudio)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = ApexPalette.NeonCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        "+ Add Audio (A1)",
                        color = Color(0xFF67E8F9),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            audioTracks.forEach { track ->
                val isSelected = track.id == effectiveSelectedId
                AudioTrackLaneRow(
                    track = track,
                    isSelected = isSelected,
                    samples = samples,
                    durationMs = durationMs,
                    playheadMs = playheadMs,
                    beatMarkersMs = beatMarkersMs,
                    isMuted = isMuted || track.isMuted,
                    onSelect = { onSelectAudioTrack(track.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * One audio-track row inside the A1 lane: kind colour strip, name,
 * mini waveform, mute badge. Tapping selects the track (white border),
 * switching the quick actions to the audio tools for that track.
 */
@Composable
private fun AudioTrackLaneRow(
    track: AudioTrack,
    isSelected: Boolean,
    samples: FloatArray,
    durationMs: Long,
    playheadMs: Long,
    beatMarkersMs: List<Long>,
    isMuted: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Accent follows the mixer's track-type language (AudioMixerPanel):
    // full-volume tracks read as SFX (amber), the rest as music (green).
    // AudioTrack.Kind is not persisted on the model, so the same
    // volume heuristic keeps both UIs consistent.
    val accent = if (track.volume >= 0.99f) Color(0xFFFBBF24) else Color(0xFF34D399)
    Row(
        modifier = modifier
            .height(40.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF0B3B48) else Color(0xFF111827))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Color.White else accent.copy(alpha = 0.35f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = track.name.ifBlank { "Audio" },
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 110.dp)
        )
        if (track.isMuted) {
            Icon(
                Icons.Default.VolumeOff,
                contentDescription = "Muted",
                tint = Color(0xFF64748B),
                modifier = Modifier.size(12.dp)
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            AudioWaveformBars(
                samples = samples,
                durationMs = durationMs,
                playheadMs = playheadMs,
                beatMarkersMs = beatMarkersMs,
                isMuted = isMuted,
                barColor = accent,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Shared waveform bar renderer used by the A1 lane rows: full-width bars
 * with played-progress tinting and beat-marker highlights.
 */
@Composable
private fun AudioWaveformBars(
    samples: FloatArray,
    durationMs: Long,
    playheadMs: Long,
    beatMarkersMs: List<Long>,
    isMuted: Boolean,
    barColor: Color = ApexPalette.NeonCyan,
    modifier: Modifier = Modifier
) {
    val barCount = 120
    val progress = (playheadMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { i ->
            val barFrac = i.toFloat() / barCount.toFloat()
            val isPlayed = barFrac <= progress
            val sampleIdx = (barFrac * (samples.size - 1)).toInt().coerceIn(samples.indices)
            val amp = samples[sampleIdx].coerceIn(0.1f, 1.0f)

            // Beat markers (from the Beats tool) render as bright bars
            // whenever markers exist — independent of snap-to-beat.
            val isBeat = beatMarkersMs.any { beatMs ->
                val beatFrac = beatMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f)
                kotlin.math.abs(beatFrac - barFrac) < (1.2f / barCount)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight(amp)
                        .background(
                            color = when {
                                isMuted -> Color(0xFF475569)
                                isBeat -> ApexPalette.NeonCyan
                                isPlayed -> Color.White
                                else -> barColor.copy(alpha = 0.85f)
                            },
                            shape = RoundedCornerShape(1.dp)
                        )
                )
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

    // Mockup: small downward triangle handle at the top of the playhead —
    // cyan fill with a white outline.
    Box(
        modifier = Modifier
            .offset(x = (playheadX - 9.dp).coerceAtLeast(0.dp), y = 0.dp)
            .zIndex(210f)
            .size(width = 18.dp, height = 14.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val triangle = Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(w / 2f, h)
                close()
            }
            drawPath(triangle, color = ApexPalette.NeonCyan)
            drawPath(triangle, color = Color.White, style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}

/**
 * 60fps playhead overlay — collects the raw position flow in THIS tiny
 * composable only, so the playhead glides smoothly during playback
 * without recomposing the tracks, clips or thumbnails above.
 */
@Composable
private fun TimelinePlayheadOverlay(
    playerPositionFlow: kotlinx.coroutines.flow.StateFlow<Long>?,
    fallbackMs: Long,
    durationMs: Long,
    msToDp: Float,
    isSnapped: Boolean = false,
    modifier: Modifier = Modifier
) {
    val liveMs = if (playerPositionFlow != null) {
        playerPositionFlow.collectAsStateWithLifecycle().value
    } else {
        fallbackMs
    }
    val posMs = liveMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
    TimelinePlayhead(
        playheadX = (posMs * msToDp).dp + 16.dp,
        playheadMs = posMs,
        onScrub = {},
        msToDp = msToDp,
        isSnapped = isSnapped,
        modifier = modifier
    )
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

