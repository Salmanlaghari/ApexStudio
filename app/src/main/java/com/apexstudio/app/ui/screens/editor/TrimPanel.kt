package com.apexstudio.app.ui.screens.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
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
    modifier: Modifier = Modifier,
    isTransformerTrimming: Boolean = false,
    transformerTrimProgress: Float = 0f,
    transformerTrimMessage: String? = null,
    transformerTrimError: String? = null,
    onTrimWithTransformer: ((clipId: String, startMs: Long, endMs: Long, replaceInTimeline: Boolean) -> Unit)? = null,
    onCancelTransformerTrim: (() -> Unit)? = null,
    onClearTransformerStatus: (() -> Unit)? = null
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

    var replaceInTimeline by remember { mutableStateOf(true) }
    var startInputText by remember(trimStart) { mutableStateOf(TimeFormat.formatMs(trimStart)) }
    var endInputText by remember(trimEnd) { mutableStateOf(TimeFormat.formatMs(trimEnd)) }
    var inputError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

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
                        "Trim & Cut Clip",
                        color = ApexPalette.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
                Text(
                    "Media3 Transformer Frame-Accurate Precision",
                    color = ApexPalette.NeonCyan.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
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
                    Text("START TIMESTAMP", color = ApexPalette.NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                    Text("END TIMESTAMP", color = ApexPalette.NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        TimeFormat.formatMs(trimEnd),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Transformer Notification / Status Banner
        AnimatedVisibility(visible = transformerTrimMessage != null || transformerTrimError != null) {
            if (transformerTrimMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ApexPalette.NeonEmerald.copy(alpha = 0.15f))
                        .border(1.dp, ApexPalette.NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ApexPalette.NeonEmerald, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(transformerTrimMessage, color = ApexPalette.NeonEmerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { onClearTransformerStatus?.invoke() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = ApexPalette.NeonEmerald, modifier = Modifier.size(14.dp))
                    }
                }
            } else if (transformerTrimError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Red.copy(alpha = 0.15f))
                        .border(1.dp, Color.Red.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(transformerTrimError, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { onClearTransformerStatus?.invoke() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.Red, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Active Trimming Progress Banner
        AnimatedVisibility(visible = isTransformerTrimming) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApexPalette.BgGlass)
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = ApexPalette.NeonCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Media3 Transformer Trimming...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "${(transformerTrimProgress * 100).toInt()}%",
                        color = ApexPalette.NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                LinearProgressIndicator(
                    progress = { transformerTrimProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ApexPalette.NeonCyan,
                    trackColor = ApexPalette.BgElevated
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onCancelTransformerTrim?.invoke() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Direct Timestamp Input Fields (Allows users to specify exact start/end timestamps)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ApexPalette.BgGlass)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Specify Exact Timestamps",
                    color = ApexPalette.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "e.g. 00:02.50 or 2.5s",
                    color = ApexPalette.TextTertiary,
                    fontSize = 10.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start Timestamp Input
                OutlinedTextField(
                    value = startInputText,
                    onValueChange = {
                        startInputText = it
                        inputError = null
                    },
                    label = { Text("Start Time", fontSize = 10.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_timestamp_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ApexPalette.NeonCyan,
                        unfocusedBorderColor = ApexPalette.BorderGlass,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = ApexPalette.NeonCyan,
                        unfocusedLabelColor = ApexPalette.TextSecondary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // End Timestamp Input
                OutlinedTextField(
                    value = endInputText,
                    onValueChange = {
                        endInputText = it
                        inputError = null
                    },
                    label = { Text("End Time", fontSize = 10.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            val s = TimeFormat.parseTimeToMs(startInputText)
                            val e = TimeFormat.parseTimeToMs(endInputText)
                            if (s != null && e != null && e > s) {
                                onTrimChange(s.coerceIn(0L, duration - 100L), e.coerceIn(s + 100L, duration))
                            } else {
                                inputError = "Invalid timestamps! End must be greater than start."
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("end_timestamp_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ApexPalette.NeonCyan,
                        unfocusedBorderColor = ApexPalette.BorderGlass,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = ApexPalette.NeonCyan,
                        unfocusedLabelColor = ApexPalette.TextSecondary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            if (inputError != null) {
                Text(
                    inputError!!,
                    color = Color.Red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        val s = TimeFormat.parseTimeToMs(startInputText)
                        val e = TimeFormat.parseTimeToMs(endInputText)
                        if (s != null && e != null && e > s) {
                            val safeS = s.coerceIn(0L, duration - 100L)
                            val safeE = e.coerceIn(safeS + 100L, duration)
                            onTrimChange(safeS, safeE)
                        } else {
                            inputError = "Invalid timestamps! Enter valid times (e.g. 00:01.50 or 1.5)."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ApexPalette.NeonCyan.copy(alpha = 0.2f),
                        contentColor = ApexPalette.NeonCyan
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("apply_timestamps_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Apply Timestamps", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

        // Start Point Slider Adjuster
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
                Text("Start Timestamp", color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

        // End Point Slider Adjuster
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
                Text("End Timestamp", color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

        // Media3 Transformer Execution Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ApexPalette.NeonCyan.copy(alpha = 0.08f),
                            ApexPalette.BgElevated
                        )
                    )
                )
                .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.MovieFilter,
                                contentDescription = null,
                                tint = ApexPalette.NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Media3 Transformer Cut",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Render trimmed MP4 using hardware acceleration",
                            color = ApexPalette.TextTertiary,
                            fontSize = 10.sp
                        )
                    }

                    // Mode switch: Replace in timeline vs Keep original
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (replaceInTimeline) "Replace" else "Add New",
                            color = if (replaceInTimeline) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(4.dp))
                        Switch(
                            checked = replaceInTimeline,
                            onCheckedChange = { replaceInTimeline = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ApexPalette.NeonCyan,
                                checkedTrackColor = ApexPalette.NeonCyan.copy(alpha = 0.4f),
                                uncheckedThumbColor = ApexPalette.TextSecondary,
                                uncheckedTrackColor = ApexPalette.BgElevated
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                // Execute Trim Button
                Button(
                    onClick = {
                        onTrimWithTransformer?.invoke(clip.id, trimStart, trimEnd, replaceInTimeline)
                    },
                    enabled = !isTransformerTrimming,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("media3_trim_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = ApexPalette.BgElevated
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
                            Icon(
                                Icons.Default.ContentCut,
                                contentDescription = null,
                                tint = ApexPalette.BgDeep,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isTransformerTrimming) "Trimming in Progress..." else "Trim Clip with Media3 Transformer",
                                color = ApexPalette.BgDeep,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Secondary Action Buttons Row (Preview Cut & Export Full)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPreviewTrimmed,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
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
                    .weight(1f)
                    .height(42.dp)
                    .testTag("export_project_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApexPalette.BgElevated,
                    contentColor = ApexPalette.NeonCyan
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export Studio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
