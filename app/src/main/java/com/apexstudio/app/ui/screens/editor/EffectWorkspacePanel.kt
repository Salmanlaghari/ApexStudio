package com.apexstudio.app.ui.screens.editor

import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexstudio.app.data.engine.RangeTimeOperation
import com.apexstudio.app.data.engine.RangeTimeRenderer
import com.apexstudio.app.data.engine.RangeTimeRenderResult
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.ClipTimeMapping
import com.apexstudio.app.domain.model.SourceRangeMs
import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.ui.theme.EditorPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

private enum class WorkspaceCategory(val label: String) { VISUAL("Visual"), VFX("VFX"), SPLIT("Split screen"), TIME("Time") }

/** Converts the live project playhead (or state fallback) to this clip's clamped source time. */
internal fun sourceTimeAtProjectPlayhead(
    clips: List<MediaClip>,
    clip: MediaClip,
    liveProjectTimeMs: Long?,
    fallbackProjectTimeMs: Long,
): Long {
    val projectTimeMs = liveProjectTimeMs?.takeIf { it >= 0L } ?: fallbackProjectTimeMs
    val projectClips = clips.ifEmpty { listOf(clip) }
    return ClipTimeMapping.sourceAtProjectTime(projectClips, clip, projectTimeMs)
        .coerceIn(clip.trimStartMs, clip.trimEndMs)
}

/** A contextual bottom workspace, not a replacement for the established editor layout. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun EffectWorkspacePanel(
    projectId: String,
    originalClip: MediaClip,
    vm: EditorViewModel,
    isPlaying: Boolean,
    onPreviewClip: (MediaClip) -> Unit,
    onSeekSource: (Long) -> Unit,
    onTogglePlayback: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val thumbnails by vm.thumbnails.collectAsStateWithLifecycle()
    val editorState by vm.state.collectAsStateWithLifecycle()
    val livePlayerPositionMs by vm.playerPositionFlow.collectAsStateWithLifecycle(
        initialValue = editorState.currentTimeMs,
    )
    var category by remember { mutableStateOf(WorkspaceCategory.VISUAL) }
    var range by remember(originalClip) { mutableStateOf(WorkspaceRange.full(originalClip)) }
    var playbackRangeStart by remember(originalClip.id) { mutableStateOf<Long?>(null) }
    var heldEffectStart by remember { mutableStateOf<Long?>(null) }
    var beforeHold by remember { mutableStateOf<Pair<String?, WorkspaceRange>?>(null) }
    var visualId by remember { mutableStateOf<String?>(null) }
    var intensity by remember(originalClip.id) { mutableFloatStateOf(1f) }
    var rendered by remember { mutableStateOf<RangeTimeRenderResult?>(null) }
    var timeOperation by remember { mutableStateOf<RangeTimeOperation?>(null) }
    var renderJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val ownedFiles = remember { mutableSetOf<String>() }
    DisposableEffect(Unit) {
        onDispose {
            renderJob?.cancel()
            // Only unpublished temporary outputs created by THIS workspace are removed.
            ownedFiles.forEach { uri -> Uri.parse(uri).path?.let { File(it).delete() } }
        }
    }

    fun visualPreview(): MediaClip = visualId?.let { id ->
        originalClip.copy(workspaceEffects = originalClip.workspaceEffects + WorkspaceEffect(
            id = id, startMs = range.startMs, endMs = range.endMs, intensity = intensity
        ))
    } ?: originalClip

    fun resetPreview() {
        vm.setPlaying(false)
        visualId = null
        playbackRangeStart = null
        heldEffectStart = null
        beforeHold = null
        range = WorkspaceRange.full(originalClip)
        rendered = null
        timeOperation = null
        message = null
        onPreviewClip(originalClip)
        onSeekSource(originalClip.trimStartMs)
    }

    fun selectVisual(id: String) {
        vm.setPlaying(false)
        vm.setPlayerError(null)
        playbackRangeStart = null
        visualId = id
        rendered = null
        timeOperation = null
        message = "Preview only · Apply saves this range effect"
        onPreviewClip(visualPreview())
        onSeekSource(range.startMs)
    }

    fun sourceTimeAtPlayhead(): Long {
        val clips = editorState.project?.clips.orEmpty().ifEmpty { listOf(originalClip) }
        return sourceTimeAtProjectPlayhead(
            clips = clips,
            clip = originalClip,
            liveProjectTimeMs = livePlayerPositionMs,
            fallbackProjectTimeMs = editorState.currentTimeMs,
        )
    }

    fun startHeldEffect(id: String) {
        if (busy || rendered != null) return
        val start = sourceTimeAtPlayhead().coerceAtMost(originalClip.trimEndMs - 1L)
        beforeHold = visualId to range
        heldEffectStart = start
        visualId = id
        playbackRangeStart = null
        vm.setPlayerError(null)
        range = WorkspaceRange(start, originalClip.trimEndMs)
        onPreviewClip(visualPreview())
        onSeekSource(start)
        vm.setPlaying(true)
        message = "Recording effect range · release to finish; Apply saves it"
    }

    fun finishHeldEffect(released: Boolean) {
        val start = heldEffectStart ?: return
        val end = sourceTimeAtPlayhead()
        heldEffectStart = null
        vm.setPlaying(false)
        if (released) {
            range = WorkspaceRange.fromPlaybackMarks(originalClip, start, end)
            message = "Held range ${range.startMs}–${range.endMs}ms · Apply saves it"
        } else {
            beforeHold?.let { (previousId, previousRange) -> visualId = previousId; range = previousRange }
            message = "Hold cancelled · previous draft restored"
        }
        beforeHold = null
        onPreviewClip(visualPreview())
        onSeekSource(range.startMs)
    }

    fun markPlaybackRange() {
        val markedAt = sourceTimeAtPlayhead()
        val start = playbackRangeStart
        if (start == null) {
            playbackRangeStart = markedAt
            message = "Range start marked at ${markedAt}ms · play, then hold Play again to mark the end"
        } else {
            range = WorkspaceRange.fromPlaybackMarks(originalClip, start, markedAt)
            playbackRangeStart = null
            vm.setPlaying(false)
            rendered = null
            timeOperation = null
            if (visualId != null) onPreviewClip(visualPreview())
            message = "Playback range selected: ${range.startMs}–${range.endMs}ms"
            onSeekSource(range.startMs)
        }
    }

    fun renderTime(operation: RangeTimeOperation) {
        vm.setPlaying(false)
        val selectedRange = SourceRangeMs(range.startMs - originalClip.trimStartMs, range.endMs - originalClip.trimStartMs)
        val eligibility = vm.rangeTimeEligibility(projectId, originalClip, selectedRange, operation)
        if (!eligibility.isEligible) {
            message = eligibility.reason
            return
        }
        visualId = null
        rendered = null
        timeOperation = null
        onPreviewClip(originalClip)
        busy = true
        message = "Rendering on this device; nothing is uploaded"
        renderJob = scope.launch {
            try {
                val output = RangeTimeRenderer.render(context, originalClip, range.startMs, range.endMs, operation)
                ownedFiles.add(output.uri)
                rendered = output
                timeOperation = operation
                val clip = originalClip.copy(uri = output.uri, durationMs = output.durationMs,
                    trimStartMs = 0L, trimEndMs = output.durationMs, thumbnail = null)
                onPreviewClip(clip)
                onSeekSource(0L)
                message = "Local rendered preview ready · Apply keeps it; Cancel keeps the original"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                message = error.message ?: "This device could not render the effect. No edit was applied."
            } finally {
                busy = false
            }
        }
    }

    val background = EditorPalette.BgDeep
    val foreground = EditorPalette.TextPrimary
    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(horizontal = 12.dp, vertical = 6.dp)) {
            // The title stays pinned while the workspace cards move horizontally.
            Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Effects", fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("Source-timed preview", fontSize = 11.sp, color = EditorPalette.TextSecondary)
            }

            Row(
                Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.width(292.dp).fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = EditorPalette.BgSurface)
                ) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Source range", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Box(Modifier.size(36.dp).combinedClickable(
                                enabled = !busy,
                                onClick = {
                                    if (!isPlaying) onSeekSource(if (rendered != null) 0L else range.startMs)
                                    onTogglePlayback()
                                },
                                onLongClick = ::markPlaybackRange,
                                onLongClickLabel = "Mark playback range start or end"
                            ).testTag("workspace-play-range-control"), contentAlignment = Alignment.Center) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause effect preview · hold to mark range" else "Play effect preview · hold to mark range",
                                    tint = foreground)
                            }
                            IconButton(onClick = ::resetPreview, enabled = !busy &&
                                (visualId != null || rendered != null || playbackRangeStart != null || range != WorkspaceRange.full(originalClip))) {
                                Icon(Icons.Default.Undo, contentDescription = "Undo effect preview", tint = foreground)
                            }
                        }
                        val frames = thumbnails[originalClip.id].orEmpty()
                        if (frames.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(4.dp))) {
                                frames.take(10).forEach { (_, bitmap) ->
                                    Image(bitmap.asImageBitmap(), contentDescription = null,
                                        contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        }
                        Text("${range.startMs}–${range.endMs} ms", fontSize = 11.sp, color = EditorPalette.TextSecondary)
                        val span = (originalClip.trimEndMs - originalClip.trimStartMs).coerceAtLeast(1L).toFloat()
                        RangeSlider(
                            value = ((range.startMs - originalClip.trimStartMs) / span)..((range.endMs - originalClip.trimStartMs) / span),
                            onValueChange = { fractions ->
                                vm.setPlaying(false)
                                playbackRangeStart = null
                                range = WorkspaceRange.fromFractions(originalClip, fractions.start, fractions.endInclusive)
                                if (rendered != null) {
                                    rendered = null
                                    timeOperation = null
                                    onPreviewClip(originalClip)
                                    message = "Range changed · tap a time preset to render again"
                                } else if (visualId != null) onPreviewClip(visualPreview())
                            },
                            onValueChangeFinished = { onSeekSource(range.startMs) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = "Selected source range" }.testTag("workspace-source-range")
                        )
                        if (playbackRangeStart != null) Text("Range start marked · play and hold again for end", fontSize = 10.sp, color = EditorPalette.NeonCyan)
                    }
                }

                Card(
                    modifier = Modifier.width(330.dp).fillMaxHeight().testTag("workspace-presets-card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = EditorPalette.BgSurface)
                ) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Presets", fontSize = 13.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            WorkspaceCategory.entries.forEach { item ->
                                FilterChip(
                                    selected = category == item,
                                    onClick = { if (!busy) category = item },
                                    label = { Text(item.label, color = foreground, fontSize = 10.sp, maxLines = 1) },
                                    modifier = Modifier.testTag("workspace-category-${item.name}")
                                )
                            }
                        }
                        when (category) {
                            WorkspaceCategory.VISUAL -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                items(listOf(
                                    WorkspaceEffectPresets.MONO to "Monochrome",
                                    WorkspaceEffectPresets.WARM to "Warm",
                                    WorkspaceEffectPresets.PIXELATE to "Pixelate",
                                    WorkspaceEffectPresets.CARTOON to "Cartoon",
                                    WorkspaceEffectPresets.BUBBLE to "Bubble",
                                    WorkspaceEffectPresets.HEARTS to "Hearts",
                                    WorkspaceEffectPresets.HEARTBEAT to "Pulse",
                                    WorkspaceEffectPresets.ICE to "Ice"
                                )) { (id, label) ->
                                    WorkspacePresetCard(label, WorkspaceEffectPresets.label(id), visualId == id, !busy,
                                        onHoldStart = { startHeldEffect(id) }, onHoldEnd = ::finishHeldEffect) { selectVisual(id) }
                                }
                            }
                            WorkspaceCategory.VFX -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                items(listOf(
                                    WorkspaceEffectPresets.VFX_CHROMATIC_SPLIT to "RGB Split",
                                    WorkspaceEffectPresets.VFX_SCAN_GLITCH to "Scan Glitch",
                                    WorkspaceEffectPresets.VFX_LIGHT_LEAK to "Light Leak",
                                    WorkspaceEffectPresets.VFX_VIGNETTE to "Vignette",
                                    WorkspaceEffectPresets.VFX_EDGE_GLOW to "Edge Glow"
                                )) { (id, label) ->
                                    WorkspacePresetCard(label, WorkspaceEffectPresets.label(id), visualId == id, !busy,
                                        onHoldStart = { startHeldEffect(id) }, onHoldEnd = ::finishHeldEffect) { selectVisual(id) }
                                }
                            }
                            WorkspaceCategory.SPLIT -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                items(listOf(WorkspaceEffectPresets.SPLIT_2 to "2 tiles", WorkspaceEffectPresets.SPLIT_3 to "3 tiles")) { (id, label) ->
                                    WorkspacePresetCard(label, "Repeats the same source in $label", visualId == id, !busy,
                                        onHoldStart = { startHeldEffect(id) }, onHoldEnd = ::finishHeldEffect) { selectVisual(id) }
                                }
                            }
                            WorkspaceCategory.TIME -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                items(listOf(RangeTimeOperation.SLOW to "Slow 0.5×", RangeTimeOperation.REPEAT to "Repeat ×2", RangeTimeOperation.REVERSE to "Reverse")) { (operation, label) ->
                                    WorkspacePresetCard(label, "Render selected range: $label", timeOperation == operation, !busy) { renderTime(operation) }
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.width(250.dp).fillMaxHeight().testTag("workspace-tuning-card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = EditorPalette.BgSurface)
                ) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (category == WorkspaceCategory.TIME) "Render status" else "Tuning & actions", fontSize = 13.sp)
                        if (category != WorkspaceCategory.TIME) {
                            Text("Intensity  ${(intensity * 100).toInt()}%", fontSize = 11.sp)
                            Slider(
                                value = intensity,
                                onValueChange = { changed ->
                                    intensity = changed
                                    if (visualId != null) onPreviewClip(visualPreview())
                                },
                                valueRange = 0f..1f,
                                enabled = !busy && visualId != null && rendered == null,
                                modifier = Modifier.fillMaxWidth().height(36.dp).testTag("workspace-effect-intensity")
                            )
                        }
                        Text(message ?: when (category) {
                            WorkspaceCategory.TIME -> "Local render: plain single-video projects ≤60s; range ≤10s. Reverse ≤5s / 720p / 150 frames."
                            WorkspaceCategory.SPLIT -> "Repeats the same source in side-by-side tiles; not multi-camera composition."
                            WorkspaceCategory.VFX -> "Source-video GLSL processing with the same range, strength and renderer in preview and export. Not generated media."
                            WorkspaceCategory.VISUAL -> "Tap a preset for the selected range, or hold a preset during playback and release to mark its range. Procedural GLSL, not AI."
                        }, fontSize = 10.sp, lineHeight = 13.sp, color = EditorPalette.TextSecondary)
                        editorState.playerError?.let { Text("Preview unavailable: $it. Apply is disabled; cancel or choose another preset.", color = EditorPalette.Warning, fontSize = 10.sp) }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }

            // Cancel and Apply remain reachable regardless of the active horizontal card.
            Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { renderJob?.cancel(); resetPreview(); onDismiss() }, modifier = Modifier.testTag("workspace-cancel")) { Text("Cancel", color = foreground) }
                Button(enabled = !busy && heldEffectStart == null && editorState.playerError == null && (visualId != null || rendered != null), onClick = {
                    vm.setPlaying(false)
                    val output = rendered
                    val operation = timeOperation
                    val applied = if (output != null && operation != null) {
                        when (val result = vm.applyRangeTimeRender(projectId, originalClip,
                            SourceRangeMs(range.startMs - originalClip.trimStartMs, range.endMs - originalClip.trimStartMs), operation,
                            output)) {
                            is RangeTimeApplyResult.Applied -> { ownedFiles.remove(output.uri); true }
                            is RangeTimeApplyResult.Rejected -> { message = result.eligibility.reason; false }
                        }
                    } else vm.applyWorkspaceEffects(projectId, originalClip, visualPreview().workspaceEffects)
                    if (applied) onDismiss() else if (message == null) message = "The project changed. Close this workspace and select the video again."
                }, modifier = Modifier.testTag("workspace-apply")) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun WorkspacePresetCard(label: String, description: String, selected: Boolean, enabled: Boolean,
    onHoldStart: (() -> Unit)? = null, onHoldEnd: ((Boolean) -> Unit)? = null, onClick: () -> Unit) {
    val currentClick by rememberUpdatedState(onClick)
    val currentStart by rememberUpdatedState(onHoldStart)
    val currentEnd by rememberUpdatedState(onHoldEnd)
    val interaction = if (onHoldStart == null) Modifier.clickable(enabled = enabled, onClick = onClick)
    else Modifier.pointerInput(enabled) {
        if (enabled) {
            var holding = false
            detectTapGestures(
                onTap = { currentClick() },
                onLongPress = { holding = true; currentStart?.invoke() },
                onPress = {
                    val released = tryAwaitRelease()
                    if (holding) { holding = false; currentEnd?.invoke(released) }
                }
            )
        }
    }.semantics {
        if (enabled) onClick { currentClick(); true }
    }
    Column(Modifier.width(88.dp).clip(RoundedCornerShape(10.dp))
        .background(if (selected) EditorPalette.BgElevated else EditorPalette.BgSurface)
        .border(if (selected) 2.dp else 1.dp, if (selected) EditorPalette.NeonCyan else EditorPalette.BorderGlass, RoundedCornerShape(10.dp))
        .then(interaction)
        .semantics(mergeDescendants = true) { contentDescription = description }.padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = EditorPalette.TextPrimary, modifier = Modifier.size(24.dp))
        Text(label, color = EditorPalette.TextPrimary, fontSize = 12.sp, maxLines = 1)
    }
}
