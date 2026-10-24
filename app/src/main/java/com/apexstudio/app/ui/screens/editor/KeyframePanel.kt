package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeCurve
import com.apexstudio.app.domain.model.KeyframeParam
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette
import com.apexstudio.app.util.TimeFormat
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow

enum class KeyframePropertyFilter(val label: String, val color: Color) {
    ALL("All", ApexPalette.NeonCyan),
    POSITION("Position", ApexPalette.NeonCyan),
    SCALE("Scale", ApexPalette.TrackVideo),
    ROTATION("Rotation", ApexPalette.NeonPurple),
    OPACITY("Opacity", ApexPalette.NeonEmerald),
    VOLUME("Volume", Color(0xFF10B981)),
    EFFECT("FX / Filter", Color(0xFFF59E0B)),
    SATURATION("Saturation", Color(0xFF22D3EE))
}

/**
 * Interactive Keyframe Editor for Apex Studio.
 *
 * Features:
 * 1. Timeline ruler with interactive diamond markers for all keyframes.
 * 2. Draggable keyframes along ruler to dynamically adjust timeMs.
 * 3. Property filter tabs: Position, Scale, Rotation, Opacity, All.
 * 4. Add keyframe at playhead with current interpolated values.
 * 5. Value sliders (playhead model: they show the interpolated value
 *    AT THE PLAYHEAD, and a change auto-generates a keyframe there —
 *    CapCut behaviour) + easing curve selectors for the active keyframe.
 * 6. Visual playhead indicator synchronized with player.
 *
 * [onUpsertParam] is the CapCut-style auto-keyframe callback: a slider
 * change at the playhead upserts one parameter there (no manual "+"
 * needed once the track has keyframes). Required (no silent-drop
 * fallback): every slider change must reach the upsert.
 */
@Composable
fun KeyframePanel(
    track: KeyframeTrack,
    playheadMs: Long,
    clipDurationMs: Long = 10_000L,
    canAdd: Boolean = true,
    onAdd: (Long) -> Unit,
    onUpdate: (Keyframe) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onUpsertParam: (KeyframeParam, Float) -> Unit,
    onPaste: ((Keyframe, Long) -> Unit)? = null,
    atPlayheadTransform: AnimatedTransform? = null,
    selectedKeyframeTimeMs: Long? = null,
    openCurveEditorOnOpen: Boolean = false,
    onCurveEditorDismiss: () -> Unit = {},
    initialPropertyFilter: KeyframePropertyFilter = KeyframePropertyFilter.ALL
) {
    val sorted = remember(track) { track.sorted().keyframes }
    var copiedKeyframe by remember { mutableStateOf<Keyframe?>(null) }
    var selectedKeyframeId by remember(sorted, selectedKeyframeTimeMs) {
        val anchorMs = selectedKeyframeTimeMs ?: playheadMs
        mutableStateOf(sorted.minByOrNull { kotlin.math.abs(it.timeMs - anchorMs) }?.id)
    }
    var curveEditorOpen by remember(openCurveEditorOnOpen, selectedKeyframeTimeMs) {
        mutableStateOf(openCurveEditorOnOpen)
    }
    var propertyFilter by remember(initialPropertyFilter) { mutableStateOf(initialPropertyFilter) }
    val effectiveDuration = clipDurationMs.coerceAtLeast(1000L)

    val activeKeyframe = sorted.firstOrNull { it.id == selectedKeyframeId }
        ?: sorted.firstOrNull()

    // CapCut-style playhead model: the sliders below show the
    // interpolated value AT THE PLAYHEAD, and a change auto-generates
    // a keyframe there (upsert) once the track has keyframes — no
    // manual "+" needed. The inspector (curve chips, Delete) still
    // operates on the SELECTED keyframe.
    val atPlayhead = atPlayheadTransform ?: track.interpolateAt(playheadMs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .testTag("keyframe-panel-root")
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        // This compact header remains pinned while the cards below scroll sideways.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(38.dp)
        ) {
            Icon(Icons.Default.Animation, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(6.dp))
            Text("Keyframe Studio", color = ApexPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    val prev = sorted.filter { it.timeMs < playheadMs }.maxByOrNull { it.timeMs }
                    if (prev != null) selectedKeyframeId = prev.id
                },
                enabled = sorted.any { it.timeMs < playheadMs },
                modifier = Modifier.size(38.dp)
            ) { Icon(Icons.Default.FastRewind, contentDescription = "Prev keyframe", tint = ApexPalette.TextSecondary, modifier = Modifier.size(17.dp)) }
            IconButton(
                onClick = {
                    val next = sorted.filter { it.timeMs > playheadMs }.minByOrNull { it.timeMs }
                    if (next != null) selectedKeyframeId = next.id
                },
                enabled = sorted.any { it.timeMs > playheadMs },
                modifier = Modifier.size(38.dp)
            ) { Icon(Icons.Default.FastForward, contentDescription = "Next keyframe", tint = ApexPalette.TextSecondary, modifier = Modifier.size(17.dp)) }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(30.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyframePropertyFilter.values().forEach { filter ->
                val active = propertyFilter == filter
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(7.dp))
                        .background(if (active) filter.color.copy(alpha = 0.22f) else ApexPalette.BgBase)
                        .border(1.dp, if (active) filter.color else ApexPalette.BorderGlass, RoundedCornerShape(7.dp))
                        .clickable { propertyFilter = filter }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(filter.label, color = if (active) filter.color else ApexPalette.TextSecondary,
                        fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        var rulerWidthPx by remember { mutableFloatStateOf(1f) }
        var rulerHeightPx by remember { mutableFloatStateOf(1f) }
        val density = LocalDensity.current

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState()).testTag("keyframe-panel-cards"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Timeline and pin actions have their own reachable card.
            Column(
                modifier = Modifier.width(280.dp).fillMaxHeight().keyframePanelCard().padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("Timeline · ${sorted.size} pins", color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.BgBase).border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                        .onSizeChanged { rulerWidthPx = it.width.toFloat(); rulerHeightPx = it.height.toFloat() }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var t = 0L
                        while (t <= effectiveDuration) {
                            val x = (t.toFloat() / effectiveDuration) * size.width
                            drawLine(ApexPalette.TextPrimary.copy(alpha = 0.12f), Offset(x, 0f), Offset(x, size.height), 1f)
                            t += 1000L
                        }
                    }
                    val playheadX = (playheadMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f) * rulerWidthPx
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(ApexPalette.NeonCyan, Offset(playheadX, 0f), Offset(playheadX, size.height), 2f)
                    }
                    sorted.forEach { kf ->
                        val isSelected = kf.id == selectedKeyframeId
                        val kfXPx = (kf.timeMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f) * rulerWidthPx
                        Box(
                            modifier = Modifier.offset {
                                IntOffset((kfXPx - 14.dp.toPx()).toInt(), ((rulerHeightPx - 28.dp.toPx()) / 2f).toInt())
                            }.size(28.dp)
                                .pointerInput(kf.id, effectiveDuration, rulerWidthPx) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaMs = (dragAmount / rulerWidthPx.coerceAtLeast(1f) * effectiveDuration).toLong()
                                        selectedKeyframeId = kf.id
                                        onUpdate(kf.copy(timeMs = (kf.timeMs + deltaMs).coerceIn(0L, effectiveDuration)))
                                    }
                                }.clickable {
                                    selectedKeyframeId = kf.id
                                    curveEditorOpen = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(if (isSelected) 14.dp else 10.dp).graphicsLayer(rotationZ = 45f)
                                .background(if (isSelected) ApexPalette.NeonCyan else propertyFilter.color)
                                .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else Color.Black.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                        .background(if (canAdd) ApexPalette.NeonCyan else ApexPalette.BorderGlass)
                        .clickable(enabled = canAdd) { onAdd(playheadMs) }.padding(horizontal = 7.dp, vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Add @ ${TimeFormat.msToShort(playheadMs)}", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (activeKeyframe != null) {
                        IconButton(onClick = { onRemove(activeKeyframe.id) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected keyframe", tint = ApexPalette.Danger, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (sorted.isNotEmpty()) {
                        Text("Clear", color = ApexPalette.TextTertiary, fontSize = 10.sp,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onClear() }.padding(6.dp))
                    }
                }
                Text("Select or drag a pin to edit its time", color = ApexPalette.TextTertiary, fontSize = 10.sp)
            }

            Column(
                Modifier.width(220.dp).fillMaxHeight().keyframePanelCard().padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("Pin actions", color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                KeyframeAction("Copy selected", activeKeyframe != null) { copiedKeyframe = activeKeyframe }
                KeyframeAction("Paste at playhead", copiedKeyframe != null && canAdd && onPaste != null) {
                    copiedKeyframe?.let { onPaste?.invoke(it, playheadMs) }
                }
                KeyframeAction("Reset selected", activeKeyframe != null) {
                    activeKeyframe?.let { onUpdate(Keyframe.identity(it.timeMs, it.id).copy(saturation = 1f)) }
                }
                Text("Copy keeps all pin values. Paste creates or replaces the pin at the playhead.",
                    color = ApexPalette.TextTertiary, fontSize = 10.sp)
            }

            if (activeKeyframe == null) {
                Column(Modifier.width(280.dp).fillMaxHeight().keyframePanelCard().padding(12.dp), verticalArrangement = Arrangement.Center) {
                    Text("No keyframes on this clip. Tap Add at the playhead to pin transform values.", color = ApexPalette.TextTertiary, fontSize = 12.sp)
                }
            } else {
                val sliderCard: @Composable (String, @Composable ColumnScope.() -> Unit) -> Unit = { title, content ->
                    Column(Modifier.width(280.dp).fillMaxHeight().keyframePanelCard().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Changes set values at playhead", color = ApexPalette.TextTertiary, fontSize = 9.sp)
                        content()
                    }
                }
                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.SCALE || propertyFilter == KeyframePropertyFilter.ROTATION) {
                    sliderCard("Transform") {
                        if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.SCALE) EnhancedKeyframeSlider(
                            label = "Scale", value = atPlayhead.scale, range = 0.1f..4f, unit = "x", color = ApexPalette.TrackVideo,
                            quickChips = listOf(0.5f to "0.5x", 1f to "1x Norm", 1.25f to "1.25x", 1.5f to "1.5x", 2f to "2x", 3f to "3x"), step = 0.05f, defaultValue = 1f,
                            onValueChange = { onUpsertParam(KeyframeParam.SCALE, it) })
                        if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.ROTATION) EnhancedKeyframeSlider(
                            label = "Rotation", value = atPlayhead.rotationDeg, range = -360f..360f, unit = "°", color = ApexPalette.NeonPurple,
                            quickChips = listOf(-180f to "-180°", -90f to "-90°", 0f to "0° Reset", 90f to "90°", 180f to "180°", 360f to "360°"), step = 5f, defaultValue = 0f,
                            onValueChange = { onUpsertParam(KeyframeParam.ROTATION, it) })
                    }
                }
                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.POSITION) {
                    sliderCard("Position") {
                        EnhancedKeyframeSlider(label = "X", value = atPlayhead.translateX, range = -1f..1f, color = ApexPalette.NeonCyan,
                            quickChips = listOf(-0.5f to "Left", 0f to "Center", 0.5f to "Right"), step = 0.05f, defaultValue = 0f,
                            onValueChange = { onUpsertParam(KeyframeParam.TRANSLATE_X, it) })
                        EnhancedKeyframeSlider(label = "Y", value = atPlayhead.translateY, range = -1f..1f, color = ApexPalette.NeonCyan,
                            quickChips = listOf(-0.5f to "Top", 0f to "Center", 0.5f to "Bottom"), step = 0.05f, defaultValue = 0f,
                            onValueChange = { onUpsertParam(KeyframeParam.TRANSLATE_Y, it) })
                    }
                }
                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.OPACITY || propertyFilter == KeyframePropertyFilter.SATURATION) {
                    sliderCard("Appearance") {
                        if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.OPACITY) EnhancedKeyframeSlider(
                            label = "Opacity", value = atPlayhead.opacity, range = 0f..1f, unit = "%", displayMultiplier = 100f, color = ApexPalette.NeonEmerald,
                            quickChips = listOf(0f to "0% Fade", .25f to "25%", .5f to "50%", .75f to "75%", 1f to "100% Solid"), step = .05f, defaultValue = 1f,
                            onValueChange = { onUpsertParam(KeyframeParam.OPACITY, it) })
                        if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.SATURATION) EnhancedKeyframeSlider(
                            label = "Saturation", value = atPlayhead.saturation ?: 1f, range = 0f..3f, unit = "%", displayMultiplier = 100f, color = Color(0xFF22D3EE),
                            quickChips = listOf(0f to "0% B&W", .5f to "50%", 1f to "100% Normal", 1.5f to "150%", 2f to "200%"), step = .05f, defaultValue = 1f,
                            onValueChange = { onUpsertParam(KeyframeParam.SATURATION, it) })
                    }
                }
                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.VOLUME) {
                    sliderCard("Audio") {
                        EnhancedKeyframeSlider(label = "Volume", value = atPlayhead.volume, range = 0f..1f, unit = "%", displayMultiplier = 100f, color = Color(0xFF10B981),
                            quickChips = listOf(0f to "Mute", .5f to "50%", 1f to "100%"), step = .05f, defaultValue = 1f,
                            onValueChange = { onUpsertParam(KeyframeParam.VOLUME, it) })
                    }
                }
                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.EFFECT) {
                    sliderCard("Effects") {
                        EnhancedKeyframeSlider(label = "Light Shift", value = atPlayhead.filterIntensity, range = 0f..2f, unit = "x", color = Color(0xFFFBBF24),
                            quickChips = listOf(0f to "Off", 1f to "Normal", 1.5f to "High"), step = .05f, defaultValue = 1f,
                            onValueChange = { onUpsertParam(KeyframeParam.FILTER_INTENSITY, it) })
                        EnhancedKeyframeSlider(label = "FX Intensity", value = atPlayhead.effectIntensity, range = 0f..1f, unit = "%", displayMultiplier = 100f, color = Color(0xFFF59E0B),
                            quickChips = listOf(0f to "0%", .5f to "50%", 1f to "100%"), step = .05f, defaultValue = 1f,
                            onValueChange = { onUpsertParam(KeyframeParam.EFFECT_INTENSITY, it) })
                    }
                }

                // The selected pin's interpolation and Bezier preview remain editable in their own sideways card.
                Column(Modifier.width(280.dp).fillMaxHeight().keyframePanelCard().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Selected · ${TimeFormat.formatMs(activeKeyframe.timeMs)}", color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Interpolation · ${curveLabel(activeKeyframe.curve)}", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF13131F))
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp)).clickable { curveEditorOpen = true }.padding(8.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = size.width; val h = size.height
                            drawLine(Color(0xFF2E2E3E), Offset(0f, h / 2f), Offset(w, h / 2f), 1f)
                            val path = Path()
                            for (i in 0..60) {
                                val t = i / 60f
                                val eased = curveProgress(activeKeyframe.curve, t)
                                val px = t * w; val py = h - eased * h
                                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                            }
                            drawPath(path, ApexPalette.NeonCyan, style = Stroke(width = 2.5f))
                            drawCircle(ApexPalette.TextPrimary, 4f, Offset(0f, h)); drawCircle(ApexPalette.NeonCyan, 4f, Offset(w, 0f))
                        }
                        Text("Tap to edit curve", color = ApexPalette.TextSecondary, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopStart))
                    }
                }
            }
        }
    }
    if (curveEditorOpen && activeKeyframe != null) {
        CurveEditorBottomSheet(
            selected = activeKeyframe.curve,
            onSelect = { curve ->
                onUpdate(activeKeyframe.copy(curve = curve))
                curveEditorOpen = false
                onCurveEditorDismiss()
            },
            onDismiss = {
                curveEditorOpen = false
                onCurveEditorDismiss()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurveEditorBottomSheet(
    selected: KeyframeCurve,
    onSelect: (KeyframeCurve) -> Unit,
    onDismiss: () -> Unit
) {
    val curves = listOf(KeyframeCurve.LINEAR, KeyframeCurve.BEZIER, KeyframeCurve.EASE_IN, KeyframeCurve.EASE_OUT)
    val selectedChoice = selected.curveChoice()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ApexPalette.BgElevated,
        contentColor = ApexPalette.TextPrimary,
        modifier = Modifier.testTag("curve-editor-bottom-sheet")
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Curve editor", color = ApexPalette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Choose how this keyframe eases into the next one.", color = ApexPalette.TextSecondary, fontSize = 12.sp)
            Box(
                Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(10.dp))
                    .background(ApexPalette.BgBase).border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp)).padding(10.dp)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawLine(ApexPalette.BorderGlass, Offset(0f, h), Offset(w, 0f), 1f)
                    val path = Path()
                    for (i in 0..60) {
                        val t = i / 60f
                        val eased = curveProgress(selectedChoice, t)
                        val point = Offset(t * w, h - eased * h)
                        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(path, ApexPalette.NeonCyan, style = Stroke(width = 3f))
                    drawCircle(Color.White, 4f, Offset(0f, h))
                    drawCircle(ApexPalette.NeonCyan, 4f, Offset(w, 0f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                curves.forEach { curve ->
                    val active = curve == selectedChoice
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (active) ApexPalette.NeonCyan.copy(alpha = .2f) else ApexPalette.BgBase)
                            .border(1.dp, if (active) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                            .clickable { onSelect(curve) }
                            .testTag("curve-option-${curve.name.lowercase()}")
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(curveLabel(curve), color = if (active) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                            fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private fun KeyframeCurve.curveChoice(): KeyframeCurve = when (this) {
    KeyframeCurve.LINEAR -> KeyframeCurve.LINEAR
    KeyframeCurve.EASE_IN -> KeyframeCurve.EASE_IN
    KeyframeCurve.EASE_OUT -> KeyframeCurve.EASE_OUT
    else -> KeyframeCurve.BEZIER
}

private fun curveLabel(curve: KeyframeCurve): String = when (curve.curveChoice()) {
    KeyframeCurve.LINEAR -> "Linear"
    KeyframeCurve.BEZIER -> "Smooth"
    KeyframeCurve.EASE_IN -> "Ease In"
    KeyframeCurve.EASE_OUT -> "Ease Out"
    else -> "Smooth"
}

private fun curveProgress(curve: KeyframeCurve, fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return when (curve.curveChoice()) {
        KeyframeCurve.LINEAR -> t
        KeyframeCurve.EASE_IN -> t * t
        KeyframeCurve.EASE_OUT -> 1f - (1f - t) * (1f - t)
        else -> t * t * (3f - 2f * t)
    }
}

@Composable
private fun KeyframeAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(7.dp))
            .background(if (enabled) ApexPalette.NeonPurple.copy(alpha = .25f) else ApexPalette.BgElevated)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) ApexPalette.TextPrimary else ApexPalette.TextTertiary, fontSize = 11.sp)
    }
}

private fun Modifier.keyframePanelCard(): Modifier = this
    .clip(RoundedCornerShape(11.dp))
    .background(ApexPalette.BgBase)
    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(11.dp))

@Composable
private fun EnhancedKeyframeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String = "",
    displayMultiplier: Float = 1f,
    color: Color,
    quickChips: List<Pair<Float, String>> = emptyList(),
    step: Float = 0.05f,
    defaultValue: Float = 1f,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        // Label + Decrement + Slider + Increment + Value + Reset
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                color = ApexPalette.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(68.dp)
            )

            // Step Nudge -
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E2232))
                    .border(1.dp, Color(0xFF2E344A), RoundedCornerShape(4.dp))
                    .clickable {
                        val next = (value - step).coerceIn(range.start, range.endInclusive)
                        onValueChange(next)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Step Down", tint = Color(0xFFCBD5E1), modifier = Modifier.size(12.dp))
            }

            Spacer(Modifier.width(4.dp))

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = ApexPalette.BorderGlass
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(4.dp))

            // Step Nudge +
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E2232))
                    .border(1.dp, Color(0xFF2E344A), RoundedCornerShape(4.dp))
                    .clickable {
                        val next = (value + step).coerceIn(range.start, range.endInclusive)
                        onValueChange(next)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Step Up", tint = Color(0xFFCBD5E1), modifier = Modifier.size(12.dp))
            }

            Spacer(Modifier.width(6.dp))

            // Formatted Value Display
            val formatted = if (displayMultiplier != 1f) {
                "${(value * displayMultiplier).toInt()}$unit"
            } else if (unit == "°") {
                "${value.toInt()}$unit"
            } else {
                "${String.format(java.util.Locale.US, "%.2f", value)}$unit"
            }
            Text(
                text = formatted,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.width(46.dp)
            )

            // Reset to Default button
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange(defaultValue) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
            }
        }

        // Quick Preset Chips
        if (quickChips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 68.dp, top = 2.dp, bottom = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                quickChips.forEach { (chipVal, chipLabel) ->
                    val isChipActive = kotlin.math.abs(value - chipVal) < 0.02f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isChipActive) color.copy(alpha = 0.25f) else Color(0xFF161924))
                            .border(1.dp, if (isChipActive) color else Color(0xFF262C3E), RoundedCornerShape(4.dp))
                            .clickable { onValueChange(chipVal) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = chipLabel,
                            color = if (isChipActive) color else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = if (isChipActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
