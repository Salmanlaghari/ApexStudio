package com.apexstudio.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(
            title = "Settings",
            subtitle = "ApexStudio • v1.0.0",
            onBack = onBack
        )
        Spacer(Modifier.height(4.dp))

        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Column {
                Text(
                    "Diagnostics",
                    color = ApexPalette.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = Icons.Default.BugReport,
                    title = "Crash Diagnostics",
                    subtitle = "View last crash stack trace",
                    tint = ApexPalette.Danger,
                    onClick = onOpenDiagnostics
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = Icons.Default.Storage,
                    title = "Storage",
                    subtitle = "Manage cache and projects",
                    tint = ApexPalette.NeonCyan,
                    onClick = { }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Column {
                Text(
                    "Appearance",
                    color = ApexPalette.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = "Neon Dark (default)",
                    tint = ApexPalette.NeonPurple,
                    onClick = { }
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "About ApexStudio",
                    subtitle = "v1.0.0 • Build 1",
                    tint = ApexPalette.NeonCyan,
                    onClick = { }
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ApexPalette.BgElevated.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.5f)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = ApexPalette.BgDeep, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = ApexPalette.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                color = ApexPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(
            Icons.Default.ChevronRight, null,
            tint = ApexPalette.TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}
