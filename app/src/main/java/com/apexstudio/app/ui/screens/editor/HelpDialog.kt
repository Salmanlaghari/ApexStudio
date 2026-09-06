package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.ApexPalette

@Composable
fun HelpDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(20.dp))
                .background(ApexPalette.BgSurface)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(20.dp))
                .clickable(enabled = false) { }
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(22.dp))
                    Text(
                        "ApexStudio Pro Editor Guide",
                        color = ApexPalette.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.BgElevated)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HelpSection("Multi-Track Timeline", "Drag playhead to scrub. Tap clip to select. Pinch timeline to zoom in/out. Use the junction (+) button between clips to apply transitions.")
                HelpSection("3D LUT Filters & FX", "Choose from 70+ Hollywood film LUTs and real-time GPU effects (VHS, Glitch, RGB Split). Adjust intensity in real time.")
                HelpSection("Video Adjustments", "Precision controls for Brightness, Contrast, Saturation, Exposure, Highlights, Shadows, Temp, Tint, Sharpness, Vignette & Grain.")
                HelpSection("Keyframe Animation", "Pin keyframes on the timeline to animate position, scale, rotation, and opacity smoothly across clips.")
                HelpSection("Text & Stickers", "Add real timeline layers for captions and stickers. Pinch to resize/rotate and drag on video preview to reposition.")
                HelpSection("Voice Over Recording", "Tap Record in Audio Studio or right toolbar to capture clear voice-overs with live input meter.")
                HelpSection("Hardware Export", "Export up to 4K resolution at 60 FPS with Media3 hardware acceleration.")
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexPalette.NeonCyan)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text("Got It", color = ApexPalette.BgDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(title, color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = ApexPalette.TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
    }
}
