package com.apexstudio.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apexstudio.app.ui.theme.ApexPalette

data class BottomNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false
)

@Composable
fun BottomNavBar(
    current: String,
    onSelect: (String) -> Unit,
    items: List<BottomNavItem> = defaultNavItems(current)
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        ApexPalette.BgDeep.copy(alpha = 0.95f),
                        ApexPalette.BgDeep
                    )
                )
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ApexPalette.BgGlass)
                .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(22.dp))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (item in items) {
                BottomNavCell(item, item.id == current, onSelect = { onSelect(item.id) })
            }
        }
    }
}

@Composable
private fun BottomNavCell(item: BottomNavItem, isActive: Boolean, onSelect: () -> Unit) {
    val tint by animateColorAsState(
        if (isActive) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
        label = "nav-tint"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp)
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ApexPalette.NeonCyan.copy(alpha = 0.35f),
                                    ApexPalette.NeonCyan.copy(alpha = 0.0f)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ApexPalette.NeonCyan.copy(alpha = 0.18f))
                        .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(50))
                )
            }
            Icon(item.icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            item.label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        )
    }
}

fun defaultNavItems(current: String): List<BottomNavItem> = listOf(
    BottomNavItem("home", "Home", Icons.Default.Home, current == "home"),
    BottomNavItem("edit", "Edit", Icons.Default.Tune, current == "edit"),
    BottomNavItem("color", "Color", Icons.Default.Palette, current == "color"),
    BottomNavItem("audio", "Audio", Icons.Default.GraphicEq, current == "audio")
)