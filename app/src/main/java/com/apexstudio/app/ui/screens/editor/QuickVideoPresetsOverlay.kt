package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.template.ProjectVideoPresets
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.presentation.viewmodel.*

internal enum class QuickVideoPresetSection(val title: String) {
    ENHANCE("Enhance · color only"), TEMPLATES("Video templates"), FILTERS("Recommended filters")
}

/** Compact select-only recipes use the shipped deterministic preview/export renderer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickVideoPresetsOverlay(vm: EditorViewModel, section: QuickVideoPresetSection, onClose: () -> Unit) {
    val target = remember { vm.state.value.let { state ->
        state.project?.let { project -> project.clips.firstOrNull { it.id == state.selectedClipId }?.let { project.id to it } }
    } }
    var error by remember { mutableStateOf<String?>(null) }
    val eligible = target?.second?.let { it.type == ClipType.VIDEO && it.trackIndex == 0 && it.uri.isNotBlank() } == true
    val choices = when (section) {
        QuickVideoPresetSection.ENHANCE -> ProjectVideoPresets.videoEnhancements.map { it.id to it.label }
        QuickVideoPresetSection.TEMPLATES -> ProjectVideoPresets.videoTemplates.map { it.id to it.label }
        QuickVideoPresetSection.FILTERS -> ProjectVideoPresets.effects.map { it.id to it.label }
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            Text(when (section) {
                QuickVideoPresetSection.ENHANCE -> "A fixed color lift, not recovered detail or AI. Tap to apply one undoable edit."
                QuickVideoPresetSection.TEMPLATES -> "Original built-in color and title recipes. Tap to apply one undoable edit."
                QuickVideoPresetSection.FILTERS -> "RGB color looks use the same shader in preview and export. HD labels describe color, not increased resolution."
            }, style = MaterialTheme.typography.bodySmall)
            if (!eligible) Text("Select an existing main-track project video first.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                items(choices) { (id, label) ->
                    OutlinedButton(enabled = eligible, onClick = {
                        val applied = target?.let { (projectId, clip) ->
                            if (section == QuickVideoPresetSection.TEMPLATES) vm.applyProjectVideoTemplate(projectId, clip, id)
                            else vm.applyProjectVideoEffect(projectId, clip, id)
                        } == true
                        if (applied) onClose() else error = "The project changed. Re-select the video and try again."
                    }, modifier = Modifier.widthIn(max = 230.dp)) { Text(label) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}
