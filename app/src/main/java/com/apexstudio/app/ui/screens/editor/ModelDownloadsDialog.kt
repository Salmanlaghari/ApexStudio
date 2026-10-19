package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apexstudio.app.ai.models.OnDemandModelPacks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/** User-driven model download controls. Opening this dialog only checks private local files. */
@Composable
fun ModelDownloadsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { OnDemandModelPacks.forApp(context) }
    val scope = rememberCoroutineScope()
    val installed = remember { mutableStateMapOf<String, Boolean>() }
    val progress = remember { mutableStateMapOf<String, OnDemandModelPacks.Progress>() }
    val failures = remember { mutableStateMapOf<String, String>() }
    val jobs = remember { mutableStateMapOf<String, Job>() }
    val packs = remember { manager.availablePacks().sortedBy { it.totalBytes } }

    LaunchedEffect(manager) {
        packs.forEach { pack -> installed[pack.id] = manager.installedDirectory(pack.id) != null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Optional AI models") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Models stay on this device. Downloads start only when you tap Download; no model inference or media upload is started here.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                items(packs, key = { it.id }) { pack ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(pack.title(), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text("${formatBytes(pack.totalBytes)} · ${pack.license} · SHA-256 checked on download", style = MaterialTheme.typography.bodySmall)
                        Text("Source revision: ${pack.sourceRevision ?: "checksum-pinned upstream file"}", style = MaterialTheme.typography.bodySmall)
                        val job = jobs[pack.id]
                        val state = progress[pack.id]
                        if (job?.isActive == true && state != null) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (state.completedBytes.toFloat() / state.totalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("${formatBytes(state.completedBytes)} / ${formatBytes(state.totalBytes)}", style = MaterialTheme.typography.bodySmall)
                        }
                        failures[pack.id]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when {
                                job?.isActive == true -> OutlinedButton(onClick = { job.cancel() }) { Text("Cancel") }
                                installed[pack.id] == true -> OutlinedButton(onClick = {
                                    scope.launch {
                                        if (manager.remove(pack.id)) installed[pack.id] = false
                                    }
                                }) { Text("Remove") }
                                else -> Button(onClick = {
                                    failures.remove(pack.id)
                                    progress[pack.id] = OnDemandModelPacks.Progress(0, pack.totalBytes)
                                    jobs[pack.id] = scope.launch {
                                        try {
                                            manager.install(pack.id) { progress[pack.id] = it }
                                            installed[pack.id] = true
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            failures[pack.id] = failure.message ?: "Download failed. Retry when ready."
                                        } finally {
                                            jobs.remove(pack.id)
                                        }
                                    }
                                }) { Text(if (failures.containsKey(pack.id)) "Retry" else "Download") }
                            }
                            if (installed[pack.id] == true) {
                                Text("Installed", modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

private fun OnDemandModelPacks.ModelPack.title(): String = when (id) {
    "whisper-tiny-multilingual" -> "Whisper tiny multilingual captions"
    "english-word-alignment" -> "English word-timing alignment"
    "super-resolution-espcn" -> "ESPCN super-resolution"
    "mediapipe-vision-models" -> "MediaPipe face, pose, hand & segmentation"
    "modnet-background-removal" -> "MODNet background removal"
    else -> id
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
