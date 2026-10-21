package com.apexstudio.app.data.effect

import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import com.apexstudio.app.domain.model.isActiveAtSourceTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceEffectGlEffectTest {
    @Test fun `trimmed export source clock includes clip offset and excludes preroll`() {
        val effect = WorkspaceEffect(WorkspaceEffectPresets.MONO, 4_000L, 5_000L)
        assertEquals(4_000L, WorkspaceEffectGlEffect.sourceTimeMs(4_000_000L, 0L))
        assertEquals(4_000L, WorkspaceEffectGlEffect.sourceTimeMs(1_000_000L, 3_000L))
        assertTrue(effect.isActiveAtSourceTime(WorkspaceEffectGlEffect.sourceTimeMs(1_000_000L, 3_000L)))
        assertFalse(effect.isActiveAtSourceTime(WorkspaceEffectGlEffect.sourceTimeMs(2_000_000L, 3_000L)))
        assertEquals(-1L, WorkspaceEffectGlEffect.sourceTimeMs(-1L, 3_000L))
        assertEquals(Long.MAX_VALUE, WorkspaceEffectGlEffect.sourceTimeMs(Long.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test
    fun sourceWindowIsInclusiveAtStartAndExclusiveAtEnd() {
        val effect = WorkspaceEffect(WorkspaceEffectPresets.MONO, startMs = 1_250L, endMs = 2_000L)

        assertFalse(effect.isActiveAtSourceTime(1_249L))
        assertTrue(effect.isActiveAtSourceTime(1_250L))
        assertTrue(effect.isActiveAtSourceTime(1_999L))
        assertFalse(effect.isActiveAtSourceTime(2_000L))
    }

    @Test
    fun invalidRangeUnknownPresetAndZeroIntensityNeverActivate() {
        assertFalse(WorkspaceEffect("unknown", 10L, 20L).isActiveAtSourceTime(15L))
        assertFalse(WorkspaceEffect(WorkspaceEffectPresets.WARM, 20L, 20L).isActiveAtSourceTime(20L))
        assertFalse(WorkspaceEffect(WorkspaceEffectPresets.WARM, -1L, 20L).isActiveAtSourceTime(10L))
        assertFalse(WorkspaceEffect(WorkspaceEffectPresets.WARM, 10L, 20L, intensity = 0f).isActiveAtSourceTime(10L))
    }

    @Test
    fun allPresetsUseTheCanonicalGlEffectFactoryAndShader() {
        val effects = WorkspaceEffectPresets.all.mapIndexed { index, id ->
            WorkspaceEffect(id, startMs = index * 1_000L, endMs = index * 1_000L + 500L)
        }
        val clip = MediaClip(
            id = "clip", name = "clip", uri = "file:///clip.mp4", durationMs = 10_000L,
            trimEndMs = 10_000L, workspaceEffects = effects
        )

        assertEquals(effects.size, WorkspaceEffectGlEffect.createEffects(effects).size)
        assertEquals(effects.size, WorkspaceEffectGlEffect.forClip(clip).size)
        // Both routes instantiate WorkspaceEffectGlEffect through the same factory; the GLSL
        // contains one shared split implementation whose local UV maps each tile to source 0..1.
        val shader = WorkspaceEffectGlEffect.fragmentShaderSource()
        assertTrue(shader.contains("fract(vTextureCoord.x * count)"))
        assertTrue(shader.contains("texture2D(uTexSampler, sourceUv)"))
        assertTrue(shader.contains("uEnabled * uIntensity"))
        assertTrue(shader.contains("uniform float uElapsed"))
        assertTrue(shader.contains("float heartField(vec2 p)"))
        assertTrue(shader.contains("vec3 bubbleLook(vec2 uv)"))
        assertTrue(shader.contains("float ink = smoothstep(0.12, 0.38, edge)"))
        assertTrue(shader.contains("float phase = fract(uElapsed * 1.15)"))
        assertTrue(WorkspaceEffectPresets.all.containsAll(setOf(
            WorkspaceEffectPresets.MONO,
            WorkspaceEffectPresets.WARM,
            WorkspaceEffectPresets.PIXELATE,
            WorkspaceEffectPresets.SPLIT_2,
            WorkspaceEffectPresets.SPLIT_3,
            WorkspaceEffectPresets.CARTOON,
            WorkspaceEffectPresets.BUBBLE,
            WorkspaceEffectPresets.HEARTS,
            WorkspaceEffectPresets.HEARTBEAT,
            WorkspaceEffectPresets.ICE
        )))
        assertEquals("Cartoon · posterized", WorkspaceEffectPresets.label(WorkspaceEffectPresets.CARTOON))
        assertEquals("Hearts · procedural", WorkspaceEffectPresets.label(WorkspaceEffectPresets.HEARTS))
    }
}
