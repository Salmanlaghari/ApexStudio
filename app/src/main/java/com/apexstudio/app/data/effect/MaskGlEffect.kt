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
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask

/**
 * Media3 [GlEffect] that applies a [VideoMask] (CapCut-style Mask tool)
 * to the video frame: only the masked region is kept, everything
 * outside fades to black (with [VideoMask.feather] softening the edge).
 *
 * Mask geometry is normalised to the frame with a **top-left** origin
 * (the same convention as the editor UI), so the Y axis is flipped to
 * texture space inside the shader. Used by the Media3 Transformer
 * export pipeline; the live preview shows the equivalent region via
 * [com.apexstudio.app.ui.screens.editor.MaskPreviewOverlay], which
 * resolves the same numbers — WYSIWYG.
 */
@UnstableApi
class MaskGlEffect private constructor(
    private val mask: VideoMask
) : GlEffect {

    override fun toGlShaderProgram(context: android.content.Context, useHdr: Boolean): GlShaderProgram {
        return MaskShaderProgram(mask, useHdr)
    }

    companion object {
        private const val TEXTURE_POOL_CAPACITY = 4

        /**
         * Build the effect for [mask], or null when there is nothing
         * to do (disabled mask, or a full-frame non-inverted mask with
         * full opacity — i.e. a no-op).
         */
        fun fromMask(mask: VideoMask?): MaskGlEffect? {
            if (mask == null || !mask.enabled) return null
            if (mask.opacity <= 0.001f) return null
            // Full-size non-inverted rect/ellipse with full opacity is a
            // no-op. LINEAR is excluded: its wipe position (centerX) still
            // cuts the frame even at full size, so it must reach the shader.
            val coversAll = mask.sizeX >= 0.999f && mask.sizeY >= 0.999f &&
                    !mask.invert && mask.shape != MaskShape.LINEAR
            if (coversAll && mask.opacity >= 0.999f) return null
            return MaskGlEffect(mask)
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

        // Signed-distance-field mask. Distances are computed in
        // frame-height units (x is aspect-corrected) so the feather
        // width looks uniform on any aspect ratio.
        private val FRAGMENT_SHADER = """
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform vec2 uCenter;    // top-left origin, 0..1
            uniform vec2 uSize;      // full width/height, 0..1
            uniform float uFeather;  // 0..1
            uniform float uInvert;   // 0 or 1
            uniform float uOpacity;  // 0..1
            uniform float uAspect;   // frame width / height
            uniform float uShape;    // 0 = rect, 1 = ellipse, 2 = linear

            // Mask factor in [0,1]. Rect/ellipse use a signed-distance
            // field in frame-height units (x aspect-corrected) so the
            // feather looks uniform on any aspect ratio. LINEAR is a
            // vertical wipe: the line sits at uCenter.x, the left side
            // is kept, and uSize.x sets the transition width.
            float maskFactor(vec2 p) {
                float m;
                if (uShape < 1.5) {
                    vec2 q = (p - uCenter) * vec2(uAspect, 1.0);
                    vec2 half_ = uSize * 0.5 * vec2(uAspect, 1.0);
                    float dist;
                    if (uShape < 0.5) {
                        vec2 d = abs(q) - half_;
                        dist = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
                    } else {
                        vec2 h = max(half_, vec2(1e-4));
                        dist = (length(q / h) - 1.0) * min(h.x, h.y);
                    }
                    float featherW = max(uFeather * 0.3, 1e-4);
                    m = 1.0 - smoothstep(-featherW, 0.0, dist);
                } else {
                    float fw = max(uSize.x * 0.5, 1e-4);
                    float d = (p.x - uCenter.x) / fw;
                    float featherW = max(uFeather, 1e-4);
                    m = 1.0 - smoothstep(-featherW, 0.0, d);
                }
                if (uInvert > 0.5) m = 1.0 - m;
                return m * uOpacity;
            }

            void main() {
                vec4 c = texture2D(uTexSampler, vTextureCoord);
                // Flip to top-left origin to match the editor UI.
                vec2 p = vec2(vTextureCoord.x, 1.0 - vTextureCoord.y);
                float m = maskFactor(p);
                gl_FragColor = vec4(c.rgb * m, c.a);
            }
        """.trimIndent()
    }

    @UnstableApi
    private class MaskShaderProgram(
        mask: VideoMask,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {

        private val glProgram: GlProgram
        private var aspect: Float = 16f / 9f

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: Exception) {
                throw VideoFrameProcessingException("Failed to compile mask shader", e)
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

            val shapeId = when (mask.shape) {
                MaskShape.RECTANGLE -> 0f
                MaskShape.ELLIPSE -> 1f
                MaskShape.LINEAR -> 2f
            }
            glProgram.setFloatsUniform("uCenter", floatArrayOf(mask.centerX, mask.centerY))
            glProgram.setFloatsUniform("uSize", floatArrayOf(mask.sizeX, mask.sizeY))
            glProgram.setFloatUniform("uFeather", mask.feather.coerceIn(0f, 1f))
            glProgram.setFloatUniform("uInvert", if (mask.invert) 1f else 0f)
            glProgram.setFloatUniform("uOpacity", mask.opacity.coerceIn(0f, 1f))
            glProgram.setFloatUniform("uShape", shapeId)
            glProgram.setFloatUniform("uAspect", aspect)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            aspect = if (inputHeight > 0) inputWidth.toFloat() / inputHeight.toFloat() else 16f / 9f
            try {
                glProgram.use()
                glProgram.setFloatUniform("uAspect", aspect)
            } catch (_: Exception) {
            }
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
