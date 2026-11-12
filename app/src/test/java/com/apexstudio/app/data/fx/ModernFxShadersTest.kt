package com.apexstudio.app.data.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun `split tone smoothstep edges stay ordered at parameter extremes`() {
        val extremes = listOf(
            1.0f to 0.9f, // the combination Kilo flagged on PR #232
            1.0f to 0.0f,
            0.99f to 0.99f,
            0.5f to 0.0f,
            0.0f to 0.0f,
            0.0f to -1.0f,
            -1.0f to -1.0f,
            2.0f to 2.0f,
            Float.NaN to Float.NaN,
            Float.POSITIVE_INFINITY to Float.NEGATIVE_INFINITY
        )
        for ((a, b) in extremes) {
            val (edge0, width) = ModernFxShaders.splitToneEdges(a, b)
            assertTrue("edge0 must be in range for a=$a b=$b", edge0 in 0f..0.98f)
            assertTrue(
                "width must be at least the minimum for a=$a b=$b",
                width >= ModernFxShaders.SPLIT_TONE_MIN_WIDTH
            )
            assertTrue("edge0 must be < edge1 for a=$a b=$b", edge0 < edge0 + width)
            assertTrue(
                "edge1 must stay at the cap for a=$a b=$b",
                edge0 + width <= ModernFxShaders.SPLIT_TONE_MAX_EDGE1 + 0.0001f
            )
        }
        // Already-valid combinations pass through unchanged.
        val (validEdge0, validWidth) = ModernFxShaders.splitToneEdges(0.24f, 0.38f)
        assertEquals(0.24f, validEdge0, 0.0f)
        assertEquals(0.38f, validWidth, 0.0f)
    }

    @Test
    fun `split tone shader sanitises unordered parameters and orders its edges`() {
        val effect = DownloadedFxEffect(
            id = "dl_test_split_tone",
            label = "Split Tone Extreme",
            category = "Creator Pack",
            family = "SPLIT_TONE",
            a = 1.0f,
            b = 0.9f,
            c = 0.5f,
            tintR = 1.0f,
            tintG = 0.5f,
            tintB = 0.25f,
            isAnimated = false,
            defaultSpeed = 1.0f
        )
        val shader = ModernFxShaders.shaderFor(effect)
        assertNotNull("SPLIT_TONE effect should create a shader", shader)
        // a=1.0 / b=0.9 is sanitised to the ordered pair 0.98 / 0.01 before
        // substitution, so the const parameters can never be unordered.
        assertTrue("a must be clamped below the upper edge", shader!!.contains("const float P_A = 0.98"))
        assertTrue("b must be clamped to the minimum width", shader.contains("const float P_B = 0.01"))
        // And the GLSL itself orders the edges before calling smoothstep.
        assertTrue(
            "shader must derive ordered edges",
            shader.contains("float splitEdge1 = clamp(splitEdge0 + max(P_B, 0.01), splitEdge0 + 0.01, 0.99)")
        )
        assertTrue(
            "smoothstep must use the ordered edges",
            shader.contains("smoothstep(splitEdge0, splitEdge1")
        )
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
