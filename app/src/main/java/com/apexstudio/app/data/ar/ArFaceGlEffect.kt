package com.apexstudio.app.data.ar

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Bakes the AR Face filter *graded look* into the exported MP4 via the
 * Media3 Transformer GL pipeline — the export counterpart of
 * [com.apexstudio.app.ui.screens.editor.ArFaceFilterOverlay] (preview).
 *
 * Design note: the live preview renders landmark-pinned graphics on a
 * Compose Canvas above the player (eye sparkles, visor rects, temple
 * bells, diya lamps) using ML Kit face landmarks. The GPU export
 * pipeline has no landmark stream, so each preset is approximated here
 * as a full-frame GLSL look that captures the preset's colour /
 * atmosphere / motion character (warmth, glow, grain, pulse, flicker).
 * Preview and export therefore agree on the *feel* of the filter even
 * though the landmark-pinned decorations are preview-only.
 *
 * The fragment shader takes the frame plus three uniforms:
 *
 *  - `uIntensity` — 0..1 opacity of the effect (the AR panel slider)
 *  - `uTime` — seconds since playback start, drives animated presets
 *    (visor sweep, diya flicker, zoom pulse)
 *  - `uTexel` — 1/width, 1/height of the frame
 *
 * Unknown filter ids fall back to a no-op passthrough so a stale id can
 * never break an export.
 */
@UnstableApi
class ArFaceGlEffect(
    private val arFilterId: String,
    private val intensity: Float
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ArFaceShaderProgram(arFilterId, intensity.coerceIn(0f, 1f), useHdr)
    }

    @UnstableApi
    private class ArFaceShaderProgram(
        private val arFilterId: String,
        private val intensity: Float,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {

        private val glProgram: GlProgram
        private var inputWidth: Int = 0
        private var inputHeight: Int = 0

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, fragmentFor(arFilterId))
            } catch (e: Exception) {
                throw androidx.media3.common.VideoFrameProcessingException(
                    "Failed to compile AR face shader for $arFilterId", e
                )
            }
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)
            // NDC [-1,1] → UV [0,1]; same mapping the FX effect uses.
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
                throw androidx.media3.common.VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
        }
    }

    companion object {
        private const val TEXTURE_POOL_CAPACITY = 4

        /** True when [id] maps to a real AR filter shader (not the passthrough). */
        fun isKnownFilter(id: String?): Boolean = id != null && id in KNOWN_IDS

        private val KNOWN_IDS: Set<String> = setOf(
            "ar_beauty_smooth",
            "ar_skin_retouch",
            "ar_face_slimming",
            "ar_big_eyes",
            "ar_cartoon_anime",
            "ar_cyber_visor",
            "ar_bokeh_blur",
            "ar_retro_grain",
            "ar_ganesh_chaturthi",
            "ar_diya_flower",
            "ar_temple_lotus",
            "ar_warm_glow",
            "ar_face_zoom_pulse"
        )

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

        private fun fragmentFor(arFilterId: String): String = when (arFilterId) {
            "ar_beauty_smooth" -> beautySmoothShader()
            "ar_skin_retouch" -> skinRetouchShader()
            "ar_face_slimming" -> faceSlimmingShader()
            "ar_big_eyes" -> bigEyesShader()
            "ar_cartoon_anime" -> cartoonAnimeShader()
            "ar_cyber_visor" -> cyberVisorShader()
            "ar_bokeh_blur" -> bokehBlurShader()
            "ar_retro_grain" -> retroGrainShader()
            "ar_ganesh_chaturthi" -> ganeshChaturthiShader()
            "ar_diya_flower" -> diyaFlowerShader()
            "ar_temple_lotus" -> templeLotusShader()
            "ar_warm_glow" -> warmGlowShader()
            "ar_face_zoom_pulse" -> faceZoomPulseShader()
            else -> identityShader()
        }

        /** Shared precision / varyings / uniforms for the AR shaders. */
        private val HEADER = """
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform float uIntensity;
            uniform float uTime;
            uniform vec2 uTexel;
        """.trimIndent()

        private fun identityShader(): String = """
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            void main() {
                gl_FragColor = texture2D(uTexSampler, vTextureCoord);
            }
        """.trimIndent()

        // Porcelain Smooth: soft glow lift, like a light diffusion filter.
        private fun beautySmoothShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 o = uTexel * 2.0;
                vec4 blur = (texture2D(uTexSampler, vTextureCoord + vec2(o.x, 0.0))
                           + texture2D(uTexSampler, vTextureCoord - vec2(o.x, 0.0))
                           + texture2D(uTexSampler, vTextureCoord + vec2(0.0, o.y))
                           + texture2D(uTexSampler, vTextureCoord - vec2(0.0, o.y))) / 4.0;
                vec3 soft = mix(orig.rgb, blur.rgb, 0.5) * 1.04 + vec3(0.02, 0.015, 0.015);
                gl_FragColor = vec4(mix(orig.rgb, soft, uIntensity), orig.a);
            }
        """.trimIndent()

        // Radiant Retouch: warm peach grade.
        private fun skinRetouchShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec3 warm = orig.rgb * vec3(1.06, 1.0, 0.94) + vec3(0.03, 0.015, 0.0);
                gl_FragColor = vec4(mix(orig.rgb, warm, uIntensity), orig.a);
            }
        """.trimIndent()

        // V-Line Slimming: gentle contour shading, darker toward the edges.
        private fun faceSlimmingShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float d = distance(vTextureCoord, vec2(0.5, 0.42));
                float shade = smoothstep(0.25, 0.75, d);
                vec3 res = orig.rgb * (1.0 - shade * 0.18 * uIntensity);
                gl_FragColor = vec4(res, orig.a);
            }
        """.trimIndent()

        // Anime Sparkle Eyes: twinkling sparkles on bright areas.
        private fun bigEyesShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float lum = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
                vec2 px = vTextureCoord / uTexel;
                float tw = sin(px.x * 0.08 + uTime * 5.0) * sin(px.y * 0.08 - uTime * 4.0);
                float sparkle = smoothstep(0.75, 0.95, lum) * max(tw, 0.0);
                vec3 res = orig.rgb + vec3(0.9, 0.98, 1.0) * sparkle * 0.6 * uIntensity;
                gl_FragColor = vec4(res, orig.a);
            }
        """.trimIndent()

        // Manga Anime: punchy saturation pop.
        private fun cartoonAnimeShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float lum = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
                vec3 sat = mix(vec3(lum), orig.rgb, 1.35);
                gl_FragColor = vec4(mix(orig.rgb, sat, uIntensity), orig.a);
            }
        """.trimIndent()

        // Cyber HUD Visor: cyan scan band sweeping down + faint scanlines.
        private fun cyberVisorShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float bandY = fract(uTime * 0.35);
                float band = smoothstep(0.06, 0.0, abs(vTextureCoord.y - bandY));
                vec3 res = orig.rgb + vec3(0.0, 0.9, 1.0) * band * 0.5 * uIntensity;
                float scan = 0.94 + 0.06 * sin(vTextureCoord.y / uTexel.y * 1.4);
                gl_FragColor = vec4(res * mix(1.0, scan, uIntensity), orig.a);
            }
        """.trimIndent()

        // Portrait Bokeh: optical-feel radial focus dim outside the centre.
        private fun bokehBlurShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float d = distance(vTextureCoord * vec2(1.0, 0.75), vec2(0.5, 0.375));
                float dim = smoothstep(0.25, 0.62, d);
                vec3 res = orig.rgb * (1.0 - dim * 0.45 * uIntensity);
                gl_FragColor = vec4(res, orig.a);
            }
        """.trimIndent()

        // Vintage 35mm: animated grain + vignette.
        private fun retroGrainShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 px = vTextureCoord / uTexel;
                float n = fract(sin(dot(vec2(px.x + fract(uTime * 12.0) * 173.0, px.y),
                                        vec2(12.9898, 78.233))) * 43758.5453);
                vec3 res = orig.rgb + (n - 0.5) * 0.16 * uIntensity;
                float d = distance(vTextureCoord, vec2(0.5, 0.5));
                res *= 1.0 - smoothstep(0.45, 0.85, d) * 0.35 * uIntensity;
                gl_FragColor = vec4(res, orig.a);
            }
        """.trimIndent()

        // Ganesh Chaturthi: golden temple-arch glow top and bottom.
        private fun ganeshChaturthiShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float topGlow = smoothstep(0.16, 0.0, vTextureCoord.y);
                float botGlow = smoothstep(0.84, 1.0, vTextureCoord.y);
                vec3 gold = vec3(1.0, 0.72, 0.25);
                vec3 res = orig.rgb + gold * (topGlow * 0.35 + botGlow * 0.30) * uIntensity;
                res = res * vec3(1.03, 1.0, 0.96);
                gl_FragColor = vec4(mix(orig.rgb, res, uIntensity), orig.a);
            }
        """.trimIndent()

        // Diya & Floral: warm flickering glow rising from the bottom corners.
        private fun diyaFlowerShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float flick = 0.85 + 0.15 * sin(uTime * 9.0 + vTextureCoord.x * 20.0);
                float d1 = distance(vTextureCoord, vec2(0.08, 0.95));
                float d2 = distance(vTextureCoord, vec2(0.92, 0.95));
                float glow = (smoothstep(0.45, 0.0, d1) + smoothstep(0.45, 0.0, d2)) * flick;
                vec3 res = orig.rgb + vec3(1.0, 0.55, 0.15) * glow * 0.55 * uIntensity;
                gl_FragColor = vec4(res, orig.a);
            }
        """.trimIndent()

        // Temple & Lotus: golden sacred aura blooming from the upper centre.
        private fun templeLotusShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float d = distance(vTextureCoord, vec2(0.5, 0.38));
                float aura = smoothstep(0.42, 0.05, d);
                vec3 res = orig.rgb + vec3(1.0, 0.75, 0.3) * aura * 0.35 * uIntensity;
                gl_FragColor = vec4(mix(orig.rgb, res, uIntensity), orig.a);
            }
        """.trimIndent()

        // Golden Warm Glow: soft warm bloom + gentle warm grade.
        private fun warmGlowShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                vec2 o = uTexel * 3.0;
                vec4 glow = (texture2D(uTexSampler, vTextureCoord + vec2(o.x, 0.0))
                           + texture2D(uTexSampler, vTextureCoord - vec2(o.x, 0.0))
                           + texture2D(uTexSampler, vTextureCoord + vec2(0.0, o.y))
                           + texture2D(uTexSampler, vTextureCoord - vec2(0.0, o.y))) / 4.0;
                vec3 warm = mix(orig.rgb, glow.rgb, 0.35) * vec3(1.05, 1.0, 0.92)
                          + vec3(0.035, 0.02, 0.0);
                gl_FragColor = vec4(mix(orig.rgb, warm, uIntensity), orig.a);
            }
        """.trimIndent()

        // Face Zoom Pulse: beat-synced zoom pulse (the export actually moves
        // the frame; the preview visualises it with pulse rings).
        private fun faceZoomPulseShader(): String = """
            $HEADER
            void main() {
                vec4 orig = texture2D(uTexSampler, vTextureCoord);
                float pulse = pow(max(0.0, sin(uTime * 4.5)), 6.0) * 0.10 * uIntensity;
                vec2 anchor = vec2(0.5, 0.45);
                vec2 uv = (vTextureCoord - anchor) * (1.0 - pulse) + anchor;
                vec4 zoomed = texture2D(uTexSampler, clamp(uv, 0.0, 1.0));
                gl_FragColor = mix(orig, zoomed, uIntensity);
            }
        """.trimIndent()
    }
}
