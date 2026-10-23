package com.apexstudio.app.ui.screens.colortools

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.apexstudio.app.presentation.viewmodel.ColorTimelineExportSnapshot
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A rendered, paused comparison of the entire eligible timeline, never a disguised source frame. */
@Composable
internal fun ColorTimelineComparisonPanel(
    snapshot: ColorTimelineExportSnapshot?,
    blocker: String?,
    split: Float,
    onSplit: (Float) -> Unit,
    embedded: Boolean,
    onBusyChanged: (Boolean) -> Unit,
    previewModifier: Modifier = Modifier.fillMaxWidth(),
    onCompareSelectedSource: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rendered by remember { mutableStateOf<ColorTimelineComparisonRenderer.Comparison?>(null) }
    var renderedSnapshot by remember { mutableStateOf<ColorTimelineExportSnapshot?>(null) }
    // Displayed immutable bitmaps are GC-owned: Compose/render-thread readers can outlive replacement.
    // Eagerly recycling a previous pair here can crash the next draw with a recycled-bitmap error.
    var frames by remember { mutableStateOf<ColorTimelineComparisonRenderer.FramePair?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val outputs = remember { ColorComparisonOutputOwner() }
    val currentJob by rememberUpdatedState(job)
    val currentOnBusyChanged by rememberUpdatedState(onBusyChanged)
    val fresh = rendered != null && renderedSnapshot == snapshot && snapshot != null
    DisposableEffect(Unit) {
        onDispose {
            currentJob?.cancel()
            outputs.dispose()
            currentOnBusyChanged(false)
        }
    }
    LaunchedEffect(rendered, fresh, positionMs) {
        frames = null
        if (!fresh) return@LaunchedEffect
        try {
            delay(100) // Do not start native frame reads for every intermediate slider tick.
            frames = ColorTimelineComparisonRenderer.decodeFrames(requireNotNull(rendered), positionMs)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            message = failure.message ?: "The rendered comparison frames could not be decoded."
        }
    }
    BeforeAfterPreview(
        before = if (fresh) frames?.before else null,
        after = if (fresh) frames?.after else null,
        split = split, onSplit = onSplit, ratio = if (embedded) 1.36f else 16f / 9f,
        modifier = previewModifier, loading = fresh && frames == null && message == null,
        error = blocker ?: message ?: if (!fresh) {
            if (busy) "Rendering both complete timeline snapshots…"
            else if (rendered != null) "Grade or project changed. Render again to compare the current timeline."
            else "Render the supported timeline to compare its complete Before and After."
        } else null
    )
    if (blocker != null && onCompareSelectedSource != null) {
        TextButton(onClick = onCompareSelectedSource) { Text("Compare selected source only") }
        Text("Source-only comparison is not the project composition and excludes PiP layers. No project edits are changed.",
            color = ApexPalette.TextSecondary, fontSize = 11.sp)
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(enabled = !busy && snapshot != null, onClick = {
            val captured = snapshot ?: return@TextButton
            busy = true
            currentOnBusyChanged(true)
            progress = 0f
            message = null
            job = scope.launch {
                try {
                    val pair = ColorTimelineComparisonRenderer.render(context, captured.clips, captured.config) { progress = it }
                    outputs.replace(pair.before, pair.after)
                    rendered = pair
                    renderedSnapshot = captured
                    positionMs = 0L
                } catch (cancelled: CancellationException) {
                    message = "Timeline comparison cancelled. Original media and saved edits are unchanged."
                    throw cancelled
                } catch (failure: Exception) {
                    message = failure.message ?: "Timeline comparison failed; no incomplete movie is shown."
                } finally {
                    busy = false
                    currentOnBusyChanged(false)
                }
            }
        }) { Text(if (busy) "Rendering ${(progress * 100).toInt()}%" else "Render Before / After") }
        if (busy) TextButton(onClick = { job?.cancel() }) { Text("Cancel") }
        if (fresh) {
            val pair = requireNotNull(rendered)
            Text("Frame ${frames?.timeMs ?: positionMs} / ${pair.durationMs} ms", color = ApexPalette.NeonCyan, fontSize = 10.sp)
            Slider(value = positionMs.coerceIn(0L, pair.durationMs).toFloat(),
                onValueChange = { positionMs = it.toLong(); message = null },
                valueRange = 0f..pair.durationMs.coerceAtLeast(1L).toFloat(), modifier = Modifier.width(150.dp))
            TextButton(onClick = {
                try {
                    check(pair.after.isFile && pair.after.length() > 0L) { "The compared export is no longer available. Render again." }
                    outputs.retainAfterForShare()
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pair.after)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(context.contentResolver, pair.after.name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share compared timeline export"))
                } catch (failure: Exception) {
                    message = "Compared export is retained; sharing failed: ${failure.message ?: "try again"}"
                }
            }) { Text("Share compared MP4") }
        }
    }
    Text(if (busy) "Two full renders use the current export settings. This may take time and storage."
        else "Paused timeline comparison; only this grade differs. Share keeps/sends the exact After MP4. Unshared pairs are removed on close/replacement. Unsupported content blocks rendering.",
        color = ApexPalette.TextTertiary, fontSize = 9.sp)
}
