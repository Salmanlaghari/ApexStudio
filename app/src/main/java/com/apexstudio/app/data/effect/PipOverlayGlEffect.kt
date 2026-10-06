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
 * external OES texture) and alpha-composited over the main video in the
 * bottom-end corner — matching the preview's BottomEnd PiP box placement
 * (see EditorPreviewArea's PIP OVERLAY box).
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
    private val marginFraction: Float = 0.03f,
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
            marginFraction = marginFraction,
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
        private val marginFraction: Float,
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
        // Bottom-end corner: x in [1 - margin - width, 1 - margin], y in [-1 + margin, -1 + margin + height].
        private var pipRect = floatArrayOf(0f, 0f, 0f, 0f) // left, bottom, right, top (NDC)

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
                Log.w(TAG, "PiP decoder setup failed for $overlayUri, PiP disabled", e)
                releaseDecoder()
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
            // Compute PiP rect in NDC. Output is inputWidth x inputHeight.
            // PiP width = widthFraction of output width; height preserves 16:9-ish
            // (use the overlay's aspect if known, else 16:9).
            val outW = inputWidth.toFloat()
            val outH = inputHeight.toFloat()
            val pipW = outW * widthFraction.coerceIn(0.05f, 0.9f)
            // Assume 16:9 for the PiP box (matches preview's 130x75dp ~ 16:9).
            val pipH = pipW * 9f / 16f
            val marginX = outW * marginFraction
            val marginY = outH * marginFraction
            // NDC: x in [-1, 1] left-to-right, y in [-1, 1] bottom-to-top.
            val right = 1f - (marginX / outW) * 2f
            val left = right - (pipW / outW) * 2f
            val bottom = -1f + (marginY / outH) * 2f
            val top = bottom + (pipH / outH) * 2f
            pipRect = floatArrayOf(left, bottom, right, top)
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
                val overlayTimeMs = timeMs - offsetMs
                val overlayEndMs = if (trimEndMs == Long.MAX_VALUE) Long.MAX_VALUE else trimEndMs
                val visible = decoderReady &&
                    overlayTimeMs >= trimStartMs &&
                    overlayTimeMs < overlayEndMs &&
                    overlayTimeMs * 1000L < overlayDurationUs

                var pipTex = 0
                if (visible) {
                    pipTex = updateOverlayTexture(overlayTimeMs * 1000L)
                }

                if (pipTex != 0) {
                    // Composite: sample PiP texture with OES sampler, blend in corner.
                    glProgram.setSamplerTexIdUniform("uPipSampler", pipTex, 1)
                    glProgram.setFloatsUniform("uPipRect", pipRect)
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
        private val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            varying vec2 vTextureCoord;
            uniform sampler2D uTexSampler;
            uniform samplerExternalOES uPipSampler;
            uniform vec4 uPipRect; // left, bottom, right, top (NDC)
            uniform float uPipOpacity;
            uniform float uPipEnabled;
            void main() {
                vec4 video = texture2D(uTexSampler, vTextureCoord);
                if (uPipEnabled < 0.5) {
                    gl_FragColor = video;
                    return;
                }
                // Convert texture coord to NDC for rect test.
                vec2 ndc = vTextureCoord * 2.0 - 1.0;
                if (ndc.x >= uPipRect.x && ndc.x <= uPipRect.z &&
                    ndc.y >= uPipRect.y && ndc.y <= uPipRect.w) {
                    // Map NDC to PiP texture coords (flip Y for OES).
                    vec2 pipUv = vec2(
                        (ndc.x - uPipRect.x) / (uPipRect.z - uPipRect.x),
                        1.0 - (ndc.y - uPipRect.y) / (uPipRect.w - uPipRect.y)
                    );
                    vec4 pip = texture2D(uPipSampler, pipUv);
                    float a = uPipOpacity;
                    vec3 outRgb = pip.rgb * a + video.rgb * (1.0 - a);
                    gl_FragColor = vec4(outRgb, video.a);
                } else {
                    gl_FragColor = video;
                }
            }
        """.trimIndent()
    }
}

// Minimal GLES11Ext reference to avoid importing javax.microedition.
private object GLES11Ext {
    const val GL_TEXTURE_EXTERNAL_OES: Int = 0x8D65
}
