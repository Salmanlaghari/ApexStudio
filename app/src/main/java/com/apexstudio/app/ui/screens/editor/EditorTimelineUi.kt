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
import com.apexstudio.editor.thumbnail.TimelineThumbnailBackfill
import com.apexstudio.editor.thumbnail.TimelineThumbnailSourceKey
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.delay
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

/**
 * State-map cap for the in-memory filmstrip: ~512 frames (≈1.8 MB of
 * 176×132 ARGB bitmaps) is an hour-plus of thumbnails at the default
 * zoom. Beyond this the extraction loop drops the entries farthest
 * from the visible window. Longer content is always recovered from
 * TimelineThumbnailCache (32 MB LRU + 128 MB disk) on scroll
 */
private const val MAX_STRIP_FRAMES = 512

@Composable
fun TimelineTrackArea(
    state: com.apexstudio.app.presentation.state.EditorState,
    onScrub: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    // Tapping empty timeline space clears the selection (hides the clip
    // action bar). Wired to VideoTimeline's background tap.
    onDeselect: () -> Unit,
    onAddMedia: (ClipType) -> Unit,
    onSplitClip: (clipId: String, atMs: Long) -> Unit,
    onDuplicateClip: (clipId: String) -> Unit,
    onDeleteClip: (clipId: String) -> Unit,
    onDeleteClips: (Set<String>) -> Unit,
    onReorderClips: (fromIndex: Int, toIndex: Int) -> Unit,
    onSetClipOffset: (clipId: String, offsetMs: Long) -> Unit,
    onTrimClip: (clipId: String, startMs: Long, endMs: Long) -> Unit,
    onToggleKeyframeAtPlayhead: (clipId: String) -> Unit,
    onMoveKeyframe: (clipId: String, keyframeId: String, newTimeMs: Long) -> Unit,
    onToggleTextKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit,
    onMoveTextKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit,
    onToggleStickerKeyframeAtPlayhead: (stickerId: String) -> Unit,
    onMoveStickerKeyframe: (stickerId: String, keyframeId: String, newTimeMs: Long) -> Unit,
    onToggleTextMaskKeyframeAtPlayhead: (clipId: String, overlayId: String) -> Unit,
    onMoveTextMaskKeyframe: (clipId: String, overlayId: String, keyframeId: String, newTimeMs: Long) -> Unit,
    onSetZoom: (Float) -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenTrim: () -> Unit,
    onOpenFx: () -> Unit,
    onOpenFilter: () -> Unit,
    onOpenAdjust: () -> Unit,
    onSelectTextOverlay: (clipId: String, overlayId: String) -> Unit,
    onSelectSticker: (String?) -> Unit,
    onClearFx: () -> Unit,
    onOpenClipTransition: (fromClipId: String, toClipId: String) -> Unit,
    onSelectAudioTrack: (String) -> Unit,
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

    val extractionSources = filmstripClips.map { TimelineThumbnailSourceKey.from(it) }
    val latestContentStarts by rememberUpdatedState(contentStarts)
    // URI replacement and speed edits also change the source frame behind
    // a clip-relative key. Canvas movement/opacity do not, and must not
    // cancel in-flight decodes after their backfill cursor has advanced.
    val clipSources = remember { mutableMapOf<String, TimelineThumbnailSourceKey>() }
    // Persistent full-clip backfill cursor, clipId -> next ms to scan.
    val backfillCursor = remember { HashMap<String, Long>() }
    var previousFrameIntervalMs by remember { mutableStateOf(frameIntervalMs) }
    // FULL-LENGTH FILMSTRIP EXTRACTION LOOP. Every pass recomputes the
    // visible window, launches the keys the window needs FIRST, then the
    // rest of each clip (leading/trailing remainder) so the whole video
    // converges to one thumbnail per interval. Jobs run on the IO
    // dispatcher and are never awaited by this coroutine, so a long clip
    // fills in the background while the UI stays responsive.
    LaunchedEffect(extractionSources, frameIntervalMs) {
        if (previousFrameIntervalMs != frameIntervalMs) {
            backfillCursor.clear()
            previousFrameIntervalMs = frameIntervalMs
        }
        for (clip in filmstripClips) {
            val source = TimelineThumbnailSourceKey.from(clip)
            if (clipSources[clip.id] != null && clipSources[clip.id] != source) {
                filmstripFrames.keys.filter { it.first == clip.id }
                    .forEach { filmstripFrames.remove(it) }
                backfillCursor.remove(clip.id)
            }
            clipSources[clip.id] = source
        }
        (clipSources.keys - filmstripClips.map { it.id }.toSet())
            .forEach { clipSources.remove(it); backfillCursor.remove(it) }
        // FULL-LENGTH FILMSTRIP: visible window first, then the REST of
        // every clip. The old implementation wrapped extraction in
        // `snapshotFlow{...}.sample(150).collectLatest { ... }` — every
        // scroll tick CANCELLED the whole in-flight batch, so a batch for
        // a deep window was always restarted before it could finish and
        // long clips stayed stuck on their first seconds (~8s of frames).
        // This loop is never cancelled by scrolling (only by a key
        // change: new/edited clip = new list = restart), re-prioritises
        // the on-screen window every pass, and back-fills the remainder
        // of each clip in the background on the IO dispatcher.
        while (true) {
            val scrollPx = timelineScrollState.value
            val viewportPx = timelineScrollState.viewportSize
            val viewport = if (viewportPx > 0) viewportPx
                else (360 * density.density).roundToInt()
            val winStartPx = (scrollPx - viewport).coerceAtLeast(0)
            val winEndPx = scrollPx + viewport + viewport
            val msToPx = msToDp * density.density
            var launched = 0
            var pending = 0

            // Launch ONE filmstrip key if capacity allows. Shared by the
            // window pass and the full-clip sweep, so there is exactly one
            // claim rule (synchronized in-flight set, 24 jobs per pass).
            fun requestKey(clip: com.apexstudio.app.domain.model.MediaClip, keyMs: Long) {
                val key = clip.id to keyMs
                if (filmstripFrames.containsKey(key) || inFlightKeys.contains(key)) return
                pending++
                if (launched >= 24 || !inFlightKeys.add(key)) return
                launched++
                // Immutable captures only: `keyMs` is a moving counter in the
                // caller's loop and `clip` would be re-read on the next pass,
                // so both must be frozen for the job.
                val targetClip = clip
                val frameAtMs = keyMs
                launch(thumbDispatcher) {
                    try {
                        val bmp = TimelineThumbnailCache.frameFor(
                            context, targetClip, frameAtMs,
                            cellWidthPx, cellHeightPx
                        )
                        ensureActive()
                        bmp?.let { filmstripFrames[key] = it }
                    } finally {
                        inFlightKeys.remove(key)
                    }
                }
            }
            for (clip in filmstripClips) {
                val startMs = latestContentStarts[clip.id] ?: continue
                val clipDur = clip.effectiveDurationMs().coerceAtLeast(0L)
                val clipStartPx = startMs * msToPx
                val visStartMs = ((winStartPx - clipStartPx) / msToPx)
                    .toLong().coerceAtLeast(0L)
                val visEndMs = ((winEndPx - clipStartPx) / msToPx).toLong()
                val onScreen = !(visEndMs < 0L || visStartMs > clipDur)
                // PRIORITY 1 — the visible window: bounded (a couple of
                // viewports), so the on-screen strip always fills first.
                val winFrom = ((visStartMs / frameIntervalMs) * frameIntervalMs)
                    .coerceIn(0L, clipDur)
                val winTo = visEndMs.coerceIn(0L, clipDur)
                if (onScreen && winFrom <= winTo) {
                    var windowKey = winFrom
                    while (windowKey <= winTo) {
                        requestKey(clip, windowKey)
                        windowKey += frameIntervalMs
                    }
                }
                // PRIORITY 2 — the whole clip, resumed from the persistent
                // cursor. Never plan more keys than the shared 24-job pass
                // budget: the old loop scanned 96 keys while requestKey
                // silently rejected everything after launch 24, then parked
                // the cursor past those unrequested timestamps. At close zoom
                // this permanently left the strip blank after ~8 seconds.
                val cursor = backfillCursor[clip.id] ?: 0L
                val backfillKeys = TimelineThumbnailBackfill.batch(
                    cursorMs = cursor,
                    durationMs = clipDur,
                    intervalMs = frameIntervalMs,
                    maxKeys = 24 - launched
                )
                backfillKeys.forEach { requestKey(clip, it) }
                // A cursor past clipDur means "done"; an empty batch due to a
                // full pass budget keeps the cursor unchanged for next pass.
                val nextCursor = backfillKeys.lastOrNull()
                    ?.plus(frameIntervalMs)
                    ?: cursor
                backfillCursor[clip.id] =
                    if (nextCursor > clipDur) clipDur + frameIntervalMs else nextCursor
            }
            // Bounded memory: only prune when the strip grows past the
            // cap, and then only the entries FARTHEST from the window —
            // scrolling away no longer instantly destroys the frames the
            // user just generated, but a long project still cannot pin
            // hundreds of bitmaps forever.
            if (filmstripFrames.size > MAX_STRIP_FRAMES) {
                val winMid = (winStartPx + winEndPx) / 2.0
                val overflow = filmstripFrames.size - MAX_STRIP_FRAMES
                filmstripFrames.keys
                    .mapNotNull { k ->
                        val s = latestContentStarts[k.first] ?: return@mapNotNull null
                        val px = (s + k.second) * msToPx
                        k to kotlin.math.abs(px - winMid)
                    }
                    .filter { (_, dist) -> dist > viewport * 2.0 }
                    .sortedByDescending { (_, dist) -> dist }
                    .take(overflow)
                    .forEach { (k, _) -> filmstripFrames.remove(k) }
            }
            // Pace: busy while work remains, slow to a crawl when the
            // window is already drawn — the back-fill then still ticks
            // once every ~400ms until the whole clip is covered.
            delay(if (pending > 0) 40L else 400L)
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
            onOpenSpeed = onOpenSpeed,
            onOpenTrim = onOpenTrim,
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
