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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.components.BuildIdentity
import com.apexstudio.app.ui.components.BuildIdentityDialog
import com.apexstudio.app.ui.theme.EditorPalette

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
            .height(EditorLayoutMetrics.TopBarHeight)
            .padding(horizontal = EditorLayoutMetrics.TopBarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back navigation and the current project name stay outside the video.
        Row(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = EditorPalette.TextPrimary,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onBack)
                    .padding(8.dp)
            )

            Column(modifier = Modifier.weight(1f)
                .semantics { contentDescription = "Build identity" }
                .clickable { showBuildIdentity = true }) {
                Text(
                    text = "ApexStudio",
                    color = EditorPalette.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
                Text(
                    text = title.ifBlank { "Untitled project" },
                    color = EditorPalette.TextSecondary,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                    .background(EditorPalette.BgElevated)
                    .border(1.dp, EditorPalette.BorderGlass, RoundedCornerShape(7.dp))
                    .clickable(onClick = onToggleCoverMode)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isCoverMode) "COVER" else "FIT",
                    color = EditorPalette.TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Resolution Dropdown
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(EditorPalette.BgElevated)
                        .border(1.dp, EditorPalette.BorderGlass, RoundedCornerShape(7.dp))
                        .clickable { showResolutionDropdown = true }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = resolution,
                            color = EditorPalette.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Resolution",
                            tint = EditorPalette.TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                androidx.compose.material3.DropdownMenu(
                    expanded = showResolutionDropdown,
                    onDismissRequest = { showResolutionDropdown = false },
                    modifier = Modifier.background(EditorPalette.BgSurface)
                ) {
                    listOf("720P", "1080P", "1440P", "4K").forEach { res ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    text = res,
                                    color = if (res == resolution) Color(0xFF2563EB) else EditorPalette.TextPrimary,
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
                        EditorPalette.GradientPrimary
                    )
                    .border(1.dp, EditorPalette.BorderGlass, RoundedCornerShape(8.dp))
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
                        tint = EditorPalette.TextPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Export",
                        color = EditorPalette.TextPrimary,
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
