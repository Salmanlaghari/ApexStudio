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
import com.apexstudio.app.domain.model.croppedOverlayAspect

/**
 * Composites a video Picture-in-Picture overlay in the export.
 *
 * The overlay video is decoded with [MediaCodec] to a [SurfaceTexture] (as an
 * external OES texture) and alpha-composited over the main video at the
 * overlay's persisted transform — centre ([centerX], [centerY]) normalised
 * 0..1, width [widthFraction] of the output width in a box of aspect
 * [aspect] (the overlay's intrinsic media aspect, matching the preview's
 * PipOverlayCanvas), and [rotationDeg] clockwise — exactly matching the
 * preview.
 *
 * Still-image overlays (no video track in the URI) are decoded to a bitmap
 * and composited through a regular 2D texture for the same timeline window,
 * so picture overlays are no longer silently dropped from the export.
 *
 * [keyframes] (when non-empty) animate the overlay per frame: each
 * keyframe holds an ABSOLUTE overlay transform (translateX = centerX,
 * translateY = centerY, scale = widthFraction, rotationDeg, opacity),
 * interpolated at the frame's timeline time — the same numbers the
 * preview resolves. Blend / border / shadow / flip come from the
 * properties panel and are applied in the shader.
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
    private val speedPoints: List<com.apexstudio.app.domain.model.SpeedPoint> = emptyList(),
    private val widthFraction: Float = 0.25f,
    private val centerX: Float = 0.85f,
    private val centerY: Float = 0.85f,
    private val rotationDeg: Float = 0f,
    private val opacity: Float = 1f,
    private val mask: com.apexstudio.app.domain.model.VideoMask? = null,
    // Overlay properties (properties panel). Defaults preserve the old
    // behaviour for callers that do not pass them.
    private val aspect: Float = 16f / 9f,
    private val blendMode: Int = 0,
    private val borderWidth: Float = 0f,
    private val borderColor: Int = 0xFFFFFFFF.toInt(),
    private val shadow: Float = 0f,
    private val flipH: Boolean = false,
    private val flipV: Boolean = false,
    private val keyframes: List<com.apexstudio.app.domain.model.Keyframe> = emptyList(),
    private val videoCrop: com.apexstudio.app.domain.model.VideoCropSettings = com.apexstudio.app.domain.model.VideoCropSettings()
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return PipOverlayShaderProgram(
            appContext = context.applicationContext,
            overlayUri = overlayUri,
            offsetMs = offsetMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            speedPoints = speedPoints,
            widthFraction = widthFraction,
            centerX = centerX,
            centerY = centerY,
            rotationDeg = rotationDeg,
            opacity = opacity,
            mask = mask,
            aspect = croppedOverlayAspect(aspect, videoCrop),
            blendMode = blendMode,
            borderWidth = borderWidth,
            borderColor = borderColor,
            shadow = shadow,
            flipH = flipH,
            flipV = flipV,
            keyframes = keyframes,
            videoCrop = videoCrop.sanitized(),
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
        private val speedPoints: List<com.apexstudio.app.domain.model.SpeedPoint>,
        private val widthFraction: Float,
        private val centerX: Float,
        private val centerY: Float,
        private val rotationDeg: Float,
        private val opacity: Float,
        private val mask: com.apexstudio.app.domain.model.VideoMask?,
        private val aspect: Float,
        private val blendMode: Int,
        private val borderWidth: Float,
        private val borderColor: Int,
        private val shadow: Float,
        private val flipH: Boolean,
        private val flipV: Boolean,
        private val keyframes: List<com.apexstudio.app.domain.model.Keyframe>,
        private val videoCrop: com.apexstudio.app.domain.model.VideoCropSettings,
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
        private var lastRequestedTargetTimeUs: Long? = null
        private var pendingOutputIndex: Int = -1
        private var pendingOutputTimeUs: Long = -1L
        private var inputEos: Boolean = false
        private val sanitizedSpeedPoints =
            com.apexstudio.app.domain.model.SpeedCurveMath.sanitize(speedPoints)

        // PiP geometry in normalized device coordinates is computed
        // per frame in drawFrame() (computeGeometry) so keyframes can
        // animate it; configure() only records the output size.
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

            // Shape mask (Mask tool) on the overlay: static for the
            // life of this effect instance. Mask coords are normalised
            // to the overlay's own frame (top-left origin) — the same
            // space as pipUv below and the preview's PiP box.
            val m = mask
            val maskEnabled = m != null && m.enabled && m.opacity > 0.001f
            val shapeId = when (m?.shape) {
                com.apexstudio.app.domain.model.MaskShape.RECTANGLE -> 0f
                com.apexstudio.app.domain.model.MaskShape.LINEAR -> 2f
                else -> 1f
            }
            glProgram.setFloatsUniform("uMaskCenter", floatArrayOf(m?.centerX ?: 0.5f, m?.centerY ?: 0.5f))
            glProgram.setFloatsUniform("uMaskSize", floatArrayOf(m?.sizeX ?: 1f, m?.sizeY ?: 1f))
            glProgram.setFloatsUniform("uMaskFeather", floatArrayOf((m?.feather ?: 0f).coerceIn(0f, 1f)))
            glProgram.setFloatsUniform("uMaskInvert", floatArrayOf(if (m?.invert == true) 1f else 0f))
            glProgram.setFloatsUniform("uMaskOpacity", floatArrayOf((m?.opacity ?: 1f).coerceIn(0f, 1f)))
            glProgram.setFloatsUniform("uMaskShape", floatArrayOf(shapeId))
            glProgram.setFloatsUniform("uMaskEnabled", floatArrayOf(if (maskEnabled) 1f else 0f))

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
            val bitmap = OverlayImageDecoder.decode(appContext, overlayUri)
            val tex = IntArray(1)
            try {
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
                // Fail loudly on a bad upload: the caller degrades gracefully
                // (PiP disabled) instead of compositing an undefined texture.
                val glError = GLES20.glGetError()
                if (glError != GLES20.GL_NO_ERROR) {
                    throw IllegalStateException(
                        "PiP image texture upload failed for $overlayUri, glError=$glError"
                    )
                }
                imageTextureId = tex[0]
                hasImageOverlay = true
            } catch (failure: Exception) {
                if (tex[0] != 0) try { GLES20.glDeleteTextures(1, tex, 0) } catch (_: Exception) {}
                throw failure
            } finally {
                bitmap.recycle()
            }
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
                ext.seekTo(
                    OverlayFrameSelectionPolicy.millisecondsToMicroseconds(trimStartMs),
                    MediaExtractor.SEEK_TO_CLOSEST_SYNC
                )
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
            // the overlay's intrinsic aspect (overlayAspect), matching
            // the preview canvas box.
            val outW = inputWidth.toFloat()
            val outH = inputHeight.toFloat()
            pipOutputPx = floatArrayOf(outW, outH)
            return Size(inputWidth, inputHeight)
        }

        /** Overlay transform resolved for [timeMs] (timeline time). */
        private data class EffOverlay(
            val centerX: Float,
            val centerY: Float,
            val widthFraction: Float,
            val rotationDeg: Float,
            val opacity: Float
        )

        private fun effectiveOverlayTransformAt(timeMs: Long): EffOverlay {
            if (keyframes.isEmpty()) {
                return EffOverlay(centerX, centerY, widthFraction, rotationDeg, opacity)
            }
            val t = com.apexstudio.app.domain.model.KeyframeTrack(keyframes)
                .interpolateAt(timeMs)
            return EffOverlay(
                centerX = t.translateX,
                centerY = t.translateY,
                widthFraction = t.scale,
                rotationDeg = t.rotationDeg,
                opacity = t.opacity
            )
        }

        /**
         * PiP geometry (centre + size, NDC) for a given normalised
         * transform. Used by configure() for the static case and by
         * drawFrame() per frame when keyframes animate the overlay.
         */
        private fun computeGeometry(
            cxNorm: Float,
            cyNorm: Float,
            widthFrac: Float,
            outW: Float,
            outH: Float
        ): Pair<FloatArray, FloatArray> {
            val pipW = outW * widthFrac.coerceIn(0.05f, 0.9f)
            val pipH = pipW / aspect.coerceIn(0.1f, 10f)
            // NDC: x in [-1, 1] left-to-right, y in [-1, 1] bottom-to-top.
            // centre x/y are normalised 0..1 from the left / top.
            val cx = (cxNorm.coerceIn(0f, 1f) * 2f) - 1f
            val cy = 1f - (cyNorm.coerceIn(0f, 1f) * 2f)
            return floatArrayOf(cx, cy) to
                floatArrayOf((pipW / outW) * 2f, (pipH / outH) * 2f)
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
                val sourceSpanMs = when {
                    trimEndMs > trimStartMs && trimEndMs != Long.MAX_VALUE -> trimEndMs - trimStartMs
                    trimEndMs == Long.MAX_VALUE && overlayDurationUs != Long.MAX_VALUE ->
                        (overlayDurationUs / 1000L - trimStartMs).coerceAtLeast(0L)
                    else -> 0L
                }
                val hasSpeedRamp = sanitizedSpeedPoints.size >= 2
                val trimmedDurationMs = when {
                    hasSpeedRamp -> com.apexstudio.app.domain.model.SpeedCurveMath
                        .effectiveDurationMs(sourceSpanMs, sanitizedSpeedPoints)
                    trimEndMs == Long.MAX_VALUE -> Long.MAX_VALUE
                    else -> sourceSpanMs
                }
                // Source-media timestamp of the frame to show. The decoder was
                // seeked to trimStartMs in setupDecoder(), so decoded frames carry
                // source-media timestamps — the trim offset must be added back.
                val sourceOffsetMs = if (hasSpeedRamp) {
                    com.apexstudio.app.domain.model.SpeedCurveMath.outputToSourceMs(
                        overlayTimeMs, sourceSpanMs, sanitizedSpeedPoints
                    )
                } else overlayTimeMs
                val sourceTimeUs = OverlayFrameSelectionPolicy.sourceTimeUs(sourceOffsetMs, trimStartMs)
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
                    // Effective transform this frame: keyframes (when
                    // present) animate position / scale / rotation /
                    // opacity; otherwise the static pip values.
                    val eff = effectiveOverlayTransformAt(timeMs)
                    val (center, size) = computeGeometry(
                        eff.centerX, eff.centerY, eff.widthFraction,
                        pipOutputPx[0], pipOutputPx[1]
                    )
                    glProgram.setFloatsUniform("uPipCenter", center)
                    glProgram.setFloatsUniform("uPipSize", size)
                    glProgram.setFloatsUniform("uOutputSize", pipOutputPx)
                    glProgram.setFloatsUniform(
                        "uPipAngle",
                        floatArrayOf(Math.toRadians(eff.rotationDeg.toDouble()).toFloat())
                    )
                    glProgram.setFloatsUniform("uPipIsImage", floatArrayOf(pipIsImage))
                    glProgram.setFloatsUniform("uPipOpacity", floatArrayOf(eff.opacity.coerceIn(0f, 1f)))
                    glProgram.setFloatsUniform("uPipEnabled", floatArrayOf(1f))
                    // Properties-panel uniforms (static per effect).
                    glProgram.setFloatsUniform("uPipBlend", floatArrayOf(blendMode.toFloat()))
                    glProgram.setFloatsUniform("uPipBorderWidth", floatArrayOf(borderWidth.coerceIn(0f, 0.05f)))
                    glProgram.setFloatsUniform(
                        "uPipBorderColor",
                        floatArrayOf(
                            android.graphics.Color.red(borderColor) / 255f,
                            android.graphics.Color.green(borderColor) / 255f,
                            android.graphics.Color.blue(borderColor) / 255f
                        )
                    )
                    glProgram.setFloatsUniform("uPipShadow", floatArrayOf(shadow.coerceIn(0f, 1f)))
                    glProgram.setFloatsUniform("uPipFlipH", floatArrayOf(if (flipH) 1f else 0f))
                    glProgram.setFloatsUniform("uPipFlipV", floatArrayOf(if (flipV) 1f else 0f))
                    glProgram.setFloatsUniform("uBoxAspect", floatArrayOf(aspect.coerceIn(0.1f, 10f)))
                    // Source rectangle only; the PiP's center/scale/rotation remain its own geometry.
                    glProgram.setFloatsUniform("uPipCrop", floatArrayOf(
                        videoCrop.left, 1f - videoCrop.bottom,
                        videoCrop.right, 1f - videoCrop.top
                    ))
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
                // Seek only when the requested source time moves backwards. A
                // decoded frame may legitimately be slightly ahead of a slow
                // timeline target and must not cause repeated flushes.
                if (OverlayFrameSelectionPolicy.mustSeek(targetTimeUs, lastRequestedTargetTimeUs)) {
                    if (pendingOutputIndex >= 0) {
                        dec.releaseOutputBuffer(pendingOutputIndex, false)
                        pendingOutputIndex = -1
                        pendingOutputTimeUs = -1L
                    }
                    ext.seekTo(targetTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    dec.flush()
                    inputEos = false
                    lastOverlayTimeUs = -1L
                    lastRequestedTargetTimeUs = null
                    synchronized(this) { frameAvailable = false }
                }
                lastRequestedTargetTimeUs = targetTimeUs

                // A previously rendered frame in the tolerance window can be
                // reused until the target passes it; its PTS is not a seek cursor.
                if (OverlayFrameSelectionPolicy.canReuseFrame(
                        targetTimeUs, lastOverlayTimeUs.takeIf { it >= 0L }, lastOverlayTimeUs >= 0L
                    )
                ) {
                    updateSurfaceTextureIfAvailable()
                    return pipTextureId
                }

                // Keep each output-frame request bounded. Short dequeue waits
                // avoid stalling the export thread while still allowing the
                // asynchronous decoder a few opportunities to produce output.
                val timeoutUs = 1_000L
                var attempts = 0
                // Feed input and drain through every eligible frame. Stop at
                // (and retain) the first frame beyond the target tolerance so a
                // later slow-motion request can consume that exact frame.
                while (attempts < 30) {
                    attempts++
                    if (pendingOutputIndex >= 0) {
                        if (OverlayFrameSelectionPolicy.actionForOutput(
                                pendingOutputTimeUs, targetTimeUs
                            ) == OverlayFrameSelectionPolicy.OutputAction.HOLD_AND_STOP
                        ) {
                            break
                        }
                        dec.releaseOutputBuffer(pendingOutputIndex, true)
                        lastOverlayTimeUs = pendingOutputTimeUs
                        pendingOutputIndex = -1
                        pendingOutputTimeUs = -1L
                        updateSurfaceTextureIfAvailable()
                        continue
                    }

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
                        if (OverlayFrameSelectionPolicy.actionForOutput(
                                info.presentationTimeUs, targetTimeUs
                        ) == OverlayFrameSelectionPolicy.OutputAction.RENDER_AND_CONTINUE
                        ) {
                            dec.releaseOutputBuffer(outIndex, true)
                            lastOverlayTimeUs = info.presentationTimeUs
                            updateSurfaceTextureIfAvailable()
                        } else {
                            pendingOutputIndex = outIndex
                            pendingOutputTimeUs = info.presentationTimeUs
                            break
                        }
                    } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    }
                    if (inputEos && outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    }
                }
                updateSurfaceTextureIfAvailable()
                return if (lastOverlayTimeUs >= 0) pipTextureId else 0
            } catch (e: Exception) {
                Log.w(TAG, "PiP texture update failed", e)
                return 0
            }
        }

        private fun updateSurfaceTextureIfAvailable() {
            synchronized(this) {
                if (frameAvailable) {
                    frameAvailable = false
                    surfaceTexture?.updateTexImage()
                }
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
            if (pendingOutputIndex >= 0) {
                try { decoder?.releaseOutputBuffer(pendingOutputIndex, false) } catch (_: Exception) {}
                pendingOutputIndex = -1
                pendingOutputTimeUs = -1L
            }
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
            uniform float uPipBlend;       // 0 normal, 1 screen, 2 multiply, 3 overlay
            uniform float uPipBorderWidth; // fraction of box width, 0 = no border
            uniform vec3 uPipBorderColor;
            uniform float uPipShadow;      // 0..1 drop-shadow strength
            uniform float uPipFlipH;       // 0 or 1
            uniform float uPipFlipV;       // 0 or 1
            uniform float uBoxAspect;      // overlay box width / height
            uniform vec4 uPipCrop;         // source UV rectangle (left,bottom,right,top)
            uniform vec2 uMaskCenter;   // overlay-frame normalised, top-left origin
            uniform vec2 uMaskSize;     // full width/height, 0..1
            uniform float uMaskFeather; // 0..1
            uniform float uMaskInvert;  // 0 or 1
            uniform float uMaskOpacity; // 0..1
            uniform float uMaskShape;   // 0 = rect, 1 = ellipse, 2 = linear
            uniform float uMaskEnabled; // 0 or 1

            // Shape-mask factor for the overlay's own frame (pipUv space,
            // top-left origin). Distances are aspect-corrected with the
            // overlay's own box aspect (uBoxAspect) — the same geometry
            // the preview canvas uses. LINEAR is a vertical wipe: the line
            // sits at uMaskCenter.x, the left side is kept, uMaskSize.x
            // sets the transition width.
            float pipMaskFactor(vec2 uv) {
                if (uMaskEnabled < 0.5) return 1.0;
                float aspect = uBoxAspect;
                float m;
                if (uMaskShape < 1.5) {
                    vec2 q = (uv - uMaskCenter) * vec2(aspect, 1.0);
                    vec2 half_ = uMaskSize * 0.5 * vec2(aspect, 1.0);
                    float dist;
                    if (uMaskShape < 0.5) {
                        vec2 d = abs(q) - half_;
                        dist = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
                    } else {
                        vec2 h = max(half_, vec2(1e-4));
                        dist = (length(q / h) - 1.0) * min(h.x, h.y);
                    }
                    float featherW = max(uMaskFeather * 0.3, 1e-4);
                    m = 1.0 - smoothstep(-featherW, 0.0, dist);
                } else {
                    float fw = max(uMaskSize.x * 0.5, 1e-4);
                    float d = (uv.x - uMaskCenter.x) / fw;
                    float featherW = max(uMaskFeather, 1e-4);
                    m = 1.0 - smoothstep(-featherW, 0.0, d);
                }
                if (uMaskInvert > 0.5) m = 1.0 - m;
                return m * uMaskOpacity;
            }

            // Per-channel Overlay blend (base = video, blend = overlay).
            vec3 blendOverlay(vec3 base, vec3 blend) {
                vec3 r1 = 2.0 * base * blend;
                vec3 r2 = 1.0 - 2.0 * (1.0 - base) * (1.0 - blend);
                return mix(r2, r1, step(base, vec3(0.5)));
            }

            // Soft rectangular shadow factor for a point in the box's
            // local frame (half-extents half_). The shadow rect is the
            // box shifted by `shift` (local coords) with a feathered
            // edge; 1 inside, fading to 0 outside.
            float shadowBox(vec2 p, vec2 half_, vec2 shift) {
                vec2 d = abs(p - shift) - half_;
                float dist = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
                return 1.0 - smoothstep(-half_.x * 0.25, 0.0, dist);
            }

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
                // Axis-aligned bounding box of the rotated rect, expanded
                // for the drop shadow (shifted + feathered, up to ~1.4x).
                vec2 hb = vec2(abs(halfPx.x * c) + abs(halfPx.y * s),
                               abs(halfPx.x * s) + abs(halfPx.y * c));
                vec2 d = fragPx - centerPx;
                vec4 outColor = video;
                if (abs(d.x) <= hb.x * 1.4 && abs(d.y) <= hb.y * 1.4) {
                    // Inverse-rotate into the overlay's local frame.
                    vec2 local = vec2(d.x * c + d.y * s, -d.x * s + d.y * c);
                    // Drop shadow UNDER the overlay (same local frame as
                    // the preview's blurred rect: shifted down-right).
                    if (uPipShadow > 0.001) {
                        vec2 shift = vec2(halfPx.x * 0.08, halfPx.y * 0.10);
                        float sh = shadowBox(local, halfPx, shift);
                        outColor = vec4(mix(outColor.rgb, vec3(0.0),
                                            uPipShadow * 0.45 * sh), video.a);
                    }
                    if (abs(local.x) <= halfPx.x && abs(local.y) <= halfPx.y) {
                        // Map to PiP texture coords (v = 1 at the rect's
                        // bottom edge): upright for both the OES video
                        // texture and the 2D bitmap texture.
                        vec2 pipUv = vec2(local.x / halfPx.x * 0.5 + 0.5,
                                          local.y / halfPx.y * 0.5 + 0.5);
                        // Mirror (properties-panel flip), before sampling
                        // so the texture itself flips, not the box.
                        if (uPipFlipH > 0.5) pipUv.x = 1.0 - pipUv.x;
                        if (uPipFlipV > 0.5) pipUv.y = 1.0 - pipUv.y;
                        pipUv = mix(uPipCrop.xy, uPipCrop.zw, pipUv);
                        vec4 pip = uPipIsImage > 0.5
                            ? texture2D(uPipImageSampler, pipUv)
                            : texture2D(uPipSampler, pipUv);
                        // Border (properties panel): an opaque frame
                        // inside the box edges. The same physical width
                        // on all sides: bw in uv-x, bw * aspect in uv-y.
                        if (uPipBorderWidth > 0.0) {
                            float bw = uPipBorderWidth;
                            vec2 edgeDist = min(pipUv, 1.0 - pipUv);
                            float borderMask = clamp(
                                step(edgeDist.x, bw) +
                                step(edgeDist.y, bw * uBoxAspect),
                                0.0, 1.0);
                            pip = vec4(mix(pip.rgb, uPipBorderColor, borderMask),
                                       max(pip.a, borderMask));
                        }
                        float a = uPipOpacity * pipMaskFactor(pipUv) * pip.a;
                        vec3 blended;
                        if (uPipBlend < 0.5) {
                            blended = pip.rgb;
                        } else if (uPipBlend < 1.5) {
                            blended = 1.0 - (1.0 - pip.rgb) * (1.0 - video.rgb);
                        } else if (uPipBlend < 2.5) {
                            blended = pip.rgb * video.rgb;
                        } else {
                            blended = blendOverlay(video.rgb, pip.rgb);
                        }
                        vec3 outRgb = blended * a + video.rgb * (1.0 - a);
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
