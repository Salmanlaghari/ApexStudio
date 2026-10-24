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
import com.apexstudio.app.domain.model.ChromaKeySettings

/**
 * Shared chroma-key pass for preview and Transformer export.
 *
 * The key target is normalized RGB distance from [ChromaKeySettings.keyColorArgb].
 * [similarity] is the fully keyed threshold and [smoothness] is the transition
 * width. Spill suppression desaturates the dominant key channel on retained pixels.
 * The pass preserves alpha so a later composition layer can provide a background.
 */
@UnstableApi
class ChromaKeyGlEffect private constructor(private val settings: ChromaKeySettings) : GlEffect {
    override fun toGlShaderProgram(context: android.content.Context, useHdr: Boolean): GlShaderProgram =
        Program(settings, useHdr)

    companion object {
        private const val TEXTURE_POOL_CAPACITY = 4

        @JvmStatic
        fun fromSettings(settings: ChromaKeySettings?): ChromaKeyGlEffect? =
            settings?.takeIf { it.enabled }?.let { ChromaKeyGlEffect(it.sanitized()) }

        internal fun normalizedKeyColor(argb: Long): FloatArray = floatArrayOf(
            ((argb shr 16) and 0xFF).toFloat() / 255f,
            ((argb shr 8) and 0xFF).toFloat() / 255f,
            (argb and 0xFF).toFloat() / 255f
        )

        internal const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            uniform mat4 uTransformationMatrix;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uTransformationMatrix * aFramePosition;
                vTextureCoord = (uTexTransformationMatrix * aFramePosition).xy;
            }
        """

        internal const val FRAGMENT_SHADER = """
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform vec3 uKeyColor;
            uniform float uSimilarity;
            uniform float uSmoothness;
            uniform float uSpillSuppression;
            void main() {
                vec4 source = texture2D(uTexSampler, vTextureCoord);
                float distanceToKey = sqrt(dot(source.rgb - uKeyColor, source.rgb - uKeyColor) / 3.0);
                float matte = smoothstep(uSimilarity, uSimilarity + uSmoothness, distanceToKey);
                vec3 rgb = source.rgb;
                float keyDominant = max(max(uKeyColor.r, uKeyColor.g), uKeyColor.b);
                if (uKeyColor.g == keyDominant && rgb.g > max(rgb.r, rgb.b)) {
                    rgb.g -= (rgb.g - max(rgb.r, rgb.b)) * uSpillSuppression;
                } else if (uKeyColor.b == keyDominant && rgb.b > max(rgb.r, rgb.g)) {
                    rgb.b -= (rgb.b - max(rgb.r, rgb.g)) * uSpillSuppression;
                } else if (uKeyColor.r == keyDominant && rgb.r > max(rgb.g, rgb.b)) {
                    rgb.r -= (rgb.r - max(rgb.g, rgb.b)) * uSpillSuppression;
                }
                gl_FragColor = vec4(rgb, source.a * matte);
            }
        """
    }

    @UnstableApi
    private class Program(settings: ChromaKeySettings, useHdr: Boolean) :
        BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {
        private val program: GlProgram

        init {
            program = try { GlProgram(VERTEX_SHADER, FRAGMENT_SHADER) }
            catch (e: Exception) { throw VideoFrameProcessingException("Failed to compile chroma-key shader", e) }
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            program.setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix())
            program.setFloatsUniform("uTexTransformationMatrix", floatArrayOf(
                0.5f, 0f, 0f, 0f, 0f, 0.5f, 0f, 0f,
                0f, 0f, 1f, 0f, 0.5f, 0.5f, 0f, 1f
            ))
            program.setFloatsUniform("uKeyColor", normalizedKeyColor(settings.keyColorArgb))
            program.setFloatUniform("uSimilarity", settings.similarity.coerceIn(0.01f, 1f))
            program.setFloatUniform("uSmoothness", settings.smoothness.coerceIn(0.01f, 0.5f))
            program.setFloatUniform("uSpillSuppression", settings.spillSuppression.coerceIn(0f, 1f))
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            try { super.release() } finally { program.delete() }
        }
    }
}

private fun ChromaKeySettings.sanitized(): ChromaKeySettings = copy(
    similarity = similarity.takeIf { it.isFinite() }?.coerceIn(0.01f, 1f) ?: 0.4f,
    smoothness = smoothness.takeIf { it.isFinite() }?.coerceIn(0.01f, 0.5f) ?: 0.15f,
    spillSuppression = spillSuppression.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
)
