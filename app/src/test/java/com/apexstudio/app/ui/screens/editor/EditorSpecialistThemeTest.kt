package com.apexstudio.app.ui.screens.editor

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.background.InstalledWallpaper
import com.apexstudio.app.domain.model.ChromaKeySettings
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.viewmodel.BackgroundUiState
import com.apexstudio.app.ui.theme.ApexTheme
import com.apexstudio.app.ui.theme.ApexThemeOption
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h720dp-mdpi")
class EditorSpecialistThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun wallpaperChromaPanelUsesSelectedThemeSurfaces() {
        val selectedTheme = mutableStateOf(ApexThemeOption.NEON)
        compose.setContent {
            ApexTheme(option = selectedTheme.value) {
                WallpaperChromaPanel(
                    settings = ChromaKeySettings(enabled = true),
                    onUpdate = {},
                    wallpapers = emptyList<InstalledWallpaper>(),
                    packState = null,
                    packLoading = false,
                    packError = null,
                    packSizeBytes = 0L,
                    catalogItemCount = 0,
                    ui = BackgroundUiState(),
                    isApplied = false,
                    targetAvailable = true,
                    onDownloadPack = {},
                    onRefreshPack = {},
                    onRepairPack = {},
                    onApply = {},
                    onRestoreOriginal = {},
                    onClose = {},
                    modifier = Modifier.testTag("wallpaper-chroma-panel")
                )
            }
        }
        assertThemeColors(selectedTheme, "wallpaper-chroma-panel") { it ->
            listOf(it.palette.bgSurface, it.palette.bgElevated)
        }
    }

    @Test
    fun maskPanelUsesSelectedThemeSurfaces() {
        val selectedTheme = mutableStateOf(ApexThemeOption.NEON)
        compose.setContent {
            ApexTheme(option = selectedTheme.value) {
                MaskPanel(
                    mask = null,
                    onUpdate = {},
                    onClose = {},
                    modifier = Modifier.testTag("mask-panel")
                )
            }
        }
        assertThemeColors(selectedTheme, "mask-panel") { it ->
            listOf(it.palette.bgSurface, it.palette.bgElevated)
        }
    }

    @Test
    fun transitionPickerUsesSelectedThemeSurfaces() {
        val selectedTheme = mutableStateOf(ApexThemeOption.NEON)
        compose.setContent {
            ApexTheme(option = selectedTheme.value) {
                TransitionPickerSheet(
                    fromClip = null,
                    toClip = null,
                    currentTransition = null,
                    onApplyTransition = { _, _ -> },
                    onApplyToAll = { _, _ -> },
                    onRemoveTransition = {},
                    onClose = {},
                    modifier = Modifier.testTag("transition-picker")
                )
            }
        }
        assertThemeColors(selectedTheme, "transition-picker") { it ->
            listOf(it.palette.bgSurface, it.palette.bgElevated)
        }
    }

    @Test
    fun trimFilmstripPlaceholderUsesSelectedThemeSurface() {
        val selectedTheme = mutableStateOf(ApexThemeOption.NEON)
        val clip = MediaClip(
            id = "theme-test-clip",
            name = "Theme test clip",
            uri = "file:///does-not-exist-theme-test.mp4",
            durationMs = 10_000L,
            trimEndMs = 10_000L
        )
        compose.setContent {
            ApexTheme(option = selectedTheme.value) {
                TrimFilmstrip(
                    clip = clip,
                    trimStart = 0L,
                    trimEnd = 10_000L,
                    duration = 10_000L,
                    minimumTrimSpan = 100L,
                    onTrimChange = { _, _ -> },
                    modifier = Modifier.testTag("trim-filmstrip")
                )
            }
        }
        assertThemeColors(selectedTheme, "trim-filmstrip") { it ->
            listOf(it.palette.bgElevated)
        }
    }

    private fun assertThemeColors(
        selectedTheme: MutableState<ApexThemeOption>,
        tag: String,
        expectedColors: (ApexThemeOption) -> List<Color>
    ) {
        ApexThemeOption.entries.forEach { option ->
            compose.runOnIdle { selectedTheme.value = option }
            compose.waitForIdle()
            val image = compose.onNodeWithTag(tag).captureToImage()
            expectedColors(option).forEach { color ->
                assertContainsColorNear(image, color, "$tag ${option.displayName}")
            }
        }
    }

    private fun assertContainsColorNear(image: ImageBitmap, expected: Color, label: String) {
        val pixels = image.toPixelMap()
        var matchingPixels = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val actual = pixels[x, y]
                if (
                    kotlin.math.abs(actual.red - expected.red) <= 0.02f &&
                    kotlin.math.abs(actual.green - expected.green) <= 0.02f &&
                    kotlin.math.abs(actual.blue - expected.blue) <= 0.02f &&
                    actual.alpha >= 0.98f
                ) {
                    matchingPixels++
                }
            }
        }
        assertTrue("Expected selected-theme surface pixels for $label; found $matchingPixels", matchingPixels > 100)
    }
}
