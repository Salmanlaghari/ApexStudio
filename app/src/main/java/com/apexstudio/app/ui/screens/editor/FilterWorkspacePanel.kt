package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import com.apexstudio.app.domain.model.WorkspaceFilterPresets
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.applyWorkspaceEffects
import com.apexstudio.app.presentation.viewmodel.setPlaying
import com.apexstudio.app.presentation.viewmodel.setPlayerError

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
    val catalog = WorkspaceFilterPresets.all
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
    val background = Color(0xFF20232B)
    val foreground = Color(0xFFF5F6F8)

    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = Modifier.testTag("filter-workspace-panel")
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", fontSize = 16.sp, modifier = Modifier.weight(1f))
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
                    Icon(Icons.Default.Undo, contentDescription = "Undo filter draft", tint = foreground)
                }
                TextButton(onClick = { onPreviewClip(originalClip); onDismiss() }) { Text("Cancel", color = foreground) }
                Button(
                    onClick = {
                        if (enabled && state.playerError == null && vm.applyWorkspaceEffects(projectId, originalClip, effects)) onDismiss()
                    },
                    enabled = enabled && dirty && state.playerError == null,
                    modifier = Modifier.testTag("filter-apply")
                ) { Icon(Icons.Default.Check, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Apply") }
            }

            state.playerError?.let { Text("Preview failed: $it. Apply is disabled; choose another look or Cancel.", color = Color(0xFFFFC2B9), fontSize = 11.sp) }
            if (disabledReason != null) {
                Text(disabledReason, color = Color(0xFFFFC2B9), fontSize = 11.sp, lineHeight = 14.sp)
            } else {
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
                                    containerColor = if (selectedIndex == indexed.index) Color(0xFF405569) else Color(0xFF30343D),
                                    labelColor = foreground,
                                    leadingIconContentColor = foreground
                                ),
                                modifier = Modifier.testTag("filter-saved-${indexed.index}")
                            )
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(catalog, key = { it.id }) { preset ->
                        val selected = selectedId == preset.id
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Color(0xFF39495A) else Color(0xFF2D3038))
                                .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color(0xFF4C515C), RoundedCornerShape(10.dp))
                                .testTag("filter-preset-${preset.id}")
                                .clickable(enabled = enabled && validRange) { previewPreset(preset.id) }
                                .semantics { contentDescription = "${preset.label}: ${preset.description}" }
                                .padding(horizontal = 6.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(6.dp)).background(Color(preset.swatchArgb))) {
                                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)))
                            }
                            Text(preset.label, color = foreground, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Strength", fontSize = 12.sp, modifier = Modifier.width(56.dp))
                    Slider(
                        value = intensity,
                        onValueChange = { intensity = it; if (selectedIndex != null) updateSelected(currentDraft().copy(intensity = it)) },
                        valueRange = 0f..1f,
                        enabled = enabled && selectedIndex != null,
                        modifier = Modifier.weight(1f).height(34.dp).testTag("filter-intensity")
                    )
                    Text("${(intensity * 100).toInt()}%", fontSize = 11.sp, modifier = Modifier.width(38.dp))
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

                TextButton(
                    onClick = { customizeExpanded = !customizeExpanded },
                    enabled = enabled && selectedIndex != null,
                    modifier = Modifier.testTag("filter-customize-toggle")
                ) { Text(if (customizeExpanded) "Customize  −" else "Customize  +", fontSize = 11.sp, color = Color(0xFFCFD4DC)) }
                if (customizeExpanded) {
                    listOf(
                        Triple("Exposure", exposure, -2f..2f),
                        Triple("Contrast", contrast, 0.5f..1.5f),
                        Triple("Saturation", saturation, 0f..2f)
                    ).forEach { (label, value, bounds) ->
                        Row(Modifier.fillMaxWidth().height(27.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontSize = 10.sp, modifier = Modifier.width(62.dp))
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
                                }, fontSize = 10.sp, modifier = Modifier.width(48.dp)
                            )
                        }
                    }
                }

                Text("Source range  ${startMs}–${endMs} ms", fontSize = 11.sp)
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
                        modifier = Modifier.fillMaxWidth().height(32.dp).testTag("filter-source-range")
                    )
                }
                Text("12 original offline shader looks (not imported presets). Preview is temporary; Apply saves the source-timed look, Cancel discards it.", fontSize = 10.sp, color = Color(0xFFB9C1CB))
            }
        }
    }
}
