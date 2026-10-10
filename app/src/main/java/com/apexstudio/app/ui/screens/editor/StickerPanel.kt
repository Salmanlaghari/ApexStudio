package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.stickers.StickerEntry
import com.apexstudio.app.data.stickers.StickerImageCache
import com.apexstudio.app.data.stickers.StickerPack
import com.apexstudio.app.ui.theme.ApexPalette
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
 * Shows the bundled openly-licensed PNG pack (`assets/stickers/`, Twemoji
 * CC-BY 4.0 — attribution in the footer). Typing in the search field
 * filters stickers by name / tags / category; category chips narrow the
 * grid further. Tapping a sticker adds it to the project canvas and
 * selects it so the user can immediately drag / resize it.
 */
@Composable
fun StickerPanel(
    onAddSticker: (assetPath: String, category: String, name: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageCache = remember { StickerImageCache(context) }
    val entries = remember { StickerPack.load(context) }
    val categories = remember(entries) { listOf(ALL_CATEGORY) + StickerPack.categories(entries) }
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
            .height(400.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(14.dp)
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

        Spacer(Modifier.height(10.dp))

        // Stickers | Animated tabs (Phase 4: Lottie)
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

        Spacer(Modifier.height(8.dp))

        if (showAnimated) {
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

        Spacer(Modifier.height(8.dp))

        // Category tabs
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(categories) { cat ->
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
                        cat.replaceFirstChar { it.uppercase() },
                        color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

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
                    else "No stickers match \"$query\"",
                    color = ApexPalette.TextSecondary,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { entry ->
                    StickerCell(
                        entry = entry,
                        imageCache = imageCache,
                        onClick = { onAddSticker(entry.assetUri, entry.category, entry.name) }
                    )
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
            .clickable { onClick() },
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

/** Phase 4: animated Lottie sticker grid (original ApexStudio animations). */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.AnimatedStickerGrid(
    onAddSticker: (assetPath: String, category: String, name: String) -> Unit
) {
    val animations = remember {
        listOf(
            "bouncing_ball.json" to "Bouncing Ball",
            "confetti_burst.json" to "Confetti",
            "heart_pop.json" to "Heart Pop",
            "pulse_ring.json" to "Pulse Ring",
            "rotating_star.json" to "Star Spin",
            "swipe_arrow.json" to "Swipe Up"
        )
    }
    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Text(
            "Animated overlays — play live in preview and export.",
            color = ApexPalette.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(animations) { (file, name) ->
                val composition by rememberLottieComposition(
                    LottieCompositionSpec.Asset("lottie/$file")
                )
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ApexPalette.BgElevated)
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
                        .clickable {
                            onAddSticker("lottie/$file", "animated", name)
                        }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        name,
                        color = ApexPalette.TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
