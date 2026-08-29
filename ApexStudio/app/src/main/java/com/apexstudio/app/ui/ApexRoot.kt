package com.apexstudio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.apexstudio.app.ui.components.NeonGradientBackground
import com.apexstudio.app.ui.screens.editor.EditorScreen
import com.apexstudio.app.ui.screens.export.ExportScreen
import com.apexstudio.app.ui.screens.home.HomeScreen
import com.apexstudio.app.ui.screens.audio.AudioStudioScreen
import com.apexstudio.app.ui.screens.colortools.ColorStudioScreen

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{projectId}"
    const val EXPORT = "export/{projectId}"
    const val AUDIO = "audio/{projectId}"
    const val COLOR = "color/{projectId}"
    fun editor(id: String) = "editor/$id"
    fun export(id: String) = "export/$id"
    fun audio(id: String) = "audio/$id"
    fun color(id: String) = "color/$id"
}

@Composable
fun ApexRoot() {
    val navController = rememberNavController()
    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        NeonGradientBackground(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onProjectOpen = { id -> navController.navigate(Routes.editor(id)) }
                    )
                }
                composable(
                    Routes.EDITOR,
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString("projectId") ?: ""
                    EditorScreen(
                        projectId = id,
                        onBack = { navController.popBackStack() },
                        onExport = { navController.navigate(Routes.export(id)) },
                        onColor = { navController.navigate(Routes.color(id)) },
                        onAudio = { navController.navigate(Routes.audio(id)) }
                    )
                }
                composable(
                    Routes.EXPORT,
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString("projectId") ?: ""
                    ExportScreen(
                        projectId = id,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    Routes.AUDIO,
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString("projectId") ?: ""
                    AudioStudioScreen(
                        projectId = id,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    Routes.COLOR,
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString("projectId") ?: ""
                    ColorStudioScreen(
                        projectId = id,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
