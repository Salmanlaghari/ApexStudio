package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.applyLocalChallenge
import com.apexstudio.app.presentation.viewmodel.normalizeChallengeTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalChallengeOverlay(vm: EditorViewModel, onClose: () -> Unit) {
    val target = remember { vm.state.value.let { state -> state.project?.let { project ->
        project.clips.firstOrNull { it.id == state.selectedClipId }?.let { project.id to it }
    } } }
    var tag by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val eligible = target?.second?.let { it.type == ClipType.VIDEO && it.trackIndex == 0 && it.uri.isNotBlank() } == true
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Challenge · local title", style = MaterialTheme.typography.titleMedium)
            Text("Add your own hashtag as an editable video title. Export, then choose Share / Next to hand the file to another Android app. No account, public challenge feed or automatic posting is connected.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = tag, onValueChange = { tag = it.take(49) }, singleLine = true,
                label = { Text("Challenge hashtag") }, placeholder = { Text("#MyChallenge") },
                supportingText = { Text("1–48 letters, numbers or underscores") }, modifier = Modifier.fillMaxWidth())
            if (!eligible) Text("Select an existing main-track project video first.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClose) { Text("Cancel") }
                Button(enabled = eligible && normalizeChallengeTag(tag) != null, onClick = {
                    if (target?.let { (projectId, clip) -> vm.applyLocalChallenge(projectId, clip, tag) } == true) onClose()
                    else error = "The project or selection changed. Reopen Challenge and try again."
                }) { Text("Apply title") }
            }
        }
    }
}
