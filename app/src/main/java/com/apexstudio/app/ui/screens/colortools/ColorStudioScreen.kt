package com.apexstudio.app.ui.screens.colortools

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexstudio.app.data.color.ColorStudioGrade
import com.apexstudio.app.data.color.ColorStudioLut
import com.apexstudio.app.data.color.ColorWheelVector
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.EditorViewModelFactory
import com.apexstudio.app.presentation.viewmodel.colorStudioTimelineExportBlocker
import com.apexstudio.app.presentation.viewmodel.startColorStudioTimelineExport
import com.apexstudio.app.presentation.viewmodel.captureColorTimelineExportSnapshot
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

private data class SourceFrame(val bitmap: Bitmap, val timeMs: Long)

internal fun colorPreviewSplitPosition(widthPx: Float, split: Float): Float =
    widthPx.coerceAtLeast(0f) * split.coerceIn(0f, 1f)

private val ColorStudioGradeSaver = Saver<MutableState<ColorStudioGrade>, List<Float>>(
    save = { state -> val grade = state.value; listOf(grade.brightness, grade.contrast, grade.saturation,
        grade.shadows.x, grade.shadows.y, grade.midtones.x, grade.midtones.y,
        grade.highlights.x, grade.highlights.y) + grade.rCurve + grade.gCurve + grade.bCurve + grade.lumaCurve },
    restore = { values ->
        mutableStateOf(if (values.size != 29) ColorStudioGrade() else runCatching {
            ColorStudioGrade(values[0], values[1], values[2],
                ColorWheelVector(values[3], values[4]), ColorWheelVector(values[5], values[6]),
                ColorWheelVector(values[7], values[8]), values.subList(9, 14),
                values.subList(14, 19), values.subList(19, 24), values.subList(24, 29))
        }.getOrDefault(ColorStudioGrade()))
    }
)

@Composable
fun ColorStudioScreen(
    projectId: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    embedded: Boolean = false,
    modifier: Modifier = Modifier,
    vm: EditorViewModel = viewModel(key = projectId, factory = EditorViewModelFactory(projectId = projectId)),
    exportRequest: Int = 0
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val exportUi by vm.export.collectAsStateWithLifecycle()
    val audioState by vm.audio.collectAsStateWithLifecycle()
    var timelineComparison by rememberSaveable(projectId) { mutableStateOf(true) }
    var comparisonBusy by remember { mutableStateOf(false) }
    val candidates = remember(state.project?.clips) { ColorMediaIO.selectedSources(state.project?.clips.orEmpty()) }
    val initiallySelected = candidates.firstOrNull { it.id == state.selectedClipId } ?: candidates.firstOrNull()
    var chosenClipId by rememberSaveable(projectId) { mutableStateOf(initiallySelected?.id) }
    val clip = candidates.firstOrNull { it.id == chosenClipId } ?: initiallySelected.also { chosenClipId = it?.id }
    var frameTime by rememberSaveable(clip?.id) { mutableLongStateOf(clip?.let { ColorMediaIO.previewTimeMs(it, it.trimStartMs) } ?: 0L) }
    var split by rememberSaveable { mutableFloatStateOf(.5f) }
    var grade by rememberSaveable(projectId, clip?.id, saver = ColorStudioGradeSaver) { mutableStateOf(ColorStudioGrade()) }
    var curveChannel by remember(clip?.id) { mutableStateOf("R") }
    var curvePoint by remember(clip?.id) { mutableIntStateOf(2) }
    var exportDialog by remember { mutableStateOf(false) }
    var exportBusy by remember { mutableStateOf(false) }
    var timelinePreflight by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var completedOutput by remember { mutableStateOf<File?>(null) }
    var completedOutputClipId by remember { mutableStateOf<String?>(null) }
    var completedOutputGrade by remember { mutableStateOf<ColorStudioGrade?>(null) }
    var requestedTimelineGrade by remember { mutableStateOf<ColorStudioGrade?>(null) }
    var timelineOutputBaseline by remember { mutableStateOf<String?>(null) }
    val timelineSnapshot = remember(state, audioState, exportUi.settings, exportUi.isExporting, grade) {
        runCatching { vm.captureColorTimelineExportSnapshot(grade) }
    }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(exportRequest) {
        if (exportRequest > 0) { exportMessage = null; exportDialog = true }
    }
    val currentExportJob by rememberUpdatedState(exportJob)
    if (embedded) BackHandler { if (exportBusy) currentExportJob?.cancel() else onBack() }

    val sourceUri = clip?.let(ColorMediaIO::sourceUri)
    var processingError by remember(sourceUri, frameTime, grade) { mutableStateOf<String?>(null) }
    val frameResult by produceState<Result<SourceFrame>?>(null, sourceUri, frameTime, clip?.type, timelineComparison) {
        value = null
        if (!timelineComparison && clip != null && sourceUri != null) {
            delay(90)
            value = try {
                Result.success(withContext(Dispatchers.IO) {
                    val decoded = ColorMediaIO.decodePreview(context, clip, frameTime)
                    SourceFrame(decoded.bitmap, decoded.sourceTimeMs)
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
        }
    }
    val sourceFrame = frameResult?.getOrNull()
    var gradedFrame by remember(sourceFrame, grade) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(sourceFrame, grade) {
        gradedFrame = null
        processingError = null
        val source = sourceFrame?.bitmap ?: return@LaunchedEffect
        try {
            val made = withContext(Dispatchers.Default) { ColorStudioLut(grade).process(source) }
            currentCoroutineContext().ensureActive()
            // Displayed immutable bitmap is GC-owned; a cancelled/in-flight reader may still hold it.
            gradedFrame = made
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            processingError = failure.message ?: "Color preview processing failed. Adjust or reset the grade and try again."
        }
    }
    DisposableEffect(Unit) { onDispose { currentExportJob?.cancel() } }

    Column(modifier.fillMaxSize().background(ApexPalette.BgBase)
        .then(if (embedded) Modifier else Modifier.statusBarsPadding().navigationBarsPadding())) {
        if (embedded) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Color", color = ApexPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { grade = ColorStudioGrade(); processingError = null }) { Text("Reset") }
                TextButton(onClick = onBack) { Text("Close") }
                TextButton(onClick = { exportMessage = null; exportDialog = true }) { Text("Export") }
            }
        } else AppTopBar(title = "Color", subtitle = "ApexStudio • Color Grading", onBack = onBack,
            onExport = { exportMessage = null; exportDialog = true })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = if (embedded) 6.dp else 12.dp, vertical = if (embedded) 4.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (embedded) 5.dp else 8.dp)
        ) {
            if (candidates.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No project video or image", color = ApexPalette.TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Add or select existing media in the project editor. Color does not create placeholder media.",
                            color = ApexPalette.TextSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !comparisonBusy, onClick = { timelineComparison = true }) {
                        Text("Project timeline", color = if (timelineComparison) ApexPalette.NeonCyan else ApexPalette.TextSecondary)
                    }
                    TextButton(enabled = !comparisonBusy, onClick = { timelineComparison = false }) {
                        Text("Selected source", color = if (!timelineComparison) ApexPalette.NeonCyan else ApexPalette.TextSecondary)
                    }
                }
                if (timelineComparison) {
                    ColorTimelineComparisonPanel(timelineSnapshot.getOrNull(), timelineSnapshot.exceptionOrNull()?.message,
                        split, { split = it.coerceIn(0f, 1f) }, embedded, { comparisonBusy = it },
                        previewModifier = Modifier.fillMaxWidth(),
                        onCompareSelectedSource = { timelineComparison = false })
                } else {
                BeforeAfterPreview(sourceFrame?.bitmap, gradedFrame, split, { split = it.coerceIn(0f, 1f) },
                    if (embedded) 1.36f else 16f / 9f, Modifier.fillMaxWidth(),
                    loading = frameResult == null, error = processingError ?: frameResult?.exceptionOrNull()?.message)
                Text("This Before/After is the selected source frame only, not a full-timeline preview.",
                    color = ApexPalette.TextTertiary, fontSize = 9.sp)
                }
                LazyRow(Modifier.fillMaxWidth().height(if (embedded) 154.dp else 170.dp), horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (!timelineComparison) item {
                        Column(Modifier.width(246.dp).height(138.dp).clip(RoundedCornerShape(14.dp))
                            .background(ApexPalette.BgSurface).border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp))
                            .padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("SOURCE • ${if (clip?.type == ClipType.IMAGE) "STILL" else "PAUSED VIDEO FRAME"}",
                                color = ApexPalette.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(clip?.name.orEmpty(), color = ApexPalette.TextPrimary, fontSize = 10.sp, maxLines = 1)
                            if (clip?.type == ClipType.VIDEO) {
                                val min = clip.trimStartMs.coerceAtLeast(0L)
                                val max = (clip.trimEndMs.takeIf { it > min } ?: clip.durationMs).coerceAtLeast(min + 1L)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(sourceFrame?.let { timecode(it.timeMs) } ?: "—", color = ApexPalette.TextSecondary, fontSize = 9.sp)
                                    Slider(value = frameTime.coerceIn(min, max).toFloat(), onValueChange = { frameTime = it.toLong() },
                                        valueRange = min.toFloat()..max.toFloat(), colors = sliderColors(ApexPalette.NeonCyan), modifier = Modifier.weight(1f).height(28.dp))
                                }
                            }
                            if (candidates.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                candidates.forEach { candidate ->
                                    val active = candidate.id == clip?.id
                                    val icon = if (candidate.type == ClipType.IMAGE) Icons.Default.Image else Icons.Default.VideoLibrary
                                    Row(Modifier.clip(RoundedCornerShape(7.dp))
                                        .background(if (active) ApexPalette.NeonCyan.copy(.15f) else ApexPalette.BgElevated)
                                        .clickable { chosenClipId = candidate.id; frameTime = ColorMediaIO.previewTimeMs(candidate, candidate.trimStartMs); grade = ColorStudioGrade(); completedOutput = null; completedOutputClipId = null; completedOutputGrade = null }
                                        .padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.Icon(icon, null, tint = if (active) ApexPalette.NeonCyan else ApexPalette.TextSecondary, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(candidate.name.ifBlank { if (candidate.type == ClipType.IMAGE) "Image" else "Video" },
                                            color = if (active) ApexPalette.NeonCyan else ApexPalette.TextSecondary, maxLines = 1, fontSize = 9.sp)
                                    }
                                }
                            } else Text("Original unchanged • standalone grade", color = ApexPalette.TextTertiary, fontSize = 9.sp)
                            Text("Whole-source export; trims/composition excluded.", color = ApexPalette.TextTertiary, fontSize = 8.sp, maxLines = 1)
                        }
                    }
                    item { WheelControl("SHADOWS", grade.shadows, ApexPalette.NeonCyan, { grade = grade.copy(shadows = it) }, Modifier.width(104.dp)) }
                    item { WheelControl("MIDTONES", grade.midtones, ApexPalette.NeonPurple, { grade = grade.copy(midtones = it) }, Modifier.width(104.dp)) }
                    item { WheelControl("HIGHLIGHTS", grade.highlights, ApexPalette.NeonPink, { grade = grade.copy(highlights = it) }, Modifier.width(104.dp)) }
                    item {
                        Column(Modifier.width(250.dp).clip(RoundedCornerShape(14.dp)).background(ApexPalette.BgSurface)
                            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp)).padding(7.dp)) {
                            Text("RGBL CURVES", color = ApexPalette.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            RgbCurvesCanvas(grade, curveChannel, curvePoint, { curveChannel = it }, { index, value ->
                                curvePoint = index
                                grade = grade.withCurve(curveChannel) { points -> points.toMutableList().also { it[index] = value } }
                            }, Modifier.fillMaxWidth().height(126.dp))
                        }
                    }
                    item {
                        Column(Modifier.width(230.dp).clip(RoundedCornerShape(14.dp)).background(ApexPalette.BgSurface)
                            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp)).padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("LIGHT & COLOR", color = ApexPalette.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            AdjustmentSlider("Brightness", grade.brightness, -1f..1f, ApexPalette.NeonCyan, { grade = grade.copy(brightness = it) })
                            AdjustmentSlider("Contrast", grade.contrast, 0f..2f, ApexPalette.NeonPurple, { grade = grade.copy(contrast = it) })
                            AdjustmentSlider("Saturation", grade.saturation, 0f..2f, ApexPalette.NeonPink, { grade = grade.copy(saturation = it) })
                        }
                    }
                }
            }
        }
    }

    if (exportDialog) {
        val timelineBlocker = vm.colorStudioTimelineExportBlocker(grade)
        AlertDialog(onDismissRequest = { if (exportBusy) exportJob?.cancel() else exportDialog = false },
            title = { Text("Export Color result") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when {
                        exportBusy && timelinePreflight -> "Verifying timeline sources before export…"
                        exportBusy -> "Processing the selected source…"
                        clip?.type == ClipType.IMAGE -> "Export one graded still image only. This standalone source export does not include the project timeline/composition. Images above 12 MP or 5000 px per side are refused."
                        clip?.type == ClipType.VIDEO -> "Export the entire selected source video as a separate MP4 with the same trilinear LUT. This does not apply project trims, speed changes, effects, overlays or composition. SDR 8-bit video up to 4K and 30 minutes only; HDR, unknown color transfer or unverified pixel formats are refused."
                        else -> "Select an existing project image or video first."
                    })
                    Text("Timeline grading renders the supported project composition to a separate MP4. In Project timeline mode, Render Before / After compares two complete rendered snapshots; Share compared MP4 sends that exact After file. Selected source mode compares only one source frame. Originals and saved project edits remain unchanged.",
                        color = ApexPalette.TextSecondary, fontSize = 11.sp)
                    if (timelineBlocker != null) {
                        Text("Timeline export unavailable: $timelineBlocker", color = ApexPalette.NeonPink, fontSize = 11.sp)
                    }
                    exportMessage?.let { Text(it, color = ApexPalette.NeonCyan, fontSize = 12.sp) }
                    if (exportUi.isExporting) {
                        CircularProgressIndicator(progress = { exportUi.progress.coerceIn(0f, 1f) })
                        Text("Rendering graded timeline: ${(exportUi.progress * 100).toInt()}%",
                            color = ApexPalette.NeonCyan, fontSize = 11.sp)
                    }
                    exportUi.error?.let { Text(it, color = ApexPalette.NeonPink, fontSize = 11.sp) }
                    if (requestedTimelineGrade != null && exportUi.outputUri != null &&
                        exportUi.outputUri != timelineOutputBaseline && !exportUi.isExporting && exportUi.error == null) {
                        Text("The requested timeline grade snapshot is exported. Open Export to review or share it. Later control changes do not modify that file.",
                            color = ApexPalette.NeonCyan, fontSize = 11.sp)
                    }
                    TextButton(
                        enabled = timelineBlocker == null && !exportBusy && !exportUi.isExporting && !comparisonBusy,
                        onClick = {
                            val exportGrade = grade
                            val captured = timelineSnapshot.getOrNull() ?: return@TextButton
                            val timelineVideos = captured.clips
                            timelinePreflight = true
                            exportBusy = true
                            exportJob = scope.launch {
                                try {
                                    for (timelineClip in timelineVideos) {
                                        val info = ColorVideoExporter.inspect(context, Uri.parse(timelineClip.uri))
                                        check(info.supported) {
                                            "Timeline Color export blocked for '${timelineClip.name}': " +
                                                (info.reason ?: "source format is not verified SDR")
                                        }
                                    }
                                    exportBusy = false
                                    timelinePreflight = false
                                    timelineOutputBaseline = exportUi.outputUri
                                    requestedTimelineGrade = exportGrade
                                    vm.startColorStudioTimelineExport(captured)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Throwable) {
                                    exportMessage = failure.message ?: "Timeline Color export was blocked; project media is unchanged."
                                } finally {
                                    exportBusy = false
                                    timelinePreflight = false
                                }
                            }
                        }
                    ) { Text(if (exportUi.isExporting) "Rendering timeline…" else "Export timeline with grade") }
                    if (exportUi.isExporting) TextButton(onClick = { exportDialog = false }) { Text("Continue in background") }
                }
            },
            confirmButton = {
                val selected = clip
                val reusableOutput = completedOutput?.takeIf { it.isFile && it.length() > 0L &&
                    completedOutputClipId == selected?.id && completedOutputGrade == grade }
                if (!exportBusy && !comparisonBusy && !exportUi.isExporting && selected != null && selected.type in setOf(ClipType.IMAGE, ClipType.VIDEO) &&
                    (reusableOutput != null || (sourceFrame != null && gradedFrame != null))) TextButton(onClick = {
                    val exportClip = selected
                    val exportGrade = grade // immutable snapshot: later UI edits cannot alter this export
                    exportBusy = true
                    exportJob = scope.launch {
                        var completedForShare: File? = null
                        try {
                            val output = reusableOutput ?: withContext(Dispatchers.IO) {
                                val source = Uri.parse(ColorMediaIO.sourceUri(exportClip))
                                if (exportClip.type == ClipType.VIDEO) ColorVideoExporter.export(context, source, exportGrade)
                                else {
                                    val (bitmap, _) = ColorMediaIO.decodeImage(context, source,
                                        ColorMediaIO.PREVIEW_MAX_DIMENSION, export = true)
                                    try {
                                        val graded = withContext(Dispatchers.Default) { ColorStudioLut(exportGrade).process(bitmap) }
                                        try {
                                            val file = File.createTempFile("color_", ".png", ColorMediaIO.exportDirectory(context))
                                            try {
                                                file.outputStream().buffered().use { stream ->
                                                    check(graded.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Image encoding failed." }
                                                }
                                                check(file.isFile && file.length() > 0L) { "The image export was not completed." }
                                                file
                                            } catch (failure: Throwable) { file.delete(); throw failure }
                                        } finally { graded.recycle() }
                                    } finally { bitmap.recycle() }
                                }
                            }
                            completedForShare = output
                            if (reusableOutput == null) {
                                completedOutput = output
                                completedOutputClipId = exportClip.id
                                completedOutputGrade = exportGrade
                            }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
                            val mime = if (exportClip.type == ClipType.VIDEO) "video/mp4" else "image/png"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = mime
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, output.name, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share standalone Color export"))
                            exportMessage = if (exportClip.type == ClipType.VIDEO) {
                                "Saved a separate graded MP4 of the full source video. Project edits/composition and the original are unchanged."
                            } else "Saved a separate graded still to the app's Color exports. Original media is untouched."
                        } catch (cancelled: CancellationException) {
                            // A completed output remains available even if launching the share sheet is cancelled.
                            throw cancelled
                        } catch (failure: Throwable) {
                            // If encoding completed, keep the file and let the user retry sharing it.
                            if (completedForShare == null) exportMessage = failure.message ?: "Color export failed. The original was not changed."
                            else exportMessage = "Export is saved, but sharing failed: ${failure.message ?: "try sharing again"}"
                        } finally { exportBusy = false }
                    }
                }) { Text(if (reusableOutput != null) "Share again" else if (selected.type == ClipType.VIDEO) "Export video" else "Export still") }
            },
            dismissButton = {
                TextButton(onClick = { if (exportBusy) exportJob?.cancel() else exportDialog = false }) {
                    Text(if (exportBusy) "Cancel export" else "Close")
                }
            })
    }
}

private fun timecode(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000L)
    return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
}

private fun ColorStudioGrade.withCurve(channel: String, transform: (List<Float>) -> List<Float>) = when (channel) {
    "G" -> copy(gCurve = transform(gCurve))
    "B" -> copy(bCurve = transform(bCurve))
    "L" -> copy(lumaCurve = transform(lumaCurve))
    else -> copy(rCurve = transform(rCurve))
}

@Composable
internal fun BeforeAfterPreview(before: Bitmap?, after: Bitmap?, split: Float, onSplit: (Float) -> Unit,
    ratio: Float, modifier: Modifier = Modifier, loading: Boolean, error: String?) {
    val currentSplit by rememberUpdatedState(split)
    val currentOnSplit by rememberUpdatedState(onSplit)
    BoxWithConstraints(modifier.aspectRatio(ratio).clip(RoundedCornerShape(14.dp)).background(ApexPalette.BgSurface)
        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp))) {
        if (before != null) Image(before.asImageBitmap(), null, Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error != null) Text(error, color = ApexPalette.NeonPink, fontSize = 11.sp, modifier = Modifier.padding(16.dp))
            else if (loading) CircularProgressIndicator(Modifier.size(26.dp), color = ApexPalette.NeonCyan, strokeWidth = 2.dp)
            else Text("Source frame unavailable", color = ApexPalette.TextTertiary, fontSize = 10.sp)
        }
        if (after != null) {
            Box(Modifier.fillMaxSize().drawWithContent {
                clipRect(left = colorPreviewSplitPosition(size.width, split)) {
                    this@drawWithContent.drawContent()
                }
            }) {
                Image(after.asImageBitmap(), null, Modifier.fillMaxSize())
            }
        }
        Text("BEFORE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(4.dp)).background(ApexPalette.BgGlass).padding(6.dp, 2.dp))
        Text("AFTER", color = ApexPalette.NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(4.dp)).background(ApexPalette.BgGlass).padding(6.dp, 2.dp))
        // A drag handle over an error/empty state obscures the reason and
        // falsely suggests that there are two rendered images to compare.
        if (before != null && after != null && error == null) {
        Box(Modifier.fillMaxHeight().width(2.dp).align(Alignment.CenterStart)
            .offset(x = maxWidth * split.coerceIn(0f, 1f)).background(ApexPalette.NeonCyan))
        Box(Modifier.align(Alignment.CenterStart).offset(x = maxWidth * split.coerceIn(0f, 1f) - 18.dp)
            .size(36.dp).clip(CircleShape).background(ApexPalette.NeonCyan).border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Icons.Default.SwapHoriz, "Drag to compare before and after",
                tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.fillMaxHeight().fillMaxWidth().pointerInput(Unit) {
            detectDragGestures { change, drag -> change.consume(); currentOnSplit((currentSplit + drag.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)) }
        }.pointerInput(Unit) {
            detectTapGestures { currentOnSplit((it.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)) }
        })
        }
        if (before != null && after == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(26.dp), color = ApexPalette.NeonCyan, strokeWidth = 2.dp)
        if (before != null && error != null) Text(error, color = ApexPalette.NeonPink, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).clip(RoundedCornerShape(5.dp)).background(ApexPalette.BgGlass).padding(5.dp))
    }
}

@Composable
private fun WheelControl(label: String, vector: ColorWheelVector, accent: Color, onChange: (ColorWheelVector) -> Unit, modifier: Modifier = Modifier) {
    val currentVector by rememberUpdatedState(vector)
    val currentOnChange by rememberUpdatedState(onChange)
    GlassCard(modifier.height(116.dp), cornerRadius = 13.dp) {
        Column(Modifier.fillMaxSize().padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            Text(label, color = ApexPalette.TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Canvas(Modifier.size(72.dp).pointerInput(Unit) {
                detectDragGestures { change, _ -> change.consume(); currentOnChange(ColorWheelVector(
                    ((change.position.x - size.width / 2f) / (minOf(size.width, size.height) * .42f)).coerceIn(-1f, 1f),
                    ((change.position.y - size.height / 2f) / (minOf(size.width, size.height) * .42f)).coerceIn(-1f, 1f))) }
            }.pointerInput(Unit) {
                detectTapGestures { point -> currentOnChange(ColorWheelVector(
                    ((point.x - size.width / 2f) / (minOf(size.width, size.height) * .42f)).coerceIn(-1f, 1f),
                    ((point.y - size.height / 2f) / (minOf(size.width, size.height) * .42f)).coerceIn(-1f, 1f))) }
            }) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                drawCircle(Brush.sweepGradient(listOf(Color(0xFFFF1744), Color(0xFFFFC400), Color(0xFF10B981), Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFF7C4DFF), Color(0xFFFF1744))), radius, center)
                drawCircle(ApexPalette.BgBase, radius * .54f, center)
                val marker = Offset(center.x + currentVector.x * radius * .83f, center.y + currentVector.y * radius * .83f)
                drawCircle(Color.White, 5f, marker); drawCircle(accent, 3f, marker)
            }
            Text("${(currentVector.x * 100).toInt()}, ${(currentVector.y * 100).toInt()}", color = accent, fontSize = 9.sp)
        }
    }
}

@Composable
private fun RgbCurvesCanvas(
    grade: ColorStudioGrade,
    selected: String,
    selectedPoint: Int,
    onSelect: (String) -> Unit,
    onPointChange: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val curves = listOf("R" to (grade.rCurve to ApexPalette.TrackOverlay), "G" to (grade.gCurve to ApexPalette.NeonEmerald),
        "B" to (grade.bCurve to ApexPalette.NeonPurple), "L" to (grade.lumaCurve to ApexPalette.NeonCyan))
    val currentCurves by rememberUpdatedState(curves)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnPointChange by rememberUpdatedState(onPointChange)
    val currentSelectedPoint by rememberUpdatedState(selectedPoint)
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(ApexPalette.BgSurface).border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp)).padding(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            curves.forEach { (channel, pair) ->
                val active = selected == channel
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (active) pair.second.copy(alpha = .2f) else ApexPalette.BgElevated)
                    .border(if (active) 1.dp else 0.dp, pair.second, RoundedCornerShape(4.dp)).clickable { onSelect(channel) }.padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(channel, color = if (active) pair.second else ApexPalette.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Point ${selectedPoint + 1}/5", color = ApexPalette.TextTertiary, fontSize = 9.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(6.dp)).background(ApexPalette.BgBase)
            .pointerInput(selected) {
                detectDragGestures(onDragStart = { point ->
                    val curve = currentCurves.first { it.first == currentSelected }.second.first
                    val index = curve.indices.minBy { abs(point.x - it * size.width / 4f) }
                    currentOnPointChange(index, (1f - point.y / size.height).coerceIn(0f, 1f))
                }, onDrag = { change, _ ->
                    change.consume()
                    val curve = currentCurves.first { it.first == currentSelected }.second.first
                    val index = curve.indices.minBy { abs(change.position.x - it * size.width / 4f) }
                    currentOnPointChange(index, (1f - change.position.y / size.height).coerceIn(0f, 1f))
                })
            }.pointerInput(selected) {
                detectTapGestures { point ->
                    val curve = currentCurves.first { it.first == currentSelected }.second.first
                    val index = curve.indices.minBy { abs(point.x - it * size.width / 4f) }
                    currentOnPointChange(index, (1f - point.y / size.height).coerceIn(0f, 1f))
                }
            }) {
            val w = size.width; val h = size.height
            for (i in 1..3) {
                drawLine(Color.White.copy(.06f), Offset(w * i / 4, 0f), Offset(w * i / 4, h), 1f)
                drawLine(Color.White.copy(.06f), Offset(0f, h * i / 4), Offset(w, h * i / 4), 1f)
            }
            val pair = curves.first { it.first == selected }.second
            val points = pair.first.mapIndexed { i, value -> Offset(w * i / 4f, (1f - value) * h) }
            for (i in 0 until points.lastIndex) drawLine(pair.second, points[i], points[i + 1], 2f)
            points.forEachIndexed { i, p -> drawCircle(Color.White, if (i == selectedPoint) 5f else 4f, p); drawCircle(pair.second, 2.5f, p) }
        }
    }
}

@Composable
private fun AdjustmentSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, accent: Color, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = ApexPalette.TextSecondary, fontSize = 10.sp)
            Text(String.format(Locale.US, "%.2f", value), color = accent, fontSize = 10.sp)
        }
        Slider(value, onChange, valueRange = range, colors = sliderColors(accent), modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun sliderColors(accent: Color) = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = ApexPalette.BgElevated)
