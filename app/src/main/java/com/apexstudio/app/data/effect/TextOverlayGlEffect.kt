package com.apexstudio.app.data.effect

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.apexstudio.app.data.text.TextSpriteRenderer
import com.apexstudio.app.domain.model.TextOverlay

/**
 * Bakes one [TextOverlay] into the exported video.
 *
 * The caption is rasterised onto a transparent sprite whose aspect
 * ratio matches the source video (so normalised caption coordinates
 * line up 1:1 with the frame), uploaded as a second GL texture and
 * alpha-composited over every frame. Because the editor preview uses
 * the same [TextSpriteRenderer] geometry, the exported caption sits
 * at exactly the position and size the user dragged it to on screen.
 *
 * Multiple captions = multiple effect instances appended to the
 * effect chain (each composites its own sprite on top of the output
 * of the previous one).
 */
@UnstableApi
class TextOverlayGlEffect(
    private val context: Context,
    private val overlay: TextOverlay,
    private val aspectRatio: Float
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return TextOverlayShaderProgram(context, overlay, aspectRatio, useHdr)
    }

    @UnstableApi
    private class TextOverlayShaderProgram(
        context: Context,
        overlay: TextOverlay,
        aspectRatio: Float,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {

        private val glProgram: GlProgram
        private val overlayTexId: IntArray = intArrayOf(0)

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: Exception) {
                throw VideoFrameProcessingException("Failed to compile text overlay shader", e)
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

            // Rasterise + upload the caption sprite (best effort — a
            // blank caption or upload failure leaves overlayTexId = 0
            // and drawFrame falls back to a video pass-through).
            if (!overlay.text.isBlank()) {
                try {
                    uploadSprite(overlay, aspectRatio)
                } catch (e: Exception) {
                    Log.w(TAG, "Text overlay sprite upload failed (${overlay.text})", e)
                }
            }
        }

        private fun uploadSprite(overlay: TextOverlay, aspect: Float) {
            // Sprite canvas: same aspect as the video, long edge
            // capped so a 4K source doesn't need a 33 MB texture just
            // to place a caption. Font size / position are normalised
            // against the canvas so the composition is resolution
            // independent.
            val longEdge = 1600
            val shortEdge = (longEdge / aspect.coerceIn(0.2f, 5f)).toInt().coerceAtLeast(1)
            val (w, h) = if (aspect >= 1f) longEdge to shortEdge else shortEdge to longEdge

            val bitmap = TextSpriteRenderer.render(listOf(overlay), w, h)
            val tex = IntArray(1)
            try {
                GLES20.glGenTextures(1, tex, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                GlUtil.checkGlError()
                overlayTexId[0] = tex[0]
            } finally {
                bitmap.recycle()
            }
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                // When no sprite is available (blank caption / upload
                // failure), sample the video itself as the "overlay" so
                // the alpha blend is an identity pass-through.
                val overlayId = if (overlayTexId[0] != 0) overlayTexId[0] else inputTexId
                glProgram.setSamplerTexIdUniform("uOverlaySampler", overlayId, 1)
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            if (overlayTexId[0] != 0) {
                GLES20.glDeleteTextures(1, overlayTexId, 0)
                overlayTexId[0] = 0
            }
        }
    }

    companion object {
        private const val TAG = "TextOverlayGlEffect"
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

        private val FRAGMENT_SHADER = """
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform sampler2D uOverlaySampler;
            void main() {
                vec4 video = texture2D(uTexSampler, vTextureCoord);
                vec4 caption = texture2D(uOverlaySampler, vTextureCoord);
                // Straight alpha blend of the caption sprite.
                vec3 outRgb = caption.rgb * caption.a + video.rgb * (1.0 - caption.a);
                gl_FragColor = vec4(outRgb, video.a);
            }
        """.trimIndent()
    }
}
