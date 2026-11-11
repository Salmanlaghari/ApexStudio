package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import com.apexstudio.app.data.stickers.AnimatedStickerEntry
import com.apexstudio.app.data.stickers.AnimatedStickerRepository
import com.apexstudio.app.data.stickers.DownloadedStickerCatalog
import com.apexstudio.app.data.stickers.StickerAssetResolver
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

private enum class StickerSourceTab { ON_DEMAND, BUNDLED }

/** On-demand Lottie packs plus the small set of animations already bundled in the APK. */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.AnimatedStickerGrid(
    onAddSticker: (assetPath: String, category: String, name: String) -> Unit,
    canAddStickers: Boolean = true
) {
    val context = LocalContext.current.applicationContext
    val packItems by PackManager.packsState.collectAsState()
    val isLoading by PackManager.isLoading.collectAsState()
    val manifestError by PackManager.manifestError.collectAsState()
    val downloadError by PackManager.downloadError.collectAsState()
    var sourceTab by remember { mutableStateOf(StickerSourceTab.ON_DEMAND) }
    var downloadedCatalog by remember { mutableStateOf(DownloadedStickerCatalog(emptyList(), emptyList())) }

    val stickerPacks = remember(packItems) { packItems.filter { it.pack.category == "stickers" } }
    val installedPacks = remember(stickerPacks) {
        stickerPacks.filter {
            it.state == PackManager.PackState.Downloaded ||
                it.state == PackManager.PackState.UpdateAvailable
        }
    }
    val bundled = remember(context) { AnimatedStickerRepository.entries(context) }
    val bundledCategories = remember(bundled) { AnimatedStickerRepository.categories(bundled) }
    val downloadedCategories = remember(downloadedCatalog.entries) {
        AnimatedStickerRepository.categories(downloadedCatalog.entries)
    }
    var selectedBundledCategory by remember(bundledCategories) {
        mutableStateOf(bundledCategories.firstOrNull().orEmpty())
    }
    var selectedDownloadedCategory by remember(downloadedCategories) {
        mutableStateOf(downloadedCategories.firstOrNull().orEmpty())
    }
    LaunchedEffect(context) { PackManager.refreshSync(context) }
    LaunchedEffect(installedPacks) {
        downloadedCatalog = AnimatedStickerRepository.loadDownloaded(context, installedPacks)
    }
    LaunchedEffect(bundledCategories) {
        if (selectedBundledCategory !in bundledCategories) {
            selectedBundledCategory = bundledCategories.firstOrNull().orEmpty()
        }
    }
    LaunchedEffect(downloadedCategories) {
        if (selectedDownloadedCategory !in downloadedCategories) {
            selectedDownloadedCategory = downloadedCategories.firstOrNull().orEmpty()
        }
    }

    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Text(
            "Animated stickers play in preview and export using the same Lottie file.",
            color = ApexPalette.TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 5.dp)
        )
        if (!canAddStickers) {
            Text(
                "Select a main-track video clip before adding a sticker.",
                color = androidx.compose.ui.graphics.Color(0xFFFFB86B),
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            StickerSourceTab.entries.forEach { tab ->
                val selected = sourceTab == tab
                val title = when (tab) {
                    StickerSourceTab.ON_DEMAND -> "On-demand · ${stickerPacks.sumOf { it.pack.itemCount }}"
                    StickerSourceTab.BUNDLED -> "Bundled · ${7 + bundled.size}"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) ApexPalette.NeonCyan else ApexPalette.BgElevated)
                        .clickable { sourceTab = tab }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        color = if (selected) androidx.compose.ui.graphics.Color(0xFF0A0E1A) else ApexPalette.TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        when (sourceTab) {
            StickerSourceTab.ON_DEMAND -> {
                Text(
                    if (stickerPacks.isNotEmpty()) {
                        "${stickerPacks.sumOf { it.pack.itemCount }} original animated stickers · download only the packs you want"
                    } else "Animated sticker packs",
                    color = ApexPalette.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                if (stickerPacks.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(stickerPacks, key = { it.pack.id }) { item ->
                            val integrityError = downloadedCatalog.errors.any {
                                it.startsWith("${item.pack.name}:")
                            }
                            StickerPackDownloadCard(
                                item = item,
                                integrityError = integrityError,
                                onDownload = { PackManager.download(context, item.pack.id) },
                                onCancel = { PackManager.cancel(context, item.pack.id) },
                                onRepair = { PackManager.reinstall(context, item.pack.id) }
                            )
                        }
                    }
                } else if (isLoading) {
                    Text("Loading sticker packs…", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                } else {
                    Text(
                        manifestError ?: "No downloadable sticker packs are available right now.",
                        color = ApexPalette.TextSecondary,
                        fontSize = 10.sp
                    )
                    Box(
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp))
                            .background(ApexPalette.BgElevated).clickable { PackManager.refresh(context) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) { Text("Retry catalog", color = ApexPalette.TextPrimary, fontSize = 10.sp) }
                }
                if (manifestError != null && stickerPacks.isNotEmpty()) {
                    Text("Using the cached pack catalog; refresh failed.", color = ApexPalette.TextSecondary, fontSize = 9.sp)
                }
                downloadError?.let {
                    Text(it, color = androidx.compose.ui.graphics.Color(0xFFFF7A7A), fontSize = 9.sp)
                }
                downloadedCatalog.errors.forEach { error ->
                    Text(error, color = androidx.compose.ui.graphics.Color(0xFFFF7A7A), fontSize = 9.sp)
                }

                if (downloadedCatalog.entries.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Download a pack above to browse its stickers. Every file is size- and SHA-256-checked before it appears here.",
                        color = ApexPalette.TextSecondary,
                        fontSize = 10.sp
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ready to add · ${downloadedCatalog.entries.size} verified stickers",
                        color = ApexPalette.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    StickerCategoryChips(
                        categories = downloadedCategories,
                        selected = selectedDownloadedCategory,
                        onSelect = { selectedDownloadedCategory = it }
                    )
                    val visible = remember(downloadedCatalog.entries, selectedDownloadedCategory) {
                        downloadedCatalog.entries.filter { it.category == selectedDownloadedCategory }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(visible, key = { it.id }) { entry ->
                            val path = AnimatedStickerRepository.assetUri(entry)
                            AnimationCell(path, entry.name, enabled = canAddStickers) {
                                onAddSticker(path, entry.category, entry.name)
                            }
                        }
                    }
                }
            }
            StickerSourceTab.BUNDLED -> {
                val originals = remember {
                    listOf(
                        "bouncing_ball.json" to "Bouncing Ball",
                        "confetti_burst.json" to "Confetti",
                        "heart_pop.json" to "Heart Pop",
                        "pulse_ring.json" to "Pulse Ring",
                        "rotating_star.json" to "Star Spin",
                        "swipe_arrow.json" to "Swipe Up",
                        "walking_cat.json" to "Walking Cat"
                    )
                }
                Text("Original animations", color = ApexPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    items(originals, key = { it.first }) { (file, name) ->
                        val path = "lottie/$file"
                        AnimationCell(path, name, enabled = canAddStickers) {
                            onAddSticker(path, "animated", name)
                        }
                    }
                }
                if (bundled.isNotEmpty() && bundledCategories.isNotEmpty()) {
                    Text(
                        "Bundled animated pack · ${bundled.size} stickers",
                        color = ApexPalette.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    StickerCategoryChips(
                        categories = bundledCategories,
                        selected = selectedBundledCategory,
                        onSelect = { selectedBundledCategory = it }
                    )
                    val visible = remember(bundled, selectedBundledCategory) {
                        bundled.filter { it.category == selectedBundledCategory }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(visible, key = { it.id }) { entry ->
                            val path = AnimatedStickerRepository.assetUri(entry)
                            AnimationCell(path, entry.name, enabled = canAddStickers) {
                                onAddSticker(path, entry.category, entry.name)
                            }
                        }
                    }
                } else {
                    Text("Bundled animated pack unavailable — see assets/stickers/ATTRIBUTION.md.", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                }
                Text(
                    "Bundled pack: Lottie Simple License. On-demand pack artwork is original to ApexStudio; see pack metadata for sources.",
                    color = ApexPalette.TextSecondary,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StickerCategoryChips(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        items(categories, key = { it }) { category ->
            val isSelected = category == selected
            Box(
                modifier = Modifier.padding(vertical = 3.dp).clip(RoundedCornerShape(50))
                    .background(if (isSelected) ApexPalette.NeonCyan else ApexPalette.BgElevated)
                    .clickable { onSelect(category) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    category.replaceFirstChar { it.uppercase() },
                    color = if (isSelected) androidx.compose.ui.graphics.Color(0xFF0A0E1A) else ApexPalette.TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun StickerPackDownloadCard(
    item: PackManager.PackUiItem,
    integrityError: Boolean = false,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRepair: () -> Unit
) {
    val pack = item.pack
    val downloading = item.state as? PackManager.PackState.Downloading
    val actionLabel: String
    val actionEnabled: Boolean
    val action: () -> Unit
    when {
        integrityError -> { actionLabel = "Repair"; actionEnabled = true; action = onRepair }
        downloading != null -> { actionLabel = "Cancel"; actionEnabled = true; action = onCancel }
        item.state == PackManager.PackState.NotDownloaded -> { actionLabel = "Download"; actionEnabled = true; action = onDownload }
        item.state == PackManager.PackState.UpdateAvailable -> { actionLabel = "Update"; actionEnabled = true; action = onDownload }
        item.state is PackManager.PackState.Failed -> { actionLabel = "Retry"; actionEnabled = true; action = onDownload }
        item.state == PackManager.PackState.ComingSoon -> { actionLabel = "Coming soon"; actionEnabled = false; action = {} }
        else -> { actionLabel = "Installed"; actionEnabled = false; action = {} }
    }
    Column(
        modifier = Modifier.width(154.dp).clip(RoundedCornerShape(9.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(9.dp))
            .padding(7.dp)
    ) {
        Text(pack.name, color = ApexPalette.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${pack.itemCount} stickers · ${PackManifestRepo.formatSize(pack.sizeBytes)}",
            color = ApexPalette.TextSecondary,
            fontSize = 9.sp,
            maxLines = 1
        )
        if (downloading != null) {
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(ApexPalette.BgDeep)) {
                if (downloading.fraction >= 0f) {
                    Box(Modifier.fillMaxWidth(downloading.fraction).fillMaxHeight().background(ApexPalette.NeonCyan))
                }
            }
            Text(
                if (downloading.totalBytes > 0L) "${(downloading.fraction * 100).toInt()}% downloaded" else "Downloading…",
                color = ApexPalette.TextSecondary,
                fontSize = 8.sp
            )
        } else {
            Text(
                when {
                    integrityError -> "File integrity check failed"
                    item.state is PackManager.PackState.Failed -> (item.state as PackManager.PackState.Failed).reason
                    else -> pack.description
                },
                color = if (integrityError) androidx.compose.ui.graphics.Color(0xFFFF7A7A) else ApexPalette.TextSecondary,
                fontSize = 8.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .background(if (actionEnabled) ApexPalette.BgDeep else ApexPalette.BgSurface)
                .clickable(enabled = actionEnabled, onClick = action)
                .padding(vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(actionLabel, color = if (actionEnabled) ApexPalette.NeonCyan else ApexPalette.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** One looping animation cell; downloaded files use the same resolved JSON as export. */
@Composable
private fun AnimationCell(assetPath: String, name: String, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val composition by rememberLottieComposition(StickerAssetResolver.compositionSpec(context, assetPath))
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size(62.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(name, color = ApexPalette.TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
