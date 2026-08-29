package com.apexstudio.app.ui.screens.colortools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.components.ScreenTopBar
import com.apexstudio.app.ui.theme.ApexPalette

@Composable
fun ColorStudioScreen(
    projectId: String,
    onBack: () -> Unit,
    vm: EditorViewModel = viewModel(factory = EditorViewModelFactory())
) {
    val luts by vm.luts.collectAsStateWithLifecycle()
    var splitX by remember { mutableStateOf(0.5f) }
    var selectedChannel by remember { mutableStateOf("R") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexPalette.BgBase)
    ) {
        ScreenTopBar(
            title = "COLOR GRADE",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Before / After
            BeforeAfterPreview(
                splitFraction = splitX,
                onSplitChange = { splitX = it },
                modifier = Modifier.fillMaxWidth()
            )

            // Color Wheels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ColorWheelCard("SHADOWS", 0.65f, 0.3f, ApexPalette.NeonCyan, 12, "Luma: 0.15", "Hue 215", "Sat 45")
                ColorWheelCard("MIDTONES", 0.5f, -0.4f, ApexPalette.NeonPurple, -8, "Luma: 0.48", "Hue 0.48", "Sat skin")
                ColorWheelCard("HIGHLIGHTS", 0.7f, 0.5f, ApexPalette.NeonPink, 18, "Luma: 0.88", "Hue 0.88", "Sat cyan")
            }

            // RGB Curves
            RgbCurvesCanvas(
                selectedChannel = selectedChannel,
                onChannelSelect = { selectedChannel = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            )

            // LUT Presets (horizontal scrollable)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(luts) { lut ->
                    LutPresetCard(
                        name = lut.name,
                        selected = lut.id == "cinematic",
                        modifier = Modifier.width(76.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = ApexPalette.TextTertiary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun BeforeAfterPreview(
    splitFraction: Float,
    onSplitChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val aspect = 16f / 9f
    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(14.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp))
    ) {
        // BEFORE
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2C3E50), Color(0xFF34495E), Color(0xFF1B2631))
                    )
                )
        )
        // AFTER
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val splitPx = (splitFraction.coerceIn(0f, 1f)) * widthPx
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { splitPx.toDp() })
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1B2A4E),
                            Color(0xFF3A1B5E),
                            Color(0xFF0E2B3F)
                        )
                    )
                )
        )
        // Vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                        radius = with(density) { 320.dp.toPx() }
                    )
                )
        )
        Text(
            "Original (RAW)",
            color = ApexPalette.TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgGlass)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Text(
            "BEFORE / AFTER",
            color = ApexPalette.NeonCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ApexPalette.BgGlass)
                .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .offset(x = with(density) { splitPx.toDp() } - 1.dp)
                .background(ApexPalette.NeonCyan)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = with(density) { splitPx.toDp() } - 12.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(ApexPalette.NeonCyan)
                .border(2.dp, Color.White, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onSplitChange(splitFraction + drag.x / widthPx)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CompareArrows,
                null,
                tint = ApexPalette.BgDeep,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ColorWheelCard(
    label: String,
    liftX: Float,
    liftY: Float,
    accent: Color,
    percent: Int,
    luma: String,
    hue: String,
    sat: String
) {
    GlassCard(
        modifier = Modifier
            .width(96.dp)
            .height(150.dp),
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                color = ApexPalette.TextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(1.dp))
            Text(
                luma,
                color = ApexPalette.TextTertiary,
                fontSize = 7.sp
            )
            Spacer(Modifier.height(2.dp))
            ColorWheel(liftX = liftX, liftY = liftY, accent = accent)
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Hue", color = ApexPalette.TextTertiary, fontSize = 7.sp)
                    Text(hue, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Sat", color = ApexPalette.TextTertiary, fontSize = 7.sp)
                    Text(sat, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ColorWheel(liftX: Float, liftY: Float, accent: Color) {
    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFFFF1744),
                        Color(0xFFFFC400),
                        Color(0xFF10B981),
                        Color(0xFF00E5FF),
                        Color(0xFF7C4DFF),
                        Color(0xFFFF1744)
                    )
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color(0xFF10141E),
                radius = radius * 0.55f,
                center = center
            )
            // Crosshair
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(center.x - radius * 0.45f, center.y),
                end = Offset(center.x + radius * 0.45f, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(center.x, center.y - radius * 0.45f),
                end = Offset(center.x, center.y + radius * 0.45f),
                strokeWidth = 1f
            )
            val maxOffset = radius * 0.45f
            val ix = center.x + liftX * maxOffset
            val iy = center.y + liftY * maxOffset
            drawCircle(color = Color.White, radius = 5f, center = Offset(ix, iy))
            drawCircle(color = accent, radius = 3f, center = Offset(ix, iy))
        }
    }
}

@Composable
private fun RgbCurvesCanvas(
    selectedChannel: String,
    onChannelSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rPoints = remember { mutableStateListOf(Offset(0.1f, 0.4f), Offset(0.4f, 0.55f), Offset(0.7f, 0.7f), Offset(0.9f, 0.85f)) }
    val gPoints = remember { mutableStateListOf(Offset(0.1f, 0.3f), Offset(0.5f, 0.5f), Offset(0.85f, 0.7f)) }
    val bPoints = remember { mutableStateListOf(Offset(0.1f, 0.5f), Offset(0.35f, 0.45f), Offset(0.7f, 0.4f), Offset(0.9f, 0.6f)) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp))
            .padding(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(
                        "R" to ApexPalette.TrackOverlay,
                        "G" to ApexPalette.NeonEmerald,
                        "B" to ApexPalette.NeonPurple,
                        "L" to ApexPalette.NeonCyan
                    ).forEach { (ch, color) ->
                        val sel = ch == selectedChannel
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (sel) color.copy(alpha = 0.2f) else ApexPalette.BgElevated
                                )
                                .border(
                                    if (sel) 1.dp else 0.dp,
                                    color,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { onChannelSelect(ch) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                ch,
                                color = if (sel) color else ApexPalette.TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ApexPalette.BgBase)
            ) {
                val w = size.width
                val h = size.height
                for (i in 1..3) {
                    val x = w * i / 4
                    drawLine(
                        color = Color.White.copy(alpha = 0.05f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                    val y = h * i / 4
                    drawLine(
                        color = Color.White.copy(alpha = 0.05f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }
                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = Offset(0f, h),
                    end = Offset(w, 0f),
                    strokeWidth = 1f
                )
                listOf(rPoints, gPoints, bPoints).forEachIndexed { idx, pts ->
                    val color = when (idx) {
                        0 -> ApexPalette.TrackOverlay
                        1 -> ApexPalette.NeonEmerald
                        else -> ApexPalette.NeonPurple
                    }
                    val mapped = pts.map { Offset(it.x * w, (1f - it.y) * h) }
                    for (i in 0 until mapped.size - 1) {
                        drawLine(
                            brush = SolidColor(color),
                            start = mapped[i],
                            end = mapped[i + 1],
                            strokeWidth = 2f
                        )
                    }
                    mapped.forEach { p ->
                        drawCircle(color = Color.White, radius = 3f, center = p)
                        drawCircle(color = color, radius = 1.8f, center = p)
                    }
                }
            }
        }
    }
}

@Composable
private fun LutPresetCard(
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) ApexPalette.NeonCyan else Color.Transparent
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A0F2E), Color(0xFF0F1B2D))
                )
            )
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(ApexPalette.NeonPurple.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(24.dp)
                .clip(CircleShape)
                .background(ApexPalette.NeonCyan.copy(alpha = 0.18f))
                .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Palette,
                null,
                tint = if (selected) ApexPalette.NeonCyan else Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            name,
            color = if (selected) ApexPalette.NeonCyan else Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.NeonCyan),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = ApexPalette.BgDeep,
                    modifier = Modifier.size(8.dp)
                )
            }
        }
    }
}
