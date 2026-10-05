package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Left Tool Rail floating over video preview
@Composable
fun LeftToolRail(
    onArFilters: () -> Unit = {},
    onEffects: () -> Unit = {},
    onFilters: () -> Unit = {},
    onAdjust: () -> Unit = {},
    onChromaKey: () -> Unit = {},
    onText: () -> Unit = {},
    onSticker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RailItem(Icons.Default.FaceRetouchingNatural, "AR Face", onArFilters)
        RailItem(Icons.Default.AutoAwesome, "Effects", onEffects)
        RailItem(Icons.Default.Palette, "LUTs", onFilters)
        RailItem(Icons.Default.Tune, "Adjust", onAdjust)
        RailItem(Icons.Default.Layers, "3D Chroma", onChromaKey)
        RailItem(Icons.Default.TextFields, "Text", onText)
        RailItem(Icons.Default.EmojiEmotions, "Sticker", onSticker)
    }
}

// Right Tool Rail floating over video preview
@Composable
fun RightToolRail(
    onAdd: () -> Unit = {},
    onAudio: () -> Unit = {},
    onRecord: () -> Unit = {},
    onCamera: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RailItem(Icons.Default.Add, "Add", onAdd)
        RailItem(Icons.Default.MusicNote, "Audio", onAudio)
        RailItem(Icons.Default.Mic, "Record", onRecord)
        RailItem(Icons.Default.CameraAlt, "Camera", onCamera)
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(44.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = Color(0xFFD1D5DB),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

