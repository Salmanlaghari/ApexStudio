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
import com.apexstudio.app.ui.components.BuildIdentity
import com.apexstudio.app.ui.components.BuildIdentityDialog
import com.apexstudio.app.ui.theme.ApexPalette

// === 1. TOP APP BAR ===
// COMPACT (Pro Phase 1): slim 40dp bar — small single-line title + small
// Export button so the video preview gets maximum full view area.
@Composable
fun TopAppBarSection(
    title: String = "ApexStudio",
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
    var showBuildIdentity by remember { mutableStateOf(false) }
    if (showBuildIdentity) {
        BuildIdentityDialog(onDismiss = { showBuildIdentity = false })
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back navigation and the current project name stay outside the video.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF111827),
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onBack)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showBuildIdentity = true }
            ) {
                Text(
                    text = title.ifBlank { "Untitled project" },
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
                Text(
                    text = "Build ${BuildIdentity.shortCommit}",
                    color = Color(0xFF6B7280),
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }

        // Right side: compact controls + small Export button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Fit / Cover Mode Toggle Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFFF3F4F6))
                    .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(7.dp))
                    .clickable(onClick = onToggleCoverMode)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isCoverMode) "COVER" else "FIT",
                    color = Color(0xFF111827),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Fullscreen Preview Toggle
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFF3F4F6))
                    .border(1.dp, Color(0xFFD1D5DB), CircleShape)
                    .clickable(onClick = onFullscreenToggle)
                    .padding(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color(0xFF111827),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Resolution Dropdown
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFFF3F4F6))
                        .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(7.dp))
                        .clickable { showResolutionDropdown = true }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = resolution,
                            color = Color(0xFF111827),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Resolution",
                            tint = Color(0xFF4B5563),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                androidx.compose.material3.DropdownMenu(
                    expanded = showResolutionDropdown,
                    onDismissRequest = { showResolutionDropdown = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    listOf("720P", "1080P", "1440P", "4K").forEach { res ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    text = res,
                                    color = if (res == resolution) Color(0xFF2563EB) else Color(0xFF111827),
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

            // Compact Export Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Color(0xFFE5E7EB)
                    )
                    .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(8.dp))
                    .clickable(onClick = onExport)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Export",
                        tint = Color(0xFF111827),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Export",
                        color = Color(0xFF111827),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
