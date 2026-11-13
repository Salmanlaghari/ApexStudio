package com.apexstudio.app.ui.screens.editor

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import com.apexstudio.app.data.effect.WorkspaceEffectGlEffect
import com.apexstudio.app.data.fx.DownloadedFxEffect
import com.apexstudio.app.data.fx.FxGlEffect
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test fun `paused preview refresh key tracks numeric drafts but playing preview stays per-frame`() {
        val initial = WorkspaceEffect(WorkspaceEffectPresets.FILTER_LAVENDER, 1_000L, 3_000L, intensity = 0.4f)
        val changed = initial.copy(intensity = 0.8f, startMs = 1_500L)

        assertEquals(listOf(initial), workspaceEffectPausedRefreshSnapshot(listOf(initial), isPlaying = false))
        assertNotEquals(
            workspaceEffectPausedRefreshSnapshot(listOf(initial), isPlaying = false),
            workspaceEffectPausedRefreshSnapshot(listOf(changed), isPlaying = false),
        )
        assertEquals(null, workspaceEffectPausedRefreshSnapshot(listOf(initial), isPlaying = true))
        assertEquals(null, workspaceEffectPausedRefreshSnapshot(listOf(changed), isPlaying = true))
    }

    @Test fun `empty or invalid draft does not leave an effect attached to preview`() {
        val active = WorkspaceEffect(WorkspaceEffectPresets.FILTER_LAVENDER, 1_000L, 3_000L)
        assertEquals(1, workspaceEffectPreviewBinding(listOf(active)).renderEffects.size)
        assertTrue(workspaceEffectPreviewBinding(emptyList()).renderEffects.isEmpty())
        assertTrue(workspaceEffectPreviewBinding(listOf(active.copy(startMs = 3_000L, endMs = 3_000L))).renderEffects.isEmpty())
    }

    // Binding/constructor assertions only: these do not render GL pixels or encode a video.
    private val downloaded = DownloadedFxEffect(
        id = "dl_test_prism", label = "Test Prism", category = "Test", family = "PRISM",
        a = 1.4f, b = 0.1f, c = 0.6f, tintR = 0.8f, tintG = 0.5f, tintB = 0.3f,
        isAnimated = true, defaultSpeed = 1f,
    )

    @Test fun `downloaded FX binding feeds independent normal and transition item renderers`() {
        val binding = requireNotNull(globalFxPreviewBinding(downloaded.id, downloaded, 0.42f, 1.75f))
        assertNull(binding.preset)
        assertSame(downloaded, binding.downloadedEffect)
        assertEquals(0.42f, binding.intensity, 0f)
        assertEquals(1.75f, binding.speed, 0f)
        val normal = binding.createEffect()
        val transitionItem = binding.createEffect()
        assertTrue(normal is FxGlEffect)
        assertTrue(transitionItem is FxGlEffect)
        assertNotSame(normal, transitionItem)
    }

    @Test fun `downloaded arrival replacement parameters and clear invalidate the shared preview key`() {
        assertNull(globalFxPreviewBinding(downloaded.id, null, 0.42f, 1.75f))
        val loaded = requireNotNull(globalFxPreviewBinding(downloaded.id, downloaded, 0.42f, 1.75f))
        assertEquals(loaded, globalFxPreviewBinding(downloaded.id, downloaded, 0.42f, 1.75f))
        assertNotEquals(loaded, globalFxPreviewBinding(downloaded.id, downloaded.copy(a = 2f), 0.42f, 1.75f))
        assertNotEquals(loaded, globalFxPreviewBinding(downloaded.id, downloaded, 0.8f, 1.75f))
        assertNotEquals(loaded, globalFxPreviewBinding(downloaded.id, downloaded, 0.42f, 2.5f))
        assertNull(globalFxPreviewBinding(downloaded.id, null, 0.42f, 1.75f))
        assertNull(globalFxPreviewBinding(null, downloaded, 0.42f, 1.75f))
    }

    @Test fun `pending stale or unknown downloaded selections never reuse another profile`() {
        assertNull(globalFxPreviewBinding("dl_test_another", downloaded, 0.7f, 1f))
        assertNull(globalFxPreviewBinding("unknown", null, 0.7f, 1f))
        assertNull(globalFxPreviewBinding(null, null, 0.7f, 1f))
        listOf(0f, -0.2f, Float.NaN).forEach { strength ->
            assertNull(globalFxPreviewBinding(downloaded.id, downloaded, strength, 1f))
        }
    }

    @Test fun `built-in selection takes precedence and never stacks a stale downloaded FX`() {
        val binding = requireNotNull(globalFxPreviewBinding(FxPreset.HUE_CYCLE.id, downloaded, 0.6f, 1.5f))
        assertEquals(FxPreset.HUE_CYCLE, binding.preset)
        assertNull(binding.downloadedEffect)
        assertEquals(0.6f, binding.intensity, 0f)
        assertEquals(1.5f, binding.speed, 0f)
        assertTrue(binding.createEffect() is FxGlEffect)
    }
}
