package com.apexstudio.app.data.effect

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Composites a video Picture-in-Picture overlay in the export.
 *
 * The overlay video is decoded with [MediaCodec] to a [SurfaceTexture] (as an
 * external OES texture) and alpha-composited over the main video at the
 * overlay's persisted transform — centre ([centerX], [centerY]) normalised
 * 0..1, width [widthFraction] of the output width in a 16:9 box, and
 * [rotationDeg] clockwise — exactly matching the preview's PipOverlayCanvas.
 *
 * Still-image overlays (no video track in the URI) are decoded to a bitmap
 * and composited through a regular 2D texture for the same timeline window,
 * so picture overlays are no longer silently dropped from the export.
 *
 * The PiP is positioned at [offsetMs] on the export timeline and shows the
 * overlay's [trimStartMs, trimEndMs] range. If the decoder fails for any
 * reason, the effect degrades gracefully to a pass-through (main video only).
 *
 * Multiple PiP overlays = multiple effect instances chained (each composites
 * on top of the previous output).
 */
@UnstableApi
class PipOverlayGlEffect(
    private val context: Context,
    private val overlayUri: String,
    private val offsetMs: Long = 0L,
    private val trimStartMs: Long = 0L,
    private val trimEndMs: Long = Long.MAX_VALUE,
    private val widthFraction: Float = 0.25f,
    private val centerX: Float = 0.85f,
    private val centerY: Float = 0.85f,
    private val rotationDeg: Float = 0f,
    private val opacity: Float = 1f
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return PipOverlayShaderProgram(
            appContext = context.applicationContext,
            overlayUri = overlayUri,
            offsetMs = offsetMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            widthFraction = widthFraction,
            centerX = centerX,
            centerY = centerY,
            rotationDeg = rotationDeg,
            opacity = opacity,
            useHdr = useHdr
        )
    }

    @UnstableApi
    private class PipOverlayShaderProgram(
        private val appContext: Context,
        private val overlayUri: String,
        private val offsetMs: Long,
        private val trimStartMs: Long,
        private val trimEndMs: Long,
        private val widthFraction: Float,
        private val centerX: Float,
        private val centerY: Float,
        private val rotationDeg: Float,
        private val opacity: Float,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, TEXTURE_POOL_CAPACITY),
        SurfaceTexture.OnFrameAvailableListener {

        private val glProgram: GlProgram
        private var pipTextureId: Int = 0
        private var surfaceTexture: SurfaceTexture? = null
        private var decoderSurface: Surface? = null
        private var decoder: MediaCodec? = null
        private var extractor: MediaExtractor? = null
        private var overlayDurationUs: Long = Long.MAX_VALUE
        private var decoderReady: Boolean = false
        private var frameAvailable: Boolean = false
        private var lastOverlayTimeUs: Long = -1L
        private var inputEos: Boolean = false

        // PiP geometry in normalized device coordinates (computed in configure()).
        // Centre (uPipCenter, NDC) + full size (uPipSize, NDC) + clockwise
        // rotation (uPipAngle, radians) — mirrors the preview canvas geometry.
        private var pipCenterNdc = floatArrayOf(0.7f, -0.7f)
        private var pipSizeNdc = floatArrayOf(0.5f, 0.28125f)
        private var pipOutputPx = floatArrayOf(1920f, 1080f)

        // Image-overlay path: when the overlay URI has no video track
        // (a still picture), the bitmap is uploaded to a regular 2D
        // texture and composited for the same timeline window instead of
        // being silently dropped.
        private var imageTextureId: Int = 0
        private var hasImageOverlay: Boolean = false

        init {
            glProgram = try {
                GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: Exception) {
                throw VideoFrameProcessingException("Failed to compile PiP overlay shader", e)
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

            try {
                setupDecoder()
            } catch (e: Exception) {
                // No decodable video track — fall back to a still image
                // overlay (picture PiP) instead of disabling the PiP.
                Log.i(TAG, "No video track in overlay $overlayUri, trying image path")
                releaseDecoder()
                try {
                    setupImageTexture()
                } catch (e2: Exception) {
                    Log.w(TAG, "PiP image setup failed for $overlayUri, PiP disabled", e2)
                    releaseImageTexture()
                }
            }
        }

        /**
         * Uploads a still-image overlay to a regular 2D texture, downsampled
         * (the PiP box is small) so the export never OOMs on a huge photo.
         */
        private fun setupImageTexture() {
            val bytes = appContext.contentResolver
                .openInputStream(Uri.parse(overlayUri))?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Cannot open overlay image $overlayUri")
            val bounds = android.graphics.BitmapFactory.Options()
                .apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IllegalArgumentException("Cannot decode overlay image $overlayUri")
            }
            var sample = 1
            while ((bounds.outWidth / sample) > 512 || (bounds.outHeight / sample) > 512) {
                sample *= 2
            }
            val opts = android.graphics.BitmapFactory.Options()
                .apply { inSampleSize = sample }
            val bitmap = android.graphics.BitmapFactory
                .decodeByteArray(bytes, 0, bytes.size, opts)
                ?: throw IllegalArgumentException("Cannot decode overlay image $overlayUri")
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
            )
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
            imageTextureId = tex[0]
            hasImageOverlay = true
            Log.d(TAG, "PiP image texture ready for $overlayUri")
        }

        private fun releaseImageTexture() {
            hasImageOverlay = false
            if (imageTextureId != 0) {
                try {
                    GLES20.glDeleteTextures(1, intArrayOf(imageTextureId), 0)
                } catch (_: Exception) {}
                imageTextureId = 0
            }
        }

        private fun setupDecoder() {
            val ext = MediaExtractor()
            ext.setDataSource(appContext, Uri.parse(overlayUri), null)
            var videoTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until ext.trackCount) {
                val f = ext.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrack = i
                    format = f
                    break
                }
            }
            if (videoTrack == -1 || format == null) {
                ext.release()
                throw IllegalArgumentException("No video track in overlay $overlayUri")
            }
            ext.selectTrack(videoTrack)
            overlayDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                Long.MAX_VALUE
            }

            // Create external OES texture for the decoder output.
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
            )
            pipTextureId = tex[0]

            val st = SurfaceTexture(pipTextureId)
            st.setOnFrameAvailableListener(this)
            surfaceTexture = st
            val surf = Surface(st)
            decoderSurface = surf

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val dec = MediaCodec.createDecoderByType(mime)
            dec.configure(format, surf, null, 0)
            // Seek to trim start.
            if (trimStartMs > 0) {
                ext.seekTo(trimStartMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }
            dec.start()
            decoder = dec
            extractor = ext
            decoderReady = true
            Log.d(TAG, "PiP decoder ready for $overlayUri")
        }

        override fun onFrameAvailable(st: SurfaceTexture?) {
            synchronized(this) { frameAvailable = true }
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            // Compute PiP geometry. Output is inputWidth x inputHeight.
            // PiP width = widthFraction of output width; height preserves
            // the 16:9 box the preview canvas uses (PIP_BOX_ASPECT).
            val outW = inputWidth.toFloat()
            val outH = inputHeight.toFloat()
            val pipW = outW * widthFraction.coerceIn(0.05f, 0.9f)
            val pipH = pipW * 9f / 16f
            // NDC: x in [-1, 1] left-to-right, y in [-1, 1] bottom-to-top.
            // centre x/y are normalised 0..1 from the left / top.
            val cx = (centerX.coerceIn(0f, 1f) * 2f) - 1f
            val cy = 1f - (centerY.coerceIn(0f, 1f) * 2f)
            pipCenterNdc = floatArrayOf(cx, cy)
            pipSizeNdc = floatArrayOf((pipW / outW) * 2f, (pipH / outH) * 2f)
            pipOutputPx = floatArrayOf(outW, outH)
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                val texMatrix = floatArrayOf(
                    0.5f, 0f, 0f, 0f,
                    0f, 0.5f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0.5f, 0.5f, 0f, 1f
                )
                glProgram.setFloatsUniform("uTexTransformationMatrix", texMatrix)

                // Determine if the PiP should be visible at this timestamp.
                val timeMs = presentationTimeUs / 1000L
                // 0-based time since the overlay started on the export timeline —
                // i.e. the playback position within the trimmed overlay clip.
                val overlayTimeMs = timeMs - offsetMs
                // The overlay plays its trimmed [trimStartMs, trimEndMs] range of
                // the source media, so it is visible for exactly the trimmed
                // duration starting at offsetMs. overlayTimeMs is 0-based, so it
                // must NOT be compared against trimStartMs directly.
                val trimmedDurationMs = when {
                    trimEndMs == Long.MAX_VALUE -> Long.MAX_VALUE
                    trimEndMs > trimStartMs -> trimEndMs - trimStartMs
                    else -> 0L
                }
                // Source-media timestamp of the frame to show. The decoder was
                // seeked to trimStartMs in setupDecoder(), so decoded frames carry
                // source-media timestamps — the trim offset must be added back.
                val sourceTimeUs = (overlayTimeMs + trimStartMs) * 1000L
                val inWindow = overlayTimeMs >= 0 && overlayTimeMs < trimmedDurationMs
                val visible = decoderReady && inWindow && sourceTimeUs < overlayDurationUs

                var pipTex = 0
                var pipIsImage = 0f
                if (hasImageOverlay) {
                    // Still picture: composite for the same timeline window.
                    if (inWindow && imageTextureId != 0) {
                        pipTex = imageTextureId
                        pipIsImage = 1f
                    }
                } else if (visible) {
                    pipTex = updateOverlayTexture(sourceTimeUs)
                }

                if (pipTex != 0) {
                    // Composite: sample PiP texture, blend at the overlay's
                    // transform (centre / size / rotation). Images use the
                    // regular 2D sampler, video the external OES sampler.
                    if (pipIsImage > 0.5f) {
                        glProgram.setSamplerTexIdUniform("uPipImageSampler", pipTex, 1)
                    } else {
                        glProgram.setSamplerTexIdUniform("uPipSampler", pipTex, 1)
                    }
                    glProgram.setFloatsUniform("uPipCenter", pipCenterNdc)
                    glProgram.setFloatsUniform("uPipSize", pipSizeNdc)
                    glProgram.setFloatsUniform("uOutputSize", pipOutputPx)
                    glProgram.setFloatsUniform(
                        "uPipAngle",
                        floatArrayOf(Math.toRadians(rotationDeg.toDouble()).toFloat())
                    )
                    glProgram.setFloatsUniform("uPipIsImage", floatArrayOf(pipIsImage))
                    glProgram.setFloatsUniform("uPipOpacity", floatArrayOf(opacity.coerceIn(0f, 1f)))
                    glProgram.setFloatsUniform("uPipEnabled", floatArrayOf(1f))
                } else {
                    glProgram.setFloatsUniform("uPipEnabled", floatArrayOf(0f))
                }
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            } catch (e: Exception) {
                // Never fail the export because of PiP — fall back to pass-through.
                Log.w(TAG, "PiP drawFrame failed, passing through", e)
                try {
                    glProgram.use()
                    glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                    glProgram.setFloatsUniform("uPipEnabled", floatArrayOf(0f))
                    glProgram.bindAttributesAndUniforms()
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                } catch (_: Exception) {}
            }
        }

        /**
         * Advances the overlay decoder to [targetTimeUs] and returns the texture ID
         * if a frame is available, or 0 if not.
         */
        private fun updateOverlayTexture(targetTimeUs: Long): Int {
            val dec = decoder ?: return 0
            val ext = extractor ?: return 0
            if (!decoderReady) return 0

            try {
                // If seeking backwards (or first frame), seek the extractor.
                if (targetTimeUs < lastOverlayTimeUs) {
                    ext.seekTo(targetTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    dec.flush()
                    inputEos = false
                    lastOverlayTimeUs = -1L
                }

                val timeoutUs = 10_000L
                var attempts = 0
                // Feed input and drain output until we reach the target timestamp.
                while (attempts < 30) {
                    attempts++
                    // Feed input.
                    if (!inputEos) {
                        val inIndex = dec.dequeueInputBuffer(timeoutUs)
                        if (inIndex >= 0) {
                            val buffer = dec.getInputBuffer(inIndex)!!
                            val sampleSize = ext.readSampleData(buffer, 0)
                            if (sampleSize < 0) {
                                dec.queueInputBuffer(
                                    inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEos = true
                            } else {
                                val st = ext.sampleTime
                                dec.queueInputBuffer(inIndex, 0, sampleSize, st, 0)
                                ext.advance()
                            }
                        }
                    }
                    // Drain output.
                    val info = MediaCodec.BufferInfo()
                    val outIndex = dec.dequeueOutputBuffer(info, timeoutUs)
                    if (outIndex >= 0) {
                        val render = info.presentationTimeUs <= targetTimeUs + 33_000L
                        dec.releaseOutputBuffer(outIndex, render)
                        if (render) {
                            lastOverlayTimeUs = info.presentationTimeUs
                            // Check if a new frame is available.
                            synchronized(this) {
                                if (frameAvailable) {
                                    frameAvailable = false
                                    surfaceTexture?.updateTexImage()
                                    return pipTextureId
                                }
                            }
                            // Frame rendered but not yet available; return texture anyway
                            // (it holds the previous frame).
                            return pipTextureId
                        }
                        if (info.presentationTimeUs > targetTimeUs) {
                            // We've passed the target; use the current texture.
                            return pipTextureId
                        }
                    } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    }
                    if (inputEos && outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    }
                }
                return if (lastOverlayTimeUs >= 0) pipTextureId else 0
            } catch (e: Exception) {
                Log.w(TAG, "PiP texture update failed", e)
                return 0
            }
        }

        override fun release() {
            super.release()
            releaseDecoder()
            releaseImageTexture()
            // The GlProgram created in init{} is a separate GL object from the
            // parent BaseGlShaderProgram's program — it must be deleted here,
            // otherwise the shader program leaks on every export.
            try {
                glProgram.delete()
            } catch (_: Exception) {}
            if (pipTextureId != 0) {
                try {
                    GLES20.glDeleteTextures(1, intArrayOf(pipTextureId), 0)
                } catch (_: Exception) {}
                pipTextureId = 0
            }
        }

        private fun releaseDecoder() {
            decoderReady = false
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            decoder = null
            try { extractor?.release() } catch (_: Exception) {}
            extractor = null
            try { decoderSurface?.release() } catch (_: Exception) {}
            decoderSurface = null
            try { surfaceTexture?.release() } catch (_: Exception) {}
            surfaceTexture = null
        }
    }

    companion object {
        private const val TAG = "PipOverlayGlEffect"
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

        // Note: uPipSampler is an external OES texture. We use a separate
        // sampler; the OES extension is enabled via the texture target.
        // uPipAngle is clockwise-positive radians in screen space
        // (y-down), matching the preview's graphicsLayer rotationZ.
        private val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform samplerExternalOES uPipSampler;
            uniform sampler2D uPipImageSampler; // still-picture overlays
            uniform float uPipIsImage; // 1 = sample the 2D image texture
            uniform vec2 uPipCenter; // NDC
            uniform vec2 uPipSize;   // full width/height, NDC
            uniform vec2 uOutputSize; // pixels
            uniform float uPipAngle; // radians, clockwise-positive
            uniform float uPipOpacity;
            uniform float uPipEnabled;
            void main() {
                vec4 video = texture2D(uTexSampler, vTextureCoord);
                if (uPipEnabled < 0.5) {
                    gl_FragColor = video;
                    return;
                }
                // Work in pixel space (uniform x/y scale) so rotation is exact.
                vec2 ndc = vTextureCoord * 2.0 - 1.0;
                vec2 fragPx = vec2((ndc.x * 0.5 + 0.5) * uOutputSize.x,
                                   (0.5 - ndc.y * 0.5) * uOutputSize.y);
                vec2 centerPx = vec2((uPipCenter.x * 0.5 + 0.5) * uOutputSize.x,
                                     (0.5 - uPipCenter.y * 0.5) * uOutputSize.y);
                vec2 halfPx = uPipSize * 0.5 * uOutputSize;
                float c = cos(uPipAngle);
                float s = sin(uPipAngle);
                // Axis-aligned bounding box of the rotated rect.
                vec2 hb = vec2(abs(halfPx.x * c) + abs(halfPx.y * s),
                               abs(halfPx.x * s) + abs(halfPx.y * c));
                vec2 d = fragPx - centerPx;
                vec4 outColor = video;
                if (abs(d.x) <= hb.x && abs(d.y) <= hb.y) {
                    // Inverse-rotate into the overlay's local frame.
                    vec2 local = vec2(d.x * c + d.y * s, -d.x * s + d.y * c);
                    if (abs(local.x) <= halfPx.x && abs(local.y) <= halfPx.y) {
                        // Map to PiP texture coords (v = 1 at the rect's
                        // bottom edge): upright for both the OES video
                        // texture and the 2D bitmap texture.
                        vec2 pipUv = vec2(local.x / halfPx.x * 0.5 + 0.5,
                                          local.y / halfPx.y * 0.5 + 0.5);
                        vec4 pip = uPipIsImage > 0.5
                            ? texture2D(uPipImageSampler, pipUv)
                            : texture2D(uPipSampler, pipUv);
                        float a = uPipOpacity;
                        vec3 outRgb = pip.rgb * a + video.rgb * (1.0 - a);
                        outColor = vec4(outRgb, video.a);
                    }
                }
                gl_FragColor = outColor;
            }
        """.trimIndent()
    }
}

// Minimal GLES11Ext reference to avoid importing javax.microedition.
private object GLES11Ext {
    const val GL_TEXTURE_EXTERNAL_OES: Int = 0x8D65
}
