package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.text.TextPreset
import com.apexstudio.app.data.text.TextPresetEngine
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.ui.theme.ApexPalette

enum class TextPanelTab {
    EDIT,
    ANIMATION,
    PRESETS
}

data class TextAnimationOption(
    val id: String,
    val label: String,
    val desc: String
)

val TEXT_ANIMATIONS = listOf(
    TextAnimationOption("NONE", "None", "Static subtitle"),
    TextAnimationOption("FADE", "Fade In", "Smooth opacity dissolve"),
    TextAnimationOption("POP", "Pop & Bounce", "Dynamic spring zoom-in"),
    TextAnimationOption("TYPEWRITER", "Typewriter", "Letter by letter reveal"),
    TextAnimationOption("SLIDE_UP", "Slide Up", "Motion slide from bottom"),
    TextAnimationOption("PULSE", "Pulse Loop", "Rhythmic breathing scale"),
    TextAnimationOption("BOUNCE", "Bounce", "Playful vertical spring")
)

@Composable
fun TextPanel(
    overlays: List<TextOverlay>,
    selectedId: String?,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onColorChange: (Long) -> Unit,
    onBgChange: (Long?) -> Unit,
    onSizeChange: (Float) -> Unit,
    onDelete: (String) -> Unit,
    onApplyPreset: (TextPreset) -> Unit = {},
    onAnimationChange: (String) -> Unit = {},
    onClose: () -> Unit
) {
    val selected = overlays.firstOrNull { it.id == selectedId }
    var activeTab by remember { mutableStateOf(TextPanelTab.EDIT) }
    var selectedCategory by remember { mutableStateOf(TextPresetEngine.categories.first()) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .heightIn(max = 360.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Text & Titles",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "Close text",
                tint = ApexPalette.NeonCyan,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable { onClose() }
                    .padding(3.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        // Caption chips: one pill per caption + add button
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ApexPalette.NeonCyan.copy(alpha = 0.15f))
                        .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(8.dp))
                        .clickable { onAdd() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Add, null,
                            tint = ApexPalette.NeonCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Add",
                            color = ApexPalette.NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            items(overlays) { o ->
                val sel = o.id == selectedId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (sel) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                            else ApexPalette.BgElevated
                        )
                        .border(
                            1.dp,
                            if (sel) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(o.id) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        o.text.ifBlank { "Text" },
                        color = if (sel) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (selected == null) {
            Text(
                "Tap + Add to place a caption on the video. Drag to reposition.",
                color = ApexPalette.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            return@Column
        }

        // Section Tabs: Edit, Animation, Presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ApexPalette.BgElevated)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                TextPanelTab.EDIT to "Edit & Style",
                TextPanelTab.ANIMATION to "Animation",
                TextPanelTab.PRESETS to "Presets (500+)"
            ).forEach { (tab, label) ->
                val isSel = activeTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) ApexPalette.NeonCyan else Color.Transparent)
                        .clickable { activeTab = tab }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSel) Color(0xFF0A0E1A) else ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (activeTab) {
            TextPanelTab.EDIT -> {
                OutlinedTextField(
                    value = selected.text,
                    onValueChange = onTextChange,
                    singleLine = false,
                    minLines = 1,
                    maxLines = 2,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 13.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ApexPalette.NeonCyan,
                        unfocusedBorderColor = ApexPalette.BorderGlass,
                        focusedContainerColor = ApexPalette.BgElevated,
                        unfocusedContainerColor = ApexPalette.BgElevated,
                        cursorColor = ApexPalette.NeonCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Text colour swatches
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Colour",
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(48.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        textColors().forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(c.toInt()))
                                    .border(
                                        2.dp,
                                        if (selected.colorArgb == c) ApexPalette.NeonCyan
                                        else ApexPalette.BorderGlass,
                                        CircleShape
                                    )
                                    .clickable { onColorChange(c) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected.colorArgb == c) {
                                    Icon(
                                        Icons.Default.Check, null,
                                        tint = Color(0xFF0A0E1A),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Background Pill Options
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Pill",
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(48.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pillOptions().forEach { (label, argb) ->
                            val sel = selected.bgArgb == argb
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (sel) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                                        else ApexPalette.BgElevated
                                    )
                                    .border(
                                        1.dp,
                                        if (sel) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onBgChange(argb) }
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (sel) ApexPalette.NeonCyan else ApexPalette.TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Size Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Size",
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(48.dp)
                    )
                    Slider(
                        value = selected.sizeScale.coerceIn(0.4f, 3f),
                        onValueChange = onSizeChange,
                        valueRange = 0.4f..3f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = ApexPalette.NeonCyan,
                            activeTrackColor = ApexPalette.NeonCyan,
                            inactiveTrackColor = ApexPalette.BgElevated
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ApexPalette.Danger.copy(alpha = 0.15f))
                            .border(1.dp, ApexPalette.Danger, RoundedCornerShape(8.dp))
                            .clickable { onDelete(selected.id) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Delete, null,
                                tint = ApexPalette.Danger,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Delete",
                                color = ApexPalette.Danger,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            TextPanelTab.ANIMATION -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Text Motion & In-Animations",
                        color = ApexPalette.NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TEXT_ANIMATIONS.forEach { opt ->
                        val isSel = selected.animationType.equals(opt.id, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSel) ApexPalette.NeonCyan.copy(alpha = 0.18f)
                                    else ApexPalette.BgElevated
                                )
                                .border(
                                    1.dp,
                                    if (isSel) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onAnimationChange(opt.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        opt.label,
                                        color = if (isSel) ApexPalette.NeonCyan else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        opt.desc,
                                        color = ApexPalette.TextTertiary,
                                        fontSize = 10.sp
                                    )
                                }
                                if (isSel) {
                                    Icon(
                                        Icons.Default.Check, null,
                                        tint = ApexPalette.NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            TextPanelTab.PRESETS -> {
                // Preset Categories
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(TextPresetEngine.categories) { cat ->
                        val isSel = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) ApexPalette.NeonCyan else ApexPalette.BgElevated)
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                cat,
                                color = if (isSel) Color(0xFF0A0E1A) else ApexPalette.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Presets in category
                val categoryPresets = TextPresetEngine.getPresetsForCategory(selectedCategory)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categoryPresets) { preset ->
                        val isCurrent = selected.presetId == preset.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (preset.bgArgb != null) Color(preset.bgArgb.toInt())
                                    else ApexPalette.BgElevated
                                )
                                .border(
                                    1.5.dp,
                                    if (isCurrent) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onApplyPreset(preset) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                preset.name,
                                color = Color(preset.colorArgb.toInt()),
                                fontSize = 11.sp,
                                fontWeight = if (preset.isBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (preset.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun textColors(): List<Long> = listOf(
    0xFFFFFFFFL, 0xFF0A0E1A, 0xFF00E5FF, 0xFF7C4DFF,
    0xFFFF2D55, 0xFFFFC400, 0xFF00C853, 0xFF2979FF
)

private fun pillOptions(): List<Pair<String, Long?>> = listOf(
    "None" to null,
    "Black" to 0xCC000000L,
    "White" to 0xE6FFFFFFL,
    "Cyan" to 0xB300E5FFL
)
