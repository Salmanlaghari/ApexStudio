package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.fx.DownloadedFxEffect
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.data.filter.FilterCategory
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h830dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FxFilterPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `fx category and preset remain selectable with Close pinned`() {
        var selectedFx: String? = null
        var closed = false
        compose.setContent {
            Box(Modifier.width(375.dp).height(240.dp)) {
                FxPanel(
                    activeFxId = selectedFx,
                    intensity = 0.7f,
                    onFxSelected = { selectedFx = it },
                    onIntensityChange = {},
                    speed = 1f,
                    onSpeedChange = {},
                    onClose = { closed = true }
                )
            }
        }

        compose.onNodeWithTag("fx-category-animated").performScrollTo().performClick()
        compose.onNodeWithText("Hue Cycle").performClick()
        compose.runOnIdle { assertEquals("hue_cycle", selectedFx) }
        compose.onNodeWithContentDescription("Close FX").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test
    fun `intensity and animated speed sliders commit when gestures finish`() {
        val intensity = mutableStateOf(0.5f)
        val speed = mutableStateOf(1f)
        var intensityCommits = 0
        var speedCommits = 0
        compose.setContent {
            Box(Modifier.width(375.dp).height(240.dp)) {
                FxPanel(
                    activeFxId = FxPreset.HUE_CYCLE.id,
                    intensity = intensity.value,
                    onFxSelected = {},
                    onIntensityChange = { intensity.value = it },
                    onIntensityChangeFinished = { intensityCommits++ },
                    speed = speed.value,
                    onSpeedChange = { speed.value = it },
                    onSpeedChangeFinished = { speedCommits++ },
                    onClose = {}
                )
            }
        }

        compose.onNodeWithTag("fx-intensity-slider").performScrollTo().performTouchInput { swipeRight() }
        compose.onNodeWithTag("fx-speed-slider").performScrollTo().performTouchInput { swipeRight() }
        compose.runOnIdle {
            assertEquals(1, intensityCommits)
            assertEquals(1, speedCommits)
        }
    }

    @Test
    fun `downloaded effects can be fetched and selected from All and Downloaded categories`() {
        val effect = DownloadedFxEffect(
            id = "dl_apex-fx-vol-01_prism_sample",
            label = "Prism Sample",
            category = "Creator Pack",
            family = "PRISM",
            a = 1.5f,
            b = 0.12f,
            c = 0.38f,
            tintR = 0.2f,
            tintG = 0.7f,
            tintB = 0.9f,
            isAnimated = true,
            defaultSpeed = 1.2f
        )
        val selectedFx = mutableStateOf<String?>(null)
        val speed = mutableStateOf(1f)
        var downloadRequests = 0
        val profiles = mutableStateOf(emptyList<DownloadedFxEffect>())
        val packState = mutableStateOf<PackManager.PackState>(PackManager.PackState.NotDownloaded)
        compose.setContent {
            Box(Modifier.width(375.dp).height(240.dp)) {
                FxPanel(
                    activeFxId = selectedFx.value,
                    intensity = 0.7f,
                    speed = speed.value,
                    onFxSelected = {
                        selectedFx.value = it
                        speed.value = effect.defaultSpeed
                    },
                    onIntensityChange = {},
                    onSpeedChange = { speed.value = it },
                    downloadedEffects = profiles.value,
                    effectsPackState = packState.value,
                    onDownloadEffectsPack = {
                        downloadRequests++
                        packState.value = PackManager.PackState.Downloaded
                        profiles.value = listOf(
                            effect,
                            effect.copy(label = "Duplicate Prism"),
                            effect.copy(id = FxPreset.HUE_CYCLE.id, label = "Built-in ID collision")
                        )
                    },
                    onClose = {}
                )
            }
        }

        repeat(5) {
            compose.onNodeWithTag("fx-category-list").performTouchInput { swipeLeft() }
        }
        compose.onNodeWithTag("fx-category-downloaded").performClick()
        compose.onNodeWithText("Download 30 effects · 5.2 KB").performScrollTo().performClick()

        compose.onNodeWithTag("fx-category-list")
            .performScrollToNode(hasTestTag("fx-category-all"))
        compose.onNodeWithTag("fx-category-all").performClick()
        compose.onNodeWithTag("fx-preset-list").performScrollTo()
        compose.onNodeWithText("Prism Sample").assertIsDisplayed().performClick()
        compose.onAllNodesWithText("Duplicate Prism").assertCountEquals(0)
        compose.onAllNodesWithText("Built-in ID collision").assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(1, downloadRequests)
            assertEquals(effect.id, selectedFx.value)
        }

        compose.onNodeWithText("Original").performClick()
        compose.runOnIdle { assertEquals(null, selectedFx.value) }
        compose.onNodeWithTag("fx-category-list")
            .performScrollToNode(hasTestTag("fx-category-downloaded"))
        compose.onNodeWithTag("fx-category-downloaded").performClick()
        compose.onNodeWithTag("fx-preset-list").performScrollTo()
        compose.onNodeWithText("Prism Sample").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(effect.id, selectedFx.value) }
        compose.onNodeWithText("1.2x").performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag("fx-category-list")
            .performScrollToNode(hasTestTag("fx-category-animated"))
        val animatedCategory = compose.onNodeWithTag("fx-category-animated")
        animatedCategory.performScrollTo()
        animatedCategory.assertIsDisplayed()
        assertFalse(
            "Animated category should start unselected",
            animatedCategory.fetchSemanticsNode().config[SemanticsProperties.Selected]
        )
        animatedCategory.performClick()
        compose.waitForIdle()
        animatedCategory.assertIsSelected()
        compose.onNodeWithTag("fx-preset-list").performScrollTo()
        compose.onNodeWithText("Hue Cycle").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Prism Sample").assertCountEquals(0)
        compose.runOnIdle { assertEquals(effect.id, selectedFx.value) }
    }

    @Test
    fun `filter category and preset selection use their real callbacks`() {
        val film = FilterPreset("film", "Film Look", "Film", "luts/film.cube")
        val soft = FilterPreset("soft", "Soft Portrait", "Portrait", "luts/soft.cube")
        val manifest = FilterManifest(
            categories = listOf(
                FilterCategory("film", "Film", listOf(film)),
                FilterCategory("portrait", "Portrait", listOf(soft))
            ),
            filters = listOf(film, soft)
        )
        val category = mutableStateOf("film")
        val selected = mutableStateOf<String?>(null)

        compose.setContent {
            FilterPanel(
                manifest = manifest,
                activeFilterId = selected.value,
                intensity = 0.5f,
                activeCategory = category.value,
                onCategoryChange = { category.value = it },
                onFilterSelected = { selected.value = it },
                onIntensityChange = {},
                onClose = {}
            )
        }

        compose.onNodeWithTag("filter-category-portrait").performClick()
        compose.onNodeWithTag("filter-chip-soft").performClick()
        compose.runOnIdle {
            assertEquals("portrait", category.value)
            assertEquals("soft", selected.value)
        }
    }

    @Test
    fun `filter workspace keeps Close reachable while draft controls scroll`() {
        val clip = MediaClip("video", "Video", "file:///source.mp4", 5_000L, 1_000L, 4_000L)
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(
                project = Project("p", "Project", 3_000L, clips = listOf(clip)),
                selectedClipId = clip.id
            )
        }
        var closed = false
        compose.setContent {
            FilterWorkspacePanel("p", clip, vm, false, {}, {}, {}, { closed = true })
        }

        compose.onNodeWithTag("filter-details-toggle").performClick()
        compose.onNodeWithTag("filter-source-range").performScrollTo()
        compose.onNodeWithTag("filter-close").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}
