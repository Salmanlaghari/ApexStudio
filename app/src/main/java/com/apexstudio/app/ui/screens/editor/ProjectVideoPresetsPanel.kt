package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.template.ProjectVideoPresets
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.applyProjectVideoEffect
import com.apexstudio.app.presentation.viewmodel.applyProjectVideoTextTemplate
import com.apexstudio.app.ui.theme.ApexPalette

/** A frozen target prevents a delayed selection from editing a different clip or project. */
@Composable
fun ProjectVideoPresetsOverlay(vm: EditorViewModel, onClose: () -> Unit) {
    val target = remember { vm.state.value.let { state ->
        state.project?.let { project ->
            project.clips.firstOrNull { it.id == state.selectedClipId }?.let { project.id to it }
        }
    } }
    var feedback by remember { mutableStateOf<String?>(null) }
    fun finish(applied: Boolean) {
        if (applied) onClose()
        else feedback = "Nothing changed. Select a current project video and reopen this panel."
    }
    ProjectVideoPresetsPanel(
        eligible = target?.second?.let { it.type == ClipType.VIDEO && it.trackIndex == 0 && it.uri.isNotBlank() &&
            it.durationMs > 0L && it.trimStartMs >= 0L && it.trimEndMs > it.trimStartMs && it.trimEndMs <= it.durationMs } == true,
        targetName = target?.second?.name,
        feedback = feedback,
        onTextTemplate = { id -> finish(target?.let { vm.applyProjectVideoTextTemplate(it.first, it.second, id) } == true) },
        onVideoEffect = { id -> finish(target?.let { vm.applyProjectVideoEffect(it.first, it.second, id) } == true) },
        onClose = onClose
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectVideoPresetsPanel(
    eligible: Boolean,
    targetName: String?,
    feedback: String?,
    onTextTemplate: (String) -> Unit,
    onVideoEffect: (String) -> Unit,
    onClose: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ApexPalette.BgSurface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Project video tools", style = MaterialTheme.typography.titleLarge)
            Text("Fixed local presets · not AI. No prompts, no image/video generation. Protected AI remains locked; these tools do not run AI or certify content as safe.",
                style = MaterialTheme.typography.bodySmall)
            Text(if (eligible) "Selected video: $targetName" else "Select an existing main-track video clip in this project. Still images, audio and overlays are not supported.")
            Text("Text → Template", style = MaterialTheme.typography.titleMedium)
            Text("Choose a ready-made title. Its words and style are fixed; no text input is needed.", style = MaterialTheme.typography.bodySmall)
            ProjectVideoPresets.textTemplates.forEach { preset ->
                OutlinedButton(onClick = { onTextTemplate(preset.id) }, enabled = eligible,
                    modifier = Modifier.fillMaxWidth().testTag("video_text_template_${preset.id}")) {
                    Text(preset.label)
                }
            }
            Text("Real video → Effects", style = MaterialTheme.typography.titleMedium)
            Text("Applies a fixed effect to this clip only, without replacing the source file. Undo restores the previous edit.", style = MaterialTheme.typography.bodySmall)
            ProjectVideoPresets.effects.forEach { preset ->
                OutlinedButton(onClick = { onVideoEffect(preset.id) }, enabled = eligible,
                    modifier = Modifier.fillMaxWidth().testTag("video_effect_${preset.id}")) {
                    Text(preset.label)
                }
            }
            feedback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}
