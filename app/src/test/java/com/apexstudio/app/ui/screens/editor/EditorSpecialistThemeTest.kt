package com.apexstudio.app.ui.screens.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.apexstudio.app.data.background.InstalledWallpaper
import com.apexstudio.app.domain.model.ChromaKeySettings
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.presentation.viewmodel.BackgroundUiState
import com.apexstudio.app.ui.theme.ApexTheme
import com.apexstudio.app.ui.theme.ApexThemeOption
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.floor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val MAX_RGB_DISTANCE = 80f
private const val MIN_MATCHING_SURFACE_PIXELS = 500

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorSpecialistThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun wallpaperChromaPanelUsesSelectedThemeSurfaces() {
        val selectedTheme = mutableStateOf(ApexThemeOption.NEON)
        val rootView = AtomicReference<View?>()
        compose.setContent {
            ApexTheme(option = selectedTheme.value) {
                val composeView = LocalView.current
                SideEffect { rootView.set(composeView) }
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
        assertThemeColors(rootView, selectedTheme, "wallpaper-chroma-panel", "Wallpaper/Chroma") { theme ->
            listOf(theme.palette.bgSurface, theme.palette.bgElevated)
        }
    }

    @Test
    fun maskPanelUsesSelectedThemeSurfaces() {
        val selectedTheme = mutableStateOf(ApexThemeOption.NEON)
        val rootView = AtomicReference<View?>()
        compose.setContent {
            ApexTheme(option = selectedTheme.value) {
                val composeView = LocalView.current
                SideEffect { rootView.set(composeView) }
                MaskPanel(
                    mask = null,
                    onUpdate = {},
                    onClose = {},
                    modifier = Modifier.testTag("mask-panel")
                )
            }
        }
        assertThemeColors(rootView, selectedTheme, "mask-panel", "Mask") { theme ->
            listOf(theme.palette.bgSurface, theme.palette.bgElevated)
        }
    }

    @Test
    fun transitionPickerUsesSelectedThemeSurfaces() {
        val selectedTheme = mutableStateOf(ApexThemeOption.NEON)
        val rootView = AtomicReference<View?>()
        compose.setContent {
            ApexTheme(option = selectedTheme.value) {
                val composeView = LocalView.current
                SideEffect { rootView.set(composeView) }
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
        assertThemeColors(rootView, selectedTheme, "transition-picker", "Transition picker") { theme ->
            listOf(theme.palette.bgSurface, theme.palette.bgElevated)
        }
    }

    @Test
    fun trimFilmstripPlaceholderUsesSelectedThemeSurface() {
        val selectedTheme = mutableStateOf(ApexThemeOption.NEON)
        val rootView = AtomicReference<View?>()
        val clip = MediaClip(
            id = "theme-test-clip",
            name = "Theme test clip",
            uri = "file:///does-not-exist-theme-test.mp4",
            durationMs = 10_000L,
            trimEndMs = 10_000L
        )
        compose.setContent {
            ApexTheme(option = selectedTheme.value) {
                val composeView = LocalView.current
                SideEffect { rootView.set(composeView) }
                Box(Modifier.fillMaxWidth().testTag("trim-filmstrip")) {
                    TrimFilmstrip(
                        clip = clip,
                        trimStart = 0L,
                        trimEnd = 10_000L,
                        duration = 10_000L,
                        minimumTrimSpan = 100L,
                        onTrimChange = { _, _ -> }
                    )
                }
            }
        }
        assertThemeColors(rootView, selectedTheme, "trim-filmstrip", "Trim filmstrip") { theme ->
            listOf(theme.palette.bgElevated)
        }
    }

    private fun assertThemeColors(
        rootView: AtomicReference<View?>,
        selectedTheme: MutableState<ApexThemeOption>,
        tag: String,
        label: String,
        expectedColors: (ApexThemeOption) -> List<Color>
    ) {
        ApexThemeOption.entries.forEach { option ->
            compose.runOnIdle { selectedTheme.value = option }
            compose.waitForIdle()
            val image = captureComposeNode(rootView, tag)
            expectedColors(option).forEach { color ->
                assertContainsColorNear(image, color, "$label ${option.displayName}")
            }
        }
    }

    private fun captureComposeNode(rootView: AtomicReference<View?>, tag: String): ImageBitmap {
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val capturedImage = AtomicReference<ImageBitmap?>()
        compose.runOnIdle {
            val view = requireNotNull(rootView.get()) { "Compose root view was not observed" }
            check(view.width > 0 && view.height > 0) {
                "Compose root view has no laid-out size: ${view.width}x${view.height}"
            }
            check(
                bounds.left >= 0f &&
                    bounds.top >= 0f &&
                    bounds.right <= view.width.toFloat() &&
                    bounds.bottom <= view.height.toFloat()
            ) {
                "Panel $tag bounds $bounds extend beyond Compose root ${view.width}x${view.height}"
            }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val left = floor(bounds.left).toInt().coerceIn(0, view.width - 1)
            val top = floor(bounds.top).toInt().coerceIn(0, view.height - 1)
            val right = ceil(bounds.right).toInt().coerceIn(left + 1, view.width)
            val bottom = ceil(bounds.bottom).toInt().coerceIn(top + 1, view.height)
            val panelBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            capturedImage.set(panelBitmap.asImageBitmap())
        }
        return requireNotNull(capturedImage.get()) { "Compose panel bitmap was not captured" }
    }

    private fun assertContainsColorNear(image: ImageBitmap, expected: Color, label: String) {
        val pixels = image.toPixelMap()
        var matchingPixels = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val actual = pixels[x, y]
                val redDistance = (actual.red - expected.red) * 255f
                val greenDistance = (actual.green - expected.green) * 255f
                val blueDistance = (actual.blue - expected.blue) * 255f
                val rgbDistanceSquared =
                    redDistance * redDistance +
                        greenDistance * greenDistance +
                        blueDistance * blueDistance
                if (
                    rgbDistanceSquared <= MAX_RGB_DISTANCE * MAX_RGB_DISTANCE &&
                    actual.alpha >= 0.98f
                ) {
                    matchingPixels++
                }
            }
        }
        assertTrue(
            "Expected selected-theme surface pixels for $label; found $matchingPixels",
            matchingPixels >= MIN_MATCHING_SURFACE_PIXELS
        )
    }
}
