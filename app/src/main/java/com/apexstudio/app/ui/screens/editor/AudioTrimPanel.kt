package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apexstudio.app.domain.model.AudioTrack
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

/** Music/voice trim never falls back to the first video clip. Values are source milliseconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrimPanel(track: AudioTrack, vm: EditorViewModel, onClose: () -> Unit) {
    val limit = track.sourceEndMs.coerceAtLeast(100L)
    var range by remember(track.id, track.trimStartMs, track.trimEndMs) {
        mutableStateOf(track.trimStartMs.toFloat()..track.trimEndMs.coerceAtLeast(track.trimStartMs + 100L).toFloat())
    }
    fun commit(start: Long, end: Long) {
        val sourceStart = start.coerceIn(0L, (limit - 100L).coerceAtLeast(0L))
        val sourceEnd = end.coerceIn(sourceStart + 100L, limit)
        vm.trimAudioTrackPlacement(track.id, sourceStart, sourceEnd,
            (track.startTimeMs + sourceStart - track.trimStartMs).coerceAtLeast(0L))
    }
    Surface(color = ApexPalette.BgElevated) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Trim audio · ${track.name}", color = ApexPalette.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text("Source ${range.start.toLong()}–${range.endInclusive.toLong()} ms · Timeline ${track.startTimeMs} ms",
                color = ApexPalette.TextSecondary)
            RangeSlider(value = range, onValueChange = { if (it.endInclusive - it.start >= 100f) range = it },
                valueRange = 0f..limit.toFloat(), onValueChangeFinished = { commit(range.start.toLong(), range.endInclusive.toLong()) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val source = track.trimStartMs + (vm.playerPositionFlow.value - track.startTimeMs)
                    commit(source.coerceAtMost(track.trimEndMs - 100L), track.trimEndMs)
                }) { Text("Start at playhead") }
                TextButton(onClick = {
                    val source = track.trimStartMs + (vm.playerPositionFlow.value - track.startTimeMs)
                    commit(track.trimStartMs, source.coerceAtLeast(track.trimStartMs + 100L))
                }) { Text("End at playhead") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { commit(0L, limit) }) { Text("Reset") }
                TextButton(onClick = { vm.seekTo(track.startTimeMs); vm.setPlaying(true) }) { Text("Preview") }
                Button(onClick = onClose) { Text("Done") }
            }
        }
    }
}
