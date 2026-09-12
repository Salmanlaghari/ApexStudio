package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat

private data class CoverStyleOption(
    val id: String,
    val label: String,
    val textColor: Color,
    val bgColors: List<Color>
)

@Composable
fun CoverPanel(
    currentPlayheadMs: Long,
    coverFrameMs: Long?,
    customCoverUri: String?,
    initialCoverText: String?,
    initialCoverStyle: String?,
    onSelectFrameAtPlayhead: (Long) -> Unit,
    onSelectCustomCover: () -> Unit,
    onSaveCoverWords: (text: String, style: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var coverText by remember(initialCoverText) { mutableStateOf(initialCoverText ?: "") }
    var selectedStyle by remember(initialCoverStyle) { mutableStateOf(initialCoverStyle ?: "cinema") }

    val styleOptions = remember {
        listOf(
            CoverStyleOption("cinema", "Cinema Bold", Color.White, listOf(Color(0xFFB71C1C), Color(0xFF000000))),
            CoverStyleOption("neon", "Neon Glow", Color(0xFF00F0FF), listOf(Color(0xFF4A148C), Color(0xFF0D47A1))),
            CoverStyleOption("gold", "Luxury Gold", Color(0xFFFFD700), listOf(Color(0xFF3E2723), Color(0xFF212121))),
            CoverStyleOption("cyberpunk", "Cyberpunk", Color.Black, listOf(Color(0xFFFFEA00), Color(0xFFFF9100))),
            CoverStyleOption("vlog", "Vlog Chic", Color.White, listOf(Color(0xFFE91E63), Color(0xFF880E4F)))
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 580.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.NeonPurple.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = ApexPalette.NeonPurple, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text("Cover & Thumbnail Studio", color = ApexPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Select frame & add bold Cover Words", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // Cover Source Options
        Text("1. CHOOSE COVER IMAGE / FRAME", color = ApexPalette.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Option 1: Use Current Video Frame
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApexPalette.BgBase)
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable {
                        onSelectFrameAtPlayhead(currentPlayheadMs)
                    }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(22.dp))
                    Text("Capture Frame", color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("At ${TimeFormat.msToShort(currentPlayheadMs)}", color = ApexPalette.NeonCyan, fontSize = 10.sp)
                }
            }

            // Option 2: Upload Custom Image Cover
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApexPalette.BgBase)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
                    .clickable {
                        onSelectCustomCover()
                    }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = ApexPalette.NeonPurple, modifier = Modifier.size(22.dp))
                    Text("Custom Photo", color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("From device gallery", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                }
            }
        }

        if (coverFrameMs != null || customCoverUri != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ApexPalette.NeonCyan.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(14.dp))
                Text(
                    text = if (customCoverUri != null) "Custom cover image active"
                    else "Cover thumbnail frame set to ${TimeFormat.msToShort(coverFrameMs ?: 0L)}",
                    color = ApexPalette.NeonCyan,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 2. Cover Words Section
        Text("2. \"COVER\" WORDS / HEADLINE", color = ApexPalette.NeonPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = coverText,
            onValueChange = { coverText = it },
            placeholder = { Text("Enter Cover Title (e.g. EPIC HIGHLIGHTS)", color = ApexPalette.TextSecondary.copy(alpha = 0.6f), fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null, tint = ApexPalette.NeonPurple) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = ApexPalette.NeonPurple,
                unfocusedBorderColor = Color(0xFF2A2A3C)
            ),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))

        // Cover Word Styles
        Text("COVER WORD STYLES", color = ApexPalette.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(styleOptions) { style ->
                val isSelected = selectedStyle == style.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(style.bgColors))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedStyle = style.id }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = style.label,
                        color = style.textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Live Cover Preview with Words
        if (coverText.isNotBlank()) {
            val currentStyle = styleOptions.firstOrNull { it.id == selectedStyle } ?: styleOptions.first()
            Text("COVER PREVIEW", color = ApexPalette.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F0F1A))
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.horizontalGradient(currentStyle.bgColors.map { it.copy(alpha = 0.85f) }))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = coverText.uppercase(),
                        color = currentStyle.textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Apply Button
        Button(
            onClick = {
                onSaveCoverWords(coverText, selectedStyle)
                onClose()
            },
            colors = ButtonDefaults.buttonColors(containerColor = ApexPalette.NeonCyan),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save Cover & Words", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
