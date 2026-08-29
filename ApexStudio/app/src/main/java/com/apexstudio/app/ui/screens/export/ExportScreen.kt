package com.apexstudio.app.ui.screens.export

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.delay

@Composable
fun ExportScreen(
    projectId: String,
    onBack: () -> Unit,
    vm: EditorViewModel = viewModel(factory = EditorViewModelFactory())
) {
    val export by vm.export.collectAsStateWithLifecycle()
    val fxList = vm.fx.collectAsStateWithLifecycle().value
    val transitionsList = vm.transitions.collectAsStateWithLifecycle().value
    val s = export.settings
    var selectedResolution by remember { mutableStateOf("4K") }
    var selectedFps by remember { mutableStateOf(60f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexPalette.BgBase)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // === Motion Graphics & Transitions ===
        SectionLabel("MOTION GRAPHICS")
        Spacer(Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(fxList) { fx ->
                MotionGraphicsCard(
                    label = fx.label,
                    icon = fx.icon,
                    selected = fx.id == "chrom",
                    modifier = Modifier.width(94.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        SectionLabel("TRANSITIONS")
        Spacer(Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(transitionsList) { t ->
                TransitionCard(
                    label = t.label,
                    icon = t.icon,
                    modifier = Modifier.width(72.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // === Resolution Pills ===
        SectionLabel("RESOLUTION")
        Spacer(Modifier.height(4.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 14.dp
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "1080p" to "1920×1080",
                        "4K" to "3840×2160",
                        "8K" to "7680×4320"
                    ).forEach { (label, sub) ->
                        ResolutionPill(
                            label = label,
                            sub = sub,
                            selected = selectedResolution == label,
                            onClick = { selectedResolution = label },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // === Frame Rate Slider ===
        SectionLabel("FRAME RATE")
        Spacer(Modifier.height(4.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 14.dp
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "30",
                        color = ApexPalette.TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = selectedFps,
                        onValueChange = { selectedFps = it },
                        valueRange = 30f..120f,
                        steps = 2,
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonCyan,
                            activeTrackColor = ApexPalette.NeonCyan,
                            inactiveTrackColor = ApexPalette.BgElevated
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        "120",
                        color = ApexPalette.TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(30f, 60f, 90f, 120f).forEach { f ->
                        val sel = selectedFps == f
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (sel) ApexPalette.NeonCyan.copy(alpha = 0.18f)
                                    else ApexPalette.BgElevated
                                )
                                .border(
                                    if (sel) 1.dp else 0.dp,
                                    ApexPalette.NeonCyan,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedFps = f }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${f.toInt()} fps",
                                color = if (sel) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // === Bitrate Quality Gauge ===
        SectionLabel("BITRATE & QUALITY")
        Spacer(Modifier.height(4.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 14.dp
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GaugeArc(
                    progress = 0.78f,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Estimated File Size",
                        color = ApexPalette.TextTertiary,
                        fontSize = 9.sp
                    )
                    Text(
                        "1.8 GB",
                        color = ApexPalette.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "120 Mbps • H.265",
                        color = ApexPalette.NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // === Export button ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                    )
                )
                .clickable { vm.startExport() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (export.isExporting) Icons.Default.Refresh
                    else Icons.Default.IosShare,
                    null,
                    tint = ApexPalette.BgDeep,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (export.isExporting)
                        "Exporting… ${(export.progress * 100).toInt()}%"
                    else "Export $selectedResolution @ ${selectedFps.toInt()}fps",
                    color = ApexPalette.BgDeep,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        }
        if (export.isExporting) {
            LaunchedEffect(Unit) {
                while (export.progress < 1f) {
                    delay(80)
                    vm.setExportProgress((export.progress + 0.01f).coerceAtMost(1f))
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
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun MotionGraphicsCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val border = if (selected) ApexPalette.NeonCyan else Color.Transparent
    Box(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A0F2E), Color(0xFF0F1B2D))
                )
            )
            .border(2.dp, border, RoundedCornerShape(14.dp))
            .clickable { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(ApexPalette.NeonPurple.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(40.dp)
                .clip(CircleShape)
                .background(ApexPalette.NeonCyan.copy(alpha = 0.18f))
                .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                tint = if (selected) ApexPalette.NeonCyan else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            label,
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

@Composable
private fun TransitionCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A2440), Color(0xFF0F1B33))
                )
            )
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, null,
            tint = ApexPalette.NeonCyan,
            modifier = Modifier.size(28.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(ApexPalette.BgDeep.copy(alpha = 0.7f))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = ApexPalette.TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ResolutionPill(
    label: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) ApexPalette.NeonCyan.copy(alpha = 0.15f)
                else ApexPalette.BgElevated
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                sub,
                color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextTertiary,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun GaugeArc(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = ApexPalette.BgElevated,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple, ApexPalette.NeonCyan)
                ),
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            "${(progress * 100).toInt()}%",
            color = ApexPalette.NeonCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
