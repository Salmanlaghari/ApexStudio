package com.apexstudio.app.data.filter

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
 * Real-time 4x5 ColorMatrix effect integrated directly into the Media3 OpenGL pipeline.
 * Applies color grading, adjustments (brightness, contrast, saturation, temperature, tint),
 * and filter matrix calculations across all Android versions.
 */
@UnstableApi
class ColorMatrixGlEffect(
    private val matrixProvider: () -> FloatArray
) : GlEffect {

    constructor(matrix: FloatArray) : this({ matrix })

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ColorMatrixShaderProgram(matrixProvider, useHdr)
    }

    @UnstableApi
    private class ColorMatrixShaderProgram(
        private val matrixProvider: () -> FloatArray,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, 4) {

        private val glProgram: GlProgram

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: Exception) {
                throw VideoFrameProcessingException("Failed to compile ColorMatrixGlEffect shader", e)
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

                val m = matrixProvider()
                if (m.size >= 20) {
                    glProgram.setFloatsUniform("uRow0", floatArrayOf(m[0], m[1], m[2], m[3]))
                    glProgram.setFloatsUniform("uRow1", floatArrayOf(m[5], m[6], m[7], m[8]))
                    glProgram.setFloatsUniform("uRow2", floatArrayOf(m[10], m[11], m[12], m[13]))
                    glProgram.setFloatsUniform("uRow3", floatArrayOf(m[15], m[16], m[17], m[18]))
                    glProgram.setFloatsUniform(
                        "uColorOffset",
                        floatArrayOf(m[4] / 255f, m[9] / 255f, m[14] / 255f, m[19] / 255f)
                    )
                } else {
                    // Identity fallback
                    glProgram.setFloatsUniform("uRow0", floatArrayOf(1f, 0f, 0f, 0f))
                    glProgram.setFloatsUniform("uRow1", floatArrayOf(0f, 1f, 0f, 0f))
                    glProgram.setFloatsUniform("uRow2", floatArrayOf(0f, 0f, 1f, 0f))
                    glProgram.setFloatsUniform("uRow3", floatArrayOf(0f, 0f, 0f, 1f))
                    glProgram.setFloatsUniform("uColorOffset", floatArrayOf(0f, 0f, 0f, 0f))
                }

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
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform sampler2D uTexSampler;
                uniform vec4 uRow0;
                uniform vec4 uRow1;
                uniform vec4 uRow2;
                uniform vec4 uRow3;
                uniform vec4 uColorOffset;

                void main() {
                    vec4 src = texture2D(uTexSampler, vTextureCoord);
                    float r = dot(src, uRow0) + uColorOffset.r;
                    float g = dot(src, uRow1) + uColorOffset.g;
                    float b = dot(src, uRow2) + uColorOffset.b;
                    float a = dot(src, uRow3) + uColorOffset.a;
                    gl_FragColor = vec4(clamp(vec3(r, g, b), 0.0, 1.0), clamp(a, 0.0, 1.0));
                }
            """.trimIndent()
        }
    }
}
