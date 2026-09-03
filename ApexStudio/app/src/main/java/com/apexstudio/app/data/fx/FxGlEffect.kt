package com.apexstudio.app.data.fx

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Real-time video FX wired into the Media3 GL pipeline — the FX-tool
 * counterpart of [com.apexstudio.app.data.filter.LutFilterGlEffect].
 *
 * Each [FxPreset] is a GLSL ES 2.0 fragment shader that takes the
 * frame plus three uniforms:
 *
 *  - `uIntensity` — 0..1 opacity of the effect (the FX panel slider)
 *  - `uTime` — seconds since playback start, drives animated FX
 *    (grain flicker, VHS roll, glitch slices)
 *  - `uTexel` — 1/width, 1/height of the frame, used by effects that
 *    need pixel-space maths (scanlines, blur, grain)
 *
 * The same shader program runs in the ExoPlayer preview effect chain
 * and in the Media3 Transformer export pipeline, so the exported MP4
 * matches exactly what the user saw on screen.
 */
@UnstableApi
class FxGlEffect(
    private val preset: FxPreset,
    private val intensity: Float
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return FxShaderProgram(preset, intensity.coerceIn(0f, 1f), useHdr)
    }

    @UnstableApi
    private class FxShaderProgram(
        private val preset: FxPreset,
        private val intensity: Float,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {

        private val glProgram: GlProgram
        private var inputWidth: Int = 0
        private var inputHeight: Int = 0

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, fragmentFor(preset))
            } catch (e: Exception) {
                throw VideoFrameProcessingException("Failed to compile FX shader for ${preset.id}", e)
            }
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)
            // NDC [-1,1] → UV [0,1]; same mapping the LUT effect uses.
            val texMatrix = floatArrayOf(
                0.5f, 0f, 0f, 0f,
                0f, 0.5f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0.5f, 0.5f, 0f, 1f
            )
            glProgram.setFloatsUniform("uTexTransformationMatrix", texMatrix)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            this.inputWidth = inputWidth
            this.inputHeight = inputHeight
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                glProgram.setFloatUniform("uIntensity", intensity)
                glProgram.setFloatUniform("uTime", presentationTimeUs / 1_000_000f)
                glProgram.setFloatsUniform(
                    "uTexel",
                    floatArrayOf(
                        1f / inputWidth.coerceAtLeast(1),
                        1f / inputHeight.coerceAtLeast(1)
                    )
                )
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
        }
    }

    companion object {
        private const val TEXTURE_POOL_CAPACITY = 1

        private val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            uniform mat4 uTransformationMatrix;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uTransformationMatrix * aFramePosition;
                vTextureCoord = (uTexTransformationMatrix * aFramePosition).xy;
            }
        """.trimIndent()

        private fun fragmentFor(preset: FxPreset): String = when (preset) {
            FxPreset.VIGNETTE -> vignetteShader()
            FxPreset.FILM_GRAIN -> grainShader()
            FxPreset.VHS -> vhsShader()
            FxPreset.GLITCH -> glitchShader()
            FxPreset.PIXELATE -> pixelateShader()
            FxPreset.CHROMATIC -> chromaticShader()
            FxPreset.SCANLINES -> scanlinesShader()
            FxPreset.SOFT_BLUR -> blurShader()
        }

        /** Shared precision / varyings / uniforms for the FX shaders. */
        private val HEADER = """
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform float uIntensity;
            uniform float uTime;
            uniform vec2 uTexel;
        """.trimIndent()

        /**
         * GLSL code fragment for a cheap hash of a 2D vector. [x] and
         * [y] are raw GLSL expressions (e.g. pixel coords derived from
         * varyings), so they are interpolated into the shader text.
         */
        private fun hash2(x: String, y: String): String =
            "fract(sin(dot(vec2($x, $y), vec2(12.9898, 78.233))) * 43758.5453)"

        private fun vignetteShader(): String = """
            $HEADER
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                float d = distance(vTextureCoord, vec2(0.5, 0.5));
                float vig = smoothstep(0.32, 0.82, d);
                color.rgb *= 1.0 - vig * 0.8 * uIntensity;
                gl_FragColor = color;
            }
        """.trimIndent()

        private fun grainShader(): String = """
            $HEADER
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                vec2 px = vTextureCoord / uTexel;
                float n = ${hash2("px.x + fract(uTime * 12.0) * 173.0", "px.y")};
                color.rgb += (n - 0.5) * 0.16 * uIntensity;
                gl_FragColor = vec4(color.rgb, color.a);
            }
        """.trimIndent()

        private fun vhsShader(): String = """
            $HEADER
            void main() {
                vec2 px = vTextureCoord / uTexel;
                vec2 uv = vTextureCoord;
                // Horizontal sync wobble.
                uv.x += sin(px.y * 0.08 + uTime * 3.0) * 0.0035 * uIntensity;
                vec4 color = texture2D(uTexSampler, uv);
                // Chromatic offset on the R / B channels.
                float shift = 0.0015 + 0.0025 * sin(uTime * 2.0);
                float rf = texture2D(uTexSampler, uv + vec2(shift, 0.0)).r;
                float bf = texture2D(uTexSampler, uv - vec2(shift, 0.0)).b;
                color.r = mix(color.r, rf, uIntensity);
                color.b = mix(color.b, bf, uIntensity);
                // Fine scanlines.
                float scan = 0.5 + 0.5 * sin(px.y * 1.4);
                color.rgb *= 0.88 + 0.12 * scan;
                // Rolling tracking bar.
                float bar = smoothstep(0.0, 0.06, abs(fract(uv.y * 3.0 - uTime * 0.6) - 0.5));
                color.rgb *= mix(0.55, 1.0, mix(1.0, bar, uIntensity));
                gl_FragColor = color;
            }
        """.trimIndent()

        private fun glitchShader(): String = """
            $HEADER
            void main() {
                vec2 px = vTextureCoord / uTexel;
                float slice = floor(px.y / 16.0);
                float t = floor(uTime * 7.0);
                float h1 = ${hash2("slice + t", "t * 0.31")};
                float h2 = ${hash2("slice + 7.0", "t * 0.73")};
                float h3 = ${hash2("slice + 3.0", "t * 0.17")};
                vec2 offset = vec2((h1 - 0.5) * 0.09, 0.0) * uIntensity;
                // Occasionally jump a whole slice sideways (VHS tracking tear).
                offset.x += step(0.9, h3) * (h2 - 0.5) * 0.18 * uIntensity;
                vec4 color = texture2D(uTexSampler, vTextureCoord + offset);
                // Per-slice RGB channel split, strongest on "hit" slices.
                vec4 rf = texture2D(uTexSampler, vTextureCoord + vec2(0.012 * uIntensity, 0.0));
                vec4 bf = texture2D(uTexSampler, vTextureCoord - vec2(0.012 * uIntensity, 0.0));
                color.r = mix(color.r, rf.r, step(0.85, h2));
                color.b = mix(color.b, bf.b, step(0.85, h1));
                gl_FragColor = vec4(color.rgb, color.a);
            }
        """.trimIndent()

        private fun pixelateShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 px = vTextureCoord / uTexel;
                // Block size shrinks as intensity rises (subtle → full pixelate).
                float blockPx = mix(48.0, 14.0, uIntensity);
                vec2 uv = (floor(px / blockPx) * blockPx + blockPx * 0.5) * uTexel;
                vec4 color = texture2D(uTexSampler, uv);
                gl_FragColor = mix(orig, color, uIntensity);
            }
        """.trimIndent()

        private fun chromaticShader(): String = """
            $HEADER
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                vec2 dir = vTextureCoord - vec2(0.5, 0.5);
                float amt = mix(0.0010, 0.0060, uIntensity);
                float r = texture2D(uTexSampler, vTextureCoord + dir * amt).r;
                float b = texture2D(uTexSampler, vTextureCoord - dir * amt).b;
                gl_FragColor = vec4(mix(color.rgb, vec3(r, color.g, b), uIntensity), color.a);
            }
        """.trimIndent()

        private fun scanlinesShader(): String = """
            $HEADER
            void main() {
                vec4 color = texture2D(uTexSampler, vTextureCoord);
                vec2 px = vTextureCoord / uTexel;
                float stripe = 1.0 - step(0.55, fract(px.y / 3.0)) * 0.28 * uIntensity;
                // Gentle moving brightness band so the look is alive.
                float band = 1.0 + 0.06 * uIntensity * sin(px.y * 0.02 + uTime * 2.0);
                color.rgb *= stripe * band;
                gl_FragColor = color;
            }
        """.trimIndent()

        private fun blurShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 o = uTexel * 1.25;
                vec4 c = texture2D(uTexSampler, vTextureCoord) * 4.0;
                c += texture2D(uTexSampler, vTextureCoord + vec2( o.x, 0.0)) * 2.0;
                c += texture2D(uTexSampler, vTextureCoord + vec2(-o.x, 0.0)) * 2.0;
                c += texture2D(uTexSampler, vTextureCoord + vec2(0.0,  o.y)) * 2.0;
                c += texture2D(uTexSampler, vTextureCoord + vec2(0.0, -o.y)) * 2.0;
                c += texture2D(uTexSampler, vTextureCoord + o);
                c += texture2D(uTexSampler, vTextureCoord - o);
                c += texture2D(uTexSampler, vTextureCoord + vec2( o.x, -o.y));
                c += texture2D(uTexSampler, vTextureCoord + vec2(-o.x,  o.y));
                c /= 16.0;
                gl_FragColor = mix(orig, c, uIntensity);
            }
        """.trimIndent()
    }
}
