package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.picker.MediaPickerHelper
import com.apexstudio.app.domain.model.Signature
import com.apexstudio.app.domain.model.SignatureCorner
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.closeSignaturePanel
import com.apexstudio.app.presentation.viewmodel.removeSignature
import com.apexstudio.app.presentation.viewmodel.updateSignature

@Composable
fun SignaturePanelOverlay(
    state: com.apexstudio.app.presentation.state.EditorState,
    vm: EditorViewModel,
    mediaPicker: MediaPickerHelper
) {
    if (!state.signaturePanelOpen) return
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
        tonalElevation = 8.dp
    ) {
        val signature = state.project?.signature ?: Signature()
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Project signature")
            Text("Branding stays above the composition and is saved with this project.")
            OutlinedTextField(
                value = signature.text,
                onValueChange = { value -> vm.updateSignature { signature -> signature.copy(text = value) } },
                label = { Text("Text") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { mediaPicker.pickSingleMedia.launch("image/*") }) {
                    Text(if (signature.logoUri == null) "Add logo" else "Change logo")
                }
                if (signature.logoUri != null && !signature.alwaysOn) {
                    OutlinedButton(onClick = { vm.updateSignature { it.copy(logoUri = null) } }) {
                        Text("Clear logo")
                    }
                }
            }
            Text("Position")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SignatureCorner.entries.forEach { corner ->
                    OutlinedButton(onClick = { vm.updateSignature { it.copy(position = corner) } }) {
                        Text(corner.name.replace('_', ' '))
                    }
                }
            }
            Text("Opacity ${(signature.alpha.coerceIn(0f, 1f) * 100).toInt()}%")
            Slider(value = signature.alpha.coerceIn(0f, 1f), onValueChange = { v -> vm.updateSignature { it.copy(alpha = v) } })
            Text("Size ${signature.fontSize.toInt()}dp")
            Slider(value = signature.fontSize.coerceIn(12f, 96f), onValueChange = { v -> vm.updateSignature { it.copy(fontSize = v) } }, valueRange = 12f..96f)
            Text("Margin ${signature.marginDp.toInt()}dp")
            Slider(value = signature.marginDp.coerceIn(0f, 64f), onValueChange = { v -> vm.updateSignature { it.copy(marginDp = v) } }, valueRange = 0f..64f)
            if (signature.alwaysOn) {
                Text("Always on (cannot be removed)")
            } else {
                Row {
                    Checkbox(checked = false, onCheckedChange = { enabled ->
                        if (enabled) vm.updateSignature { it.copy(alwaysOn = true) }
                    })
                    Text("Always on (cannot later be removed)", modifier = Modifier.padding(top = 12.dp))
                }
            }
            Divider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.closeSignaturePanel() }) { Text("Done") }
                if (!signature.alwaysOn) {
                    OutlinedButton(onClick = { vm.removeSignature(); vm.closeSignaturePanel() }) { Text("Remove signature") }
                }
            }
        }
    }
}
