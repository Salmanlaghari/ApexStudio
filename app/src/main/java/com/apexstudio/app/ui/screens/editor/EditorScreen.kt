package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * ApexStudio — Pro Video Editor Screen
 * Premium mobile editor UI: dark cinematic theme, glassmorphism panels,
 * purple/violet accent glow, multi-track timeline, floating toolbars.
 */
@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit = {},
    onExport: () -> Unit = {},
    onColor: () -> Unit = {},
    onAudio: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0F))) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 1) Top header
            EditorTopBar(
                onMenu = onBack,
                onExport = onExport
            )

            // 2) Preview + floating side toolbars
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                VideoPreviewArea(modifier = Modifier.fillMaxSize())

                LeftEditToolbar(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp)
                )

                RightAddToolbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                )
            }

            // 3) Playback controls
            PlaybackControls()

            // 4) Multi-track timeline (scrollable)
            ProfessionalTimeline(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
            )

            // 5) Bottom editing toolbar (Edit / Audio / Text / Effects / Overlay / Transition / Filters)
            BottomEditToolbar()

            // 6) Bottom navigation (Media / Elements / Project / Settings)
            BottomNavStrip()
        }
    }
}

/* ----------------------------- 1. TOP HEADER ----------------------------- */

@Composable
private fun EditorTopBar(
    onMenu: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hamburger
        GlassCircleIcon(
            icon = Icons.Filled.Menu,
            onClick = onMenu
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Apex",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Studio",
                    color = ApexPalette.NeonPurple,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Pro Video Editor",
                color = ApexPalette.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        GlassCircleIcon(icon = Icons.Outlined.Undo, onClick = {})
        Spacer(Modifier.width(8.dp))
        GlassCircleIcon(icon = Icons.Outlined.Redo, onClick = {})
        Spacer(Modifier.width(8.dp))
        GlassCircleIcon(icon = Icons.Filled.HelpOutline, onClick = {})

        Spacer(Modifier.width(10.dp))

        // Export pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(ApexPalette.NeonPurple, Color(0xFF9D5BFF))
                    )
                )
                .clickable(onClick = onExport)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.IosShare,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Export",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/* ----------------------------- 2. VIDEO PREVIEW ----------------------------- */

@Composable
private fun VideoPreviewArea(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        // Cinematic preview canvas with faux landscape + safe-zone overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1A1226),
                            Color(0xFF0B0B12),
                            Color(0xFF050507)
                        )
                    )
                )
        ) {
            // Faux cinematic backdrop
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // Sky gradient
                val brush = Brush.verticalGradient(
                    listOf(Color(0xFFFF7A45), Color(0xFF3A1A4A), Color.Transparent)
                )
                drawRect(brush = brush, topLeft = Offset(0f, 0f), size = size)
                // Mountain shapes (simple polygon)
                val mountain = Path().apply {
                    moveTo(0f, h * 0.55f)
                    lineTo(w * 0.20f, h * 0.30f)
                    lineTo(w * 0.32f, h * 0.45f)
                    lineTo(w * 0.50f, h * 0.18f)
                    lineTo(w * 0.68f, h * 0.40f)
                    lineTo(w * 0.85f, h * 0.28f)
                    lineTo(w, h * 0.50f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    mountain,
                    Brush.verticalGradient(
                        listOf(Color(0xFF1F1832), Color(0xFF08070D))
                    )
                )
                // Foreground silhouette (hiker)
                val hiker = Path().apply {
                    val cx = w * 0.5f
                    val baseY = h * 0.92f
                    moveTo(cx - 30f, baseY)
                    lineTo(cx - 22f, baseY - 90f)
                    lineTo(cx - 18f, baseY - 110f)
                    lineTo(cx - 10f, baseY - 110f)
                    lineTo(cx - 6f, baseY - 90f)
                    lineTo(cx + 6f, baseY - 90f)
                    lineTo(cx + 10f, baseY - 110f)
                    lineTo(cx + 18f, baseY - 110f)
                    lineTo(cx + 22f, baseY - 90f)
                    lineTo(cx + 30f, baseY)
                    close()
                }
                drawPath(hiker, Color(0xFF0A0A12))
            }

            // Resolution chip
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC0F0F18))
                    .border(0.5.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "1080P",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.SwapVert,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Fullscreen chip
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC0F0F18))
                    .border(0.5.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.CropFree,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Safe-zone frame corners
            SafeZoneOverlay()
        }
    }
}

@Composable
private fun SafeZoneOverlay() {
    Canvas(modifier = Modifier.fillMaxSize().padding(28.dp)) {
        val w = size.width
        val h = size.height
        val arm = 22f
        val stroke = Stroke(width = 1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
        val color = ApexPalette.NeonPurple.copy(alpha = 0.55f)
        // top-left
        drawLine(color, Offset(0f, 0f), Offset(arm, 0f), stroke)
        drawLine(color, Offset(0f, 0f), Offset(0f, arm), stroke)
        // top-right
        drawLine(color, Offset(w, 0f), Offset(w - arm, 0f), stroke)
        drawLine(color, Offset(w, 0f), Offset(w, arm), stroke)
        // bottom-left
        drawLine(color, Offset(0f, h), Offset(arm, h), stroke)
        drawLine(color, Offset(0f, h), Offset(0f, h - arm), stroke)
        // bottom-right
        drawLine(color, Offset(w, h), Offset(w - arm, h), stroke)
        drawLine(color, Offset(w, h), Offset(w, h - arm), stroke)
    }
}

/* ----------------------- 3. FLOATING LEFT EDIT TOOLBAR ----------------------- */

@Composable
private fun LeftEditToolbar(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xCC0C0E16))
            .border(0.6.dp, ApexPalette.BorderGlass, RoundedCornerShape(22.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ToolbarItem(icon = Icons.Filled.AutoAwesome, label = "Effects")
        ToolbarItem(icon = Icons.Outlined.FilterBAndW, label = "Filters")
        ToolbarItem(icon = Icons.Outlined.Tune, label = "Adjust")
        ToolbarItem(icon = Icons.Filled.TextFields, label = "Text")
        ToolbarItem(icon = Icons.Outlined.SentimentSatisfied, label = "Sticker")
    }
}

@Composable
private fun ToolbarItem(icon: ImageVector, label: String) {
    var selected by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { selected = !selected }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (selected) Brush.linearGradient(
                        listOf(ApexPalette.NeonPurple, Color(0xFF9D5BFF))
                    ) else Brush.linearGradient(
                        listOf(Color(0xFF15151D), Color(0xFF15151D))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) Color.White else ApexPalette.NeonPurple,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (selected) Color.White else ApexPalette.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/* ----------------------- 4. FLOATING RIGHT ADD TOOLBAR ----------------------- */

@Composable
private fun RightAddToolbar(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xCC0C0E16))
            .border(0.6.dp, ApexPalette.BorderGlass, RoundedCornerShape(22.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AddItem(icon = Icons.Outlined.Add, label = "Add")
        AddItem(icon = Icons.Filled.MusicNote, label = "Audio")
        AddItem(icon = Icons.Filled.Mic, label = "Record")
        AddItem(icon = Icons.Filled.Cameraswitch, label = "Camera")
    }
}

@Composable
private fun AddItem(icon: ImageVector, label: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF15151D)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = ApexPalette.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = ApexPalette.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/* ----------------------------- 5. PLAYBACK CONTROLS ----------------------------- */

@Composable
private fun PlaybackControls() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "00:04.37",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            " / 00:18.69",
            color = ApexPalette.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.weight(1f))

        IconButton(icon = Icons.Filled.SkipPrevious, tint = Color.White)
        Spacer(Modifier.width(18.dp))

        // Play button (purple gradient)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .shadow(8.dp, CircleShape, ambientColor = ApexPalette.NeonPurple)
                .background(
                    Brush.linearGradient(
                        listOf(ApexPalette.NeonPurple, Color(0xFF9D5BFF))
                    )
                )
                .clickable { },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(18.dp))
        IconButton(icon = Icons.Filled.SkipNext, tint = Color.White)

        Spacer(Modifier.weight(1f))

        IconButton(icon = Icons.Outlined.CropFree, tint = ApexPalette.TextSecondary)
        Spacer(Modifier.width(14.dp))
        IconButton(icon = Icons.Filled.Tune, tint = ApexPalette.TextSecondary)
    }
}

@Composable
private fun IconButton(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/* ----------------------------- 6. TIMELINE ----------------------------- */

@Composable
private fun ProfessionalTimeline(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 14.dp)
            .padding(top = 4.dp, bottom = 4.dp)
    ) {
        // Ruler (timecodes)
        TimelineRuler()
        Spacer(Modifier.height(6.dp))

        // Multi-track lanes
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track icon column
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimelineTrackIcon(icon = Icons.Filled.TextFields)
                TimelineTrackIcon(icon = Icons.Outlined.FilterBAndW, label = "T")
                TimelineTrackIcon(icon = Icons.Filled.AutoAwesome, label = "fx")
                TimelineTrackIcon(icon = Icons.Filled.MusicNote)
                TimelineTrackIcon(icon = Icons.Filled.Mic)
            }

            // Lanes column (scrollable)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 0.dp)
            ) {
                item { VideoLane() }
                item { TextLane() }
                item { EffectsLane() }
                item { MusicLane() }
                item { VoiceOverLane() }
            }
        }
    }
}

@Composable
private fun TimelineRuler() {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 56.dp + 6.dp)) {
        val ticks = listOf("00:00", "00:02", "00:04", "00:06", "00:08", "00:10", "00:12", "00:14", "00:16", "00:18")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ticks.forEach { t ->
                Text(
                    t,
                    color = ApexPalette.TextTertiary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TimelineTrackIcon(icon: ImageVector, label: String? = null) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF101019))
            .border(0.5.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = ApexPalette.TextSecondary,
            modifier = Modifier.size(14.dp)
        )
        if (label != null) {
            Spacer(Modifier.width(2.dp))
            Text(label, color = ApexPalette.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun VideoLane() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF4A2E8F),
                        Color(0xFF7C4DFF),
                        Color(0xFF4A2E8F)
                    )
                )
            )
            .border(1.dp, ApexPalette.NeonPurple, RoundedCornerShape(10.dp))
    ) {
        // Thumbnail strip simulation
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stripeCount = 14
            val sw = size.width / stripeCount
            for (i in 0 until stripeCount) {
                drawRect(
                    color = Color(0xFF101019).copy(alpha = 0.55f),
                    topLeft = Offset(i * sw + 2f, 4f),
                    size = Size(sw - 4f, size.height - 8f)
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(i * sw + sw / 2f, 8f),
                    end = Offset(i * sw + sw / 2f, size.height - 8f),
                    strokeWidth = 1f
                )
            }
        }

        // Left badge (1.0x)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xCC101019))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text("1.0x", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        }

        // "+" add clip button
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFF101019))
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }

        // Trim handles
        TrimHandles()
    }
}

@Composable
private fun TrimHandles() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 36.dp, end = 36.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-20).dp)
                .size(width = 10.dp, height = 36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 20.dp)
                .size(width = 10.dp, height = 36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun TextLane() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF2D1B69),
                        Color(0xFF7C4DFF),
                        Color(0xFF2D1B69)
                    )
                )
            )
            .border(1.dp, ApexPalette.NeonPurple.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.TextFields,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "ApexStudio  Pro Video Editor",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EffectsLane() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF1A3E78),
                        Color(0xFF3B82F6),
                        Color(0xFF1A3E78)
                    )
                )
            )
            .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Cinematic Glow",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        // fx badge right
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .size(width = 30.dp, height = 22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xCC101019).copy(alpha = 0.8f))
                .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("fx", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MusicLane() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF10B981))
            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Dreamscape",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        // Waveform
        Canvas(modifier = Modifier.fillMaxSize().padding(start = 90.dp, end = 8.dp)) {
            val w = size.width
            val h = size.height
            val bars = 60
            val barW = w / bars
            val mid = h / 2
            for (i in 0 until bars) {
                val amp = (kotlin.math.sin(i * 0.45f) * 0.5f + 0.5f) * (h * 0.85f)
                val x = i * barW + barW / 2f
                drawLine(
                    color = Color.White,
                    start = Offset(x, mid - amp / 2),
                    end = Offset(x, mid + amp / 2),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun VoiceOverLane() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF7C4DFF))
            .border(1.dp, Color(0xFF9D5BFF), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Voice Over",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Canvas(modifier = Modifier.fillMaxSize().padding(start = 90.dp, end = 8.dp)) {
            val w = size.width
            val h = size.height
            val bars = 60
            val barW = w / bars
            val mid = h / 2
            for (i in 0 until bars) {
                val amp = (kotlin.math.cos(i * 0.6f + 1.2f) * 0.5f + 0.5f) * (h * 0.85f)
                val x = i * barW + barW / 2f
                drawLine(
                    color = Color.White,
                    start = Offset(x, mid - amp / 2),
                    end = Offset(x, mid + amp / 2),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/* ----------------------------- 7. BOTTOM EDIT TOOLBAR ----------------------------- */

@Composable
private fun BottomEditToolbar() {
    var selected by remember { mutableStateOf(0) }
    val items = listOf(
        "Edit" to Icons.Outlined.Tune,
        "Audio" to Icons.Filled.MusicNote,
        "Text" to Icons.Filled.TextFields,
        "Effects" to Icons.Filled.AutoAwesome,
        "Overlay" to Icons.Outlined.Layers,
        "Transition" to Icons.Outlined.SwapVert,
        "Filters" to Icons.Outlined.FilterBAndW
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val active = selected == i
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { selected = i }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) ApexPalette.NeonPurple else ApexPalette.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    color = if (active) ApexPalette.NeonPurple else ApexPalette.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/* ----------------------------- 8. BOTTOM NAVIGATION ----------------------------- */

@Composable
private fun BottomNavStrip() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC0C0E16))
            .border(0.6.dp, ApexPalette.BorderGlass, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(icon = Icons.Outlined.Movie, label = "Media")
            NavItem(icon = Icons.Outlined.GridView, label = "Elements")
            NavItem(icon = Icons.Outlined.Layers, label = "Project")
            NavItem(icon = Icons.Filled.Tune, label = "Settings")
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = ApexPalette.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = ApexPalette.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/* ----------------------------- HELPERS ----------------------------- */

@Composable
private fun GlassCircleIcon(
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xCC15151D))
            .border(0.6.dp, ApexPalette.BorderGlass, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
