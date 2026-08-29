package com.apexstudio.app.ui.screens.colortools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexstudio.app.presentation.state.ColorToolState
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ColorStudioScreen(
    projectId: String,
    onBack: () -> Unit,
    vm: EditorViewModel = viewModel()
) {
    val color by vm.color.collectAsStateWithLifecycle()
    val luts by vm.luts.collectAsStateWithLifecycle()
    var splitX by remember { mutableStateOf(0.5f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(
            title = "Color Grading & FX",
            subtitle = "Cinematic • Pro Grade",
            onBack = onBack
        )

        Spacer(Modifier.height(4.dp))

        // Before/After split preview
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("BEFORE", color = ApexPalette.TextTertiary,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("  |  ", color = ApexPalette.TextTertiary, fontSize = 11.sp)
                    Text("AFTER", color = ApexPalette.NeonCyan,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ApexPalette.BgElevated)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("16:9", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    // After (graded) full background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(gradedColor())
                    )
                    // Before overlay
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(splitX)
                            .background(ungradedColor())
                    )
                    // Divider line
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .offset(x = (splitX * 320).dp - 1.dp)
                            .width(2.dp)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = (splitX * 320).dp - 14.dp, y = 100.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tune, null,
                            tint = ApexPalette.BgDeep, modifier = Modifier.size(16.dp))
                    }
                    // labels
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("BEFORE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ApexPalette.NeonCyan.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("AFTER", color = ApexPalette.BgDeep, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    // Drag to split
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    splitX = (change.x / size.width).coerceIn(0f, 1f)
                                }
                            }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Color wheels row
        GlassCard(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ColorWheel(
                    label = "SHADOWS",
                    value = color.shadows,
                    onChange = vm::updateColorShadows,
                    tint = ApexPalette.NeonCyan
                )
                ColorWheel(
                    label = "MIDTONES",
                    value = color.midtones,
                    onChange = vm::updateColorMidtones,
                    tint = ApexPalette.NeonPurple
                )
                ColorWheel(
                    label = "HIGHLIGHTS",
                    value = color.highlights,
                    onChange = vm::updateColorHighlights,
                    tint = ApexPalette.NeonPink
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // RGB Curves
        GlassCard(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Column {
                Text("RGB Curves Editor",
                    color = ApexPalette.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("R", "G", "B", "Luma").forEach { ch ->
                        val sel = ch == color.activeChannel.name
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (sel) ApexPalette.NeonCyan.copy(alpha = 0.18f) else Color.Transparent
                                )
                                .clickable { vm.setColorChannel(ColorToolState.Channel.valueOf(ch)) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(ch, color = if (sel) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ApexPalette.BgBase)
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val grid = 4
                        val w = size.width
                        val h = size.height
                        for (i in 0..grid) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.06f),
                                start = Offset(w * i / grid, 0f),
                                end = Offset(w * i / grid, h),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.06f),
                                start = Offset(0f, h * i / grid),
                                end = Offset(w, h * i / grid),
                                strokeWidth = 1f
                            )
                        }
                    }
                    WaveformCurve(
                        points = color.curvePoints,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // LUT Presets
        Text("LUT PRESETS",
            color = ApexPalette.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(luts) { lut ->
                LutCard(
                    name = lut.name,
                    selected = color.selectedLut == lut.id,
                    onClick = { vm.selectLut(lut.id) }
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ColorWheel(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    tint: Color
) {
    var center by remember { mutableStateOf(Offset.Unspecified) }
    val angle = (value * Math.PI).toFloat()
    val handleR = 18f * value.coerceAtLeast(0.1f)
    val handleX = (50 + handleR * cos(angle)).toFloat()
    val handleY = (50 - handleR * sin(angle)).toFloat()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(ApexPalette.BgBase)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { off ->
                            center = Offset(size.width / 2f, size.height / 2f)
                            val dx = off.x - center.x
                            val dy = off.y - center.y
                            val r = sqrt(dx * dx + dy * dy)
                            val a = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                            val v = (a / Math.PI).toFloat()
                            onChange(v.coerceIn(-1f, 1f))
                        },
                        onDrag = { change, _ ->
                            val dx = change.x - center.x
                            val dy = change.y - center.y
                            val a = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                            onChange((a / Math.PI).toFloat().coerceIn(-1f, 1f))
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2 - 4
                val steps = 36
                for (i in 0 until steps) {
                    val a0 = (i * 360f / steps - 90f)
                    val a1 = ((i + 1) * 360f / steps - 90f)
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFFFF4444), Color(0xFFFFBB33), Color(0xFFEEFF00),
                                Color(0xFF00FF66), Color(0xFF00E5FF), Color(0xFF4466FF),
                                Color(0xFFAA00FF), Color(0xFFFF00AA), Color(0xFFFF4444)
                            )
                        ),
                        startAngle = a0, sweepAngle = (360f / steps) + 1f, useCenter = true,
                        topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                        size = Size(radius * 2, radius * 2),
                        alpha = 0.85f
                    )
                }
                // outer ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = radius,
                    center = Offset(size.width / 2, size.height / 2),
                    style = Stroke(width = 2f)
                )
                // handle
                val rPx = (kotlin.math.abs(value) * radius * 0.9f).coerceAtLeast(8f)
                val a = (value * Math.PI).toFloat()
                val hx = size.width / 2 + rPx * cos(a)
                val hy = size.height / 2 - rPx * sin(a)
                drawCircle(color = Color.Black.copy(alpha = 0.4f),
                    radius = 12f, center = Offset(hx, hy))
                drawCircle(color = tint, radius = 10f, center = Offset(hx, hy))
                drawCircle(color = Color.White, radius = 10f, center = Offset(hx, hy),
                    style = Stroke(width = 2f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = ApexPalette.TextSecondary,
            fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            "[${(value * 100).toInt()}%]",
            color = tint,
            fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LutCard(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(90.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(
                listOf(Color(0xFF1B1B2E), Color(0xFF16213E))
            ))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(48.dp)
                .background(Brush.linearGradient(
                    listOf(ApexPalette.NeonPurple.copy(alpha = 0.7f),
                        ApexPalette.NeonCyan.copy(alpha = 0.5f))
                ))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    if (selected) ApexPalette.NeonCyan else Color.Black.copy(alpha = 0.6f)
                )
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(name, color = if (selected) ApexPalette.BgDeep else Color.White,
                fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun gradedColor(): Brush = Brush.linearGradient(
    listOf(
        Color(0xFF1B3A4B), // teal-ish highlights
        Color(0xFF2E1F47), // purple midtones
        Color(0xFF12333A)
    )
)

private fun ungradedColor(): Brush = Brush.linearGradient(
    listOf(
        Color(0xFF3A3A3A),
        Color(0xFF555555),
        Color(0xFF2A2A2A)
    )
)
