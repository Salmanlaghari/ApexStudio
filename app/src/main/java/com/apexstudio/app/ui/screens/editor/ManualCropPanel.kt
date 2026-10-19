package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.ui.theme.ApexPalette
import kotlin.math.roundToInt

/** Local draft only: Cancel and sheet disposal cannot alter the project or its history. */
@Composable
fun ManualCropPanel(
    initialCrop: VideoCropSettings,
    onApply: (VideoCropSettings) -> Unit,
    onCancel: () -> Unit
) {
    var draft by remember(initialCrop) { mutableStateOf(initialCrop.sanitized()) }
    var control by remember { mutableStateOf("Left") }
    val currentDraft = rememberUpdatedState(draft)
    val controls = remember { ManualCropEdge.entries.map { it.label } + listOf("Pan X", "Pan Y") }
    val edge = ManualCropEdge.entries.firstOrNull { it.label == control }
    val value = edge?.let { draft.edgePosition(it) } ?: if (control == "Pan X") {
        (draft.left + draft.right) / 2f
    } else (draft.top + draft.bottom) / 2f

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text("Manual crop · draft", color = ApexPalette.TextPrimary, style = MaterialTheme.typography.titleSmall)
        Text("Drag the crop window to pan, or adjust an edge below. Apply saves one undoable edit. Free edge changes unlock the aspect ratio.",
            color = ApexPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Canvas(Modifier.fillMaxWidth().height(96.dp).padding(vertical = 6.dp)
            .semantics { contentDescription = "Normalized source crop diagram. Drag to pan the draft crop." }
            .pointerInput(Unit) {
                detectDragGestures { change, delta ->
                    change.consume()
                    val crop = currentDraft.value
                    if (size.width > 0 && size.height > 0) {
                        draft = crop.moveCenter((crop.left + crop.right) / 2f + delta.x / size.width,
                            (crop.top + crop.bottom) / 2f + delta.y / size.height)
                    }
                }
            }) {
            drawRect(ApexPalette.BgElevated)
            val origin = Offset(draft.left * size.width, draft.top * size.height)
            val area = Size(draft.width * size.width, draft.height * size.height)
            drawRect(ApexPalette.NeonCyan.copy(alpha = .18f), origin, area)
            drawRect(ApexPalette.NeonCyan, origin, area, style = Stroke(2.dp.toPx()))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(controls) { label ->
                FilterChip(selected = control == label, onClick = { control = label }, label = { Text(label) })
            }
        }
        Text("$control: ${(value * 100f).roundToInt()}% · retained ${(draft.width * 100f).roundToInt()}% × ${(draft.height * 100f).roundToInt()}%",
            color = ApexPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Slider(value = value.coerceIn(0f, 1f), onValueChange = { position ->
            draft = when {
                edge != null -> draft.moveEdge(edge, position)
                control == "Pan X" -> draft.moveCenter(position, (draft.top + draft.bottom) / 2f)
                else -> draft.moveCenter((draft.left + draft.right) / 2f, position)
            }
        }, modifier = Modifier.semantics { contentDescription = "$control crop position" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { draft = VideoCropSettings() }) { Text("Full frame") }
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { onApply(draft) }) { Text("Apply crop") }
        }
    }
}
