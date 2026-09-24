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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeCurve
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat

enum class KeyframePropertyFilter(val label: String, val color: Color) {
    ALL("All", ApexPalette.NeonCyan),
    POSITION("Position", ApexPalette.NeonCyan),
    SCALE("Scale", ApexPalette.TrackVideo),
    ROTATION("Rotation", ApexPalette.NeonPurple),
    OPACITY("Opacity", ApexPalette.NeonEmerald),
    VOLUME("Volume", Color(0xFF10B981)),
    EFFECT("FX / Filter", Color(0xFFF59E0B))
}

/**
 * Interactive Keyframe Editor for Apex Studio.
 *
 * Features:
 * 1. Timeline ruler with interactive diamond markers for all keyframes.
 * 2. Draggable keyframes along ruler to dynamically adjust timeMs.
 * 3. Property filter tabs: Position, Scale, Rotation, Opacity, All.
 * 4. Add keyframe at playhead with current interpolated values.
 * 5. Value sliders and easing curve selectors for the active keyframe.
 * 6. Visual playhead indicator synchronized with player.
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
    onApplyPreset: ((com.apexstudio.app.data.animation.AnimationPresetType) -> Unit)? = null,
    onClose: () -> Unit
) {
    val sorted = remember(track) { track.sorted().keyframes }
    var selectedKeyframeId by remember(sorted) {
        mutableStateOf(sorted.minByOrNull { kotlin.math.abs(it.timeMs - playheadMs) }?.id)
    }
    var propertyFilter by remember { mutableStateOf(KeyframePropertyFilter.ALL) }
    val effectiveDuration = clipDurationMs.coerceAtLeast(1000L)

    val activeKeyframe = sorted.firstOrNull { it.id == selectedKeyframeId }
        ?: sorted.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(14.dp)
    ) {
        // --- Header Row ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Animation,
                contentDescription = null,
                tint = ApexPalette.NeonCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Keyframe Studio",
                color = ApexPalette.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )

            // Jump to Prev / Next Keyframe
            IconButton(
                onClick = {
                    val prev = sorted.filter { it.timeMs < playheadMs }.maxByOrNull { it.timeMs }
                    if (prev != null) selectedKeyframeId = prev.id
                },
                enabled = sorted.any { it.timeMs < playheadMs }
            ) {
                Icon(Icons.Default.FastRewind, contentDescription = "Prev keyframe", tint = ApexPalette.TextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = {
                    val next = sorted.filter { it.timeMs > playheadMs }.minByOrNull { it.timeMs }
                    if (next != null) selectedKeyframeId = next.id
                },
                enabled = sorted.any { it.timeMs > playheadMs }
            ) {
                Icon(Icons.Default.FastForward, contentDescription = "Next keyframe", tint = ApexPalette.TextSecondary, modifier = Modifier.size(18.dp))
            }

            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        // --- Property Tabs Row ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyframePropertyFilter.values().forEach { filter ->
                val active = propertyFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) filter.color.copy(alpha = 0.22f) else ApexPalette.BgBase)
                        .border(1.dp, if (active) filter.color else ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                        .clickable { propertyFilter = filter }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        filter.label,
                        color = if (active) filter.color else ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        if (onApplyPreset != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Presets:",
                    color = ApexPalette.TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                com.apexstudio.app.data.animation.AnimationPresetType.values().forEach { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ApexPalette.BgBase)
                            .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable { onApplyPreset(preset) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = preset.name.replace('_', ' ').lowercase().capitalize(),
                            color = ApexPalette.NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // --- Interactive Keyframe Timeline Ruler ---
        var rulerWidthPx by remember { mutableFloatStateOf(1f) }
        var rulerHeightPx by remember { mutableFloatStateOf(1f) }
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ApexPalette.BgBase)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                .onSizeChanged {
                    rulerWidthPx = it.width.toFloat()
                    rulerHeightPx = it.height.toFloat()
                }
        ) {
            // Background grid / ticks
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tickStepMs = 1000L
                var t = 0L
                while (t <= effectiveDuration) {
                    val x = (t.toFloat() / effectiveDuration) * size.width
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    t += tickStepMs
                }
            }

            // Playhead indicator
            val playheadX = (playheadMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f) * rulerWidthPx
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = ApexPalette.NeonCyan,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, size.height),
                    strokeWidth = 2f
                )
            }

            // Draggable Keyframe Diamond Markers
            sorted.forEach { kf ->
                val isSelected = kf.id == selectedKeyframeId
                val kfProgress = (kf.timeMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
                val kfXPx = kfProgress * rulerWidthPx
                val kfXDp = with(density) { kfXPx.toDp() }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (kfXPx - 14.dp.toPx()).toInt(),
                                ((rulerHeightPx - 28.dp.toPx()) / 2f).toInt()
                            )
                        }
                        .size(28.dp)
                        .pointerInput(kf.id, effectiveDuration, rulerWidthPx) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaFraction = dragAmount / rulerWidthPx.coerceAtLeast(1f)
                                val deltaMs = (deltaFraction * effectiveDuration).toLong()
                                val newTime = (kf.timeMs + deltaMs).coerceIn(0L, effectiveDuration)
                                selectedKeyframeId = kf.id
                                onUpdate(kf.copy(timeMs = newTime))
                            }
                        }
                        .clickable {
                            selectedKeyframeId = kf.id
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 14.dp else 10.dp)
                            .graphicsLayer(rotationZ = 45f)
                            .background(if (isSelected) ApexPalette.NeonCyan else propertyFilter.color)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }

        // Timeline Action Controls (Add at playhead, Delete selected, Clear all)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (canAdd) ApexPalette.NeonCyan else ApexPalette.BorderGlass)
                    .clickable(enabled = canAdd) {
                        onAdd(playheadMs)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add at playhead (${TimeFormat.msToShort(playheadMs)})", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(8.dp))

            if (activeKeyframe != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.BgBase)
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                        .clickable { onRemove(activeKeyframe.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = ApexPalette.Danger, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = ApexPalette.Danger, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (sorted.isNotEmpty()) {
                Text(
                    text = "Clear all",
                    color = ApexPalette.TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable { onClear() }
                        .padding(4.dp)
                )
            }
        }

        // --- Active Keyframe Inspector & Sliders ---
        if (activeKeyframe != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApexPalette.BgBase)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Keyframe @ ${TimeFormat.formatMs(activeKeyframe.timeMs)}",
                        color = ApexPalette.NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Drag diamond above to slide time",
                        color = ApexPalette.TextTertiary,
                        fontSize = 10.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Sliders based on selected tab
                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.SCALE) {
                    EnhancedKeyframeSlider(
                        label = "Scale",
                        value = activeKeyframe.scale,
                        range = 0.1f..4.0f,
                        unit = "x",
                        color = ApexPalette.TrackVideo,
                        quickChips = listOf(
                            0.5f to "0.5x",
                            1.0f to "1.0x (Norm)",
                            1.25f to "1.25x",
                            1.5f to "1.5x",
                            2.0f to "2.0x",
                            3.0f to "3.0x"
                        ),
                        step = 0.05f,
                        defaultValue = 1.0f,
                        onValueChange = { onUpdate(activeKeyframe.copy(scale = it)) }
                    )
                }

                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.ROTATION) {
                    EnhancedKeyframeSlider(
                        label = "Rotation",
                        value = activeKeyframe.rotationDeg,
                        range = -360f..360f,
                        unit = "°",
                        color = ApexPalette.NeonPurple,
                        quickChips = listOf(
                            -180f to "-180°",
                            -90f to "-90°",
                            0f to "0° (Reset)",
                            90f to "90°",
                            180f to "180°",
                            360f to "360°"
                        ),
                        step = 5f,
                        defaultValue = 0f,
                        onValueChange = { onUpdate(activeKeyframe.copy(rotationDeg = it)) }
                    )
                }

                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.OPACITY) {
                    EnhancedKeyframeSlider(
                        label = "Opacity",
                        value = activeKeyframe.opacity,
                        range = 0f..1f,
                        unit = "%",
                        displayMultiplier = 100f,
                        color = ApexPalette.NeonEmerald,
                        quickChips = listOf(
                            0f to "0% (Fade)",
                            0.25f to "25%",
                            0.5f to "50%",
                            0.75f to "75%",
                            1.0f to "100% (Solid)"
                        ),
                        step = 0.05f,
                        defaultValue = 1.0f,
                        onValueChange = { onUpdate(activeKeyframe.copy(opacity = it)) }
                    )
                }

                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.POSITION) {
                    EnhancedKeyframeSlider(
                        label = "Position X",
                        value = activeKeyframe.translateX,
                        range = -1f..1f,
                        unit = "",
                        color = ApexPalette.NeonCyan,
                        quickChips = listOf(
                            -0.5f to "Left",
                            0f to "Center",
                            0.5f to "Right"
                        ),
                        step = 0.05f,
                        defaultValue = 0f,
                        onValueChange = { onUpdate(activeKeyframe.copy(translateX = it)) }
                    )
                    EnhancedKeyframeSlider(
                        label = "Position Y",
                        value = activeKeyframe.translateY,
                        range = -1f..1f,
                        unit = "",
                        color = ApexPalette.NeonCyan,
                        quickChips = listOf(
                            -0.5f to "Top",
                            0f to "Center",
                            0.5f to "Bottom"
                        ),
                        step = 0.05f,
                        defaultValue = 0f,
                        onValueChange = { onUpdate(activeKeyframe.copy(translateY = it)) }
                    )
                }

                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.VOLUME) {
                    EnhancedKeyframeSlider(
                        label = "Volume",
                        value = activeKeyframe.volume,
                        range = 0f..1f,
                        unit = "%",
                        displayMultiplier = 100f,
                        color = Color(0xFF10B981),
                        quickChips = listOf(
                            0f to "Mute",
                            0.5f to "50%",
                            1.0f to "100%"
                        ),
                        step = 0.05f,
                        defaultValue = 1.0f,
                        onValueChange = { onUpdate(activeKeyframe.copy(volume = it)) }
                    )
                }

                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.EFFECT) {
                    EnhancedKeyframeSlider(
                        label = "Light Shift",
                        value = activeKeyframe.filterIntensity,
                        range = 0f..2f,
                        unit = "x",
                        color = Color(0xFFFBBF24),
                        quickChips = listOf(0f to "Off", 1f to "Normal", 1.5f to "High"),
                        step = 0.05f,
                        defaultValue = 1.0f,
                        onValueChange = { onUpdate(activeKeyframe.copy(filterIntensity = it)) }
                    )
                    EnhancedKeyframeSlider(
                        label = "FX Intensity",
                        value = activeKeyframe.effectIntensity,
                        range = 0f..1f,
                        unit = "%",
                        displayMultiplier = 100f,
                        color = Color(0xFFF59E0B),
                        quickChips = listOf(0f to "0%", 0.5f to "50%", 1f to "100%"),
                        step = 0.05f,
                        defaultValue = 1.0f,
                        onValueChange = { onUpdate(activeKeyframe.copy(effectIntensity = it)) }
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Interpolation Curve Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Curve:", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                    KeyframeCurve.values().forEach { curve ->
                        val active = activeKeyframe.curve == curve
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) ApexPalette.NeonCyan.copy(alpha = 0.25f) else ApexPalette.BgElevated)
                                .border(1.dp, if (active) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(6.dp))
                                .clickable { onUpdate(activeKeyframe.copy(curve = curve)) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (curve == KeyframeCurve.BEZIER) "Bezier Curve" else curve.name.lowercase().replace('_', ' '),
                                color = if (active) ApexPalette.NeonCyan else ApexPalette.TextTertiary,
                                fontSize = 9.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Interactive Bezier / Easing Curve Visualizer Preview
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF13131F))
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        // Grid lines
                        drawLine(
                            color = Color(0xFF2E2E3E),
                            start = Offset(0f, h / 2f),
                            end = Offset(w, h / 2f),
                            strokeWidth = 1f
                        )
                        // Curve Path
                        val path = Path()
                        val steps = 60
                        for (i in 0..steps) {
                            val t = i.toFloat() / steps.toFloat()
                            val eased = when (activeKeyframe.curve) {
                                KeyframeCurve.LINEAR -> t
                                KeyframeCurve.EASE_IN -> t * t
                                KeyframeCurve.EASE_OUT -> 1f - (1f - t) * (1f - t)
                                KeyframeCurve.EASE_IN_OUT -> if (t < 0.5f) 2f * t * t else 1f - 2f * (1f - t) * (1f - t)
                                KeyframeCurve.BEZIER -> t * t * (3f - 2f * t)
                                KeyframeCurve.HOLD -> if (t < 1f) 0f else 1f
                            }
                            val px = t * w
                            val py = h - (eased * h)
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        drawPath(
                            path = path,
                            color = ApexPalette.NeonCyan,
                            style = Stroke(width = 2.5f)
                        )
                        // Start and End keyframe dots
                        drawCircle(color = Color.White, radius = 4f, center = Offset(0f, h))
                        drawCircle(color = ApexPalette.NeonCyan, radius = 4f, center = Offset(w, 0f))
                    }
                    Text(
                        text = "Smooth Bezier Easing: ${activeKeyframe.curve.name}",
                        color = ApexPalette.TextSecondary,
                        fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            }
        } else {
            Text(
                text = "No keyframes on this clip. Tap '+ Add at playhead' to pin transform values.",
                color = ApexPalette.TextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}

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
