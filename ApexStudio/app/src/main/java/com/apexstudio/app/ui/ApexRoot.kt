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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.apexstudio.app.ui.components.BottomNavBar
import com.apexstudio.app.ui.components.NeonGradientBackground
import com.apexstudio.app.ui.screens.audio.AudioStudioScreen
import com.apexstudio.app.ui.screens.colortools.ColorStudioScreen
import com.apexstudio.app.ui.screens.diagnostics.CrashDiagnosticsScreen
import com.apexstudio.app.ui.screens.editor.EditorScreen
import com.apexstudio.app.ui.screens.export.ExportScreen
import com.apexstudio.app.ui.screens.home.HomeScreen
import com.apexstudio.app.ui.screens.settings.SettingsScreen

@Composable
fun ApexRoot() {
    var currentTab by remember { mutableStateOf("home") }
    var projectId by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var showExportSettings by remember { mutableStateOf(false) }

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
                        onOpenDiagnostics = { overlay = Overlay.Diagnostics }
                    )
                    is Overlay.Diagnostics -> CrashDiagnosticsScreen(
                        onBack = { overlay = null }
                    )
                    else -> when (currentTab) {
                        "home" -> HomeScreen(
                            onProjectOpen = { id ->
                                projectId = id
                                currentTab = "edit"
                            },
                            onOpenSettings = { overlay = Overlay.Settings }
                        )
                        "edit" -> EditorScreen(
                            projectId = projectId ?: "p1",
                            onBack = { currentTab = "home" },
                            onExport = { showExportSettings = true },
                            onColor = { currentTab = "color" },
                            onAudio = { currentTab = "audio" }
                        )
                        "color" -> ColorStudioScreen(
                            projectId = projectId ?: "p1",
                            onBack = { currentTab = "edit" },
                            onExport = { showExportSettings = true }
                        )
                        "audio" -> AudioStudioScreen(
                            projectId = projectId ?: "p1",
                            onBack = { currentTab = "edit" },
                            onExport = { showExportSettings = true }
                        )
                        "export" -> ExportScreen(
                            projectId = projectId ?: "p1",
                            onBack = { currentTab = "edit" },
                            onExport = { showExportSettings = true }
                        )
                    }
                }
            }
        }

        if (showExportSettings) {
            ExportScreen(
                projectId = projectId ?: "p1",
                onBack = { showExportSettings = false },
                onExport = { showExportSettings = false }
            )
        } else {
            // Bottom nav only on the 4 main tabs
            if (overlay == null && currentTab != "export") {
                BottomNavBar(
                    current = currentTab,
                    onSelect = { tab ->
                        if (currentTab != tab) currentTab = tab
                    }
                )
            }
        }
    }
}

private sealed class Overlay {
    data object Settings : Overlay()
    data object Diagnostics : Overlay()
}