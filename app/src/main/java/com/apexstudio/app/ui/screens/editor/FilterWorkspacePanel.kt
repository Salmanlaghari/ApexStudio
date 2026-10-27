package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexstudio.app.data.effect.WorkspaceFilterThumbnailGenerator
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import com.apexstudio.app.domain.model.WorkspaceFilterPresets
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.applyWorkspaceEffects
import com.apexstudio.app.presentation.viewmodel.setPlaying
import com.apexstudio.app.presentation.viewmodel.setPlayerError
import com.apexstudio.app.ui.theme.EditorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A compact, local-only color-look workspace. Drafts are preview-only until the single Apply boundary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterWorkspacePanel(
    projectId: String,
    originalClip: MediaClip,
    vm: EditorViewModel,
    isPlaying: Boolean,
    onPreviewClip: (MediaClip) -> Unit,
    onSeekSource: (Long) -> Unit,
    onTogglePlayback: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val catalog = WorkspaceFilterPresets.all
    var filterSampleBitmaps by remember(originalClip.id) { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    val filterSampleSourceMs = remember(originalClip.id) {
        sourceTimeAtProjectPlayhead(
            clips = state.project?.clips.orEmpty(),
            clip = originalClip,
            liveProjectTimeMs = null,
            fallbackProjectTimeMs = state.currentTimeMs,
        )
    }
    LaunchedEffect(originalClip.id, filterSampleSourceMs) {
        val sampleFrame = withContext(Dispatchers.IO) {
            runCatching {
                if (originalClip.uri.isBlank()) null else {
                    val playable = com.apexstudio.app.data.media.MediaUriResolver
                        .resolvePlayableUri(context, originalClip.uri)
                    val missingLocalFile = playable.scheme == "file" &&
                        !java.io.File(playable.path.orEmpty()).isFile
                    if (missingLocalFile) null else {
                        com.apexstudio.app.data.media.VideoThumbnailExtractor
                            .extractFrame(context, playable.toString(), filterSampleSourceMs)
                    }
                }
            }.getOrNull()
        }
        filterSampleBitmaps = WorkspaceFilterThumbnailGenerator.generate(
            context = context,
            source = sampleFrame,
            presetIds = catalog.map { it.id },
        )
    }
    val firstExisting = remember(originalClip) { originalClip.workspaceEffects.indexOfFirst { it.id in WorkspaceEffectPresets.filterIds }.takeIf { it >= 0 } }
    var effects by remember(originalClip) { mutableStateOf(originalClip.workspaceEffects) }
    var selectedIndex by remember(originalClip) { mutableStateOf(firstExisting) }
    var selectedId by remember(originalClip) {
        mutableStateOf(firstExisting?.let { originalClip.workspaceEffects[it].id } ?: catalog.first().id)
    }
    val initial = firstExisting?.let { originalClip.workspaceEffects[it] }
    var intensity by remember(originalClip) { mutableFloatStateOf(initial?.intensity?.coerceIn(0f, 1f) ?: 0.78f) }
    var exposure by remember(originalClip) { mutableFloatStateOf(initial?.exposure?.coerceIn(-2f, 2f) ?: 0f) }
    var contrast by remember(originalClip) { mutableFloatStateOf(initial?.contrast?.coerceIn(0.5f, 1.5f) ?: 1f) }
    var saturation by remember(originalClip) { mutableFloatStateOf(initial?.saturation?.coerceIn(0f, 2f) ?: 1f) }
    var customizeExpanded by remember(originalClip) { mutableStateOf(false) }
    var startMs by remember(originalClip) { mutableLongStateOf(initial?.startMs ?: originalClip.trimStartMs) }
    var endMs by remember(originalClip) { mutableLongStateOf(initial?.endMs ?: originalClip.trimEndMs) }

    val currentProject = state.project
    val persistedClip = currentProject?.clips?.firstOrNull { it.id == originalClip.id }
    val targetCurrent = currentProject?.id == projectId && state.selectedClipId == originalClip.id && persistedClip == originalClip
    val isMainVideo = originalClip.type == ClipType.VIDEO && originalClip.trackIndex == 0 && originalClip.uri.isNotBlank()
    val enabled = targetCurrent && isMainVideo
    val disabledReason = when {
        !isMainVideo -> "Filters can only be applied to a main-track video. Overlay/PiP filters are disabled because the overlay renderer does not consume these effects yet."
        !targetCurrent -> "This clip changed or is no longer selected. Close and reopen Filters to avoid applying to a stale target."
        else -> null
    }

    LaunchedEffect(effects, enabled) {
        onPreviewClip(if (enabled) originalClip.copy(workspaceEffects = effects) else originalClip)
    }

    fun updateSelected(updated: WorkspaceEffect) {
        val idx = selectedIndex
        effects = if (idx != null && idx in effects.indices) effects.toMutableList().also { it[idx] = updated }
        else effects + updated
        selectedIndex = idx ?: effects.lastIndex
    }

    fun currentDraft() = WorkspaceEffect(selectedId, startMs, endMs, intensity, exposure, contrast, saturation)

    fun previewPreset(id: String) {
        vm.setPlaying(false)
        vm.setPlayerError(null)
        selectedId = id
        val currentIdx = selectedIndex
        val entry = currentDraft().copy(id = id)
        effects = if (currentIdx != null && currentIdx in effects.indices) {
            effects.toMutableList().also { it[currentIdx] = entry }
        } else effects + entry
        selectedIndex = currentIdx ?: effects.lastIndex
    }

    fun selectSavedFilter(index: Int) {
        val effect = effects.getOrNull(index) ?: return
        if (effect.id !in WorkspaceEffectPresets.filterIds) return
        vm.setPlaying(false)
        selectedIndex = index
        selectedId = effect.id
        intensity = effect.intensity.coerceIn(0f, 1f)
        exposure = effect.exposure.coerceIn(-2f, 2f)
        contrast = effect.contrast.coerceIn(0.5f, 1.5f)
        saturation = effect.saturation.coerceIn(0f, 2f)
        startMs = effect.startMs.coerceIn(originalClip.trimStartMs, originalClip.trimEndMs - 1L)
        endMs = effect.endMs.coerceIn(startMs + 1L, originalClip.trimEndMs)
    }

    val sourceSpan = (originalClip.trimEndMs - originalClip.trimStartMs).coerceAtLeast(1L)
    val validRange = originalClip.trimEndMs > originalClip.trimStartMs
    val dirty = effects != originalClip.workspaceEffects
    var category by remember { mutableStateOf("All") }
    val additions = com.apexstudio.app.domain.model.WorkspaceFilterPresetAdditions
    val categories = remember { listOf("All", "Originals") + additions.entries.map { it.group }.distinct() }
    val categoryCatalog = catalog.filter { preset ->
        category == "All" || if (category == "Originals") additions.find(preset.id) == null
        else additions.find(preset.id)?.group == category
    }
    val background = EditorPalette.BgDeep
    val foreground = EditorPalette.TextPrimary
    val density = LocalDensity.current
    val compactPhone = LocalConfiguration.current.screenWidthDp < 600
    val panelMaxHeight = if (compactPhone) 256.dp else 300.dp
    val workspaceScroll = remember(originalClip.id, density) {
        ScrollState(initial = with(density) { 320.dp.roundToPx() })
    }

    if (compactPhone) {
        var mobileTuningExpanded by remember(originalClip.id) { mutableStateOf(false) }
        Surface(
            color = background,
            contentColor = foreground,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            modifier = Modifier.testTag("filter-workspace-panel").heightIn(max = panelMaxHeight)
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = panelMaxHeight)
                .padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filters", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onPreviewClip(originalClip); onDismiss() },
                        modifier = Modifier.testTag("filter-close")) {
                        Icon(Icons.Default.Close, contentDescription = "Close filters", tint = foreground)
                    }
                }

                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Looks", fontSize = 13.sp)
                    if (disabledReason != null) {
                        Text(disabledReason, color = EditorPalette.Warning, fontSize = 10.sp,
                            lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().testTag("filter-look-categories")) {
                        items(categories) { name ->
                            FilterChip(selected = category == name, onClick = { category = name },
                                label = { Text(name, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EditorPalette.NeonPurple.copy(alpha = 0.18f),
                                    selectedLabelColor = EditorPalette.NeonPurple
                                ), modifier = Modifier.testTag("filter-look-category-$name"))
                        }
                    }
                    Column(Modifier.fillMaxWidth().testTag("filter-presets-card")) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                            items(categoryCatalog, key = { it.id }) { preset ->
                                val selected = selectedId == preset.id
                                Column(
                                    Modifier.width(70.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) EditorPalette.BgElevated else EditorPalette.BgDeep)
                                        .border(if (selected) 2.dp else 1.dp,
                                            if (selected) EditorPalette.NeonPurple else EditorPalette.BorderGlass,
                                            RoundedCornerShape(10.dp))
                                        .testTag("filter-preset-${preset.id}")
                                        .clickable(enabled = enabled && validRange) { previewPreset(preset.id) }
                                        .semantics { contentDescription = "${preset.label}: ${preset.description}" }
                                        .padding(horizontal = 4.dp, vertical = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val sample = filterSampleBitmaps[preset.id]
                                    if (sample != null) {
                                        Image(bitmap = sample, contentDescription = "${preset.label} sample preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                                .clip(RoundedCornerShape(6.dp)).testTag("filter-sample-${preset.id}"))
                                    } else {
                                        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(6.dp))
                                            .background(Color(preset.swatchArgb))) {
                                            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)))
                                        }
                                    }
                                    Text(preset.label, color = foreground, fontSize = 10.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    Text("${catalog.size} looks · ${categoryCatalog.size} shown",
                        fontSize = 10.sp, color = EditorPalette.TextSecondary)

                    TextButton(onClick = { mobileTuningExpanded = !mobileTuningExpanded },
                        modifier = Modifier.fillMaxWidth().height(28.dp).testTag("filter-details-toggle")) {
                        Text(if (mobileTuningExpanded) "Hide range & adjustments" else "Range & adjustments",
                            fontSize = 11.sp, color = EditorPalette.TextSecondary)
                    }
                    if (mobileTuningExpanded) {
                    Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().testTag("filter-source-card")) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Source range  ${startMs}–${endMs} ms", fontSize = 11.sp,
                                color = EditorPalette.TextSecondary, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                if (!isPlaying) onSeekSource(startMs)
                                onTogglePlayback()
                            }, enabled = enabled, modifier = Modifier.testTag("filter-play-preview")) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause filter preview" else "Play filter preview",
                                    tint = foreground)
                            }
                            IconButton(onClick = {
                                effects = originalClip.workspaceEffects
                                val idx = effects.indexOfFirst { it.id in WorkspaceEffectPresets.filterIds }.takeIf { it >= 0 }
                                selectedIndex = idx
                                idx?.let { selectSavedFilter(it) } ?: run {
                                    selectedId = catalog.first().id
                                    intensity = 0.78f; exposure = 0f; contrast = 1f; saturation = 1f
                                    startMs = originalClip.trimStartMs; endMs = originalClip.trimEndMs
                                }
                            }, enabled = dirty || selectedIndex != firstExisting,
                                modifier = Modifier.testTag("filter-undo")) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo filter draft", tint = foreground)
                            }
                        }
                        val filterEffects = effects.withIndex().filter { it.value.id in WorkspaceEffectPresets.filterIds }
                        if (filterEffects.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                items(filterEffects, key = { "${it.index}-${it.value.id}" }) { indexed ->
                                    val preset = WorkspaceFilterPresets.find(indexed.value.id)
                                    AssistChip(onClick = { selectSavedFilter(indexed.index) },
                                        label = { Text("${preset?.label ?: indexed.value.id}  ${indexed.value.startMs}–${indexed.value.endMs}ms", maxLines = 1) },
                                        leadingIcon = { Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp))
                                            .background(Color(preset?.swatchArgb ?: 0xFF8C91A0))) },
                                        modifier = Modifier.testTag("filter-saved-${indexed.index}"))
                                }
                            }
                        }
                        TextButton(onClick = {
                            selectedIndex = null
                            intensity = 0.78f; exposure = 0f; contrast = 1f; saturation = 1f
                            startMs = originalClip.trimStartMs; endMs = originalClip.trimEndMs
                        }, enabled = enabled, modifier = Modifier.testTag("filter-new-range")) {
                            Text(if (selectedIndex == null) "Choose a look for a new range" else "Add another range", fontSize = 11.sp)
                        }
                        if (validRange) {
                            val rangeStart = ((startMs - originalClip.trimStartMs).toFloat() / sourceSpan).coerceIn(0f, 1f)
                            val rangeEnd = ((endMs - originalClip.trimStartMs).toFloat() / sourceSpan).coerceIn(rangeStart, 1f)
                            RangeSlider(value = rangeStart..rangeEnd, onValueChange = { fractions ->
                                vm.setPlaying(false)
                                val start = originalClip.trimStartMs + (fractions.start * sourceSpan).toLong()
                                val end = originalClip.trimStartMs + (fractions.endInclusive * sourceSpan).toLong()
                                startMs = start.coerceIn(originalClip.trimStartMs, originalClip.trimEndMs - 1L)
                                endMs = end.coerceIn(startMs + 1L, originalClip.trimEndMs)
                                if (selectedIndex != null) updateSelected(currentDraft())
                            }, onValueChangeFinished = { onSeekSource(startMs) },
                                enabled = enabled && validRange && selectedIndex != null,
                                modifier = Modifier.fillMaxWidth().height(36.dp).testTag("filter-source-range"))
                        }
                    }

                    Column(Modifier.fillMaxWidth().testTag("filter-tuning-card")) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Strength", fontSize = 11.sp, modifier = Modifier.width(58.dp))
                            Slider(value = intensity, onValueChange = {
                                intensity = it
                                if (selectedIndex != null) updateSelected(currentDraft().copy(intensity = it))
                            }, valueRange = 0f..1f, enabled = enabled && selectedIndex != null,
                                modifier = Modifier.weight(1f).height(32.dp).testTag("filter-intensity"))
                            Text("${(intensity * 100).toInt()}%", fontSize = 10.sp, modifier = Modifier.width(34.dp))
                            IconButton(onClick = {
                                val idx = selectedIndex
                                if (idx != null && idx in effects.indices) {
                                    effects = effects.toMutableList().also { it.removeAt(idx) }
                                    selectedIndex = null
                                }
                            }, enabled = enabled && selectedIndex != null,
                                modifier = Modifier.testTag("filter-remove")) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove selected filter", tint = foreground)
                            }
                        }
                        state.playerError?.let { Text("Preview failed: $it. Apply is disabled; choose another look or Cancel.",
                            color = EditorPalette.Warning, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }

                    Column(Modifier.fillMaxWidth().testTag("filter-customize-card")) {
                        TextButton(onClick = { customizeExpanded = !customizeExpanded },
                            enabled = enabled && selectedIndex != null,
                            modifier = Modifier.testTag("filter-customize-toggle")) {
                            Text(if (customizeExpanded) "Adjustments  −" else "Adjustments  +", fontSize = 11.sp,
                                color = EditorPalette.TextSecondary)
                        }
                        if (customizeExpanded) {
                            listOf(Triple("Exposure", exposure, -2f..2f),
                                Triple("Contrast", contrast, 0.5f..1.5f),
                                Triple("Saturation", saturation, 0f..2f)).forEach { (label, value, bounds) ->
                                Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, fontSize = 10.sp, modifier = Modifier.width(58.dp))
                                    Slider(value = value, onValueChange = { changed ->
                                        when (label) {
                                            "Exposure" -> exposure = changed
                                            "Contrast" -> contrast = changed
                                            else -> saturation = changed
                                        }
                                        if (selectedIndex != null) updateSelected(currentDraft())
                                    }, valueRange = bounds, enabled = enabled && selectedIndex != null,
                                        modifier = Modifier.weight(1f).height(28.dp).testTag("filter-${label.lowercase()}"))
                                    Text(if (label == "Exposure") "${"%.1f".format(value)} EV" else "${"%.2f".format(value)}×",
                                        fontSize = 9.sp, modifier = Modifier.width(42.dp))
                                }
                            }
                        }
                    }
                    }
                    }
                }

                Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onPreviewClip(originalClip); onDismiss() },
                        modifier = Modifier.testTag("filter-cancel")) { Text("Cancel", color = foreground) }
                    Button(onClick = {
                        if (enabled && state.playerError == null && vm.applyWorkspaceEffects(projectId, originalClip, effects)) onDismiss()
                    }, enabled = enabled && dirty && state.playerError == null,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("filter-apply"),
                        colors = ButtonDefaults.buttonColors(containerColor = EditorPalette.NeonPurple)) {
                        Icon(Icons.Default.Check, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Apply")
                    }
                }
            }
        }
        return
    }

    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = Modifier.testTag("filter-workspace-panel").heightIn(max = panelMaxHeight)
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = panelMaxHeight).padding(horizontal = 12.dp, vertical = if (compactPhone) 4.dp else 6.dp)) {
            // Pinned workspace title and close affordance.
            Row(Modifier.fillMaxWidth().height(if (compactPhone) 34.dp else 38.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", fontSize = if (compactPhone) 14.sp else 16.sp, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onPreviewClip(originalClip); onDismiss() },
                    modifier = Modifier.testTag("filter-close")
                ) { Icon(Icons.Default.Close, contentDescription = "Close filters", tint = foreground) }
            }

            Row(
                Modifier.weight(1f).fillMaxWidth().horizontalScroll(workspaceScroll),
                horizontalArrangement = Arrangement.spacedBy(if (compactPhone) 6.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.width(310.dp).fillMaxHeight().testTag("filter-source-card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = EditorPalette.BgSurface)
                ) {
                    Column(Modifier.fillMaxSize().padding(if (compactPhone) 8.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compactPhone) 4.dp else 5.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Source range", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                if (!isPlaying) onSeekSource(startMs)
                                onTogglePlayback()
                            }, enabled = enabled, modifier = Modifier.testTag("filter-play-preview")) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause filter preview" else "Play filter preview", tint = foreground)
                            }
                            IconButton(onClick = {
                                effects = originalClip.workspaceEffects
                                val idx = effects.indexOfFirst { it.id in WorkspaceEffectPresets.filterIds }.takeIf { it >= 0 }
                                selectedIndex = idx
                                idx?.let { selectSavedFilter(it) } ?: run {
                                    selectedId = catalog.first().id
                                    intensity = 0.78f
                                    exposure = 0f
                                    contrast = 1f
                                    saturation = 1f
                                    startMs = originalClip.trimStartMs
                                    endMs = originalClip.trimEndMs
                                }
                            }, enabled = dirty || selectedIndex != firstExisting, modifier = Modifier.testTag("filter-undo")) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo filter draft", tint = foreground)
                            }
                        }
                        if (disabledReason != null) {
                            Text(disabledReason, color = EditorPalette.Warning, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        val filterEffects = effects.withIndex().filter { it.value.id in WorkspaceEffectPresets.filterIds }
                        if (filterEffects.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                items(filterEffects, key = { "${it.index}-${it.value.id}" }) { indexed ->
                                    val preset = WorkspaceFilterPresets.find(indexed.value.id)
                                    AssistChip(
                                        onClick = { selectSavedFilter(indexed.index) },
                                        label = { Text("${preset?.label ?: indexed.value.id}  ${indexed.value.startMs}–${indexed.value.endMs}ms", maxLines = 1) },
                                        leadingIcon = { Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(Color(preset?.swatchArgb ?: 0xFF8C91A0))) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (selectedIndex == indexed.index) EditorPalette.NeonPurple.copy(alpha = 0.16f) else EditorPalette.BgDeep,
                                            labelColor = foreground,
                                            leadingIconContentColor = foreground
                                        ),
                                        modifier = Modifier.testTag("filter-saved-${indexed.index}")
                                    )
                                }
                            }
                        }
                        Text("${startMs}–${endMs} ms", fontSize = 11.sp, color = EditorPalette.TextSecondary)
                        if (validRange) {
                            val rangeStart = ((startMs - originalClip.trimStartMs).toFloat() / sourceSpan).coerceIn(0f, 1f)
                            val rangeEnd = ((endMs - originalClip.trimStartMs).toFloat() / sourceSpan).coerceIn(rangeStart, 1f)
                            RangeSlider(
                                value = rangeStart..rangeEnd,
                                onValueChange = { fractions ->
                                    vm.setPlaying(false)
                                    val start = originalClip.trimStartMs + (fractions.start * sourceSpan).toLong()
                                    val end = originalClip.trimStartMs + (fractions.endInclusive * sourceSpan).toLong()
                                    startMs = start.coerceIn(originalClip.trimStartMs, originalClip.trimEndMs - 1L)
                                    endMs = end.coerceIn(startMs + 1L, originalClip.trimEndMs)
                                    if (selectedIndex != null) updateSelected(currentDraft())
                                },
                                onValueChangeFinished = { onSeekSource(startMs) },
                                enabled = enabled && validRange && selectedIndex != null,
                                modifier = Modifier.fillMaxWidth().height(36.dp).testTag("filter-source-range")
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.width(300.dp).fillMaxHeight().testTag("filter-presets-card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = EditorPalette.BgSurface)
                ) {
                    Column(Modifier.fillMaxSize().padding(if (compactPhone) 7.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compactPhone) 4.dp else 6.dp)) {
                        Text("Looks", fontSize = 13.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().testTag("filter-look-categories")) {
                            items(categories) { name ->
                                FilterChip(selected = category == name, onClick = { category = name },
                                    label = { Text(name, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EditorPalette.NeonPurple.copy(alpha = 0.18f),
                                        selectedLabelColor = EditorPalette.NeonPurple
                                    ), modifier = Modifier.testTag("filter-look-category-$name"))
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            items(categoryCatalog, key = { it.id }) { preset ->
                                val selected = selectedId == preset.id
                                Column(
                                    Modifier.width(if (compactPhone) 70.dp else 76.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) EditorPalette.BgElevated else EditorPalette.BgDeep)
                                        .border(if (selected) 2.dp else 1.dp, if (selected) EditorPalette.NeonPurple else EditorPalette.BorderGlass, RoundedCornerShape(10.dp))
                                        .testTag("filter-preset-${preset.id}")
                                        .clickable(enabled = enabled && validRange) { previewPreset(preset.id) }
                                        .semantics { contentDescription = "${preset.label}: ${preset.description}" }
                                        .padding(horizontal = 5.dp, vertical = if (compactPhone) 4.dp else 7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val sample = filterSampleBitmaps[preset.id]
                                    if (sample != null) {
                                        Image(
                                            bitmap = sample,
                                            contentDescription = "${preset.label} sample preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().height(if (compactPhone) 46.dp else 56.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .testTag("filter-sample-${preset.id}"),
                                        )
                                    } else {
                                        Box(Modifier.fillMaxWidth().height(if (compactPhone) 46.dp else 56.dp).clip(RoundedCornerShape(6.dp)).background(Color(preset.swatchArgb))) {
                                            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)))
                                        }
                                    }
                                    Text(preset.label, color = foreground, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Text("${catalog.size} original offline shader looks · ${categoryCatalog.size} shown", fontSize = 10.sp, color = EditorPalette.TextSecondary)
                    }
                }

                Card(
                    modifier = Modifier.width(240.dp).fillMaxHeight().testTag("filter-tuning-card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = EditorPalette.BgSurface)
                ) {
                    Column(Modifier.fillMaxSize().padding(if (compactPhone) 8.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Tuning & actions", fontSize = 13.sp)
                        TextButton(onClick = {
                            selectedIndex = null
                            intensity = 0.78f; exposure = 0f; contrast = 1f; saturation = 1f
                            startMs = originalClip.trimStartMs; endMs = originalClip.trimEndMs
                        }, enabled = enabled, modifier = Modifier.testTag("filter-new-range")) {
                            Text(if (selectedIndex == null) "Choose a look for a new range" else "Add another range", fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Strength", fontSize = 11.sp, modifier = Modifier.width(52.dp))
                            Slider(
                                value = intensity,
                                onValueChange = { intensity = it; if (selectedIndex != null) updateSelected(currentDraft().copy(intensity = it)) },
                                valueRange = 0f..1f,
                                enabled = enabled && selectedIndex != null,
                                modifier = Modifier.weight(1f).height(32.dp).testTag("filter-intensity")
                            )
                            Text("${(intensity * 100).toInt()}%", fontSize = 10.sp, modifier = Modifier.width(34.dp))
                            IconButton(
                                onClick = {
                                    val idx = selectedIndex
                                    if (idx != null && idx in effects.indices) {
                                        effects = effects.toMutableList().also { it.removeAt(idx) }
                                        selectedIndex = null
                                    }
                                },
                                enabled = enabled && selectedIndex != null,
                                modifier = Modifier.testTag("filter-remove")
                            ) { Icon(Icons.Default.DeleteOutline, contentDescription = "Remove selected filter", tint = foreground) }
                        }
                        state.playerError?.let { Text("Preview failed: $it. Apply is disabled; choose another look or Cancel.", color = EditorPalette.Warning, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }

                Card(
                    modifier = Modifier.width(250.dp).fillMaxHeight().testTag("filter-customize-card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = EditorPalette.BgSurface)
                ) {
                    Column(Modifier.fillMaxSize().padding(if (compactPhone) 8.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Adjustments", fontSize = 13.sp)
                        TextButton(
                            onClick = { customizeExpanded = !customizeExpanded },
                            enabled = enabled && selectedIndex != null,
                            modifier = Modifier.testTag("filter-customize-toggle")
                        ) { Text(if (customizeExpanded) "Customize  −" else "Customize  +", fontSize = 11.sp, color = EditorPalette.TextSecondary) }
                        if (customizeExpanded) {
                            listOf(
                                Triple("Exposure", exposure, -2f..2f),
                                Triple("Contrast", contrast, 0.5f..1.5f),
                                Triple("Saturation", saturation, 0f..2f)
                            ).forEach { (label, value, bounds) ->
                                Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, fontSize = 10.sp, modifier = Modifier.width(58.dp))
                                    Slider(
                                        value = value,
                                        onValueChange = { changed ->
                                            when (label) {
                                                "Exposure" -> exposure = changed
                                                "Contrast" -> contrast = changed
                                                else -> saturation = changed
                                            }
                                            if (selectedIndex != null) updateSelected(currentDraft())
                                        },
                                        valueRange = bounds,
                                        enabled = enabled && selectedIndex != null,
                                        modifier = Modifier.weight(1f).height(28.dp).testTag("filter-${label.lowercase()}")
                                    )
                                    Text(
                                        when (label) {
                                            "Exposure" -> "${"%.1f".format(value)} EV"
                                            else -> "${"%.2f".format(value)}×"
                                        }, fontSize = 9.sp, modifier = Modifier.width(42.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pinned cancellation and commit actions remain visible while cards scroll.
            Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onPreviewClip(originalClip); onDismiss() }, modifier = Modifier.testTag("filter-cancel")) { Text("Cancel", color = foreground) }
                Button(
                    onClick = {
                        if (enabled && state.playerError == null && vm.applyWorkspaceEffects(projectId, originalClip, effects)) onDismiss()
                    },
                    enabled = enabled && dirty && state.playerError == null,
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("filter-apply"),
                    colors = ButtonDefaults.buttonColors(containerColor = EditorPalette.NeonPurple)
                ) { Icon(Icons.Default.Check, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Apply") }
            }
        }
    }
}
