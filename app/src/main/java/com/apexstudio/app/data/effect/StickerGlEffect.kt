package com.apexstudio.app.data.effect

import android.content.Context
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
import com.apexstudio.app.data.stickers.LottieFrameCache
import com.apexstudio.app.data.stickers.StickerImageCache
import com.apexstudio.app.data.stickers.StickerSpriteRenderer
import com.apexstudio.app.domain.model.StickerOverlay

/**
 * Bakes one PNG [StickerOverlay] into the exported video.
 *
 * The sticker bitmap is rasterised onto a transparent sprite whose
 * aspect ratio matches the source video (crop / cutout / rotation /
 * opacity applied), uploaded as a second GL texture and
 * alpha-composited over every frame inside the sticker's
 * [StickerOverlay.startMs]..[StickerOverlay.endMs] window.
 *
 * The editor preview draws through [StickerSpriteRenderer] with the
 * same geometry, so the exported sticker sits at exactly the
 * position, size, rotation and crop the user set on the canvas.
 *
 * Multiple stickers = multiple effect instances appended to the
 * effect chain (each composites its own sprite on top of the output
 * of the previous one). Legacy emoji stickers (no [StickerOverlay.assetPath])
 * keep going through [TextOverlayGlEffect].
 */
@UnstableApi
class StickerGlEffect(
    private val context: Context,
    private val sticker: StickerOverlay,
    private val aspectRatio: Float
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return StickerShaderProgram(context, sticker, aspectRatio, useHdr)
    }

    @UnstableApi
    private class StickerShaderProgram(
        context: Context,
        private val sticker: StickerOverlay,
        aspectRatio: Float,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY) {

        // Constructor params captured as properties: init can use the
        // params directly, but drawFrame (where keyframed stickers
        // re-upload) cannot — plain params are out of scope there.
        private val effectContext: Context = context
        private val effectAspectRatio: Float = aspectRatio

        private val glProgram: GlProgram
        private val overlayTexId: IntArray = intArrayOf(0)

        // Phase 4: animated Lottie stickers — frame strip + sprite geometry,
        // re-uploaded only when the frame index changes.
        private val lottieFrames: List<android.graphics.Bitmap>? =
            if (LottieFrameCache.isLottieAsset(sticker.assetPath)) {
                try {
                    LottieFrameCache.frames(context, sticker.assetPath!!)
                } catch (e: Exception) {
                    throw VideoFrameProcessingException("Animated sticker asset is unavailable; export stopped rather than omitting it", e)
                }
            } else null
        private var lottieW = 0
        private var lottieH = 0
        private var lastLottieFrame = -1
        // Keyframe state: re-rasterise the sprite only when the
        // keyframe-interpolated transform visibly changes (quantised),
        // mirroring TextOverlayGlEffect's animated re-upload. Without
        // keyframes this key is constant and the sprite uploads once.
        private var lastKeyframeStateKey: Any? = null

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: Exception) {
                throw VideoFrameProcessingException("Failed to compile sticker overlay shader", e)
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

            // Fail closed: a missing asset or GPU failure must not produce a
            // successful export with this selected sticker silently omitted.
            lastKeyframeStateKey = keyframeStateKey(sticker)
            val path = sticker.assetPath
            if (!path.isNullOrBlank()) {
                try {
                    if (lottieFrames != null) {
                        // Lottie: remember sprite geometry; first frame
                        // uploaded now, rest swapped in drawFrame().
                        val (w, h) = spriteSize(aspectRatio)
                        lottieW = w
                        lottieH = h
                        ensureLottieTexture()
                        uploadLottieFrame(lottieFrames[0])
                        lastLottieFrame = 0
                    } else {
                        uploadSprite(context, sticker, path, aspectRatio)
                    }
                } catch (e: Exception) {
                    throw VideoFrameProcessingException("Sticker asset could not be rendered ($path); export stopped rather than omitting it", e)
                }
            }
        }

        private fun uploadSprite(
            context: Context,
            sticker: StickerOverlay,
            assetPath: String,
            aspect: Float
        ) {
            // Sprite canvas: same aspect as the video, long edge
            // capped so a 4K source doesn't need a 33 MB texture just
            // to place a sticker. Position / size are normalised
            // against the canvas so the composition is resolution
            // independent.
            val (w, h) = StickerSpriteSize.forAspect(aspect)

            val bitmap = requireNotNull(StickerImageCache(context).get(assetPath)) {
                "Sticker asset is missing or cannot be decoded: $assetPath"
            }
            val sprite = StickerSpriteRenderer.render(bitmap, sticker, w, h)
            val tex = IntArray(1)
            try {
                // Drop the previous texture before uploading the new
                // keyframe state — otherwise every re-rasterise leaks
                // a GPU texture (keyframed stickers re-upload often).
                if (overlayTexId[0] != 0) {
                    GLES20.glDeleteTextures(1, overlayTexId, 0)
                    overlayTexId[0] = 0
                }
                GLES20.glGenTextures(1, tex, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, sprite, 0)
                GlUtil.checkGlError()
                overlayTexId[0] = tex[0]
            } finally {
                sprite.recycle()
            }
        }

        /** Sprite canvas size for the current video aspect (shared PNG/Lottie). */
        private fun spriteSize(aspect: Float): Pair<Int, Int> {
            return StickerSpriteSize.forAspect(aspect)
        }

        private fun uploadLottieFrame(frame: android.graphics.Bitmap, eff: StickerOverlay = sticker) {
            val sprite = StickerSpriteRenderer.render(frame, eff, lottieW, lottieH)
            try {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId[0])
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, sprite, 0)
                GlUtil.checkGlError()
            } finally {
                sprite.recycle()
            }
        }

        private fun ensureLottieTexture() {
            if (overlayTexId[0] != 0) return
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            overlayTexId[0] = tex[0]
        }

        /**
         * The sticker with its keyframe offsets applied at [timeMs] —
         * base position/size/rotation/opacity PLUS the interpolated
         * keyframe transform, exactly as the preview evaluates it.
         * Without keyframes this is the sticker itself.
         */
        private fun effectiveStickerAt(timeMs: Long): StickerOverlay {
            if (sticker.keyframes.keyframes.isEmpty()) return sticker
            val kf = sticker.effectiveTransformAt(timeMs)
            return sticker.copy(
                x = (sticker.x + kf.translateX).coerceIn(0f, 1f),
                y = (sticker.y + kf.translateY).coerceIn(0f, 1f),
                sizeScale = (sticker.sizeScale * kf.scale).coerceIn(0.05f, 8f),
                rotationDeg = sticker.rotationDeg + kf.rotationDeg,
                opacity = (sticker.opacity * kf.opacity).coerceIn(0f, 1f)
            )
        }

        /**
         * Quantised keyframe state: the sprite only re-rasterises when
         * the effective transform visibly changed (sub-pixel jitter
         * collapses), so smooth keyframed motion bakes per-frame
         * without re-uploading an identical sprite.
         */
        private fun keyframeStateKey(eff: StickerOverlay): Any = listOf(
            (eff.x * 10000f).toInt(),
            (eff.y * 10000f).toInt(),
            (eff.sizeScale * 1000f).toInt(),
            (eff.rotationDeg * 10f).toInt(),
            (eff.opacity * 255f).toInt()
        )

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                val timeMs = presentationTimeUs / 1000L
                val inWindow = timeMs >= sticker.startMs && timeMs <= sticker.endMs
                // Keyframed stickers: re-rasterise when the interpolated
                // transform changed (or the Lottie frame advanced).
                val eff = effectiveStickerAt(timeMs)
                val kfKey = keyframeStateKey(eff)
                val kfChanged = kfKey != lastKeyframeStateKey
                if (inWindow && kfChanged) {
                    try {
                        val frames = lottieFrames
                        if (frames != null && frames.isNotEmpty()) {
                            val idx = LottieFrameCache.frameIndex(timeMs, sticker.startMs, frames.size)
                            ensureLottieTexture()
                            uploadLottieFrame(frames[idx], eff)
                            lastLottieFrame = idx
                        } else {
                            val path = sticker.assetPath
                            if (!path.isNullOrBlank()) uploadSprite(effectContext, eff, path, effectAspectRatio)
                        }
                        lastKeyframeStateKey = kfKey
                    } catch (e: Exception) {
                        throw VideoFrameProcessingException("Sticker animation failed; export stopped rather than freezing it", e)
                    }
                }
                // Phase 4: animated Lottie sticker — advance frames by time.
                val frames = lottieFrames
                if (frames != null && frames.isNotEmpty() && inWindow) {
                    val idx = LottieFrameCache.frameIndex(timeMs, sticker.startMs, frames.size)
                    if (idx != lastLottieFrame) {
                        ensureLottieTexture()
                        uploadLottieFrame(frames[idx], eff)
                        lastLottieFrame = idx
                    }
                }
                val isVisible = (overlayTexId[0] != 0) && (timeMs >= sticker.startMs && timeMs <= sticker.endMs)
                val overlayId = if (isVisible) overlayTexId[0] else inputTexId
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
        private const val TAG = "StickerGlEffect"
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
            void main() {
                vec4 video = texture2D(uTexSampler, vTextureCoord);
                vec4 sticker = texture2D(uOverlaySampler, vTextureCoord);
                // Straight alpha blend of the sticker sprite.
                vec3 outRgb = sticker.rgb * sticker.a + video.rgb * (1.0 - sticker.a);
                gl_FragColor = vec4(outRgb, video.a);
            }
        """.trimIndent()
    }
}
