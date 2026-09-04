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
    OPACITY("Opacity", ApexPalette.NeonEmerald)
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
                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.POSITION) {
                    KeyframeSliderRow(
                        label = "Position X",
                        value = activeKeyframe.translateX,
                        range = -1f..1f,
                        color = ApexPalette.NeonCyan,
                        onValueChange = { onUpdate(activeKeyframe.copy(translateX = it)) }
                    )
                    KeyframeSliderRow(
                        label = "Position Y",
                        value = activeKeyframe.translateY,
                        range = -1f..1f,
                        color = ApexPalette.NeonCyan,
                        onValueChange = { onUpdate(activeKeyframe.copy(translateY = it)) }
                    )
                }

                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.SCALE) {
                    KeyframeSliderRow(
                        label = "Scale",
                        value = activeKeyframe.scale,
                        range = 0.1f..3f,
                        color = ApexPalette.TrackVideo,
                        onValueChange = { onUpdate(activeKeyframe.copy(scale = it)) }
                    )
                }

                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.ROTATION) {
                    KeyframeSliderRow(
                        label = "Rotation",
                        value = activeKeyframe.rotationDeg,
                        range = -180f..180f,
                        color = ApexPalette.NeonPurple,
                        onValueChange = { onUpdate(activeKeyframe.copy(rotationDeg = it)) }
                    )
                }

                if (propertyFilter == KeyframePropertyFilter.ALL || propertyFilter == KeyframePropertyFilter.OPACITY) {
                    KeyframeSliderRow(
                        label = "Opacity",
                        value = activeKeyframe.opacity,
                        range = 0f..1f,
                        color = ApexPalette.NeonEmerald,
                        onValueChange = { onUpdate(activeKeyframe.copy(opacity = it)) }
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
                                text = curve.name.lowercase().replace('_', ' '),
                                color = if (active) ApexPalette.NeonCyan else ApexPalette.TextTertiary,
                                fontSize = 9.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
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
private fun KeyframeSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = ApexPalette.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(72.dp))
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
        Spacer(Modifier.width(8.dp))
        Text(
            text = String.format("%.2f", value),
            color = ApexPalette.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(42.dp)
        )
    }
}
