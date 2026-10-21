package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.stickers.StickerEntry
import com.apexstudio.app.data.stickers.StickerImageCache
import com.apexstudio.app.data.stickers.StickerPack
import com.apexstudio.app.domain.model.StickerOverlay
import com.apexstudio.app.data.animation.AnimationPresetOptions
import com.apexstudio.app.data.animation.AnimationPresetMode
import com.apexstudio.app.data.animation.AnimationPresetType
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val ALL_CATEGORY = "All"

/**
 * Searchable sticker library picker.
 *
 * Shows bundled sticker PNG packs (attribution in the footer). Typing in
 * the search field filters local sticker names / tags / categories; chips
 * narrow the grid further. Tapping a decoded sticker adds it to the canvas.
 */
@Composable
fun StickerPanel(
    onAddSticker: (assetPath: String, category: String, name: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    selectedSticker: StickerOverlay? = null,
    totalTimelineMs: Long = 0L,
    onUpdateSticker: ((StickerOverlay) -> Unit)? = null,
    onDuplicate: ((String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val imageCache = remember { StickerImageCache(context) }
    val entries = remember { StickerPack.load(context) }
    val categories = remember(entries) {
        val packCategories = StickerPack.categories(entries)
        listOf(ALL_CATEGORY) + listOf("3d").filter { it in packCategories } + packCategories.filterNot { it == "3d" }
    }
    val attribution = remember { StickerPack.attribution(context) }

    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ALL_CATEGORY) }
    var showAnimated by remember { mutableStateOf(false) }

    val filtered = remember(entries, query, selectedCategory) {
        val byCategory = if (selectedCategory == ALL_CATEGORY) entries
        else entries.filter { it.category == selectedCategory }
        StickerPack.search(byCategory, query)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(14.dp)
            .testTag("sticker-panel-root")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Sticker Library",
                color = ApexPalette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        if (selectedSticker != null && (onUpdateSticker != null || onDuplicate != null || onDelete != null)) {
            StickerPropertiesStrip(selectedSticker, totalTimelineMs, onUpdateSticker, onDuplicate, onDelete)
        }

        Spacer(Modifier.height(6.dp))

        // Stickers | Animated tabs (the animated pack has a separate scope).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(false to "Stickers", true to "Animated").forEach { (tab, label) ->
                val isSel = showAnimated == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) ApexPalette.NeonCyan else ApexPalette.BgElevated)
                        .clickable { showAnimated = tab }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSel) androidx.compose.ui.graphics.Color(0xFF0A0E1A) else ApexPalette.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (showAnimated) {
            Text(
                "Separate bundled animated pack; static sticker search does not filter it.",
                color = ApexPalette.TextSecondary,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(6.dp))
            AnimatedStickerGrid(onAddSticker = onAddSticker)
        } else {
        // Tag-based search
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search stickers… (e.g. love, fire, cat)", fontSize = 12.sp, color = ApexPalette.TextSecondary) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = ApexPalette.TextSecondary, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = ApexPalette.TextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { query = "" }
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ApexPalette.TextPrimary,
                unfocusedTextColor = ApexPalette.TextPrimary,
                focusedBorderColor = ApexPalette.NeonCyan,
                unfocusedBorderColor = ApexPalette.BorderGlass,
                cursorColor = ApexPalette.NeonCyan
            ),
            textStyle = TextStyle(fontSize = 13.sp)
        )

        Text(
            "Offline · bundled stickers only",
            color = ApexPalette.TextSecondary,
            fontSize = 10.sp
        )

        Spacer(Modifier.height(4.dp))

        // Category tabs
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(categories, key = { it }) { cat ->
                val isSelected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                            else ApexPalette.BgElevated
                        )
                        .border(
                            1.dp,
                            if (isSelected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (cat == "3d") "3D look" else cat.replaceFirstChar { it.uppercase() },
                        color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Sticker grid
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (entries.isEmpty()) "Sticker pack failed to load"
                    else if (query.isBlank()) "No bundled stickers in this category"
                    else "No bundled stickers match \"$query\"",
                    color = ApexPalette.TextSecondary,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(filtered, key = { it.id }) { entry ->
                    Box(Modifier.width(56.dp)) {
                        StickerCell(
                            entry = entry,
                            imageCache = imageCache,
                            onClick = { onAddSticker(entry.assetUri, entry.category, entry.name) }
                        )
                    }
                }
            }
        }
        } // end else — sticker search/categories/grid

        Spacer(Modifier.height(8.dp))

        // License attribution (required by CC-BY 4.0)
        Text(
            text = attribution,
            color = ApexPalette.TextSecondary.copy(alpha = 0.75f),
            fontSize = 9.sp,
            maxLines = 2
        )
    }

    DisposableEffect(Unit) {
        onDispose { imageCache.evictAll() }
    }
}

@Composable
private fun StickerCell(
    entry: StickerEntry,
    imageCache: StickerImageCache,
    onClick: () -> Unit
) {
    var bitmap by remember(entry.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(entry.id, entry.assetUri) {
        // Decode off the main thread; ensureActive() drops the result if
        // the cell left composition mid-decode (fast scroll / category
        // switch) so we never write state to a disposed composition.
        val decoded = withContext(Dispatchers.IO) { imageCache.get(entry.assetUri) }
        ensureActive()
        bitmap = decoded
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(ApexPalette.BgBase)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                        .then(if (bitmap != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = entry.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.5f)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
            )
        }
    }
}


@Composable
private fun StickerPropertiesStrip(
    sticker: StickerOverlay,
    totalTimelineMs: Long,
    onUpdate: ((StickerOverlay) -> Unit)?,
    onDuplicate: ((String) -> Unit)?,
    onDelete: ((String) -> Unit)?
) {
    val timeline = totalTimelineMs.coerceAtLeast(2L)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).testTag("sticker-properties-strip")) {
        Text("${sticker.name} · Properties", color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onUpdate != null) {
                item(key = "sticker-opacity-${sticker.id}") {
                    StickerPropertyCard("Opacity · ${(sticker.opacity * 100).toInt()}%") {
                        var value by remember(sticker.id, sticker.opacity) { mutableFloatStateOf(sticker.opacity) }
                        Slider(value = value, onValueChange = { value = it }, onValueChangeFinished = { onUpdate(sticker.copy(opacity = value)) }, valueRange = 0f..1f, modifier = Modifier.testTag("sticker-opacity-slider"))
                    }
                }
                val shownStart = sticker.startMs.coerceIn(0L, timeline - 1L)
                val shownEnd = sticker.endMs.coerceIn(shownStart + 1L, timeline)
                item(key = "sticker-range-${sticker.id}") {
                    StickerPropertyCard("Range · ${shownStart / 1000.0}s–${shownEnd / 1000.0}s") {
                        var start by remember(sticker.id, sticker.startMs, sticker.endMs) { mutableFloatStateOf(shownStart.toFloat()) }
                        Slider(value = start, onValueChange = { start = it.coerceAtMost((shownEnd - 1L).toFloat()) }, onValueChangeFinished = {
                            val nextStart = start.toLong().coerceIn(0L, shownEnd - 1L)
                            onUpdate(sticker.copy(startMs = nextStart, endMs = shownEnd))
                        }, valueRange = 0f..(timeline - 1L).toFloat(), modifier = Modifier.testTag("sticker-range-start-slider"))
                        var end by remember(sticker.id, sticker.startMs, sticker.endMs) { mutableFloatStateOf(shownEnd.toFloat()) }
                        Slider(value = end, onValueChange = { end = it.coerceAtLeast((shownStart + 1L).toFloat()) }, onValueChangeFinished = {
                            val nextEnd = end.toLong().coerceIn(shownStart + 1L, timeline)
                            onUpdate(sticker.copy(startMs = shownStart, endMs = nextEnd))
                        }, valueRange = 1f..timeline.toFloat(), modifier = Modifier.testTag("sticker-range-end-slider"))
                    }
                }
                item(key = "sticker-size-${sticker.id}") {
                    StickerPropertyCard("Size · ${"%.1f".format(sticker.sizeScale)}×") {
                        var value by remember(sticker.id, sticker.sizeScale) { mutableFloatStateOf(sticker.sizeScale.coerceIn(0.1f, 4f)) }
                        Slider(value = value, onValueChange = { value = it }, onValueChangeFinished = { onUpdate(sticker.copy(sizeScale = value)) }, valueRange = 0.1f..4f)
                        var rotation by remember(sticker.id, sticker.rotationDeg) { mutableFloatStateOf(sticker.rotationDeg.coerceIn(-180f, 180f)) }
                        Text("Rotation · ${rotation.toInt()}°", color = ApexPalette.TextSecondary, fontSize = 9.sp)
                        Slider(value = rotation, onValueChange = { rotation = it }, onValueChangeFinished = { onUpdate(sticker.copy(rotationDeg = rotation)) }, valueRange = -180f..180f)
                    }
                }
                item(key = "sticker-animation-${sticker.id}") {
                    StickerPropertyCard("Animation · preview/export") {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            StickerAction("Fade In") { onUpdate(sticker.withPreset(AnimationPresetType.FADE_IN, AnimationPresetMode.IN, shownStart, shownEnd)) }
                            StickerAction("Pulse") { onUpdate(sticker.withPreset(AnimationPresetType.PULSE, AnimationPresetMode.LOOP, shownStart, shownEnd)) }
                        }
                    }
                }
            }
            if (onDuplicate != null) item(key = "sticker-duplicate-${sticker.id}") { StickerActionCard("Duplicate") { onDuplicate(sticker.id) } }
            if (onDelete != null) item(key = "sticker-delete-${sticker.id}") { StickerActionCard("Delete") { onDelete(sticker.id) } }
        }
    }
}

@Composable
private fun StickerPropertyCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.width(152.dp).clip(RoundedCornerShape(10.dp)).background(ApexPalette.BgElevated).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(title, color = ApexPalette.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun StickerActionCard(label: String, action: () -> Unit) {
    Box(Modifier.width(82.dp).clip(RoundedCornerShape(10.dp)).background(ApexPalette.BgElevated).clickable(onClick = action).padding(12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StickerAction(label: String, action: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(ApexPalette.BgSurface).clickable(onClick = action).padding(horizontal = 7.dp, vertical = 6.dp)) {
        Text(label, color = ApexPalette.NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun StickerOverlay.withPreset(type: AnimationPresetType, mode: AnimationPresetMode, startMs: Long, endMs: Long): StickerOverlay {
    val duration = (endMs - startMs).coerceIn(AnimationPresetOptions.MIN_DURATION_MS, AnimationPresetOptions.MAX_DURATION_MS)
    val track = AnimationPresetOptions.createTrack(type, mode, duration, AnimationPresetOptions.DEFAULT_INTENSITY, startMs)
    val merged = keyframes.keyframes.filterNot { old -> track.keyframes.any { it.timeMs == old.timeMs } } + track.keyframes
    return copy(keyframes = KeyframeTrack(merged).sorted())
}
