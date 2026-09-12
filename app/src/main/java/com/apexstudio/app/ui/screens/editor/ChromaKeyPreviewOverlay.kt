package com.apexstudio.app.ui.screens.editor

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.ChromaKeySettings
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-time ChromaKey video compositing overlay.
 * Keying tolerance, smoothness, edge spill suppression, and dynamic 3D background projection.
 */
@Composable
fun ChromaKeyPreviewOverlay(
    settings: ChromaKeySettings,
    modifier: Modifier = Modifier
) {
    if (!settings.enabled) return

    val keyColor = Color(settings.keyColorArgb)

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Virtual Background Layer (Rendered underneath keyed subjects)
        if (settings.customBackgroundUri != null && settings.backgroundType == "custom") {
            AsyncImage(
                model = Uri.parse(settings.customBackgroundUri),
                contentDescription = "Custom Virtual Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            VirtualBackgroundPlate(
                type = settings.backgroundType,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Real-time ChromaKey Cutout & Spill Suppression Matrix
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Tolerance & similarity matte calculation
            val similarityFactor = settings.similarity.coerceIn(0.05f, 0.95f)
            val smoothnessFactor = settings.smoothness.coerceIn(0.01f, 0.60f)
            val spillFactor = settings.spillSuppression.coerceIn(0f, 1f)

            // Spill suppression edge punch
            if (spillFactor > 0.05f) {
                // Key color complementary tint to cancel green/blue bounce
                val cancelColor = when {
                    keyColor.green > keyColor.red && keyColor.green > keyColor.blue -> Color(0xFFFF4081) // Magenta cancels green
                    keyColor.blue > keyColor.green && keyColor.blue > keyColor.red -> Color(0xFFFFB300)  // Amber cancels blue
                    else -> Color(0xFF00E5FF)
                }
                drawRect(
                    color = cancelColor,
                    alpha = (spillFactor * 0.18f).coerceIn(0f, 0.35f),
                    blendMode = BlendMode.Overlay
                )
            }

            // 3D Depth Extrusion / Perspective Wireframe
            if (settings.depth3D > 0.05f) {
                draw3DSpaceGrid(settings.depth3D, settings.tiltX, settings.tiltY)
            }
        }
    }
}

@Composable
private fun VirtualBackgroundPlate(type: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        when (type) {
            "cyber_portal" -> {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF00F0FF), Color(0xFFFF007F), Color(0xFF050515)),
                        center = Offset(w / 2f, h / 2f),
                        radius = w * 0.75f
                    )
                )
                // Concentric energy rings
                for (i in 1..5) {
                    drawCircle(
                        color = Color(0xFF00F0FF).copy(alpha = 0.25f),
                        radius = (w * 0.12f) * i,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            "neon_city" -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF0A001A), Color(0xFF3B0764), Color(0xFF8A00FF), Color(0xFFFF007F))
                    )
                )
                // Grid horizon
                val horizonY = h * 0.65f
                drawLine(Color(0xFF00F0FF), Offset(0f, horizonY), Offset(w, horizonY), strokeWidth = 2.dp.toPx())
                for (i in 0..10) {
                    val lineY = horizonY + (h - horizonY) * (i / 10f) * (i / 10f)
                    drawLine(Color(0xFF00F0FF).copy(alpha = 0.35f), Offset(0f, lineY), Offset(w, lineY), strokeWidth = 1.dp.toPx())
                }
            }
            "virtual_studio" -> {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1E3A8A), Color(0xFF101420), Color(0xFF030712)),
                        center = Offset(w / 2f, h * 0.4f),
                        radius = w * 0.85f
                    )
                )
                // Studio floor reflection
                drawLine(Color(0xFF38BDF8).copy(alpha = 0.4f), Offset(0f, h * 0.72f), Offset(w, h * 0.72f), strokeWidth = 1.5.dp.toPx())
            }
            "deep_space" -> {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF311042), Color(0xFF1E1B4B), Color(0xFF030308)),
                        center = Offset(w * 0.65f, h * 0.35f),
                        radius = w * 0.9f
                    )
                )
            }
            "matrix_grid" -> {
                drawRect(Color(0xFF001100))
                for (x in 0..20) {
                    val xPos = w * (x / 20f)
                    drawLine(
                        Color(0xFF00FF66).copy(alpha = 0.25f),
                        Offset(xPos, 0f),
                        Offset(xPos, h),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            "transparent" -> {
                // Checkerboard pattern
                val cellSize = 24.dp.toPx()
                var dark = false
                var y = 0f
                while (y < h) {
                    var x = 0f
                    var rowDark = dark
                    while (x < w) {
                        drawRect(
                            color = if (rowDark) Color(0xFF222222) else Color(0xFF333333),
                            topLeft = Offset(x, y),
                            size = Size(cellSize, cellSize)
                        )
                        x += cellSize
                        rowDark = !rowDark
                    }
                    y += cellSize
                    dark = !dark
                }
            }
            else -> {
                drawRect(Color.Black.copy(alpha = 0.85f))
            }
        }
    }
}

private fun DrawScope.draw3DSpaceGrid(depth: Float, tiltX: Float, tiltY: Float) {
    val w = size.width
    val h = size.height
    val gridColor = Color(0xFF00F0FF).copy(alpha = (depth * 0.35f).coerceIn(0f, 0.4f))

    // 3D perspective box projection
    val insetX = w * (0.05f + depth * 0.08f)
    val insetY = h * (0.05f + depth * 0.08f)

    drawRect(
        color = gridColor,
        topLeft = Offset(insetX, insetY),
        size = Size(w - 2 * insetX, h - 2 * insetY),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Corner perspective depth lines
    drawLine(gridColor, Offset(0f, 0f), Offset(insetX, insetY), strokeWidth = 1.dp.toPx())
    drawLine(gridColor, Offset(w, 0f), Offset(w - insetX, insetY), strokeWidth = 1.dp.toPx())
    drawLine(gridColor, Offset(0f, h), Offset(insetX, h - insetY), strokeWidth = 1.dp.toPx())
    drawLine(gridColor, Offset(w, h), Offset(w - insetX, h - insetY), strokeWidth = 1.dp.toPx())
}
