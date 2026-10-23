package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.domain.model.PhotoCanvasSettings
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

@Composable
internal fun PhotoCanvasPanel(settings: PhotoCanvasSettings, onChange: (PhotoCanvasSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("photo-canvas-panel")) {
        Text("Canvas size", color = ApexPalette.TextPrimary)
        Text("Fit keeps the entire photo. Fill crops the edges. Your original image is unchanged.",
            color = ApexPalette.TextSecondary, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoCanvasSettings.PRESETS.forEach { preset ->
                FilterChip(
                    selected = settings.aspectPreset == preset,
                    onClick = { onChange(settings.copy(aspectPreset = preset)) },
                    label = { Text(if (preset == "original") "Original" else preset) },
                    modifier = Modifier.testTag("photo-canvas-ratio-$preset")
                )
            }
        }
        if (!settings.isOriginal) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !settings.fill, onClick = { onChange(settings.copy(fill = false)) }, label = { Text("Fit") })
                FilterChip(selected = settings.fill, onClick = { onChange(settings.copy(fill = true)) }, label = { Text("Fill") })
            }
            Text("Background", color = ApexPalette.TextPrimary, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoCanvasSettings.BACKGROUNDS.forEach { (label, argb) ->
                    FilterChip(
                        selected = settings.backgroundArgb == argb,
                        onClick = { onChange(settings.copy(backgroundArgb = argb)) },
                        label = { Text(label) },
                        leadingIcon = { Box(Modifier.size(16.dp).background(Color(argb), RoundedCornerShape(4.dp))) }
                    )
                }
            }
        }
    }
}
