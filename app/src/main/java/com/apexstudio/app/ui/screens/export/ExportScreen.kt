package com.apexstudio.app.ui.screens.export

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexstudio.app.domain.model.ExportResolution
import com.apexstudio.app.data.engine.AudioPlaybackMix
import com.apexstudio.app.data.export.ExportEngine
import com.apexstudio.app.data.export.exportTargetBitrate
import com.apexstudio.app.presentation.viewmodel.*
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ExportScreen(
    projectId: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    vm: EditorViewModel = viewModel(
        key = projectId,
        factory = EditorViewModelFactory(projectId = projectId)
    ),
    embedded: Boolean = false
) {
    val export by vm.export.collectAsStateWithLifecycle()
    val editorState by vm.state.collectAsStateWithLifecycle()
    val audioState by vm.audio.collectAsStateWithLifecycle()
    var wholeProject by rememberSaveable(projectId) { mutableStateOf(false) }
    val fxList = vm.fx.collectAsStateWithLifecycle().value
    val transitionsList = vm.transitions.collectAsStateWithLifecycle().value

    // Resolution / FPS / FX are now read from the ViewModel's
    // ExportSettings + activeFxId so the picker state survives
    // screen exit and matches what the export engine bakes in.
    // Previously the resolution and FPS lived in a local
    // `remember { mutableStateOf(...) }` that reset to "4K" / 60
    // every time the user re-opened the Export screen, and the
    // Motion Graphics chips had a hardcoded `selected = fx.id ==
    // "chrom"` (only one card ever highlighted) plus an empty
    // `.clickable { }` that did nothing.
    val storedResolution = export.settings.resolution
    val selectedResolution = ExportResolution.canonical(storedResolution)
    LaunchedEffect(storedResolution) {
        if (storedResolution != selectedResolution) {
            vm.updateExport { it.copy(resolution = selectedResolution) }
        }
    }
    val selectedFps = export.settings.frameRate.toFloat()
    val activeFxId = editorState.activeFxId
    // Pro Phase 1: aspect ratio + bitrate from ExportSettings
    val selectedAspect = export.settings.aspectRatio
    val selectedBitrateMbps = export.settings.bitrateMbps
    // Live file-size estimate: bitrate x duration (was hardcoded "1.8 GB")
    val videoScope = videoExportScope(editorState)
    val selectedScopeBlockReason = selectedVideoExportUnsupportedReason(editorState)
    androidx.activity.compose.BackHandler {
        if (export.isExporting) vm.cancelCurrentExport() else onBack()
    }
    var shareError by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val projectFilterPreset = remember(context, editorState.activeFilterId) {
        val filterId = editorState.activeFilterId
        if (filterId == null) null else runCatching {
            com.apexstudio.app.data.filter.LutFilterEngine(context).manifest.filters
                .firstOrNull { it.id == filterId }
        }.getOrNull()
    }
    val projectBlockReason = wholeProjectExportUnsupportedReason(editorState, audioState, export.settings, projectFilterPreset)
    val projectTimeline = com.apexstudio.app.data.export.ProjectCompositionTimeline.build(projectMainExportClips(editorState))
    val selectedQuality = export.settings.quality.label.lowercase()
    val selectedSettingsBlockReason = videoExportSettingsUnsupportedReason(
        selectedResolution, export.settings.frameRate, selectedQuality, selectedAspect, selectedBitrateMbps
    )
    val selectedBlockReason = selectedScopeBlockReason ?: selectedSettingsBlockReason
    val renderedDurationMs = if (wholeProject) projectTimeline.durationMs else videoScope.durationMs
    val outputBitrateBps = remember(selectedResolution, selectedFps, selectedQuality, selectedBitrateMbps) {
        exportTargetBitrate(ExportEngine.ExportConfig(
            resolution = selectedResolution,
            fps = selectedFps.toInt(),
            quality = selectedQuality,
            bitrateMbps = selectedBitrateMbps
        ))
    }
    val scopeAudioSources = if (wholeProject) projectMainExportClips(editorState) else listOfNotNull(videoScope.baseClip)
    val projectAudioTracks = editorState.project?.audioTracks.orEmpty()
    val hasEnabledMusic = projectAudioTracks.any { AudioPlaybackMix.isEnabled(it, projectAudioTracks) }
    val hasAudibleOverlay = !wholeProject && editorState.project?.clips.orEmpty().any {
        it.type == com.apexstudio.app.domain.model.ClipType.OVERLAY &&
            it.trackIndex !in editorState.hiddenVideoLayers && it.overlayVolume.isFinite() && it.overlayVolume > 0f
    }
    val audioMayBeIncluded =
        (!audioState.isMuted && audioState.volume > 0f && scopeAudioSources.any {
            it.type == com.apexstudio.app.domain.model.ClipType.VIDEO && it.volume > 0f
        }) || hasEnabledMusic || hasAudibleOverlay
    val payloadEstimate = remember(renderedDurationMs, outputBitrateBps, audioMayBeIncluded) {
        estimateExportPayload(renderedDurationMs, outputBitrateBps, audioMayBeIncluded)
    }
    val canRequestVideoExport = export.isExportEngineReady &&
        (if (wholeProject) projectBlockReason == null else videoScope.baseClip != null && selectedBlockReason == null)
    val requestVideoExport: () -> Unit = {
        if (export.isExporting) vm.cancelCurrentExport()
        else if (canRequestVideoExport) {
            if (wholeProject) vm.startWholeProjectExport() else vm.startExport(
                resolution = selectedResolution, fps = selectedFps.toInt(), quality = selectedQuality,
                aspectRatio = selectedAspect, bitrateMbps = selectedBitrateMbps
            )
        }
    }
    // A new URI gets a fresh state immediately; never display the previous file's bytes while reading it.
    var measuredOutputSize by remember(context, export.outputUri) { mutableStateOf<ActualOutputSize?>(null) }
    LaunchedEffect(context, export.outputUri) {
        val uri = export.outputUri ?: return@LaunchedEffect
        measuredOutputSize = withContext(Dispatchers.IO) { measureCompletedOutputSize(context, uri) }
    }


    Column(
        modifier = if (embedded) {
            Modifier.fillMaxWidth().background(ApexPalette.BgBase)
        } else {
            Modifier.fillMaxSize().background(ApexPalette.BgBase).statusBarsPadding().navigationBarsPadding()
        }
    ) {
        if (embedded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(232.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassCard(modifier = Modifier.width(276.dp).fillMaxHeight(), cornerRadius = 14.dp, contentPadding = PaddingValues(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("EXPORT SCOPE", color = ApexPalette.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            androidx.compose.material3.IconButton(onClick = { if (export.isExporting) vm.cancelCurrentExport() else onBack() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close export controls", tint = ApexPalette.TextPrimary)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = !wholeProject, enabled = !export.isExporting, onClick = { wholeProject = false }, label = { Text("Selected clip") })
                            FilterChip(selected = wholeProject, enabled = !export.isExporting, onClick = { wholeProject = true }, label = { Text("Whole project") })
                        }
                        Text(
                            if (wholeProject) "Whole project: main timeline videos, global LUT, color and FX; hard cuts only. Photos, visible PiP, AR and advanced composition are unsupported."
                            else "Selected clip: ${videoScope.baseClip?.let { com.apexstudio.app.util.TimeFormat.formatMs(videoScope.durationMs) } ?: "No exportable video selected"}. Source and edit limits are checked before export.",
                            color = ApexPalette.TextSecondary, fontSize = 10.sp, maxLines = 4
                        )
                        (if (wholeProject) projectBlockReason else selectedBlockReason)?.let {
                            Text(it, color = ApexPalette.Danger, fontSize = 10.sp, maxLines = 3)
                        }
                        export.warning?.let { Text(it, color = ApexPalette.NeonCyan, fontSize = 10.sp, maxLines = 2) }
                        if (wholeProject) {
                            AudioExportWarning("Project scope retains every clip's trim.")
                        } else {
                            VideoExportScopeWarning(videoScope)
                        }
                    }
                }

                GlassCard(modifier = Modifier.width(278.dp).fillMaxHeight(), cornerRadius = 14.dp, contentPadding = PaddingValues(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("VIDEO OUTPUT", color = ApexPalette.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for ((label, sub) in listOf("720P" to "1280×720", "1080P" to "1920×1080", "1440P" to "2560×1440", "4K" to "3840×2160")) {
                                CompactResolutionPill(label, sub, selectedResolution == label, !export.isExporting, { vm.updateExport { it.copy(resolution = label) } }, Modifier.weight(1f))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for ((label, sub) in listOf("16:9" to "Landscape", "9:16" to "Portrait", "1:1" to "Square")) {
                                CompactResolutionPill(label, sub, selectedAspect == label, !export.isExporting, { vm.updateExport { it.copy(aspectRatio = label) } }, Modifier.weight(1f))
                            }
                        }
                        Text("MAX FPS • ${selectedFps.toInt()} fps", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (fps in listOf(24, 30, 60, 90, 120)) {
                                CompactExportChoice("$fps", selectedFps.toInt() == fps, !export.isExporting) { vm.updateExport { it.copy(frameRate = fps) } }
                            }
                        }
                        Text("FPS is an output ceiling; no frame interpolation.", color = ApexPalette.TextTertiary, fontSize = 9.sp, maxLines = 2)
                    }
                }

                GlassCard(modifier = Modifier.width(270.dp).fillMaxHeight(), cornerRadius = 14.dp, contentPadding = PaddingValues(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("ENCODING", color = ApexPalette.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("BITRATE • ${if (selectedBitrateMbps == 0) "Auto" else "$selectedBitrateMbps Mbps"}", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                        for (options in listOf(listOf(0 to "Auto", 8 to "8", 12 to "12"), listOf(16 to "16", 24 to "24", 32 to "32"))) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                for ((mbps, label) in options) {
                                    CompactExportChoice(if (mbps == 0) label else "$label Mbps", selectedBitrateMbps == mbps, !export.isExporting) {
                                        vm.updateExport { it.copy(bitrateMbps = mbps) }
                                    }
                                }
                            }
                        }
                        Text("QUALITY • AUTO BITRATE ONLY", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (quality in com.apexstudio.app.domain.model.ExportQuality.entries) {
                                FilterChip(selected = export.settings.quality == quality, enabled = !export.isExporting && selectedBitrateMbps == 0,
                                    onClick = { vm.updateExport { it.copy(quality = quality) } }, label = { Text(quality.label, fontSize = 10.sp) })
                            }
                        }
                        Text("Fixed bitrate takes precedence over quality.", color = ApexPalette.TextTertiary, fontSize = 9.sp, maxLines = 2)
                        Text("Format: MP4 (H.264). MOV and GIF export are not supported.", color = ApexPalette.TextSecondary, fontSize = 9.sp, maxLines = 2)
                    }
                }

                GlassCard(modifier = Modifier.width(270.dp).fillMaxHeight(), cornerRadius = 14.dp, contentPadding = PaddingValues(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("SIZE & PROGRESS", color = ApexPalette.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GaugeArc(progress = if (export.isExporting || export.outputUri != null) export.progress else 0f, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(exportProgressLabel(export.isExporting, export.progress) ?: "Estimated video payload", color = ApexPalette.TextSecondary, fontSize = 9.sp)
                                Text(if (export.outputUri != null) when (val measured = measuredOutputSize) {
                                    is ActualOutputSize.Measured -> formatByteCount(measured.bytes)
                                    is ActualOutputSize.Unavailable -> measured.reason
                                    null -> "Reading completed file size…"
                                } else payloadEstimate?.let { "≈ ${formatByteCount(it.videoBytes)}" } ?: "Unavailable",
                                    color = ApexPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text("${selectedResolution} • $selectedAspect • ${selectedFps.toInt()} fps • ${String.format(java.util.Locale.US, "%.1f", outputBitrateBps / 1_000_000.0)} Mbps • H.264 / MP4", color = ApexPalette.NeonCyan, fontSize = 9.sp, maxLines = 2)
                        Text("Scope: ${if (wholeProject) "whole project" else "selected clip"} • ${com.apexstudio.app.util.TimeFormat.formatMs(renderedDurationMs)}. Estimate excludes audio/container overhead; completed bytes are measured.", color = ApexPalette.TextSecondary, fontSize = 9.sp, maxLines = 3)
                        export.error?.let { Text("Error: $it", color = ApexPalette.Danger, fontSize = 10.sp, maxLines = 2) }
                    }
                }

                GlassCard(modifier = Modifier.width(260.dp).fillMaxHeight(), cornerRadius = 14.dp, contentPadding = PaddingValues(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("EXPORT & SHARE", color = ApexPalette.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            androidx.compose.material3.Button(
                                enabled = export.isExporting || canRequestVideoExport,
                                onClick = requestVideoExport,
                                modifier = Modifier.weight(1f).heightIn(min = 42.dp)
                            ) {
                                Icon(if (export.isExporting) Icons.Default.Close else Icons.Default.IosShare, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                val exportActionLabel = when {
                                    export.isExporting -> "Cancel • ${(export.progress * 100).toInt()}%"
                                    !export.isExportEngineReady -> "Engine unavailable"
                                    !wholeProject && videoScope.baseClip == null -> "Select a clip"
                                    wholeProject && projectBlockReason != null -> "Project export blocked"
                                    !wholeProject && selectedBlockReason != null -> "Clip export blocked"
                                    else -> "Export ${if (wholeProject) "project" else "clip"} • ${selectedResolution}"
                                }
                                Text(exportActionLabel, maxLines = 1, fontSize = 11.sp)
                            }
                            androidx.compose.material3.IconButton(
                                enabled = !export.isExporting && export.isExportEngineReady,
                                onClick = { vm.startAudioExport() }
                            ) {
                                Icon(Icons.Default.AudioFile, contentDescription = "Export audio as AAC")
                            }
                        }
                        if (export.outputUri != null) {
                            OutlinedButton(enabled = !export.isExporting, onClick = {
                                shareError = runCatching { shareCompletedExport(context, requireNotNull(export.outputUri)) }
                                    .exceptionOrNull()?.let { it.message ?: "No sharing app is available. The export remains saved." }
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text("Share completed export", maxLines = 1)
                            }
                        } else {
                            Text("On-device video export • H.264 / MP4", color = ApexPalette.TextSecondary, fontSize = 9.sp, maxLines = 1)
                        }
                        shareError?.let { Text(it, color = ApexPalette.Danger, fontSize = 10.sp, maxLines = 2) }
                    }
                }
            }
        } else {
        AppTopBar(
            title = "Export",
            subtitle = "ApexStudio • Export Settings",
            onBack = { if (export.isExporting) vm.cancelCurrentExport() else onBack() },
            onExport = if (!export.isExporting && canRequestVideoExport) requestVideoExport else null
        )
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp, vertical = 2.dp),
            cornerRadius = 16.dp,
            backgroundColor = ApexPalette.BgGlassSoft,
            borderColor = ApexPalette.NeonPurple.copy(alpha = 0.35f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ApexPalette.NeonPurple, ApexPalette.NeonCyan))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.IosShare, contentDescription = null, tint = ApexPalette.BgDeep)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("EXPORT YOUR STORY", color = ApexPalette.NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${if (wholeProject) "Whole project" else "Selected clip"} • $selectedResolution • $selectedAspect • MP4",
                        color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AudioExportWarning(export.warning)
            SectionLabel("VIDEO SCOPE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !wholeProject, enabled = !export.isExporting,
                    onClick = { wholeProject = false }, label = { Text("Selected clip") })
                FilterChip(selected = wholeProject, enabled = !export.isExporting,
                    onClick = { wholeProject = true }, label = { Text("Whole project") })
            }
            if (wholeProject) {
                AudioExportWarning(projectBlockReason ?: "Whole-project export: main videos in timeline order with global LUT filters, color adjustments and FX. Cuts are hard cuts; photos, visible PiP layers, AR and advanced composition are not supported by this route.")
            } else {
                VideoExportScopeWarning(videoScope)
                AudioExportWarning(selectedBlockReason)
            }
            SectionLabel("MOTION GRAPHICS")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(fxList) { fx ->
                    MotionGraphicsCard(
                        label = fx.label,
                        icon = fx.icon,
                        selected = fx.id == activeFxId,
                        enabled = !export.isExporting,
                        onClick = {
                            // Tapping the same FX again clears it.
                            vm.setActiveFx(if (fx.id == activeFxId) null else fx.id)
                        },
                        modifier = Modifier.width(88.dp)
                    )
                }
            }

            // Do not present decorative transition cards as working export actions.
            Text("Transitions are edited on the timeline. Whole-project export currently supports hard cuts only.",
                color = ApexPalette.TextSecondary, fontSize = 10.sp)

            SectionLabel("RESOLUTION")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for ((label, sub) in listOf(
                            "720P" to "1280×720",
                            "1080P" to "1920×1080",
                            "1440P" to "2560×1440",
                            "4K" to "3840×2160"
                        )) {
                            ResolutionPill(
                                label = label,
                                sub = sub,
                                selected = selectedResolution == label,
                                enabled = !export.isExporting,
                                onClick = {
                                    vm.updateExport { it.copy(resolution = label) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Pro Phase 1: aspect ratio selector (16:9 / 9:16 / 1:1)
            SectionLabel("ASPECT RATIO")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((label, sub) in listOf(
                        "16:9" to "Landscape",
                        "9:16" to "Portrait",
                        "1:1" to "Square"
                    )) {
                        ResolutionPill(
                            label = label,
                            sub = sub,
                            selected = selectedAspect == label,
                            enabled = !export.isExporting,
                            onClick = {
                                vm.updateExport { it.copy(aspectRatio = label) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text("FPS is an output ceiling: higher-rate source frames can be dropped; lower-rate sources are not interpolated.", color = ApexPalette.TextSecondary, fontSize = 10.sp)
            SectionLabel("MAXIMUM OUTPUT FPS")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "30",
                            color = ApexPalette.TextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = selectedFps,
                            enabled = !export.isExporting,
                            onValueChange = { v ->
                                vm.updateExport { it.copy(frameRate = v.toInt()) }
                            },
                            valueRange = 30f..120f,
                            steps = 2,
                            colors = SliderDefaults.colors(
                                thumbColor = ApexPalette.NeonCyan,
                                activeTrackColor = ApexPalette.NeonCyan,
                                inactiveTrackColor = ApexPalette.BgElevated
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            "120",
                            color = ApexPalette.TextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (f in listOf(24f, 30f, 60f, 90f, 120f)) {
                            val sel = selectedFps == f
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (sel) ApexPalette.NeonCyan.copy(alpha = 0.18f)
                                        else ApexPalette.BgElevated
                                    )
                                    .border(
                                        if (sel) 1.dp else 0.dp,
                                        ApexPalette.NeonCyan,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable(enabled = !export.isExporting) {
                                        vm.updateExport { it.copy(frameRate = f.toInt()) }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${f.toInt()} fps",
                                    color = if (sel) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Pro Phase 1: bitrate control (Auto + presets, Mbps)
            SectionLabel("BITRATE")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val bitrateOptions = listOf(0 to "Auto", 8 to "8", 12 to "12", 16 to "16", 24 to "24", 32 to "32")
                    for ((mbps, label) in bitrateOptions) {
                        val sel = selectedBitrateMbps == mbps
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (sel) ApexPalette.NeonCyan.copy(alpha = 0.15f)
                                    else ApexPalette.BgElevated
                                )
                                .border(
                                    if (sel) 1.5.dp else 1.dp,
                                    if (sel) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = !export.isExporting) {
                                    vm.updateExport { it.copy(bitrateMbps = mbps) }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    label,
                                    color = if (sel) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    if (mbps == 0) "auto" else "Mbps",
                                    color = if (sel) ApexPalette.NeonCyan else ApexPalette.TextTertiary,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }
            }

            SectionLabel("QUALITY • AUTO BITRATE ONLY")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (quality in com.apexstudio.app.domain.model.ExportQuality.entries) {
                    FilterChip(
                        selected = export.settings.quality == quality,
                        enabled = !export.isExporting && selectedBitrateMbps == 0,
                        onClick = { vm.updateExport { it.copy(quality = quality) } },
                        label = { Text(quality.label) }
                    )
                }
            }
            if (selectedBitrateMbps != 0) {
                Text(
                    "Quality is disabled while a fixed bitrate is selected; the explicit bitrate takes precedence.",
                    color = ApexPalette.TextSecondary,
                    fontSize = 10.sp
                )
            }

            SectionLabel("SIZE ESTIMATE")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The gauge now drives off the live export
                        // progress (was hardcoded 0.78f so it never
                        // changed once the export started). When
                        // export.progress == 0f and isExporting is
                        // false we leave the gauge blank rather than
                        // showing a misleading "0%" before the user
                        // taps the button.
                        GaugeArc(
                            progress = if (export.isExporting || export.outputUri != null) export.progress else 0f,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                exportProgressLabel(export.isExporting, export.progress)
                                    ?: if (export.outputUri != null && export.progress >= 1f) "Actual last-export file size"
                                    else "Estimated video payload (not total file size)",
                                color = ApexPalette.TextTertiary,
                                fontSize = 9.sp
                            )
                            val sizeDescription = when {
                                export.outputUri != null -> when (val measured = measuredOutputSize) {
                                    is ActualOutputSize.Measured -> "${formatByteCount(measured.bytes)} (${measured.bytes} bytes)"
                                    is ActualOutputSize.Unavailable -> measured.reason
                                    null -> "Reading completed file size…"
                                }
                                payloadEstimate != null -> "≈ ${formatByteCount(payloadEstimate.videoBytes)}"
                                else -> "Unavailable — rendered duration or bitrate is invalid"
                            }
                            Text(
                                sizeDescription,
                                color = ApexPalette.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "Requested ${String.format(java.util.Locale.US, "%.1f", outputBitrateBps / 1_000_000.0)} Mbps • ${com.apexstudio.app.util.TimeFormat.formatMs(renderedDurationMs)} • $selectedAspect • H.264 / MP4",
                                color = ApexPalette.NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (export.outputUri == null) {
                        Text(
                            if (audioMayBeIncluded)
                                "Video-payload estimate only for this scope (${if (wholeProject) "whole project" else "selected clip"}). Audio may be included but Media3 does not configure its AAC bitrate; audio and container overhead are excluded. Actual file bytes are measured after export."
                            else
                                "Video-payload estimate only for this scope (${if (wholeProject) "whole project" else "selected clip"}); no configured audio mix is expected. Container overhead is excluded and encoded size can vary. Actual file bytes are measured after export.",
                            color = ApexPalette.TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Pro Phase 1: no-watermark differentiator badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ApexPalette.NeonCyan.copy(alpha = 0.1f))
                            .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = ApexPalette.NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (editorState.project?.signature != null)
                                "Project signature • 100% on-device"
                            else
                                "No watermark • 100% on-device",
                            color = ApexPalette.NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val selectedClip = videoScope.baseClip
            val trimStart = selectedClip?.trimStartMs ?: 0L
            val trimEnd = selectedClip?.trimEndMs ?: selectedClip?.durationMs ?: 0L
            val clipDuration = selectedClip?.durationMs ?: 0L
            val isTrimmed = trimStart > 0L || (trimEnd < clipDuration && trimEnd > 0L)
            val trimmedDuration = (trimEnd - trimStart).coerceAtLeast(0L)

            SectionLabel(if (wholeProject) "SELECTED CLIP TRIM • PROJECT KEEPS EVERY CLIP'S TRIM" else "MEDIA3 TRANSFORMER TRIMMING")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isTrimmed) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                                else ApexPalette.BgElevated
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ContentCut,
                            contentDescription = null,
                            tint = if (isTrimmed) ApexPalette.NeonCyan else ApexPalette.TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isTrimmed) "Trim Points Active" else "Full Video Clip",
                                color = if (isTrimmed) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isTrimmed) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ApexPalette.NeonCyan)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("TRIMMED", color = ApexPalette.BgDeep, fontSize = 7.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (isTrimmed)
                                "Range: ${com.apexstudio.app.util.TimeFormat.formatMs(trimStart)} ➔ ${com.apexstudio.app.util.TimeFormat.formatMs(trimEnd)} (Length: ${com.apexstudio.app.util.TimeFormat.formatMs(trimmedDuration)})"
                            else "Full selected clip (${com.apexstudio.app.util.TimeFormat.formatMs(clipDuration)})",
                            color = ApexPalette.TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            if (export.outputUri != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 14.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ApexPalette.NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (export.warning == null) "Export complete"
                                else "Export complete with warning",
                                color = ApexPalette.NeonCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            "Completed file saved on this device:",
                            color = ApexPalette.TextSecondary,
                            fontSize = 10.sp
                        )
                        OutlinedButton(enabled = !export.isExporting, onClick = {
                            shareError = runCatching {
                                shareCompletedExport(context, requireNotNull(export.outputUri))
                            }.exceptionOrNull()?.let {
                                it.message ?: "No sharing app is available. The export remains saved."
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share / Next")
                        }
                        Text("Shares the last completed export. Re-export after changing edits or settings. Nothing is published automatically.",
                            color = ApexPalette.TextSecondary, fontSize = 10.sp)
                        shareError?.let { Text(it, color = ApexPalette.Danger, fontSize = 11.sp) }
                    }
                }
            }

            if (export.error != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 14.dp
                ) {
                    Text(
                        "Error: ${export.error}",
                        color = ApexPalette.Danger,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple)
                    )
                )
                .clickable(enabled = export.isExporting || canRequestVideoExport, onClick = requestVideoExport),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (export.isExporting) Icons.Default.Close
                    else Icons.Default.IosShare,
                    null,
                    tint = ApexPalette.BgDeep,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (export.isExporting)
                        if (export.progress <= 0f) "Cancel • Preparing export…"
                        else "Cancel • Exporting… ${(export.progress * 100).toInt()}%"
                    else if (!export.isExportEngineReady) "Export engine unavailable"
                    else if (!wholeProject && videoScope.baseClip == null) "Select a project clip to export"
                    else if (wholeProject && projectBlockReason != null) "Whole-project export unavailable for these edits"
                    else if (!wholeProject && selectedBlockReason != null && videoScope.baseClip != null) "Selected-clip export unavailable for these edits"
                    else "Export ${if (wholeProject) "project • " else ""}$selectedResolution • $selectedAspect @ ${selectedFps.toInt()}fps",
                    color = ApexPalette.BgDeep,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        }

        // Standalone audio export: AAC (.m4a) from the project's audio.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A2233))
                .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable(enabled = !export.isExporting && export.isExportEngineReady) {
                    vm.startAudioExport()
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AudioFile,
                    null,
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (export.isExportEngineReady) "Export Audio (AAC)" else "Export engine unavailable",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
        Text(
            "Exports readable audio from V1, visible PiP, and enabled music tracks as AAC (.m4a). No size estimate: the AAC bitrate is not explicitly configured; completed file bytes are measured above.",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            color = ApexPalette.TextSecondary,
            fontSize = 10.sp
        )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = ApexPalette.TextTertiary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun CompactExportChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.16f) else ApexPalette.BgElevated)
            .border(1.dp, if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun CompactResolutionPill(
    label: String,
    sub: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.15f) else ApexPalette.BgElevated)
            .border(1.dp, if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(sub, color = ApexPalette.TextTertiary, fontSize = 6.sp, maxLines = 1)
    }
}

@Composable
private fun MotionGraphicsCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (selected) ApexPalette.NeonCyan else Color.Transparent
    Box(
        modifier = modifier
            .height(74.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A0F2E), Color(0xFF0F1B2D))
                )
            )
            .border(1.5.dp, border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(ApexPalette.NeonPurple.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(32.dp)
                .clip(CircleShape)
                .background(ApexPalette.NeonCyan.copy(alpha = 0.18f))
                .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                tint = if (selected) ApexPalette.NeonCyan else Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            label,
            color = if (selected) ApexPalette.NeonCyan else Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
        )
    }
}

@Composable
private fun TransitionCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A2440), Color(0xFF0F1B33))
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, null,
            tint = ApexPalette.NeonCyan,
            modifier = Modifier.size(20.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(ApexPalette.BgDeep.copy(alpha = 0.7f))
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = ApexPalette.TextPrimary, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ResolutionPill(
    label: String,
    sub: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) ApexPalette.NeonCyan.copy(alpha = 0.15f)
                else ApexPalette.BgElevated
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                sub,
                color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextTertiary,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun GaugeArc(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = ApexPalette.BgElevated,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple, ApexPalette.NeonCyan)
                ),
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            "${(progress * 100).toInt()}%",
            color = ApexPalette.NeonCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
