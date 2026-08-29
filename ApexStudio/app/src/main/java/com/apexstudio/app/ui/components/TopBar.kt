package com.apexstudio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.ui.theme.ApexPalette

@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    extraRight: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onHome != null) {
            NeonIconButton(
                icon = Icons.Default.Home,
                onClick = onHome,
                size = 40.dp,
                iconSize = 20.dp
            )
        } else if (onBack != null) {
            NeonIconButton(
                icon = Icons.Default.ChevronLeft,
                onClick = onBack,
                size = 40.dp,
                iconSize = 22.dp
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(ApexPalette.NeonCyan, ApexPalette.NeonPurple))),
            contentAlignment = Alignment.Center
        ) {
            Text("A", color = ApexPalette.BgDeep, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = ApexPalette.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = ApexPalette.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (onUndo != null) {
            NeonIconButton(
                icon = Icons.Default.Undo,
                onClick = onUndo,
                enabled = canUndo,
                tint = if (canUndo) ApexPalette.TextPrimary else ApexPalette.TextMuted
            )
            Spacer(Modifier.width(4.dp))
        }
        if (onRedo != null) {
            NeonIconButton(
                icon = Icons.Default.Refresh,
                onClick = onRedo,
                enabled = canRedo,
                tint = if (canRedo) ApexPalette.TextPrimary else ApexPalette.TextMuted
            )
            Spacer(Modifier.width(4.dp))
        }
        if (extraRight != null) extraRight()
        if (onExport != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                ApexPalette.NeonCyan.copy(alpha = 0.6f),
                                ApexPalette.NeonPurple.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(1.dp, ApexPalette.NeonCyan.copy(alpha = 0.5f), CircleShape)
                    .clickable(onClick = onExport),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.IosShare,
                    null,
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}