package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.ui.theme.EditorPalette
import com.apexstudio.app.util.TimeFormat

/** All times are project-timeline milliseconds, not positions relative to the owning clip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextDurationRangeCard(
    overlay: TextOverlay,
    timelineDurationMs: Long,
    playheadMs: Long,
    clipEnds: List<Pair<String, Long>>,
    onRangeChange: (Long, Long) -> Unit
) {
    val total = timelineDurationMs.coerceAtLeast(1L)
    val initialStart = overlay.startMs.coerceIn(0L, total - 1L)
    val initialEnd = overlay.endMs.coerceIn(initialStart + 1L, total)
    var start by remember(overlay.id, overlay.startMs, overlay.endMs, total) { mutableLongStateOf(initialStart) }
    var end by remember(overlay.id, overlay.startMs, overlay.endMs, total) { mutableLongStateOf(initialEnd) }
    fun commit(from: Long, to: Long) {
        val safeStart = from.coerceIn(0L, total - 1L)
        val safeEnd = to.coerceIn(safeStart + 1L, total)
        start = safeStart
        end = safeEnd
        onRangeChange(safeStart, safeEnd)
    }
    Column(Modifier.fillMaxWidth().testTag("text-duration-range"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Timeline range", color = EditorPalette.NeonCyan, fontSize = 12.sp)
        Text("${TimeFormat.formatMs(start)} – ${TimeFormat.formatMs(end)} · ${TimeFormat.formatMs(end - start)}",
            color = EditorPalette.TextPrimary, fontSize = 11.sp, modifier = Modifier.testTag("text-selected-duration"))
        RangeSlider(
            value = start.toFloat()..end.toFloat(),
            onValueChange = { range ->
                start = range.start.toLong().coerceIn(0L, total - 1L)
                end = range.endInclusive.toLong().coerceIn(start + 1L, total)
            },
            onValueChangeFinished = { commit(start, end) },
            valueRange = 0f..total.toFloat(),
            enabled = timelineDurationMs > 0L,
            modifier = Modifier.fillMaxWidth().height(40.dp).testTag("text-project-range-slider")
                .semantics { if (timelineDurationMs <= 0L) disabled() }
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = timelineDurationMs > 0L, onClick = { commit(playheadMs.coerceAtMost(end - 1L), end) }) {
                Text("Start here", fontSize = 10.sp)
            }
            TextButton(enabled = timelineDurationMs > 0L, onClick = { commit(start, playheadMs.coerceAtLeast(start + 1L)) }) {
                Text("End here", fontSize = 10.sp)
            }
            TextButton(enabled = timelineDurationMs > 0L, onClick = { commit(0L, total) }) {
                Text("Whole timeline", fontSize = 10.sp)
            }
        }
        if (clipEnds.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            clipEnds.forEachIndexed { index, (name, clipEnd) ->
                TextButton(enabled = clipEnd > start && timelineDurationMs > 0L, onClick = { commit(start, clipEnd) }) {
                    Text("Through clip ${index + 1} · ${TimeFormat.formatMs(clipEnd)}", fontSize = 10.sp)
                }
            }
        }
        val manualMotionAcrossCuts = (overlay.keyframes.keyframes.isNotEmpty() || overlay.maskKeyframes.isNotEmpty()) &&
            clipEnds.any { (_, cut) -> cut > start && cut < end }
        Text(if (manualMotionAcrossCuts) "Manual text/mask keyframes across cuts are not supported in timeline export. Keep this title within one clip or use preset motion."
            else "Drag either handle or set an edge at the playhead. Range may cross clip cuts; preset motion stays continuous.",
            color = if (manualMotionAcrossCuts) EditorPalette.Warning else EditorPalette.TextTertiary,
            fontSize = 9.sp, maxLines = 3)
    }
}
