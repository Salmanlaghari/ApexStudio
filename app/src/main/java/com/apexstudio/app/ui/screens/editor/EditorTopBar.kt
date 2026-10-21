package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.components.BuildIdentityDialog
import com.apexstudio.app.ui.theme.EditorPalette

/** Project identity and export controls shown above the editor preview. */
@Composable
fun TopAppBarSection(
    title: String = "ApexStudio",
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    resolution: String = "1080P",
    onSelectResolution: (String) -> Unit = {},
    isCoverMode: Boolean = DEFAULT_PREVIEW_COVER_MODE,
    onToggleCoverMode: () -> Unit = {},
    onFullscreenToggle: () -> Unit = {},
    onBack: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onHelp: () -> Unit = {},
    onExport: () -> Unit = {},
    projectId: String = ""
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .fillMaxHeight()
                    .clickable(onClick = onBack, role = Role.Button)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = EditorPalette.TextPrimary,
                    modifier = Modifier.size(22.dp).padding(start = 2.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = { showBuildIdentity = true }, role = Role.Button)
                    .semantics { contentDescription = "Build identity" },
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title.ifBlank { "ApexStudio" },
                    color = EditorPalette.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
                Text(
                    text = projectId.ifBlank { title.ifBlank { "Untitled project" } },
                    color = EditorPalette.TextSecondary,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }

        Box(
            modifier = Modifier
                .widthIn(min = 40.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(EditorPalette.BgElevated)
                .border(1.dp, EditorPalette.BorderGlass, RoundedCornerShape(7.dp))
                .clickable(onClick = onToggleCoverMode, role = Role.Button)
                .semantics { contentDescription = if (isCoverMode) "Switch to fit" else "Switch to cover" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isCoverMode) "COVER" else "FIT",
                color = EditorPalette.TextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp)
            )
        }

        Box {
            Row(
                modifier = Modifier
                    .height(26.dp)
                    .widthIn(min = 70.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(EditorPalette.BgElevated)
                    .border(1.dp, EditorPalette.BorderGlass, RoundedCornerShape(7.dp))
                    .clickable(onClick = { showResolutionDropdown = true }, role = Role.Button)
                    .semantics { contentDescription = "Resolution: $resolution" }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = resolution,
                    color = EditorPalette.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = EditorPalette.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
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

        Row(
            modifier = Modifier
                .widthIn(min = 72.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EditorPalette.GradientPrimary)
                .border(1.dp, EditorPalette.BorderGlass, RoundedCornerShape(8.dp))
                .clickable(onClick = onExport, role = Role.Button)
                .semantics { contentDescription = "Export" }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Upload,
                contentDescription = null,
                tint = EditorPalette.TextPrimary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Export",
                color = EditorPalette.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 3.dp)
            )
        }
    }
}
