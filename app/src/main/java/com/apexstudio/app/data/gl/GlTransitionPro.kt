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
import org.json.JSONArray
import org.json.JSONObject

/**
 * One gl-transitions shader (https://github.com/gl-transitions/gl-transitions,
 * MIT) from `assets/gl_transitions/<glId>.glsl`.
 */
data class GlTransitionInfo(
    val id: String,
    val name: String,
    val author: String,
    val params: List<GlTransitionParam> = emptyList()
)

data class GlTransitionParam(
    /** GLSL uniform name, e.g. "uPixelSize". */
    val name: String,
    /** "float" | "int" | "bool" | "vec2" | "vec3" | "vec4". */
    val type: String,
    /** Default value(s). */
    val default: List<Float>
)

/**
 * Loads `assets/gl_transitions/manifest.json` (written by the Phase 4
 * shader porter). Cached; never throws — returns empty on failure so a
 * missing manifest can never break the transition picker.
 */
object GlTransitionManifest {
    private const val TAG = "GlTransitionManifest"
    private const val MANIFEST = "gl_transitions/manifest.json"

    @Volatile
    private var cached: List<GlTransitionInfo>? = null

    fun load(context: Context): List<GlTransitionInfo> {
        cached?.let { return it }
        val parsed = try {
            val json = context.assets.open(MANIFEST).bufferedReader().use { it.readText() }
            parse(json)
        } catch (e: Exception) {
            Log.w(TAG, "gl_transitions manifest missing", e)
            emptyList()
        }
        cached = parsed
        return parsed
    }

    private fun parse(json: String): List<GlTransitionInfo> {
        val out = mutableListOf<GlTransitionInfo>()
        val root = JSONObject(json)
        val arr: JSONArray = root.optJSONArray("transitions") ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").ifBlank { continue }
            val params = mutableListOf<GlTransitionParam>()
            val parr = o.optJSONArray("params")
            if (parr != null) {
                for (j in 0 until parr.length()) {
                    val p = parr.optJSONObject(j) ?: continue
                    val name = p.optString("name").ifBlank { continue }
                    val type = p.optString("type", "float").lowercase()
                    val d = p.opt("default")
                    val defaults = when (d) {
                        is Number -> listOf(d.toFloat())
                        is JSONArray -> (0 until d.length()).mapNotNull {
                            d.optDouble(it, Double.NaN).takeIf { !it.isNaN() }?.toFloat()
                        }
                        is Boolean -> listOf(if (d) 1f else 0f)
                        else -> listOf(0f)
                    }
                    params += GlTransitionParam(name, type, defaults.ifEmpty { listOf(0f) })
                }
            }
            out += GlTransitionInfo(
                id = id,
                name = o.optString("name", id),
                author = o.optString("author", "gl-transitions"),
                params = params
            )
        }
        return out.sortedBy { it.name.lowercase() }
    }
}

/**
 * Media3 [GlEffect] that runs a ported gl-transitions GLSL ES 3.0 shader.
 *
 * gl-transitions shaders are two-texture (`uFrom`/`uTo`); the export
 * pipeline here is single-texture, so `uFrom` = the current video frame and
 * `uTo` = a 1x1 black texture (dip-to-black semantics, matching the app's
 * existing transition behaviour). `uProgress` is driven by frame time,
 * `uRatio` by the video aspect, and every declared param is set to its
 * manifest default.
 *
 * A missing/ broken shader file falls back to a plain pass-through so a
 * bad asset can never fail an export.
 */
@UnstableApi
class GlTransitionAdapterEffect(
    private val glId: String,
    private val durationUs: Long = 1_000_000L,
    private val startUs: Long = 0L
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        val params = try {
            GlTransitionManifest.load(context).firstOrNull { it.id == glId }?.params
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val fragSource = try {
            context.assets.open("gl_transitions/$glId.glsl").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "gl-transitions shader missing: $glId — pass-through", e)
            return PassthroughFallback(useHdr)
        }
        return try {
            GlTransitionProgram(fragSource, params, durationUs, startUs, useHdr)
        } catch (e: Exception) {
            Log.w(TAG, "gl-transitions shader compile failed: $glId — pass-through", e)
            PassthroughFallback(useHdr)
        }
    }

    @UnstableApi
    private class GlTransitionProgram(
        fragSource: String,
        private val params: List<GlTransitionParam>,
        private val durationUs: Long,
        private val startUs: Long,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, 1) {

        private val glProgram: GlProgram
        private val blackTexId: Int
        private var aspectRatio: Float = 16f / 9f

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, fragSource)
            } catch (e: Exception) {
                throw VideoFrameProcessingException("gl-transitions shader failed: ${e.message}", e)
            }
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            // 1x1 opaque black texture for the shader's uTo sampler.
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            val black = java.nio.ByteBuffer.allocateDirect(4).apply {
                put(0.toByte()); put(0.toByte()); put(0.toByte()); put(255.toByte())
                position(0)
            }
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, black
            )
            blackTexId = tex[0]
            // Manifest defaults for every declared param (best effort —
            // a missing uniform is skipped, never fatal).
            for (p in params) {
                try {
                    when (p.type) {
                        "int", "bool" ->
                            glProgram.setIntUniform(p.name, p.default.firstOrNull()?.toInt() ?: 0)
                        "vec2", "vec3", "vec4" ->
                            glProgram.setFloatsUniform(p.name, p.default.toFloatArray())
                        else ->
                            glProgram.setFloatUniform(p.name, p.default.firstOrNull() ?: 0f)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "param ${p.name}: ${e.message}")
                }
            }
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            if (inputHeight > 0) aspectRatio = inputWidth.toFloat() / inputHeight
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                glProgram.setSamplerTexIdUniform("uFrom", inputTexId, 0)
                glProgram.setSamplerTexIdUniform("uTo", blackTexId, 1)
                val elapsedUs = (presentationTimeUs - startUs).coerceAtLeast(0L)
                val progress = if (durationUs > 0) {
                    (elapsedUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                } else 1f
                glProgram.setFloatsUniformIfPresent("uProgress", floatArrayOf(progress))
                glProgram.setFloatsUniformIfPresent("uRatio", floatArrayOf(aspectRatio))
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                GLES20.glDeleteTextures(1, intArrayOf(blackTexId), 0)
            } catch (_: Exception) {
            }
        }

        companion object {
            // 300 es to match the ported shaders (which declare
            // `in vec2 vTexCoord`).
            private const val VERTEX_SHADER = """#version 300 es
in vec4 aFramePosition;
out vec2 vTexCoord;
void main() {
    gl_Position = aFramePosition;
    vTexCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""
        }
    }

    /** Never-fail pass-through used when a shader asset is broken. */
    @UnstableApi
    private class PassthroughFallback(useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
        private val glProgram: GlProgram = GlProgram(
            VERT,
            """precision mediump float;
varying vec2 vTextureCoord;
uniform sampler2D uTexSampler;
void main() { gl_FragColor = texture2D(uTexSampler, vTextureCoord); }"""
        ).apply {
            setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
        }

        override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        companion object {
            private const val VERT = """attribute vec4 aFramePosition;
varying vec2 vTextureCoord;
void main() {
    gl_Position = aFramePosition;
    vTextureCoord = aFramePosition.xy * 0.5 + 0.5;
}"""
        }
    }

    companion object {
        private const val TAG = "GlTransitionAdapter"
        /** Picker/export id prefix for gl-transitions entries. */
        const val ID_PREFIX = "glpro_"
    }
}
