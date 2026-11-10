package com.apexstudio.app.data.gl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PackedTransitionShaderSourceTest {
    @Test
    fun `helper bodies are rewritten and packed coordinates clamp both axes`() {
        val rewritten = PackedTransitionShaderSource.rewrite(shader())

        assertNotNull(rewritten)
        assertTrue(rewritten!!.contains("vec4 getFromColor(vec2 uv) { return texture(uPacked, packedFrom(uv)); }"))
        assertTrue(rewritten.contains("vec4 getToColor(vec2 uv) { return texture(uPacked, packedTo(uv)); }"))
        assertTrue(rewritten.contains("vec2 c = clamp(uv, vec2(0.0), vec2(1.0));"))
        assertTrue(rewritten.contains("return vec2(c.x * 0.5, c.y);"))
        assertTrue(rewritten.contains("return vec2(0.5 + c.x * 0.5, c.y);"))
        assertFalse(rewritten.contains("uniform sampler2D uFrom;"))
        assertFalse(rewritten.contains("uniform sampler2D uTo;"))
        assertEquals(1, Regex("uniform sampler2D uPacked;").findAll(rewritten).count())
    }

    @Test
    fun `nested function arguments are balanced before rewriting`() {
        val source = shader().replace(
            "getFromColor(uv)",
            "texture(uFrom, mix(clamp(uv + vec2(sin(uProgress), cos(uProgress)), vec2(0.0), vec2(1.0)), uv, smoothstep(0.0, 1.0, uProgress)))"
        )

        val rewritten = PackedTransitionShaderSource.rewrite(source)

        assertNotNull(rewritten)
        assertTrue(rewritten!!.contains("texture(uPacked, packedFrom(mix(clamp(uv + vec2(sin(uProgress), cos(uProgress)), vec2(0.0), vec2(1.0)), uv, smoothstep(0.0, 1.0, uProgress))))"))
    }

    @Test
    fun `direct multiple samples are all rewritten`() {
        val source = shader().replace(
            "return mix(getFromColor(uv), getToColor(uv), uProgress);",
            "return texture(uFrom, uv) + texture(uTo, displace(texture(uFrom, vec2(uv.x, sin(uv.y))), uv));"
        )

        val rewritten = PackedTransitionShaderSource.rewrite(source)

        assertNotNull(rewritten)
        assertEquals(3, rewritten!!.split("texture(uPacked, packedFrom(").size - 1)
        assertEquals(2, rewritten.split("texture(uPacked, packedTo(").size - 1)
        assertFalse(rewritten.contains("texture(uFrom"))
        assertFalse(rewritten.contains("texture(uTo"))
    }

    @Test
    fun `progress ratio and unrelated uniforms remain unchanged`() {
        val rewritten = PackedTransitionShaderSource.rewrite(shader())!!

        assertTrue(rewritten.contains("uniform float uProgress;"))
        assertTrue(rewritten.contains("uniform float uRatio;"))
        assertTrue(rewritten.contains("uniform float uStrength;"))
    }

    @Test
    fun `lexer ignores quoted literals numeric literals and continued preprocessor directives`() {
        val injected = """#define UNUSED_SAMPLE texture(uFrom, uv) \
+ texture(uTo, uv)
#define UNUSED_LABEL "texture(uFrom, uv)"
const float numeric = 1e-3 + .25 + 10u;
const char* label = "texture(uFrom, uv)";
vec4 transition(vec2 uv) {"""
        val source = shader().replace("vec4 transition(vec2 uv) {", injected)

        val rewritten = PackedTransitionShaderSource.rewrite(source)

        assertNotNull(rewritten)
        assertTrue(rewritten!!.contains("#define UNUSED_SAMPLE texture(uFrom, uv)"))
        assertTrue(rewritten.contains("+ texture(uTo, uv)"))
        assertTrue(rewritten.contains("#define UNUSED_LABEL \"texture(uFrom, uv)\""))
        assertTrue(rewritten.contains("const float numeric = 1e-3 + .25 + 10u;"))
        assertTrue(rewritten.contains("const char* label = \"texture(uFrom, uv)\";"))
        assertTrue(rewritten.contains("texture(uPacked, packedFrom(uv))"))
        assertTrue(rewritten.contains("texture(uPacked, packedTo(uv))"))
    }

    @Test
    fun `unsupported active sampler use is rejected`() {
        val source = shader().replace(
            "return mix(getFromColor(uv), getToColor(uv), uProgress);",
            "return textureProj(uFrom, vec3(uv, 1.0)) + getToColor(uv);"
        )

        assertNull(PackedTransitionShaderSource.rewrite(source))
    }

    @Test
    fun `all bundled transition shaders support packed A and B sampling`() {
        val assetRoots = listOf(
            File("app/src/main/assets/gl_transitions"),
            File("src/main/assets/gl_transitions")
        )
        val assetRoot = assetRoots.firstOrNull { it.isDirectory }
        assertNotNull("Transition assets directory is required for this test", assetRoot)
        val shaders = assetRoot!!.listFiles { file -> file.extension == "glsl" }.orEmpty()
        assertTrue("Expected the 100-plus bundled transition shaders", shaders.size >= 100)
        val manifestIds = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(File(assetRoot, "manifest.json").readText())
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(manifestIds, BundledPackedTransitionIds.all)

        val unsupported = shaders.filter { PackedTransitionShaderSource.rewrite(it.readText()) == null }
        assertTrue(
            "Shaders cannot be applied as real project transitions: ${unsupported.map { it.name }.take(12)}",
            unsupported.isEmpty()
        )
    }

    private fun shader(): String = """
        #version 300 es
        uniform sampler2D uFrom;
        uniform sampler2D uTo;
        uniform float uProgress;
        uniform float uRatio;
        uniform float uStrength;
        vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
        vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
        vec2 displace(vec4 color, vec2 uv) { return uv + color.xy; }
        vec4 transition(vec2 uv) {
            return mix(getFromColor(uv), getToColor(uv), uProgress);
        }
    """.trimIndent()
}
