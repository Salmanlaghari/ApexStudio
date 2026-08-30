package com.apexstudio.app.ui.screens.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.BuildConfig
import com.apexstudio.app.data.crashlog.CrashLog
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette
import kotlinx.coroutines.launch

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
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(
            title = "Crash Diagnostics",
            subtitle = "ApexStudio • Diagnostics",
            onBack = onBack
        )

        Spacer(Modifier.height(4.dp))

        // Crash log card
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Crash Log",
                            color = ApexPalette.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Last recorded crash diagnostics",
                            color = ApexPalette.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
                            color = ApexPalette.Danger,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text(
                            "No crash log available.\n\nNote: Native/NDK crashes are not captured by this handler.",
                            color = ApexPalette.Danger,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Center)
                        )
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
                HelpItem("1.", "Reproduce the crash in the app (e.g. import music).")
                HelpItem("2.", "Open Settings → View Crash Log.")
                HelpItem("3.", "Screenshot or copy the red diagnostic text.")
                HelpItem("4.", "Share the screenshot for an evidence-based fix.")
            }
        }

        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(14.dp))
            GlassCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column {
                    Text(
                        "Debug",
                        color = ApexPalette.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Force a test crash to verify the capture pipeline.",
                        color = ApexPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { throw RuntimeException("Test crash for diagnostics") },
                        colors = ButtonDefaults.buttonColors(containerColor = ApexPalette.Danger),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Force Test Crash")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "After the app restarts, reopen this screen to verify the log.",
                        color = ApexPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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
