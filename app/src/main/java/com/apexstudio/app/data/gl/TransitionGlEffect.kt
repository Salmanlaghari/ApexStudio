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
                    TransitionEngine.Companion.TransitionType.WIPE -> 1
                    TransitionEngine.Companion.TransitionType.ZOOM_BLUR -> 2
                    TransitionEngine.Companion.TransitionType.SLIDE -> 3
                    TransitionEngine.Companion.TransitionType.GLITCH -> 4
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
                    } else {
                        // Glitch
                        float slice = floor(uv.y * 32.0);
                        float displace = sin(slice * 13.0 + p * 10.0) * 0.04 * p;
                        vec4 gCol = texture2D(uTexSampler, uv + vec2(displace, 0.0));
                        gl_FragColor = gCol;
                    }
                }
            """.trimIndent()
        }
    }
}
