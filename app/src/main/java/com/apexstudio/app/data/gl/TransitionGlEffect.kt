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
                val typeCode = transitionType.typeIndex
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

                float hash(vec2 p) {
                    p = fract(p * vec2(123.34, 456.21));
                    p += dot(p, p + 45.32);
                    return fract(p.x * p.y);
                }

                void main() {
                    vec2 uv = vTextureCoord;
                    vec4 col = texture2D(uTexSampler, uv);
                    float p = uProgress;

                    // Family 1: Dissolve & Fade (0, 5..13)
                    if (uType < 0.5 || (uType >= 4.5 && uType < 13.5)) {
                        if (uType > 5.5 && uType < 6.5) {
                            // Fade White
                            gl_FragColor = mix(col, vec4(1.0), p);
                        } else if (uType > 6.5 && uType < 7.5) {
                            // Color Flash
                            gl_FragColor = mix(col, vec4(0.0, 0.85, 1.0, 1.0), p);
                        } else if (uType > 9.5 && uType < 10.5) {
                            // Exposure fade
                            gl_FragColor = vec4(col.rgb * (1.0 + sin(p * 3.14159) * 3.0), col.a);
                        } else {
                            // Standard fade / luma / burn dissolve
                            float alpha = 1.0 - smoothstep(0.0, 1.0, p);
                            gl_FragColor = vec4(col.rgb * alpha, col.a);
                        }
                    } 
                    // Family 2: Wipe & Split (1, 14..22)
                    else if ((uType >= 0.5 && uType < 1.5) || (uType >= 13.5 && uType < 22.5)) {
                        float val = 1.0;
                        if (uType > 13.5 && uType < 14.5) {
                            // Wipe Left
                            val = step(1.0 - uv.x, 1.0 - p);
                        } else if (uType > 14.5 && uType < 15.5) {
                            // Wipe Up
                            val = step(uv.y, 1.0 - p);
                        } else if (uType > 16.5 && uType < 17.5) {
                            // Radial wipe
                            float a = (atan(uv.y - 0.5, uv.x - 0.5) / 6.28318) + 0.5;
                            val = step(a, 1.0 - p);
                        } else if (uType > 17.5 && uType < 18.5) {
                            // Circle wipe
                            val = step(length(uv - vec2(0.5)), (1.0 - p) * 0.72);
                        } else {
                            // Horizontal wipe
                            val = step(uv.x, 1.0 - p);
                        }
                        gl_FragColor = vec4(col.rgb * val, col.a);
                    } 
                    // Family 3: Zoom & Warp (2, 32..40)
                    else if ((uType >= 1.5 && uType < 2.5) || (uType >= 31.5 && uType < 40.5)) {
                        vec2 center = vec2(0.5, 0.5);
                        vec2 dir = (uv - center) * p * 0.18;
                        vec4 accum = vec4(0.0);
                        accum += texture2D(uTexSampler, uv - dir * 2.0);
                        accum += texture2D(uTexSampler, uv - dir);
                        accum += texture2D(uTexSampler, uv);
                        accum += texture2D(uTexSampler, uv + dir);
                        accum += texture2D(uTexSampler, uv + dir * 2.0);
                        gl_FragColor = accum / 5.0;
                    } 
                    // Family 4: Slide & Push (3, 23..31)
                    else if ((uType >= 2.5 && uType < 3.5) || (uType >= 22.5 && uType < 31.5)) {
                        vec2 slideUv = uv + vec2(p, 0.0);
                        if (slideUv.x > 1.0 || slideUv.x < 0.0) {
                            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                        } else {
                            gl_FragColor = texture2D(uTexSampler, slideUv);
                        }
                    } 
                    // Family 5: Glitch & Digital (4, 41..49)
                    else if ((uType >= 3.5 && uType < 4.5) || (uType >= 40.5 && uType < 49.5)) {
                        float slice = floor(uv.y * 32.0);
                        float displace = sin(slice * 13.0 + p * 10.0) * 0.04 * p;
                        vec4 gCol = texture2D(uTexSampler, uv + vec2(displace, 0.0));
                        if (abs(displace) > 0.02) {
                            gCol.r = texture2D(uTexSampler, uv + vec2(displace + 0.015, 0.0)).r;
                            gCol.b = texture2D(uTexSampler, uv + vec2(displace - 0.015, 0.0)).b;
                        }
                        gl_FragColor = gCol;
                    } 
                    // Family 6: Light & Flash (50..59)
                    else {
                        float flash = sin(p * 3.14159);
                        vec4 light = vec4(1.0, 0.6, 0.2, 0.0) * flash * 1.5;
                        if (uType > 52.5 && uType < 53.5) {
                            // Strobe
                            flash = mod(p * 6.0, 1.0) > 0.5 ? 0.8 : 0.0;
                            light = vec4(vec3(flash), 0.0);
                        } else if (uType > 53.5 && uType < 54.5) {
                            // Neon cyan
                            light = vec4(0.0, 0.9, 1.0, 0.0) * flash * 1.5;
                        }
                        gl_FragColor = clamp(col + light, 0.0, 1.0);
                    }
                }
            """.trimIndent()
        }
    }
}
