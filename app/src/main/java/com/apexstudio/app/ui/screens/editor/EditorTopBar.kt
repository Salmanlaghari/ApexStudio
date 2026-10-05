package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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

// === 1. TOP APP BAR ===
@Composable
fun TopAppBarSection(
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    resolution: String = "1080P",
    onSelectResolution: (String) -> Unit = {},
    isCoverMode: Boolean = true,
    onToggleCoverMode: () -> Unit = {},
    onFullscreenToggle: () -> Unit = {},
    onBack: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onHelp: () -> Unit = {},
    onExport: () -> Unit = {}
) {
    var showResolutionDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Hamburger menu + Two-line title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onBack)
            )

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Apex",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Studio",
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = "Pro Video Editor",
                    color = Color(0xFF9CA3AF),
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Right side: Fit/Cover toggle + Fullscreen + Resolution dropdown + Export button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Fit / Cover Mode Toggle Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1B1B26))
                    .border(1.dp, Color(0xFF2E2E40), RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleCoverMode)
                    .padding(horizontal = 7.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isCoverMode) "COVER" else "FIT",
                    color = Color(0xFFD1D5DB),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Fullscreen Preview Toggle
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF1B1B26))
                    .border(1.dp, Color(0xFF2E2E40), CircleShape)
                    .clickable(onClick = onFullscreenToggle)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Resolution Dropdown
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1B1B26))
                        .border(1.dp, Color(0xFF2E2E40), RoundedCornerShape(8.dp))
                        .clickable { showResolutionDropdown = true }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = resolution,
                            color = Color(0xFFD1D5DB),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Resolution",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                androidx.compose.material3.DropdownMenu(
                    expanded = showResolutionDropdown,
                    onDismissRequest = { showResolutionDropdown = false },
                    modifier = Modifier.background(ApexPalette.BgElevated)
                ) {
                    listOf("720P", "1080P", "1440P", "4K").forEach { res ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    text = res,
                                    color = if (res == resolution) Color(0xFF8B5CF6) else Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            onClick = {
                                onSelectResolution(res)
                                showResolutionDropdown = false
                            }
                        )
                    }
                }
            }

            // Prominent Gradient Export Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF2563EB), Color(0xFF3B82F6))
                        )
                    )
                    .clickable(onClick = onExport)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Export",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Export",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

