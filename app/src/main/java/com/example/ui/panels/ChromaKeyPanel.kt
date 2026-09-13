package com.example.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BackgroundPlate
import com.example.model.ChromaKeyState

/**
 * 3D ChromaKey Tool Panel:
 * Direct, real-time control over:
 * 1. Key Color picker (Neon Green, Screen Blue, Magenta, Studio Amber)
 * 2. Similarity threshold
 * 3. Smoothness falloff
 * 4. Spill Suppression (eliminates green/blue fringe reflection)
 * 5. Alpha Matte View toggle
 * 6. Background plate selector
 */
@Composable
fun ChromaKeyPanel(
    chromaKeyState: ChromaKeyState,
    onChromaKeyChange: (ChromaKeyState) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val presetColors = listOf(
        Pair("Green Screen", 0xFF00FF00.toInt()),
        Pair("Screen Blue", 0xFF0055FF.toInt()),
        Pair("Studio Magenta", 0xFFFF007F.toInt()),
        Pair("Amber Key", 0xFFFFB300.toInt())
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Master Enable & Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "3D Chroma Key Processor",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (chromaKeyState.enabled) "Active: Real-time pixel shader pass" else "Bypassed",
                    fontSize = 11.sp,
                    color = if (chromaKeyState.enabled) Color(0xFF10B981) else Color.Gray
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onChromaKeyChange(ChromaKeyState()) },
                    modifier = Modifier.testTag("chroma_reset_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Keyer", tint = Color.Gray)
                }

                Switch(
                    checked = chromaKeyState.enabled,
                    onCheckedChange = { onChromaKeyChange(chromaKeyState.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981)
                    ),
                    modifier = Modifier.testTag("chroma_enable_switch")
                )
            }
        }

        // Key Color Selector
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Key Color Sample", fontSize = 12.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                presetColors.forEach { (name, colorInt) ->
                    val isSelected = chromaKeyState.keyColor == colorInt
                    val composeColor = Color(colorInt)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onChromaKeyChange(chromaKeyState.copy(keyColor = colorInt, enabled = true))
                            }
                            .background(if (isSelected) Color(0xFF272F3E) else Color(0xFF1A1D24))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF00F0FF) else Color(0xFF333846),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(composeColor)
                                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.Black, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(name, fontSize = 9.sp, color = Color.White)
                    }
                }
            }
        }

        // 1. Similarity Slider
        SliderControl(
            label = "Similarity Threshold",
            value = chromaKeyState.similarity,
            valueRange = 0.05f..0.85f,
            displayValue = "${(chromaKeyState.similarity * 100).toInt()}%",
            onValueChange = { onChromaKeyChange(chromaKeyState.copy(similarity = it, enabled = true)) },
            testTag = "chroma_similarity_slider"
        )

        // 2. Smoothness Slider
        SliderControl(
            label = "Edge Smoothness",
            value = chromaKeyState.smoothness,
            valueRange = 0.01f..0.45f,
            displayValue = "${(chromaKeyState.smoothness * 100).toInt()}%",
            onValueChange = { onChromaKeyChange(chromaKeyState.copy(smoothness = it, enabled = true)) },
            testTag = "chroma_smoothness_slider"
        )

        // 3. Spill Suppression Slider
        SliderControl(
            label = "Spill Suppression",
            value = chromaKeyState.spillSuppression,
            valueRange = 0.0f..1.0f,
            displayValue = "${(chromaKeyState.spillSuppression * 100).toInt()}%",
            onValueChange = { onChromaKeyChange(chromaKeyState.copy(spillSuppression = it, enabled = true)) },
            testTag = "chroma_spill_slider"
        )

        // 4. Matte View & Background Plate
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onChromaKeyChange(chromaKeyState.copy(showMatteOnly = !chromaKeyState.showMatteOnly))
                    }
                    .background(if (chromaKeyState.showMatteOnly) Color(0xFF4C1D95) else Color(0xFF1E222D))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Visibility, contentDescription = "Matte Only", tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    text = if (chromaKeyState.showMatteOnly) "Matte View (ON)" else "Show Matte Only",
                    fontSize = 11.sp,
                    color = Color.White
                )
            }

            Text("Background Plate:", fontSize = 11.sp, color = Color.Gray)
        }

        // Background Plate Selector
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(BackgroundPlate.values()) { plate ->
                val isSelected = chromaKeyState.backgroundPlate == plate
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onChromaKeyChange(chromaKeyState.copy(backgroundPlate = plate)) }
                        .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF1A1D24))
                        .border(1.dp, if (isSelected) Color(0xFF93C5FD) else Color(0xFF2C3240), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(plate.displayName, fontSize = 11.sp, color = Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun SliderControl(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
    testTag: String = ""
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.sp, color = Color(0xFFE2E8F0))
            Text(displayValue, fontSize = 11.sp, color = Color(0xFF00F0FF), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00F0FF),
                activeTrackColor = Color(0xFF00F0FF),
                inactiveTrackColor = Color(0xFF262E3D)
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
