package com.apexstudio.app.data.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FxShaderCatalogTest {
    @Test
    fun `all FX gallery presets resolve to unique concrete fragment shaders`() {
        val presets = FxPreset.values()
        assertTrue("Expected a 50+ effect catalog", presets.size >= 50)
        assertEquals("FX ids must be stable and unique", presets.size, presets.map { it.id }.toSet().size)

        val shaders = presets.associateWith(FxGlEffect::shaderSourceFor)
        for ((preset, shader) in shaders) {
            assertTrue("${preset.id} needs a fragment main", shader.contains("void main()"))
            assertTrue("${preset.id} needs to write pixels", shader.contains("gl_FragColor"))
            assertTrue("${preset.id} needs to sample source pixels", shader.contains("texture2D(uTexSampler"))
            if (preset.isAnimated) {
                assertTrue("${preset.id} is animated but has no time uniform", shader.contains("uniform float uTime"))
                assertTrue("${preset.id} is animated but has no speed uniform", shader.contains("uSpeed"))
            }
        }
        assertTrue("At least 50 distinct shader programs should be present", shaders.values.toSet().size >= 50)
        assertFalse("No preset should resolve to a blank shader", shaders.values.any { it.isBlank() })
    }
}
