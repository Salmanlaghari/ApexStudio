package com.apexstudio.app.data.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernFxShadersTest {
    private val creatorPresets = FxPreset.values().filter { it.category == "Creator Pack" }

    @Test
    fun `creator pack contains at least fifty new stable unique profiles`() {
        assertEquals("Creator Pack should contain fifty original profiles", 50, creatorPresets.size)
        assertEquals(creatorPresets.size, creatorPresets.map { it.id }.toSet().size)
        assertTrue(creatorPresets.all { it.id.startsWith("creator_") })
        assertTrue("The category must be exposed to consumers", FxPreset.categories().contains("Creator Pack"))
    }

    @Test
    fun `every new id dispatches to its own complete GLSL ES shader`() {
        val sources = creatorPresets.associateWith(FxGlEffect::shaderSourceFor)
        assertEquals("Every Creator Pack preset must have a shader", creatorPresets.size, sources.size)
        assertEquals("Profiles must use distinct shader programs", creatorPresets.size, sources.values.toSet().size)
        for ((preset, source) in sources) {
            assertTrue("${preset.id} must define main", source.contains("void main()"))
            assertTrue("${preset.id} must sample the frame", source.contains("texture2D(uTexSampler"))
            assertTrue("${preset.id} must declare time", source.contains("uniform float uTime"))
            assertTrue("${preset.id} must declare texel size", source.contains("uniform vec2 uTexel"))
            assertTrue("${preset.id} must mix the effect at the requested strength", source.contains("mix(base.rgb, effected, clamp(uIntensity, 0.0, 1.0))"))
            if (preset.isAnimated) {
                assertTrue("${preset.id} must have a speed uniform", source.contains("uniform float uSpeed"))
                assertTrue("${preset.id} must use its speed uniform", source.contains("uTime * uSpeed"))
            }
        }
    }

    @Test
    fun `all ten spatial temporal shader families have concrete variants`() {
        val shaders = creatorPresets.associate { it.id to FxGlEffect.shaderSourceFor(it) }
        val familyMarkers = listOf(
            "redSample", "shadowTint", "crossWave", "centerUv", "phosphor",
            "highlight", "grid = mod", "folded", "noise = fract", "radialWeight"
        )
        for (marker in familyMarkers) {
            assertTrue("Missing shader family marker $marker", shaders.values.any { it.contains(marker) })
        }
        assertFalse("No new preset may resolve to an empty shader", shaders.values.any { it.isBlank() })
    }
}
