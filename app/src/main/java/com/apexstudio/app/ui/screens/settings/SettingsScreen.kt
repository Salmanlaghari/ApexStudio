package com.apexstudio.app.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.BuildIdentity
import com.apexstudio.app.ui.components.BuildIdentityDialog
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette
import com.apexstudio.app.ui.theme.ApexThemeOption
import kotlinx.coroutines.launch

private const val SUPPORT_EMAIL = "laghariprince2@gmail.com"

private enum class ContactTopic(val title: String, val subject: String, val prompt: String) {
    REPORT("Report a problem", "ApexStudio issue report", "Describe what happened and the steps to reproduce it."),
    SUGGESTION("Suggestions", "ApexStudio suggestion", "Tell us what you would like to see in ApexStudio.")
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenPacks: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeFlow = remember(context) { SettingsPreferences.theme(context) }
    val activeTheme by themeFlow.collectAsState(initial = ApexThemeOption.default)
    var showBuildIdentity by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }
    var showThemes by remember { mutableStateOf(false) }
    var fallbackTopic by remember { mutableStateOf<ContactTopic?>(null) }

    if (showBuildIdentity) BuildIdentityDialog(onDismiss = { showBuildIdentity = false })
    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onBuildDetails = {
                showAbout = false
                showBuildIdentity = true
            }
        )
    }
    if (showPolicy) PolicyDialog(onDismiss = { showPolicy = false })
    if (showThemes) {
        ThemeDialog(
            selected = activeTheme,
            onDismiss = { showThemes = false },
            onSelect = { choice ->
                scope.launch { SettingsPreferences.setTheme(context, choice) }
                showThemes = false
            }
        )
    }
    fallbackTopic?.let { topic ->
        EmailFallbackDialog(
            topic = topic,
            onDismiss = { fallbackTopic = null },
            onCopyAddress = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Support email", SUPPORT_EMAIL))
                }
                Toast.makeText(
                    context,
                    if (clipboard != null) "Email address copied" else "Email: $SUPPORT_EMAIL",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    val launchEmail: (ContactTopic) -> Unit = { topic ->
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, topic.subject)
            putExtra(Intent.EXTRA_TEXT, topic.prompt)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            fallbackTopic = topic
        } catch (_: SecurityException) {
            fallbackTopic = topic
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(
            title = "Settings",
            subtitle = "ApexStudio • v1.0.0",
            onBack = onBack
        )
        Spacer(Modifier.height(4.dp))

        GlassCard(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Column {
                SettingsSectionTitle("Diagnostics & storage")
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = Icons.Default.BugReport,
                    title = "View Crash Log",
                    subtitle = "Open crash diagnostics stored on this device",
                    tint = ApexPalette.Danger,
                    onClick = onOpenDiagnostics
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = Icons.Default.CloudDownload,
                    title = "Downloadable Packs",
                    subtitle = "Browse filters, effects and models",
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onOpenPacks
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = Icons.Default.Storage,
                    title = "Storage & privacy",
                    subtitle = "What is stored and where it connects",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { showPolicy = true }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        GlassCard(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Column {
                SettingsSectionTitle("Appearance")
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = Icons.Default.Palette,
                    title = "Themes",
                    subtitle = activeTheme.displayName,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { showThemes = true }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        GlassCard(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Column {
                SettingsSectionTitle("About & support")
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = Icons.Default.PrivacyTip,
                    title = "Policy",
                    subtitle = "Privacy and data handling",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { showPolicy = true }
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "About ApexStudio",
                    subtitle = "Installed build ${BuildIdentity.shortCommit}",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { showAbout = true }
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = Icons.Default.BugReport,
                    title = "Report a problem",
                    subtitle = SUPPORT_EMAIL,
                    tint = ApexPalette.Danger,
                    onClick = { launchEmail(ContactTopic.REPORT) }
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    icon = Icons.Default.Lightbulb,
                    title = "Suggestions",
                    subtitle = SUPPORT_EMAIL,
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = { launchEmail(ContactTopic.SUGGESTION) }
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        color = ApexPalette.TextSecondary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit, onBuildDetails: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About ApexStudio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A mobile video editor for trimming, arranging and styling media you choose.")
                Text("Version 1.0.0 • Build ${BuildIdentity.shortCommit}")
                Text("Editing features work on selected media. Optional music, content-pack and AR features may use third-party services; see Policy for details.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = onBuildDetails) { Text("Build details") } }
    )
}

@Composable
private fun PolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy policy") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsPolicyCopy.sections.forEach { section ->
                    Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(section.body, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ThemeDialog(
    selected: ApexThemeOption,
    onDismiss: () -> Unit,
    onSelect: (ApexThemeOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a theme") },
        text = {
            Column {
                Text("Changes dashboard accents, background glow and app controls. Editor surfaces stay dark; timeline track colors stay fixed for layer recognition.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                ApexThemeOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(option) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Box(
                            modifier = Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(
                                Brush.linearGradient(listOf(option.primary, option.secondary))
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(option.displayName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun EmailFallbackDialog(
    topic: ContactTopic,
    onDismiss: () -> Unit,
    onCopyAddress: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No email app available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("We couldn't open an email composer. You can still contact us about: ${topic.title.lowercase()}.")
                Text(SUPPORT_EMAIL, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text("Subject: ${topic.subject}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = onCopyAddress) { Text("Copy email") } }
    )
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
                .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.5f)))),
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
