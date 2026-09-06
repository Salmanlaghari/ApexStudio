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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.ApexPalette

data class StickerCategory(
    val id: String,
    val name: String,
    val items: List<String>
)

private val STICKER_CATEGORIES = listOf(
    StickerCategory("emoji", "Emoji", listOf("🔥", "⚡", "✨", "💥", "❤️", "😍", "🎉", "👑", "🚀", "💯", "😎", "🍿")),
    StickerCategory("3d", "3D", listOf("💎", "🔮", "🏆", "🎲", "🛸", "👾", "🤖", "⭐", "🌟", "💡")),
    StickerCategory("shapes", "Shapes", listOf("⭕", "⏹️", "🔺", "🔷", "🟩", "⭐", "🖤", "🤍", "🌐", "🏁")),
    StickerCategory("arrows", "Arrows", listOf("➡️", "⬅️", "⬆️", "⬇️", "↗️", "↘️", "🔄", "🔁", "⚡", "🎯")),
    StickerCategory("social", "Social", listOf("👍", "💬", "🔔", "📸", "🎥", "🎬", "📍", "📢", "💬", "❤️")),
    StickerCategory("love", "Love", listOf("❤️", "💖", "💕", "💘", "🌹", "💌", "💝", "💋", "🌸", "💐")),
    StickerCategory("travel", "Travel", listOf("✈️", "🏖️", "🏔️", "🗺️", "🗼", "🗽", "⛺", "🧳", "🌅", "🌋")),
    StickerCategory("gaming", "Gaming", listOf("🎮", "🕹️", "👾", "🎯", "🏆", "⚔️", "🛡️", "🔥", "⚡", "👑")),
    StickerCategory("reaction", "Reaction", listOf("😱", "🤯", "🥳", "🥺", "🥶", "🥸", "🤡", "👻", "👽", "💩")),
    StickerCategory("decorative", "Decorative", listOf("✨", "💫", "🎨", "🎭", "🎗️", "🎀", "🎈", "🎁", "🪄", "🔮"))
)

@Composable
fun StickerPanel(
    onAddSticker: (symbol: String, category: String, name: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("emoji") }
    val activeCategory = STICKER_CATEGORIES.firstOrNull { it.id == selectedCategory } ?: STICKER_CATEGORIES.first()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
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
                "Stickers & Elements",
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

        // Category tabs
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(STICKER_CATEGORIES) { cat ->
                val isSelected = cat.id == selectedCategory
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
                        .clickable { selectedCategory = cat.id }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        cat.name,
                        color = if (isSelected) ApexPalette.NeonCyan else ApexPalette.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Sticker Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activeCategory.items) { item ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ApexPalette.BgBase)
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(10.dp))
                        .clickable {
                            onAddSticker(item, activeCategory.name, "Sticker_$item")
                            onClose()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item,
                        fontSize = 26.sp
                    )
                }
            }
        }
    }
}
