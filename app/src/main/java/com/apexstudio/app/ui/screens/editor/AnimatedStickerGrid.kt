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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.stickers.AnimatedStickerEntry
import com.apexstudio.app.data.stickers.AnimatedStickerRepository
import com.apexstudio.app.ui.theme.ApexPalette
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * "Animated" tab of the sticker panel.
 *
 * Renders two deliberately separate sources so nothing is duplicated:
 * 1. the seven ORIGINAL animations already bundled in `assets/lottie/`,
 * 2. the 22-entry animated pack loaded from
 *    `assets/stickers/animated/manifest.json` via [AnimatedStickerRepository].
 *
 * The pack is manifest-driven instead of the old hard-coded Kotlin list,
 * so adding a animation is an assets-only change and the picker gets
 * category chips for free. Licence and provenance for that pack are
 * recorded in `assets/stickers/ATTRIBUTION.md`.
 *
 * Tapping any cell hands back `path + category + name`; the path is the
 * same string `LottieFrameCache.isLottieAsset` accepts, so the canvas
 * preview and the export shader load exactly the bytes previewed here.
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.AnimatedStickerGrid(
    onAddSticker: (assetPath: String, category: String, name: String) -> Unit
) {
    val context = LocalContext.current
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
    val packed = remember(context) { AnimatedStickerRepository.entries(context) }
    val categories = remember(packed) { AnimatedStickerRepository.categories(packed) }
    var selectedCategory by remember(packed) {
        mutableStateOf(categories.firstOrNull() ?: "")
    }
    val visible = remember(packed, selectedCategory) {
        packed.filter { it.category == selectedCategory }
    }

    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Text(
            "Animated stickers play live in preview and export.",
            color = ApexPalette.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            "Original animations",
            color = ApexPalette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            items(originals, key = { it.first }) { (file, name) ->
                val path = "lottie/$file"
                AnimationCell(assetPath = path, name = name) {
                    onAddSticker(path, "animated", name)
                }
            }
        }

        if (packed.isNotEmpty() && categories.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Animated pack — ${packed.size} stickers",
                color = ApexPalette.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories, key = { it }) { cat ->
                    val selected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) ApexPalette.NeonCyan else ApexPalette.BgElevated
                            )
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            cat,
                            color = if (selected) androidx.compose.ui.graphics.Color(0xFF0A0E1A)
                            else ApexPalette.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visible, key = { it.id }) { entry ->
                    val path = AnimatedStickerRepository.assetUri(entry)
                    AnimationCell(assetPath = path, name = entry.name) {
                        onAddSticker(path, entry.category, entry.name)
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Animated pack unavailable — see assets/stickers/ATTRIBUTION.md.",
                color = ApexPalette.TextSecondary,
                fontSize = 11.sp
            )
        }

        Text(
            "Licence: Lottie Simple License • source recorded in assets/stickers/ATTRIBUTION.md",
            color = ApexPalette.TextSecondary,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** One 72dp looping animation cell with a caption. */
@Composable
private fun AnimationCell(assetPath: String, name: String, onClick: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(assetPath))
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ApexPalette.BgElevated)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
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
