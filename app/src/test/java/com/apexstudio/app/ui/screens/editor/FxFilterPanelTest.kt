package com.apexstudio.app.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.apexstudio.app.data.filter.FilterCategory
import com.apexstudio.app.data.filter.FilterManifest
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import org.junit.Assert.assertEquals
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

        compose.onNodeWithTag("filter-source-range").performScrollTo()
        compose.onNodeWithTag("filter-close").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}
