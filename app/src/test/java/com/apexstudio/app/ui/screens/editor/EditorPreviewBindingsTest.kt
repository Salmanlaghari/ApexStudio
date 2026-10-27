package com.apexstudio.app.ui.screens.editor

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import com.apexstudio.app.data.effect.WorkspaceEffectGlEffect
import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class EditorPreviewBindingsTest {
    @Test fun `actual preview effect chain keeps stable slots for live numeric drafts`() {
        val initial = WorkspaceEffect(
            id = WorkspaceEffectPresets.FILTER_LAVENDER,
            startMs = 1_000L,
            endMs = 4_000L,
            intensity = 0.35f,
        )
        val selected = workspaceEffectPreviewBinding(listOf(initial))
        val numericDraft = workspaceEffectPreviewBinding(listOf(initial.copy(
            startMs = 1_500L,
            endMs = 4_500L,
            intensity = 0.9f,
            exposure = 0.5f,
        )))
        val changed = workspaceEffectPreviewBinding(listOf(initial.copy(
            id = WorkspaceEffectPresets.FILTER_SOFT_WARM,
            startMs = 2_000L,
            endMs = 5_000L,
            intensity = 0.9f,
        )))

        // Intensity/range/grade values are read from the live atomic draft per frame.
        // Only adding/removing/replacing effect slots changes the Player stage identity.
        assertEquals(listOf(initial), selected.sourceEffects)
        assertEquals(1, selected.renderEffects.size)
        assertTrue(selected.renderEffects.single() is WorkspaceEffectGlEffect)
        assertEquals(selected.renderStageIds, numericDraft.renderStageIds)
        assertEquals(1, numericDraft.renderEffects.size)
        assertNotEquals(selected.renderStageIds, changed.renderStageIds)
        assertEquals(1, changed.renderEffects.size)
        assertTrue(changed.renderEffects.single() is GlEffect)
    }

    @Test fun `zero-strength live slot remains attached so increasing it does not rebuild preview`() {
        val inactive = WorkspaceEffect(WorkspaceEffectPresets.FILTER_LAVENDER, 1_000L, 3_000L, intensity = 0f)
        val binding = workspaceEffectPreviewBinding(listOf(inactive))
        assertEquals(listOf(inactive.id), binding.renderStageIds)
        assertEquals(1, binding.renderEffects.size)
    }

    @Test fun `empty or invalid draft does not leave an effect attached to preview`() {
        val active = WorkspaceEffect(WorkspaceEffectPresets.FILTER_LAVENDER, 1_000L, 3_000L)
        assertEquals(1, workspaceEffectPreviewBinding(listOf(active)).renderEffects.size)
        assertTrue(workspaceEffectPreviewBinding(emptyList()).renderEffects.isEmpty())
        assertTrue(workspaceEffectPreviewBinding(listOf(active.copy(startMs = 3_000L, endMs = 3_000L))).renderEffects.isEmpty())
    }
}
