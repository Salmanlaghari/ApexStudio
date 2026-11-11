package com.apexstudio.app.ui.screens.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.template.ShortsMusicRepository
import com.apexstudio.app.data.template.ShortsTemplate
import com.apexstudio.app.data.template.ShortsTemplateCatalog
import com.apexstudio.app.ui.screens.editor.rememberTextFontFamily
import com.apexstudio.app.ui.theme.ApexPalette
import java.util.Locale

@Composable
fun ShortsTemplatesScreen(
    onBack: () -> Unit,
    onUseTemplate: (String) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val catalogResult = remember(context) {
        runCatching { ShortsMusicRepository.loadCatalog(context).associateBy { it.id } }
    }
    val musicById = catalogResult.getOrNull().orEmpty()
    var selectedTemplate by remember { mutableStateOf<ShortsTemplate?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexPalette.BgBase)
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ApexPalette.TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Shorts templates", color = ApexPalette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("10 editable video starters", color = ApexPalette.TextSecondary, fontSize = 12.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Text(
                "Cards preview sample text and styling; details list title/subtitle motion and soundtrack licensing. Choose one video to start editing—music downloads only when you apply the template.",
                color = ApexPalette.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text("Vertical 9:16. Starts with up to 30 seconds; adjust the crop and trim in the editor.", color = ApexPalette.TextPrimary, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        if (catalogResult.isFailure) {
            Text(
                "Music details could not be loaded. Reopen this page to try again.",
                color = Color(0xFFFFB4AB),
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(ShortsTemplateCatalog.templates.chunked(2), key = { row -> row.first().id }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { template ->
                        ShortsTemplateCard(
                            template = template,
                            musicTitle = musicById[template.musicAssetId]?.trackTitle ?: "Music details unavailable",
                            musicLicense = musicById[template.musicAssetId]?.license ?: "",
                            modifier = Modifier.weight(1f),
                            onClick = { selectedTemplate = template }
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    selectedTemplate?.let { template ->
        val music = musicById[template.musicAssetId]
        AlertDialog(
            onDismissRequest = { selectedTemplate = null },
            title = { Text(template.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(template.description, color = ApexPalette.TextSecondary, fontSize = 13.sp)
                    Text("Sample title: ${template.sampleTitle}", color = ApexPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Sample subtitle: ${template.sampleSubtitle}", color = ApexPalette.TextSecondary, fontSize = 12.sp)
                    Text(
                        "Title motion: ${motionName(template.titleAnimationId)} · subtitle: ${motionName(template.subtitleAnimationId)}",
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp
                    )
                    if (music != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("${music.trackTitle} — ${music.artist}", color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "${music.license}${if (music.attributionRequired) " · credit required" else " · credit appreciated"} · ${formatMiB(music.bytes)} · ${music.durationMs / 1000}s",
                            color = ApexPalette.TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            if (music.attributionRequired) "Credit to include: ${music.creditText}" else "Courtesy credit: ${music.creditText}",
                            color = ApexPalette.TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { runCatching { uriHandler.openUri(music.sourcePageUrl) } }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("Source", fontSize = 11.sp)
                            }
                            TextButton(onClick = { runCatching { uriHandler.openUri(music.licenseUrl) } }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("License", fontSize = 11.sp)
                            }
                            if (music.attribution.isNotBlank()) {
                                TextButton(onClick = { clipboard.setText(AnnotatedString(music.creditText)) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Text("Copy credit", fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        Text("The music metadata is unavailable, so this template cannot be applied yet.", color = Color(0xFFFFB4AB), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = music != null,
                    onClick = {
                        selectedTemplate = null
                        onUseTemplate(template.id)
                    }
                ) { Text("Choose video") }
            },
            dismissButton = {
                TextButton(onClick = { selectedTemplate = null }) { Text("Back") }
            }
        )
    }
}

@Composable
private fun ShortsTemplateCard(
    template: ShortsTemplate,
    musicTitle: String,
    musicLicense: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val titleFontFamily = rememberTextFontFamily(template.titleFontFamily, isBold = true, isItalic = false)
    val subtitleFontFamily = rememberTextFontFamily(template.subtitleFontFamily, isBold = false, isItalic = false)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(template.backgroundStartArgb.toInt()),
                            Color(template.backgroundEndArgb.toInt())
                        )
                    )
                )
                .padding(9.dp)
        ) {
            Text(
                template.category.uppercase(Locale.ROOT),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    template.sampleTitle,
                    color = Color(template.titleColorArgb.toInt()),
                    fontSize = 14.sp,
                    fontFamily = titleFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    template.sampleSubtitle,
                    color = Color(template.subtitleColorArgb.toInt()),
                    fontSize = 9.sp,
                    fontFamily = subtitleFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${template.targetDurationMs / 1000}s",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(template.title, color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(
            musicTitle,
            color = ApexPalette.NeonCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(musicLicense, color = ApexPalette.TextSecondary, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatMiB(bytes: Long): String = String.format(Locale.US, "%.1f MB download", bytes / (1024f * 1024f))

private fun motionName(id: String): String = when (id) {
    "FADE_IN" -> "Fade in"
    "SLIDE_UP" -> "Rise"
    "SLIDE_BOUNCE" -> "Spring up"
    "BOUNCE" -> "Bounce"
    else -> id.lowercase(Locale.ROOT).replace('_', ' ')
}

private fun String.toComposeFontFamily() = when (lowercase(Locale.ROOT)) {
    "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
    "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
    "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
    else -> androidx.compose.ui.text.font.FontFamily.SansSerif
}
