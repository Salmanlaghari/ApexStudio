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
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.applyProjectVideoTemplate
import com.apexstudio.app.presentation.viewmodel.applyProjectVideoTextMotion
import com.apexstudio.app.presentation.viewmodel.applyProjectVideoEffect
import com.apexstudio.app.presentation.viewmodel.applyProjectVideoTextTemplate
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

/** A frozen target prevents a delayed selection from editing a different clip or project. */
@Composable
fun ProjectVideoPresetsOverlay(
    vm: EditorViewModel,
    onClose: () -> Unit,
    onLearnedUpscale: ((String, MediaClip) -> Unit)? = null,
) {
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
        onClose = onClose,
        onVideoTemplate = { id -> finish(target?.let { vm.applyProjectVideoTemplate(it.first, it.second, id) } == true) },
        onTextMotion = { id -> finish(target?.let { vm.applyProjectVideoTextMotion(it.first, it.second, id) } == true) },
        onLearnedUpscale = if (target != null && onLearnedUpscale != null) {
            { onLearnedUpscale.invoke(target.first, target.second) }
        } else null
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
    onClose: () -> Unit,
    onVideoTemplate: (String) -> Unit = {},
    onTextMotion: (String) -> Unit = {},
    onLearnedUpscale: (() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ApexPalette.BgSurface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Project video tools", style = MaterialTheme.typography.titleLarge)
            Text("Fixed local editing choices; no text or prompt input. Color presets are deterministic, not AI.",
                style = MaterialTheme.typography.bodySmall)
            Text(if (eligible) "Selected video: $targetName" else "Select an existing main-track video clip in this project. Still images, audio and overlays are not supported.")
            Text("Video Templates · Fixed Combo", style = MaterialTheme.typography.titleMedium)
            Text("Each template applies a fixed color look and fixed title together as one undoable change.", style = MaterialTheme.typography.bodySmall)
            ProjectVideoPresets.videoTemplates.forEach { preset ->
                OutlinedButton(onClick = { onVideoTemplate(preset.id) }, enabled = eligible,
                    modifier = Modifier.fillMaxWidth().testTag("video_template_${preset.id}")) {
                    Text(preset.label)
                }
            }
            Text("Text Templates · Fixed Titles", style = MaterialTheme.typography.titleMedium)
            Text("Choose a ready-made title. Its words and style are fixed.", style = MaterialTheme.typography.bodySmall)
            ProjectVideoPresets.textTemplates.forEach { preset ->
                OutlinedButton(onClick = { onTextTemplate(preset.id) }, enabled = eligible,
                    modifier = Modifier.fillMaxWidth().testTag("video_text_template_${preset.id}")) {
                    Text(preset.label)
                }
            }
            Text("Text Motion · Fixed Title", style = MaterialTheme.typography.titleMedium)
            Text("Adds the fixed YOUR STORY title with a selected entrance motion; no text entry.", style = MaterialTheme.typography.bodySmall)
            ProjectVideoPresets.textMotions.forEach { motion ->
                OutlinedButton(onClick = { onTextMotion(motion.id) }, enabled = eligible,
                    modifier = Modifier.fillMaxWidth().testTag("video_text_motion_${motion.id}")) {
                    Text(motion.label)
                }
            }
            Text("Video Filters", style = MaterialTheme.typography.titleMedium)
            Text("Applies a fixed effect to this clip only, without replacing the source file. Undo restores the previous edit.", style = MaterialTheme.typography.bodySmall)
            Text("Natural HD, Daylight and Vivid mix RGB pixel colors using the same renderer in preview and export. HD is a color look, not recovered detail, HDR or a resolution upgrade.",
                style = MaterialTheme.typography.bodySmall)
            ProjectVideoPresets.effects.forEach { preset ->
                OutlinedButton(onClick = { onVideoEffect(preset.id) }, enabled = eligible,
                    modifier = Modifier.fillMaxWidth().testTag("video_effect_${preset.id}")) {
                    Text(preset.label)
                }
            }
            Text("Video Enhance · Deterministic Color", style = MaterialTheme.typography.titleMedium)
            Text("A fixed contrast, saturation and exposure color matrix. Learned video upscaling is a separate tool.",
                style = MaterialTheme.typography.bodySmall)
            ProjectVideoPresets.videoEnhancements.forEach { preset ->
                OutlinedButton(onClick = { onVideoEffect(preset.id) }, enabled = eligible,
                    modifier = Modifier.fillMaxWidth().testTag("video_enhancement_${preset.id}")) {
                    Text(preset.label)
                }
            }
            Text("Learned Video Upscale · Separate", style = MaterialTheme.typography.titleMedium)
            Text("This learned upscaling tool is separate from the deterministic color preset.",
                style = MaterialTheme.typography.bodySmall)
            onLearnedUpscale?.let { callback ->
                OutlinedButton(onClick = callback, enabled = eligible,
                    modifier = Modifier.fillMaxWidth().testTag("video_learned_upscale")) {
                    Text("Learned Video Upscale")
                }
            }
            feedback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}
