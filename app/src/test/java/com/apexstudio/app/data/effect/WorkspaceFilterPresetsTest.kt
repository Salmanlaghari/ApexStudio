package com.apexstudio.app.data.effect

import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import com.apexstudio.app.domain.model.WorkspaceFilterPresetAdditions
import com.apexstudio.app.domain.model.WorkspaceFilterPresets
import com.apexstudio.app.domain.model.hasValidParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilterPresetsTest {
    @Test fun `catalog preserves twelve looks and adds fifty distinct supported recipes`() {
        val original = listOf("soft_warm", "cool", "faded", "amber", "rose", "mint", "teal_orange",
            "crisp", "matte", "bw_contrast", "sepia", "lavender").map { "filter_$it" }.toSet()
        assertEquals(50, WorkspaceFilterPresetAdditions.entries.size)
        assertEquals(50, WorkspaceFilterPresetAdditions.entries.map { it.parameters }.distinct().size)
        assertEquals(62, WorkspaceFilterPresets.all.size)
        assertEquals(62, WorkspaceFilterPresets.all.map { it.id }.distinct().size)
        assertEquals(original, WorkspaceFilterPresets.all.map { it.id }.filter { it !in WorkspaceFilterPresetAdditions.ids }.toSet())
        assertTrue(WorkspaceFilterPresets.all.all {
            it.id in WorkspaceEffectPresets.all && it.id in WorkspaceEffectPresets.filterIds && it.description.isNotBlank()
        })
    }

    @Test fun `old four argument effects retain neutral customization while custom controls validate`() {
        val oldSaved = WorkspaceEffect(WorkspaceEffectPresets.MONO, 100L, 900L, 0.65f)
        assertEquals(0f, oldSaved.exposure, 0f)
        assertEquals(1f, oldSaved.contrast, 0f)
        assertEquals(1f, oldSaved.saturation, 0f)
        assertTrue(oldSaved.hasValidParameters(1_000L))

        val customized = oldSaved.copy(exposure = -0.75f, contrast = 1.35f, saturation = 0.4f)
        assertTrue(customized.hasValidParameters(1_000L))
        assertFalse(customized.copy(exposure = Float.NaN).hasValidParameters())
        assertFalse(customized.copy(contrast = 4f).hasValidParameters())
        assertFalse(customized.copy(saturation = -0.1f).hasValidParameters())
        assertFalse(customized.hasValidParameters(500L))
    }
}
