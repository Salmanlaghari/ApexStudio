package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apexstudio.app.ui.theme.ApexPalette

/** Readiness is explicit: a missing model is not a working AI toggle. */
data class AiToolCard(
    val id: String,
    val title: String,
    val detail: String,
    val group: String,
    val ready: Boolean,
    val enabled: Boolean = false,
    val unavailableReason: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiToolsPanel(
    tools: List<AiToolCard>,
    processing: Boolean,
    progress: Float,
    message: String,
    onToggle: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit
) {
    var group by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = ApexPalette.BgSurface) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("On-device AI", style = MaterialTheme.typography.titleLarge, color = ApexPalette.TextPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close AI tools", tint = ApexPalette.TextPrimary) }
            }
            Text("Local processing only. No cloud API. Model and safety requirements are checked before a feature can run.",
                modifier = Modifier.padding(horizontal = 16.dp), color = ApexPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = group == null, onClick = { group = null }, label = { Text("All") }) }
                items(tools.map { it.group }.distinct()) { value ->
                    FilterChip(selected = group == value, onClick = { group = value }, label = { Text(value) })
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tools.filter { group == null || it.group == group }, key = { it.id }) { tool ->
                    Card(Modifier.width(268.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tool.title, style = MaterialTheme.typography.titleMedium)
                            Text(tool.detail, style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (tool.enabled) "ON" else "OFF", modifier = Modifier.weight(1f))
                                Switch(checked = tool.enabled, onCheckedChange = { onToggle(tool.id, it) },
                                    enabled = !processing && (tool.ready || tool.enabled))
                            }
                            if (!tool.ready) Text(tool.unavailableReason ?: "Required local model is not installed.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (processing) {
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel processing") }
            }
            if (message.isNotBlank()) Text(message, modifier = Modifier.padding(horizontal = 16.dp),
                color = ApexPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
