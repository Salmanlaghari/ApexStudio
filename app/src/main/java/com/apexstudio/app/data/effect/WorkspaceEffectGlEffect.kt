package com.apexstudio.app.data.effect

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
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.WorkspaceEffect
import com.apexstudio.app.domain.model.WorkspaceEffectPresets
import com.apexstudio.app.domain.model.isActiveAtSourceTime

/**
 * Deterministic ranged visual effect, shared by the ExoPlayer preview and Transformer export.
 * [WorkspaceEffect.startMs]/[WorkspaceEffect.endMs] are absolute source-media timestamps;
 * Preview PTS are source-absolute; export PTS are trim-relative before speed changes.
 * Export callers therefore supply the source trim offset. Windows are start-inclusive/end-exclusive.
 */
@UnstableApi
class WorkspaceEffectGlEffect(private val effect: WorkspaceEffect, private val sourceTimeOffsetMs: Long = 0L) : GlEffect {
    init { require(sourceTimeOffsetMs >= 0L) }
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        WorkspaceShaderProgram(effect, sourceTimeOffsetMs, useHdr)

    @UnstableApi
    private class WorkspaceShaderProgram(
        private val effect: WorkspaceEffect,
        private val sourceTimeOffsetMs: Long,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {
        private val program = try {
            GlProgram(VERTEX_SHADER, fragmentShaderSource())
        } catch (e: Exception) {
            throw VideoFrameProcessingException("Failed to compile workspace effect shader", e)
        }
        private var width = 1
        private var height = 1

        init {
            program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            program.setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix())
            program.setFloatsUniform(
                "uTexTransformationMatrix",
                floatArrayOf(
                    0.5f, 0f, 0f, 0f,
                    0f, 0.5f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0.5f, 0.5f, 0f, 1f
                )
            )
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            width = inputWidth.coerceAtLeast(1)
            height = inputHeight.coerceAtLeast(1)
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val sourceTimeMs = sourceTimeMs(presentationTimeUs, sourceTimeOffsetMs)
                val enabled = effect.isActiveAtSourceTime(sourceTimeMs)
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setFloatUniform("uEnabled", if (enabled) 1f else 0f)
                program.setFloatUniform("uIntensity", effect.intensity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f)
                program.setFloatUniform("uKind", kindFor(effect.id).toFloat())
                program.setFloatsUniform("uTexel", floatArrayOf(1f / width, 1f / height))
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            program.delete()
        }
    }

    companion object {
        private const val TEXTURE_POOL_CAPACITY = 4

        private fun kindFor(id: String): Int = when (id) {
            WorkspaceEffectPresets.MONO -> 0
            WorkspaceEffectPresets.WARM -> 1
            WorkspaceEffectPresets.PIXELATE -> 2
            WorkspaceEffectPresets.SPLIT_2 -> 3
            WorkspaceEffectPresets.SPLIT_3 -> 4
            else -> -1
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

        /** Shared GLSL for all workspace-effect users; split variants repeat identical source tiles. */
        internal fun fragmentShaderSource(): String = """
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform float uEnabled;
            uniform float uIntensity;
            uniform float uKind;
            uniform vec2 uTexel;
            void main() {
                vec4 original = texture2D(uTexSampler, vTextureCoord);
                vec3 effected = original.rgb;
                if (uKind < 0.5) {
                    float gray = dot(original.rgb, vec3(0.299, 0.587, 0.114));
                    effected = vec3(gray);
                } else if (uKind < 1.5) {
                    effected = original.rgb * vec3(1.08, 1.00, 0.86);
                } else if (uKind < 2.5) {
                    float blockPx = mix(32.0, 6.0, uIntensity);
                    vec2 pixel = floor(vTextureCoord / uTexel / blockPx) * blockPx + blockPx * 0.5;
                    effected = texture2D(uTexSampler, pixel * uTexel).rgb;
                } else {
                    float count = (uKind < 3.5) ? 2.0 : 3.0;
                    vec2 sourceUv = vec2(fract(vTextureCoord.x * count), vTextureCoord.y);
                    effected = texture2D(uTexSampler, sourceUv).rgb;
                }
                gl_FragColor = vec4(mix(original.rgb, effected, uEnabled * uIntensity), original.a);
            }
        """.trimIndent()

        /** The one canonical effect-chain constructor used in preview and both export routes. */
        fun createEffects(effects: List<WorkspaceEffect>, sourceTimeOffsetMs: Long = 0L): List<GlEffect> = effects
            .filter { it.id in WorkspaceEffectPresets.all && it.endMs > it.startMs && it.startMs >= 0L && it.intensity.isFinite() && it.intensity > 0f }
            .map { WorkspaceEffectGlEffect(it, sourceTimeOffsetMs) }

        /** Source-clock evaluation happens before any export speed transformation. */
        internal fun sourceTimeMs(presentationTimeUs: Long, offsetMs: Long): Long {
            require(offsetMs >= 0L)
            val relativeMs = presentationTimeUs / 1_000L
            if (presentationTimeUs < 0L) return -1L // Do not enable ranges for decode preroll.
            return if (relativeMs > Long.MAX_VALUE - offsetMs) Long.MAX_VALUE else relativeMs + offsetMs
        }

        fun forClip(clip: MediaClip?): List<GlEffect> = createEffects(clip?.workspaceEffects.orEmpty())
    }
}
