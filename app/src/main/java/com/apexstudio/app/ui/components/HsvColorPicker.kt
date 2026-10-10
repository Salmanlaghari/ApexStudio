package com.apexstudio.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.ApexPalette
import kotlin.math.roundToInt

/**
 * Phase 4: compact HSV color picker (original ApexStudio code, inspired by
 * KvColorPicker-Android's hue-wheel concept, MIT).
 *
 * Hue slider + saturation/value pad + live preview. Calls [onColorChange]
 * continuously while dragging so the text preview updates live; the caller
 * persists on release (or just takes the last value — both work).
 */
@Composable
fun HsvColorPicker(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var hue by remember(initialColor) { mutableStateOf(rgbToHsv(initialColor)[0]) }
    var sat by remember(initialColor) { mutableStateOf(rgbToHsv(initialColor)[1]) }
    var value by remember(initialColor) { mutableStateOf(rgbToHsv(initialColor)[2]) }
    val current = remember(hue, sat, value) { Color.hsv(hue, sat, value) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Preview swatch
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(current)
                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            )
            // Saturation/Value pad
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.White, Color.hsv(hue, 1f, 1f))
                        )
                    )
                    .pointerInput(hue) {
                        detectDragGestures(
                            onDragStart = { off ->
                                sat = (off.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (off.y / size.height).coerceIn(0f, 1f)
                                onColorChange(Color.hsv(hue, sat, value))
                            },
                            onDrag = { change, _ ->
                                sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                onColorChange(Color.hsv(hue, sat, value))
                            }
                        )
                    }
            ) {
                // Dark overlay for value
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black)
                            )
                        )
                )
                // SV cursor
                Canvas(modifier = Modifier.matchParentSize()) {
                    val cx = sat * size.width
                    val cy = (1f - value) * size.height
                    drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
                    drawCircle(current, radius = 5.dp.toPx(), center = Offset(cx, cy))
                }
            }
        }
        // Hue slider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.horizontalGradient(
                        (0..6).map { Color.hsv(it * 60f, 1f, 1f) }
                    )
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { off ->
                            hue = (off.x / size.width).coerceIn(0f, 1f) * 360f
                            onColorChange(Color.hsv(hue, sat, value))
                        },
                        onDrag = { change, _ ->
                            hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                            onColorChange(Color.hsv(hue, sat, value))
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val cx = (hue / 360f) * size.width
                drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(cx, size.height / 2), style = Stroke(2.dp.toPx()))
            }
        }
        // Hex readout
        Text(
            text = "#%06X".format(
                0xFFFFFF and (
                    (current.red * 255).roundToInt() shl 16 or
                        ((current.green * 255).roundToInt() shl 8) or
                        (current.blue * 255).roundToInt()
                    )
            ),
            color = ApexPalette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun rgbToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    val s = if (max == 0f) 0f else d / max
    return floatArrayOf((h + 360f) % 360f, s, max)
}
