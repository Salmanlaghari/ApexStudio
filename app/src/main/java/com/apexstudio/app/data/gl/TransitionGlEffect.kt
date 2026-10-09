package com.apexstudio.app.data.gl

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
 * Media3 [GlEffect] adapter that integrates transitions and dynamic visual effects
 * directly into the Media3 pipeline for both ExoPlayer preview and Transformer export.
 */
@UnstableApi
class TransitionGlEffect(
    private val transitionType: TransitionEngine.Companion.TransitionType = TransitionEngine.Companion.TransitionType.CROSS_DISSOLVE,
    private val durationUs: Long = 1_000_000L,
    private val startUs: Long = 0L
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return TransitionShaderProgram(transitionType, durationUs, startUs, useHdr)
    }

    @UnstableApi
    private class TransitionShaderProgram(
        private val transitionType: TransitionEngine.Companion.TransitionType,
        private val durationUs: Long,
        private val startUs: Long,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, 1) {

        private val glProgram: GlProgram

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: Exception) {
                throw VideoFrameProcessingException("Failed to compile TransitionGlEffect shader", e)
            }

            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)
            val texMatrix = floatArrayOf(
                0.5f, 0f, 0f, 0f,
                0f, 0.5f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0.5f, 0.5f, 0f, 1f
            )
            glProgram.setFloatsUniform("uTexTransformationMatrix", texMatrix)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)

                val elapsedUs = (presentationTimeUs - startUs).coerceAtLeast(0L)
                val progress = if (durationUs > 0) {
                    (elapsedUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                } else {
                    1f
                }
                glProgram.setFloatUniform("uProgress", progress)
                val typeCode = when (transitionType) {
                    TransitionEngine.Companion.TransitionType.CROSS_DISSOLVE -> 0
                    TransitionEngine.Companion.TransitionType.WIPE,
                    TransitionEngine.Companion.TransitionType.WIPE_RIGHT,
                    TransitionEngine.Companion.TransitionType.WIPE_UP,
                    TransitionEngine.Companion.TransitionType.WIPE_DOWN,
                    TransitionEngine.Companion.TransitionType.CLOCK_WIPE -> 1
                    TransitionEngine.Companion.TransitionType.ZOOM_BLUR -> 2
                    TransitionEngine.Companion.TransitionType.SLIDE,
                    TransitionEngine.Companion.TransitionType.SLIDE_RIGHT,
                    TransitionEngine.Companion.TransitionType.SLIDE_UP -> 3
                    TransitionEngine.Companion.TransitionType.GLITCH -> 4
                    TransitionEngine.Companion.TransitionType.FADE_BLACK -> 5
                    TransitionEngine.Companion.TransitionType.FADE_WHITE -> 6
                    TransitionEngine.Companion.TransitionType.LIGHT_LEAK -> 7
                    // Pro Phase 1: new shader transitions
                    TransitionEngine.Companion.TransitionType.CROSS_BLUR -> 8
                    TransitionEngine.Companion.TransitionType.DOORWAY -> 9
                    TransitionEngine.Companion.TransitionType.PIXELIZE -> 10
                    TransitionEngine.Companion.TransitionType.CROSSWARP -> 11
                }
                glProgram.setFloatUniform("uType", typeCode.toFloat())
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        companion object {
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

            private val FRAGMENT_SHADER = """
                precision highp float;
                varying vec2 vTextureCoord;
                uniform sampler2D uTexSampler;
                uniform float uProgress;
                uniform float uType;

                void main() {
                    vec2 uv = vTextureCoord;
                    vec4 col = texture2D(uTexSampler, uv);
                    float p = uProgress;

                    if (uType < 0.5) {
                        // Dissolve fade to black/next
                        float alpha = 1.0 - smoothstep(0.0, 1.0, p);
                        gl_FragColor = vec4(col.rgb * alpha, col.a);
                    } else if (uType < 1.5) {
                        // Directional wipe
                        float edge = 1.0 - p;
                        float val = step(uv.x, edge);
                        gl_FragColor = vec4(col.rgb * val, col.a);
                    } else if (uType < 2.5) {
                        // Zoom Blur effect
                        vec2 center = vec2(0.5, 0.5);
                        vec2 dir = (uv - center) * p * 0.15;
                        vec4 accum = vec4(0.0);
                        accum += texture2D(uTexSampler, uv - dir * 2.0);
                        accum += texture2D(uTexSampler, uv - dir);
                        accum += texture2D(uTexSampler, uv);
                        accum += texture2D(uTexSampler, uv + dir);
                        accum += texture2D(uTexSampler, uv + dir * 2.0);
                        gl_FragColor = accum / 5.0;
                    } else if (uType < 3.5) {
                        // Slide push
                        vec2 slideUv = uv + vec2(p, 0.0);
                        if (slideUv.x > 1.0) {
                            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                        } else {
                            gl_FragColor = texture2D(uTexSampler, slideUv);
                        }
                    } else if (uType < 4.5) {
                        // Glitch
                        float slice = floor(uv.y * 32.0);
                        float displace = sin(slice * 13.0 + p * 10.0) * 0.04 * p;
                        vec4 gCol = texture2D(uTexSampler, uv + vec2(displace, 0.0));
                        gl_FragColor = gCol;
                    } else if (uType < 5.5) {
                        // Fade to black
                        float alpha = 1.0 - smoothstep(0.0, 1.0, p);
                        gl_FragColor = vec4(col.rgb * alpha, col.a);
                    } else if (uType < 6.5) {
                        // Fade to white / flash
                        vec3 white = vec3(1.0, 1.0, 1.0);
                        float flash = sin(p * 3.14159265);
                        gl_FragColor = vec4(mix(col.rgb, white, flash), col.a);
                    } else if (uType < 7.5) {
                        // Light leak
                        float flare = sin(p * 3.14159265);
                        vec3 flareCol = vec3(1.0, 0.75, 0.4) * flare * 0.8;
                        gl_FragColor = vec4(col.rgb + flareCol, col.a);
                    } else if (uType < 8.5) {
                        // Cross blur (single-texture): defocus peaks mid-transition
                        float blurAmt = sin(p * 3.14159265) * 0.02;
                        vec4 bAcc = vec4(0.0);
                        bAcc += texture2D(uTexSampler, uv + vec2(blurAmt, 0.0));
                        bAcc += texture2D(uTexSampler, uv - vec2(blurAmt, 0.0));
                        bAcc += texture2D(uTexSampler, uv + vec2(0.0, blurAmt));
                        bAcc += texture2D(uTexSampler, uv - vec2(0.0, blurAmt));
                        bAcc += texture2D(uTexSampler, uv);
                        gl_FragColor = bAcc / 5.0;
                    } else if (uType < 9.5) {
                        // Doorway (single-texture): frame shrinks toward center
                        float openAmt = smoothstep(0.0, 1.0, p);
                        float dScale = max(1.0 - openAmt * 0.9, 0.05);
                        vec2 dUv = (uv - vec2(0.5)) / dScale + vec2(0.5);
                        vec4 dCol = texture2D(uTexSampler, clamp(dUv, 0.0, 1.0));
                        dCol.rgb *= mix(1.0, 0.5, openAmt);
                        gl_FragColor = dCol;
                    } else if (uType < 10.5) {
                        // Pixelize (single-texture): mosaic peaks mid-transition
                        float cells = mix(120.0, 12.0, sin(p * 3.14159265));
                        vec2 grid = vec2(cells, cells * 9.0 / 16.0);
                        vec2 pUv = (floor(uv * grid) + 0.5) / grid;
                        gl_FragColor = texture2D(uTexSampler, clamp(pUv, 0.0, 1.0));
                    } else {
                        // Crosswarp (single-texture): bow + horizontal sweep
                        float bow = sin(p * 3.14159265) * 0.1 * (uv.y - 0.5) * 2.0;
                        vec2 wUv = uv + vec2(bow + p * 0.2, 0.0);
                        vec4 wCol = texture2D(uTexSampler, wUv);
                        if (wUv.x < 0.0 || wUv.x > 1.0) {
                            wCol = vec4(0.0, 0.0, 0.0, 1.0);
                        }
                        gl_FragColor = wCol;
                    }
                }
            """.trimIndent()
        }
    }
}
