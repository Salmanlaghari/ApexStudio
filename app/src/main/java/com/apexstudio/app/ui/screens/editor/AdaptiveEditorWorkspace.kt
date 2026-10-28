package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.KeyframeParam
import com.apexstudio.app.presentation.state.EditorState
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.ui.theme.EditorPalette

/**
 * PR169/170's preview-first shell stacks preview and timeline by default.
 * The side inspector remains available to hosts that explicitly opt into landscape.
 */
@Composable
internal fun ColumnScope.AdaptiveEditorWorkspace(
    landscape: Boolean,
    horizontalProjectMode: Boolean = false,
    focused: Boolean,
    fullscreen: Boolean,
    preview: @Composable ColumnScope.() -> Unit,
    timeline: @Composable ColumnScope.() -> Unit,
    inspector: @Composable ColumnScope.() -> Unit,
) {
    var inspectorOpen by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().weight(1f).testTag("adaptive-editor-workspace")) {
        if (!landscape || fullscreen) {
            if (fullscreen) {
                preview()
                timeline()
            } else if (horizontalProjectMode && !focused) {
                Column(Modifier.fillMaxWidth().weight(1f).testTag("horizontal-project-workspace")) {
                    Column(
                        Modifier.fillMaxWidth().wrapContentHeight().testTag("horizontal-project-preview-pane"),
                        content = preview
                    )
                    Column(
                        Modifier.fillMaxWidth().weight(1f).testTag("horizontal-project-timeline-pane"),
                        content = timeline
                    )
                }
            } else if (focused) {
                Column(
                    Modifier.fillMaxWidth().weight(0.001f).testTag("portrait-preview-pane"),
                    content = preview
                )
                Column(
                    Modifier.fillMaxWidth().weight(1f).testTag("portrait-timeline-pane"),
                    content = timeline
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().weight(1f).testTag("portrait-preview-pane"),
                    content = preview
                )
                Column(
                    Modifier.fillMaxWidth().wrapContentHeight().testTag("portrait-timeline-pane"),
                    content = timeline
                )
            }
        } else {
            Row(Modifier.fillMaxWidth().weight(if (focused) .001f else .48f).testTag("landscape-preview-row")) {
                Column(Modifier.weight(1f).fillMaxHeight().testTag("landscape-preview"), content = preview)
                if (!focused) {
                    Column(Modifier.width(if (inspectorOpen) 224.dp else 48.dp).fillMaxHeight()
                        .background(EditorPalette.BgElevated).testTag("landscape-inspector")) {
                        TextButton(onClick = { inspectorOpen = !inspectorOpen },
                            modifier = Modifier.fillMaxWidth().height(48.dp).semantics {
                                contentDescription = if (inspectorOpen) "Hide video properties" else "Show video properties"
                            }) {
                            Text(if (inspectorOpen) "Hide properties ›" else "‹", maxLines = 1, fontSize = 11.sp)
                        }
                        if (inspectorOpen) {
                            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp), content = inspector)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().weight(if (focused) 1f else .52f).testTag("landscape-timeline"), content = timeline)
        }
    }
}

/** Direct controls edit the selected video's real keyframe track, never a display-only draft. */
@Composable
internal fun LandscapeVideoInspector(state: EditorState, vm: EditorViewModel) {
    val clip = selectedKeyframeVideo(state)
    if (clip == null) {
        Text("Select a video layer for transform keyframes. Text, audio and sticker tools stay in the dock.",
            color = EditorPalette.TextSecondary, fontSize = 12.sp)
        return
    }
    Text(clip.name, color = EditorPalette.TextPrimary, fontSize = 12.sp, maxLines = 1)
    val transform = clip.keyframes.interpolateAt(state.playerPositionMs)
    TextButton(onClick = { vm.toggleFrameKeyframeAtPlayheadFor(clip.id) }) {
        Text("Add / remove keyframe", fontSize = 12.sp)
    }
    if (clip.keyframes.isEmpty()) {
        Text("Add a keyframe to enable animated properties.", color = EditorPalette.TextSecondary, fontSize = 11.sp)
    }
    fun update(param: KeyframeParam, value: Float) {
        vm.upsertFrameKeyframeParam(clip.id, vm.livePlayheadMs(), param, value)
    }
    InspectorSlider("Position X", transform.translateX, -1f..1f, !clip.keyframes.isEmpty(), { update(KeyframeParam.TRANSLATE_X, it) }, { vm.flushProject() })
    InspectorSlider("Position Y", transform.translateY, -1f..1f, !clip.keyframes.isEmpty(), { update(KeyframeParam.TRANSLATE_Y, it) }, { vm.flushProject() })
    InspectorSlider("Scale", transform.scale, .1f..4f, !clip.keyframes.isEmpty(), { update(KeyframeParam.SCALE, it) }, { vm.flushProject() })
    InspectorSlider("Rotation", transform.rotationDeg, -180f..180f, !clip.keyframes.isEmpty(), { update(KeyframeParam.ROTATION, it) }, { vm.flushProject() })
    InspectorSlider("Opacity", transform.opacity, 0f..1f, !clip.keyframes.isEmpty(), { update(KeyframeParam.OPACITY, it) }, { vm.flushProject() })
}

@Composable
private fun InspectorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    onValue: (Float) -> Unit, onFinished: () -> Unit) {
    Text("$label  ${"%.2f".format(java.util.Locale.ROOT, value)}", color = EditorPalette.TextSecondary, fontSize = 11.sp)
    Slider(value = value.coerceIn(range), onValueChange = onValue, enabled = enabled,
        valueRange = range, onValueChangeFinished = onFinished, modifier = Modifier.fillMaxWidth().height(44.dp))
}

@Composable
internal fun LandscapeTimelineControls(state: EditorState, vm: EditorViewModel, seek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val position by vm.playerPositionFlow.collectAsState()
    Row(modifier.horizontalScroll(rememberScrollState())) {
        TextButton(onClick = { vm.togglePlay() }, enabled = state.durationMs > 0L) {
            Text(if (state.isPlaying) "Pause" else "Play", fontSize = 11.sp)
        }
        TextButton(onClick = { vm.stepFrame(false); seek(vm.livePlayheadMs()) }, enabled = state.durationMs > 0L,
            modifier = Modifier.widthIn(min = 44.dp).testTag("previous-frame")) { Text("−1f", fontSize = 11.sp) }
        TextButton(onClick = { vm.stepFrame(true); seek(vm.livePlayheadMs()) }, enabled = state.durationMs > 0L,
            modifier = Modifier.widthIn(min = 44.dp).testTag("next-frame")) { Text("+1f", fontSize = 11.sp) }
        Text(preciseEditorTime(position), color = EditorPalette.NeonCyan, fontSize = 11.sp,
            modifier = Modifier.padding(top = 14.dp).testTag("precise-timecode"))
        TextButton(onClick = { vm.setTimelineZoom(state.timelineZoom / 1.5f) }, modifier = Modifier.widthIn(min = 44.dp)) { Text("−", fontSize = 16.sp) }
        TextButton(onClick = { vm.setTimelineZoom(state.timelineZoom * 1.5f) }, modifier = Modifier.widthIn(min = 44.dp)) { Text("+", fontSize = 16.sp) }
        TextButton(onClick = { vm.toggleMagneticSnapping() }) { Text(if (state.magneticSnapping) "Snap on" else "Snap off", fontSize = 11.sp) }
    }
}

internal fun preciseEditorTime(ms: Long): String {
    val value = ms.coerceAtLeast(0L)
    return String.format(java.util.Locale.ROOT, "%02d:%02d.%03d", value / 60_000L, value / 1000L % 60L, value % 1000L)
}
