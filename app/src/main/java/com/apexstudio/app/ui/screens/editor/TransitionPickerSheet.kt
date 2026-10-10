package com.apexstudio.app.ui.screens.editor

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.*
import com.apexstudio.app.ui.theme.ApexPalette
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Professional Video Transitions Bottom Sheet UI Component.
 * Allows selecting, previewing, timing, and applying professional transitions
 * (Cross Dissolve, Directional Wipes, Fade to Black/White, Zoom Push, Motion Slides,
 * Glitch, Light Leak) between clips on the timeline.
 */
@Composable
fun TransitionPickerSheet(
    fromClip: MediaClip?,
    toClip: MediaClip?,
    currentTransition: ClipTransition?,
    onApplyTransition: (type: String, durationMs: Long) -> Unit,
    onApplyToAll: (type: String, durationMs: Long) -> Unit,
    onRemoveTransition: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Phase 2: register pack transitions on first open (idempotent).
    val packContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.apexstudio.app.data.packs.PackLoader.ensurePackTransitionsRegistered(packContext)
    }
    var selectedTypeId by remember(currentTransition) {
        mutableStateOf(currentTransition?.type ?: "cross_dissolve")
    }
    var durationMs by remember(currentTransition) {
        mutableStateOf(currentTransition?.durationMs ?: 500L)
    }
    var activeCategory by remember {
        mutableStateOf(TransitionCategory.ALL)
    }

    val selectedDef = remember(selectedTypeId) {
        TransitionLibrary.getById(selectedTypeId) ?: TransitionLibrary.allTransitions().first()
    }

    // Animation progress for the live transition previewer (0f -> 1f loop)
    val infiniteTransition = rememberInfiniteTransition(label = "TransitionLoop")
    val previewProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(top = 14.dp, start = 16.dp, end = 16.dp, bottom = 20.dp)
    ) {
        // Drag Handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.25f))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(12.dp))

        // Header: Title + Context (Clip A -> Clip B) + Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Transform,
                        contentDescription = null,
                        tint = ApexPalette.NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Video Transitions",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val clipA = fromClip?.name?.ifEmpty { "Clip A" } ?: "Clip A"
                val clipB = toClip?.name?.ifEmpty { "Clip B" } ?: "Clip B"
                Text(
                    text = "Between: $clipA ➔ $clipB",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close Transitions",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Live Transition Visual Canvas Preview — uses the REAL end frame of
        // Clip A and start frame of Clip B so the preview shows what the
        // transition will actually look like (not gradient placeholders).
        val context = LocalContext.current
        var clipABitmap by remember(fromClip?.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
        var clipBBitmap by remember(toClip?.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(fromClip?.id, toClip?.id) {
            clipABitmap = fromClip?.let { clip ->
                try {
                    val uri = com.apexstudio.app.data.media.MediaUriResolver
                        .resolvePlayableUri(context, clip.uri).toString()
                    // Last frame of Clip A (what the transition starts from)
                    com.apexstudio.app.data.media.VideoThumbnailExtractor.extractFrame(
                        context, uri, (clip.trimEndMs - 100).coerceAtLeast(0)
                    )
                } catch (e: Exception) { null }
            }
            clipBBitmap = toClip?.let { clip ->
                try {
                    val uri = com.apexstudio.app.data.media.MediaUriResolver
                        .resolvePlayableUri(context, clip.uri).toString()
                    // First frame of Clip B (what the transition ends on)
                    com.apexstudio.app.data.media.VideoThumbnailExtractor.extractFrame(
                        context, uri, clip.trimStartMs
                    )
                } catch (e: Exception) { null }
            }
        }
        TransitionLivePreview(
            selectedType = selectedDef.id,
            progress = previewProgress,
            clipABitmap = clipABitmap,
            clipBBitmap = clipBBitmap,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransitionCategory.values().forEach { cat ->
                val isSelected = cat == activeCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.22f)
                            else ApexPalette.BgElevated
                        )
                        .border(
                            1.dp,
                            if (isSelected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { activeCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat.label,
                        color = if (isSelected) ApexPalette.NeonCyan else Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Transition Grid
        val displayedTransitions = remember(activeCategory) {
            TransitionLibrary.getByCategory(activeCategory)
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // "None" option
            item {
                val isNone = selectedTypeId == "none"
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(105.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isNone) ApexPalette.NeonCyan.copy(alpha = 0.15f) else ApexPalette.BgElevated)
                        .border(
                            1.5.dp,
                            if (isNone) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedTypeId = "none" }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF26334D)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = "None",
                                tint = if (isNone) ApexPalette.NeonCyan else Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "None (Cut)",
                            color = if (isNone) Color.White else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            items(displayedTransitions) { trans ->
                val isSelected = trans.id == selectedTypeId
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(105.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                if (isSelected) trans.gradientColors
                                else listOf(Color(0xFF1E2538), Color(0xFF131722))
                            )
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedTypeId = trans.id }
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Tag (e.g. Popular, Cinematic)
                        if (trans.tag != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ApexPalette.NeonPurple.copy(alpha = 0.8f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    trans.tag,
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Spacer(Modifier.height(12.dp))
                        }

                        // Icon
                        Icon(
                            imageVector = trans.icon,
                            contentDescription = trans.name,
                            tint = if (isSelected) Color.White else ApexPalette.NeonCyan,
                            modifier = Modifier.size(28.dp)
                        )

                        // Name
                        Text(
                            text = trans.name,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Duration Slider & Timing Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ApexPalette.BgElevated)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Transition Duration",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    TransitionLibrary.formatDuration(durationMs),
                    color = ApexPalette.NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Slider(
                value = durationMs.toFloat(),
                onValueChange = { durationMs = it.toLong() },
                valueRange = 100f..2000f,
                steps = 18,
                colors = SliderDefaults.colors(
                    thumbColor = ApexPalette.NeonCyan,
                    activeTrackColor = ApexPalette.NeonCyan,
                    inactiveTrackColor = Color(0xFF26334D)
                ),
                modifier = Modifier.height(28.dp)
            )

            // Preset duration chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(200L, 300L, 500L, 800L, 1000L, 1500L).forEach { presetMs ->
                    val isPreset = durationMs == presetMs
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isPreset) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                                else Color(0xFF1E2538)
                            )
                            .border(
                                1.dp,
                                if (isPreset) ApexPalette.NeonCyan else Color(0xFF2E384D),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { durationMs = presetMs }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            TransitionLibrary.formatDuration(presetMs),
                            color = if (isPreset) ApexPalette.NeonCyan else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Bottom Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Apply to All Button
            OutlinedButton(
                onClick = {
                    if (selectedTypeId == "none") {
                        onRemoveTransition()
                    } else {
                        onApplyToAll(selectedTypeId, durationMs)
                    }
                    onClose()
                },
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ApexPalette.NeonCyan
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Apply to All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // Apply Button
            Button(
                onClick = {
                    if (selectedTypeId == "none") {
                        onRemoveTransition()
                    } else {
                        onApplyTransition(selectedTypeId, durationMs)
                    }
                    onClose()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApexPalette.NeonCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Apply", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Animated real-time visual canvas demonstrating how the selected transition
 * transforms outgoing Clip A into incoming Clip B.
 */
@Composable
private fun TransitionLivePreview(
    selectedType: String,
    progress: Float,
    clipABitmap: android.graphics.Bitmap?,
    clipBBitmap: android.graphics.Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B0E14))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val p = progress.coerceIn(0f, 1f)

            // Real clip frames (fall back to gradients while loading).
            // Clip A = last frame of the outgoing clip, Clip B = first frame
            // of the incoming clip — so the preview shows the ACTUAL transition.
            val clipAImage = clipABitmap?.asImageBitmap()
            val clipBImage = clipBBitmap?.asImageBitmap()
            val clipABrush = Brush.linearGradient(
                listOf(Color(0xFF1E1B4B), Color(0xFF312E81)),
                start = Offset.Zero,
                end = Offset(w, h)
            )
            val clipBBrush = Brush.linearGradient(
                listOf(Color(0xFF065F46), Color(0xFF047857), Color(0xFF0E7490)),
                start = Offset.Zero,
                end = Offset(w, h)
            )

            fun drawClipA(alpha: Float = 1f, topLeft: Offset = Offset.Zero, size: Size = Size(w, h)) {
                if (clipAImage != null) {
                    drawImage(
                        image = clipAImage,
                        dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        alpha = alpha
                    )
                } else {
                    drawRect(brush = clipABrush, alpha = alpha, topLeft = topLeft, size = size)
                }
            }
            fun drawClipB(alpha: Float = 1f, topLeft: Offset = Offset.Zero, size: Size = Size(w, h)) {
                if (clipBImage != null) {
                    drawImage(
                        image = clipBImage,
                        dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        alpha = alpha
                    )
                } else {
                    drawRect(brush = clipBBrush, alpha = alpha, topLeft = topLeft, size = size)
                }
            }

            when (selectedType) {
                "cross_dissolve", "cross" -> {
                    drawClipA()
                    drawClipB(alpha = p)
                }

                "fade_black" -> {
                    if (p < 0.5f) {
                        val fadeOut = 1f - (p * 2f)
                        drawClipA(alpha = fadeOut)
                    } else {
                        val fadeIn = (p - 0.5f) * 2f
                        drawClipB(alpha = fadeIn)
                    }
                }

                "fade_white" -> {
                    if (p < 0.5f) {
                        val flash = p * 2f
                        drawClipA()
                        drawRect(color = Color.White, alpha = flash)
                    } else {
                        val flash = (1f - (p - 0.5f) * 2f)
                        drawClipB()
                        drawRect(color = Color.White, alpha = flash)
                    }
                }

                "wipe_left", "wipe" -> {
                    drawClipA()
                    val splitX = w * (1f - p)
                    clipRect(left = splitX, top = 0f, right = w, bottom = h) {
                        drawClipB()
                    }
                    drawLine(
                        color = Color(0xFF38BDF8),
                        start = Offset(splitX, 0f),
                        end = Offset(splitX, h),
                        strokeWidth = 3f
                    )
                }

                "wipe_right" -> {
                    drawClipA()
                    val splitX = w * p
                    clipRect(left = 0f, top = 0f, right = splitX, bottom = h) {
                        drawClipB()
                    }
                    drawLine(
                        color = Color(0xFF38BDF8),
                        start = Offset(splitX, 0f),
                        end = Offset(splitX, h),
                        strokeWidth = 3f
                    )
                }

                "wipe_up" -> {
                    drawClipA()
                    val splitY = h * (1f - p)
                    clipRect(left = 0f, top = splitY, right = w, bottom = h) {
                        drawClipB()
                    }
                    drawLine(
                        color = Color(0xFFEC4899),
                        start = Offset(0f, splitY),
                        end = Offset(w, splitY),
                        strokeWidth = 3f
                    )
                }

                "wipe_down" -> {
                    drawClipA()
                    val splitY = h * p
                    clipRect(left = 0f, top = 0f, right = w, bottom = splitY) {
                        drawClipB()
                    }
                    drawLine(
                        color = Color(0xFFEC4899),
                        start = Offset(0f, splitY),
                        end = Offset(w, splitY),
                        strokeWidth = 3f
                    )
                }

                "clock_wipe" -> {
                    drawClipA()
                    val sweepAngle = p * 360f
                    // Clip the real Clip B frame to the sweeping arc
                    clipPath(
                        Path().apply {
                            moveTo(w / 2f, h / 2f)
                            arcTo(
                                rect = Rect(
                                    offset = Offset(-w * 0.25f, -h * 0.25f),
                                    size = Size(w * 1.5f, h * 1.5f)
                                ),
                                startAngleDegrees = -90f,
                                sweepAngleDegrees = sweepAngle,
                                forceMoveTo = false
                            )
                            close()
                        }
                    ) {
                        drawClipB()
                    }
                }

                "zoom_blur", "zoom" -> {
                    val scaleA = 1f + p * 0.5f
                    val scaleB = 1.5f - (1f - p) * 0.5f
                    drawClipA(alpha = (1f - p))
                    drawClipB(alpha = p)
                }

                "slide_left", "slide" -> {
                    val offsetAX = -w * p
                    val offsetBX = w * (1f - p)
                    clipRect(left = 0f, top = 0f, right = w, bottom = h) {
                        drawClipA(topLeft = Offset(offsetAX, 0f), size = Size(w, h))
                        drawClipB(topLeft = Offset(offsetBX, 0f), size = Size(w, h))
                    }
                }

                "slide_right" -> {
                    val offsetAX = w * p
                    val offsetBX = -w * (1f - p)
                    clipRect(left = 0f, top = 0f, right = w, bottom = h) {
                        drawClipA(topLeft = Offset(offsetAX, 0f), size = Size(w, h))
                        drawClipB(topLeft = Offset(offsetBX, 0f), size = Size(w, h))
                    }
                }

                "slide_up" -> {
                    val offsetAY = -h * p
                    val offsetBY = h * (1f - p)
                    clipRect(left = 0f, top = 0f, right = w, bottom = h) {
                        drawClipA(topLeft = Offset(0f, offsetAY), size = Size(w, h))
                        drawClipB(topLeft = Offset(0f, offsetBY), size = Size(w, h))
                    }
                }

                "glitch" -> {
                    drawClipA()
                    if (p > 0.1f) {
                        // Render glitch scanlines & RGB offsets
                        val sliceH = h / 8f
                        for (i in 0 until 8) {
                            val sliceY = i * sliceH
                            val shift = ((i % 3) - 1) * 20f * (1f - kotlin.math.abs(p - 0.5f) * 2f)
                            clipRect(left = 0f, top = sliceY, right = w, bottom = sliceY + sliceH) {
                                if (i % 2 == 0) {
                                    drawClipB(topLeft = Offset(shift, 0f), size = Size(w, h))
                                } else {
                                    drawClipA(topLeft = Offset(-shift, 0f), size = Size(w, h))
                                }
                            }
                        }
                    }
                    if (p > 0.6f) {
                        drawClipB(alpha = (p - 0.6f) * 2.5f)
                    }
                }

                "light_leak" -> {
                    drawClipA()
                    drawClipB(alpha = p)
                    val flare = kotlin.math.sin(p * PI.toFloat())
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFDE047).copy(alpha = 0.9f * flare), Color.Transparent),
                            center = Offset(w * p, h * 0.5f),
                            radius = w * 0.6f
                        ),
                        radius = w * 0.6f,
                        center = Offset(w * p, h * 0.5f)
                    )
                }

                "cross_blur" -> {
                    // Defocus blend: crossfade + horizontal streaks suggesting blur
                    drawClipA(alpha = 1f - p)
                    drawClipB(alpha = p)
                    val blurAmt = kotlin.math.sin(p * PI.toFloat())
                    if (blurAmt > 0.05f) {
                        for (i in 0 until 7) {
                            val y = h * (i + 1) / 8f
                            drawLine(
                                color = Color.White.copy(alpha = 0.08f * blurAmt),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 2f + 6f * blurAmt
                            )
                        }
                    }
                }

                "doorway" -> {
                    // B pushes in from behind, A shrinks toward center
                    val openAmt = p.coerceIn(0f, 1f)
                    val bScale = 1.3f - 0.3f * openAmt
                    val bw = w * bScale
                    val bh = h * bScale
                    drawClipB(
                        topLeft = Offset((w - bw) / 2f, (h - bh) / 2f),
                        size = Size(bw, bh)
                    )
                    val aScale = (1f - openAmt * 0.94f).coerceAtLeast(0.02f)
                    val aw = w * aScale
                    val ah = h * aScale
                    clipRect(left = 0f, top = 0f, right = w, bottom = h) {
                        drawClipA(
                            alpha = 1f - openAmt * openAmt,
                            topLeft = Offset((w - aw) / 2f, (h - ah) / 2f),
                            size = Size(aw, ah)
                        )
                    }
                }

                "pixelize" -> {
                    // Mosaic: draw A/B then overlay a growing grid
                    if (p < 0.5f) drawClipA() else drawClipB()
                    val cells = (3 + (1f - kotlin.math.abs(p - 0.5f) * 2f) * 9).toInt()
                    val cw = w / cells
                    val ch = h / cells
                    for (gx in 0 until cells) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.25f),
                            start = Offset(gx * cw, 0f),
                            end = Offset(gx * cw, h),
                            strokeWidth = 1f
                        )
                    }
                    for (gy in 0 until cells) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.25f),
                            start = Offset(0f, gy * ch),
                            end = Offset(w, gy * ch),
                            strokeWidth = 1f
                        )
                    }
                }

                "crosswarp" -> {
                    // Warp sweep: A slides right bowing, B enters from left
                    val bow = kotlin.math.sin(p * PI.toFloat()) * h * 0.08f
                    val sweep = w * p
                    clipRect(left = 0f, top = 0f, right = w, bottom = h) {
                        drawClipA(topLeft = Offset(sweep * 0.9f, bow * (1f - p)), size = Size(w, h))
                        drawClipB(topLeft = Offset(-w * (1f - p) * 0.9f, -bow * p), size = Size(w, h))
                    }
                    // warp seam glow
                    val seamX = w * (1f - p)
                    drawLine(
                        color = Color(0xFFF472B6).copy(alpha = 0.5f * kotlin.math.sin(p * PI.toFloat())),
                        start = Offset(seamX, 0f),
                        end = Offset(seamX, h),
                        strokeWidth = 4f
                    )
                }

                else -> {
                    // Default cut
                    if (p < 0.5f) drawClipA()
                    else drawClipB()
                }
            }
        }

        // Overlay Labels for Shot A and Shot B
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Shot A", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "Transition Preview (${(progress * 100).toInt()}%)",
                    color = ApexPalette.NeonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Shot B", color = Color(0xFF6EE7B7), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
