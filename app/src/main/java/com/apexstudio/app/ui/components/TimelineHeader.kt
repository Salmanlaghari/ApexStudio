package com.apexstudio.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.ui.screens.editor.selectedKeyframeVideo
import com.apexstudio.app.util.TimeFormat
import kotlin.math.abs

/** Keep header composition out of the large track renderer for ART verifier safety. */
@Composable
internal fun TimelineHeader(
    durationMs: Long,
    headerKeyframeClip: MediaClip?,
    playheadMs: Long,
    onToggleKeyframe: (String) -> Unit,
    onRevealPlayhead: () -> Unit,
    multiSelectMode: Boolean,
    onToggleMultiSelect: () -> Unit,
    onAddMedia: () -> Unit,
) {
    val headerHasKeyframe = headerKeyframeClip?.keyframes?.keyframes
        ?.any { abs(it.timeMs - playheadMs) <= 150L } == true
        Row(
            modifier = Modifier.fillMaxWidth().height(TIMELINE_HEADER_HEIGHT_DP.dp)
                .background(Color(0xFF151F2D)).padding(horizontal = 10.dp)
                .testTag("timeline-header"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(4.dp, 14.dp).background(Color(0xFF39D9D0), RoundedCornerShape(2.dp)))
            Row(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Timeline", color = Color(0xFFE7EDF6), fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                Text(TimeFormat.msToShort(durationMs), color = Color(0xFF8C9AAF),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp).testTag("timeline-duration"))
            }
            if (headerKeyframeClip != null) {
                val description = if (headerHasKeyframe) "Remove keyframe" else "Add keyframe"
                IconButton(
                    onClick = { onToggleKeyframe(headerKeyframeClip.id) },
                    modifier = Modifier.size(44.dp).testTag("timeline-header-keyframe"),
                ) {
                    Canvas(Modifier.size(20.dp).semantics { contentDescription = description }) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = size.width / 2f - 2.dp.toPx()
                        val diamond = Path().apply {
                            moveTo(cx, cy - radius)
                            lineTo(cx + radius, cy)
                            lineTo(cx, cy + radius)
                            lineTo(cx - radius, cy)
                            close()
                        }
                        val tint = if (headerHasKeyframe) Color(0xFF39D9D0) else Color(0xFF8C9AAF)
                        drawPath(diamond, tint, style = Stroke(width = 2.dp.toPx()))
                        val mark = Path().apply {
                            moveTo(cx - 3.dp.toPx(), cy)
                            lineTo(cx + 3.dp.toPx(), cy)
                            if (!headerHasKeyframe) {
                                moveTo(cx, cy - 3.dp.toPx())
                                lineTo(cx, cy + 3.dp.toPx())
                            }
                        }
                        drawPath(mark, tint, style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }
            IconButton(onClick = onRevealPlayhead,
                modifier = Modifier.size(44.dp).testTag("timeline-reveal-playhead")) {
                Icon(Icons.Default.CenterFocusStrong, "Reveal playhead", tint = Color(0xFF8C9AAF), modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onToggleMultiSelect,
                modifier = Modifier.size(44.dp).testTag("timeline-multiselect")) {
                Icon(Icons.Default.DoneAll,
                    if (multiSelectMode) "Exit multi-select" else "Select multiple clips",
                    tint = if (multiSelectMode) Color(0xFF39D9D0) else Color(0xFF8C9AAF),
                    modifier = Modifier.size(20.dp))
            }
            IconButton(
                onClick = onAddMedia,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF39D9D0).copy(alpha = 0.12f))
                    .testTag("timeline-header-add-media")
            ) {
                Icon(Icons.Default.Add, "Add clip", tint = Color(0xFF39D9D0), modifier = Modifier.size(20.dp))
            }
        }
}

@Composable
internal fun rememberTimelineKeyframeClip(
    clips: List<MediaClip>,
    selectedClipId: String?,
    selectedTextOverlayId: String?,
    selectedStickerId: String?,
    selectedAudioTrackId: String?,
    hiddenVideoLayers: Set<Int>,
    playheadMs: Long,
    durationMs: Long,
): MediaClip? = remember(clips, selectedClipId, selectedTextOverlayId,
    selectedStickerId, selectedAudioTrackId, hiddenVideoLayers, playheadMs, durationMs) {
    selectedKeyframeVideo(EditorState(
        project = Project("timeline-header", "Timeline", durationMs, clips = clips),
        selectedClipId = selectedClipId,
        selectedTextOverlayId = selectedTextOverlayId,
        selectedStickerId = selectedStickerId,
        selectedAudioTrackId = selectedAudioTrackId,
        hiddenVideoLayers = hiddenVideoLayers,
        playerPositionMs = playheadMs,
    ))
}
