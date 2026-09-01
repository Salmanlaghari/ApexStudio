package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.domain.model.Keyframe
import com.apexstudio.app.domain.model.KeyframeCurve
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Bottom-sheet style keyframe animation editor. Renders:
 *
 * 1. A "Add at playhead" button that pins a new keyframe on the
 *    selected clip at the current playhead position.
 * 2. A scrubber so the user can pick a non-playhead time before
 *    adding a keyframe.
 * 3. A list of every existing keyframe on the clip, with per-key
 *    sliders for translate X / Y, scale, rotation and opacity.
 * 4. A curve selector (LINEAR / EASE_IN / EASE_OUT / EASE_IN_OUT /
 *    HOLD) on each keyframe.
 * 5. A delete button per keyframe and a "Clear all" at the bottom.
 */
@Composable
fun KeyframePanel(
    track: KeyframeTrack,
    playheadMs: Long,
    canAdd: Boolean,
    onAdd: (Long) -> Unit,
    onUpdate: (Keyframe) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    var newTimeMs by remember(playheadMs) { mutableLongStateOf(playheadMs) }
    val sorted = track.sorted().keyframes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Animation,
                contentDescription = null,
                tint = ApexPalette.NeonCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Keyframe Animation",
                color = ApexPalette.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Add translate / scale / rotation / opacity markers on the clip. The values interpolate between keyframes.",
            color = ApexPalette.TextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        // --- Add row ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("at", color = ApexPalette.TextSecondary, fontSize = 12.sp, modifier = Modifier.width(28.dp))
            Slider(
                value = newTimeMs.toFloat(),
                onValueChange = { newTimeMs = it.toLong() },
                valueRange = 0f..kotlin.math.max(newTimeMs.toFloat(), 60_000f),
                colors = SliderDefaults.colors(
                    thumbColor = ApexPalette.NeonCyan,
                    activeTrackColor = ApexPalette.NeonCyan,
                    inactiveTrackColor = ApexPalette.BorderGlass
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text("${newTimeMs / 1000f}s", color = ApexPalette.TextPrimary, fontSize = 12.sp, modifier = Modifier.width(48.dp))
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (canAdd) ApexPalette.NeonCyan else ApexPalette.BorderGlass)
                    .clickable(enabled = canAdd) { onAdd(newTimeMs) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (sorted.isEmpty()) {
            Text(
                text = "No keyframes yet. Add one to start animating.",
                color = ApexPalette.TextTertiary,
                fontSize = 12.sp
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sorted, key = { it.id }) { kf ->
                    KeyframeRow(
                        keyframe = kf,
                        onUpdate = onUpdate,
                        onRemove = { onRemove(kf.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexPalette.BgBase)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                    .clickable(enabled = sorted.isNotEmpty()) { onClear() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = ApexPalette.TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear all", color = ApexPalette.TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun KeyframeRow(
    keyframe: Keyframe,
    onUpdate: (Keyframe) -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Diamond marker so users can scan the list quickly.
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ApexPalette.NeonCyan)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Keyframe at ${keyframe.timeMs / 1000f}s",
                color = ApexPalette.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove keyframe", tint = ApexPalette.TextTertiary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        KeyframeSlider(
            label = "Translate X",
            value = keyframe.translateX,
            range = -1f..1f,
            onValueChange = { v -> onUpdate(keyframe.copy(translateX = v)) }
        )
        KeyframeSlider(
            label = "Translate Y",
            value = keyframe.translateY,
            range = -1f..1f,
            onValueChange = { v -> onUpdate(keyframe.copy(translateY = v)) }
        )
        KeyframeSlider(
            label = "Scale",
            value = keyframe.scale,
            range = 0.25f..4f,
            onValueChange = { v -> onUpdate(keyframe.copy(scale = v)) }
        )
        KeyframeSlider(
            label = "Rotation",
            value = keyframe.rotationDeg,
            range = -180f..180f,
            onValueChange = { v -> onUpdate(keyframe.copy(rotationDeg = v)) }
        )
        KeyframeSlider(
            label = "Opacity",
            value = keyframe.opacity,
            range = 0f..1f,
            onValueChange = { v -> onUpdate(keyframe.copy(opacity = v)) }
        )
        Spacer(Modifier.height(4.dp))
        // Curve selector.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyframeCurve.values().forEach { curve ->
                val selected = keyframe.curve == curve
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.18f) else ApexPalette.BgElevated)
                        .border(1.dp, if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(6.dp))
                        .clickable { onUpdate(keyframe.copy(curve = curve)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = curve.name.lowercase().replace('_', ' '),
                        color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyframeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ApexPalette.TextSecondary, fontSize = 10.sp, modifier = Modifier.width(74.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ApexPalette.NeonCyan,
                activeTrackColor = ApexPalette.NeonCyan,
                inactiveTrackColor = ApexPalette.BorderGlass
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = String.format("%.2f", value),
            color = ApexPalette.TextPrimary,
            fontSize = 10.sp,
            modifier = Modifier.width(44.dp)
        )
    }
}
