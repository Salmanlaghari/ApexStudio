package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.filter.FilterCategory
import com.apexstudio.app.data.filter.FilterColorMatrix
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.filter.GpuImageLutEngine
import com.apexstudio.app.presentation.state.LutGalleryViewMode
import com.apexstudio.app.presentation.state.LutTargetTrack
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.launch

/**
 * Professional Color Grading LUTs (Look-Up Tables) Panel
 *
 * Powered by GPUImage library:
 * - Real-time GPUImageLookupFilter application
 * - Thumbnail preview gallery where each LUT button shows a small sample image of a video frame with that specific filter applied
 * - Track targeting (All Tracks, Main V1, Overlay V2, Active Clip)
 * - 70+ Professional Cinema, Film, and Stylized 3D LUTs
 * - Live Before / After Comparison split wiper and hold-to-compare peek
 * - View mode switcher: Multi-column Gallery Grid vs Compact Filmstrip
 * - Frame Sync action to sample whatever video frame the playhead is currently parked at
 * - Secondary color adjustments: Temperature, Tint, Contrast, and Saturation
 * - Favorite presets management and custom search
 */
@Composable
fun ColorGradingLutPanel(
    manifest: FilterManifest,
    activeFilterId: String?,
    intensity: Float,
    targetTrack: LutTargetTrack,
    compareMode: Boolean,
    splitPosition: Float,
    favoriteIds: Set<String>,
    customLuts: List<FilterPreset>,
    contrast: Float,
    saturation: Float,
    temperatureK: Float,
    tint: Float,
    thumbnails: Map<String?, androidx.compose.ui.graphics.ImageBitmap> = emptyMap(),
    galleryViewMode: LutGalleryViewMode = LutGalleryViewMode.GRID,
    isLoadingThumbnails: Boolean = false,
    onSelectFilter: (String?) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onTargetTrackChange: (LutTargetTrack) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleCompare: () -> Unit,
    onSplitPositionChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onTintChange: (Float) -> Unit,
    onGalleryViewModeChange: (LutGalleryViewMode) -> Unit = {},
    onRefreshThumbnails: () -> Unit = {},
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf("all") }
    var isHoldingOriginal by remember { mutableStateOf(false) }
    var showFineTuneControls by remember { mutableStateOf(false) }

    // Raw video frame (un-filtered baseline sample)
    val rawVideoFrame = thumbnails[null]

    // Aggregate all available presets (bundled + custom imports)
    val allPresets = remember(manifest, customLuts) {
        (manifest.filters + manifest.categories.flatMap { it.filters } + customLuts)
            .distinctBy { it.id }
    }

    // Filter presets based on category, favorites, and search query
    val displayedPresets = remember(allPresets, selectedCategoryId, favoriteIds, searchQuery) {
        allPresets.filter { preset ->
            val matchesCategory = when (selectedCategoryId) {
                "all" -> true
                "favorites" -> favoriteIds.contains(preset.id)
                "custom" -> preset.category.equals("Custom", ignoreCase = true)
                else -> {
                    val cat = manifest.categories.firstOrNull { it.id == selectedCategoryId }
                    cat != null && (preset.category.equals(cat.name, ignoreCase = true) || cat.filters.any { it.id == preset.id })
                }
            }
            val matchesSearch = searchQuery.isBlank() ||
                    preset.name.contains(searchQuery, ignoreCase = true) ||
                    preset.category.contains(searchQuery, ignoreCase = true)

            matchesCategory && matchesSearch
        }
    }

    val activePreset = remember(activeFilterId, allPresets) {
        allPresets.firstOrNull { it.id == activeFilterId }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .navigationBarsPadding()
    ) {
        // --- 1. Header Bar with View Mode Switcher ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Color Grading & LUTs",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ApexPalette.NeonCyan.copy(alpha = 0.15f))
                                .border(0.5.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "3D LUTs",
                                color = ApexPalette.NeonCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        activePreset?.name ?: "Raw Video (Un-graded)",
                        color = if (activePreset != null) ApexPalette.NeonCyan else ApexPalette.TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }

            // View Mode Toggle (Gallery Grid vs Filmstrip)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ApexPalette.BgElevated)
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onGalleryViewModeChange(LutGalleryViewMode.GRID) },
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (galleryViewMode == LutGalleryViewMode.GRID) ApexPalette.NeonCyan.copy(alpha = 0.25f) else Color.Transparent)
                ) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = "Gallery Grid View",
                        tint = if (galleryViewMode == LutGalleryViewMode.GRID) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { onGalleryViewModeChange(LutGalleryViewMode.STRIP) },
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (galleryViewMode == LutGalleryViewMode.STRIP) ApexPalette.NeonCyan.copy(alpha = 0.25f) else Color.Transparent)
                ) {
                    Icon(
                        Icons.Default.ViewCarousel,
                        contentDescription = "Filmstrip Carousel View",
                        tint = if (galleryViewMode == LutGalleryViewMode.STRIP) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Search Toggle Button
            IconButton(
                onClick = { searchVisible = !searchVisible },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (searchVisible) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search Presets",
                    tint = if (searchVisible) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Before / After A/B Compare Toggle Button
            IconButton(
                onClick = onToggleCompare,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (compareMode) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated)
            ) {
                Icon(
                    Icons.Default.Compare,
                    contentDescription = "Compare Before/After",
                    tint = if (compareMode) ApexPalette.NeonCyan else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Reset Button
            IconButton(
                onClick = onReset,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
            ) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = "Reset Grading",
                    tint = ApexPalette.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Close Button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ApexPalette.BgElevated)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // --- 2. Target Track Selector & Frame Sync Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Track selector
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ApexPalette.BgElevated)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                LutTargetTrack.values().forEach { track ->
                    val selected = track == targetTrack
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) ApexPalette.NeonCyan.copy(alpha = 0.22f)
                                else Color.Transparent
                            )
                            .clickable { onTargetTrackChange(track) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            track.shortBadge,
                            color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Sync Video Frame Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ApexPalette.BgElevated)
                    .border(0.5.dp, ApexPalette.BorderGlass, RoundedCornerShape(8.dp))
                    .clickable(onClick = onRefreshThumbnails)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoadingThumbnails) {
                    CircularProgressIndicator(
                        color = ApexPalette.NeonCyan,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(12.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Sync Video Frame",
                        tint = ApexPalette.NeonCyan,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    if (isLoadingThumbnails) "Updating..." else "Sync Frame",
                    color = ApexPalette.NeonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // --- 3. Split Comparison Slider (When active) ---
        if (compareMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexPalette.BgElevated)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Split Comparison (Left: Original | Right: Graded)",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${(splitPosition * 100).toInt()}%",
                        color = ApexPalette.NeonCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = splitPosition,
                    onValueChange = onSplitPositionChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = ApexPalette.NeonCyan,
                        activeTrackColor = ApexPalette.NeonCyan,
                        inactiveTrackColor = ApexPalette.BorderGlass
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        // Search text field (when opened)
        AnimatedVisibility(visible = searchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter by name or style (e.g. teal, kodak, cinema)...", fontSize = 11.sp, color = ApexPalette.TextTertiary) },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = ApexPalette.TextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ApexPalette.NeonCyan,
                    unfocusedBorderColor = ApexPalette.BorderGlass,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .height(48.dp)
            )
        }

        // --- 4. Category Tabs ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LutCategoryChip(
                label = "All (${allPresets.size})",
                selected = selectedCategoryId == "all",
                onClick = { selectedCategoryId = "all" }
            )

            LutCategoryChip(
                label = "★ Favorites (${favoriteIds.size})",
                selected = selectedCategoryId == "favorites",
                onClick = { selectedCategoryId = "favorites" }
            )

            manifest.categories.forEach { cat ->
                LutCategoryChip(
                    label = cat.name,
                    selected = selectedCategoryId == cat.id,
                    onClick = { selectedCategoryId = cat.id }
                )
            }

            if (customLuts.isNotEmpty()) {
                LutCategoryChip(
                    label = "Custom (${customLuts.size})",
                    selected = selectedCategoryId == "custom",
                    onClick = { selectedCategoryId = "custom" }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // --- 5. THUMBNAIL PREVIEW GALLERY ---
        // Each LUT button shows a small sample image of a video frame with that specific filter applied
        if (galleryViewMode == LutGalleryViewMode.GRID) {
            // Multi-column thumbnail preview gallery
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 92.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 250.dp)
            ) {
                // Original / Raw Video Frame Card (No filter)
                item {
                    RawVideoGridCard(
                        selected = activeFilterId == null,
                        thumbnail = rawVideoFrame,
                        onClick = { onSelectFilter(null) }
                    )
                }

                // Graded LUT buttons showing the video frame with the specific filter applied
                items(displayedPresets, key = { it.id }) { preset ->
                    val isSelected = activeFilterId == preset.id
                    val isFavorite = favoriteIds.contains(preset.id)
                    val thumb = thumbnails[preset.id]

                    LutGalleryGridCard(
                        preset = preset,
                        selected = isSelected,
                        favorite = isFavorite,
                        thumbnail = thumb,
                        rawVideoFrame = rawVideoFrame,
                        onClick = { onSelectFilter(preset.id) },
                        onToggleFavorite = { onToggleFavorite(preset.id) }
                    )
                }
            }
        } else {
            // Compact horizontal filmstrip carousel
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                // Raw / Original Video (No Grade)
                item {
                    RawVideoTile(
                        selected = activeFilterId == null,
                        thumbnail = rawVideoFrame,
                        onClick = { onSelectFilter(null) }
                    )
                }

                // LUT Preset Cards
                items(displayedPresets, key = { it.id }) { preset ->
                    val isSelected = activeFilterId == preset.id
                    val isFavorite = favoriteIds.contains(preset.id)
                    val thumb = thumbnails[preset.id]

                    LutPresetCard(
                        preset = preset,
                        selected = isSelected,
                        favorite = isFavorite,
                        thumbnail = thumb,
                        rawVideoFrame = rawVideoFrame,
                        onClick = { onSelectFilter(preset.id) },
                        onToggleFavorite = { onToggleFavorite(preset.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // --- 6. Intensity Control & Quick Steppers ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "LUT Intensity",
                color = ApexPalette.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Slider(
                value = intensity,
                onValueChange = onIntensityChange,
                valueRange = 0f..1f,
                enabled = activeFilterId != null,
                colors = SliderDefaults.colors(
                    thumbColor = ApexPalette.NeonCyan,
                    activeTrackColor = ApexPalette.NeonCyan,
                    inactiveTrackColor = ApexPalette.BorderGlass
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${(intensity * 100).toInt()}%",
                color = if (activeFilterId == null) ApexPalette.TextTertiary else ApexPalette.NeonCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        }

        // Quick Preset Chips for Intensity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(0.25f, 0.50f, 0.75f, 1.0f).forEach { stepVal ->
                val isStep = Math.abs(intensity - stepVal) < 0.05f && activeFilterId != null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isStep) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated)
                        .border(
                            0.5.dp,
                            if (isStep) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(enabled = activeFilterId != null) { onIntensityChange(stepVal) }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "${(stepVal * 100).toInt()}%",
                        color = if (isStep) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Toggle secondary adjustments expander
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (showFineTuneControls) ApexPalette.NeonPurple.copy(alpha = 0.2f) else ApexPalette.BgElevated)
                    .border(
                        0.5.dp,
                        if (showFineTuneControls) ApexPalette.NeonPurple else ApexPalette.BorderGlass,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { showFineTuneControls = !showFineTuneControls }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = if (showFineTuneControls) ApexPalette.NeonPurple else ApexPalette.TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "White Balance & Tone",
                    color = if (showFineTuneControls) ApexPalette.NeonPurple else ApexPalette.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    if (showFineTuneControls) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = if (showFineTuneControls) ApexPalette.NeonPurple else ApexPalette.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // --- 7. Secondary Color Adjustments (White Balance, Tone) ---
        AnimatedVisibility(visible = showFineTuneControls) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexPalette.BgElevated)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Color Temperature (Kelvin)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Temp", color = ApexPalette.TextSecondary, fontSize = 10.sp, modifier = Modifier.width(42.dp))
                    Slider(
                        value = temperatureK,
                        onValueChange = onTemperatureChange,
                        valueRange = 2500f..8500f,
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonAmber,
                            activeTrackColor = ApexPalette.NeonAmber
                        ),
                        modifier = Modifier.weight(1f).height(24.dp)
                    )
                    Text("${temperatureK.toInt()}K", color = ApexPalette.NeonAmber, fontSize = 9.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                }

                // Tint
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tint", color = ApexPalette.TextSecondary, fontSize = 10.sp, modifier = Modifier.width(42.dp))
                    Slider(
                        value = tint,
                        onValueChange = onTintChange,
                        valueRange = -50f..50f,
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonPink,
                            activeTrackColor = ApexPalette.NeonPink
                        ),
                        modifier = Modifier.weight(1f).height(24.dp)
                    )
                    Text(String.format("%+d", tint.toInt()), color = ApexPalette.NeonPink, fontSize = 9.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                }

                // Contrast
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Contrast", color = ApexPalette.TextSecondary, fontSize = 10.sp, modifier = Modifier.width(42.dp))
                    Slider(
                        value = contrast,
                        onValueChange = onContrastChange,
                        valueRange = 0.5f..1.8f,
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonCyan,
                            activeTrackColor = ApexPalette.NeonCyan
                        ),
                        modifier = Modifier.weight(1f).height(24.dp)
                    )
                    Text(String.format("%.1fx", contrast), color = ApexPalette.NeonCyan, fontSize = 9.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                }

                // Saturation
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sat", color = ApexPalette.TextSecondary, fontSize = 10.sp, modifier = Modifier.width(42.dp))
                    Slider(
                        value = saturation,
                        onValueChange = onSaturationChange,
                        valueRange = 0.0f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonCyan,
                            activeTrackColor = ApexPalette.NeonCyan
                        ),
                        modifier = Modifier.weight(1f).height(24.dp)
                    )
                    Text(String.format("%.1fx", saturation), color = ApexPalette.NeonCyan, fontSize = 9.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

/**
 * Category Chip
 */
@Composable
private fun LutCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.2f) else ApexPalette.BgElevated)
            .border(
                1.dp,
                if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = if (selected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * Gallery Grid Card for LUT Presets
 * Each card shows a sample image of the video frame with that specific filter applied.
 */
@Composable
private fun LutGalleryGridCard(
    preset: FilterPreset,
    selected: Boolean,
    favorite: Boolean,
    thumbnail: androidx.compose.ui.graphics.ImageBitmap?,
    rawVideoFrame: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ApexPalette.BgElevated)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .testTag("lut_grid_${preset.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp))
        ) {
            // Video frame with specific filter applied
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = preset.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else if (rawVideoFrame != null) {
                Image(
                    bitmap = rawVideoFrame,
                    contentDescription = preset.name,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(FilterColorMatrix.getComposeColorMatrix(preset.id, 1f)),
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Image(
                    painter = painterResource(com.apexstudio.app.R.drawable.filter_sample_portrait),
                    contentDescription = preset.name,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(FilterColorMatrix.getComposeColorMatrix(preset.id, 1f)),
                    modifier = Modifier.matchParentSize()
                )
            }

            // Category tag on top start
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    preset.category.take(8),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Favorite star on top end
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (favorite) ApexPalette.NeonAmber else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(11.dp)
                )
            }

            // If selected: ACTIVE checkmark badge
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ApexPalette.NeonCyan)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(9.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "ACTIVE",
                            color = Color.Black,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Preset name label
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.12f) else Color.Transparent)
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            Text(
                text = preset.name,
                color = if (selected) ApexPalette.NeonCyan else Color.White,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Raw / Unfiltered Video Grid Card
 */
@Composable
private fun RawVideoGridCard(
    selected: Boolean,
    thumbnail: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ApexPalette.BgElevated)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .testTag("lut_grid_raw")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp))
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = "Original Video Frame",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Image(
                    painter = painterResource(com.apexstudio.app.R.drawable.filter_sample_portrait),
                    contentDescription = "Original Video Frame",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    "RAW",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ApexPalette.NeonCyan)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) ApexPalette.NeonCyan.copy(alpha = 0.12f) else Color.Transparent)
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            Text(
                "Original",
                color = if (selected) ApexPalette.NeonCyan else Color.White,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Raw / Unfiltered Video Tile (Filmstrip Carousel)
 */
@Composable
private fun RawVideoTile(
    selected: Boolean,
    thumbnail: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag("lut_strip_raw")
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ApexPalette.BgElevated)
                .border(
                    2.dp,
                    if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                    RoundedCornerShape(10.dp)
                )
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = "Original Video",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Image(
                    painter = painterResource(com.apexstudio.app.R.drawable.filter_sample_portrait),
                    contentDescription = "Original Video",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            // Banner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                    .padding(vertical = 2.dp, horizontal = 4.dp)
            ) {
                Text(
                    "Original",
                    color = if (selected) ApexPalette.NeonCyan else Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.NeonCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

/**
 * LUT Preset Card with Video Frame Sample Graded with that Specific Filter
 */
@Composable
private fun LutPresetCard(
    preset: FilterPreset,
    selected: Boolean,
    favorite: Boolean,
    thumbnail: androidx.compose.ui.graphics.ImageBitmap?,
    rawVideoFrame: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag("lut_strip_${preset.id}")
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ApexPalette.BgElevated)
                .border(
                    2.dp,
                    if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                    RoundedCornerShape(10.dp)
                )
        ) {
            // Video frame with specific filter applied
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = preset.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else if (rawVideoFrame != null) {
                Image(
                    bitmap = rawVideoFrame,
                    contentDescription = preset.name,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(FilterColorMatrix.getComposeColorMatrix(preset.id, 1f)),
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Image(
                    painter = painterResource(com.apexstudio.app.R.drawable.filter_sample_portrait),
                    contentDescription = preset.name,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(FilterColorMatrix.getComposeColorMatrix(preset.id, 1f)),
                    modifier = Modifier.matchParentSize()
                )
            }

            // Favorite star badge at Top Start
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (favorite) ApexPalette.NeonAmber else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
            }

            // Selection Check Badge at Top End
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.NeonCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            // Name and Category Scrim Banner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    preset.name,
                    color = if (selected) ApexPalette.NeonCyan else Color.White,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
