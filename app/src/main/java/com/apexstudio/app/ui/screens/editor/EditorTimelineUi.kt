package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.media.TimelineThumbnailCache
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// === 4. CAPCUT-STYLE TIMELINE TRACK AREA ===

/**
 * Filmstrip frame interval: ONE frame per [FILMSTRIP_CELL_DP] of clip
 * width. The ms interval follows zoom (narrow at high zoom, wide at
 * overview zoom), so a 10-minute clip never decodes more frames than
 * the screen can show.
 *
 * Frames are generated LAZILY for the visible window (+1 viewport of
 * buffer), CANCELLED when the window moves away (collectLatest), and
 * cached in memory (LruCache) + on disk (TimelineThumbnailCache, key
 * uri + time + size). Cells render a placeholder until their frame
 * arrives. The old pipeline decoded EVERY second of EVERY clip up
 * front (up to 180 decoder calls per clip) — a 3-minute clip took tens
 * of seconds to fill, so only its first seconds ever showed real
 * frames. Clip block width is ALWAYS duration x pxPerSecond and never
 * derives from how many thumbnails exist.
 */
private const val FILMSTRIP_CELL_DP = 64f

@Composable
fun TimelineTrackArea(
    state: com.apexstudio.app.presentation.state.EditorState,
    onScrub: (Long) -> Unit = {},
    onToggleSnapToBeat: () -> Unit = {},
    onToggleMagneticSnapping: () -> Unit = {},
    onToggleRippleEdit: () -> Unit = {},
    onSelectClip: (String?) -> Unit = {},
    // Tapping empty timeline space clears the selection (hides the clip
    // action bar). Wired to VideoTimeline's background tap.
    onDeselect: () -> Unit = {},
    onSelectFx: (String) -> Unit = {},
    onSelectFilter: (String) -> Unit = {},
    onAddMedia: (ClipType) -> Unit = {},
    onSplitClip: (clipId: String, atMs: Long) -> Unit = { _, _ -> },
    onDuplicateClip: (clipId: String) -> Unit = {},
    onDeleteClip: (clipId: String) -> Unit = {},
    onDeleteClips: (Set<String>) -> Unit = {},
    onMoveClipLeft: (clipId: String) -> Unit = {},
    onMoveClipRight: (clipId: String) -> Unit = {},
    onReorderClips: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onShiftClipOffset: (clipId: String, deltaMs: Long) -> Unit = { _, _ -> },
    onSetClipOffset: (clipId: String, offsetMs: Long) -> Unit = { _, _ -> },
    onTrimClip: (clipId: String, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> },
    onToggleKeyframeAtPlayhead: (clipId: String) -> Unit = {},
    onMoveKeyframe: (clipId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _ -> },
    onToggleTextKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onMoveTextKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _, _ -> },
    onToggleStickerKeyframeAtPlayhead: (stickerId: String) -> Unit = {},
    onMoveStickerKeyframe: (stickerId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _ -> },
    onToggleTextMaskKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onMoveTextMaskKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit = { _, _, _, _ -> },
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onResetZoom: () -> Unit = {},
    onSetZoom: (Float) -> Unit = {},
    onOpenSpeed: () -> Unit = {},
    onOpenAudio: () -> Unit = {},
    onOpenTrim: () -> Unit = {},
    onOpenFx: () -> Unit = {},
    onOpenFilter: () -> Unit = {},
    onOpenAdjust: () -> Unit = {},
    onSelectTextOverlay: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onDeleteTextOverlay: (clipId: String, overlayId: String) -> Unit = { _, _ -> },
    onSelectSticker: (String?) -> Unit = {},
    onDeleteSticker: (String) -> Unit = {},
    onClearFx: () -> Unit = {},
    onOpenAnimation: () -> Unit = {},
    onOpenTransition: () -> Unit = {},
    onOpenClipTransition: (fromClipId: String, toClipId: String) -> Unit = { _, _ -> },
    onOpenChromaKey: () -> Unit = {},
    onOpenMask: () -> Unit = {},
    onOpenArFilters: () -> Unit = {},
    onOpenRoyaltyMusic: () -> Unit = {},
    onSelectAudioTrack: (String) -> Unit = {},
    playerPositionFlow: kotlinx.coroutines.flow.StateFlow<Long>? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val clips = state.project?.clips ?: emptyList()

    // Timeline scroll state, hoisted HERE (not inside VideoTimeline) so
    // opening/closing any bottom sheet can never reset the scroll to
    // 00:00 and strand the playhead off-screen.
    val timelineScrollState = rememberScrollState()
    val timelineVScrollState = rememberScrollState()

    // Filmstrip clips: main video + photo (IMAGE) clips share the V1
    // filmstrip lane, and overlay (OVERLAY) clips get frames too — the
    // overlay rows previously showed no frames because only VIDEO/IMAGE
    // clips were extracted here. Photo clips previously rendered nowhere,
    // leaving the V1 track empty even with a project loaded.
    val filmstripClips = remember(clips) {
        clips.filter {
            it.type == ClipType.VIDEO || it.type == ClipType.IMAGE || it.type == ClipType.OVERLAY
        }
    }

    // ms-per-dp at the current zoom — mirrors VideoTimeline's scaling
    // (54dp/s x zoom, clamped to 24..540dp/s) so extraction windows,
    // cell positions and the ruler all agree.
    val msToDp = remember(state.timelineZoom) {
        ((54.dp * state.timelineZoom.coerceIn(0.5f, 10f))
            .coerceIn(24.dp, 540.dp)).value / 1000f
    }
    // One frame per 64dp of clip width; the ms interval follows zoom.
    val frameIntervalMs = remember(msToDp) {
        (FILMSTRIP_CELL_DP / msToDp).toLong().coerceIn(100L, 5000L)
    }
    // Cell pixel size for extraction (V1 row is 56dp tall; overlay rows
    // reuse the same width at a shorter height — the cache keys on
    // size, so the two don't collide).
    val cellWidthPx = remember(density) {
        (FILMSTRIP_CELL_DP * density.density).roundToInt().coerceAtLeast(32)
    }
    val cellHeightPx = remember(density) {
        (48 * density.density).roundToInt().coerceAtLeast(24)
    }

    // Visible-window filmstrip frames: (clipId, clip-relative ms) ->
    // bitmap. Only the visible window (+1 viewport buffer) is ever
    // held: entries outside it are pruned on every window move, so
    // scrolling a 10-minute clip can't accumulate hundreds of bitmaps.
    // The LruCache + disk cache (TimelineThumbnailCache) own
    // longer-term retention, and frames are re-fetched from them.
    val filmstripFrames = remember { mutableStateMapOf<Pair<String, Long>, Bitmap>() }
    // In-flight extraction keys: touched from up to 4 IO threads at
    // once (launch children remove in finally), so a plain HashSet
    // would corrupt — synchronized set. `add`'s return value is the
    // atomic check-and-claim.
    val inFlightKeys = remember { java.util.Collections.synchronizedSet(mutableSetOf<Pair<String, Long>>()) }
    val thumbDispatcher = remember { Dispatchers.IO.limitedParallelism(4) }

    // Content start (timeline ms) per clip: V1 clips (track 0) tile
    // cumulatively from 0; every other clip sits at timelineOffsetMs
    // (same layout rule the lanes use).
    val contentStarts = remember(filmstripClips) {
        val starts = mutableMapOf<String, Long>()
        var acc = 0L
        filmstripClips.forEach { clip ->
            if (clip.trackIndex == 0 &&
                (clip.type == ClipType.VIDEO || clip.type == ClipType.IMAGE)
            ) {
                starts[clip.id] = acc
                acc += clip.effectiveDurationMs().coerceAtLeast(0L)
            } else {
                starts[clip.id] = clip.timelineOffsetMs
            }
        }
        starts
    }

    // Last-seen trim per clip: frames are keyed by clip-relative
    // time from trimStart, so a trim move makes the old frames stale.
    // The extraction effect invalidates them (below) before it starts
    // collecting, so a re-trim re-fills with the new trim's frames.
    val clipTrims = remember { mutableMapOf<String, Pair<Long, Long>>() }

    // Lazy viewport extraction: on every window move (sampled to
    // ~7/sec), compute the keys the visible window needs, prune keys
    // outside it, and extract the missing ones. collectLatest cancels
    // the previous batch when the window moves again, so a scrolled-
    // away range stops paying for frames it no longer needs.
    LaunchedEffect(filmstripClips, frameIntervalMs) {
        // Trim invalidation FIRST (before collecting): a clip whose
        // trim moved keeps its id but its frames are stale.
        for (clip in filmstripClips) {
            val trim = clip.trimStartMs to clip.trimEndMs
            if (clipTrims[clip.id] != null && clipTrims[clip.id] != trim) {
                filmstripFrames.keys.filter { it.first == clip.id }
                    .forEach { filmstripFrames.remove(it) }
            }
            clipTrims[clip.id] = trim
        }
        (clipTrims.keys - filmstripClips.map { it.id }.toSet())
            .forEach { clipTrims.remove(it) }
        snapshotFlow { timelineScrollState.value to timelineScrollState.viewportSize }
            .sample(150)
            .collectLatest { (scrollPx, viewportPx) ->
                val viewport = if (viewportPx > 0) viewportPx
                    else (360 * density.density).roundToInt()
                val bufferPx = viewport
                val winStartPx = (scrollPx - bufferPx).coerceAtLeast(0)
                val winEndPx = scrollPx + viewportPx + bufferPx
                val msToPx = msToDp * density.density
                coroutineScope {
                    for (clip in filmstripClips) {
                        val startMs = contentStarts[clip.id] ?: continue
                        val clipDur = clip.effectiveDurationMs().coerceAtLeast(0L)
                        val clipStartPx = startMs * msToPx
                        val visStartMs = ((winStartPx - clipStartPx) / msToPx)
                            .toLong().coerceAtLeast(0L)
                        val visEndMs = ((winEndPx - clipStartPx) / msToPx).toLong()
                        // Prune this clip's entries outside the window
                        // (even for off-screen clips).
                        val keepFrom = (visStartMs - frameIntervalMs).coerceAtLeast(0L)
                        val keepTo = visEndMs + frameIntervalMs
                        filmstripFrames.keys
                            .filter { it.first == clip.id &&
                                (it.second < keepFrom || it.second > keepTo) }
                            .forEach { filmstripFrames.remove(it) }
                        if (visEndMs < 0L || visStartMs > clipDur) continue
                        var keyMs = (visStartMs / frameIntervalMs) * frameIntervalMs
                        val endKeyMs = minOf(visEndMs, clipDur)
                        while (keyMs <= endKeyMs) {
                            val key = clip.id to keyMs
                            if (!filmstripFrames.containsKey(key) &&
                                inFlightKeys.add(key)
                            ) {
                                launch(thumbDispatcher) {
                                    try {
                                        val bmp = TimelineThumbnailCache.frameFor(
                                            context, clip, keyMs,
                                            cellWidthPx, cellHeightPx
                                        )
                                        // Don't store a frame whose job
                                        // was cancelled while it was
                                        // extracting (stale trim/window).
                                        ensureActive()
                                        bmp?.let { filmstripFrames[key] = it }
                                    } finally {
                                        inFlightKeys.remove(key)
                                    }
                                }
                            }
                            keyMs += frameIntervalMs
                        }
                    }
                }
            }
    }
    // Drop frames for clips that no longer exist.
    LaunchedEffect(filmstripClips.map { it.id }) {
        val alive = filmstripClips.map { it.id }.toSet()
        filmstripFrames.keys.filter { it.first !in alive }
            .forEach { filmstripFrames.remove(it) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF08080E))
            .padding(top = 2.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Top
    ) {
        // Multi-Layer Video Timeline Component: Horizontal Scrolling, Multi-Layer Tracks (Video, Audio, Overlay), Drag-and-Drop Reordering
        com.apexstudio.app.ui.components.VideoTimeline(
            state = state,
            onScrub = onScrub,
            onSelectClip = onSelectClip,
            onDeselect = onDeselect,
            onReorderClips = onReorderClips,
            onAddMedia = { type -> onAddMedia(type) },
            onSplitClip = onSplitClip,
            onDuplicateClip = onDuplicateClip,
            onDeleteClip = onDeleteClip,
            onZoomChange = { newZoom ->
                onSetZoom(newZoom)
            },
            onOpenTransition = { fromId, toId ->
                onOpenClipTransition(fromId, toId)
            },
            filmstripFrames = filmstripFrames,
            frameIntervalMs = frameIntervalMs,
            onTrimClip = onTrimClip,
            onMoveClipOffset = onSetClipOffset,
            onDeleteClips = onDeleteClips,
            onToggleKeyframeAtPlayhead = onToggleKeyframeAtPlayhead,
            onMoveKeyframe = onMoveKeyframe,
            onToggleTextKeyframeAtPlayhead = onToggleTextKeyframeAtPlayhead,
            onMoveTextKeyframe = onMoveTextKeyframe,
            onToggleStickerKeyframeAtPlayhead = onToggleStickerKeyframeAtPlayhead,
            onMoveStickerKeyframe = onMoveStickerKeyframe,
            onToggleTextMaskKeyframeAtPlayhead = onToggleTextMaskKeyframeAtPlayhead,
            onMoveTextMaskKeyframe = onMoveTextMaskKeyframe,
            onSelectAudioTrack = onSelectAudioTrack,
            onSelectTextOverlay = onSelectTextOverlay,
            onSelectSticker = onSelectSticker,
            onOpenFilter = onOpenFilter,
            onOpenFxPanel = onOpenFx,
            onOpenAdjust = onOpenAdjust,
            onClearFx = onClearFx,
            playerPositionFlow = playerPositionFlow,
            scrollState = timelineScrollState,
            vScrollState = timelineVScrollState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

/**
 * Bottom timeline action row: TIMELINE-ONLY tools. Everything that
 * also exists in the bottom bars was removed from here (duplicates):
 * - Overlay / Effects / Adjust -> main bottom bar (ContextualBottomToolbar)
 * - Transition -> main bottom bar (nearest V1 junction to playhead)
 * - Split -> clip bar (VIDEO clips)
 * - Curves (Keyframe Studio) -> clip bar "Animation" (keyframes belong
 *   to a selected clip; the no-selection shortcut was not a distinct
 *   function, so nothing is lost)
 * - Move <-/-> -> same operation as drag-reorder on the timeline
 *   (reorderClips), which remains
 * - Undo/redo were NEVER added here: they live in the transport row
 *   above the timeline (PlaybackControlBar); duplicating them here
 *   would create the duplicate this cleanup removes.
 *
 * Kept: keyframe nav (prev / +/-KF / next) + timeline zoom — these
 * exist nowhere else. Content padding + edge fades so the first/last
 * chip is never half-cut.
 */
@Composable
fun BottomTimelineActionRow(
    hasKeyframeAtPlayhead: Boolean,
    timelineZoom: Float,
    onToggleKeyframe: () -> Unit,
    onPrevKeyframe: () -> Unit,
    onNextKeyframe: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F0F1A))
            .border(1.dp, Color(0xFF232336), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Scrolling content padding: the first/last chip is fully
            // visible at rest instead of starting half-cut.
            Spacer(Modifier.width(6.dp))
            // Keyframe nav (timeline-only).
            BottomActionChip(
                icon = Icons.Default.FastRewind,
                label = null,
                tint = ApexPalette.NeonCyan,
                onClick = onPrevKeyframe
            )
            BottomActionChip(
                icon = null,
                label = if (hasKeyframeAtPlayhead) "- KF" else "+ KF",
                selected = hasKeyframeAtPlayhead,
                onClick = onToggleKeyframe
            )
            BottomActionChip(
                icon = Icons.Default.FastForward,
                label = null,
                tint = ApexPalette.NeonCyan,
                onClick = onNextKeyframe
            )
            // Timeline zoom (timeline-only).
            BottomActionChip(
                icon = Icons.Default.ZoomOut,
                label = null,
                onClick = onZoomOut
            )
            Text(
                text = String.format(java.util.Locale.US, "%.1fx", timelineZoom),
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 3.dp)
            )
            BottomActionChip(
                icon = Icons.Default.ZoomIn,
                label = null,
                onClick = onZoomIn
            )
            Spacer(Modifier.width(6.dp))
        }
        // Edge fades (no touch handling — gestures pass through to
        // the scrollable Row below).
        Box(
            Modifier.align(Alignment.CenterStart)
                .width(20.dp).fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF0F0F1A), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier.align(Alignment.CenterEnd)
                .width(20.dp).fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xFF0F0F1A))
                    )
                )
        )
    }
}

@Composable
private fun BottomActionChip(
    icon: ImageVector?,
    label: String?,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = Color.White,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = when {
        !enabled -> Color(0xFF555566)
        selected -> ApexPalette.NeonCyan
        else -> tint
    }
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.25f) else Color(0xFF1A1A28))
            .border(
                1.dp,
                if (selected) ApexPalette.NeonCyan else Color(0xFF383852),
                RoundedCornerShape(6.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
        }
        if (label != null) {
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
