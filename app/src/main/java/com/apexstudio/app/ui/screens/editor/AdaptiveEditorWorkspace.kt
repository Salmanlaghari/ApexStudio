package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
    timeline: @Composable ColumnScope.(fillTimelinePane: Boolean) -> Unit,
    inspector: @Composable ColumnScope.() -> Unit,
    compactTimelinePaneHeight: Dp = 160.dp,
    manualTimelinePaneHeight: Dp? = null,
    onManualTimelinePaneHeightChange: (Dp?) -> Unit = {},
) {
    var inspectorOpen by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().weight(1f).testTag("adaptive-editor-workspace")) {
        if (!landscape || fullscreen) {
            if (fullscreen) {
                preview()
                timeline(false)
            } else if (horizontalProjectMode && !focused) {
                Column(Modifier.fillMaxWidth().weight(1f).testTag("horizontal-project-workspace")) {
                    Column(
                        Modifier.fillMaxWidth().wrapContentHeight().testTag("horizontal-project-preview-pane"),
                        content = preview
                    )
                    Column(
                        Modifier.fillMaxWidth().weight(1f).testTag("horizontal-project-timeline-pane"),
                        content = { timeline(true) }
                    )
                }
            } else if (focused) {
                Column(
                    Modifier.fillMaxWidth().wrapContentHeight().testTag("portrait-preview-pane"),
                    content = preview
                )
                Column(
                    Modifier.fillMaxWidth().weight(1f).testTag("portrait-timeline-pane"),
                    content = { timeline(false) }
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val availableWorkspaceHeight = maxHeight
                    val effectiveTimelineHeight = manualTimelinePaneHeight?.let {
                        clampTimelinePaneHeight(
                            requestedTimelinePaneHeight = it,
                            availableWorkspaceHeight = availableWorkspaceHeight,
                            compactTimelinePaneHeight = compactTimelinePaneHeight,
                        )
                    }
                    val currentHeightState = rememberUpdatedState(effectiveTimelineHeight)
                    val availableHeightState = rememberUpdatedState(availableWorkspaceHeight)
                    val compactHeightState = rememberUpdatedState(compactTimelinePaneHeight)
                    val onHeightChangeState = rememberUpdatedState(onManualTimelinePaneHeightChange)
                    val density = LocalDensity.current
                    Column(
                        Modifier.fillMaxSize().pointerInput(
                            availableWorkspaceHeight, compactTimelinePaneHeight, density
                        ) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                                val startHeight = clampTimelinePaneHeight(
                                    requestedTimelinePaneHeight =
                                        currentHeightState.value ?: compactHeightState.value,
                                    availableWorkspaceHeight = availableHeightState.value,
                                    compactTimelinePaneHeight = compactHeightState.value,
                                )
                                val startHeightPx = with(density) { startHeight.toPx() }
                                val handleHeightPx = with(density) { TIMELINE_RESIZE_HANDLE_HEIGHT.toPx() }
                                val handleHitSlopPx = with(density) { 12.dp.toPx() }
                                val handleTop = size.height - startHeightPx - handleHeightPx
                                val handleBottom = size.height - startHeightPx
                                if (down.position.y >= handleTop - handleHitSlopPx &&
                                    down.position.y <= handleBottom + handleHitSlopPx
                                ) {
                                    var dragging = false
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        val delta = change.position - down.position
                                        if (!dragging &&
                                            kotlin.math.abs(delta.y) > viewConfiguration.touchSlop &&
                                            kotlin.math.abs(delta.y) >= kotlin.math.abs(delta.x)
                                        ) {
                                            dragging = true
                                        }
                                        if (dragging) {
                                            val requested = with(density) {
                                                (startHeightPx - delta.y).toDp()
                                            }
                                            val bounded = clampTimelinePaneHeight(
                                                requestedTimelinePaneHeight = requested,
                                                availableWorkspaceHeight = availableHeightState.value,
                                                compactTimelinePaneHeight = compactHeightState.value,
                                            )
                                            val isCompact =
                                                bounded.value <= compactHeightState.value.value + 1f
                                            onHeightChangeState.value(if (isCompact) null else bounded)
                                            change.consume()
                                        }
                                        if (!change.pressed) break
                                    }
                                }
                            }
                        }
                    ) {
                        Column(
                            Modifier.fillMaxWidth().weight(1f).testTag("portrait-preview-pane"),
                            content = preview
                        )
                        TimelineResizeHandle(onReset = { onManualTimelinePaneHeightChange(null) })
                        Column(
                            Modifier.fillMaxWidth()
                                .then(
                                    if (effectiveTimelineHeight == null) Modifier.wrapContentHeight()
                                    else Modifier.height(effectiveTimelineHeight)
                                )
                                .testTag("portrait-timeline-pane"),
                            content = { timeline(effectiveTimelineHeight != null) }
                        )
                    }
                }
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
            Column(Modifier.fillMaxWidth().weight(if (focused) 1f else .52f).testTag("landscape-timeline"), content = { timeline(false) })
        }
    }
}

private val TIMELINE_RESIZE_HANDLE_HEIGHT = 32.dp
private val TIMELINE_PREVIEW_MIN_HEIGHT = 180.dp

internal fun clampTimelinePaneHeight(
    requestedTimelinePaneHeight: Dp,
    availableWorkspaceHeight: Dp,
    compactTimelinePaneHeight: Dp,
    minimumPreviewHeight: Dp = TIMELINE_PREVIEW_MIN_HEIGHT,
    resizeHandleHeight: Dp = TIMELINE_RESIZE_HANDLE_HEIGHT,
): Dp {
    val maximumTimelineHeight =
        (availableWorkspaceHeight - minimumPreviewHeight - resizeHandleHeight).coerceAtLeast(0.dp)
    val minimumTimelineHeight = compactTimelinePaneHeight.coerceAtLeast(0.dp).coerceAtMost(maximumTimelineHeight)
    return requestedTimelinePaneHeight.coerceIn(minimumTimelineHeight, maximumTimelineHeight)
}

@Composable
private fun TimelineResizeHandle(onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TIMELINE_RESIZE_HANDLE_HEIGHT)
            .semantics { contentDescription = "Resize timeline. Drag up or down; tap to reset to compact." }
            .clickable(role = Role.Button, onClick = onReset)
            .testTag("timeline-resize-handle"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(EditorPalette.TextSecondary.copy(alpha = 0.7f))
        )
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
