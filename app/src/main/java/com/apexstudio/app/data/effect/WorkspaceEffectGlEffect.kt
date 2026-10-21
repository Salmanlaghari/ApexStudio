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
import com.apexstudio.app.domain.model.hasValidParameters
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
                program.setFloatUniform("uExposure", effect.exposure.takeIf { it.isFinite() }?.coerceIn(-2f, 2f) ?: 0f)
                program.setFloatUniform("uContrast", effect.contrast.takeIf { it.isFinite() }?.coerceIn(0.5f, 1.5f) ?: 1f)
                program.setFloatUniform("uSaturation", effect.saturation.takeIf { it.isFinite() }?.coerceIn(0f, 2f) ?: 1f)
                program.setFloatUniform("uKind", kindFor(effect.id).toFloat())
                val elapsedMs = if (sourceTimeMs >= effect.startMs) sourceTimeMs - effect.startMs else 0L
                program.setFloatUniform("uElapsed", (elapsedMs.coerceAtMost(86_400_000L) / 1_000f))
                program.setFloatsUniform("uTexel", floatArrayOf(1f / width, 1f / height))
                program.setFloatUniform("uAspect", width.toFloat() / height.toFloat())
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
            WorkspaceEffectPresets.CARTOON -> 5
            WorkspaceEffectPresets.BUBBLE -> 6
            WorkspaceEffectPresets.HEARTS -> 7
            WorkspaceEffectPresets.HEARTBEAT -> 8
            WorkspaceEffectPresets.ICE -> 9
            WorkspaceEffectPresets.FILTER_SOFT_WARM -> 10
            WorkspaceEffectPresets.FILTER_COOL -> 11
            WorkspaceEffectPresets.FILTER_FADED -> 12
            WorkspaceEffectPresets.FILTER_AMBER -> 13
            WorkspaceEffectPresets.FILTER_ROSE -> 14
            WorkspaceEffectPresets.FILTER_MINT -> 15
            WorkspaceEffectPresets.FILTER_TEAL_ORANGE -> 16
            WorkspaceEffectPresets.FILTER_CRISP -> 17
            WorkspaceEffectPresets.FILTER_MATTE -> 18
            WorkspaceEffectPresets.FILTER_BW_CONTRAST -> 19
            WorkspaceEffectPresets.FILTER_SEPIA -> 20
            WorkspaceEffectPresets.FILTER_LAVENDER -> 21
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
            uniform float uExposure;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uKind;
            uniform float uElapsed;
            uniform float uAspect;
            uniform vec2 uTexel;

            float hash21(vec2 p) {
                p = fract(p * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            float heartField(vec2 p) {
                p.x = abs(p.x);
                float a = p.x * p.x + p.y * p.y - 1.0;
                return a * a * a - p.x * p.x * p.y * p.y * p.y;
            }

            vec3 addHeart(vec3 base, vec2 uv, vec2 center, float size, vec3 color) {
                vec2 p = vec2((uv.x - center.x) / size, (center.y - uv.y) / size);
                float shape = 1.0 - smoothstep(-0.025, 0.035, heartField(p));
                float shine = 1.0 - smoothstep(0.0, 0.24, length(p - vec2(-0.27, 0.38)));
                return mix(base, min(color * (1.0 + shine * 0.28), vec3(1.0)), shape * 0.92);
            }

            vec3 bubbleLook(vec2 uv) {
                vec2 warped = uv;
                float rim = 0.0;
                float shine = 0.0;
                for (int i = 0; i < 3; i++) {
                    vec2 center = (i == 0)
                        ? vec2(0.27 + 0.035 * sin(uElapsed * 0.65), 0.34 + 0.025 * cos(uElapsed * 0.8))
                        : ((i == 1)
                            ? vec2(0.69 + 0.03 * cos(uElapsed * 0.5 + 1.0), 0.62 + 0.035 * sin(uElapsed * 0.7))
                            : vec2(0.48 + 0.04 * sin(uElapsed * 0.4 + 2.0), 0.22 + 0.025 * cos(uElapsed * 0.6)));
                    vec2 delta = (uv - center) * vec2(uAspect, 1.0);
                    float distanceToCenter = length(delta);
                    float radius = (i == 1) ? 0.145 : 0.105;
                    float inside = 1.0 - smoothstep(radius - 0.012, radius + 0.012, distanceToCenter);
                    float edge = 1.0 - smoothstep(0.014, 0.033, abs(distanceToCenter - radius));
                    vec2 lensOffset = (center - uv) * (inside * 0.18);
                    warped += lensOffset;
                    rim = max(rim, edge);
                    float glint = 1.0 - smoothstep(0.018, 0.045, length(delta - vec2(-radius * 0.36, radius * 0.48)));
                    shine = max(shine, glint * inside);
                }
                vec3 refracted = texture2D(uTexSampler, clamp(warped, vec2(0.001), vec2(0.999))).rgb;
                return mix(refracted, vec3(0.72, 0.91, 1.0), rim * 0.58) + vec3(0.18, 0.28, 0.34) * shine;
            }

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
                } else if (uKind < 4.5) {
                    float count = (uKind < 3.5) ? 2.0 : 3.0;
                    vec2 sourceUv = vec2(fract(vTextureCoord.x * count), vTextureCoord.y);
                    effected = texture2D(uTexSampler, sourceUv).rgb;
                } else if (uKind < 5.5) {
                    vec3 north = texture2D(uTexSampler, vTextureCoord + vec2(0.0, uTexel.y * 2.0)).rgb;
                    vec3 east = texture2D(uTexSampler, vTextureCoord + vec2(uTexel.x * 2.0, 0.0)).rgb;
                    float edge = max(length(original.rgb - north), length(original.rgb - east));
                    float ink = smoothstep(0.12, 0.38, edge);
                    float steps = mix(9.0, 4.0, uIntensity);
                    vec3 poster = floor(original.rgb * steps + 0.5) / steps;
                    effected = poster * mix(vec3(1.0), vec3(0.22, 0.25, 0.31), ink * 0.8);
                } else if (uKind < 6.5) {
                    effected = bubbleLook(vTextureCoord);
                } else if (uKind < 7.5) {
                    float bob = sin(uElapsed * 2.0) * 0.035;
                    effected = addHeart(original.rgb, vTextureCoord, vec2(0.22, 0.28 + bob), 0.095, vec3(1.0, 0.25, 0.40));
                    effected = addHeart(effected, vTextureCoord, vec2(0.53, 0.66 - bob), 0.078, vec3(1.0, 0.48, 0.66));
                    effected = addHeart(effected, vTextureCoord, vec2(0.79, 0.39 + bob), 0.088, vec3(0.98, 0.31, 0.52));
                } else if (uKind < 8.5) {
                    float phase = fract(uElapsed * 1.15);
                    float firstBeat = (phase - 0.09) * 27.0;
                    float secondBeat = (phase - 0.24) * 25.0;
                    float beat = max(exp(-firstBeat * firstBeat), 0.72 * exp(-secondBeat * secondBeat));
                    effected = original.rgb * (vec3(1.0) + vec3(0.14, -0.16, -0.10) * beat) + vec3(0.10, 0.008, 0.018) * beat;
                } else if (uKind < 9.5) {
                    vec2 cell = floor(vTextureCoord * vec2(54.0, 54.0));
                    float frostSpeck = step(0.994, hash21(cell));
                    float vignette = smoothstep(0.0, 0.7, length((vTextureCoord - 0.5) * vec2(uAspect, 1.0)));
                    effected = original.rgb * vec3(0.78, 0.93, 1.17) + vec3(0.05, 0.12, 0.18) * vignette + vec3(0.75, 0.91, 1.0) * frostSpeck * 0.45;
                } else if (uKind < 10.5) {
                    effected = original.rgb * vec3(1.06, 1.01, 0.94) + vec3(0.018, 0.006, -0.012);
                    effected = pow(max(effected, vec3(0.0)), vec3(0.96));
                } else if (uKind < 11.5) {
                    float luma = dot(original.rgb, vec3(0.299, 0.587, 0.114));
                    effected = original.rgb * vec3(0.91, 1.00, 1.10) + vec3(0.008, 0.018, 0.035) * (1.0 - luma);
                } else if (uKind < 12.5) {
                    float luma = dot(original.rgb, vec3(0.299, 0.587, 0.114));
                    vec3 muted = mix(vec3(luma), original.rgb, 0.76);
                    effected = mix(vec3(0.075), muted * 0.86 + vec3(0.10), 0.92);
                } else if (uKind < 13.5) {
                    effected = original.rgb * vec3(1.12, 1.01, 0.83) + vec3(0.035, 0.012, -0.022);
                } else if (uKind < 14.5) {
                    effected = original.rgb * vec3(1.045, 0.94, 1.00) + vec3(0.018, 0.003, 0.021);
                } else if (uKind < 15.5) {
                    effected = original.rgb * vec3(0.91, 1.035, 0.98) + vec3(0.012, 0.025, 0.018);
                } else if (uKind < 16.5) {
                    float luma = dot(original.rgb, vec3(0.299, 0.587, 0.114));
                    float shadows = 1.0 - smoothstep(0.18, 0.72, luma);
                    float highlights = smoothstep(0.24, 0.82, luma);
                    effected = original.rgb + vec3(-0.015, 0.035, 0.055) * shadows + vec3(0.075, 0.018, -0.025) * highlights;
                } else if (uKind < 17.5) {
                    vec3 centered = (original.rgb - 0.5) * 1.12 + 0.5;
                    float luma = dot(centered, vec3(0.299, 0.587, 0.114));
                    effected = mix(vec3(luma), centered, 1.08);
                } else if (uKind < 18.5) {
                    effected = (original.rgb - 0.5) * 0.88 + 0.5;
                    effected = effected * vec3(0.98, 1.00, 1.03) + vec3(0.055, 0.048, 0.045);
                } else if (uKind < 19.5) {
                    float gray = dot(original.rgb, vec3(0.299, 0.587, 0.114));
                    float contrast = clamp((gray - 0.5) * 1.42 + 0.5, 0.0, 1.0);
                    effected = vec3(contrast);
                } else if (uKind < 20.5) {
                    float gray = dot(original.rgb, vec3(0.299, 0.587, 0.114));
                    effected = vec3(gray) * vec3(1.12, 0.91, 0.70) + vec3(0.025, 0.012, 0.0);
                } else if (uKind < 21.5) {
                    effected = original.rgb * vec3(0.98, 0.91, 1.08) + vec3(0.018, 0.012, 0.035);
                } else {
                    effected = original.rgb;
                }
                vec3 graded = effected * exp2(uExposure);
                graded = (graded - 0.5) * uContrast + 0.5;
                float gradedLuma = dot(graded, vec3(0.299, 0.587, 0.114));
                graded = mix(vec3(gradedLuma), graded, uSaturation);
                gl_FragColor = vec4(mix(original.rgb, clamp(graded, 0.0, 1.0), uEnabled * uIntensity), original.a);
            }
        """.trimIndent()

        /** The one canonical effect-chain constructor used in preview and both export routes. */
        fun createEffects(effects: List<WorkspaceEffect>, sourceTimeOffsetMs: Long = 0L): List<GlEffect> = effects
            .filter { it.hasValidParameters() && it.intensity > 0f }
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
