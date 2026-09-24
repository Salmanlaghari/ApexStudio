package com.apexstudio.app.ui.screens.editor

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.filter.GpuColorProfile
import com.apexstudio.app.data.filter.GpuColorProfiles
import com.apexstudio.app.data.filter.GpuFilterConfig
import com.apexstudio.app.data.filter.StylisticEffectType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.ui.theme.ApexPalette

@Composable
fun GpuVideoFilterPanel(
    selectedClip: MediaClip?,
    config: GpuFilterConfig,
    manifest: FilterManifest,
    selectedTab: Int,
    compareMode: Boolean,
    splitPosition: Float,
    lutThumbnails: Map<String?, androidx.compose.ui.graphics.ImageBitmap> = emptyMap(),
    onTabSelected: (Int) -> Unit,
    onConfigChange: (GpuFilterConfig) -> Unit,
    onToggleCompare: () -> Unit,
    onSplitPositionChange: (Float) -> Unit,
    onApplyToClip: () -> Unit,
    onApplyToAllClips: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var effectCategoryFilter by remember { mutableStateOf("All") }
    val effectCategories = listOf("All", "Artistic", "Cinematic", "Retro", "Distortion", "Stylistic")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
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
                ApexPalette.NeonCyan.copy(alpha = 0.35f),
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Drag Handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ApexPalette.TextTertiary.copy(alpha = 0.5f))
        )

        // Header: Title & Quick Compare Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ApexPalette.NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "GPU Video Filters & Effects",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
                Text(
                    if (selectedClip != null) "Editing: ${selectedClip.name}" else "Real-time GPUImage Shader Pipeline",
                    color = ApexPalette.NeonCyan.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // A/B Split Screen Compare Button
                IconButton(
                    onClick = onToggleCompare,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (compareMode) ApexPalette.NeonCyan.copy(alpha = 0.25f)
                            else ApexPalette.BgGlass
                        )
                        .border(
                            1.dp,
                            if (compareMode) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                            CircleShape
                        )
                        .testTag("gpu_filter_compare_button")
                ) {
                    Icon(
                        Icons.Default.Compare,
                        contentDescription = "Compare Mode",
                        tint = if (compareMode) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.BgGlass)
                        .testTag("gpu_filter_close_button")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ApexPalette.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Live A/B Split Slider (when Compare Mode is active)
        AnimatedVisibility(visible = compareMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApexPalette.BgGlass)
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("A/B Split Divider Position", color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${(splitPosition * 100).toInt()}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Slider(
                    value = splitPosition,
                    onValueChange = onSplitPositionChange,
                    valueRange = 0.05f..0.95f,
                    colors = SliderDefaults.colors(
                        thumbColor = ApexPalette.NeonCyan,
                        activeTrackColor = ApexPalette.NeonCyan,
                        inactiveTrackColor = ApexPalette.BgBase
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )
            }
        }

        // Studio Navigation Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = ApexPalette.NeonCyan,
            divider = {},
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ApexPalette.BgElevated)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FilterVintage, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Presets", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                selectedContentColor = ApexPalette.NeonCyan,
                unselectedContentColor = ApexPalette.TextSecondary
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Stylistic", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                selectedContentColor = ApexPalette.NeonCyan,
                unselectedContentColor = ApexPalette.TextSecondary
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Grading", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                selectedContentColor = ApexPalette.NeonCyan,
                unselectedContentColor = ApexPalette.TextSecondary
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { onTabSelected(3) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("3D LUTs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                selectedContentColor = ApexPalette.NeonCyan,
                unselectedContentColor = ApexPalette.TextSecondary
            )
        }

        // ==========================================
        // TAB 0: PRESET FILTER GALLERY (COLOR PROFILES)
        // ==========================================
        if (selectedTab == 0) {
            var profileCategoryFilter by remember { mutableStateOf("All") }
            val profileCategories = GpuColorProfiles.CATEGORIES

            // Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                profileCategories.forEach { cat ->
                    val isSelected = profileCategoryFilter == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                                else ApexPalette.BgElevated
                            )
                            .border(
                                1.dp,
                                if (isSelected) ApexPalette.NeonCyan
                                else ApexPalette.BorderGlass,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { profileCategoryFilter = cat }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            cat,
                            color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            val activeProfile = GpuColorProfiles.findById(config.activeProfileId)

            // Active Profile Hero Adjustment Card
            if (activeProfile != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(activeProfile.gradientColors.first()).copy(alpha = 0.35f),
                                    Color(activeProfile.gradientColors.last()).copy(alpha = 0.2f),
                                    ApexPalette.BgElevated
                                )
                            )
                        )
                        .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(activeProfile.iconEmoji, fontSize = 22.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        activeProfile.name,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(ApexPalette.NeonCyan.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            activeProfile.category.uppercase(),
                                            color = ApexPalette.NeonCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                                Text(
                                    activeProfile.description,
                                    color = ApexPalette.TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                onConfigChange(GpuFilterConfig())
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(ApexPalette.BgGlass)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Preset", tint = ApexPalette.TextTertiary, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Master Profile Intensity Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Preset Strength", color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${(config.profileIntensity * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Slider(
                            value = config.profileIntensity,
                            onValueChange = { newIntensity ->
                                onConfigChange(activeProfile.applyWithIntensity(newIntensity))
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = ApexPalette.NeonCyan,
                                activeTrackColor = ApexPalette.NeonCyan,
                                inactiveTrackColor = ApexPalette.BgBase
                            ),
                            modifier = Modifier.fillMaxWidth().height(26.dp)
                        )
                    }

                    // Shortcut to Fine Tune in Color Grading Tab
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { onTabSelected(2) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Fine-Tune in Grading Tab", color = ApexPalette.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Filtered Profiles List
            val filteredProfiles = remember(profileCategoryFilter) {
                if (profileCategoryFilter == "All") GpuColorProfiles.ALL
                else GpuColorProfiles.ALL.filter { it.category == profileCategoryFilter }
            }

            // Grid of Preset Profiles (2 columns)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredProfiles.chunked(2).forEach { rowProfiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowProfiles.forEach { profile ->
                            val isSelected = config.activeProfileId == profile.id
                            GpuColorProfileCard(
                                profile = profile,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        onConfigChange(GpuFilterConfig())
                                    } else {
                                        onConfigChange(profile.applyWithIntensity(1.0f))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowProfiles.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ==========================================
        // TAB 1: STYLISTIC EFFECTS (GPU SHADERS)
        // ==========================================
        if (selectedTab == 1) {
            // Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                effectCategories.forEach { cat ->
                    val isSelected = effectCategoryFilter == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                                else ApexPalette.BgElevated
                            )
                            .border(
                                1.dp,
                                if (isSelected) ApexPalette.NeonCyan
                                else ApexPalette.BorderGlass,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { effectCategoryFilter = cat }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            cat,
                            color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Stylistic Effect Grid
            val filteredEffects = remember(effectCategoryFilter) {
                if (effectCategoryFilter == "All") StylisticEffectType.values().toList()
                else StylisticEffectType.values().filter { it.category == effectCategoryFilter || it == StylisticEffectType.NONE }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredEffects.forEach { effect ->
                    val isSelected = config.stylisticEffect == effect
                    Box(
                        modifier = Modifier
                            .width(108.dp)
                            .height(90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.18f)
                                else ApexPalette.BgElevated
                            )
                            .border(
                                1.5.dp,
                                if (isSelected) ApexPalette.NeonCyan
                                else ApexPalette.BorderGlass,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onConfigChange(
                                    config.copy(
                                        stylisticEffect = effect,
                                        effectParam = effect.defaultParam
                                    )
                                )
                            }
                            .padding(8.dp)
                            .testTag("effect_${effect.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = getEffectIcon(effect),
                                contentDescription = effect.title,
                                tint = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                effect.title,
                                color = if (isSelected) Color.White else ApexPalette.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                effect.category,
                                color = ApexPalette.TextTertiary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // Effect Controls (Intensity & Specific Parameter Slider)
            if (config.stylisticEffect != StylisticEffectType.NONE) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ApexPalette.BgGlass)
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stylistic Intensity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Effect Intensity", color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${(config.stylisticIntensity * 100).toInt()}%", color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Slider(
                        value = config.stylisticIntensity,
                        onValueChange = { onConfigChange(config.copy(stylisticIntensity = it)) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonCyan,
                            activeTrackColor = ApexPalette.NeonCyan,
                            inactiveTrackColor = ApexPalette.BgBase
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("stylistic_intensity_slider")
                    )

                    // Secondary Dynamic Parameter Slider (e.g. Dot Density, Pixel Size, Curvature)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(config.stylisticEffect.paramName, color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${(config.effectParam * 100).toInt()}%", color = ApexPalette.NeonPink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Slider(
                        value = config.effectParam,
                        onValueChange = { onConfigChange(config.copy(effectParam = it)) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonPink,
                            activeTrackColor = ApexPalette.NeonPink,
                            inactiveTrackColor = ApexPalette.BgBase
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("stylistic_param_slider")
                    )
                }
            }
        }

        // ==========================================
        // TAB 2: PARAMETRIC COLOR GRADING
        // ==========================================
        if (selectedTab == 2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ApexPalette.BgGlass)
                    .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Temperature
                ColorGradeSlider(
                    title = "Temperature (White Balance)",
                    value = config.temperature,
                    valueRange = 2000f..8000f,
                    displayValue = "${config.temperature.toInt()}K",
                    accentColor = ApexPalette.NeonAmber,
                    onValueChange = { onConfigChange(config.copy(temperature = it)) },
                    onReset = { onConfigChange(config.copy(temperature = 5000f)) }
                )

                // Tint
                ColorGradeSlider(
                    title = "Tint (Green / Magenta)",
                    value = config.tint,
                    valueRange = -100f..100f,
                    displayValue = "${config.tint.toInt()}",
                    accentColor = ApexPalette.NeonPink,
                    onValueChange = { onConfigChange(config.copy(tint = it)) },
                    onReset = { onConfigChange(config.copy(tint = 0f)) }
                )

                // Exposure
                ColorGradeSlider(
                    title = "Exposure",
                    value = config.exposure,
                    valueRange = -2f..2f,
                    displayValue = String.format("%.1f EV", config.exposure),
                    accentColor = ApexPalette.NeonCyan,
                    onValueChange = { onConfigChange(config.copy(exposure = it)) },
                    onReset = { onConfigChange(config.copy(exposure = 0f)) }
                )

                // Contrast
                ColorGradeSlider(
                    title = "Contrast",
                    value = config.contrast,
                    valueRange = 0.2f..2.2f,
                    displayValue = String.format("%.2fx", config.contrast),
                    accentColor = ApexPalette.NeonPurple,
                    onValueChange = { onConfigChange(config.copy(contrast = it)) },
                    onReset = { onConfigChange(config.copy(contrast = 1.0f)) }
                )

                // Saturation
                ColorGradeSlider(
                    title = "Saturation",
                    value = config.saturation,
                    valueRange = 0f..2.5f,
                    displayValue = String.format("%.2fx", config.saturation),
                    accentColor = ApexPalette.NeonEmerald,
                    onValueChange = { onConfigChange(config.copy(saturation = it)) },
                    onReset = { onConfigChange(config.copy(saturation = 1.0f)) }
                )

                // Brightness
                ColorGradeSlider(
                    title = "Brightness",
                    value = config.brightness,
                    valueRange = -0.8f..0.8f,
                    displayValue = String.format("%.2f", config.brightness),
                    accentColor = Color.White,
                    onValueChange = { onConfigChange(config.copy(brightness = it)) },
                    onReset = { onConfigChange(config.copy(brightness = 0f)) }
                )

                // Vibrance
                ColorGradeSlider(
                    title = "Vibrance",
                    value = config.vibrance,
                    valueRange = -1f..1f,
                    displayValue = String.format("%.2f", config.vibrance),
                    accentColor = ApexPalette.NeonCyan,
                    onValueChange = { onConfigChange(config.copy(vibrance = it)) },
                    onReset = { onConfigChange(config.copy(vibrance = 0f)) }
                )
            }
        }

        // ==========================================
        // TAB 3: 3D LUT LOOKS & PRESETS
        // ==========================================
        if (selectedTab == 3) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // LUT Intensity Slider
                if (config.filterPresetId != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ApexPalette.BgGlass)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("LUT Grade Intensity", color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${(config.filterIntensity * 100).toInt()}%", color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Slider(
                            value = config.filterIntensity,
                            onValueChange = { onConfigChange(config.copy(filterIntensity = it)) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = ApexPalette.NeonCyan,
                                activeTrackColor = ApexPalette.NeonCyan,
                                inactiveTrackColor = ApexPalette.BgBase
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                        )
                    }
                }

                // Presets Horizontal Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Original (None)
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(86.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (config.filterPresetId == null) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                                else ApexPalette.BgElevated
                            )
                            .border(
                                1.5.dp,
                                if (config.filterPresetId == null) ApexPalette.NeonCyan
                                else ApexPalette.BorderGlass,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onConfigChange(config.copy(filterPresetId = null)) }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.NotInterested, contentDescription = null, tint = ApexPalette.TextSecondary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("None", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Manifest Presets
                    manifest.filters.forEach { preset ->
                        val isSelected = config.filterPresetId == preset.id
                        val thumb = lutThumbnails[preset.id]
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(86.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) ApexPalette.NeonCyan.copy(alpha = 0.25f)
                                    else ApexPalette.BgElevated
                                )
                                .border(
                                    1.5.dp,
                                    if (isSelected) ApexPalette.NeonCyan
                                    else ApexPalette.BorderGlass,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onConfigChange(config.copy(filterPresetId = preset.id)) }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (thumb != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = thumb,
                                        contentDescription = preset.name,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.MovieFilter,
                                        contentDescription = null,
                                        tint = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    preset.name,
                                    color = if (isSelected) Color.White else ApexPalette.TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons Row: Reset, Apply to All Clips, Apply to Selected Clip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Reset
            Button(
                onClick = onReset,
                modifier = Modifier
                    .weight(0.8f)
                    .height(44.dp)
                    .testTag("gpu_filter_reset_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApexPalette.BgElevated,
                    contentColor = ApexPalette.TextSecondary
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ApexPalette.BorderGlass)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Apply to All Clips
            Button(
                onClick = onApplyToAllClips,
                modifier = Modifier
                    .weight(1.1f)
                    .height(44.dp)
                    .testTag("gpu_filter_apply_all_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApexPalette.BgElevated,
                    contentColor = ApexPalette.NeonCyan
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("All Clips", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Done / Apply
            Button(
                onClick = onApplyToClip,
                modifier = Modifier
                    .weight(1.3f)
                    .height(44.dp)
                    .testTag("gpu_filter_done_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(10.dp)
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
                        Icon(Icons.Default.Check, contentDescription = null, tint = ApexPalette.BgDeep, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Apply Grade", color = ApexPalette.BgDeep, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorGradeSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reset $title",
                    tint = ApexPalette.TextTertiary,
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .clickable { onReset() }
                )
            }
            Text(displayValue, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = ApexPalette.BgBase
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )
    }
}

private fun getEffectIcon(effect: StylisticEffectType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (effect) {
        StylisticEffectType.NONE -> Icons.Default.NotInterested
        StylisticEffectType.VIGNETTE -> Icons.Default.CenterFocusStrong
        StylisticEffectType.HALFTONE -> Icons.Default.Grain
        StylisticEffectType.TOON, StylisticEffectType.SMOOTH_TOON -> Icons.Default.ColorLens
        StylisticEffectType.SKETCH -> Icons.Default.Draw
        StylisticEffectType.PIXELATION -> Icons.Default.GridOn
        StylisticEffectType.EMBOSS -> Icons.Default.Layers
        StylisticEffectType.SWIRL -> Icons.Default.RotateRight
        StylisticEffectType.BULGE -> Icons.Default.Camera
        StylisticEffectType.GLASS_SPHERE -> Icons.Default.Lens
        StylisticEffectType.KUWAHARA -> Icons.Default.Brush
        StylisticEffectType.POSTERIZE -> Icons.Default.FilterFrames
        StylisticEffectType.CROSSHATCH -> Icons.Default.BorderClear
        StylisticEffectType.SOLARIZE -> Icons.Default.WbSunny
        StylisticEffectType.GAUSSIAN_BLUR -> Icons.Default.BlurOn
        StylisticEffectType.SHARPEN -> Icons.Default.Details
        StylisticEffectType.SEPIA -> Icons.Default.FilterVintage
        StylisticEffectType.INVERT -> Icons.Default.InvertColors
    }
}

@Composable
private fun GpuColorProfileCard(
    profile: GpuColorProfile,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) ApexPalette.BgElevated
                else ApexPalette.BgBase
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .testTag("gpu_profile_${profile.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient Header Tile
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(profile.gradientColors.first()),
                                Color(profile.gradientColors.last())
                            )
                        )
                    )
                    .padding(8.dp)
            ) {
                // Icon Emoji
                Text(
                    profile.iconEmoji,
                    fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                // Category Tag
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        profile.category,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Selected Checkmark Badge
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(ApexPalette.NeonCyan),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Active",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Description info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    profile.name,
                    color = if (isSelected) ApexPalette.NeonCyan else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    profile.description,
                    color = ApexPalette.TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 2,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

