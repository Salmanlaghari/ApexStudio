package com.apexstudio.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.WaveformGenerator

@Composable
fun AudioWaveform(
    seed: Long,
    modifier: Modifier = Modifier,
    color: Color = ApexPalette.NeonCyan,
    secondaryColor: Color = ApexPalette.NeonPurple,
    progress: Float = 0.5f,
    samples: Int = 240
) {
    val data = remember(seed) { WaveformGenerator.generate(seed, samples) }
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val mid = h / 2
        val step = w / (samples - 1)
        val barWidth = (w / samples) * 0.5f
        for (i in 0 until samples) {
            val v = data[i]
            val x = i * step
            val barH = (kotlin.math.abs(v) * h * 0.92f).coerceAtLeast(2f)
            val played = (i.toFloat() / samples) < progress
            val c = if (played) color else secondaryColor.copy(alpha = 0.45f)
            drawLine(
                brush = Brush.verticalGradient(
                    listOf(c.copy(alpha = 0.3f), c, c.copy(alpha = 0.3f))
                ),
                start = Offset(x, mid - barH / 2),
                end = Offset(x, mid + barH / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun WaveformCurve(
    points: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    color: Color = ApexPalette.NeonCyan,
    fillColor: Color = ApexPalette.NeonCyan.copy(alpha = 0.18f)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (points.size < 2) return@Canvas
        val path = Path()
        val fillPath = Path()
        points.forEachIndexed { idx, (px, py) ->
            val x = px * w
            val y = h - py * h
            if (idx == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(w, h)
        fillPath.close()
        drawPath(fillPath, brush = Brush.verticalGradient(
            listOf(fillColor, Color.Transparent)
        ))
        drawPath(
            path,
            color = color,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
        // glow
        drawPath(
            path,
            brush = Brush.linearGradient(
                listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
            ),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
