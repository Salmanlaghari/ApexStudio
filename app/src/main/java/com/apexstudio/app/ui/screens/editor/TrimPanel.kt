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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat

@Composable
fun TrimPanel(
    clip: MediaClip?,
    currentPlayheadMs: Long,
    onTrimChange: (startMs: Long, endMs: Long) -> Unit,
    onSetStartAtPlayhead: () -> Unit,
    onSetEndAtPlayhead: () -> Unit,
    onResetTrim: () -> Unit,
    onPreviewTrimmed: () -> Unit,
    onExport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (clip == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(ApexPalette.BgElevated)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Select a video clip on the timeline to trim",
                color = ApexPalette.TextSecondary,
                fontSize = 13.sp
            )
        }
        return
    }

    val duration = clip.durationMs.coerceAtLeast(1000L)
    val trimStart = clip.trimStartMs.coerceIn(0L, duration - 100L)
    val trimEnd = clip.trimEndMs.coerceIn(trimStart + 100L, duration)
    val trimmedDuration = (trimEnd - trimStart).coerceAtLeast(0L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        ApexPalette.BgElevated,
                        ApexPalette.BgDeep
                    )
                )
            )
            .border(
                1.dp,
                ApexPalette.NeonCyan.copy(alpha = 0.3f),
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Drag handle & Header
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ApexPalette.TextTertiary.copy(alpha = 0.5f))
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ContentCut,
                        contentDescription = "Trim tool",
                        tint = ApexPalette.NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Trim & Set Clip Points",
                        color = ApexPalette.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
                Text(
                    "Media3 Transformer Frame-Accurate Precision",
                    color = ApexPalette.TextTertiary,
                    fontSize = 10.sp
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgGlass)
                    .testTag("close_trim_panel_button")
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = ApexPalette.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Summary Metric Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ApexPalette.BgGlass)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trim Start
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("START POINT", color = ApexPalette.NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        TimeFormat.formatMs(trimStart),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(ApexPalette.BorderGlass)
                )

                // Trimmed Duration
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TRIMMED DURATION", color = ApexPalette.NeonPink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        TimeFormat.formatMs(trimmedDuration),
                        color = ApexPalette.NeonPink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "of ${TimeFormat.formatMs(duration)}",
                        color = ApexPalette.TextTertiary,
                        fontSize = 9.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(ApexPalette.BorderGlass)
                )

                // Trim End
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("END POINT", color = ApexPalette.NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        TimeFormat.formatMs(trimEnd),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // One-Touch Quick Actions at Playhead
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSetStartAtPlayhead,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("set_start_at_playhead_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApexPalette.BgElevated,
                    contentColor = ApexPalette.NeonCyan
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.FirstPage, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Set Start Here", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onSetEndAtPlayhead,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("set_end_at_playhead_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApexPalette.BgElevated,
                    contentColor = ApexPalette.NeonCyan
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.LastPage, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Set End Here", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Start Point Adjuster
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ApexPalette.BgGlass)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Start Point", color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(TimeFormat.formatMs(trimStart), color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }

            Slider(
                value = trimStart.toFloat(),
                onValueChange = { newStart ->
                    val s = newStart.toLong().coerceIn(0L, trimEnd - 100L)
                    onTrimChange(s, trimEnd)
                },
                valueRange = 0f..(duration - 100L).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = ApexPalette.NeonCyan,
                    activeTrackColor = ApexPalette.NeonCyan,
                    inactiveTrackColor = ApexPalette.BgBase
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .testTag("trim_start_slider")
            )

            // Nudge buttons for Start
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NudgeButton(label = "-1s", onClick = { onTrimChange((trimStart - 1000L).coerceAtLeast(0L), trimEnd) }, modifier = Modifier.weight(1f))
                NudgeButton(label = "-0.1s", onClick = { onTrimChange((trimStart - 100L).coerceAtLeast(0L), trimEnd) }, modifier = Modifier.weight(1f))
                NudgeButton(label = "+0.1s", onClick = { onTrimChange((trimStart + 100L).coerceAtMost(trimEnd - 100L), trimEnd) }, modifier = Modifier.weight(1f))
                NudgeButton(label = "+1s", onClick = { onTrimChange((trimStart + 1000L).coerceAtMost(trimEnd - 100L), trimEnd) }, modifier = Modifier.weight(1f))
            }
        }

        // End Point Adjuster
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ApexPalette.BgGlass)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("End Point", color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(TimeFormat.formatMs(trimEnd), color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }

            Slider(
                value = trimEnd.toFloat(),
                onValueChange = { newEnd ->
                    val e = newEnd.toLong().coerceIn(trimStart + 100L, duration)
                    onTrimChange(trimStart, e)
                },
                valueRange = 100f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = ApexPalette.NeonCyan,
                    activeTrackColor = ApexPalette.NeonCyan,
                    inactiveTrackColor = ApexPalette.BgBase
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .testTag("trim_end_slider")
            )

            // Nudge buttons for End
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NudgeButton(label = "-1s", onClick = { onTrimChange(trimStart, (trimEnd - 1000L).coerceAtLeast(trimStart + 100L)) }, modifier = Modifier.weight(1f))
                NudgeButton(label = "-0.1s", onClick = { onTrimChange(trimStart, (trimEnd - 100L).coerceAtLeast(trimStart + 100L)) }, modifier = Modifier.weight(1f))
                NudgeButton(label = "+0.1s", onClick = { onTrimChange(trimStart, (trimEnd + 100L).coerceAtMost(duration)) }, modifier = Modifier.weight(1f))
                NudgeButton(label = "+1s", onClick = { onTrimChange(trimStart, (trimEnd + 1000L).coerceAtMost(duration)) }, modifier = Modifier.weight(1f))
            }
        }

        // Quick Preset Clips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PresetPill(
                label = "First 5s",
                onClick = { onTrimChange(0L, 5000L.coerceAtMost(duration)) },
                modifier = Modifier.weight(1f)
            )
            PresetPill(
                label = "First 15s",
                onClick = { onTrimChange(0L, 15000L.coerceAtMost(duration)) },
                modifier = Modifier.weight(1f)
            )
            PresetPill(
                label = "First 30s",
                onClick = { onTrimChange(0L, 30000L.coerceAtMost(duration)) },
                modifier = Modifier.weight(1f)
            )
            PresetPill(
                label = "Reset Full",
                onClick = onResetTrim,
                modifier = Modifier.weight(1f),
                isAccent = true
            )
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPreviewTrimmed,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("preview_trimmed_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApexPalette.BgElevated,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ApexPalette.BorderGlass)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Preview Cut", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onExport,
                modifier = Modifier
                    .weight(1.3f)
                    .height(44.dp)
                    .testTag("export_trimmed_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = ApexPalette.BgDeep
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = ApexPalette.BgDeep, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export Trimmed", color = ApexPalette.BgDeep, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NudgeButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = ApexPalette.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PresetPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isAccent) ApexPalette.NeonPink.copy(alpha = 0.15f)
                else ApexPalette.BgElevated
            )
            .border(
                1.dp,
                if (isAccent) ApexPalette.NeonPink.copy(alpha = 0.5f)
                else ApexPalette.BorderGlass,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (isAccent) ApexPalette.NeonPink else ApexPalette.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
