package com.apexstudio.app.ui.screens.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.filter.GpuColorProfile
import com.apexstudio.app.data.filter.GpuColorProfiles
import com.apexstudio.app.data.filter.GpuColorProfileThumbnailGenerator
import com.apexstudio.app.data.filter.GpuFilterConfig
import com.apexstudio.app.data.filter.OneTapLooks
import com.apexstudio.app.data.filter.StylisticEffectType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    onApplyOneTapLook: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var effectCategoryFilter by remember { mutableStateOf("All") }
    val effectCategories = listOf("All", "Artistic", "Cinematic", "Retro", "Distortion", "Stylistic")

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Half height so the preview + playhead stay visible while a
            // filter is applied; the overlay wrapper adds status/nav insets.
            .fillMaxHeight(0.55f)
            .navigationBarsPadding()
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Drag Handle (fixed at the top — stays visible while scrolling)
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ApexPalette.TextTertiary.copy(alpha = 0.5f))
        )

        // Scrollable content. The Reset / All Clips / Apply Grade buttons
        // stay sticky at the bottom (outside this scroll container).
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

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
                        color = ApexPalette.TextPrimary,
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
                    Text("${(splitPosition * 100).toInt()}%", color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
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

        // Featured One-Tap Looks group stays at the top of the filter workspace.
        Text(
            "One-Tap Looks",
            color = ApexPalette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        OneTapLookCard(
            profile = OneTapLooks.VIRAL_HDR,
            selected = config.activeProfileId == OneTapLooks.VIRAL_HDR_ID,
            onClick = { onApplyOneTapLook(OneTapLooks.VIRAL_HDR_ID) }
        )

        // Studio Navigation Tabs — scrollable with single-line labels so
        // labels never wrap ("Stylisti c") on narrow phones.
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = ApexPalette.NeonCyan,
            edgePadding = 0.dp,
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
                        Text("Presets", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
                        Text("Stylistic", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
                        Text("Grading", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
                        Text("Filters", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
                                        color = ApexPalette.TextPrimary,
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
                            Text("${(config.profileIntensity * 100).toInt()}%", color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
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

            // Filtered Profiles List (Phase 2: includes pack profiles; PR-C: + downloaded packs).
            // Pack profiles register async (IO thread); packsReady re-keys the
            // cached list so pack filters appear on first panel open.
            val packContext = LocalContext.current
            var packsReady by remember { mutableStateOf(GpuColorProfiles.hasPackProfiles()) }
            var dlVersion by remember { mutableStateOf(0) }
            val dlPacks by com.apexstudio.app.data.packs.download.PackManager.packsState.collectAsState()
            LaunchedEffect(Unit) {
                // Await the catalog so the downloadable section below is
                // populated before the list first renders.
                com.apexstudio.app.data.packs.download.PackManager.refreshSync(packContext)
                withContext(Dispatchers.IO) {
                    com.apexstudio.app.data.packs.PackLoader.ensurePackProfilesRegistered(packContext)
                    com.apexstudio.app.data.packs.download.DownloadedPacks.ensureDownloadedFilterPacksRegisteredScan(packContext)
                }
                packsReady = true
            }
            // Re-register only when the SET of downloaded pack ids changes —
            // not on every progress tick of an in-flight download.
            // derivedStateOf emits only when the resulting set's *content*
            // changes, so the effect below never restarts spuriously.
            val downloadedPackIds by remember {
                derivedStateOf {
                    dlPacks
                        .filter { it.state == com.apexstudio.app.data.packs.download.PackManager.PackState.Downloaded }
                        .map { it.pack.id }
                        .toSet()
                }
            }
            LaunchedEffect(downloadedPackIds) {
                if (downloadedPackIds.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        com.apexstudio.app.data.packs.download.DownloadedPacks.ensureDownloadedFilterPacksRegisteredScan(packContext)
                    }
                    dlVersion++
                }
            }
            val filteredProfiles = remember(profileCategoryFilter, packsReady, dlVersion) {
                val all = GpuColorProfiles.ALL_WITH_DOWNLOADED
                if (profileCategoryFilter == "All") all
                else all.filter { it.category == profileCategoryFilter }
            }

            val allProfilesForSamples = remember(packsReady, dlVersion) {
                GpuColorProfiles.ALL_WITH_DOWNLOADED
            }
            val unfilteredSample = lutThumbnails[null]
            var profileSampleBitmaps by remember {
                mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
            }
            LaunchedEffect(unfilteredSample, allProfilesForSamples) {
                profileSampleBitmaps = if (unfilteredSample == null) {
                    emptyMap()
                } else {
                    GpuColorProfileThumbnailGenerator.generate(
                        unfilteredSample.asAndroidBitmap(),
                        allProfilesForSamples
                    )
                }
            }

            // Horizontal scrollable strip of Preset Profiles (video stays visible above)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filteredProfiles, key = { it.id }) { profile ->
                    val isSelected = config.activeProfileId == profile.id
                    GpuColorProfileCard(
                        profile = profile,
                        isSelected = isSelected,
                        preview = profileSampleBitmaps[profile.id],
                        onClick = {
                            if (isSelected) {
                                onConfigChange(GpuFilterConfig())
                            } else {
                                onConfigChange(profile.applyWithIntensity(1.0f))
                            }
                        },
                        modifier = Modifier.width(140.dp)
                    )
                }
            }

            // PR-C: Downloadable filter packs (on-demand). This is the live
            // gating proof for PackManager.requirePack(): pack content is
            // inaccessible until downloaded; the UI prompts download instead.
            // Only actually-downloadable packs are listed (coming-soon packs
            // live in the Pack Store, never as dead buttons here).
            val downloadableFilterPacks = dlPacks.filter { it.pack.category == "filters" && it.pack.isAvailable }
            if (downloadableFilterPacks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "More Filters — Downloadable",
                    color = ApexPalette.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                downloadableFilterPacks.forEach { item ->
                    DownloadableFilterPackRow(item = item)
                    Spacer(Modifier.height(6.dp))
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
                // The featured look's slider scales its full recipe; ordinary LUTs keep LUT-only intensity.
                val viralHdrProfile = GpuColorProfiles.findById(config.activeProfileId)
                    ?.takeIf { it.id == OneTapLooks.VIRAL_HDR_ID }
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
                            Text(if (viralHdrProfile != null) "Look Intensity" else "Filter Intensity", color = ApexPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val intensity = if (viralHdrProfile != null) config.profileIntensity else config.filterIntensity
                            Text("${(intensity * 100).toInt()}%", color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Slider(
                            value = if (viralHdrProfile != null) config.profileIntensity else config.filterIntensity,
                            onValueChange = { intensity ->
                                onConfigChange(viralHdrProfile?.applyWithIntensity(intensity)
                                    ?: config.copy(filterIntensity = intensity))
                            },
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
                            .clickable { onConfigChange(config.copy(filterPresetId = null, activeProfileId = null, profileIntensity = 1f)) }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.NotInterested, contentDescription = null, tint = ApexPalette.TextSecondary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("None", color = ApexPalette.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                                .clickable { onConfigChange(config.copy(filterPresetId = preset.id, activeProfileId = null, profileIntensity = 1f)) }
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

        } // end scrollable content

        // Action Buttons Row: Reset, Apply to All Clips, Apply to Selected Clip
        // — sticky at the bottom, above the navigation bar.
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
private fun OneTapLookCard(
    profile: GpuColorProfile,
    selected: Boolean,
    onClick: () -> Unit
) {
    val gradient = profile.gradientColors.map { argb -> Color(argb) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(gradient.map { it.copy(alpha = 0.22f) }))
            .border(
                1.5.dp,
                if (selected) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("one-tap-look-${profile.id}")
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            Text("HDR", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(profile.name, color = ApexPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(profile.description, color = ApexPalette.TextSecondary, fontSize = 10.sp, maxLines = 2)
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Viral HDR selected", tint = ApexPalette.NeonCyan)
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
    preview: ImageBitmap?,
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
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = "${profile.name} sample preview",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().testTag("gpu-profile-sample-${profile.id}")
                    )
                }
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
                        color = ApexPalette.TextPrimary,
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
                    color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
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


/**
 * PR-C: one downloadable filter pack row inside the filter panel.
 *
 * Live gating proof for [com.apexstudio.app.data.packs.download.PackManager.requirePack]:
 * the pack's filters are inaccessible until downloaded — the UI shows a
 * download prompt instead of the feature. No dead buttons: every state has a
 * working action (Download / Cancel / Delete / Retry), coming-soon packs are
 * not listed here at all.
 */
@Composable
private fun DownloadableFilterPackRow(
    item: com.apexstudio.app.data.packs.download.PackManager.PackUiItem
) {
    val context = LocalContext.current
    val pack = item.pack
    // The gate: content is only usable when the pack is installed.
    val unlocked = com.apexstudio.app.data.packs.download.PackManager.requirePack(context, pack.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                pack.name,
                color = ApexPalette.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (unlocked) "Downloaded — filters are in the list above"
                else "${com.apexstudio.app.data.packs.download.PackManifestRepo.formatSize(pack.sizeBytes)} • tap Download to unlock ${pack.name}",
                color = ApexPalette.TextSecondary,
                fontSize = 11.sp,
                maxLines = 2
            )
            val st = item.state
            if (st is com.apexstudio.app.data.packs.download.PackManager.PackState.Downloading) {
                Spacer(Modifier.height(6.dp))
                if (st.fraction >= 0) {
                    LinearProgressIndicator(
                        progress = { st.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = ApexPalette.NeonCyan,
                        trackColor = ApexPalette.BgBase
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ApexPalette.NeonCyan)
                }
            }
            if (st is com.apexstudio.app.data.packs.download.PackManager.PackState.Failed) {
                Text(st.reason, color = ApexPalette.Danger, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        when (val st = item.state) {
            is com.apexstudio.app.data.packs.download.PackManager.PackState.NotDownloaded,
            is com.apexstudio.app.data.packs.download.PackManager.PackState.UpdateAvailable ->
                TextButton(onClick = {
                    com.apexstudio.app.data.packs.download.PackManager.download(context, pack.id)
                }) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = ApexPalette.NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (st is com.apexstudio.app.data.packs.download.PackManager.PackState.UpdateAvailable) "Update" else "Download", color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            is com.apexstudio.app.data.packs.download.PackManager.PackState.Downloading ->
                TextButton(onClick = {
                    com.apexstudio.app.data.packs.download.PackManager.cancel(context, pack.id)
                }) {
                    Text("Cancel", color = ApexPalette.TextSecondary, fontSize = 12.sp)
                }
            is com.apexstudio.app.data.packs.download.PackManager.PackState.Downloaded ->
                Text("Added ✓", color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            is com.apexstudio.app.data.packs.download.PackManager.PackState.Failed ->
                TextButton(onClick = {
                    com.apexstudio.app.data.packs.download.PackManager.download(context, pack.id)
                }) {
                    Text("Retry", color = ApexPalette.NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            is com.apexstudio.app.data.packs.download.PackManager.PackState.ComingSoon -> { /* not listed */ }
        }
    }
}
