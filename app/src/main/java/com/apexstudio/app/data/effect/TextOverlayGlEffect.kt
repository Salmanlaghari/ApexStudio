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
import com.apexstudio.app.data.text.TextAnimEngine
import com.apexstudio.app.data.text.TextFontRegistry
import com.apexstudio.app.data.text.TextSpriteRenderer
import com.apexstudio.app.domain.model.TextOverlay

/**
 * Bakes timed text overlays into the exported video. All captions active on a
 * frame are rendered, in list order, into one full-frame transparent sprite;
 * the effect then composites that sprite in a single GL pass.
 *
 * Only the current active-set sprite is retained. In particular, construction
 * does not rasterize or upload captions that occur later in the project, which
 * keeps word-by-word karaoke exports bounded by the current frame rather than
 * by the total number of words.
 *
 * The single-overlay constructor remains available for existing callers.
 */
@UnstableApi
class TextOverlayGlEffect(
    context: Context,
    private val overlays: List<TextOverlay>,
    private val aspectRatio: Float
) : GlEffect {

    /** Backwards-compatible API for the original one-caption effect. */
    constructor(context: Context, overlay: TextOverlay, aspectRatio: Float) :
        this(context, listOf(overlay), aspectRatio)

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        TextFontRegistry.init(context)
        return TextOverlayShaderProgram(overlays, aspectRatio, useHdr)
    }

    @UnstableApi
    private class TextOverlayShaderProgram(
        private val overlays: List<TextOverlay>,
        private val aspectRatio: Float,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {
        private val glProgram: GlProgram
        private val overlayTexId = intArrayOf(0)
        private var cachedKey: List<TextOverlayFramePlanner.OverlayVisualKey>? = null

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
            glProgram.setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix())
            val texMatrix = floatArrayOf(
                0.5f, 0f, 0f, 0f,
                0f, 0.5f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0.5f, 0.5f, 0f, 1f
            )
            glProgram.setFloatsUniform("uTexTransformationMatrix", texMatrix)
            // Deliberately no sprite/bitmap/texture allocation here: the first
            // upload happens only when at least one timed overlay is active.
        }

        /** Replaces the sole cached texture with a sprite for the active overlays. */
        private fun uploadActiveSprite(plan: TextOverlayFramePlanner.Plan, timeMs: Long) {
            val (width, height) = TextOverlaySpriteSize.forAspect(aspectRatio)
            val states = plan.overlays.associate { overlay ->
                overlay.id to TextAnimEngine.fullState(overlay, timeMs, forExport = true)
            }
            val masks = plan.overlays.mapNotNull { overlay ->
                overlay.effectiveMaskAt(timeMs)?.let { overlay.id to it }
            }.toMap()
            val bitmap = TextSpriteRenderer.render(
                overlays = plan.overlays,
                width = width,
                height = height,
                states = states,
                masks = masks
            )
            val newTexture = intArrayOf(0)
            try {
                // Do not keep textures for inactive captions/windows. There is
                // at most one cached texture for the entire overlay list.
                deleteCachedTexture()
                GLES20.glGenTextures(1, newTexture, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, newTexture[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                GlUtil.checkGlError()
                overlayTexId[0] = newTexture[0]
                newTexture[0] = 0
            } finally {
                bitmap.recycle()
                if (newTexture[0] != 0) GLES20.glDeleteTextures(1, newTexture, 0)
            }
        }

        private fun deleteCachedTexture() {
            if (overlayTexId[0] != 0) {
                GLES20.glDeleteTextures(1, overlayTexId, 0)
                overlayTexId[0] = 0
            }
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val timeMs = presentationTimeUs / 1000L
                val plan = TextOverlayFramePlanner.plan(overlays, timeMs)
                if (plan.overlays.isEmpty()) {
                    deleteCachedTexture()
                    cachedKey = null
                } else if (plan.cacheKey != cachedKey) {
                    try {
                        uploadActiveSprite(plan, timeMs)
                        cachedKey = plan.cacheKey
                    } catch (e: Exception) {
                        // A failed render/upload must not display pixels from a
                        // previous timestamp or retain its full-frame sprite.
                        deleteCachedTexture()
                        cachedKey = null
                        Log.w(TAG, "Text overlay frame failed at ${timeMs}ms", e)
                    }
                }

                glProgram.use()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                val hasCurrentSprite = plan.cacheKey != null && cachedKey == plan.cacheKey && overlayTexId[0] != 0
                glProgram.setSamplerTexIdUniform(
                    "uOverlaySampler",
                    if (hasCurrentSprite) overlayTexId[0] else 0,
                    1
                )
                glProgram.setFloatsUniform("uOverlayEnabled", floatArrayOf(if (hasCurrentSprite) 1f else 0f))
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            deleteCachedTexture()
            cachedKey = null
            glProgram.delete()
        }
    }

    companion object {
        private const val TAG = "TextOverlayGlEffect"
        private const val TEXTURE_POOL_CAPACITY = 4

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
            uniform float uOverlayEnabled;
            void main() {
                vec4 video = texture2D(uTexSampler, vTextureCoord);
                if (uOverlayEnabled > 0.5) {
                    vec4 caption = texture2D(uOverlaySampler, vTextureCoord);
                    // Android Canvas/ARGB_8888 sprites upload premultiplied RGB.
                    // Multiplying by alpha again darkens translucent text/masks.
                    vec3 outRgb = caption.rgb + video.rgb * (1.0 - caption.a);
                    gl_FragColor = vec4(outRgb, video.a);
                } else {
                    gl_FragColor = video;
                }
            }
        """.trimIndent()
    }
}
