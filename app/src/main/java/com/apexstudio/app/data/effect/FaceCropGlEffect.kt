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
import com.apexstudio.app.domain.model.FaceCropTrack
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.ClipType
import kotlin.math.roundToInt

/**
 * Per-frame crop shader driven by persisted face-track keyframes. The shader receives frame PTS;
 * [FaceCropRenderConfig] makes the PTS convention explicit so trim/speed are never mistaken for
 * absolute source time. The crop window retains the source-pixel aspect ratio encoded by the track.
 */
@UnstableApi
class FaceCropGlEffect private constructor(
    private val config: FaceCropRenderConfig,
) : GlEffect {
    override fun toGlShaderProgram(context: android.content.Context, useHdr: Boolean): GlShaderProgram =
        CropProgram(config, useHdr)

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

        /** Preview's Media3 effect PTS are source-media presentation timestamps (before player speed). */
        @JvmStatic
        fun forPreview(config: FaceCropRenderConfig): FaceCropGlEffect = FaceCropGlEffect(config.previewConfig())

        /** Export runs on clipped source PTS BEFORE any speed-changing or output-geometry effect. */
        @JvmStatic
        fun forExport(config: FaceCropRenderConfig): FaceCropGlEffect = FaceCropGlEffect(config.exportConfig())
    }

    @UnstableApi
    private class CropProgram(
        private val config: FaceCropRenderConfig,
        useHdr: Boolean,
    ) : BaseGlShaderProgram(useHdr, 4) {
        private val glProgram = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        } catch (e: Exception) {
            throw VideoFrameProcessingException("Failed to compile face crop shader", e)
        }

        init {
            glProgram.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            glProgram.setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix())
            glProgram.setFloatsUniform("uTexTransformationMatrix", floatArrayOf(
                0.5f, 0f, 0f, 0f,
                0f, 0.5f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0.5f, 0.5f, 0f, 1f,
            ))
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            val cropAspect = config.track.outputAspectRatio
            // Keep a fixed, aspect-correct output size even as the rectangle moves frame-to-frame.
            val width = minOf(inputWidth.toFloat(), inputHeight * cropAspect).roundToInt().coerceAtLeast(1)
            val height = (width / cropAspect).roundToInt().coerceAtLeast(1)
            return Size(width, height)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val crop = config.track.cropAtSourceTime(config.sourceTimestampMs(presentationTimeUs))
                glProgram.use()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                // Domain is top-left normalized; GL texture v has a bottom-left origin.
                glProgram.setFloatsUniform("uCropWindow", floatArrayOf(
                    crop.left, 1f - crop.bottom, crop.right, 1f - crop.top,
                ))
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            glProgram.delete()
        }
    }

}

/** Shared clip-selection and timestamp contract for preview/export crop configuration. */
data class FaceCropRenderConfig(
    val track: FaceCropTrack,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    /** true for preview source-absolute PTS; false for clipped source PTS BEFORE export speed. */
    val ptsAreSourceAbsolute: Boolean = false,
) {
    init {
        require(trimStartMs >= 0L)
        require(trimEndMs == 0L || trimEndMs >= trimStartMs)
    }

    fun sourceTimestampMs(presentationTimeUs: Long): Long {
        val ptsMs = presentationTimeUs.coerceAtLeast(0L) / 1_000L
        if (ptsAreSourceAbsolute) return ptsMs
        // The effect precedes speed changes. Inverting a speed curve here would apply speed twice.
        val maxOffset = if (trimEndMs > trimStartMs) trimEndMs - trimStartMs else Long.MAX_VALUE - trimStartMs
        // Effects attached to an EditedMediaItem use that item's clock; the sequence placement is
        // applied outside this effect. Add only the clip's source trim to the item-local timestamp.
        return trimStartMs + ptsMs.coerceAtMost(maxOffset)
    }

    fun previewConfig(): FaceCropRenderConfig = copy(ptsAreSourceAbsolute = true)
    fun exportConfig(): FaceCropRenderConfig = copy(ptsAreSourceAbsolute = false)

    companion object {
        /** Main-track video only: image/photo crops stay on the existing photo renderer path. */
        @JvmStatic
        fun forMainVideo(clip: MediaClip?): FaceCropRenderConfig? = clip
            ?.takeIf { it.type == ClipType.VIDEO && it.trackIndex == 0 }
            ?.faceCropTrack
            ?.let { FaceCropRenderConfig(it, clip.trimStartMs, clip.trimEndMs) }
    }
}
