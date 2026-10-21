package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.components.BuildIdentityDialog
import com.apexstudio.app.ui.theme.EditorPalette

/** Branded editor header: identity is in the mark, while project details remain semantic. */
@OptIn(ExperimentalFoundationApi::class)
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
    projectId: String = "",
    exportEnabled: Boolean = true,
    resolutionEnabled: Boolean = true
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .combinedClickable(
                    role = Role.Button,
                    onClick = { showBuildIdentity = true },
                    onLongClick = onBack
                )
                .semantics {
                    contentDescription = "ApexStudio build identity. Project ${projectId.ifBlank { title }}"
                    customActions = listOf(CustomAccessibilityAction("Back") {
                        onBack()
                        true
                    })
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            ApexStudioMark(Modifier.size(32.dp))
            Spacer(Modifier.width(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 145.dp)) {
                Text("Apex", color = EditorPalette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false)
                Text("Studio", color = EditorPalette.NeonPurple, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
        }

        Box {
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .widthIn(min = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, EditorPalette.BorderGlass, RoundedCornerShape(8.dp))
                    .clickable(enabled = resolutionEnabled, onClick = { showResolutionDropdown = true }, role = Role.Button)
                    .semantics {
                        contentDescription = "Resolution: $resolution; preview ${if (isCoverMode) "cover" else "fit"}"
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(resolution, color = EditorPalette.TextPrimary, fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp, maxLines = 1, softWrap = false)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = EditorPalette.TextSecondary,
                    modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = showResolutionDropdown,
                onDismissRequest = { showResolutionDropdown = false },
                modifier = Modifier.background(EditorPalette.BgSurface)
            ) {
                DropdownMenuItem(
                    text = { Text(if (isCoverMode) "Preview: Cover" else "Preview: Fit", color = EditorPalette.TextPrimary) },
                    onClick = {
                        onToggleCoverMode()
                        showResolutionDropdown = false
                    }
                )
                HorizontalDivider(color = EditorPalette.Divider)
                listOf("720P", "1080P", "1440P", "4K").forEach { res ->
                    DropdownMenuItem(
                        text = {
                            Text(res, color = if (res == resolution) EditorPalette.NeonCyan else EditorPalette.TextPrimary,
                                fontWeight = FontWeight.SemiBold)
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
                .height(34.dp)
                .widthIn(min = 78.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(EditorPalette.GradientPrimary)
                .clickable(enabled = exportEnabled, onClick = onExport, role = Role.Button)
                .semantics { contentDescription = "Export" }
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Upload, contentDescription = null, tint = EditorPalette.TextPrimary,
                modifier = Modifier.size(15.dp))
            Text("Export", color = EditorPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** Small original Compose vector mark; no raster or remote branding dependency. */
@Composable
private fun ApexStudioMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val outer = Path().apply {
            moveTo(w * .5f, h * .04f)
            lineTo(w * .98f, h * .94f)
            lineTo(w * .02f, h * .94f)
            close()
        }
        drawPath(outer, Brush.linearGradient(listOf(EditorPalette.NeonCyan, EditorPalette.NeonPurple)), style = Fill)
        val cutout = Path().apply {
            moveTo(w * .5f, h * .37f)
            lineTo(w * .69f, h * .77f)
            lineTo(w * .31f, h * .77f)
            close()
        }
        drawPath(cutout, EditorPalette.BgBase, style = Fill)
        drawLine(
            brush = Brush.horizontalGradient(listOf(EditorPalette.NeonCyan, EditorPalette.NeonPurple)),
            start = androidx.compose.ui.geometry.Offset(w * .37f, h * .84f),
            end = androidx.compose.ui.geometry.Offset(w * .63f, h * .84f),
            strokeWidth = h * .055f
        )
    }
}
