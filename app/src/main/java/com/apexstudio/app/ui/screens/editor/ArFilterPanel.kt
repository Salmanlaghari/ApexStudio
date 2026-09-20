package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.ar.ArFilterCatalog
import com.apexstudio.app.data.ar.ArFilterCategory
import com.apexstudio.app.data.ar.ArFilterPreset
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Filter panel for AR/AI face filters, beauty retouching, anime styles,
 * and festival greeting frames (e.g., Ganesh Chaturthi & Diya overlays).
 */
@Composable
fun ArFilterPanel(
    activeFilterId: String?,
    intensity: Float,
    customText: String,
    onFilterSelected: (String?) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onCustomTextChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(ArFilterCategory.ALL) }
    val filters = remember(selectedCategory) {
        if (selectedCategory == ArFilterCategory.ALL) {
            ArFilterCatalog.FILTERS
        } else {
            ArFilterCatalog.FILTERS.filter { it.category == selectedCategory }
        }
    }
    val activePreset = remember(activeFilterId) {
        ArFilterCatalog.getFilterById(activeFilterId)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color(0xFF0F0F1A))
            .border(1.dp, Color(0xFF232338), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ApexPalette.NeonCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ApexPalette.NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "AI / AR Face & Festival Filters",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeFilterId != null) {
                    Text(
                        text = "Reset",
                        color = ApexPalette.NeonPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onFilterSelected(null) }
                            .padding(4.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(2.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Category Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ArFilterCategory.values().forEach { cat ->
                val isSel = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSel) ApexPalette.NeonCyan.copy(alpha = 0.25f)
                            else Color(0xFF1B1B2A)
                        )
                        .border(
                            1.dp,
                            if (isSel) ApexPalette.NeonCyan else Color(0xFF2C2C40),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = cat.icon,
                            contentDescription = null,
                            tint = if (isSel) ApexPalette.NeonCyan else Color(0xFF9CA3AF),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = cat.title,
                            color = if (isSel) Color.White else Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Horizontal Filter Cards Carousel
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            // "None" option
            item {
                ArFilterThumbnailCard(
                    title = "None",
                    subtitle = "Original",
                    accentColor = Color(0xFF4B5563),
                    isSelected = activeFilterId == null,
                    onClick = { onFilterSelected(null) }
                )
            }

            items(filters) { item ->
                ArFilterThumbnailCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    accentColor = Color(item.accentHex),
                    isSelected = activeFilterId == item.id,
                    onClick = {
                        onFilterSelected(item.id)
                        onIntensityChange(item.defaultIntensity)
                        if (item.hasEditableText && customText.isBlank()) {
                            onCustomTextChange(item.defaultGreetingText)
                        }
                    }
                )
            }
        }

        // Editable Text input for Festival & Occasion Overlays
        if (activePreset?.hasEditableText == true) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF161626))
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Customize Festival Greeting Text:",
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextField(
                    value = customText,
                    onValueChange = onCustomTextChange,
                    placeholder = {
                        Text(
                            activePreset.defaultGreetingText,
                            color = Color(0xFF6B7280),
                            fontSize = 13.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F0F1A),
                        unfocusedContainerColor = Color(0xFF0F0F1A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color(0xFFF59E0B),
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }

        // Intensity Slider
        if (activeFilterId != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Intensity",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    modifier = Modifier.width(60.dp)
                )
                Slider(
                    value = intensity,
                    onValueChange = onIntensityChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = ApexPalette.NeonCyan,
                        activeTrackColor = ApexPalette.NeonCyan,
                        inactiveTrackColor = Color(0xFF222238)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(intensity * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(40.dp)
                )
            }
        }
    }
}

@Composable
private fun ArFilterThumbnailCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(82.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = if (isSelected) 0.35f else 0.15f),
                            Color(0xFF141422)
                        )
                    )
                )
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) accentColor else Color(0xFF26263A),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (title == "None") Icons.Default.Block else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = title.take(9),
                    color = if (isSelected) Color.White else Color(0xFFD1D5DB),
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(3.dp))
        Text(
            text = title,
            color = if (isSelected) Color.White else Color(0xFF9CA3AF),
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
