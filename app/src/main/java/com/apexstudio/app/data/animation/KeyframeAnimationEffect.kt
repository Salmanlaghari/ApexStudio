package com.apexstudio.app.data.animation

import android.content.Context
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.apexstudio.app.domain.model.AnimatedTransform
import com.apexstudio.app.domain.model.KeyframeTrack
import com.apexstudio.app.domain.model.SpeedCurveMath
import com.apexstudio.app.domain.model.SpeedPoint

/** One keyframe state sampled on the frame presentation-time clock. */
internal data class KeyframeAnimationSample(
    val timeMs: Long,
    val transform: AnimatedTransform,
    val saturation: Float
)

/**
 * Renders transform, opacity, and saturation from one keyframe sample for each
 * frame. Media3 timestamps are mapped into the project's keyframe clock before
 * interpolation; export callers use [KeyframeAnimationClock].
 */
@UnstableApi
class KeyframeAnimationEffect(
    private val trackProvider: () -> KeyframeTrack,
    /** Converts an effect timestamp into the project-global keyframe clock. */
    private val timeMsAtPresentationTimeUs: (Long) -> Long = { it.coerceAtLeast(0L) / 1_000L }
) {
    fun buildEffects(): List<androidx.media3.common.Effect> {
        val track = trackProvider()
        return if (track.isEmpty()) emptyList() else listOf(KeyframeGlEffect(track, timeMsAtPresentationTimeUs))
    }

    @UnstableApi
    private class KeyframeGlEffect(
        private val track: KeyframeTrack,
        private val timeMsAtPresentationTimeUs: (Long) -> Long
    ) : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
            KeyframeShaderProgram(track, timeMsAtPresentationTimeUs, useHdr)
    }

    @UnstableApi
    private class KeyframeShaderProgram(
        private val track: KeyframeTrack,
        private val timeMsAtPresentationTimeUs: (Long) -> Long,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {
        private val glProgram = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        } catch (e: Exception) {
            throw VideoFrameProcessingException("Failed to compile keyframe animation shader", e)
        }
        private var outputWidth = 1
        private var outputHeight = 1

        init {
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            glProgram.setFloatsUniform("uTexTransformationMatrix", TEXTURE_TRANSFORM)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            outputWidth = inputWidth.coerceAtLeast(1)
            outputHeight = inputHeight.coerceAtLeast(1)
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            val sample = sampleAtPresentationTimeUs(track, presentationTimeUs, timeMsAtPresentationTimeUs)
            try {
                android.opengl.GLES20.glClearColor(0f, 0f, 0f, 1f)
                android.opengl.GLES20.glClear(android.opengl.GLES20.GL_COLOR_BUFFER_BIT)
                glProgram.use()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                glProgram.setFloatsUniform(
                    "uTransformationMatrix",
                    transformToGlMatrix(sample.transform, outputWidth, outputHeight)
                )
                glProgram.setFloatsUniform("uSaturationMatrix", saturationMatrix(sample.saturation))
                glProgram.setFloatUniform("uOpacity", sample.transform.opacity.coerceIn(0f, 1f))
                glProgram.bindAttributesAndUniforms()
                android.opengl.GLES20.glDrawArrays(android.opengl.GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GlUtil.checkGlError()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                glProgram.delete()
            } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TEXTURE_POOL_CAPACITY = 4
        private val TEXTURE_TRANSFORM = floatArrayOf(
            0.5f, 0f, 0f, 0f,
            0f, 0.5f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0.5f, 0.5f, 0f, 1f
        )

        /** Sample every keyframed visual property after mapping the frame clock. */
        internal fun sampleAtPresentationTimeUs(
            track: KeyframeTrack,
            presentationTimeUs: Long,
            timeMsAtPresentationTimeUs: (Long) -> Long = { it.coerceAtLeast(0L) / 1_000L }
        ): KeyframeAnimationSample {
            val timeMs = timeMsAtPresentationTimeUs(presentationTimeUs)
            return KeyframeAnimationSample(
                timeMs = timeMs,
                transform = track.interpolateAt(timeMs),
                saturation = track.saturationFactorAt(timeMs)
            )
        }

        /** Saturation matrix in OpenGL column-major order; alpha is multiplied separately. */
        internal fun saturationMatrix(saturation: Float): FloatArray {
            val inv = 1f - saturation
            val r = 0.2126f
            val g = 0.7152f
            val b = 0.0722f
            return floatArrayOf(
                r + (1f - r) * saturation, r * inv, r * inv, 0f,
                g * inv, g + (1f - g) * saturation, g * inv, 0f,
                b * inv, b * inv, b + (1f - b) * saturation, 0f,
                0f, 0f, 0f, 1f
            )
        }

        /** Android/Compose affine matrix: rotation and scale, then unscaled translation. */
        fun transformToMatrix(t: AnimatedTransform): android.graphics.Matrix {
            val matrix = android.graphics.Matrix()
            matrix.setValues(transformMatrixValues(t))
            return matrix
        }

        internal fun transformMatrixValues(t: AnimatedTransform): FloatArray {
            val radians = Math.toRadians(t.rotationDeg.toDouble())
            val cos = kotlin.math.cos(radians).toFloat() * t.scale
            val sin = kotlin.math.sin(radians).toFloat() * t.scale
            return floatArrayOf(
                cos, -sin, t.translateX,
                sin, cos, t.translateY,
                0f, 0f, 1f
            )
        }

        internal fun transformToGlMatrix(t: AnimatedTransform, width: Int, height: Int): FloatArray {
            val radians = Math.toRadians(t.rotationDeg.toDouble())
            val cos = kotlin.math.cos(radians).toFloat() * t.scale
            val sin = kotlin.math.sin(radians).toFloat() * t.scale
            val safeWidth = width.coerceAtLeast(1).toFloat()
            val safeHeight = height.coerceAtLeast(1).toFloat()
            return floatArrayOf(
                cos, -sin * safeWidth / safeHeight, 0f, 0f,
                sin * safeHeight / safeWidth, cos, 0f, 0f,
                0f, 0f, 1f, 0f,
                2f * t.translateX, -2f * t.translateY, 0f, 1f
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
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform mat4 uSaturationMatrix;
            uniform float uOpacity;
            varying vec2 vTextureCoord;
            void main() {
                vec4 src = texture2D(uTexSampler, vTextureCoord);
                vec3 rgb = (uSaturationMatrix * vec4(src.rgb, 1.0)).rgb * uOpacity;
                gl_FragColor = vec4(rgb, src.a);
            }
        """.trimIndent()
    }
}

/**
 * Maps Media3's pre-speed, clipped-item frame timestamp to the project's
 * sequential main-track clock. Transformer effect timestamps start at zero
 * for the clipped item. The retained trim span defines the speed-curve domain;
 * elapsed source time is converted through the same constant/variable-speed
 * math as the timeline before adding the item's project placement.
 */
internal data class KeyframeAnimationClock(
    val trimStartMs: Long,
    val trimEndMs: Long,
    val timelineStartMs: Long,
    val clipSpeed: Float,
    val speedPoints: List<SpeedPoint>
) {
    fun projectTimeMs(presentationTimeUs: Long): Long {
        val elapsedSourceMs = presentationTimeUs.coerceAtLeast(0L) / 1_000L
        val spanMs = (trimEndMs - trimStartMs).coerceAtLeast(0L)
        val sourceOffsetMs = if (spanMs > 0L) elapsedSourceMs.coerceAtMost(spanMs) else elapsedSourceMs
        val points = SpeedCurveMath.sanitize(speedPoints)
        val outputOffsetMs = if (points.size >= 2 && spanMs > 0L) {
            SpeedCurveMath.sourceToOutputMs(sourceOffsetMs, spanMs, points)
        } else {
            val speed = SpeedCurveMath.sanitizeConstantSpeed(clipSpeed)
            (sourceOffsetMs.toDouble() / speed.toDouble()).toLong().coerceAtLeast(0L)
        }
        val safeStart = timelineStartMs.coerceAtLeast(0L)
        return if (outputOffsetMs > 0L && safeStart > Long.MAX_VALUE - outputOffsetMs) {
            Long.MAX_VALUE
        } else {
            safeStart + outputOffsetMs
        }
    }
}
