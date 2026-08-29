package com.apexstudio.app.ui.screens.colortools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.apexstudio.app.ui.theme.ApexPalette

@Composable
fun ColorStudioScreen(
    projectId: String,
    onBack: () -> Unit,
    vm: EditorViewModel = viewModel(factory = EditorViewModelFactory())
) {
    val luts by vm.luts.collectAsStateWithLifecycle()
    var splitX by remember { mutableStateOf(0.5f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexPalette.BgBase)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        // === Before / After split preview ===
        Text(
            "CINEMATIC PREVIEW",
            color = ApexPalette.TextTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        BeforeAfterPreview(
            splitFraction = splitX,
            onSplitChange = { splitX = it },
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        )

        Spacer(Modifier.height(18.dp))

        // === Color Wheels ===
        Text(
            "COLOR WHEELS",
            color = ApexPalette.TextTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ColorWheelCard("Shadows", 0.65f, 0.3f, ApexPalette.NeonCyan)
            ColorWheelCard("Midtones", 0.5f, -0.4f, ApexPalette.NeonPurple)
            ColorWheelCard("Highlights", 0.7f, 0.5f, ApexPalette.NeonPink)
        }

        Spacer(Modifier.height(18.dp))

        // === RGB Curves ===
        Text(
            "RGB CURVES",
            color = ApexPalette.TextTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        RgbCurvesCanvas(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(180.dp)
        )

        Spacer(Modifier.height(18.dp))

        // === LUT Presets ===
        Text(
            "LUT PRESETS",
            color = ApexPalette.TextTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(luts) { lut ->
                LutPresetCard(
                    name = lut.label,
                    selected = lut.id == "cinematic",
                    modifier = Modifier.width(100.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }
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
            .clip(RoundedCornerShape(18.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(18.dp))
    ) {
        // BEFORE (cool/ungraded)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1F2937), Color(0xFF374151), Color(0xFF1B2A4E))
                    )
                )
        )
        // AFTER (cinematic graded) - overlay clipped by split
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
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                        radius = with(density) { 320.dp.toPx() }
                    )
                )
        )
        // BEFORE / AFTER labels
        Text(
            "BEFORE",
            color = ApexPalette.TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ApexPalette.BgGlass)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text(
            "AFTER",
            color = ApexPalette.NeonCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ApexPalette.BgGlass)
                .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        // Divider + handle
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
                .offset(x = with(density) { splitPx.toDp() } - 14.dp)
                .size(28.dp)
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
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ColorWheelCard(
    label: String,
    liftX: Float,
    liftY: Float,
    accent: Color
) {
    GlassCard(
        modifier = Modifier
            .width(108.dp)
            .height(140.dp),
        cornerRadius = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label.uppercase(),
                color = ApexPalette.TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            ColorWheel(liftX = liftX, liftY = liftY, accent = accent)
            Spacer(Modifier.height(6.dp))
            Text(
                "Lift",
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ColorWheel(liftX: Float, liftY: Float, accent: Color) {
    Box(
        modifier = Modifier
            .size(74.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            // Outer ring
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFFFF1744),
                        Color(0xFFFFC400),
                        Color(0xFF00E676),
                        Color(0xFF00E5FF),
                        Color(0xFF7C4DFF),
                        Color(0xFFFF1744)
                    )
                ),
                radius = radius,
                center = center
            )
            // Inner mask
            drawCircle(
                color = Color(0xFF121824),
                radius = radius * 0.55f,
                center = center
            )
            // Indicator dot at offset
            val maxOffset = radius * 0.45f
            val ix = center.x + liftX * maxOffset
            val iy = center.y + liftY * maxOffset
            drawCircle(
                color = Color.White,
                radius = 5f,
                center = Offset(ix, iy)
            )
            drawCircle(
                color = accent,
                radius = 3f,
                center = Offset(ix, iy)
            )
        }
    }
}

@Composable
private fun RgbCurvesCanvas(modifier: Modifier = Modifier) {
    val rPoints = remember { mutableStateListOf(Offset(0.1f, 0.4f), Offset(0.4f, 0.55f), Offset(0.7f, 0.7f), Offset(0.9f, 0.85f)) }
    val gPoints = remember { mutableStateListOf(Offset(0.1f, 0.3f), Offset(0.5f, 0.5f), Offset(0.85f, 0.7f)) }
    val bPoints = remember { mutableStateListOf(Offset(0.1f, 0.5f), Offset(0.35f, 0.45f), Offset(0.7f, 0.4f), Offset(0.9f, 0.6f)) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "R" to ApexPalette.TrackOverlay,
                    "G" to ApexPalette.TrackAudio,
                    "B" to ApexPalette.NeonPurple
                ).forEach { (ch, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            ch,
                            color = ApexPalette.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ApexPalette.BgBase)
            ) {
                val w = size.width
                val h = size.height
                // Grid
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
                // Diagonal reference
                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = Offset(0f, h),
                    end = Offset(w, 0f),
                    strokeWidth = 1f
                )
                // Curves
                listOf(rPoints, gPoints, bPoints).forEachIndexed { idx, pts ->
                    val color = when (idx) {
                        0 -> ApexPalette.TrackOverlay
                        1 -> ApexPalette.TrackAudio
                        else -> ApexPalette.NeonPurple
                    }
                    val mapped = pts.map { Offset(it.x * w, (1f - it.y) * h) }
                    for (i in 0 until mapped.size - 1) {
                        drawLine(
                            brush = SolidColor(color),
                            start = mapped[i],
                            end = mapped[i + 1],
                            strokeWidth = 2.5f
                        )
                    }
                    mapped.forEach { p ->
                        drawCircle(
                            color = Color.White,
                            radius = 4f,
                            center = p
                        )
                        drawCircle(
                            color = color,
                            radius = 2.5f,
                            center = p
                        )
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
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A0F2E), Color(0xFF0F1B2D))
                )
            )
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
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
                .size(36.dp)
                .clip(CircleShape)
                .background(ApexPalette.NeonCyan.copy(alpha = 0.18f))
                .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Palette,
                null,
                tint = if (selected) ApexPalette.NeonCyan else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            name,
            color = if (selected) ApexPalette.NeonCyan else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                    )
                )
        )
    }
}
