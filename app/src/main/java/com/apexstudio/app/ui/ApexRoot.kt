package com.apexstudio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.apexstudio.app.ui.components.BottomNavBar
import com.apexstudio.app.ui.components.BottomNavItem
import com.apexstudio.app.ui.components.defaultNavItems
import com.apexstudio.app.ui.components.NeonGradientBackground
import com.apexstudio.app.camerakit.CameraKitDeliveryRoute
import com.apexstudio.app.ui.screens.diagnostics.CrashDiagnosticsScreen
import com.apexstudio.app.ui.screens.editor.EditorScreen
import com.apexstudio.app.ui.screens.export.ExportScreen
import com.apexstudio.app.ui.screens.home.HomeScreen
import com.apexstudio.app.ui.screens.packs.PacksScreen
import com.apexstudio.app.ui.screens.settings.SettingsScreen

/** Select a workspace; editor-owned workspaces release preview codecs before exporting. */
internal fun apexContentTab(currentTab: String, showExportSettings: Boolean): String =
    if (showExportSettings) "export" else currentTab

@Composable
fun ApexRoot() {
    var currentTab by remember { mutableStateOf("home") }
    var projectId by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var showExportSettings by remember { mutableStateOf(false) }
    var packsOpenedFromHome by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            NeonGradientBackground(modifier = Modifier.fillMaxSize()) {
                when (overlay) {
                    is Overlay.Settings -> SettingsScreen(
                        onBack = { overlay = null },
                        onOpenDiagnostics = { overlay = Overlay.Diagnostics },
                        onOpenPacks = { packsOpenedFromHome = false; overlay = Overlay.Packs }
                    )
                    is Overlay.Diagnostics -> CrashDiagnosticsScreen(
                        onBack = { overlay = null }
                    )
                    is Overlay.Packs -> PacksScreen(
                        onBack = {
                            overlay = if (packsOpenedFromHome) null else Overlay.Settings
                            packsOpenedFromHome = false
                        }
                    )
                    else -> when (apexContentTab(currentTab, showExportSettings)) {
                        "home" -> HomeScreen(
                            onProjectOpen = { id ->
                                projectId = id
                                currentTab = "edit"
                            },
                            onOpenSettings = { overlay = Overlay.Settings }
                        )
                        "edit", "color", "audio" -> EditorScreen(
                            projectId = projectId ?: "p1",
                            onBack = { currentTab = "home" },
                            onExport = { showExportSettings = true },
                            onColor = { currentTab = "color" },
                            onAudio = { currentTab = "audio" },
                            entryWorkspace = apexContentTab(currentTab, showExportSettings),
                            onCloseWorkspace = { showExportSettings = false; currentTab = "edit" }
                        )
                        // Keep export as its own page. Leaving EditorScreen here disposes
                        // its preview/overlay players before the encoder starts, while the
                        // shared project-keyed ViewModel keeps export settings and state.
                        "export" -> ExportScreen(
                            projectId = projectId ?: "p1",
                            onBack = { showExportSettings = false },
                            onExport = { showExportSettings = false },
                            onSettings = { overlay = Overlay.Settings }
                        )
                        "lenses" -> CameraKitDeliveryRoute(
                            onBack = { currentTab = "home" }
                        )
                    }
                }
            }
        }

        val navItems = when {
            showExportSettings -> listOf(
                BottomNavItem("home", "Home", Icons.Default.Home),
                BottomNavItem("edit", "Edit", Icons.Default.Tune),
                BottomNavItem("export", "Export", Icons.Default.IosShare, selectedColor = Color(0xFF9768FF)),
                BottomNavItem("library", "Library", Icons.Default.VideoLibrary)
            )
            currentTab == "home" -> listOf(
                BottomNavItem("home", "Projects", Icons.Default.Folder, selectedColor = Color(0xFF9768FF)),
                BottomNavItem("templates", "Templates", Icons.Default.PlayArrow),
                BottomNavItem("settings", "Settings", Icons.Default.Settings)
            )
            else -> defaultNavItems(currentTab)
        }
        if (overlay == null && (showExportSettings || currentTab !in setOf("export", "edit", "color", "audio"))) {
            BottomNavBar(
                current = if (showExportSettings) "export" else currentTab,
                items = navItems,
                onSelect = { tab ->
                    when (tab) {
                        "templates" -> { packsOpenedFromHome = true; overlay = Overlay.Packs }
                        "settings" -> overlay = Overlay.Settings
                        "export" -> {
                            showExportSettings = true
                            currentTab = "edit"
                        }
                        "library" -> {
                            showExportSettings = false
                            currentTab = "home"
                        }
                        else -> {
                            showExportSettings = false
                            currentTab = tab
                        }
                    }
                }
            )
        }
    }
}

private sealed class Overlay {
    data object Settings : Overlay()
    data object Diagnostics : Overlay()
    data object Packs : Overlay()
}
