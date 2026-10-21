package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.apexstudio.app.domain.model.SpeedCurveMath
import com.apexstudio.app.domain.model.SpeedPoint
import kotlin.math.sqrt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.apexstudio.app.domain.model.SpeedPreset
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

/**
 * Compact speed controls for the editor. Rate presets and the custom rate
 * slider retain their separate callbacks; video clips also expose the
 * editable speed curve, and motion-blur controls retain their existing
 * state callbacks. The control cards scroll horizontally within the panel.
 */

@Composable
fun SpeedRampPanel(
    selectedClipId: String?,
    currentSpeed: Float,
    activeClipSpeed: Float,
    opticalFlowEnabled: Boolean = false,
    opticalFlowIntensity: Float = 0.5f,
    onSelectPreset: (SpeedPreset) -> Unit,
    onCustomSpeed: (Float) -> Unit,
    onToggleOpticalFlow: (Boolean) -> Unit = {},
    onChangeOpticalFlowIntensity: (Float) -> Unit = {},
    // Variable speed curve (PR-E). Empty = no curve (constant speed).
    speedPoints: List<com.apexstudio.app.domain.model.SpeedPoint> = emptyList(),
    clipIsVideo: Boolean = true,
    clipSourceDurationMs: Long = 0L,
    onCurveChanged: (List<com.apexstudio.app.domain.model.SpeedPoint>) -> Unit = {},
    onClearCurve: () -> Unit = {},
    onClose: () -> Unit
) {
    var sliderValue by remember(selectedClipId, activeClipSpeed) { mutableFloatStateOf(activeClipSpeed.coerceIn(0.1f, 10f)) }
    var motionBlurOn by remember(selectedClipId, opticalFlowEnabled) { mutableStateOf(opticalFlowEnabled) }
    var blurIntensity by remember(selectedClipId, opticalFlowIntensity) { mutableFloatStateOf(opticalFlowIntensity) }

    Column(
        modifier = Modifier.fillMaxWidth().height(300.dp)
            .testTag("speed-ramp-panel-root")
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(6.dp))
            Text("Speed & Motion", color = ApexPalette.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(240.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()).testTag("speed-ramp-panel-cards"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.width(280.dp).fillMaxHeight().speedPanelCard().padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("Playback rate", color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(if (selectedClipId == null) "Preview rate · ${formatRate(activeClipSpeed)}" else "Clip rate · ${formatRate(activeClipSpeed)}",
                    color = ApexPalette.TextSecondary, fontSize = 10.sp)
                val extendedPresets = listOf("0.1x" to .1f, "0.25x" to .25f, "0.5x" to .5f, "1x" to 1f, "2x" to 2f, "4x" to 4f, "8x" to 8f, "10x" to 10f)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(extendedPresets, key = { it.first }) { (label, mult) ->
                        val selected = kotlin.math.abs(sliderValue - mult) < .05f
                        SpeedPresetChip(label, selected) {
                            sliderValue = mult
                            val preset = SpeedPreset.values().firstOrNull { kotlin.math.abs(it.multiplier - mult) < 0.001f }
                            if (preset != null) onSelectPreset(preset) else onCustomSpeed(mult)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Custom · ${formatRate(sliderValue)}", color = ApexPalette.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    Text(if (sliderValue < 1f) "Slow" else if (sliderValue > 1f) "Fast" else "Normal",
                        color = if (sliderValue < 1f) Color(0xFF60A5FA) else if (sliderValue > 1f) Color(0xFFF59E0B) else ApexPalette.TextSecondary,
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onCustomSpeed(sliderValue) },
                    valueRange = .1f..10f,
                    colors = SliderDefaults.colors(thumbColor = ApexPalette.NeonCyan, activeTrackColor = ApexPalette.NeonCyan, inactiveTrackColor = ApexPalette.BorderGlass)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0.1x", color = ApexPalette.TextTertiary, fontSize = 9.sp)
                    Text("1x", color = ApexPalette.TextTertiary, fontSize = 9.sp)
                    Text("5x", color = ApexPalette.TextTertiary, fontSize = 9.sp)
                    Text("10x", color = ApexPalette.TextTertiary, fontSize = 9.sp)
                }
            }

            if (clipIsVideo) {
                Column(modifier = Modifier.width(300.dp).fillMaxHeight().speedPanelCard().padding(8.dp)) {
                    SpeedCurveEditor(
                        points = speedPoints,
                        baseSpeed = activeClipSpeed,
                        clipSourceDurationMs = clipSourceDurationMs,
                        onCurveChanged = onCurveChanged,
                        onClearCurve = onClearCurve
                    )
                }
            }

            Column(
                modifier = Modifier.width(280.dp).fillMaxHeight().speedPanelCard().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Optical Flow Motion Blur", color = ApexPalette.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Frame interpolation and directional blur", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                    }
                    Switch(
                        checked = motionBlurOn,
                        onCheckedChange = { motionBlurOn = it; onToggleOpticalFlow(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ApexPalette.NeonCyan,
                            uncheckedThumbColor = ApexPalette.TextTertiary, uncheckedTrackColor = ApexPalette.BgBase)
                    )
                }
                if (motionBlurOn) {
                    HorizontalDivider(color = ApexPalette.BorderGlass)
                    Text("Blur intensity · ${(blurIntensity * 100).toInt()}%", color = ApexPalette.TextSecondary, fontSize = 11.sp)
                    Slider(
                        value = blurIntensity,
                        onValueChange = { blurIntensity = it; onChangeOpticalFlowIntensity(it) },
                        valueRange = .1f..1f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF60A5FA), activeTrackColor = Color(0xFF60A5FA), inactiveTrackColor = ApexPalette.BorderGlass)
                    )
                }
            }
        }
    }
}

/**
 * Variable speed curve editor (PR-E): a CapCut-style ramping graph.
 *
 * - X = position in the clip (0% → 100%), Y = playback speed on a
 *   log₂ scale (0.1x at the bottom → 10x at the top, 1x centred).
 * - Tap the graph to add a control point (max 8); drag a point to
 *   reshape; endpoints stay pinned at 0% / 100% (only their speed
 *   moves). A selected interior point can be removed.
 * - Each point can be Smooth (default, eased PCHIP curve) or Sharp
 *   (the segment ending at it is a straight line — abrupt
 *   drop/rise, CapCut velocity-edit style). The "Smooth slow-mo"
 *   preset uses sharp drops/rises around a flat 0.1x hold.
 * - The curve is drawn with the same PCHIP interpolation the engine
 *   uses, so what you see is what the preview/export play.
 * - Drafting is local: the parent is only notified on gesture end
 *   (or preset tap), so state updates + project persistence don't
 *   spam while the finger is still down.
 */
@Composable
private fun SpeedCurveEditor(
    points: List<SpeedPoint>,
    baseSpeed: Float,
    clipSourceDurationMs: Long,
    onCurveChanged: (List<SpeedPoint>) -> Unit,
    onClearCurve: () -> Unit
) {
    val math = SpeedCurveMath
    val draftState = remember(points, baseSpeed) {
        mutableStateOf(points.ifEmpty { math.flat(baseSpeed) })
    }
    var selectedIndex by remember(points) { mutableIntStateOf(-1) }
    // Graph size (for gesture math) and the point being dragged.
    var graphSize by remember { mutableStateOf(IntSize.Zero) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    val draft = draftState.value
    val hasCurve = points.size >= 2
    val outMs = remember(draft, clipSourceDurationMs) {
        math.effectiveDurationMs(clipSourceDurationMs, draft)
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Speed Curve",
                color = ApexPalette.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            if (hasCurve) {
                Text(
                    text = "Curve active",
                    color = ApexPalette.NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable { selectedIndex = -1; onClearCurve() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) { Text("Reset", color = ApexPalette.TextSecondary, fontSize = 10.sp) }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Tap or drag to shape · select a point for Sharp / Remove",
            color = ApexPalette.TextSecondary,
            fontSize = 9.sp
        )
        Spacer(Modifier.height(8.dp))

        // Preset chips: one tap applies the preset curve.
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpeedCurveMath.CurvePreset.values().forEach { preset ->
                val isActive = hasCurve && draft == preset.points
                SpeedPresetChip(
                    label = preset.label,
                    selected = isActive,
                    onClick = {
                        draftState.value = preset.points
                        selectedIndex = -1
                        onCurveChanged(preset.points)
                    }
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Graph.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A0E18))
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { graphSize = it }
                    // Drag to move a point (repo-standard detector).
                    // A drag starting on empty graph adds a point at
                    // the drag-start position and moves it.
                    .pointerInput(points) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                val cur = draftState.value
                                val w = graphSize.width.toFloat()
                                val h = graphSize.height.toFloat()
                                if (w <= 0f || h <= 0f) return@detectDragGestures
                                val hitRadius = 28.dp.toPx()
                                var nearest = -1
                                var nearestDist = Float.MAX_VALUE
                                cur.forEachIndexed { i, p ->
                                    val dx = p.timeFraction * w - pos.x
                                    val dy = math.speedToGraphY(p.speed) * h - pos.y
                                    val d = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                    if (d < hitRadius && d < nearestDist) {
                                        nearest = i
                                        nearestDist = d
                                    }
                                }
                                if (nearest < 0 && cur.size < 8) {
                                    val added = SpeedPoint(
                                        timeFraction = (pos.x / w).coerceIn(0f, 1f),
                                        speed = math.graphYToSpeed(pos.y / h)
                                    )
                                    val withNew = (cur + added).sortedBy { it.timeFraction }
                                    draftState.value = withNew
                                    nearest = withNew.indexOf(added)
                                    selectedIndex = nearest
                                } else if (nearest >= 0) {
                                    selectedIndex = nearest
                                }
                                draggingIndex = nearest
                            },
                            onDragEnd = {
                                if (draggingIndex >= 0) {
                                    onCurveChanged(draftState.value)
                                }
                                draggingIndex = -1
                            },
                            onDragCancel = { draggingIndex = -1 },
                            onDrag = { change, dragAmount ->
                                val idx = draggingIndex
                                if (idx >= 0) {
                                    val w = graphSize.width.toFloat()
                                    val h = graphSize.height.toFloat()
                                    if (w > 0f && h > 0f) {
                                        val cur = draftState.value
                                        if (idx in cur.indices) {
                                            val p = cur[idx]
                                            val newX = p.timeFraction * w + dragAmount.x
                                            val newY = math.speedToGraphY(p.speed) * h + dragAmount.y
                                            val newF = when (idx) {
                                                0 -> 0f
                                                cur.lastIndex -> 1f
                                                else -> math.clampPointFraction(
                                                    cur, idx,
                                                    (newX / w).coerceIn(0f, 1f)
                                                )
                                            }
                                            val newS = math.graphYToSpeed(newY / h)
                                            val updated = cur.toMutableList()
                                            updated[idx] = p.copy(
                                                timeFraction = newF,
                                                speed = newS
                                            )
                                            draftState.value = updated
                                        }
                                    }
                                }
                                change.consume()
                            }
                        )
                    }
                    // Tap: select a point, or add one on empty graph.
                    // Cooperates with the drag detector via consumption:
                    // a real drag consumes the down, so no tap fires.
                    .pointerInput(points) {
                        detectTapGestures(
                            onTap = { pos ->
                                val cur = draftState.value
                                val w = graphSize.width.toFloat()
                                val h = graphSize.height.toFloat()
                                if (w <= 0f || h <= 0f) return@detectTapGestures
                                val hitRadius = 28.dp.toPx()
                                var nearest = -1
                                var nearestDist = Float.MAX_VALUE
                                cur.forEachIndexed { i, p ->
                                    val dx = p.timeFraction * w - pos.x
                                    val dy = math.speedToGraphY(p.speed) * h - pos.y
                                    val d = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                    if (d < hitRadius && d < nearestDist) {
                                        nearest = i
                                        nearestDist = d
                                    }
                                }
                                if (nearest >= 0) {
                                    selectedIndex = nearest
                                } else if (cur.size < 8) {
                                    val added = SpeedPoint(
                                        timeFraction = (pos.x / w).coerceIn(0f, 1f),
                                        speed = math.graphYToSpeed(pos.y / h)
                                    )
                                    val withNew = (cur + added).sortedBy { it.timeFraction }
                                    draftState.value = withNew
                                    selectedIndex = withNew.indexOf(added)
                                    onCurveChanged(withNew)
                                }
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height
                // Grid lines (log-spaced; 1x brighter).
                math.GRID_SPEEDS.forEach { sp ->
                    val y = math.speedToGraphY(sp) * h
                    drawLine(
                        color = if (sp == 1f) Color.White.copy(alpha = 0.35f)
                        else ApexPalette.TextPrimary.copy(alpha = 0.12f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }
                // Curve (same PCHIP the engine uses).
                val curve = draftState.value
                val path = Path()
                for (i in 0..100) {
                    val f = i / 100f
                    val s = math.speedAt(curve, f)
                    val x = f * w
                    val y = math.speedToGraphY(s) * h
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = ApexPalette.NeonCyan,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                // Control points as diamonds (keyframe style).
                curve.forEachIndexed { i, p ->
                    val cx = p.timeFraction * w
                    val cy = math.speedToGraphY(p.speed) * h
                    val r = if (i == selectedIndex) 11.dp.toPx() else 8.dp.toPx()
                    val diamond = Path().apply {
                        moveTo(cx, cy - r)
                        lineTo(cx + r, cy)
                        lineTo(cx, cy + r)
                        lineTo(cx - r, cy)
                        close()
                    }
                    drawPath(
                        path = diamond,
                        color = if (i == selectedIndex) ApexPalette.NeonCyan
                        else Color(0xFF0A0A0F)
                    )
                    drawPath(
                        path = diamond,
                        color = if (i == selectedIndex) Color.White
                        else ApexPalette.NeonCyan,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
            // Y-axis labels (clamped inside the box so the 10x / 0.1x
            // edge labels don't clip).
            math.GRID_SPEEDS.forEach { sp ->
                Text(
                    text = formatRate(sp),
                    color = ApexPalette.TextTertiary,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = 5.dp,
                            y = ((math.speedToGraphY(sp) * 76f).dp - 6.dp)
                                .coerceIn(0.dp, 64.dp)
                        )
                )
            }
        }
        // X-axis labels.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0%", color = ApexPalette.TextTertiary, fontSize = 10.sp)
            Text("50%", color = ApexPalette.TextTertiary, fontSize = 10.sp)
            Text("100%", color = ApexPalette.TextTertiary, fontSize = 10.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Output: ${formatSeconds(outMs)}  •  Source: ${formatSeconds(clipSourceDurationMs)}",
                color = ApexPalette.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            if (selectedIndex in 1 until draft.lastIndex) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val cur = draftState.value
                            draftState.value = cur.filterIndexed { i, _ -> i != selectedIndex }
                            selectedIndex = -1
                            onCurveChanged(draftState.value)
                        }
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                ) { Text("Remove point", color = Color(0xFFF87171), fontSize = 10.sp) }
            }
        }
        if (selectedIndex in draft.indices) {
            val p = draft[selectedIndex]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Point: ${(p.timeFraction * 100).toInt()}%  •  ${formatRate(p.speed)}" +
                        if (p.sharp) "  •  Sharp" else "  •  Smooth",
                    color = ApexPalette.NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                // Sharp toggle: makes the segment ENDING at this point
                // a straight line (abrupt drop/rise) instead of an
                // eased curve. Not applicable to the first point
                // (no segment ends there).
                if (selectedIndex >= 1) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val cur = draftState.value
                                val updated = cur.toMutableList()
                                updated[selectedIndex] = updated[selectedIndex].copy(sharp = !p.sharp)
                                draftState.value = updated
                                onCurveChanged(updated)
                            }
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                    ) {
                        Text(text = if (p.sharp) "Sharp: On" else "Sharp: Off",
                            color = if (p.sharp) ApexPalette.NeonCyan else ApexPalette.TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

private fun Modifier.speedPanelCard(): Modifier = this
    .clip(RoundedCornerShape(11.dp))
    .background(ApexPalette.BgBase)
    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(11.dp))

private fun formatSeconds(ms: Long): String {
    val s = ms / 1000f
    return if (s == s.toInt().toFloat()) "${s.toInt()}s" else "%.1fs".format(s)
}

@Composable
private fun SpeedPresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) ApexPalette.NeonCyan.copy(alpha = 0.18f) else ApexPalette.BgBase
    val border = if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass
    val fg = if (selected) ApexPalette.NeonCyan else ApexPalette.TextPrimary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(label, color = fg, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
    }
}

private fun formatRate(v: Float): String {
    val rounded = (v * 100f).toInt() / 100f
    return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}x" else "${rounded}x"
}
