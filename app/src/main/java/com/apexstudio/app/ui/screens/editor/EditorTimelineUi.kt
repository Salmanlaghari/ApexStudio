package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.media.ThumbnailExtractor
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// === 4. CAPCUT-STYLE TIMELINE TRACK AREA ===

/**
 * Extract per-second filmstrip thumbnails for a clip's trimmed range via
 * [ThumbnailExtractor.extractPerSecondFrames] (MediaMetadataRetriever,
 * off the main thread). Photo clips get a single decoded frame.
 *
 * Keyed by second (0, 1, 2, …) so the V1 lane renders one cell per
 * second, each exactly 1s wide — frames scroll and scale with zoom
 * exactly like the ruler and playhead. The extractor samples evenly
 * across the FULL trimmed range (capped decoder calls) and expands to
 * a dense map, so long clips show real frames across their whole
 * length, not just the first [maxSeconds].
 *
 * [onFrame] is invoked per decoded frame (progressive fill) — the
 * caller merges it into the lane immediately instead of waiting for
 * the whole set.
 */
private suspend fun extractClipThumbnails(
    context: android.content.Context,
    clip: com.apexstudio.app.domain.model.MediaClip,
    onFrame: (suspend (second: Int, bitmap: Bitmap) -> Unit)? = null
): Map<Int, Bitmap> {
    // OVERLAY clips can be image-backed too (overlay picker allows
    // ImageAndVideo). Try the still loader first for them as well;
    // if it fails, fall through to video-frame extraction below.
    if (clip.type == ClipType.IMAGE || clip.type == ClipType.OVERLAY) {
        val photo = com.apexstudio.app.data.photoedit.PhotoEditRenderer.loadBitmap(
            context, clip.uri, maxDim = 240
        )
        if (photo != null) return mapOf(0 to photo)
        if (clip.type == ClipType.IMAGE) {
            // Second chance: MediaMetadataRetriever can decode a still
            // (JPEG/PNG) as a frame on many devices, so a loadBitmap
            // failure (transient decode/OOM) must not strand the clip
            // on placeholders — fall through to the retriever path
            // below instead of returning empty here.
        }
    }
    val trimStart = clip.trimStartMs
    val trimEnd =
        if (clip.trimEndMs > trimStart) clip.trimEndMs else clip.durationMs.coerceAtLeast(1000L)
    return ThumbnailExtractor.extractPerSecondFrames(
        context = context,
        uri = clip.uri,
        trimStartMs = trimStart,
        trimEndMs = trimEnd,
        frameWidthPx = 120,
        frameHeightPx = 80,
        maxSeconds = 180,
        onFrame = onFrame
    )
}

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
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onResetZoom: () -> Unit = {},
    onSetZoom: (Float) -> Unit = {},
    onOpenSpeed: () -> Unit = {},
    onOpenAudio: () -> Unit = {},
    onOpenTrim: () -> Unit = {},
    onOpenText: () -> Unit = {},
    onOpenStickers: () -> Unit = {},
    onOpenFx: () -> Unit = {},
    onOpenVoice: () -> Unit = {},
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
    // Multi-layer video tracks (V1..V10) + 60fps playhead flow.
    onToggleLayerVisibility: (Int) -> Unit = {},
    onToggleLayerLock: (Int) -> Unit = {},
    onDeleteLayer: (Int) -> Unit = {},
    onAddLayer: () -> Unit = {},
    onMoveClipToLayer: (String, Int) -> Unit = { _, _ -> },
    onAddClipToLayer: (Int) -> Unit = {},
    playerPositionFlow: kotlinx.coroutines.flow.StateFlow<Long>? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    // Per-clip filmstrip thumbnails, extracted once per clip (keyed by
    // id/uri/trim), cached in this map. A per-second generated fallback
    // is seeded first so the lane never shows a dark strip; the real
    // frames replace it when ready.
    //
    // Bitmaps are RECYCLED when replaced or when their clip leaves the
    // project: without this, every trim (which re-extracts) leaked the
    // old set's native memory, and removed clips' bitmaps lingered
    // until GC finalization. The map is bounded PER CLIP by the
    // extractor (≤180 frames); it intentionally holds every live clip
    // (evicting a visible clip would blank its row), so there is no
    // global eviction — the bound is per-clip.
    val thumbnailsByClip = remember { mutableStateMapOf<String, Map<Int, Bitmap>>() }
    filmstripClips.forEach { clip ->
        LaunchedEffect(clip.id, clip.uri, clip.trimStartMs, clip.trimEndMs) {
            // Bitmaps seeded as the placeholder set for this clip, so a
            // progressive real frame can recycle exactly the placeholder
            // it replaces. Placeholders are DISTINCT bitmaps (one per
            // cell), so per-frame recycling is safe for them.
            val fallbackBitmaps = mutableSetOf<Bitmap>()
            // On re-extraction (e.g. trim change) the existing map is a
            // DENSE map whose bitmaps are ALIASED (one instance under
            // several seconds) — never recycle those one-by-one while
            // other cells still display the same instance. Snapshot
            // them and recycle the whole set only once the new map has
            // fully replaced the old one.
            val previousBitmaps = thumbnailsByClip[clip.id]?.values?.toSet() ?: emptySet()
            if (!thumbnailsByClip.containsKey(clip.id)) {
                val clipSeconds = ((clip.trimEndMs - clip.trimStartMs)
                    .coerceAtLeast(1000L) / 1000L).toInt().coerceIn(1, 180)
                val seeded = com.apexstudio.app.data.media.ThumbnailExtractor
                    .generateFilmstripFrames(clipSeconds, 120, 80, clip.uri.hashCode().toLong())
                    .mapIndexed { i, b -> i to b }.toMap()
                fallbackBitmaps += seeded.values
                thumbnailsByClip[clip.id] = seeded
            }
            try {
                // Bounded retry: a single transient extraction failure
                // (retriever race, file still copying, grant timing)
                // used to strand the clip on teal placeholders forever
                // — the LaunchedEffect keys never change, so nothing
                // ever re-ran the extraction. extractClipThumbnails
                // returns an empty map on failure (never disguised
                // placeholders), so retry up to 3 attempts with
                // back-off before giving up and keeping placeholders.
                var real: Map<Int, Bitmap> = emptyMap()
                var attempt = 0
                while (real.isEmpty() && attempt < 3) {
                    if (attempt > 0) {
                        kotlinx.coroutines.delay(if (attempt == 1) 1500L else 3000L)
                    }
                    attempt++
                    try {
                        real = extractClipThumbnails(context, clip) { second, bitmap ->
                            withContext(Dispatchers.Main) {
                                val current = thumbnailsByClip[clip.id] ?: emptyMap()
                                val old = current[second]
                                thumbnailsByClip[clip.id] = current + (second to bitmap)
                                // Recycle a replaced placeholder, never a real
                                // frame (dense-map aliases share instances).
                                if (old != null && old !== bitmap && fallbackBitmaps.remove(old)) {
                                    if (!old.isRecycled) old.recycle()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("TimelineTrackArea", "Thumbnail extraction attempt $attempt failed for ${clip.id}: ${e.message}")
                    }
                }
                if (real.isNotEmpty()) {
                    // Wholesale replace with the complete new dense map
                    // (a shorter re-trim must not leave stale old
                    // seconds behind). The progressive pass already put
                    // the same new instances in for sampled seconds, so
                    // this only adds the dense-expansion tail. After
                    // the swap, no cell references the previous
                    // bitmaps, so the whole previous set can be
                    // recycled — as can any placeholder never replaced.
                    thumbnailsByClip[clip.id] = real
                    // Defensive identity guard (dense-map aliasing): only
                    // recycle previous bitmaps that are NOT also in the
                    // new map. The extractor always returns fresh
                    // instances, so this set is normally everything —
                    // but recycling an instance that is still displayed
                    // would blank live cells, so never rely on that.
                    val newInstances = real.values.toSet()
                    previousBitmaps.filter { it !in newInstances }
                        .forEach { if (!it.isRecycled) it.recycle() }
                    // Recycle any placeholder never replaced by a real frame.
                    fallbackBitmaps.forEach { if (!it.isRecycled) it.recycle() }
                    fallbackBitmaps.clear()
                }
                // If extraction failed (real empty), the placeholders
                // stay displayed — never recycle them.
            } catch (e: Exception) {
                Log.w("TimelineTrackArea", "Thumbnail extraction failed for ${clip.id}: ${e.message}")
            }
        }
    }
    // Drop thumbnails for clips that no longer exist (recycling them).
    LaunchedEffect(filmstripClips.map { it.id }) {
        val alive = filmstripClips.map { it.id }.toSet()
        val removed = thumbnailsByClip.keys - alive
        removed.forEach { id ->
            thumbnailsByClip.remove(id)?.values?.forEach { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
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
            thumbnailsByClip = thumbnailsByClip,
            onTrimClip = onTrimClip,
            onMoveClipOffset = onSetClipOffset,
            onDeleteClips = onDeleteClips,
            onToggleKeyframeAtPlayhead = onToggleKeyframeAtPlayhead,
            onMoveKeyframe = onMoveKeyframe,
            onToggleTextKeyframeAtPlayhead = onToggleTextKeyframeAtPlayhead,
            onMoveTextKeyframe = onMoveTextKeyframe,
            onSelectAudioTrack = onSelectAudioTrack,
            onSelectTextOverlay = onSelectTextOverlay,
            onSelectSticker = onSelectSticker,
            onOpenText = onOpenText,
            onOpenStickers = onOpenStickers,
            onOpenFilter = onOpenFilter,
            onOpenFxPanel = onOpenFx,
            onOpenAdjust = onOpenAdjust,
            onOpenVoice = onOpenVoice,
            onAddAudioTrack = { onAddMedia(ClipType.AUDIO) },
            onClearFx = onClearFx,
            onToggleLayerVisibility = onToggleLayerVisibility,
            onToggleLayerLock = onToggleLayerLock,
            onDeleteLayer = onDeleteLayer,
            onAddLayer = onAddLayer,
            onMoveClipToLayer = onMoveClipToLayer,
            onAddClipToLayer = onAddClipToLayer,
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
 * Bottom timeline action row: EVERY function that used to stack above
 * the timeline now lives in this single horizontally-scrollable line
 * BELOW the timeline (plus the Adjust quick-chips line above it):
 * Overlay / Transition / Effects (all acting AT THE PLAYHEAD, same
 * callbacks as the old centre-add row), Curves (Keyframe Studio),
 * Split, Adjust, keyframe nav (±KF / prev / next), clip move ←/→,
 * and timeline zoom. No function lost — only relocated.
 */
@Composable
fun BottomTimelineActionRow(
    hasKeyframeAtPlayhead: Boolean,
    timelineZoom: Float,
    canSplit: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canAddTransition: Boolean,
    onToggleKeyframe: () -> Unit,
    onPrevKeyframe: () -> Unit,
    onNextKeyframe: () -> Unit,
    onSplit: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onOpenKeyframes: () -> Unit,
    onAddOverlay: () -> Unit,
    onOpenTransition: () -> Unit,
    onOpenEffects: () -> Unit,
    onOpenAdjust: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F0F1A))
            .border(1.dp, Color(0xFF232336), RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Add-at-playhead group (was the centre-add row).
        BottomActionChip(
            icon = Icons.Default.AddPhotoAlternate,
            label = "Overlay",
            onClick = onAddOverlay
        )
        BottomActionChip(
            icon = Icons.Default.Transform,
            label = "Transition",
            enabled = canAddTransition,
            onClick = onOpenTransition
        )
        BottomActionChip(
            icon = Icons.Default.AutoFixHigh,
            label = "Effects",
            onClick = onOpenEffects
        )
        BottomActionChip(
            icon = Icons.Default.Tune,
            label = "Adjust",
            onClick = onOpenAdjust
        )
        // Curves (Keyframe Studio) + Split (was the quick-action row).
        BottomActionChip(
            icon = Icons.Default.Animation,
            label = "Curves",
            tint = ApexPalette.NeonCyan,
            onClick = onOpenKeyframes
        )
        BottomActionChip(
            icon = Icons.Default.ContentCut,
            label = "Split",
            enabled = canSplit,
            onClick = onSplit
        )
        // Keyframe nav.
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
        // Clip move.
        if (canMoveLeft) {
            BottomActionChip(
                icon = Icons.Default.ArrowBack,
                label = null,
                onClick = onMoveLeft
            )
        }
        if (canMoveRight) {
            BottomActionChip(
                icon = Icons.Default.ArrowForward,
                label = null,
                onClick = onMoveRight
            )
        }
        // Timeline zoom.
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


@Composable
private fun TrackSidebarCell(
    badge: String,
    label: String,
    icon: ImageVector,
    tint: Color,
    height: androidx.compose.ui.unit.Dp,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) tint.copy(alpha = 0.25f) else Color(0xFF141420))
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) tint else tint.copy(alpha = 0.45f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) tint else tint.copy(alpha = 0.25f))
                        .padding(horizontal = 2.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        text = badge,
                        color = if (isSelected) Color.Black else tint,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) tint else tint.copy(alpha = 0.9f),
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = label,
                color = if (isSelected) tint else Color.White.copy(alpha = 0.85f),
                fontSize = 7.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
