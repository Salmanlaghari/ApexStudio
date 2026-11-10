package com.apexstudio.app.data.gl

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.apexstudio.app.data.export.ProjectTransitionTimeline
import java.nio.ByteBuffer
import java.util.LinkedHashMap
import java.util.Random

/**
 * Composition-level effect for a project transition. Its input is the 2W×H
 * texture produced by [ProjectTransitionCompositorSettings]: outgoing A on
 * the left, incoming B on the right. The selected GLSL transition runs on the
 * same A/B frames in both project preview and whole-project export.
 */
@UnstableApi
internal class PackedTransitionGlEffect(
    private val timeline: ProjectTransitionTimeline.Plan
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        PackedTransitionProgram(context, timeline, useHdr)

    companion object {
        /** Pack-only shaders are excluded until their source assets have the same two-input contract. */
        fun supportsTransitionType(id: String): Boolean {
            if (id.startsWith(GlTransitionAdapterEffect.ID_PREFIX)) {
                return id.removePrefix(GlTransitionAdapterEffect.ID_PREFIX) in BundledPackedTransitionIds.all
            }
            return id.lowercase() in BUILTIN_TRANSITION_IDS
        }

        private val BUILTIN_TRANSITION_IDS = setOf(
            "cross_dissolve", "cross", "dissolve", "wipe_left", "wipe",
            "zoom_blur", "zoom", "zoom_push", "slide_left", "slide", "glitch",
            "fade_black", "dip_black", "fade_white", "flash", "dip_white",
            "wipe_right", "wipe_up", "wipe_down", "clock_wipe", "radial_wipe", "circle",
            "slide_right", "slide_up", "light_leak", "cross_blur", "blur",
            "doorway", "pixelize", "crosswarp"
        )
    }
}

/** Typed uniform and generated auxiliary sampler support shared by the packed and legacy adapters. */
internal object TransitionGlSupport {
    private const val TEXTURE_SIZE = 256

    fun createDisplacementTexture(): Int = createTexture(noiseBytes())

    fun createLumaTexture(): Int = createTexture(lumaBytes())

    fun deleteTexture(textureId: Int) {
        if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
    }

    fun bindManifestParameters(
        program: GlProgram,
        params: List<GlTransitionParam>,
        displacementTexture: Int,
        lumaTexture: Int,
        firstTextureUnit: Int
    ) {
        var textureUnit = firstTextureUnit
        params.forEach { param ->
            if (program.getUniformLocation(param.name) < 0) return@forEach
            val values = param.default
            when (param.type.lowercase()) {
                "sampler2d" -> {
                    val textureId = when (param.name) {
                        "uDisplacementMap" -> displacementTexture
                        "uLuma" -> lumaTexture
                        else -> throw IllegalArgumentException("Unsupported transition sampler ${param.name}")
                    }
                    if (textureId == 0) throw IllegalStateException("Transition sampler ${param.name} is unavailable")
                    program.setSamplerTexIdUniform(param.name, textureId, textureUnit++)
                }
                "int", "bool" -> program.setIntUniform(param.name, values.firstOrNull()?.toInt() ?: 0)
                "ivec2" -> program.setIntsUniform(
                    param.name,
                    IntArray(2) { index -> values.getOrElse(index) { values.lastOrNull() ?: 0f }.toInt() }
                )
                "vec2", "vec3", "vec4" -> program.setFloatsUniform(param.name, values.toFloatArray())
                "float" -> program.setFloatUniform(param.name, values.firstOrNull() ?: 0f)
                else -> throw IllegalArgumentException("Unsupported transition uniform type ${param.type}")
            }
        }
    }

    private fun createTexture(bytes: ByteArray): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val texture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val buffer = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); position(0) }
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, TEXTURE_SIZE, TEXTURE_SIZE,
            0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
        )
        return texture
    }

    private fun noiseBytes(): ByteArray {
        val random = Random(0x4150455853545544L)
        val bytes = ByteArray(TEXTURE_SIZE * TEXTURE_SIZE * 4)
        var index = 0
        repeat(TEXTURE_SIZE * TEXTURE_SIZE) {
            val value = random.nextInt(256).toByte()
            bytes[index++] = value
            bytes[index++] = value
            bytes[index++] = value
            bytes[index++] = 0xFF.toByte()
        }
        return bytes
    }

    private fun lumaBytes(): ByteArray {
        val bytes = ByteArray(TEXTURE_SIZE * TEXTURE_SIZE * 4)
        var index = 0
        for (y in 0 until TEXTURE_SIZE) {
            for (x in 0 until TEXTURE_SIZE) {
                val value = ((0.7f * x + 0.3f * y) / (TEXTURE_SIZE - 1) * 255f).toInt().coerceIn(0, 255).toByte()
                bytes[index++] = value
                bytes[index++] = value
                bytes[index++] = value
                bytes[index++] = 0xFF.toByte()
            }
        }
        return bytes
    }
}

@UnstableApi
private class PackedTransitionProgram(
    context: Context,
    private val timeline: ProjectTransitionTimeline.Plan,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {
    private val appContext = context.applicationContext
    private val manifest = GlTransitionManifest.load(appContext).associateBy { it.id }
    private val builtinProgram = GlProgram(VERTEX_SHADER, BUILTIN_FRAGMENT_SHADER).apply {
        setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
    }
    private val programCache = LinkedHashMap<String, ProgramEntry>(4, 0.75f, true)
    private val displacementTexture = TransitionGlSupport.createDisplacementTexture()
    private val lumaTexture = TransitionGlSupport.createLumaTexture()
    private var aspectRatio = 16f / 9f

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (inputHeight > 0 && inputWidth > 1) {
            aspectRatio = inputWidth.toFloat() / 2f / inputHeight.toFloat()
        }
        return Size((inputWidth / 2).coerceAtLeast(1), inputHeight.coerceAtLeast(1))
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val transition = timeline.transitionAt(presentationTimeUs / 1_000L)
        try {
            if (transition == null) {
                drawBuiltin(inputTexId, progress = 0f, type = 0)
                return
            }
            val type = transition.transition.type
            if (type.startsWith(GlTransitionAdapterEffect.ID_PREFIX)) {
                drawGlPro(inputTexId, presentationTimeUs, transition, type.removePrefix(GlTransitionAdapterEffect.ID_PREFIX))
            } else {
                val builtinType = builtinType(type)
                    ?: throw IllegalArgumentException("Unsupported project transition '$type'")
                val progress = ((presentationTimeUs / 1_000L - transition.startMs).toFloat() /
                    transition.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                drawBuiltin(inputTexId, progress, builtinType)
            }
        } catch (e: VideoFrameProcessingException) {
            throw e
        } catch (e: Exception) {
            throw VideoFrameProcessingException(
                "Could not render project transition at ${presentationTimeUs}us: ${e.message}",
                e
            )
        }
    }

    private fun drawGlPro(
        inputTexId: Int,
        presentationTimeUs: Long,
        transition: ProjectTransitionTimeline.Transition,
        glId: String
    ) {
        val entry = programFor(glId)
        val progress = ((presentationTimeUs / 1_000L - transition.startMs).toFloat() /
            transition.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        val program = entry.program
        program.use()
        program.setSamplerTexIdUniform("uPacked", inputTexId, 0)
        program.setFloatsUniformIfPresent("uProgress", floatArrayOf(progress))
        program.setFloatsUniformIfPresent("uRatio", floatArrayOf(aspectRatio))
        TransitionGlSupport.bindManifestParameters(
            program,
            entry.params,
            displacementTexture,
            lumaTexture,
            firstTextureUnit = 1
        )
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun programFor(glId: String): ProgramEntry {
        programCache[glId]?.let { return it }
        val info = manifest[glId]
            ?: throw IllegalArgumentException("Transition shader '$glId' is missing from the manifest")
        val raw = appContext.assets.open("gl_transitions/$glId.glsl").bufferedReader().use { it.readText() }
        val rewritten = PackedTransitionShaderSource.rewrite(raw)
            ?: throw IllegalArgumentException("Transition shader '$glId' cannot sample packed A/B inputs safely")
        val program = GlProgram(VERTEX_SHADER, rewritten)
        try {
            program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
        } catch (failure: Exception) {
            program.delete()
            throw failure
        }
        programCache[glId] = ProgramEntry(program, info.params)
        while (programCache.size > MAX_CACHED_PROGRAMS) {
            val eldest = programCache.entries.iterator().next()
            if (eldest.key == glId && programCache.size == 1) break
            eldest.value.program.delete()
            programCache.remove(eldest.key)
        }
        return programCache.getValue(glId)
    }

    private fun drawBuiltin(inputTexId: Int, progress: Float, type: Int) {
        builtinProgram.use()
        builtinProgram.setSamplerTexIdUniform("uPacked", inputTexId, 0)
        builtinProgram.setFloatUniform("uProgress", progress)
        builtinProgram.setFloatUniform("uRatio", aspectRatio)
        builtinProgram.setIntUniform("uType", type)
        builtinProgram.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        programCache.values.forEach { it.program.delete() }
        programCache.clear()
        builtinProgram.delete()
        TransitionGlSupport.deleteTexture(displacementTexture)
        TransitionGlSupport.deleteTexture(lumaTexture)
    }

    private data class ProgramEntry(
        val program: GlProgram,
        val params: List<GlTransitionParam>
    )

    companion object {
        private const val MAX_CACHED_PROGRAMS = 3
        private const val VERTEX_SHADER = """#version 300 es
in vec4 aFramePosition;
out vec2 vTexCoord;
void main() {
    gl_Position = aFramePosition;
    vTexCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""
        private const val BUILTIN_FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uPacked;
uniform float uProgress;
uniform float uRatio;
uniform int uType;
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
vec4 fromColor(vec2 uv) {
    vec2 c = clamp(uv, vec2(0.0), vec2(1.0));
    return texture(uPacked, vec2(c.x * 0.5, c.y));
}
vec4 toColor(vec2 uv) {
    vec2 c = clamp(uv, vec2(0.0), vec2(1.0));
    return texture(uPacked, vec2(0.5 + c.x * 0.5, c.y));
}
void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    vec2 uv = vTexCoord;
    vec4 a = fromColor(uv);
    vec4 b = toColor(uv);
    if (uType == 0) {
        fragColor = mix(a, b, p);
    } else if (uType == 1 || uType == 7 || uType == 8 || uType == 9) {
        float axis = (uType == 1 || uType == 7) ? uv.x : uv.y;
        float directed = (uType == 7 || uType == 9) ? 1.0 - axis : axis;
        float edge = p * 1.12 - 0.06;
        float reveal = smoothstep(edge - 0.04, edge + 0.04, 1.0 - directed);
        fragColor = mix(a, b, reveal);
    } else if (uType == 2 || uType == 14) {
        float blur = sin(p * 3.14159265) * (uType == 2 ? 0.012 : 0.006);
        vec4 ca = vec4(0.0), cb = vec4(0.0);
        for (float x = -2.0; x <= 2.0; x += 1.0) {
            for (float y = -2.0; y <= 2.0; y += 1.0) {
                vec2 off = vec2(x, y) * blur;
                ca += fromColor(uv + off);
                cb += toColor(uv + off);
            }
        }
        fragColor = mix(ca / 25.0, cb / 25.0, smoothstep(0.25, 0.75, p));
    } else if (uType == 3 || uType == 11 || uType == 12) {
        vec2 offset = (uType == 12) ? vec2(0.0, p) : vec2((uType == 11 ? 1.0 : -1.0) * p, 0.0);
        vec2 uvA = uv - offset;
        vec2 uvB = uv + (uType == 12 ? vec2(0.0, -(1.0 - p)) : vec2((uType == 11 ? -1.0 : 1.0) * (1.0 - p), 0.0));
        float split = (uType == 12) ? 1.0 - p : (uType == 11 ? p : 1.0 - p);
        fragColor = (uType == 12 ? uv.y < split : (uType == 11 ? uv.x > split : uv.x < split))
            ? fromColor(uvA) : toColor(uvB);
    } else if (uType == 4) {
        float slice = floor(uv.y * 24.0);
        float rnd = hash21(vec2(slice, floor(p * 15.0)));
        float displacement = (rnd - 0.5) * 0.15 * sin(p * 3.14159265);
        vec2 glitchUv = uv + vec2(displacement, 0.0);
        vec4 base = mix(fromColor(glitchUv), toColor(glitchUv), p);
        if (abs(displacement) > 0.03) {
            base.r = mix(fromColor(glitchUv + vec2(0.01, 0.0)).r, toColor(glitchUv + vec2(0.01, 0.0)).r, p);
            base.b = mix(fromColor(glitchUv - vec2(0.01, 0.0)).b, toColor(glitchUv - vec2(0.01, 0.0)).b, p);
        }
        fragColor = base;
    } else if (uType == 5 || uType == 6) {
        vec4 flash = uType == 5 ? vec4(0.0, 0.0, 0.0, 1.0) : vec4(1.0);
        fragColor = p < 0.5 ? mix(a, flash, p * 2.0) : mix(flash, b, (p - 0.5) * 2.0);
    } else if (uType == 10) {
        vec2 d = uv - vec2(0.5);
        float angle = mod(atan(d.y, d.x) + 6.2831853, 6.2831853) / 6.2831853;
        fragColor = angle <= p ? b : a;
    } else if (uType == 13) {
        vec4 base = mix(a, b, p);
        float flare = sin(p * 3.14159265);
        fragColor = vec4(base.rgb + vec3(1.0, 0.75, 0.4) * flare * 0.85, base.a);
    } else if (uType == 15) {
        float openAmt = smoothstep(0.0, 1.0, p);
        vec2 uvB = (uv - vec2(0.5)) / mix(1.3, 1.0, openAmt) + vec2(0.5);
        vec4 colB = toColor(uvB);
        float aScale = max(1.0 - openAmt * 0.94, 0.02);
        vec2 uvA = (uv - vec2(0.5)) / aScale + vec2(0.5);
        vec4 colA = fromColor(uvA);
        colA.rgb *= mix(1.0, 0.55, openAmt);
        float halfSize = 0.5 * aScale;
        float inA = step(abs(uv.x - 0.5), halfSize) * step(abs(uv.y - 0.5), halfSize);
        fragColor = mix(colB, colA, inA * (1.0 - smoothstep(0.9, 1.0, openAmt)));
    } else if (uType == 16) {
        float cells = mix(140.0, 10.0, sin(p * 3.14159265));
        vec2 grid = vec2(cells, cells / max(uRatio, 0.1));
        vec2 puv = (floor(uv * grid) + 0.5) / grid;
        fragColor = mix(fromColor(puv), toColor(puv), smoothstep(0.35, 0.65, p));
    } else if (uType == 17) {
        float c = sin(p * 3.14159265);
        float bow = c * 0.16 * (uv.y - 0.5) * 2.0;
        vec2 uvA = uv + vec2(bow * (1.0 - p) + p * 0.85, 0.0);
        vec2 uvB = uv + vec2(-bow * p - (1.0 - p) * 0.85, 0.0);
        fragColor = mix(fromColor(uvA), toColor(uvB), smoothstep(0.15, 0.85, p));
    } else {
        fragColor = mix(a, b, p);
    }
}
"""

        private fun builtinType(id: String): Int? = when (id.lowercase()) {
            "cross_dissolve", "cross", "dissolve" -> 0
            "wipe_left", "wipe" -> 1
            "zoom_blur", "zoom", "zoom_push" -> 2
            "slide_left", "slide" -> 3
            "glitch" -> 4
            "fade_black", "dip_black" -> 5
            "fade_white", "flash", "dip_white" -> 6
            "wipe_right" -> 7
            "wipe_up" -> 8
            "wipe_down" -> 9
            "clock_wipe", "radial_wipe", "circle" -> 10
            "slide_right" -> 11
            "slide_up" -> 12
            "light_leak" -> 13
            "cross_blur", "blur" -> 14
            "doorway" -> 15
            "pixelize" -> 16
            "crosswarp" -> 17
            else -> null
        }
    }
}
