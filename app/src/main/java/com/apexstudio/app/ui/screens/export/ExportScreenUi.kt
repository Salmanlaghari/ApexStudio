package com.apexstudio.app.ui.screens.export

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.apexstudio.app.data.media.VideoThumbnailExtractor
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat

@Composable
internal fun ExportProjectPreviewCard(
    project: Project?,
    isExporting: Boolean,
    progress: Float,
    completed: Boolean
) {
    val context = LocalContext.current
    val clip = remember(project?.clips) {
        project?.clips?.firstOrNull { it.trackIndex == 0 && (it.type == ClipType.VIDEO || it.type == ClipType.IMAGE) }
            ?: project?.clips?.firstOrNull { it.type == ClipType.VIDEO || it.type == ClipType.IMAGE }
    }
    val remoteOrImageUri = when {
        !project?.coverCustomUri.isNullOrBlank() -> project?.coverCustomUri
        !clip?.thumbnail.isNullOrBlank() -> clip?.thumbnail
        clip?.type == ClipType.IMAGE -> clip.uri
        else -> null
    }
    var videoFrame by remember(clip?.uri, project?.coverFrameMs) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(clip?.id, clip?.uri, clip?.trimStartMs, project?.coverFrameMs, remoteOrImageUri) {
        videoFrame = if (remoteOrImageUri == null && clip?.type == ClipType.VIDEO) {
            VideoThumbnailExtractor.extractFrame(
                context,
                clip.uri,
                (project?.coverFrameMs ?: clip.trimStartMs).coerceAtLeast(0L)
            )?.asImageBitmap()
        } else null
    }
    val progressFraction = when {
        completed -> 1f
        isExporting -> progress.coerceIn(0f, 1f)
        else -> 0f
    }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier.width(134.dp).height(88.dp).clip(RoundedCornerShape(12.dp))
                    .background(ApexPalette.BgElevated),
                contentAlignment = Alignment.Center
            ) {
                when {
                    videoFrame != null -> Image(videoFrame!!, contentDescription = "Preview of ${project?.name ?: "video project"}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    remoteOrImageUri != null -> AsyncImage(
                        model = Uri.parse(remoteOrImageUri),
                        contentDescription = "Preview of ${project?.name ?: "video project"}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF241146), Color(0xFF10506A)))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = ApexPalette.TextPrimary, modifier = Modifier.size(32.dp))
                    }
                }
                Box(Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(topStart = 8.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .75f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Text(TimeFormat.msToShort(project?.durationMs ?: 0L), color = ApexPalette.TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(project?.name ?: "Untitled project", color = ApexPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${project?.clips?.size ?: 0} media clips  ·  ${TimeFormat.msToShort(project?.durationMs ?: 0L)}", color = ApexPalette.TextSecondary, fontSize = 10.sp, maxLines = 1)
                Text(
                    when {
                        completed -> "Export complete"
                        isExporting -> "Rendering your video"
                        else -> "Ready to export"
                    },
                    color = if (completed || isExporting) ApexPalette.NeonCyan else ApexPalette.TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Export progress", color = ApexPalette.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${(progressFraction * 100).toInt()}%", color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)).background(ApexPalette.BgElevated)) {
            if (progressFraction > 0f) {
                Box(Modifier.fillMaxWidth(progressFraction).fillMaxHeight().background(Brush.horizontalGradient(listOf(ApexPalette.NeonPurple, Color(0xFF427BFF), ApexPalette.NeonCyan))))
            }
        }
        Text(
            if (isExporting) "Rendering on this device. You can cancel from the export button."
            else if (completed) "The finished file is stored in ApexStudio's export folder on this device."
            else "Choose output settings below before rendering.",
            color = ApexPalette.TextTertiary,
            fontSize = 9.sp,
            maxLines = 2
        )
    }
}

@Composable
internal fun ExportBrandHeader(onSettings: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(Modifier.size(42.dp)) {
            val w = size.width
            val h = size.height
            val mark = Path().apply {
                moveTo(w * .08f, h * .88f)
                lineTo(w * .48f, h * .10f)
                quadraticTo(w * .52f, h * .01f, w * .57f, h * .10f)
                lineTo(w * .96f, h * .88f)
            }
            drawPath(mark, Brush.linearGradient(listOf(ApexPalette.NeonPurple, Color(0xFF547BFF), ApexPalette.NeonCyan)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * .18f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawLine(Color(0xFF8A70FF), Offset(w * .28f, h * .62f), Offset(w * .75f, h * .62f), strokeWidth = w * .14f, cap = StrokeCap.Round)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row {
                Text("Apex", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Studio", color = ApexPalette.NeonPurple, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Text("AI VIDEO EDITOR", color = ApexPalette.TextSecondary, fontSize = 7.sp, letterSpacing = 2.sp)
        }
        if (onSettings != null) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = ApexPalette.TextPrimary)
            }
        }
    }
}

@Composable
internal fun ExportPrimarySettingsRows(
    resolution: String,
    frameRate: Int,
    enabled: Boolean,
    onResolutionSelected: (String) -> Unit,
    onFrameRateSelected: (Int) -> Unit
) {
    val resolutionOptions = listOf(
        "720P" to "1280 × 720",
        "1080P" to "1920 × 1080",
        "1440P" to "2560 × 1440",
        "4K" to "3840 × 2160"
    )
    val fpsOptions = listOf(24, 30, 60, 90, 120).map { "$it FPS" to "${it} fps" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExportSettingPickerRow(
            badge = "HD",
            value = if (resolution.equals("4K", ignoreCase = true)) "4K" else "${resolution.removeSuffix("P").removeSuffix("p")}p",
            subtitle = when (resolution.uppercase()) {
                "720P" -> "HD resolution"
                "1440P" -> "2K QHD resolution"
                "4K" -> "Ultra HD resolution"
                else -> "Full HD resolution"
            },
            options = resolutionOptions,
            selected = resolution.uppercase(),
            enabled = enabled,
            testTag = "export-resolution-setting",
            onSelect = onResolutionSelected
        )
        ExportSettingPickerRow(
            badge = "FPS",
            value = "$frameRate FPS",
            subtitle = "Smooth motion",
            options = fpsOptions,
            selected = "$frameRate FPS",
            enabled = enabled,
            testTag = "export-frame-rate-setting",
            onSelect = { onFrameRateSelected(it.substringBefore(' ').toInt()) }
        )
        ExportSettingPickerRow(
            badge = "FILE",
            value = "MP4",
            subtitle = "Widely compatible format",
            options = null,
            selected = "MP4",
            enabled = false,
            testTag = "export-format-setting",
            onSelect = {}
        )
    }
}

@Composable
private fun ExportSettingPickerRow(
    badge: String,
    value: String,
    subtitle: String,
    options: List<Pair<String, String>>?,
    selected: String,
    enabled: Boolean,
    testTag: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember(testTag) { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF1B1E29))
                .border(1.dp, Color(0xFF292E3B), RoundedCornerShape(13.dp))
                .then(if (options != null) Modifier.clickable(enabled = enabled) { expanded = true } else Modifier)
                .testTag(testTag)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF242A38)), contentAlignment = Alignment.Center) {
                Text(badge, color = Color(0xFFE1E5F2), fontSize = if (badge.length > 3) 8.sp else 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, color = ApexPalette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(subtitle, color = ApexPalette.TextSecondary, fontSize = 10.sp, maxLines = 1)
            }
            if (options != null) Text("›", color = ApexPalette.TextSecondary, fontSize = 24.sp, lineHeight = 24.sp)
        }
        if (options != null) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (label, detail) ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(label, fontWeight = FontWeight.SemiBold)
                                Text(detail, color = ApexPalette.TextSecondary, fontSize = 10.sp)
                            }
                        },
                        trailingIcon = if (label.equals(selected, ignoreCase = true)) {
                            { Text("✓", color = ApexPalette.NeonPurple, fontWeight = FontWeight.Bold) }
                        } else null,
                        onClick = { expanded = false; onSelect(label) }
                    )
                }
            }
        }
    }
}
