package com.apexstudio.app.data.template

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.applyTransmissionTemplate
import com.apexstudio.app.presentation.viewmodel.loadTransmissionTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortsTemplateRendererCompatibilityTest {
    @Test
    fun `every Shorts look resolves to a bundled LUT and live FX renderer`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifest = LutFilterEngine(context).manifest
        val templates = ShortsTemplateCatalog.asTransmissionTemplates()

        assertEquals(10, templates.size)
        templates.forEach { template ->
            assertNotNull("Missing LUT for ${template.id}: ${template.filterId}", manifest.presetById(template.filterId))
            assertNotNull("Missing FX for ${template.id}: ${template.fxPresetId}", FxPreset.byId(template.fxPresetId))
            assertTrue(template.transitionType in setOf("cross", "wipe", "zoom", "slide", "glitch"))
        }
    }

    @Test
    fun `saved Shorts look is available and applies its filter and effect in the editor`() {
        val template = ShortsTemplateCatalog.templates.first().asTransmissionTemplate()
        val viewModel = EditorViewModel()
        val project = Project(
            id = "shorts-template-apply",
            name = "Shorts project",
            durationMs = 5_000L,
            clips = listOf(
                MediaClip("video", "video.mp4", "file:///video.mp4", 5_000L, trimEndMs = 5_000L, type = ClipType.VIDEO)
            ),
            lastTransmissionTemplateId = template.id
        )
        viewModel._state.value = viewModel.state.value.copy(project = project)
        viewModel.loadTransmissionTemplates()

        assertTrue(viewModel.transmissionTemplates.value.any { it.id == template.id })
        viewModel.applyTransmissionTemplate(template.id)

        assertEquals(template.filterId, viewModel.state.value.activeFilterId)
        assertEquals(template.filterIntensity, viewModel.state.value.filterIntensity)
        assertEquals(template.fxPresetId, viewModel.state.value.activeFxId)
        assertEquals(template.fxIntensity, viewModel.state.value.fxIntensity)
    }
}
