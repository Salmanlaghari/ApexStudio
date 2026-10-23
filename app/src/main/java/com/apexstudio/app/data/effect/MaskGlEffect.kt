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
import com.apexstudio.app.domain.model.MaskModel
import com.apexstudio.app.domain.model.MaskShape
import com.apexstudio.app.domain.model.VideoMask
import com.apexstudio.app.domain.model.resolveMaskAt

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
    private val mask: VideoMask,
    private val maskTimeMsProvider: (() -> Long)?,
    private val timeMsAtPresentationTimeUs: ((Long) -> Long)?
) : GlEffect {

    override fun toGlShaderProgram(context: android.content.Context, useHdr: Boolean): GlShaderProgram {
        return MaskShaderProgram(mask, maskTimeMsProvider, timeMsAtPresentationTimeUs, useHdr)
    }

    companion object {
        private const val TEXTURE_POOL_CAPACITY = 4

        // Must match GLSL: uniform float uPolyX[32] / uPolyY[32].
        private const val POLY_POINTS = 32

        /**
         * Build the effect for [mask], or null when there is nothing
         * to do (disabled mask, or a full-frame non-inverted mask with
         * full opacity — i.e. a no-op).
         */
        fun fromMask(
            mask: VideoMask?,
            maskTimeMsProvider: (() -> Long)? = null,
            timeMsAtPresentationTimeUs: ((Long) -> Long)? = null
        ): MaskGlEffect? {
            if (mask == null || !mask.enabled) return null
            // An animated mask may have a transparent base value while its
            // keyframes become visible later; do not prune that live effect.
            val animated = mask.maskKeyframes.isNotEmpty()
            if (mask.opacity <= 0.001f && !animated) return null
            // Full-size non-inverted rect/ellipse with full opacity is a
            // no-op. LINEAR is excluded: its wipe position (centerX) still
            // cuts the frame even at full size, so it must reach the shader.
            val coversAll = mask.sizeX >= 0.999f && mask.sizeY >= 0.999f &&
                    !mask.invert && mask.shape != MaskShape.LINEAR
            if (coversAll && mask.opacity >= 0.999f && !animated) return null
            return MaskGlEffect(mask, maskTimeMsProvider, timeMsAtPresentationTimeUs)
        }

        /** Resolve the geometry for one decoded frame; exposed for JVM tests. */
        internal fun resolveMaskAtFrame(
            mask: VideoMask,
            presentationTimeUs: Long,
            maskTimeMsProvider: (() -> Long)? = null,
            timeMsAtPresentationTimeUs: ((Long) -> Long)? = null
        ): VideoMask {
            if (mask.maskKeyframes.isEmpty()) return mask
            val timeMs = (maskTimeMsProvider?.invoke()
                ?: timeMsAtPresentationTimeUs?.invoke(presentationTimeUs)
                ?: (presentationTimeUs / 1_000L))
                .coerceAtLeast(0L)
            return mask.resolveMaskAt(timeMs)
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
        // Signed-distance mask. Distances live in frame-height units
        // (x aspect-corrected) so the feather looks uniform at any
        // aspect ratio, and shape points are rotated before measuring.
        //
        // uShape contract (see MaskShape.shaderId):
        //   0 rect | 1 ellipse | 2 horizontal wipe | 3 circle |
        //   4 heart | 5 star | 6 triangle | 7 mirror | 8 pen | 9 blur faces
        // Modes 4/5/6/8 are polygons fed from MaskModel.outline() via
        // uPoly, the SAME points the preview strokes, which is what keeps
        // preview and export in lockstep instead of drifting.
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
            uniform float uShape;    // see contract above
            uniform float uRotation; // clockwise radians, y-down frame
            // Two float arrays rather than one vec2[]: GlProgram only sets
            // float arrays with glUniform1fv, and mixing in a vec2 array
            // would make the whole call a GL_INVALID_OPERATION.
            uniform float uPolyX[32]; // polygon x, shape space
            uniform float uPolyY[32]; // polygon y, shape space
            uniform float uPolyN;     // incl. the closing duplicate

            float sdBox(vec2 q, vec2 h) {
                vec2 d = abs(q) - h;
                return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
            }

            float sdOval(vec2 q, vec2 h) {
                vec2 hh = max(h, vec2(1e-4));
                return (length(q / hh) - 1.0) * min(hh.x, hh.y);
            }

            // Even-odd polygon test: distance d (negative inside), signed.
            float sdPoly(vec2 p) {
                float d = 1e9;
                float inside = 0.0;
                for (int i = 0; i < 31; i++) {
                    if (float(i + 1) >= uPolyN) break;
                    vec2 a = vec2(uPolyX[i], uPolyY[i]);
                    vec2 b = vec2(uPolyX[i + 1], uPolyY[i + 1]);
                    vec2 e = b - a;
                    vec2 w = p - a;
                    float ee = max(dot(e, e), 1e-7);
                    float t = clamp(dot(w, e) / ee, 0.0, 1.0);
                    d = min(d, length(w - e * t));
                    if ((b.y > p.y) != (a.y > p.y)) {
                        float slope = (abs(e.y) < 1e-7 ? 1e-7 : e.y);
                        if (p.x < a.x + e.x * ((p.y - a.y) / slope)) {
                            inside = 1.0 - inside;
                        }
                    }
                }
                return inside > 0.5 ? -d : d;
            }

            vec2 rot(vec2 v) {
                float cc = cos(-uRotation);
                float ss = sin(-uRotation);
                return vec2(v.x * cc - v.y * ss, v.x * ss + v.y * cc);
            }

            float applyMask(float dist) {
                float fw = max(uFeather * 0.3, 1e-4);
                float m = 1.0 - smoothstep(-fw, 0.0, dist);
                if (uInvert > 0.5) m = 1.0 - m;
                return m * uOpacity;
            }

            float maskFactor(vec2 p) {
                vec2 q = rot((p - uCenter) * vec2(uAspect, 1.0));
                vec2 half_ = uSize * 0.5 * vec2(uAspect, 1.0);

                if (uShape < 0.5) return applyMask(sdBox(q, half_));
                if (uShape < 1.5 || (uShape > 2.5 && uShape < 3.5)) {
                    // ellipse and circle share the SDF; circle must be
                    // square, so collapse the extent to its short side.
                    if (uShape > 2.5 && uShape < 3.5) {
                        float r = min(half_.x, half_.y);
                        half_ = vec2(r, r);
                    }
                    return applyMask(sdOval(q, half_));
                }
                if (uShape < 2.5) {
                    // Horizontal wipe - unchanged ramp: the line sits at
                    // uCenter.x, left side kept, uSize.x is the width.
                    float fw = max(uSize.x * 0.5, 1e-4);
                    float d = (p.x - uCenter.x) / fw;
                    float fwN = max(uFeather, 1e-4);
                    float m = 1.0 - smoothstep(-fwN, 0.0, d);
                    if (uInvert > 0.5) m = 1.0 - m;
                    return m * uOpacity;
                }
                if (uShape > 8.5 && uShape < 9.5) {
                    // Blur-faces cuts nothing; main() softens the region.
                    return uOpacity;
                }
                if (uShape > 6.5 && uShape < 7.5) {
                    // Mirror folds the frame about its vertical centre, so
                    // the kept region reads symmetric on both halves.
                    q.x = abs(q.x);
                    return applyMask(sdOval(q, half_));
                }
                // 4 heart, 5 star, 6 triangle, 8 pen: true polygon.
                vec2 n = max(half_, vec2(1e-4));
                float sw = min(n.x, n.y);
                return applyMask(sdPoly(q / n) * sw);
            }

            void main() {
                vec4 c = texture2D(uTexSampler, vTextureCoord);
                // Flip to top-left origin to match the editor UI.
                vec2 p = vec2(vTextureCoord.x, 1.0 - vTextureCoord.y);

                // Blur-faces keeps the frame and only softens the region
                // (9-tap box blur on the face-sized footstep), then applies
                // the ordinary opacity multiplies like every other mode.
                if (uShape > 8.5 && uShape < 9.5) {
                    vec2 q0 = rot((p - uCenter) * vec2(uAspect, 1.0));
                    vec2 half0 = max(uSize * 0.5 * vec2(uAspect, 1.0), vec2(1e-4));
                    float radial = length(q0 / half0);
                    float soft = max(uFeather, 0.06);
                    float face = 1.0 - smoothstep(1.0 - soft, 1.0, radial);
                    if (uInvert > 0.5) face = 1.0 - face;
                    vec2 stepUv = vec2(uSize.y * 0.05 / max(uAspect, 1e-3), uSize.y * 0.05) * 0.5;
                    vec4 blur = vec4(0.0);
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            blur += texture2D(uTexSampler, vTextureCoord + vec2(float(i), float(j)) * stepUv);
                        }
                    }
                    blur /= 9.0;
                    c = mix(c, blur, clamp(face, 0.0, 1.0));
                    gl_FragColor = vec4(c.rgb * uOpacity, c.a);
                    return;
                }

                float m = maskFactor(p);
                gl_FragColor = vec4(c.rgb * m, c.a);
            }
        """.trimIndent()
    }

    @UnstableApi
    private class MaskShaderProgram(
        private val mask: VideoMask,
        private val maskTimeMsProvider: (() -> Long)?,
        private val timeMsAtPresentationTimeUs: ((Long) -> Long)?,
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

            // Single source of truth: MaskShape.shaderId pins 0 rect,
            // 1 ellipse, 2 horizontal, 3 circle, 4 heart, 5 star,
            // 6 triangle, 7 mirror, 8 pen, 9 blur faces, so reordering
            // the enum can never silently remap an exported mask.
            val shapeId = mask.shape.shaderId
            glProgram.setFloatUniform("uShape", shapeId)

            // Bug 4's ten-mode contract travels alongside the shape id.
            // The polygon batches are MaskModel.outline() - literally the
            // points MaskPreviewOverlay stroked into its Path this frame -
            // so preview and export cannot disagree about the region.
            setAnimatedUniforms(mask)
            val polyPoints = MaskModel.outline(mask.shape, mask.drawPoints)
            val polyCount = minOf(polyPoints.size, POLY_POINTS)
            val polyX = FloatArray(POLY_POINTS)
            val polyY = FloatArray(POLY_POINTS)
            for (i in 0 until polyCount) {
                polyX[i] = polyPoints[i].x
                polyY[i] = polyPoints[i].y
            }
            glProgram.setFloatsUniform("uPolyX", polyX)
            glProgram.setFloatsUniform("uPolyY", polyY)
            glProgram.setFloatUniform("uPolyN", polyCount.toFloat())
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
                setAnimatedUniforms(resolveMaskAtFrame(mask, presentationTimeUs, maskTimeMsProvider, timeMsAtPresentationTimeUs))
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            try {
                super.release()
            } finally {
                // The base class owns the texture pool, not this linked shader program.
                try {
                    glProgram.delete()
                } catch (e: GlUtil.GlException) {
                    throw VideoFrameProcessingException(e)
                }
            }
        }

        private fun setAnimatedUniforms(frameMask: VideoMask) {
            glProgram.setFloatsUniform("uCenter", floatArrayOf(frameMask.centerX, frameMask.centerY))
            glProgram.setFloatsUniform("uSize", floatArrayOf(frameMask.sizeX, frameMask.sizeY))
            glProgram.setFloatUniform("uFeather", frameMask.feather.coerceIn(0f, 1f))
            glProgram.setFloatUniform("uInvert", if (frameMask.invert) 1f else 0f)
            glProgram.setFloatUniform("uOpacity", frameMask.opacity.coerceIn(0f, 1f))
            glProgram.setFloatUniform(
                "uRotation",
                Math.toRadians(frameMask.rotationDeg.toDouble()).toFloat()
            )
        }
    }
}
