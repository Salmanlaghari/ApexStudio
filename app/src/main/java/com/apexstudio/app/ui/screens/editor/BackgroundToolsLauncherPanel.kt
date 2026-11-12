package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.EditorPalette as ApexPalette

@Composable
fun BackgroundToolsLauncherPanel(
    onOpenAiBackground: () -> Unit,
    onOpenChromaWallpaper: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ApexPalette.BgSurface)
            .padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Background tools", color = ApexPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Choose a real processed-background workflow", color = ApexPalette.TextPrimary.copy(alpha = 0.66f), fontSize = 11.sp)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexPalette.TextPrimary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenAiBackground,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29293B), contentColor = ApexPalette.TextPrimary)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AI background replacement", fontWeight = FontWeight.SemiBold)
                Text("Blur, solid color, or a project image", fontSize = 10.sp, color = ApexPalette.TextPrimary.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onOpenChromaWallpaper,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ApexPalette.NeonCyan, contentColor = Color(0xFF071018))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Chroma key + wallpapers", fontWeight = FontWeight.Bold)
                Text("Green/blue screen, download a catalog wallpaper, then Apply", fontSize = 10.sp)
            }
        }
    }
}
