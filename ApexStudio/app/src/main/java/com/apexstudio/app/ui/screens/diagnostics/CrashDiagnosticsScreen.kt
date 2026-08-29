package com.apexstudio.app.ui.screens.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValueimport androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.crashlog.CrashLog
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CrashDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf(CrashLog.read(context)) }

    fun refresh() {
        log = CrashLog.read(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Transparent)
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(
            title = "Crash Diagnostics",
            subtitle = "ApexStudio • Diagnostics",
            onBack = onBack
        )

        Spacer(Modifier.height(4.dp))

        // Status card
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (log.exists) Brush.linearGradient(
                                listOf(ApexPalette.Danger, ApexPalette.NeonPurple)
                            ) else Brush.linearGradient(
                                listOf(ApexPalette.Success, ApexPalette.NeonCyan)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (log.exists) Icons.Default.BugReport else Icons.Default.SdStorage,
                        null, tint = ApexPalette.BgDeep,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (log.exists) "Crash detected" else "No crashes recorded",
                        color = ApexPalette.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (log.exists) "${log.sizeBytes} bytes • " +
                            SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
                                .format(Date(log.lastModified))
                        else "ApexStudio has not crashed since install",
                        color = ApexPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Crash log card
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Crash Log",
                        color = ApexPalette.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.weight(1f))
                    ActionPill(
                        icon = Icons.Default.Refresh,
                        label = "Refresh",
                        color = ApexPalette.NeonCyan,
                        onClick = { refresh() }
                    )
                    Spacer(Modifier.width(8.dp))
                    ActionPill(
                        icon = Icons.Default.Delete,
                        label = "Clear",
                        color = ApexPalette.Danger,
                        enabled = log.exists,
                        onClick = {
                            scope.launch {
                                CrashLog.clear(context)
                                refresh()
                            }
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ApexPalette.BgBase)
                        .border(1.dp, ApexPalette.BorderGlass, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    if (log.exists && log.content.isNotBlank()) {
                        Text(
                            log.content,
                            color = ApexPalette.TextPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ApexPalette.BgElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.SdStorage,
                                    null, tint = ApexPalette.TextTertiary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No crash log to display",
                                color = ApexPalette.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // How to use card
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Column {
                Text(
                    "How to use this screen",
                    color = ApexPalette.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                HelpItem("1.", "If ApexStudio crashes, reopen the app and come back here.")
                HelpItem("2.", "The most recent stack trace is saved automatically.")
                HelpItem("3.", "Tap \"Copy\" to share the trace for debugging.")
                HelpItem("4.", "Use \"Clear\" to remove the log after fixing the issue.")
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (enabled) color.copy(alpha = 0.18f) else ApexPalette.BgElevated
            )
            .border(
                1.dp,
                if (enabled) color else ApexPalette.BorderGlass,
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (enabled) color else ApexPalette.TextTertiary,
            modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (enabled) color else ApexPalette.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HelpItem(num: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            num,
            color = ApexPalette.NeonCyan,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = ApexPalette.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
