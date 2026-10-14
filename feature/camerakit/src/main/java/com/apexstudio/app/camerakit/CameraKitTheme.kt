package com.apexstudio.app.camerakit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Minimal theme + top bar local to the Camera Kit feature module.
 *
 * This module must not depend on the app module (a base->feature->base
 * cycle is illegal in Gradle), so it cannot use the app's `ApexPalette` /
 * `AppTopBar`. The two colors below are copied verbatim from the app palette
 * (ApexPalette.NeonCyan / ApexPalette.Danger) and the top bar mirrors
 * AppTopBar's look (back chevron + "A" badge + title) so the Lenses screen
 * looks identical from the user's side.
 */
object CameraKitColors {
    val NeonCyan = Color(0xFF00E5FF)
    val Danger = Color(0xFFEF4444)
}

@Composable
fun CameraKitTopBar(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CameraKitColors.NeonCyan),
            contentAlignment = Alignment.Center
        ) {
            Text("A", color = Color.Black, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
