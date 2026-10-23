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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.apexstudio.app.data.audio.TrackWaveformCache
import com.apexstudio.app.data.stickers.StickerImageCache
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.data.export.ProjectTextOverlayTimeline
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.ClipTimeMapping
import com.apexstudio.app.domain.model.ClipTransition
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.VideoAdjustments
import com.apexstudio.app.domain.model.TransitionLibrary
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import com.apexstudio.app.domain.model.WorkspaceFilterPresets
import com.apexstudio.app.presentation.viewmodel.AudioTrackPlacement
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.sample
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal const val TIMELINE_HEADER_HEIGHT_DP = 44
internal const val OVERLAY_LAYER_HEIGHT_DP = 60
internal const val OVERLAY_FILMSTRIP_HEIGHT_DP = 52
internal const val OVERLAY_CLIP_HEIGHT_DP = 42
internal const val AUDIO_LANE_ROW_HEIGHT_DP = 46
internal const val AUDIO_TRACK_ITEM_HEIGHT_DP = 40

/** Timeline scale is expressed in dp/ms, while pointer drag deltas are physical px. */
internal fun sourceTrimDeltaToPx(sourceDeltaMs: Long, msToDp: Float, density: Float): Float =
    sourceDeltaMs.toFloat() * msToDp * density

internal fun sourceTrimDeltaToMs(deltaPx: Float, msToDp: Float, density: Float): Long {
    val pxPerMs = msToDp * density
    return if (pxPerMs.isFinite() && pxPerMs > 0f && deltaPx.isFinite()) {
        (deltaPx / pxPerMs).roundToLong()
    } else 0L
}

/** V1 retains the historical, visually dominant filmstrip height. */
@Suppress("UNUSED_PARAMETER")
internal fun mainVideoLaneHeight(
    availableTimelineHeightDp: Float,
    hasMainVideo: Boolean,
    supportingLanesHeightDp: Float = 0f,
    hasWaveformStrip: Boolean = false,
): Dp = if (hasMainVideo) 64.dp else 0.dp

/** Content-bounded height for normal editor mode; focused mode may still fill its viewport. */
internal fun compactTimelineHeight(state: EditorState): Dp {
    val project = state.project
    val clips = project?.clips.orEmpty()
    val layerIndex = TimelineLayerContentIndex.from(clips)
    val hasMain = layerIndex.videoByLayer[0].orEmpty().any { it.type != ClipType.OVERLAY }
    val overlayLayers = layerIndex.videoByLayer.keys.count { it in 1..9 }
    val textLayers = mapProjectTextLayers(
        clips,
        clips.flatMap { it.textOverlays },
        layerIndex.textOwnerByOverlayId,
    )
    val textRowCount = timelineLaneRowCount(timelineLaneRowIndices(textLayers.map {
        TimelineLaneSpan(it.startMs, it.endMs)
    }))
    val stickerSpans = project?.stickers.orEmpty().map { sticker ->
        val start = sticker.startMs.coerceAtLeast(0L)
        TimelineLaneSpan(start, if (sticker.endMs == Long.MAX_VALUE) start + 3000L else sticker.endMs)
    }
    val stickerRowCount = timelineLaneRowCount(timelineLaneRowIndices(stickerSpans))
    val hasText = textRowCount > 0
    val hasStickers = stickerRowCount > 0
    val hasFx = !state.activeFilterId.isNullOrBlank() || !state.activeFxId.isNullOrBlank() ||
        adjustmentsSummary(state.adjustments).isNotEmpty()
    val musicRows = project?.audioTracks.orEmpty().count { it.kind == AudioTrack.Kind.MUSIC }
    val voiceRows = project?.audioTracks.orEmpty().count { it.kind != AudioTrack.Kind.MUSIC }
    val hasWaveform = state.audioWaveform.isNotEmpty() && hasMain

    // Keep this estimate in lockstep with the rendered header, ruler, and
    // historical portrait timeline rows below.
    var height = TIMELINE_HEADER_HEIGHT_DP + 36
    if (hasMain) height += 10 + 64
    if (hasWaveform) height += 10 + 12
    val supportRows = overlayLayers + (if (hasText) 1 else 0) +
        (if (hasStickers) 1 else 0) + (if (hasFx) 1 else 0) +
        (if (musicRows > 0) 1 else 0) + (if (voiceRows > 0) 1 else 0)
    if (supportRows > 0) height += 10
    height += overlayLayers * (OVERLAY_LAYER_HEIGHT_DP + 10)
    if (hasText) height += textRowCount * 40 + 10
    if (hasStickers) height += stickerRowCount * 40 + 10
    if (hasFx) height += 40 + 10
    height += musicRows * AUDIO_LANE_ROW_HEIGHT_DP + if (musicRows > 0) 10 else 0
    height += voiceRows * AUDIO_LANE_ROW_HEIGHT_DP + if (voiceRows > 0) 10 else 0
    return height.dp
}

/** Small in-row identity chip: it overlays the time-zero edge instead of reserving a gutter. */
@Composable
private fun TimelineLaneIdentity(label: String, accent: Color) {
    Text(
        text = label,
        color = Color(0xFFE5F2FF),
        fontSize = 8.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xE6101A2B))
            .border(0.75.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

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
    // Pro-timeline layers (mockup): project-level stickers (NOT the legacy
    // per-clip list), selection ids, and the filter/FX/adjustment state that
    // the FX lane renders as segments. Everything the user adds must appear
    // as a visible, tappable layer.
    selectedTextOverlayId: String? = null,
    selectedStickerId: String? = null,
    activeFilterId: String? = null,
    filterIntensity: Float = 1f,
    adjustments: VideoAdjustments = VideoAdjustments(),
    fxIntensity: Float = 1f,
    onSelectTextOverlay: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onSelectSticker: (String?) -> Unit = {},
    onOpenFilter: () -> Unit = {},
    onOpenFxPanel: () -> Unit = {},
    onOpenAdjust: () -> Unit = {},
    onClearFx: () -> Unit = {},
    totalDurationMs: Long,
    playheadMs: Long,
    timelineZoom: Float = 1.0f,
    selectedClipId: String? = null,
    selectedAudioTrackId: String? = null,
    onSelectAudioTrack: (String) -> Unit = {},
    // Source trim bounds and project placement are independent audio values.
    onMoveAudioTrack: (trackId: String, startTimeMs: Long) -> Unit = { _, _ -> },
    onTrimAudioTrack: (trackId: String, trimStartMs: Long, trimEndMs: Long, startTimeMs: Long) -> Unit = { _, _, _, _ -> },
    isPlaying: Boolean = false,
    audioWaveform: FloatArray = FloatArray(0),
    beatMarkersMs: List<Long> = emptyList(),
    snapToBeat: Boolean = false,
    // Lazy filmstrip frames: (clipId, clip-relative ms) -> bitmap.
    // Only the visible window is ever held (see TimelineTrackArea);
    // lanes look frames up by exact key with a nearest-earlier fallback.
    filmstripFrames: Map<Pair<String, Long>, Bitmap> = emptyMap(),
    // Ms between filmstrip cells at the current zoom (one frame per
    // 64dp of clip width). Lanes and the extractor share this value.
    frameIntervalMs: Long = 1000L,
    onScrub: (Long) -> Unit = {},
    onSelectClip: (String) -> Unit = {},
    // Tapping empty timeline space (no clip/track consumed the tap)
    // clears the selection (hides the clip action bar).
    onDeselect: () -> Unit = {},
    onReorderClips: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onAddMedia: (ClipType) -> Unit = {},
    onSplitClip: (clipId: String, atMs: Long) -> Unit = { _, _ -> },
    onDuplicateClip: (clipId: String) -> Unit = {},
    onDeleteClip: (clipId: String) -> Unit = {},
    onZoomChange: (Float) -> Unit = {},
    transitions: List<ClipTransition> = emptyList(),
    onOpenTransition: (fromClipId: String, toClipId: String) -> Unit = { _, _ -> },
    // Long-press menu: clip-level Speed and Trim (P2/P3).
    onOpenSpeed: () -> Unit,
    onOpenTrim: () -> Unit,
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
    onToggleStickerKeyframeAtPlayhead: (stickerId: String) -> Unit = {},
    onMoveStickerKeyframe: (stickerId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _ -> },
    onToggleTextMaskKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onMoveTextMaskKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _, _ -> },
    onSetTextOverlayRange: ((clipId: String, overlayId: String, startMs: Long, endMs: Long) -> Unit)? = null,
    // Multi-layer video tracks (CapCut-style, up to 10). Layer 0 = main (V1),
    // layers 1..9 = overlay layers (V2..V10) composited PiP above the main.
    hiddenVideoLayers: Set<Int> = emptySet(),
    lockedVideoLayers: Set<Int> = emptySet(),
    /**
     * 60fps-safe playhead stream. When provided, the playhead + ruler
     * collect it in isolated composables so playback ticks do NOT
     * recompose the whole timeline. Falls back to [playheadMs] when null.
     */
    playerPositionFlow: kotlinx.coroutines.flow.StateFlow<Long>? = null,
    /**
     * Hoisted timeline scroll state. Owned by the caller (the editor
     * screen), NOT by this composable's local remember — so opening or
     * closing any bottom sheet (or any timeline recomposition) can never
     * reset the scroll to 00:00 and strand the playhead off-screen.
     * Defaults keep standalone callers working.
     */
    scrollState: ScrollState = rememberScrollState(),
    /** Hoisted vertical scroll state (same ownership as [scrollState]). */
    vScrollState: ScrollState = rememberScrollState(),
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val safeDurationMs = totalDurationMs.coerceAtLeast(1000L)
    // Build the per-layer and text-owner indexes once per project update.
    // Previously each overlay row filtered every clip, and each text item
    // searched every clip's text list during composition (O(layers × clips)
    // plus O(text × clips × text-per-clip) on project open).
    val layerContentIndex = remember(clips) { TimelineLayerContentIndex.from(clips) }
    val logicalTextLayers = remember(clips, textOverlays, layerContentIndex) {
        mapProjectTextLayers(clips, textOverlays, layerContentIndex.textOwnerByOverlayId)
    }
    val textLaneRows = remember(logicalTextLayers) {
        timelineLaneRowIndices(logicalTextLayers.map { TimelineLaneSpan(it.startMs, it.endMs) })
    }
    val stickerLaneRows = remember(stickers) {
        timelineLaneRowIndices(stickers.map { sticker ->
            val start = sticker.startMs.coerceAtLeast(0L)
            TimelineLaneSpan(start, if (sticker.endMs == Long.MAX_VALUE) start + 3000L else sticker.endMs)
        })
    }
    // 60fps playhead isolation: the raw flow drives ONLY the playhead overlay
    // (tiny composable). Everything else uses a 10Hz-sampled snapshot so
    // playback ticks don't recompose tracks/clips/thumbnails every frame.
    var uiPlayheadMs by remember { mutableStateOf(playheadMs.coerceIn(0L, safeDurationMs)) }
    // The duration MUST be a key: this collector captures safeDurationMs
    // from the composition that started it. Without the key it kept the
    // FIRST composition's duration (empty project -> ~1000ms) forever and
    // clamped the playhead to ~00:01 after a video was added — the ruler
    // and auto-scroll froze near 00:00 while the preview timecode (which
    // reads state directly) kept advancing.
    LaunchedEffect(playerPositionFlow, safeDurationMs) {
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
    val headerKeyframeClip = rememberTimelineKeyframeClip(
        clips, selectedClipId, selectedTextOverlayId, selectedStickerId,
        selectedAudioTrackId, hiddenVideoLayers, safePlayheadMs, safeDurationMs,
    )
    // Playhead provider: reads the 10Hz-sampled position at CALL time
    // (drag end / draw phase), never during composition. Rows receive
    // this stable lambda instead of a Long, so playhead ticks never
    // recompose clip rows / waveform bars — they only re-read on demand.
    // The lambda is remembered once and reads the State object, so its
    // identity never changes across recompositions (rows stay skipped).
    val playheadMsProvider = remember { { uiPlayheadMs } }
    // Snap edges (every clip start/end across all video tracks) —
    // hoisted + remembered: the old inline flatMap at the row call site
    // rebuilt this list on every timeline recomposition (10x/s during
    // playback) for every overlay row.
    val allSnapEdges = remember(clips) {
        clips.flatMap { listOf(it.timelineOffsetMs, it.timelineOffsetMs + it.effectiveDurationMs()) }
    }
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

    // CapCut-style FIXED-CENTRE playhead: while playing, the timeline
    // scrolls underneath a playhead pinned at the viewport centre.
    // The RAW position flow is collected (not the 10Hz snapshot) so
    // the scroll moves at the same rate the playhead overlay draws —
    // a 10Hz scroll would let the playhead wobble around centre at
    // high zoom. Instant scrollTo (no animation): animateScrollTo on
    // every update caused visible stutter during playback.
    LaunchedEffect(isPlaying, playerPositionFlow) {
        if (!isPlaying) return@LaunchedEffect
        val flow = playerPositionFlow ?: return@LaunchedEffect
        flow.collect { posMs ->
            val viewportWidth = scrollState.viewportSize
            if (viewportWidth <= 0) return@collect
            val playheadPx = with(density) {
                (posMs.coerceIn(0L, safeDurationMs) * msToDp).dp.toPx()
            }.roundToInt()
            scrollState.scrollTo((playheadPx - viewportWidth / 2).coerceAtLeast(0))
        }
    }
    // Snapshot-driven centre: no-flow fallback while playing (centre on
    // every snapshot tick), and — while PAUSED — centre only if the
    // playhead is off-screen (a seek/jump). Never while the user drags
    // the ruler: the playhead stays in-viewport under the finger there,
    // so this never fights an active scrub.
    LaunchedEffect(safePlayheadMs) {
        val viewportWidth = scrollState.viewportSize
        if (viewportWidth <= 0) return@LaunchedEffect
        val playheadPx = with(density) { (safePlayheadMs * msToDp).dp.toPx() }.roundToInt()
        val offScreen = playheadPx < scrollState.value ||
            playheadPx > scrollState.value + viewportWidth
        if (isPlaying) {
            if (playerPositionFlow == null) {
                scrollState.scrollTo((playheadPx - viewportWidth / 2).coerceAtLeast(0))
            }
        } else if (offScreen) {
            scrollState.scrollTo((playheadPx - viewportWidth / 2).coerceAtLeast(0))
        }
    }

    // Restore: when the timeline (re)lays out while playing — e.g. right
    // after a bottom sheet closed and the scroll position was reset by a
    // recomposition — re-center on the playhead the moment the viewport
    // is measured. Keyed on viewportSize so it fires on first layout
    // (0 -> measured) and on rotation. Gated on isPlaying so a paused
    // user's manual scroll is never yanked back to the playhead.
    LaunchedEffect(scrollState.viewportSize) {
        if (isPlaying && scrollState.viewportSize > 0) {
            val playheadX = safePlayheadMs * msToDp
            val playheadPx = with(density) { playheadX.dp.toPx() }.roundToInt()
            val viewportWidth = scrollState.viewportSize
            if (playheadPx > scrollState.value + viewportWidth || playheadPx < scrollState.value) {
                val targetScroll = (playheadPx - viewportWidth / 2).coerceAtLeast(0)
                scrollState.scrollTo(targetScroll)
            }
        }
    }

    // Reveal: when the selection moves to an OVERLAY clip whose chip is
    // off-screen, scroll the timeline so the chip is visible. An overlay
    // added at the playhead sits at timelineOffsetMs — if the viewport
    // is elsewhere, its OVL row looks EMPTY and the overlay cannot be
    // tapped/edited, which reads as "overlay missing from the timeline"
    // even though the clip exists. Overlay-only: V1 chips are laid out
    // cumulatively, not by timelineOffsetMs, so their reveal needs
    // different math (the centre-playhead logic already covers V1).
    LaunchedEffect(selectedClipId) {
        val clip = clips.firstOrNull { it.id == selectedClipId } ?: return@LaunchedEffect
        if (clip.type != ClipType.OVERLAY) return@LaunchedEffect
        val viewportWidth = scrollState.viewportSize
        if (viewportWidth <= 0) return@LaunchedEffect
        val clipStartPx = with(density) { (clip.timelineOffsetMs * msToDp).dp.toPx() }.roundToInt()
        val clipEndPx = with(density) {
            ((clip.timelineOffsetMs + clip.effectiveDurationMs()) * msToDp).dp.toPx()
        }.roundToInt()
        val scroll = scrollState.value
        if (clipStartPx < scroll || clipEndPx > scroll + viewportWidth) {
            scrollState.scrollTo((clipStartPx - 32).coerceAtLeast(0))
        }
    }

    // Reveal (new-clip appearance): an overlay clip that APPEARS in the
    // project without a selection change (undo/redo restoring it, a
    // paste, a project sync) must also scroll into view — otherwise
    // its OVL row looks empty even though the clip exists. A batch
    // paste reveals the batch's full extent, not just its first
    // clip. Gated on the previously-seen set being non-empty so the
    // initial project load never yanks the scroll: opening a project
    // shows 00:00, not a mid-timeline overlay.
    var seenOverlayIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(clips) {
        val previousIds = seenOverlayIds
        val currentIds = clips.filter { it.type == ClipType.OVERLAY }.map { it.id }.toSet()
        seenOverlayIds = currentIds
        // Only reveal when a clip appeared mid-session (previous set
        // non-empty): the initial project load must never yank the
        // scroll to a mid-timeline overlay.
        if (previousIds.isEmpty()) return@LaunchedEffect
        val appeared = currentIds - previousIds
        if (appeared.isEmpty()) return@LaunchedEffect
        val appearedClips = clips.filter { it.id in appeared }
        if (appearedClips.isEmpty()) return@LaunchedEffect
        val viewportWidth = scrollState.viewportSize
        if (viewportWidth <= 0) return@LaunchedEffect
        // Batch reveal: a multi-clip paste can drop several overlays
        // at once — revealing only the first leaves the rest
        // off-screen. Scroll so the batch's full extent is visible:
        // if the batch starts left of the viewport (or is wider
        // than the viewport), show its start; if the start is
        // already visible and the whole batch fits, scroll minimally
        // to reveal its end.
        fun clipStartPx(clip: MediaClip) = with(density) {
            (clip.timelineOffsetMs * msToDp).dp.toPx()
        }.roundToInt()
        fun clipEndPx(clip: MediaClip) = with(density) {
            ((clip.timelineOffsetMs + clip.effectiveDurationMs()) * msToDp).dp.toPx()
        }.roundToInt()
        val scroll = scrollState.value
        if (appearedClips.size == 1) {
            // Single clip: unchanged behaviour — scroll so its start
            // is visible whenever any part of it is off-screen.
            val clip = appearedClips[0]
            val clipStartPx = clipStartPx(clip)
            val clipEndPx = clipEndPx(clip)
            if (clipStartPx < scroll || clipEndPx > scroll + viewportWidth) {
                scrollState.scrollTo((clipStartPx - 32).coerceAtLeast(0))
            }
            return@LaunchedEffect
        }
        val batchStartPx = appearedClips.minOf { clipStartPx(it) }
        val batchEndPx = appearedClips.maxOf { clipEndPx(it) }
        if (batchStartPx < scroll || batchEndPx > scroll + viewportWidth) {
            if (batchStartPx < scroll || batchEndPx - batchStartPx > viewportWidth) {
                scrollState.scrollTo((batchStartPx - 32).coerceAtLeast(0))
            } else {
                scrollState.scrollTo((batchEndPx - viewportWidth + 32).coerceAtLeast(0))
            }
        }
    }

    // Drag-and-drop state for video clips
    // V1 lane: main video clips + photo (IMAGE) clips on layer 0 share the
    // filmstrip lane (photo clips previously rendered nowhere -> empty V1
    // track). Overlay layers (1..9) render in their own rows below.
    val videoClips = remember(layerContentIndex) {
        layerContentIndex.videoByLayer[0].orEmpty().filter { it.type != ClipType.OVERLAY }
    }
    // Multi-layer video tracks: distinct trackIndex values across all
    // video clips (0..9). A layer appears ONLY when it holds clips —
    // empty tracks are never shown (no "+ Add overlay (OVLx)" / "+ OVL2"
    // placeholder rows; a track is created automatically when the user
    // adds an overlay from the bottom bar). Unlike the old editor, an empty
    // project does not reserve a black V1 lane: hiding that presentation
    // must not rewrite clips, offsets, or intentional timeline gaps.
    val videoLayers = remember(layerContentIndex) {
        layerContentIndex.videoByLayer.keys.sorted().take(10)
    }
    val hasMainVideo = videoClips.isNotEmpty()
    // Overlay row: readable 42dp filmstrip + 10dp keyframes + 8dp padding.
    val overlayLayerHeight = OVERLAY_LAYER_HEIGHT_DP.dp
    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragAccumulatedOffsetPx by remember { mutableFloatStateOf(0f) }
    var targetDropIndex by remember { mutableIntStateOf(-1) }

    // Measured clip bounds in horizontal track space (index -> Pair(leftPx, widthPx))
    val clipLayoutBounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }

    // Tracks Mute / Active states
    var isAudioMuted by remember { mutableStateOf(false) }
    // (Per-layer visibility replaced the old single overlay toggle.)

    // Visible-window (content px) for filmstrip cell culling: lanes
    // compose ONLY cells intersecting [scroll - buffer, scroll +
    // viewport + buffer], so a 10-minute clip at 10x zoom never
    // composes ~5000 cells. Sampled (~12/s) — a full viewport of
    // buffer on each side covers the lag during flings. The initial
    // window is generous so cells render before the first scroll
    // emission.
    var visibleWindowPx by remember { mutableStateOf(0 to 8192) }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value to scrollState.viewportSize }
            .sample(80)
            .collect { (scroll, viewport) ->
                val vp = if (viewport > 0) viewport else 8192
                visibleWindowPx = (scroll - vp).coerceAtLeast(0) to (scroll + viewport + vp)
            }
    }

    // Multi-select state (pro timeline): select several clips, then move/delete together.
    var multiSelectMode by remember { mutableStateOf(false) }
    var multiSelectedIds by remember { mutableStateOf(setOf<String>()) }
    var timelineOptionsExpanded by remember { mutableStateOf(false) }

    // Snap indicator: true when the playhead sits on (or within threshold of) a beat marker
    // while snap-to-beat is enabled — shown as a badge on the playhead.
    val snapThresholdMs = 200L
    val isPlayheadSnapped = snapToBeat &&
        beatMarkersMs.any { kotlin.math.abs(it - safePlayheadMs) <= snapThresholdMs }

    // Shared geometry for the ruler and scrollable canvas. A1 grows with
    // audio track count so every populated row remains tappable.
    // A1 grows with the audio track count (34dp per extra row, capped) so
    // every track stays visible and tappable — never collapsed or hidden.
    val rulerHeight = 36.dp
    // A1/A2 audio lanes (mockup): A1 = music, A2 = voice. Each lane grows
    // 46dp per extra audio row so every track stays visible and tappable —
    // never collapsed or hidden; the outer vertical scroll handles overflow.
    // A1 = music, A2 = voice/SFX — keyed off the persisted AudioTrack.kind
    // (Kilo: the old volume>=0.99f threshold misfiled SFX into the voice lane
    // and broke on any track at exactly the boundary).
    val musicTracks = remember(audioTracks) { audioTracks.filter { it.kind == AudioTrack.Kind.MUSIC } }
    val voiceTracks = remember(audioTracks) { audioTracks.filter { it.kind != AudioTrack.Kind.MUSIC } }
    // Lane heights grow 46dp per extra row (one AudioTrackLaneRow each) so
    // every track stays visible; the outer vertical scroll handles overflow.
    val a1Height = (musicTracks.size * AUDIO_LANE_ROW_HEIGHT_DP).dp
    val a2Height = (voiceTracks.size * AUDIO_LANE_ROW_HEIGHT_DP).dp
    // Supporting rows use generous touch-friendly heights beneath the dominant V1 filmstrip.
    val textLaneHeight = timelineLaneRowCount(textLaneRows) * 40
    val stickerLaneHeight = timelineLaneRowCount(stickerLaneRows) * 40
    val fxLaneHeight = 40
    // Same "something applied" test the FX lane itself uses for
    // its segments — the lane renders only when this is true.
    val hasFx = !activeFilterId.isNullOrBlank() || !activeFxId.isNullOrBlank() ||
        adjustmentsSummary(adjustments).isNotEmpty()
    val showWaveformStrip = audioWaveform.isNotEmpty() && videoClips.isNotEmpty()
    val waveformStripHeight = 12
    val overlayRowsTotal = videoLayers.filter { it in 1..9 }
        .sumOf { overlayLayerHeight.value.toInt() + 10 }
    val textLaneTotal = if (textOverlays.isNotEmpty()) textLaneHeight + 10 else 0
    val stickerLaneTotal = if (stickers.isNotEmpty()) stickerLaneHeight + 10 else 0
    val fxLaneTotal = if (hasFx) fxLaneHeight + 10 else 0
    val a1Total = if (musicTracks.isNotEmpty()) a1Height.value.toInt() + 10 else 0
    val a2Total = if (voiceTracks.isNotEmpty()) a2Height.value.toInt() + 10 else 0
    val v1Height = if (hasMainVideo) 64.dp else 0.dp
    // Keep the ruler, breathing room, and dominant filmstrip together. The actual source waveform is
    // separated slightly below V1; supporting rows scroll beneath this band.
    val fixedHeaderHeight = rulerHeight.value.toInt() +
        (if (hasMainVideo) 10 + v1Height.value.toInt() else 0) +
        (if (showWaveformStrip) 10 + waveformStripHeight else 0)
    val supportRowsTotal = overlayRowsTotal + textLaneTotal + stickerLaneTotal +
        fxLaneTotal + a1Total + a2Total
    val timelineTotalDp = fixedHeaderHeight +
        (if (supportRowsTotal > 0) 10 else 0) +
        supportRowsTotal
    val scrollableTotalDp = timelineTotalDp - fixedHeaderHeight

    // No left sidebar: the timeline canvas uses the FULL screen
    // width. The old sidebar pills (V1 / OVL1 / FX / A1 / A2 +
    // buttons) are gone; hide/lock/mute now live in the contextual
    // bar of the selected clip/track. Pure-black viewport below the
    // restored title/action header, with no identity gutter.
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().fillMaxHeight()
            .background(Color.Black)
            .border(1.dp, Color(0xFF141414))
    ) {
        // In compact/landscape layouts let ALL rows scroll vertically,
        // rather than letting a pinned V1 consume the whole track viewport.
        val compactHeight = maxHeight < 240.dp
        Column(Modifier.fillMaxSize()) {
        TimelineHeader(
            durationMs = safeDurationMs,
            headerKeyframeClip = headerKeyframeClip,
            playheadMs = safePlayheadMs,
            onToggleKeyframe = onToggleKeyframeAtPlayhead,
            onRevealPlayhead = {
                coroutineScope.launch {
                    val positionPx = with(density) { (playheadMsProvider() * msToDp).dp.toPx() }
                    scrollState.scrollTo((positionPx - scrollState.viewportSize / 2f).roundToInt().coerceAtLeast(0))
                }
            },
            multiSelectMode = multiSelectMode,
            onToggleMultiSelect = {
                multiSelectMode = !multiSelectMode
                if (!multiSelectMode) multiSelectedIds = emptySet()
            },
            onAddMedia = { onAddMedia(ClipType.VIDEO) },
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
        // --- TIMELINE VIEWPORT WITH PINCH-TO-ZOOM GESTURE DETECTOR ---
        Box(
            modifier = Modifier
                .fillMaxSize().clipToBounds()
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
                // Tapping empty timeline space (no clip/track consumed the
                // tap) clears the selection. Child clickables (clips,
                // ruler scrub, HUD, multi-select) win over this parent
                // clickable, so a tap on a clip never deselects.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDeselect() }
        ) {
            // Draw only visible grid lines. Scroll is read during drawing, not
            // composition, so panning does not rebuild every thumbnail row.
            Canvas(Modifier.fillMaxSize()) {
                val step = secondWidthDp.toPx()
                val offset = scrollState.value.toFloat() % step
                var x = -offset
                while (x <= size.width) {
                    drawLine(Color(0xFF1A2639), Offset(x, rulerHeight.toPx()), Offset(x, size.height), 1.dp.toPx())
                    x += step
                }
            }
            Column(modifier = Modifier.fillMaxSize().then(
                if (compactHeight) Modifier.verticalScroll(vScrollState) else Modifier
            )) {
                // PINNED HEADER: ruler + (when populated) V1 stay fixed while the other
                // tracks scroll vertically. Horizontally synced with the
                // scrolling content via translationX (no recomposition
                // per scroll frame); clipped so it never draws over the
                // scrolling rows. The header also accepts horizontal
                // swipes (scrollable on the shared scrollState, same
                // direction as horizontalScroll) — without it a swipe
                // starting on the ruler/V1 would do nothing, since only
                // the lower box scrolls. Child gestures (ruler scrub,
                // clip drag) consume first, so they still win.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(fixedHeaderHeight.dp)
                        .clipToBounds()
                        .scrollable(
                            state = scrollState,
                            orientation = Orientation.Horizontal,
                            reverseDirection = true
                        )
                ) {
                    Box(
                        modifier = Modifier
                            // The pinned header is translated, not horizontally
                            // scrolled. Release its viewport constraint BEFORE
                            // measuring the full-duration ruler and V1 row.
                            .wrapContentWidth(Alignment.Start, unbounded = true)
                            .width(timelineWidthDp)
                            .height(fixedHeaderHeight.dp)
                            .graphicsLayer { translationX = -scrollState.value.toFloat() }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
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
                                    .testTag("timeline-ruler")
                            )

                            if (hasMainVideo) Spacer(Modifier.height(10.dp))

                            // TRACK 1: V1 (Main Video + Photo Filmstrip Track,
                            // layer 0 only). Keep its timing model untouched;
                            // simply don't consume screen space when it has
                            // no clips, so overlay/audio-only workspaces use
                            // the freed height for their real layers.
                            if (hasMainVideo) TimelineVideoTrack(
                                videoClips = videoClips,
                                selectedClipId = selectedClipId,
                                msToDp = msToDp,
                                filmstripFrames = filmstripFrames,
                                frameIntervalMs = frameIntervalMs,
                                visibleWindowPx = visibleWindowPx,
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
                                onSplitClip = onSplitClip,
                                onDuplicateClip = onDuplicateClip,
                                onDeleteClip = onDeleteClip,
                                onOpenSpeed = onOpenSpeed,
                                onOpenTrim = onOpenTrim,
                                playheadMsProvider = playheadMsProvider,
                                onMoveKeyframe = onMoveKeyframe,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(v1Height)
                                    // This boundary keeps lane bounds separate from the full-height
                                    // timeline deselect target.
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "Main video track"
                                    }
                            )

                            // Thin audio waveform strip directly under
                            // V1 (first clip's audio only — that is what
                            // state.audioWaveform holds). 12dp, pinned
                            // with the header, spanning exactly the
                            // first clip's duration. Multi-clip projects
                            // get the strip under the first clip only.
                            if (showWaveformStrip) {
                                Spacer(Modifier.height(10.dp))
                                val firstClip = videoClips.first()
                                val stripWidthDp =
                                    (firstClip.effectiveDurationMs() * msToDp).dp
                                Box(
                                modifier = Modifier
                                        .width(stripWidthDp)
                                        .height(waveformStripHeight.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF073A42))
                                        .semantics(mergeDescendants = true) { contentDescription = "Source audio waveform" }
                                ) {
                                    AudioWaveformBars(
                                        samples = audioWaveform,
                                        durationMs = firstClip.effectiveDurationMs(),
                                        playheadMsProvider = playheadMsProvider,
                                        beatMarkersMs = beatMarkersMs,
                                        isMuted = isAudioMuted,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // SCROLLABLE TRACKS: overlay layers + TXT/STK/FX/A1/A2.
                // A lane renders ONLY when it holds something — empty
                // tracks are never shown.
                Box(
                    modifier = Modifier
                        .then(if (compactHeight) Modifier.height(scrollableTotalDp.dp) else Modifier.weight(1f))
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .then(if (compactHeight) Modifier else Modifier.verticalScroll(vScrollState))
                ) {
                    Box(
                        modifier = Modifier
                            .width(timelineWidthDp)
                            .height(scrollableTotalDp.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                // 4dp gap between the pinned V1 row and
                                // the first scrolling row (was the
                                // Column's spacedBy before the split).
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            // TRACKS 2..N: Overlay video layers V2..V10 (CapCut-style).
                            // Only layers holding clips render — videoLayers
                            // already excludes empty layers. Each layer gets
                            // its own PiP row, absolutely positioned by
                            // timelineOffsetMs. Hide/lock live in the
                            // contextual bar of the selected clip.
                            videoLayers.filter { it in 1..9 }.forEach { layer ->
                                val layerClips = layerContentIndex.videoByLayer[layer].orEmpty()
                                TimelineVideoLayerRow(
                                    layerIndex = layer,
                                    clips = layerClips,
                                    filmstripFrames = filmstripFrames,
                                    frameIntervalMs = frameIntervalMs,
                                    visibleWindowPx = visibleWindowPx,
                                    selectedClipId = selectedClipId,
                                    msToDp = msToDp,
                                    isHidden = layer in hiddenVideoLayers,
                                    isLocked = layer in lockedVideoLayers,
                                    onSelectClip = onSelectClip,
                                    onMoveClipOffset = onMoveClipOffset,
                                    onDeleteClip = onDeleteClip,
                                    onSeekToKeyframe = onScrub,
                                    onMoveKeyframe = onMoveKeyframe,
                                    onToggleKeyframeAtPlayhead = onToggleKeyframeAtPlayhead,
                                    // Provider, not the ticking Long: rows
                                    // read the playhead only at drag end
                                    // (snap), so ticks never recompose them.
                                    playheadMsProvider = playheadMsProvider,
                                    // Snap edges: every clip's start/end
                                    // across all video tracks (remembered
                                    // above, not rebuilt per recomposition).
                                    snapEdges = allSnapEdges,
                                    onTrimClip = onTrimClip,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(overlayLayerHeight)
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = "V${layer + 1} overlay"
                                        }
                                )
                            }

                            // TEXT lane — only when text exists. No "+ Add
                            // text" placeholder: text is added from the
                            // bottom bar, which creates this lane.
                            if (textOverlays.isNotEmpty()) {
                                TimelineTextLane(
                                    textLayers = logicalTextLayers,
                                    rowIndices = textLaneRows,
                                    msToDp = msToDp,
                                    timelineDurationMs = totalDurationMs,
                                    selectedTextOverlayId = selectedTextOverlayId,
                                    onSelectTextOverlay = onSelectTextOverlay,
                                    onSeekToKeyframe = onScrub,
                                    onMoveTextKeyframe = onMoveTextKeyframe,
                                    onToggleTextKeyframeAtPlayhead = onToggleTextKeyframeAtPlayhead,
                                    onMoveTextMaskKeyframe = onMoveTextMaskKeyframe,
                                    onToggleTextMaskKeyframeAtPlayhead = onToggleTextMaskKeyframeAtPlayhead,
                                    onSetTextOverlayRange = onSetTextOverlayRange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(textLaneHeight.dp)
                                        .semantics(mergeDescendants = true) { contentDescription = "Text" }
                                )
                            }

                            // STICKER lane — only when stickers exist. No
                            // "+ Add sticker" placeholder: stickers are
                            // added from the bottom bar.
                            if (stickers.isNotEmpty()) {
                                TimelineStickerLane(
                                    stickers = stickers,
                                    rowIndices = stickerLaneRows,
                                    msToDp = msToDp,
                                    selectedStickerId = selectedStickerId,
                                    onSelectSticker = onSelectSticker,
                                    onSeekToKeyframe = onScrub,
                                    onMoveStickerKeyframe = onMoveStickerKeyframe,
                                    onToggleStickerKeyframeAtPlayhead = onToggleStickerKeyframeAtPlayhead,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(stickerLaneHeight.dp)
                                        .semantics(mergeDescendants = true) { contentDescription = "Stickers" }
                                )
                            }

                            // TRACK 3: FX lane — only when a filter, FX or
                            // adjustment is applied. No "+ Add FX"
                            // placeholder: FX is added from the bottom
                            // bar, which creates this lane.
                            if (hasFx) {
                                TimelineFxTrack(
                                    activeFxId = activeFxId,
                                    activeFilterId = activeFilterId,
                                    filterIntensity = filterIntensity,
                                    adjustments = adjustments,
                                    fxIntensity = fxIntensity,
                                    durationMs = safeDurationMs,
                                    msToDp = msToDp,
                                    fxKeyframes = videoClips.firstOrNull { it.id == selectedClipId }?.keyframes?.keyframes
                                        ?: emptyList(),
                                    onSeekToKeyframe = onScrub,
                                    onToggleKeyframeAtPlayhead = {
                                        selectedClipId?.let { onToggleKeyframeAtPlayhead(it) }
                                    },
                                    onOpenFilter = onOpenFilter,
                                    onOpenFxPanel = onOpenFxPanel,
                                    onOpenAdjust = onOpenAdjust,
                                    onClearFx = onClearFx,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(fxLaneHeight.dp)
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = "Effects and filters"
                                        }
                                )
                            }

                            // TRACK 4: A1 (music) + TRACK 5: A2 (voice).
                            // Each renders only when it holds tracks.
                            if (musicTracks.isNotEmpty()) {
                                TimelineAudioTrack(
                                    audioTracks = musicTracks,
                                    durationMs = safeDurationMs,
                                    msToDp = msToDp,
                                    isMuted = isAudioMuted,
                                    beatMarkersMs = beatMarkersMs,
                                    snapToBeat = snapToBeat,
                                    playheadMsProvider = playheadMsProvider,
                                    selectedAudioTrackId = selectedAudioTrackId,
                                    onSelectAudioTrack = onSelectAudioTrack,
                                    onMoveAudioTrack = onMoveAudioTrack,
                                    onTrimAudioTrack = onTrimAudioTrack,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(a1Height)
                                        .semantics(mergeDescendants = true) { contentDescription = "Music" }
                                )
                            }
                            if (voiceTracks.isNotEmpty()) {
                                TimelineAudioTrack(
                                    audioTracks = voiceTracks,
                                    durationMs = safeDurationMs,
                                    msToDp = msToDp,
                                    isMuted = isAudioMuted,
                                    beatMarkersMs = beatMarkersMs,
                                    snapToBeat = snapToBeat,
                                    playheadMsProvider = playheadMsProvider,
                                    selectedAudioTrackId = selectedAudioTrackId,
                                    onSelectAudioTrack = onSelectAudioTrack,
                                    onMoveAudioTrack = onMoveAudioTrack,
                                    onTrimAudioTrack = onTrimAudioTrack,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(a2Height)
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = "Voice and sound effects"
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // --- 3. UNIFIED VERTICAL PLAYHEAD LINE & TIMESTAMP BADGE ---
            // At canvas-root level (not inside the scrolled content) so it
            // spans the full visible height — pinned header included — and
            // its handle never scrolls off-screen. The horizontal scroll
            // offset is subtracted inside the overlay. Clipped so a
            // left-scrolled playhead never draws over the sidebar.
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                TimelinePlayheadOverlay(
                    playerPositionFlow = playerPositionFlow,
                    fallbackMs = safePlayheadMs,
                    durationMs = safeDurationMs,
                    msToDp = msToDp,
                    isSnapped = isPlayheadSnapped,
                    // Provider, not a snapshot read: reading
                    // scrollState.value here would recompose the whole
                    // timeline on every scroll frame.
                    scrollOffsetPx = { scrollState.value.toFloat() }
                )
            }
            // NOTE: the canvas Box stays open — the Zoom HUD + multi-select
            // overlays below are its children (they use BoxScope align).

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

        // Multi-select action bar: bulk delete + done.
        if (multiSelectMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .zIndex(30f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFFFFFFF).copy(alpha = 0.95f))
                    .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${multiSelectedIds.size} selected",
                    color = Color(0xFF111827),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (multiSelectedIds.isNotEmpty()) {
                    Text(
                        text = "Delete",
                        color = Color(0xFFB42318),
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
                    color = Color(0xFF8A5A00),
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
            // Keep the primary add action and the editing utilities pinned to
            // the ruler. The filmstrip can be panned far beyond its final clip,
            // so an end-of-strip-only add button is otherwise unreachable.
            Row(
                // Do not clip this 20dp visual row: its children retain Android
                // minimum touch targets outside the chrome, above the ruler.
                modifier = Modifier.align(Alignment.TopEnd).zIndex(500f)
                    .background(Color(0xFF10182A), RoundedCornerShape(bottomStart = 7.dp))
                    .border(1.dp, Color(0xFF202A3D), RoundedCornerShape(bottomStart = 7.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(28.dp, rulerHeight)
                        .clickable { onAddMedia(ClipType.VIDEO) }
                        .testTag("timeline-add-media"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add video media",
                        tint = ApexPalette.NeonCyan, modifier = Modifier.size(17.dp))
                }
                Box(
                    modifier = Modifier.size(28.dp, rulerHeight)
                        .clickable { timelineOptionsExpanded = !timelineOptionsExpanded }
                        .testTag("timeline-overflow"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Timeline options",
                        tint = ApexPalette.NeonCyan, modifier = Modifier.size(18.dp))
                }
            }
            // Render the compact menu in the timeline's own overlay rather
            // than a Popup: the latter was clipped/omitted by the nested
            // horizontally-scrolling viewport on device-sized hosts.
            if (timelineOptionsExpanded) {
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = rulerHeight)
                        .zIndex(501f).widthIn(min = 176.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10182A))
                        .border(1.dp, Color(0xFF26364D), RoundedCornerShape(8.dp))
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            timelineOptionsExpanded = false
                            coroutineScope.launch {
                                val positionPx = with(density) { (playheadMsProvider() * msToDp).dp.toPx() }
                                scrollState.scrollTo((positionPx - scrollState.viewportSize / 2f).roundToInt().coerceAtLeast(0))
                            }
                        }.testTag("timeline-reveal-playhead").padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { Text("Reveal playhead", color = Color(0xFFE4EDF7), fontSize = 12.sp) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            timelineOptionsExpanded = false
                            multiSelectMode = !multiSelectMode
                            if (!multiSelectMode) multiSelectedIds = emptySet()
                        }.testTag("timeline-multiselect").padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { Text(if (multiSelectMode) "Exit multi-select" else "Select multiple clips", color = Color(0xFFE4EDF7), fontSize = 12.sp) }
                }
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
    // Tapping empty timeline space clears the selection (hides the clip
    // action bar). No-op default keeps standalone callers working.
    onDeselect: () -> Unit = {},
    onReorderClips: (fromIndex: Int, toIndex: Int) -> Unit,
    onAddMedia: (ClipType) -> Unit,
    onSplitClip: (clipId: String, atMs: Long) -> Unit,
    onDuplicateClip: (clipId: String) -> Unit,
    onDeleteClip: (clipId: String) -> Unit,
    onZoomChange: (Float) -> Unit,
    filmstripFrames: Map<Pair<String, Long>, Bitmap> = emptyMap(),
    frameIntervalMs: Long = 1000L,
    transitions: List<ClipTransition> = state.project?.transitions ?: emptyList(),
    onOpenTransition: (fromClipId: String, toClipId: String) -> Unit = { _, _ -> },
    onOpenSpeed: () -> Unit,
    onOpenTrim: () -> Unit,
    modifier: Modifier = Modifier,
    onTrimClip: (clipId: String, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> },
    onMoveClipOffset: (clipId: String, offsetMs: Long) -> Unit = { _, _ -> },
    onDeleteClips: (Set<String>) -> Unit = {},
    onToggleKeyframeAtPlayhead: (clipId: String) -> Unit = {},
    onMoveKeyframe: (clipId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _ -> },
    onToggleTextKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onMoveTextKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _, _ -> },
    onToggleStickerKeyframeAtPlayhead: (stickerId: String) -> Unit = {},
    onMoveStickerKeyframe: (stickerId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _ -> },
    onToggleTextMaskKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onMoveTextMaskKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _, _ -> },
    onSelectAudioTrack: (String) -> Unit = {},
    onMoveAudioTrack: (trackId: String, startTimeMs: Long) -> Unit = { _, _ -> },
    onTrimAudioTrack: (trackId: String, trimStartMs: Long, trimEndMs: Long, startTimeMs: Long) -> Unit = { _, _, _, _ -> },
    // Multi-layer controls (wired by the editor screen).
    hiddenVideoLayers: Set<Int> = state.hiddenVideoLayers,
    lockedVideoLayers: Set<Int> = state.lockedVideoLayers,
    playerPositionFlow: kotlinx.coroutines.flow.StateFlow<Long>? = null,
    activeFilterId: String? = state.activeFilterId,
    filterIntensity: Float = state.filterIntensity,
    adjustments: com.apexstudio.app.domain.model.VideoAdjustments = state.adjustments,
    fxIntensity: Float = state.fxIntensity,
    selectedTextOverlayId: String? = state.selectedTextOverlayId,
    selectedStickerId: String? = state.selectedStickerId,
    onSelectTextOverlay: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onSelectSticker: (String?) -> Unit = {},
    onOpenFilter: () -> Unit = {},
    onOpenFxPanel: () -> Unit = {},
    onOpenAdjust: () -> Unit = {},
    onClearFx: () -> Unit = {},
    // Hoisted scroll state (see the full overload): owned by the caller
    // so sheet open/close can never reset the timeline scroll.
    scrollState: ScrollState = rememberScrollState(),
    vScrollState: ScrollState = rememberScrollState(),
    onSetTextOverlayRange: ((clipId: String, overlayId: String, startMs: Long, endMs: Long) -> Unit)? = null,
) {
    val clips = state.project?.clips ?: emptyList()
    val audioTracks = state.project?.audioTracks ?: emptyList()
    val textOverlays = clips.flatMap { it.textOverlays }
    // Project-level stickers: addStickerOverlay() stores them on
    // Project.stickers (the old clips.flatMap read the legacy per-clip
    // list, so added stickers never appeared as layers).
    val stickers = state.project?.stickers ?: emptyList()

    VideoTimeline(
        clips = clips,
        audioTracks = audioTracks,
        textOverlays = textOverlays,
        stickers = stickers,
        activeFxId = state.activeFxId,
        selectedTextOverlayId = selectedTextOverlayId,
        selectedStickerId = selectedStickerId,
        activeFilterId = activeFilterId,
        filterIntensity = filterIntensity,
        adjustments = adjustments,
        fxIntensity = fxIntensity,
        onSelectTextOverlay = onSelectTextOverlay,
        onSelectSticker = onSelectSticker,
        filmstripFrames = filmstripFrames,
        frameIntervalMs = frameIntervalMs,
        onOpenFilter = onOpenFilter,
        onOpenFxPanel = onOpenFxPanel,
        onOpenAdjust = onOpenAdjust,
        onClearFx = onClearFx,
        totalDurationMs = state.durationMs,
        playheadMs = state.playerPositionMs,
        timelineZoom = state.timelineZoom,
        selectedClipId = state.selectedClipId,
        isPlaying = state.isPlaying,
        audioWaveform = state.audioWaveform,
        beatMarkersMs = state.beatMarkersMs,
        snapToBeat = state.snapToBeat,
        onScrub = onScrub,
        onSelectClip = { onSelectClip(it) },
        onDeselect = onDeselect,
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
        onOpenSpeed = onOpenSpeed,
        onOpenTrim = onOpenTrim,
        onMoveKeyframe = onMoveKeyframe,
        onToggleTextKeyframeAtPlayhead = onToggleTextKeyframeAtPlayhead,
        onMoveTextKeyframe = onMoveTextKeyframe,
        onToggleStickerKeyframeAtPlayhead = onToggleStickerKeyframeAtPlayhead,
        onMoveStickerKeyframe = onMoveStickerKeyframe,
        onToggleTextMaskKeyframeAtPlayhead = onToggleTextMaskKeyframeAtPlayhead,
        onMoveTextMaskKeyframe = onMoveTextMaskKeyframe,
        onSetTextOverlayRange = onSetTextOverlayRange,
        selectedAudioTrackId = state.selectedAudioTrackId,
        onSelectAudioTrack = onSelectAudioTrack,
        onMoveAudioTrack = onMoveAudioTrack,
        onTrimAudioTrack = onTrimAudioTrack,
        hiddenVideoLayers = hiddenVideoLayers,
        lockedVideoLayers = lockedVideoLayers,
        playerPositionFlow = playerPositionFlow,
        scrollState = scrollState,
        vScrollState = vScrollState
    )
}

// ==========================================
// SUB-COMPONENTS
// ==========================================

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
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(Color(0xFF111A2C))
            .border(1.dp, Color(0xFF30405C), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
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
                                color = Color(0xFF475569),
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
                                color = Color(0xFF64748B),
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
                    color = if (secondWidthDp >= 180.dp) ApexPalette.NeonCyan.copy(alpha = 0.6f) else Color(0xFF64748B),
                    start = Offset(halfXPx, h - if (secondWidthDp >= 180.dp) 8f else 6f),
                    end = Offset(halfXPx, h),
                    strokeWidth = if (secondWidthDp >= 180.dp) 1.5f else 1f
                )

                // Full second tick
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = Offset(secXPx, h - 12f),
                    end = Offset(secXPx, h),
                    strokeWidth = 1.5f
                )
            }

            // Neutral baseline keeps timing separate from clip selection accents.
            drawLine(
                color = Color(0xFF31415B),
                start = Offset(0f, h - 0.75f),
                end = Offset(size.width, h - 0.75f),
                strokeWidth = 1.5f
            )

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

        // Time labels: CapCut-style majors every 2s (MM:SS). At
        // frame-level zoom (>= 180dp/s) show 1s majors so the ruler
        // never looks empty.
        val stepSec = if (secondWidthDp >= 180.dp) 1 else 2

        // Time labels: laid out in a Box (NOT a Row) so each label's
        // offset(x) is ABSOLUTE from the ruler origin, matching the
        // Canvas ticks and the playhead. In a Row, offsets are relative
        // to each child's sequential position, so every label's text
        // width accumulated and pushed all later labels right — the
        // ruler read progressively "behind" the playhead (00:20 on the
        // preview vs ~00:16 on the ruler, drift growing with time).
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            for (sec in 0..totalSec step stepSec) {
                val secOffsetDp = (sec * 1000L * msToDp).dp
                Box(
                    modifier = Modifier
                        .offset(x = secOffsetDp)
                        .padding(start = 2.dp, top = 2.dp)
                ) {
                    Text(
                        // CapCut-style: compact MM:SS majors at every step.
                        text = TimeFormat.msToShort(sec * 1000L),
                        color = Color(0xFF9EC9E8),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
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
/**
 * Filmstrip cells for ONE clip: dp-interval frames culled to the
 * visible window.
 *
 * - Clip width is ALWAYS duration x pxPerSecond (set by the caller);
 *   cells never size the clip — a long clip renders its full width
 *   whether or not its frames have arrived yet.
 * - Only cells intersecting [clipStartPx, clipEndPx] ∩ visibleWindow
 *   are composed, so a 10-minute clip at 10x zoom never composes
 *   ~5000 cells (it composes the ~40–60 on screen).
 * - A cell shows its exact frame when present, else the nearest
 *   EARLIER frame (e.g. right after a zoom change, when the new
 *   interval's keys haven't arrived yet), else the placeholder.
 * - Cell positions derive from TIME (cell index x interval x msToDp),
 *   never from accumulated cell widths, so rounding can't drift the
 *   frames away from their true timeline positions.
 */
@Composable
private fun FilmstripCells(
    clipId: String,
    clipStartMs: Long,
    clipDurMs: Long,
    msToDp: Float,
    frameIntervalMs: Long,
    visibleWindowPx: Pair<Int, Int>,
    filmstripFrames: Map<Pair<String, Long>, Bitmap>,
    placeholderIconSize: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val msToPx = msToDp * density.density
    val clipStartPx = clipStartMs * msToPx
    val cellPx = frameIntervalMs * msToPx
    val cellWidthDp = (frameIntervalMs * msToDp).dp
    val cellCount = kotlin.math.ceil(clipDurMs / frameIntervalMs.toFloat())
        .toInt().coerceAtLeast(1)
    // Sorted keys for this clip (nearest-earlier fallback).
    val clipKeys = remember(filmstripFrames, clipId) {
        filmstripFrames.keys.filter { it.first == clipId }.map { it.second }.sorted()
    }
    Box(modifier = modifier) {
        if (cellPx <= 0f) return@Box
        val firstCell = maxOf(
            0,
            kotlin.math.floor((visibleWindowPx.first - clipStartPx) / cellPx).toInt()
        )
        val lastCell = minOf(
            cellCount - 1,
            kotlin.math.ceil((visibleWindowPx.second - clipStartPx) / cellPx).toInt()
        )
        for (cell in firstCell..lastCell) {
            val keyMs = cell * frameIntervalMs
            val frameBmp = filmstripFrames[clipId to keyMs] ?: run {
                val idx = clipKeys.binarySearch(keyMs)
                val earlier = if (idx >= 0) idx - 1 else -idx - 2
                if (earlier >= 0) filmstripFrames[clipId to clipKeys[earlier]] else null
            }
            Box(
                modifier = Modifier
                    .offset(x = (cell * frameIntervalMs * msToDp).dp)
                    .width(cellWidthDp)
                    .fillMaxHeight()
                    .background(Color(0xFF101A2B))
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
                            tint = Color(0xFF62748F),
                            modifier = Modifier.size(placeholderIconSize)
                        )
                    }
                }
            }
        }
    }
}

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
    filmstripFrames: Map<Pair<String, Long>, Bitmap>,
    frameIntervalMs: Long,
    visibleWindowPx: Pair<Int, Int>,
    selectedClipId: String?,
    msToDp: Float,
    isHidden: Boolean,
    isLocked: Boolean,
    onSelectClip: (String) -> Unit,
    onMoveClipOffset: (clipId: String, offsetMs: Long) -> Unit,
    onDeleteClip: (String) -> Unit,
    onSeekToKeyframe: (Long) -> Unit,
    onMoveKeyframe: (clipId: String, keyframeId: String, newTimeMs: Long) -> Unit,
    onToggleKeyframeAtPlayhead: (String) -> Unit,
    modifier: Modifier = Modifier,
    // Playhead provider + every clip edge in the project: drag-to-move
    // snaps the overlay's start/end to the nearest of these (200ms
    // window). The playhead is a PROVIDER (read at drag end only), not
    // a ticking Long — passing a Long recomposed this row on every
    // playhead tick just to recompute a remember nobody reads unless
    // a drag is in flight.
    playheadMsProvider: () -> Long = { 0L },
    snapEdges: List<Long> = emptyList(),
    // Trim commit (called ONCE at drag end — the trim handles keep
    // local state during the drag so a trim never spams undo entries).
    onTrimClip: (clipId: String, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> }
) {
    // Readable historical overlay filmstrip with its own name and keyframe
    // strip. Four dp of vertical inset leaves a 52dp filmstrip inside the
    // allocated 60dp lane.
    // The row renders ONLY when the layer holds clips (the caller
    // never passes an empty layer), so there is no empty branch.
    Column(
        modifier = modifier
            .background(if (isHidden) Color(0xFF111827) else Color(0xFF0A1B2D))
            .padding(vertical = 4.dp)
    ) {
        if (isHidden) {
            Text(
                "Layer OVL$layerIndex hidden",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
            return@Column
        }

        // Filmstrip plus keyframe strip, sharing the lane rail geometry.
        Box(modifier = Modifier.fillMaxWidth().height(OVERLAY_FILMSTRIP_HEIGHT_DP.dp)) {
            clips.forEach { clip ->
                val isSelected = clip.id == selectedClipId
                // Speed-ramped clips occupy their OUTPUT length.
                val clipDur = clip.effectiveDurationMs().coerceAtLeast(500L)
                val clipWidthDp = maxOf((clipDur * msToDp).dp, 56.dp)
                val clipOffsetDp = (clip.timelineOffsetMs * msToDp).dp
                var dragDx by remember(clip.id) { mutableFloatStateOf(0f) }
                var isDragging by remember(clip.id) { mutableStateOf(false) }
                // Trim-handle drag state (pixels, local until commit).
                var trimStartDxPx by remember(clip.id) { mutableFloatStateOf(0f) }
                var trimEndDxPx by remember(clip.id) { mutableFloatStateOf(0f) }
                val density = LocalDensity.current
                // Snap targets are computed at drag END (the only place
                // they're read) from the live playhead — never
                // remembered during composition.
                // Live chip width while a trim handle is dragging.
                val liveChipWidthDp = clipWidthDp -
                    with(density) { trimStartDxPx.toDp() } +
                    with(density) { trimEndDxPx.toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = clipOffsetDp)
                        .width(clipWidthDp)
                        .height(OVERLAY_FILMSTRIP_HEIGHT_DP.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(trimStartDxPx.roundToInt(), 0)
                            }
                            .width(liveChipWidthDp.coerceAtLeast(24.dp))
                            .height(OVERLAY_CLIP_HEIGHT_DP.dp)
                            .testTag("timeline-overlay-clip-${clip.id}")
                            .graphicsLayer { translationX = dragDx }
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                if (isSelected) Color(0xFF124554)
                                else if (isDragging) Color(0xFF145363)
                                else Color(0xFF0D2A3A)
                            )
                            .border(
                                width = if (isSelected || isDragging) 1.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFF00A99D)
                                else if (isDragging) ApexPalette.NeonCyan
                                else ApexPalette.NeonCyan.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(7.dp)
                            )
                            .pointerInput(clip, isLocked, msToDp, density.density) {
                                if (!isLocked) {
                                    // Let ordinary horizontal swipes pan the
                                    // timeline; moving an overlay is an explicit
                                    // long-press drag so the two gestures do not
                                    // compete for the same pointer stream.
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { isDragging = true },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragDx += dragAmount.x
                                        },
                                        onDragEnd = {
                                            val deltaMs = (dragDx / (msToDp * density.density)).toLong()
                                            val rawOffset =
                                                (clip.timelineOffsetMs + deltaMs).coerceAtLeast(0L)
                                            // Snap the start (and the end)
                                            // to the nearest playhead /
                                            // clip edge within 200ms.
                                            // Targets: playhead (read live
                                            // at drag end) + every OTHER
                                            // clip's start/end.
                                            val snapTargets = (snapEdges + playheadMsProvider()).filter {
                                                it != clip.timelineOffsetMs &&
                                                    it != clip.timelineOffsetMs + clipDur
                                            }.distinct()
                                            var snapped = rawOffset
                                            var bestDist = 200L
                                            for (edge in snapTargets) {
                                                val dStart = kotlin.math.abs(rawOffset - edge)
                                                if (dStart < bestDist) {
                                                    bestDist = dStart
                                                    snapped = edge
                                                }
                                                val dEnd = kotlin.math.abs(
                                                    (rawOffset + clipDur) - edge
                                                )
                                                if (dEnd < bestDist) {
                                                    bestDist = dEnd
                                                    snapped = (edge - clipDur).coerceAtLeast(0L)
                                                }
                                            }
                                            dragDx = 0f
                                            isDragging = false
                                            if (snapped != clip.timelineOffsetMs) {
                                                onMoveClipOffset(clip.id, snapped)
                                            }
                                        },
                                        onDragCancel = { dragDx = 0f; isDragging = false }
                                    )
                                }
                            }
                            .clickable { onSelectClip(clip.id) }
                            .semantics {
                                contentDescription = "Overlay clip ${clip.name}, track V${layerIndex + 1}"
                                selected = isSelected
                            }
                    ) {
                        // Filmstrip thumbnails inside the PiP block:
                        // dp-interval cells, culled to the visible
                        // window. Clip width is ALWAYS duration x
                        // pxPerSecond; cells never size the clip.
                        FilmstripCells(
                            clipId = clip.id,
                            clipStartMs = clip.timelineOffsetMs,
                            clipDurMs = clipDur,
                            msToDp = msToDp,
                            frameIntervalMs = frameIntervalMs,
                            visibleWindowPx = visibleWindowPx,
                            filmstripFrames = filmstripFrames,
                            placeholderIconSize = 10.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(Modifier.fillMaxSize().background(Color(0xFF00C7D9).copy(alpha = 0.16f)))
                        Text(clip.name, color = Color.White, fontSize = 8.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 13.dp, bottom = 2.dp)
                                .background(Color(0xBB111B28), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp))
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
                        // Trim handles (both edges): drag to trim, commit
                        // once at drag end. Children win the gesture over
                        // the chip's move-drag (they consume the drag).
                        // Minimum trimmed span is 100ms (matches
                        // trimClip's own enforcement).
                        if (!isLocked) {
                            // Left edge → trimStartMs.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .width(12.dp)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .testTag("timeline-overlay-trim-start-${clip.id}")
                                    .pointerInput(clip, msToDp, density.density) {
                                        detectDragGestures(
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val requestedDxPx = trimStartDxPx + dragAmount.x
                                                val newStart = timelineTrimStartForOutputDeltaMs(
                                                    clip,
                                                    sourceTrimDeltaToMs(requestedDxPx, msToDp, density.density)
                                                )
                                                // Keep the live chip edge under the pointer using the
                                                // actual output-duration change after curve renormalization.
                                                val outputShiftMs = clip.effectiveDurationMs() -
                                                    clip.copy(trimStartMs = newStart).effectiveDurationMs()
                                                trimStartDxPx = sourceTrimDeltaToPx(outputShiftMs, msToDp, density.density)
                                            },
                                            onDragEnd = {
                                                val newStart = timelineTrimStartForOutputDeltaMs(
                                                    clip,
                                                    sourceTrimDeltaToMs(trimStartDxPx, msToDp, density.density)
                                                )
                                                trimStartDxPx = 0f
                                                if (newStart != clip.trimStartMs) {
                                                    onTrimClip(clip.id, newStart, clip.trimEndMs)
                                                }
                                            },
                                            onDragCancel = { trimStartDxPx = 0f }
                                        )
                                    }
                            )
                            // Right edge → trimEndMs.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(12.dp)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .testTag("timeline-overlay-trim-end-${clip.id}")
                                    .pointerInput(clip, msToDp, density.density) {
                                        detectDragGestures(
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val requestedDxPx = trimEndDxPx + dragAmount.x
                                                val newEnd = timelineTrimEndForOutputDeltaMs(
                                                    clip,
                                                    sourceTrimDeltaToMs(requestedDxPx, msToDp, density.density)
                                                )
                                                val outputShiftMs = clip.copy(trimEndMs = newEnd).effectiveDurationMs() -
                                                    clip.effectiveDurationMs()
                                                trimEndDxPx = sourceTrimDeltaToPx(outputShiftMs, msToDp, density.density)
                                            },
                                            onDragEnd = {
                                                val newEnd = timelineTrimEndForOutputDeltaMs(
                                                    clip,
                                                    sourceTrimDeltaToMs(trimEndDxPx, msToDp, density.density)
                                                )
                                                trimEndDxPx = 0f
                                                if (newEnd != clip.trimEndMs) {
                                                    onTrimClip(clip.id, clip.trimStartMs, newEnd)
                                                }
                                            },
                                            onDragCancel = { trimEndDxPx = 0f }
                                        )
                                    }
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
                        onSeekToKeyframe = onSeekToKeyframe,
                        onMoveKeyframe = { id, time -> onMoveKeyframe(clip.id, id, time) },
                        onAddAtPlayhead = { onToggleKeyframeAtPlayhead(clip.id) },
                        accentColor = ApexPalette.NeonCyan,
                        showLaneBackground = false,
                        markerTagPrefix = "overlay-${clip.id}",
                        addButtonAlignment = Alignment.BottomEnd,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("timeline-keyframe-layer-overlay-${clip.id}")
                    )
                }
            }
            Box(Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp).zIndex(5f)) {
                TimelineLaneIdentity("Overlay V${layerIndex + 1}", ApexPalette.NeonCyan)
            }
        }
    }
}
/**
 * Text overlays lane — rendered ONLY when text exists (the caller
 * gates on it): every text element appears here as a tappable layer
 * segment positioned by startMs, each with its own keyframe diamond
 * strip. Tapping selects the overlay (white border) and arms the text
 * quick actions below the timeline.
 */
internal data class MappedTimelineTextLayer(
    val overlay: TextOverlay,
    val ownerClipId: String?,
    val startMs: Long,
    val endMs: Long
)

internal fun mapProjectTextLayers(
    clips: List<MediaClip>,
    overlays: List<TextOverlay>,
    ownerByOverlayId: Map<String, String>
): List<MappedTimelineTextLayer> {
    // IDs in older projects are not necessarily globally unique. Resolve the
    // actual source-list owner, and coalesce only explicitly linked segments.
    val owners = java.util.IdentityHashMap<TextOverlay, java.util.ArrayDeque<String>>()
    clips.forEach { clip -> clip.textOverlays.forEach { overlay ->
        owners.getOrPut(overlay) { java.util.ArrayDeque() }.addLast(clip.id)
    } }
    val seenGroups = mutableSetOf<String>()
    return overlays.mapNotNull { overlay ->
        val owner = owners[overlay]?.pollFirst() ?: ownerByOverlayId[overlay.id]
        val group = overlay.projectTextLayerId
        if (group != null && !seenGroups.add(group)) return@mapNotNull null
        val range = ProjectTextOverlayTimeline.range(clips, overlay.id, owner)
        val start = range?.startMs ?: overlay.startMs.coerceAtLeast(0L)
        val end = range?.endMs ?: overlay.endMs
        MappedTimelineTextLayer(overlay.copy(startMs = start, endMs = end), owner, start, end)
    }
}

@Composable
private fun TimelineTextLane(
    textLayers: List<MappedTimelineTextLayer>,
    rowIndices: List<Int>,
    msToDp: Float,
    timelineDurationMs: Long,
    selectedTextOverlayId: String? = null,
    onSelectTextOverlay: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onSeekToKeyframe: (Long) -> Unit = {},
    onMoveTextKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _, _ -> },
    onToggleTextKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onMoveTextMaskKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _, _ -> },
    onToggleTextMaskKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onSetTextOverlayRange: ((clipId: String, overlayId: String, startMs: Long, endMs: Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A1B2D))
            .border(1.dp, Color(0xFF203047), RoundedCornerShape(8.dp))
            // Overlapping segments occupy separate 28dp subrows.
    ) {
        textLayers.forEachIndexed { itemIndex, layer ->
            val textOverlay = layer.overlay
            val ownerClipId = layer.ownerClipId
            val ovStartMs = layer.startMs.coerceAtLeast(0L)
            val ovEndMs = if (layer.endMs == Long.MAX_VALUE) ovStartMs + 3000L else layer.endMs
            val ovDur = (ovEndMs - ovStartMs).coerceAtLeast(500L)
            val ovWidthDp = maxOf((ovDur * msToDp).dp, 40.dp)
            val ovOffsetDp = (ovStartMs * msToDp).dp

            val isSelected = textOverlay.id == selectedTextOverlayId
            Box(
                modifier = Modifier
                    .offset(x = ovOffsetDp)
                    .offset(y = ((rowIndices.getOrElse(itemIndex) { 0 }) * 28).dp)
                    .width(ovWidthDp)
                    // Leave clearance for the rotated mask diamond at the row edge.
                    .height(if (textOverlay.mask != null) 26.dp else 18.dp)
                    .testTag("timeline-keyframe-layer-text-${textOverlay.id}")
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(ovWidthDp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF5124A8), Color(0xFF7838D1))))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Color.White else Color(0xFFAE8BFF).copy(alpha = 0.8f),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .clickable {
                            if (ownerClipId != null) onSelectTextOverlay(ownerClipId, textOverlay.id)
                        }
                        .semantics {
                            contentDescription = "Text overlay ${textOverlay.text.ifEmpty { "Title" }}"
                            selected = isSelected
                        }
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
                    showLaneBackground = false,
                    markerTagPrefix = "text-${textOverlay.id}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (textOverlay.mask != null) 16.dp else 18.dp)
                        .align(Alignment.TopStart)
                        .testTag("timeline-keyframe-layer-text-markers-${textOverlay.id}")
                )
                // Mask keyframe strip (gold) — only when the text has
                // a mask. Mask keyframes carry (id, timeMs) only, so
                // they ride the shared strip as lightweight Keyframes
                // with the same tap/drag/+ UX.
                if (textOverlay.mask != null) {
                    KeyframeDiamondStrip(
                        keyframes = textOverlay.maskKeyframes.map {
                            Keyframe(id = it.id, timeMs = it.timeMs)
                        },
                        spanStartMs = ovStartMs,
                        spanDurationMs = ovDur,
                        stripWidthDp = ovWidthDp,
                        msToDp = msToDp,
                        onSeekToKeyframe = onSeekToKeyframe,
                        onMoveKeyframe = { kfId, newTimeMs ->
                            if (ownerClipId != null) onMoveTextMaskKeyframe(ownerClipId, textOverlay.id, kfId, newTimeMs)
                        },
                        onAddAtPlayhead = {
                            if (ownerClipId != null) onToggleTextMaskKeyframeAtPlayhead(ownerClipId, textOverlay.id)
                        },
                        accentColor = Color(0xFFFFC107),
                        showLaneBackground = false,
                        markerTagPrefix = "text-mask-${textOverlay.id}",
                        modifier = Modifier
                        .height(10.dp)
                            .align(Alignment.BottomStart)
                            .testTag("timeline-keyframe-layer-text-mask-${textOverlay.id}")
                    )
                }
                if (isSelected && ownerClipId != null && onSetTextOverlayRange != null) {
                    val latestStart by rememberUpdatedState(ovStartMs)
                    val latestEnd by rememberUpdatedState(ovEndMs)
                    val latestHandler by rememberUpdatedState(onSetTextOverlayRange)
                    @Composable
                    fun handle(isStart: Boolean) {
                        Box(
                            Modifier
                                .align(if (isStart) Alignment.CenterStart else Alignment.CenterEnd)
                                .width(8.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White)
                                .pointerInput(textOverlay.id, isStart, msToDp, timelineDurationMs) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val currentStart = latestStart
                                        val currentEnd = latestEnd
                                        val delta = if (msToDp > 0f && density > 0f)
                                            (dragAmount.x / (msToDp * density)).roundToLong() else 0L
                                        if (isStart) {
                                            val next = (currentStart + delta).coerceIn(0L, currentEnd - 1L)
                                            latestHandler?.invoke(ownerClipId, textOverlay.id, next, currentEnd)
                                        } else {
                                            val next = (currentEnd + delta).coerceIn(currentStart + 1L, timelineDurationMs.coerceAtLeast(currentStart + 1L))
                                            latestHandler?.invoke(ownerClipId, textOverlay.id, currentStart, next)
                                        }
                                    }
                                }
                                .testTag("timeline-text-range-handle-${if (isStart) "start" else "end"}-${textOverlay.id}")
                        )
                    }
                    handle(isStart = true)
                    handle(isStart = false)
                }
            }
            Box(Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 1.dp).zIndex(5f)) {
                TimelineLaneIdentity("Text", Color(0xFFAE8BFF))
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
    showLaneBackground: Boolean = true,
    markerTagPrefix: String? = null,
    addButtonAlignment: Alignment = Alignment.CenterEnd,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    Box(
        modifier = modifier
            .width(stripWidthDp)
            .then(if (showLaneBackground) Modifier.background(Color(0xFFE9EDF2).copy(alpha = 0.6f)) else Modifier)
    ) {
        val spanDur = spanDurationMs.coerceAtLeast(1L)
        keyframes.forEach { kf ->
            // Keep the gesture coroutine alive across marker updates, but make
            // each event/release observe the latest timeline data and callbacks.
            val currentKeyframe by rememberUpdatedState(kf)
            val currentSpanStartMs by rememberUpdatedState(spanStartMs)
            val currentSpanDurationMs by rememberUpdatedState(spanDur)
            val currentOnSeekToKeyframe by rememberUpdatedState(onSeekToKeyframe)
            val currentOnMoveKeyframe by rememberUpdatedState(onMoveKeyframe)
            val frac = ((kf.timeMs - spanStartMs).toFloat() / spanDur.toFloat()).coerceIn(0f, 1f)
            var dragDx by remember(kf.id) { mutableFloatStateOf(0f) }
            var isDragging by remember(kf.id) { mutableStateOf(false) }
            var markerCoordinates by remember(kf.id) { mutableStateOf<LayoutCoordinates?>(null) }
            val diamondXdp = (frac * stripWidthDp.value).dp - 5.dp

            Box(
                modifier = Modifier
                    .offset(x = diamondXdp)
                    .size(10.dp)
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = dragDx
                        rotationZ = 45f
                    }
                    .background(
                        if (isDragging) Color.White else accentColor,
                        RoundedCornerShape(2.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                    .onGloballyPositioned { markerCoordinates = it }
                    .pointerInput(kf.id, msToDp, density) {
                        awaitEachGesture {
                            // Marker gestures are more specific than either
                            // horizontal timeline scroller. Claim the down in
                            // Main pass so the parent cannot consume the first
                            // drag delta (which used to shorten physical drags
                            // or cancel markers in horizontally-scrolled rows).
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            down.consume()
                            var previousRootX = markerCoordinates
                                ?.localToRoot(down.position)?.x ?: down.position.x
                            var totalDx = 0f
                            var dragged = false
                            var released = false
                            while (!released) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                // Use root-space coordinates: the marker and
                                // its scrollable ancestors may translate during
                                // the gesture, but finger movement must remain
                                // measured in physical screen pixels.
                                val pointerRootX = markerCoordinates
                                    ?.localToRoot(change.position)?.x ?: change.position.x
                                val deltaX = pointerRootX - previousRootX
                                previousRootX = pointerRootX
                                if (change.pressed) {
                                    totalDx += deltaX
                                    if (!dragged && abs(totalDx) > viewConfiguration.touchSlop) {
                                        dragged = true
                                        isDragging = true
                                    }
                                    if (dragged) {
                                        change.consume()
                                        dragDx += deltaX
                                    }
                                } else {
                                    change.consume()
                                    released = true
                                }
                            }
                            if (dragged) {
                                val deltaMs = sourceTrimDeltaToMs(totalDx, msToDp, density)
                                val latestKeyframe = currentKeyframe
                                val latestSpanStartMs = currentSpanStartMs
                                val latestSpanDurationMs = currentSpanDurationMs
                                val newTimeMs = (latestKeyframe.timeMs + deltaMs)
                                    .coerceIn(latestSpanStartMs, latestSpanStartMs + latestSpanDurationMs)
                                dragDx = 0f
                                isDragging = false
                                if (newTimeMs != latestKeyframe.timeMs) {
                                    currentOnMoveKeyframe(latestKeyframe.id, newTimeMs)
                                }
                            } else {
                                currentOnSeekToKeyframe(currentKeyframe.timeMs)
                            }
                        }
                    }
                    .then(markerTagPrefix?.let { Modifier.testTag("timeline-keyframe-marker-$it-${kf.id}") } ?: Modifier)
                    .semantics {
                        onClick(label = "Seek to keyframe") {
                            onSeekToKeyframe(kf.timeMs)
                            true
                        }
                    }
            ) {}
        }

        // "+" chip to toggle a keyframe at the playhead.
        if (showAddButton) {
            Box(
                modifier = Modifier
                    .align(addButtonAlignment)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.25f))
                    .border(1.dp, accentColor, CircleShape)
                    .clickable(onClick = onAddAtPlayhead),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color(0xFF111827), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
    filmstripFrames: Map<Pair<String, Long>, Bitmap>,
    frameIntervalMs: Long,
    visibleWindowPx: Pair<Int, Int>,
    draggingClipId: String?,
    dragAccumulatedOffsetPx: Float,
    targetDropIndex: Int,
    transitions: List<ClipTransition> = emptyList(),
    onOpenTransition: (fromClipId: String, toClipId: String) -> Unit = { _, _ -> },
    onSelectClip: (String) -> Unit,
    onSeekToKeyframe: ((Long) -> Unit)? = null,
    onClipBoundsMeasured: (index: Int, leftPx: Float, widthPx: Float) -> Unit,
    onAddClip: () -> Unit,
    multiSelectMode: Boolean = false,
    multiSelectedIds: Set<String> = emptySet(),
    onToggleMultiSelect: (String) -> Unit = {},
    onTrimClip: (clipId: String, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> },
    onReorderByDrag: (clipId: String, deltaX: Float) -> Unit = { _, _ -> },
    onReorderByDragEnd: () -> Unit = {},
    onMoveKeyframe: (clipId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _ -> },
    // Long-press context menu (P2/P3): Split / Duplicate / Speed /
    // Trim / Delete, offered directly on the clip. The playhead
    // provider makes "Split at playhead" land wherever the user is
    // currently scrubbed to — the second-by-second edit path.
    onSplitClip: (clipId: String, atMs: Long) -> Unit = { _, _ -> },
    onDuplicateClip: (clipId: String) -> Unit = {},
    onDeleteClip: (clipId: String) -> Unit = {},
    onOpenSpeed: () -> Unit,
    onOpenTrim: () -> Unit,
    // Live playhead: the long-press menu splits at THIS position.
    playheadMsProvider: () -> Long = { 0L },
    modifier: Modifier = Modifier
) {

    // Which clip's long-press menu is open (null = dismissed).
    val density = LocalDensity.current.density
    var contextMenuClipId by remember { mutableStateOf<String?>(null) }
    // Timeline-global start offset for each V1 clip (keyframes are timeline-global).
    val clipTimelineStarts = remember(videoClips) {
        val starts = mutableListOf<Long>()
        var acc = 0L
        videoClips.forEach { c ->
            starts.add(acc)
            // Speed-ramped clips occupy their OUTPUT length.
            acc += c.effectiveDurationMs()
        }
        starts
    }

    // CapCut-style flat filmstrip: no border, radius or padding —
    // the clips run edge to edge, with the transition junction badge
    // as the boundary marker between them.
    Box(
        modifier = modifier
            .background(Color(0xFF0B1220))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            videoClips.forEachIndexed { index, clip ->
                val isSelected = clip.id == selectedClipId
                val isBeingDragged = clip.id == draggingClipId
                val isMultiSelected = clip.id in multiSelectedIds
                // Live trim preview (updated during grip drag, committed on release).
                var trimPreviewStart by remember(clip) { mutableStateOf<Long?>(null) }
                var trimPreviewEnd by remember(clip) { mutableStateOf<Long?>(null) }
                val effTrimStart = trimPreviewStart ?: clip.trimStartMs
                val effTrimEnd = trimPreviewEnd ?: clip.trimEndMs
                // Speed-ramped clips occupy their OUTPUT length; during a
                // live trim drag the preview trims define the span.
                val clipDur = if (trimPreviewStart != null || trimPreviewEnd != null) {
                    clip.copy(trimStartMs = effTrimStart, trimEndMs = effTrimEnd)
                        .effectiveDurationMs().coerceAtLeast(500L)
                } else {
                    clip.effectiveDurationMs().coerceAtLeast(500L)
                }
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
                        .testTag("timeline-clip-${clip.id}")
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInParent()
                            val width = coords.size.width.toFloat()
                            onClipBoundsMeasured(index, pos.x, width)
                        }
                        .zIndex(if (isBeingDragged) 100f else 1f)
                        .shadow(if (isSelected || isMultiSelected) 7.dp else 0.dp, RoundedCornerShape(7.dp))
                        .graphicsLayer {
                            if (isBeingDragged) {
                                translationX = dragAccumulatedOffsetPx
                                scaleX = 1.05f
                                scaleY = 1.05f
                                shadowElevation = 16f
                                alpha = 0.92f
                            }
                        }
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (isMultiSelected) Color(0xFF28231B)
                            else if (isSelected) Color(0xFF103442)
                            else Color(0xFF0D2636)
                        )
                        .border(
                            width = if (isBeingDragged) 2.5.dp else if (isSelected || isMultiSelected) 2.dp else 1.dp,
                            color = if (isBeingDragged) ApexPalette.NeonCyan
                            else if (isMultiSelected) ApexPalette.NeonAmber
                            else if (isSelected) ApexPalette.NeonCyan
                            else Color(0xFF178B9B).copy(alpha = 0.75f),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .pointerInput(clip.id, isSelected) {
                            // Direct finger-drag reorder for the SELECTED clip (no long-press
                            // needed).
                            // Long-press itself now opens the clip context menu (see below).
                            // One V1 clip cannot be reordered. Let the shared
                            // timeline scroll own its swipe, including after
                            // selection, instead of swallowing it as a no-op.
                            if (isSelected && !multiSelectMode && videoClips.size > 1) {
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
                            detectTapGestures(
                                onTap = {
                                    if (multiSelectMode) onToggleMultiSelect(clip.id)
                                    else onSelectClip(clip.id)
                                },
                                onLongPress = {
                                    // Long-press opens the clip context menu
                                    // (Split / Duplicate / Speed / Trim /
                                    // Delete) on the video layer itself.
                                    onSelectClip(clip.id)
                                    contextMenuClipId = clip.id
                                }
                            )
                        }
                ) {
                    // Filmstrip: dp-interval cells (one frame per
                    // 64dp of clip width), culled to the visible
                    // window. Clip width is ALWAYS duration x
                    // pxPerSecond; cells never size the clip.
                    FilmstripCells(
                        clipId = clip.id,
                        clipStartMs = clipTimelineStarts.getOrElse(index) { 0L },
                        clipDurMs = clipDur,
                        msToDp = msToDp,
                        frameIntervalMs = frameIntervalMs,
                        visibleWindowPx = visibleWindowPx,
                        filmstripFrames = filmstripFrames,
                        placeholderIconSize = 14.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF00C7D9).copy(alpha = 0.14f), Color(0xFF123D59).copy(alpha = 0.08f))
                            )
                        )
                    )

                    // Persisted source-time looks are visible as colored, labeled range
                    // bands on the clip itself. The strip is decorative and leaves the
                    // established clip selection/trim gestures untouched.
                    if (clip.workspaceEffects.isNotEmpty() && effTrimEnd > effTrimStart) {
                        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
                            val displayedClip = clip.copy(trimStartMs = effTrimStart, trimEndMs = effTrimEnd)
                            val outputSpan = ClipTimeMapping.mainClipDurationMs(displayedClip).toFloat().coerceAtLeast(1f)
                            clip.workspaceEffects.forEachIndexed { effectIndex, effect ->
                                val visibleStart = maxOf(effect.startMs, effTrimStart)
                                val visibleEnd = minOf(effect.endMs, effTrimEnd)
                                if (effect.id in WorkspaceEffectPresets.all && visibleEnd > visibleStart) {
                                    val left = (ClipTimeMapping.mainOutputOffsetAtSourceMs(displayedClip, visibleStart) / outputSpan).coerceIn(0f, 1f)
                                    val right = (ClipTimeMapping.mainOutputOffsetAtSourceMs(displayedClip, visibleEnd) / outputSpan).coerceIn(left, 1f)
                                    val bandWidth = (maxWidth * (right - left)).coerceAtLeast(18.dp)
                                    val row = effectIndex % 3
                                    val preset = WorkspaceFilterPresets.find(effect.id)
                                    val tint = Color(preset?.swatchArgb ?: 0xFF5E91CA)
                                    Box(
                                        Modifier.offset(x = maxWidth * left, y = maxHeight - (14 + row * 12).dp)
                                            .width(bandWidth).height(11.dp).clip(RoundedCornerShape(3.dp))
                                            .background(tint.copy(alpha = 0.93f))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(3.dp)),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            WorkspaceEffectPresets.label(effect.id),
                                            color = Color(0xFF10141B), fontSize = 7.sp, fontWeight = FontWeight.Bold,
                                            maxLines = 1, overflow = TextOverflow.Clip,
                                            modifier = Modifier.padding(horizontal = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(clip.name, color = Color.White, fontSize = 9.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 4.dp)
                            .background(Color(0xBB111B28), RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp))

                    // Mask badge (Mask tool): shown when a shape mask is
                    // attached to the clip.
                    if (clip.mask != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF00E5FF).copy(alpha = 0.92f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Mask: ${clip.mask?.shape?.label ?: ""}",
                                color = Color.Black,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Trim Grips on active clip — draggable to trim start/end.
                    if (isSelected && !multiSelectMode) {
                        var trimDragAccumX by remember(clip, msToDp, density) { mutableFloatStateOf(0f) }
                        // Left grip: trim start
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(16.dp).fillMaxHeight()
                                .testTag("timeline-trim-start-${clip.id}")
                                .pointerInput(clip, msToDp, density) {
                                    detectDragGestures(
                                        onDragStart = {
                                            trimDragAccumX = 0f
                                            trimPreviewStart = clip.trimStartMs
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            trimDragAccumX += dragAmount.x
                                            val deltaMs = sourceTrimDeltaToMs(trimDragAccumX, msToDp, density)
                                            val end = trimPreviewEnd ?: clip.trimEndMs
                                            trimPreviewStart = timelineTrimStartForOutputDeltaMs(
                                                clip.copy(trimEndMs = end), deltaMs
                                            )
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
                                    .size(width = 6.dp, height = 30.dp)
                                    .background(Brush.verticalGradient(listOf(ApexPalette.NeonPurple, ApexPalette.NeonCyan)), RoundedCornerShape(3.dp))
                            )
                        }
                        // Right grip: trim end
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(16.dp).fillMaxHeight()
                                .testTag("timeline-trim-end-${clip.id}")
                                .pointerInput(clip, msToDp, density) {
                                    detectDragGestures(
                                        onDragStart = {
                                            trimDragAccumX = 0f
                                            trimPreviewEnd = clip.trimEndMs
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            trimDragAccumX += dragAmount.x
                                            val deltaMs = sourceTrimDeltaToMs(trimDragAccumX, msToDp, density)
                                            val start = trimPreviewStart ?: clip.trimStartMs
                                            trimPreviewEnd = timelineTrimEndForOutputDeltaMs(
                                                clip.copy(trimStartMs = start), deltaMs
                                            )
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
                                    .size(width = 6.dp, height = 30.dp)
                                    .background(Brush.verticalGradient(listOf(ApexPalette.NeonPurple, ApexPalette.NeonCyan)), RoundedCornerShape(3.dp))
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
                        showLaneBackground = false,
                        markerTagPrefix = "main-${clip.id}",
                        modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center)
                                .testTag("timeline-keyframe-layer-main-${clip.id}")
                        )
                    }

                    // Long-press context menu (P2/P3): every action the
                    // clip bar exposes, reachable straight from the clip.
                    // Split uses the LIVE playhead, so the Second-by-second
                    // edit flow needs no detour through the toolbar.
                    androidx.compose.material3.DropdownMenu(
                        expanded = contextMenuClipId == clip.id,
                        onDismissRequest = { contextMenuClipId = null }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Split at playhead") },
                            onClick = {
                                contextMenuClipId = null
                                onSplitClip(clip.id, playheadMsProvider())
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                contextMenuClipId = null
                                onDuplicateClip(clip.id)
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Speed") },
                            onClick = {
                                contextMenuClipId = null
                                onSelectClip(clip.id)
                                onOpenSpeed()
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Trim") },
                            onClick = {
                                contextMenuClipId = null
                                onSelectClip(clip.id)
                                onOpenTrim()
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                contextMenuClipId = null
                                onDeleteClip(clip.id)
                            }
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

            // A restrained outlined add affordance sits at the true filmstrip end.
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF111A2B))
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onAddClip)
                    .testTag("timeline-add-at-end"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Clip",
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Box(Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 3.dp).zIndex(5f)) {
            TimelineLaneIdentity("Video", ApexPalette.NeonCyan)
        }
    }
}

/**
 * Sticker lane — rendered ONLY when stickers exist (the caller gates
 * on it): every project sticker appears as a tappable segment
 * positioned by its startMs. Tapping selects the sticker (white
 * border) and arms the sticker quick actions below the timeline.
 */
@Composable
private fun TimelineStickerLane(
    stickers: List<StickerOverlay>,
    rowIndices: List<Int>,
    msToDp: Float,
    selectedStickerId: String? = null,
    onSelectSticker: (String?) -> Unit = {},
    onSeekToKeyframe: (Long) -> Unit = {},
    onMoveStickerKeyframe: (stickerId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _ -> },
    onToggleStickerKeyframeAtPlayhead: (stickerId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageCache = remember(context) { StickerImageCache(context.applicationContext) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A1B2D))
            .border(1.dp, Color(0xFF203047), RoundedCornerShape(8.dp))
    ) {
        stickers.forEachIndexed { itemIndex, sticker ->
            val previewBitmap by produceState<Bitmap?>(initialValue = null, key1 = sticker.assetPath) {
                val path = sticker.assetPath ?: return@produceState
                value = withContext(Dispatchers.IO) { imageCache.get(path) }
            }
            val start = sticker.startMs.coerceAtLeast(0L)
            val rawEnd = if (sticker.endMs == Long.MAX_VALUE) start + 3000L else sticker.endMs
            val dur = (rawEnd - start).coerceAtLeast(500L)
            val isSelected = sticker.id == selectedStickerId
            val assetName = sticker.assetPath?.substringAfterLast("/")?.substringBeforeLast(".")
            val label = when {
                sticker.name.isNotBlank() -> sticker.name
                !assetName.isNullOrBlank() -> assetName
                else -> sticker.symbolOrUri.take(12)
            }
            val segWidthDp = (dur * msToDp).dp.coerceAtLeast(52.dp)
            val hasKeyframeLane = isSelected || sticker.keyframes.keyframes.isNotEmpty()
            Box(
                modifier = Modifier
                    .offset(x = (start * msToDp).dp)
                    .offset(y = ((rowIndices.getOrElse(itemIndex) { 0 }) * 28).dp)
                    .width(segWidthDp)
                    .height(28.dp)
                    .testTag("timeline-keyframe-layer-sticker-${sticker.id}")
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(segWidthDp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Brush.horizontalGradient(
                                if (isSelected) listOf(Color(0xFF5434BD), Color(0xFF7251DB))
                                else listOf(Color(0xFF292460), Color(0xFF49328F))
                            )
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Color.White else Color(0xFF45D5C5).copy(alpha = 0.75f),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .clickable { onSelectSticker(sticker.id) }
                        .semantics {
                            contentDescription = "Sticker $label"
                            selected = isSelected
                        }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (sticker.symbolOrUri.isNotBlank()) {
                            Text(sticker.symbolOrUri, fontSize = 10.sp, maxLines = 1)
                        }
                        Text(
                            text = label,
                            color = Color(0xFFFCE7FF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Position keyframe strip (same UX as text keyframes:
                // tap diamond = seek, drag = move, "+" = add at playhead).
                // Always shown for the selected sticker; for others it
                // appears once they have keyframes — a static sticker
                // stays a clean single row.
                if (hasKeyframeLane) {
                    KeyframeDiamondStrip(
                        keyframes = sticker.keyframes.keyframes,
                        spanStartMs = start,
                        spanDurationMs = dur,
                        stripWidthDp = segWidthDp,
                        msToDp = msToDp,
                        onSeekToKeyframe = onSeekToKeyframe,
                        onMoveKeyframe = { kfId, newTimeMs ->
                            onMoveStickerKeyframe(sticker.id, kfId, newTimeMs)
                        },
                        onAddAtPlayhead = { onToggleStickerKeyframeAtPlayhead(sticker.id) },
                        accentColor = Color(0xFF45D5C5),
                        showLaneBackground = false,
                        markerTagPrefix = "sticker-${sticker.id}",
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("timeline-keyframe-layer-sticker-markers-${sticker.id}")
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp).zIndex(5f)) {
            TimelineLaneIdentity("Stickers", Color(0xFFA88CFF))
        }
    }
}

/**
 * FX lane (mockup): one tappable segment per applied filter / FX /
 * adjustment — e.g. "Bright −5 · Contrast · 14 FX". Tapping a segment
 * opens the matching panel. The caller renders this lane ONLY when
 * something is applied, so there is no empty branch.
 */
@Composable
private fun TimelineFxTrack(
    activeFxId: String?,
    activeFilterId: String?,
    filterIntensity: Float,
    adjustments: VideoAdjustments,
    fxIntensity: Float,
    durationMs: Long,
    msToDp: Float,
    fxKeyframes: List<Keyframe> = emptyList(),
    onSeekToKeyframe: (Long) -> Unit = {},
    onToggleKeyframeAtPlayhead: () -> Unit = {},
    onOpenFilter: () -> Unit = {},
    onOpenFxPanel: () -> Unit = {},
    onOpenAdjust: () -> Unit = {},
    onClearFx: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val adjSummary = remember(adjustments) { adjustmentsSummary(adjustments) }
    val hasAdjustments = adjSummary.isNotEmpty()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF10182A))
            .border(1.dp, Color(0xFF263A57), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                if (!activeFilterId.isNullOrBlank()) {
                    FxSegment(
                        emoji = "🎨",
                        text = "${prettifyId(activeFilterId)} ${(filterIntensity.coerceIn(0f, 1f) * 100).toInt()}%",
                        onClick = onOpenFilter
                    )
                }
                if (!activeFxId.isNullOrBlank()) {
                    FxSegment(
                        emoji = "✨",
                        text = "${prettifyId(activeFxId)} ${(fxIntensity.coerceIn(0f, 1f) * 100).toInt()}%",
                        onClick = onOpenFxPanel
                    )
                }
                if (hasAdjustments) {
                    FxSegment(
                        emoji = "🎚",
                        text = adjSummary,
                        onClick = onOpenAdjust
                    )
                }
                // + chip to open the FX panel for more effects.
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF12243A))
                        .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.7f), CircleShape)
                        .clickable(onClick = onOpenFxPanel)
                        .semantics { contentDescription = "Add effect" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                // x chip: clears the whole lane (FX + LUT filter + adjustments).
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF12243A))
                        .border(1.dp, Color(0xFF64748B).copy(alpha = 0.7f), CircleShape)
                        .clickable(onClick = onClearFx)
                        .semantics { contentDescription = "Clear effects and filters" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("x", color = Color(0xFF9AA9C2), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        .background(Color(0xFFE9EDF2).copy(alpha = 0.6f))
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
                        Text("+", color = Color(0xFF111827), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp).zIndex(5f)) {
            TimelineLaneIdentity("FX", Color(0xFF42B9FF))
        }
    }
}

@Composable
private fun FxSegment(
    emoji: String,
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF5124A8).copy(alpha = 0.88f),
                        Color(0xFF147B84).copy(alpha = 0.72f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "$emoji $text",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun prettifyId(id: String): String =
    id.replace("_", " ").replaceFirstChar { it.uppercase() }

private fun formatAdjValue(v: Float): String {
    val pct = (v * 100).toInt()
    return if (pct > 0) "+$pct" else "$pct"
}

/**
 * Compact "Bright −5 · Contrast · 14 FX"-style summary of every non-default
 * adjustment, for the FX lane segment (mockup).
 */
private fun adjustmentsSummary(a: VideoAdjustments): String {
    val parts = mutableListOf<String>()
    if (a.brightness != 0f) parts += "Bright ${formatAdjValue(a.brightness)}"
    if (a.contrast != 1f) parts += "Contrast"
    if (a.saturation != 1f) parts += "Sat"
    if (a.exposure != 0f) parts += "Exp ${formatAdjValue(a.exposure)}"
    if (a.highlights != 0f) parts += "High ${formatAdjValue(a.highlights)}"
    if (a.shadows != 0f) parts += "Shad ${formatAdjValue(a.shadows)}"
    if (a.temperature != 0f) parts += "Temp ${formatAdjValue(a.temperature)}"
    if (a.tint != 0f) parts += "Tint ${formatAdjValue(a.tint)}"
    if (a.hdr != 0f) parts += "HDR"
    if (a.brilliance != 0f) parts += "Brill ${formatAdjValue(a.brilliance)}"
    if (a.sharpness != 0f) parts += "Sharp ${formatAdjValue(a.sharpness)}"
    if (a.vignette != 0f) parts += "Vig ${formatAdjValue(a.vignette)}"
    if (a.grain != 0f) parts += "Grain"
    if (a.fade != 0f) parts += "Fade"
    if (a.lightness != 0f) parts += "Light ${formatAdjValue(a.lightness)}"
    if (a.whites != 0f) parts += "Whites ${formatAdjValue(a.whites)}"
    if (a.blacks != 0f) parts += "Blacks ${formatAdjValue(a.blacks)}"
    if (a.hue != 0f) parts += "Hue ${formatAdjValue(a.hue)}"
    if (a.clarity != 0f) parts += "Clarity"
    if (a.denoise != 0f) parts += "Denoise"
    if (a.vibrance != 0f) parts += "Vibrance"
    if (parts.isEmpty()) return ""
    val shown = parts.take(3).joinToString(" · ")
    val suffix = if (parts.size > 3) " · +${parts.size - 3}" else " · ${parts.size} FX"
    return shown + suffix
}

/**
 * A1/A2 audio lane — rendered ONLY when it holds tracks (the caller
 * gates on it). Waveforms appear only after actual PCM analysis is available. Every
 * audio track gets its own row so added audio is actually present
 * and manageable in the timeline.
 */
/**
 * Phase 4: per-track waveform. Decodes the track's own audio (real PCM via
 * MediaAnalyzer, cached) instead of reusing the video clip's waveform.
 * Remains blank until this track's own PCM decode lands — never synthetic.
 */
@Composable
private fun rememberTrackWaveform(track: AudioTrack, fallback: FloatArray): FloatArray {
    val context = LocalContext.current
    var samples by remember(track.uri, track.trimStartMs, track.trimEndMs) {
        mutableStateOf(
            TrackWaveformCache.peek(track.uri, track.trimStartMs, track.trimEndMs) ?: fallback
        )
    }
    LaunchedEffect(track.uri, track.trimStartMs, track.trimEndMs) {
        TrackWaveformCache.get(context, track.uri, track.trimStartMs, track.trimEndMs)
            ?.let { samples = it }
    }
    return samples
}

@Composable
private fun TimelineAudioTrack(
    audioTracks: List<AudioTrack>,
    durationMs: Long,
    msToDp: Float,
    isMuted: Boolean,
    beatMarkersMs: List<Long>,
    snapToBeat: Boolean,
    // Playhead provider (read in draw phase only): passing the ticking
    // Long recomposed every audio row on every playhead tick just to
    // re-tint 120 waveform boxes.
    playheadMsProvider: () -> Long = { 0L },
    selectedAudioTrackId: String? = null,
    onSelectAudioTrack: (String) -> Unit = {},
    onMoveAudioTrack: (trackId: String, startTimeMs: Long) -> Unit = { _, _ -> },
    onTrimAudioTrack: (trackId: String, trimStartMs: Long, trimEndMs: Long, startTimeMs: Long) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val samples = FloatArray(0)
    // A track is selected only after a user tap. Highlighting the first row
    // without updating EditorState made the UI look selected while its
    // contextual actions still targeted the global tools.
    val effectiveSelectedId = selectedAudioTrackId

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1727))
            .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {

        audioTracks.forEach { track ->
            val isSelected = track.id == effectiveSelectedId
            // Phase 4: each track paints its OWN decoded waveform
            // (falls back to the shared clip waveform while decoding).
            val trackSamples = rememberTrackWaveform(track, samples)
            AudioTrackLaneRow(
                track = track,
                isSelected = isSelected,
                samples = trackSamples,
                durationMs = durationMs,
                playheadMsProvider = playheadMsProvider,
                beatMarkersMs = beatMarkersMs,
                isMuted = isMuted || track.isMuted,
                onSelect = { onSelectAudioTrack(track.id) },
                msToDp = msToDp,
                onMove = onMoveAudioTrack,
                onTrim = onTrimAudioTrack,
                modifier = Modifier.fillMaxWidth()
            )
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
    playheadMsProvider: () -> Long = { 0L },
    beatMarkersMs: List<Long>,
    isMuted: Boolean,
    onSelect: () -> Unit,
    msToDp: Float,
    onMove: (trackId: String, startTimeMs: Long) -> Unit,
    onTrim: (trackId: String, trimStartMs: Long, trimEndMs: Long, startTimeMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (track.kind != AudioTrack.Kind.MUSIC) Color(0xFF26D7C8) else Color(0xFFA17AFF)
    val density = LocalDensity.current
    var transientDxPx by remember(track.id) { mutableFloatStateOf(0f) }
    var gestureMode by remember(track.id) { mutableStateOf(AudioBlockGesture.NONE) }
    val sourceEnd = track.sourceEndMs.coerceAtLeast(1L)
    val initialTrimStart = track.trimStartMs.coerceIn(0L, sourceEnd - 1L)
    val initialTrimEnd = track.trimEndMs.coerceIn(initialTrimStart + 1L, sourceEnd)
    val initialStart = track.startTimeMs.coerceAtLeast(0L)
    val deltaMs = (transientDxPx / (msToDp * density.density)).roundToInt().toLong()
    val requestedHead = (initialTrimStart + deltaMs).coerceIn(0L, (initialTrimEnd - 250L).coerceAtLeast(0L))
    val headPreview = if (gestureMode == AudioBlockGesture.LEFT) AudioTrackPlacement.trimmed(
        track, requestedHead, initialTrimEnd, AudioTrackPlacement.timelineStartAfterHeadTrim(track, requestedHead)
    ) else null
    val previewStart = when (gestureMode) {
        AudioBlockGesture.MOVE -> (initialStart + deltaMs).coerceAtLeast(0L)
        AudioBlockGesture.LEFT -> headPreview!!.startTimeMs
        else -> initialStart
    }
    val previewTrimStart = if (gestureMode == AudioBlockGesture.LEFT) {
        headPreview!!.trimStartMs
    } else initialTrimStart
    val previewTrimEnd = if (gestureMode == AudioBlockGesture.RIGHT) {
        (initialTrimEnd + deltaMs).coerceIn(previewTrimStart + 250L, sourceEnd.coerceAtLeast(previewTrimStart + 250L))
    } else initialTrimEnd
    val displayedSpanMs = (previewTrimEnd - previewTrimStart).coerceAtLeast(250L)
    val displayedWidth = maxOf((displayedSpanMs * msToDp).dp, 42.dp)

    Box(modifier = modifier.height(AUDIO_TRACK_ITEM_HEIGHT_DP.dp)) {
        Box(
            modifier = Modifier
                .offset(x = (previewStart * msToDp).dp)
                .width(displayedWidth)
                .fillMaxHeight()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) Color(0xFF102A35) else Color(0xFF101D2B))
                .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) ApexPalette.NeonCyan else accent.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                .testTag("timeline-audio-track-${track.id}")
                .semantics { selected = isSelected }
                .clickable(onClick = onSelect)
                .pointerInput(track, msToDp) {
                    detectDragGestures(
                        onDragStart = { down ->
                            onSelect()
                            val edgePx = 14.dp.toPx()
                            gestureMode = when {
                                down.x <= edgePx -> AudioBlockGesture.LEFT
                                down.x >= size.width - edgePx -> AudioBlockGesture.RIGHT
                                else -> AudioBlockGesture.MOVE
                            }
                            transientDxPx = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            transientDxPx += amount.x
                        },
                        onDragEnd = {
                            val changeMs = (transientDxPx / (msToDp * density.density)).roundToInt().toLong()
                            when (gestureMode) {
                                AudioBlockGesture.MOVE -> onMove(track.id, (initialStart + changeMs).coerceAtLeast(0L))
                                AudioBlockGesture.LEFT -> {
                                    val sourceStart = (initialTrimStart + changeMs).coerceIn(0L, (initialTrimEnd - 250L).coerceAtLeast(0L))
                                    val timelineStart = AudioTrackPlacement.timelineStartAfterHeadTrim(track, sourceStart)
                                    onTrim(track.id, sourceStart, initialTrimEnd, timelineStart)
                                }
                                AudioBlockGesture.RIGHT -> {
                                    val sourceEndMs = (initialTrimEnd + changeMs).coerceIn(initialTrimStart + 250L, sourceEnd.coerceAtLeast(initialTrimStart + 250L))
                                    onTrim(track.id, initialTrimStart, sourceEndMs, initialStart)
                                }
                                AudioBlockGesture.NONE -> Unit
                            }
                            transientDxPx = 0f
                            gestureMode = AudioBlockGesture.NONE
                        },
                        onDragCancel = {
                            transientDxPx = 0f
                            gestureMode = AudioBlockGesture.NONE
                        }
                    )
                }
        ) {
            AudioWaveformBars(
                samples = samples,
                durationMs = displayedSpanMs,
                playheadMsProvider = { (playheadMsProvider() - previewStart).coerceAtLeast(0L) },
                beatMarkersMs = beatMarkersMs,
                isMuted = isMuted,
                barColor = accent,
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
                Text(track.name.ifBlank { "Audio" }, color = Color(0xFFD7E6F3), fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
                if (track.isMuted) Icon(Icons.Default.VolumeOff, contentDescription = "Muted", tint = Color(0xFF64748B), modifier = Modifier.size(12.dp))
            }
            Box(Modifier.align(Alignment.CenterStart).width(14.dp).fillMaxHeight().background(accent.copy(alpha = 0.65f)))
            Box(Modifier.align(Alignment.CenterEnd).width(14.dp).fillMaxHeight().background(accent.copy(alpha = 0.65f)))
        }
        Box(Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp).zIndex(5f)) {
            TimelineLaneIdentity(if (track.kind == AudioTrack.Kind.MUSIC) "Music" else "Audio", accent)
        }
    }
}

private enum class AudioBlockGesture { NONE, MOVE, LEFT, RIGHT }

/**
 * Shared waveform bar renderer used by the A1 lane rows: full-width bars
 * with played-progress tinting and beat-marker highlights.
 *
 * Drawn in ONE Canvas (draw phase), not 120 Box composables: the old
 * Row-of-Boxes recomposed 120 composables on every playhead tick per
 * audio row (1200+ compositions/sec), and the per-bar beat scan was
 * O(bars × markers) inside composition. The playhead is read in the
 * draw phase (redraw on ticks, never recomposition); beat bars are
 * precomputed once per marker set.
 */
@Composable
private fun AudioWaveformBars(
    samples: FloatArray,
    durationMs: Long,
    playheadMsProvider: () -> Long = { 0L },
    beatMarkersMs: List<Long>,
    isMuted: Boolean,
    barColor: Color = ApexPalette.NeonCyan,
    modifier: Modifier = Modifier
) {
    // Beat bars: the old per-bar window check (±1.2 bar widths) lit up
    // to ~3 bars per marker — precompute the same set once here.
    val beatBars = remember(beatMarkersMs, durationMs) {
        val count = 120
        val out = mutableSetOf<Int>()
        for (beatMs in beatMarkersMs) {
            val center = (beatMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f) * count).toInt()
            for (d in -1..1) out.add((center + d).coerceIn(0, count - 1))
        }
        out
    }
    Canvas(modifier = modifier) {
        val barCount = 120
        // Draw-phase read: re-draws on playhead ticks, never recomposes.
        val progress = (playheadMsProvider().toFloat() / durationMs.toFloat().coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        val slotW = size.width / barCount
        val barW = 2.dp.toPx()
        val radius = 1.dp.toPx()
        if (samples.isEmpty()) return@Canvas
        for (i in 0 until barCount) {
            val barFrac = i.toFloat() / barCount.toFloat()
            val sampleIdx = (barFrac * (samples.size - 1)).toInt().coerceIn(samples.indices)
            val amp = samples[sampleIdx].coerceIn(0f, 1.0f)
            val barH = size.height * amp
            val x = i * slotW + (slotW - barW) / 2f
            val color = when {
                isMuted -> Color(0xFF64748B)
                i in beatBars -> ApexPalette.NeonCyan
                barFrac <= progress -> Color(0xFF225E65)
                else -> Color(0xFF26D7C8).copy(alpha = 0.9f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, (size.height - barH) / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(radius, radius)
            )
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
    // A fine white cursor with a cyan locator cap; seeking stays on the ruler.
    Box(
        modifier = modifier
            .offset(x = playheadX - 1.dp)
            .width(2.dp)
            .fillMaxHeight()
            .zIndex(200f)
            .background(if (isSnapped) Color(0xFF00A99D) else Color(0xFF111827))
    ) {
        Box(Modifier.align(Alignment.TopCenter).size(8.dp, 5.dp)
            .background(Color(0xFF00A99D), RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)))
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
    // Horizontal scroll offset (px) provider of the canvas. The overlay
    // sits at canvas-root level (not inside the scrolled content), so
    // the offset shifts the playhead to keep it glued to the playhead
    // position. A provider (read inside graphicsLayer, i.e. the
    // layout/draw phase) instead of a plain Float: reading
    // scrollState.value during composition would recompose the whole
    // VideoTimeline on every scroll frame.
    scrollOffsetPx: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    // The flow can stall (e.g. its collector was cancelled while a sheet
    // opened) while state keeps advancing — the preview timecode reads
    // state directly, so a stalled flow showed a frozen playhead next to
    // a moving timecode. fallbackMs comes straight from the state-driven
    // playheadMs param (via the snap sync above), so never let the
    // rendered playhead fall BEHIND the authoritative state position.
    val flowMs = if (playerPositionFlow != null) {
        playerPositionFlow.collectAsStateWithLifecycle().value
    } else {
        fallbackMs
    }
    val liveMs = maxOf(flowMs, fallbackMs)
    val posMs = liveMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
    // Content-space X (unscrolled); the scroll shift is applied as a
    // layer translation below, so scrolling never recomposes this.
    Box(modifier = Modifier.graphicsLayer { translationX = -scrollOffsetPx() }) {
        TimelinePlayhead(
            playheadX = (posMs * msToDp).dp,
            playheadMs = posMs,
            onScrub = {},
            msToDp = msToDp,
            isSnapped = isSnapped,
            modifier = modifier
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
                color = Color(0xFF111827),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = precisionLabel,
                color = Color(0xFF007F76),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )

            if (abs(zoom - 1.0f) > 0.05f) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE9EDF2))
                        .clickable(onClick = onResetZoom)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "1.0x",
                        color = Color(0xFF374151),
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

    // CapCut-style boundary badge: a small WHITE square at the clip
    // boundary when no transition is set (tap = add transition); a
    // dark pill with the transition icon + label when one is set.
    Box(
        modifier = modifier
            .size(
                width = if (hasTransition) 38.dp else 26.dp,
                height = if (hasTransition) 30.dp else 26.dp
            )
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (hasTransition) Color(0xFFF1EDFA)
                else Color.White
            )
            .border(
                width = if (hasTransition) 1.5.dp else 1.dp,
                color = if (hasTransition) Color(0xFF007F76) else Color(0xFFCBD5E1),
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
                    tint = Color(0xFF007F76),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = transDef?.badgeText?.take(4) ?: "TRN",
                    color = Color(0xFF374151),
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
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    "+",
                    color = Color(0xFF0F172A),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
