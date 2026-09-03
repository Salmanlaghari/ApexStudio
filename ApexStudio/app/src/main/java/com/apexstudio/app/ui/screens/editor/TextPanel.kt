package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * Bottom-sheet caption editor. Lists every caption on the selected
 * clip (tap to select, drag on the preview to position), edits the
 * selected caption's text / colour / pill / size, and adds/deletes
 * captions. All changes persist to the project immediately.
 */
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
    onClose: () -> Unit
) {
    val selected = overlays.firstOrNull { it.id == selectedId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Text",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "Close text",
                tint = ApexPalette.NeonCyan,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onClose() }
                    .padding(4.dp)
            )
        }
        Spacer(Modifier.height(10.dp))

        // Caption chips: one pill per caption + an add button.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ApexPalette.NeonCyan.copy(alpha = 0.15f))
                        .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(10.dp))
                        .clickable { onAdd() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add, null,
                        tint = ApexPalette.NeonCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            items(overlays) { o ->
                val sel = o.id == selectedId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (sel) ApexPalette.NeonCyan.copy(alpha = 0.2f)
                            else ApexPalette.BgElevated
                        )
                        .border(
                            1.dp,
                            if (sel) ApexPalette.NeonCyan else ApexPalette.BorderGlass,
                            RoundedCornerShape(10.dp)
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
        Spacer(Modifier.height(12.dp))

        if (selected == null) {
            Text(
                "Tap + to add a caption — it will appear on the video and drag to move it.",
                color = ApexPalette.TextTertiary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexPalette.NeonCyan.copy(alpha = 0.15f))
                    .border(1.dp, ApexPalette.NeonCyan, RoundedCornerShape(10.dp))
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Add caption at playhead",
                    color = ApexPalette.NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            return@Column
        }

        OutlinedTextField(
            value = selected.text,
            onValueChange = onTextChange,
            singleLine = false,
            minLines = 1,
            maxLines = 3,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = 14.sp
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

        Spacer(Modifier.height(12.dp))

        // Text colour swatches.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Colour",
                color = ApexPalette.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(52.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                textColors().forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
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

        Spacer(Modifier.height(12.dp))

        // Pill background.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Pill",
                color = ApexPalette.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(52.dp)
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
                            .padding(horizontal = 10.dp, vertical = 4.dp)
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

        Spacer(Modifier.height(12.dp))

        Text(
            "Size",
            color = ApexPalette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = selected.sizeScale.coerceIn(0.4f, 3f),
            onValueChange = onSizeChange,
            valueRange = 0.4f..3f,
            colors = SliderDefaults.colors(
                thumbColor = ApexPalette.NeonCyan,
                activeTrackColor = ApexPalette.NeonCyan,
                inactiveTrackColor = ApexPalette.BgElevated
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ApexPalette.Danger.copy(alpha = 0.15f))
                    .border(1.dp, ApexPalette.Danger, RoundedCornerShape(10.dp))
                    .clickable { onDelete(selected.id) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Delete, null,
                        tint = ApexPalette.Danger,
                        modifier = Modifier.size(14.dp)
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
