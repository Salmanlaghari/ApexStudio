package com.apexstudio.app.data.effect

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
 * Media3 [GlEffect] that crops the video to a normalized window of
 * the source frame and scales that window up to fill the output —
 * the standard way a video editor's crop box is applied.
 *
 * [window] is expressed in texture space with a bottom-left origin
 * (u0, v0, u1, v1), where (0,0) is the bottom-left of the frame and
 * (1,1) is the top-right. Build it from a UI-space crop rectangle
 * (0..1, top-left origin) via [fromRect], which flips the Y axis.
 *
 * The effect is a pure "sample a sub-rect of the input and stretch it
 * over the full output quad" pass, so it composes correctly with the
 * LUT / keyframe effects and is used both by the ExoPlayer preview
 * (`player.setVideoEffects`) and the Media3 Transformer export
 * pipeline.
 */
@UnstableApi
class VideoCropGlEffect private constructor(
    private val u0: Float,
    private val v0: Float,
    private val u1: Float,
    private val v1: Float
) : GlEffect {

    override fun toGlShaderProgram(context: android.content.Context, useHdr: Boolean): GlShaderProgram {
        return CropShaderProgram(u0, v0, u1, v1, useHdr)
    }

    companion object {
        private const val TEXTURE_POOL_CAPACITY = 1

        /**
         * Build a crop effect from a UI crop rectangle given in
         * normalized (0..1) coordinates with a **top-left** origin (as
         * produced by the editor's crop overlay). Returns null when the
         * rect covers the full frame, i.e. there is nothing to crop.
         */
        @JvmStatic
        fun fromRect(left: Float, top: Float, right: Float, bottom: Float): VideoCropGlEffect? {
            val l = left.coerceIn(0f, 1f)
            val t = top.coerceIn(0f, 1f)
            val r = right.coerceIn(0f, 1f)
            val b = bottom.coerceIn(0f, 1f)
            if (r - l < 0.01f || b - t < 0.01f) return null
            // Whole frame → no crop needed.
            if (l <= 0.001f && t <= 0.001f && r >= 0.999f && b >= 0.999f) return null
            // Flip to bottom-left texture space: texture v=0 is the
            // bottom of the picture (UI bottom = 1).
            return VideoCropGlEffect(
                u0 = l,
                v0 = 1f - b,
                u1 = r,
                v1 = 1f - t
            )
        }

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
            uniform vec4 uCropWindow;
            void main() {
                vec2 uv = vTextureCoord;
                vec2 cropUv = vec2(
                    uCropWindow.x + uv.x * (uCropWindow.z - uCropWindow.x),
                    uCropWindow.y + uv.y * (uCropWindow.w - uCropWindow.y)
                );
                gl_FragColor = texture2D(uTexSampler, cropUv);
            }
        """.trimIndent()
    }

    @UnstableApi
    private class CropShaderProgram(
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {

        private val glProgram: GlProgram

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: Exception) {
                throw VideoFrameProcessingException("Failed to compile crop shader", e)
            }

            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )

            // uTransformationMatrix: identity — NDC pass-through.
            glProgram.setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix())

            // uTexTransformationMatrix: map NDC [-1,1] → UV [0,1].
            val texMatrix = floatArrayOf(
                0.5f, 0f, 0f, 0f,
                0f, 0.5f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0.5f, 0.5f, 0f, 1f
            )
            glProgram.setFloatsUniform("uTexTransformationMatrix", texMatrix)

            // The crop window in texture space: (u0, v0, u1, v1).
            glProgram.setFloatsUniform("uCropWindow", floatArrayOf(u0, v0, u1, v1))
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
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
}
