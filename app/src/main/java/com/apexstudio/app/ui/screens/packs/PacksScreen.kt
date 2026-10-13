package com.apexstudio.app.ui.screens.packs

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.data.packs.download.PackManifestRepo
import com.apexstudio.app.ui.components.AppTopBar
import com.apexstudio.app.ui.components.GlassCard
import com.apexstudio.app.ui.theme.ApexPalette

/**
 * On-demand Pack Store (PR-C / "Blake").
 *
 * Lists downloadable content packs grouped by category. Heavy assets
 * (filter/transition/effect packs, AI models, camera content) live here —
 * never bundled in the APK.
 *
 * Every action works or isn't shown: coming-soon packs render disabled,
 * failed downloads offer Retry, and deleting a pack cleans its files.
 */
@Composable
fun PacksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val packs by PackManager.packsState.collectAsState()
    val manifestError by PackManager.manifestError.collectAsState()
    val downloadError by PackManager.downloadError.collectAsState()
    val isLoading by PackManager.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        PackManager.refresh(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(
            title = "Downloadable Packs",
            subtitle = "Heavy content, on demand — keeps the app light",
            onBack = onBack,
            extraRight = {
                TextButton(onClick = {
                    PackManager.clearDownloadError()
                    PackManager.refresh(context)
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ApexPalette.NeonCyan, modifier = Modifier.size(18.dp))
                }
            }
        )

        Spacer(Modifier.height(4.dp))

        // Explainer banner.
        GlassCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            cornerRadius = 18.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = ApexPalette.NeonCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Filters, transitions, effects and AI models download only when you want them. " +
                        "Delete anytime to free space.",
                    color = ApexPalette.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Catalog error (offline / network): shown instead of failing silently.
        if (manifestError != null || downloadError != null) {
            val message = downloadError ?: manifestError ?: ""
            GlassCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                cornerRadius = 18.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = ApexPalette.Danger,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        message,
                        color = ApexPalette.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (isLoading && packs.isEmpty()) {
            // Loading: distinguish from a genuinely empty catalog.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ApexPalette.NeonCyan)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Loading pack catalog…",
                        color = ApexPalette.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (packs.isEmpty()) {
            // Manifest still loading (or offline with no cache).
            GlassCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                cornerRadius = 18.dp
            ) {
                Text(
                    "Loading pack catalog… check your connection and pull to refresh.",
                    color = ApexPalette.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Group by category, preserving manifest order.
        val grouped = packs.groupBy { it.pack.category }
        for ((category, items) in grouped) {
            Text(
                PackManifestRepo.categoryTitle(category),
                color = ApexPalette.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            for (item in items) {
                PackRow(
                    item = item,
                    onDownload = { PackManager.download(context, item.pack.id) },
                    onCancel = { PackManager.cancel(context, item.pack.id) },
                    onDelete = { PackManager.delete(context, item.pack.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PackRow(
    item: PackManager.PackUiItem,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pack = item.pack
    GlassCard(modifier = modifier.fillMaxWidth(), cornerRadius = 18.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pack.name,
                        color = ApexPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        pack.description,
                        color = ApexPalette.TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 3
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${PackManifestRepo.formatSize(pack.sizeBytes)} • v${pack.versionName}",
                        color = ApexPalette.TextSecondary,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                PackAction(item = item, onDownload = onDownload, onCancel = onCancel, onDelete = onDelete)
            }

            // Download progress.
            val state = item.state
            if (state is PackManager.PackState.Downloading) {
                Spacer(Modifier.height(8.dp))
                if (state.fraction >= 0) {
                    LinearProgressIndicator(
                        progress = { state.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = ApexPalette.NeonCyan,
                        trackColor = Color(0xFF1E293B)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${PackManifestRepo.formatSize(state.doneBytes)} / ${PackManifestRepo.formatSize(state.totalBytes)}",
                        color = ApexPalette.TextSecondary,
                        fontSize = 10.sp
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ApexPalette.NeonCyan)
                    Text("Downloading…", color = ApexPalette.TextSecondary, fontSize = 10.sp)
                }
            }
            if (state is PackManager.PackState.Failed) {
                Spacer(Modifier.height(4.dp))
                Text(state.reason, color = ApexPalette.Danger, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PackAction(
    item: PackManager.PackUiItem,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    when (val state = item.state) {
        is PackManager.PackState.NotDownloaded ->
            ActionButton(icon = Icons.Default.Download, label = "Get", tint = ApexPalette.NeonCyan, onClick = onDownload)
        is PackManager.PackState.Downloading ->
            ActionButton(icon = Icons.Default.ErrorOutline, label = "Cancel", tint = ApexPalette.TextSecondary, onClick = onCancel)
        is PackManager.PackState.Downloaded ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                ActionButton(icon = Icons.Default.Delete, label = "Delete", tint = ApexPalette.Danger, onClick = onDelete)
            }
        is PackManager.PackState.UpdateAvailable ->
            ActionButton(icon = Icons.Default.SystemUpdate, label = "Update", tint = ApexPalette.NeonPurple, onClick = onDownload)
        is PackManager.PackState.Failed ->
            ActionButton(icon = Icons.Default.Refresh, label = "Retry", tint = ApexPalette.NeonCyan, onClick = onDownload)
        is PackManager.PackState.ComingSoon ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = ApexPalette.TextSecondary, modifier = Modifier.size(16.dp))
                Text("Soon", color = ApexPalette.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
