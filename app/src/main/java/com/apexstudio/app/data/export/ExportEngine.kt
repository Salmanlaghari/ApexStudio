package com.apexstudio.app.data.export

import android.content.Context
import android.media.MediaCodecInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.apexstudio.app.data.ar.ArFaceGlEffect
import com.apexstudio.app.data.ar.ArFilterCatalog
import com.apexstudio.app.data.audio.AudioTrackEnvelopeAudioProcessor
import com.apexstudio.app.data.audio.DenoiseAudioProcessor
import com.apexstudio.app.data.effect.TextOverlayGlEffect
import com.apexstudio.app.data.effect.VideoCropGlEffect
import com.apexstudio.app.data.effect.FaceCropGlEffect
import com.apexstudio.app.data.effect.AspectCropTransformation
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.filter.LutFilterGlEffect
import com.apexstudio.app.data.fx.FxGlEffect
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.data.gl.TransitionEngine
import com.apexstudio.app.data.gl.GlTransitionAdapterEffect
import com.apexstudio.app.data.gl.TransitionGlEffect
import com.apexstudio.app.data.template.TimelineTemplate
import com.apexstudio.app.data.template.TimelineTemplateManager
import com.apexstudio.app.domain.model.DenoiseStrength
import com.apexstudio.app.domain.model.TextOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File

/**
 * Production Video Export and Rendering Engine built with AndroidX Media3 Transformer.
 *
 * Core Engineering Highlights:
 * 1. Hardware-Accelerated Encoding:
 *    - Uses [DefaultEncoderFactory] configured with hardware H.264/AVC & H.265/HEVC profiles.
 *    - Adaptive bitrate configuration for 720p, 1080p, and 4K resolutions.
 * 2. High-Resolution 1080p & 4K OOM Prevention:
 *    - Streams uncompressed video frames directly across GPU surface textures (zero CPU byte buffering).
 *    - Prevents OutOfMemoryError (OOM) on large 4K files by delegating frame buffers to hardware decoders/encoders.
 * 3. Guaranteed Audio-Video Sync:
 *    - Employs Media3's [Effects.createExperimentalSpeedChangingEffect] linking audio resamplers and video timestamps.
 *    - Applies frame-accurate [MediaItem.ClippingConfiguration] so audio never drifts from video cuts.
 * 4. Background Processing & Thread Safety:
 *    - Runs fully on [Dispatchers.IO] using Kotlin Coroutines and emits real-time progress via [StateFlow].
 *    - Clean cancellation and lifecycle teardown to prevent black screens, frozen frames, or native leaks.
 */
internal data class OverlayAudioDurationSelection(
    val sources: List<ExportEngine.AudioExportSource>,
    val omittedCount: Int
)

/** Unknown/non-positive duration means no safe preflight cap is available. */
internal fun selectOverlayAudioWithinDuration(
    sources: List<ExportEngine.AudioExportSource>,
    videoDurationMs: Long?
): OverlayAudioDurationSelection {
    val eligible = if (videoDurationMs == null || videoDurationMs <= 0L) sources
    else sources.filter { it.timelinePositionMs < videoDurationMs }
    return OverlayAudioDurationSelection(eligible, sources.size - eligible.size)
}

/** Testable cancellable continuation boundary for a callback-driven Media3 render. */
internal suspend fun awaitCancellableAudioRender(
    start: (onCompleted: () -> Unit, onFailure: (Exception) -> Unit) -> (() -> Unit)
) = suspendCancellableCoroutine<Unit> { continuation ->
    val completed = AtomicBoolean(false)
    val cancelRequested = AtomicBoolean(false)
    val cancelRender = java.util.concurrent.atomic.AtomicReference<(() -> Unit)?>(null)
    val cancelInvoked = AtomicBoolean(false)
    fun cancelOnce() {
        val action = cancelRender.get() ?: return
        if (cancelInvoked.compareAndSet(false, true)) action()
    }
    continuation.invokeOnCancellation {
        if (completed.compareAndSet(false, true)) {
            cancelRequested.set(true)
            cancelOnce()
        }
    }
    if (!continuation.isActive) return@suspendCancellableCoroutine
    try {
        val cancel = start(
            {
                if (completed.compareAndSet(false, true)) continuation.resume(Unit)
            },
            { failure ->
                if (completed.compareAndSet(false, true)) {
                    continuation.resumeWith(Result.failure(failure))
                }
            }
        )
        cancelRender.set(cancel)
        if (cancelRequested.get()) cancelOnce()
    } catch (failure: Exception) {
        if (completed.compareAndSet(false, true)) continuation.resumeWith(Result.failure(failure))
    }
}

@UnstableApi
class ExportEngine(private val context: Context) {

    companion object {
        private const val TAG = "ExportEngine"

        /**
         * The curve fractions are defined across each source's retained trim,
         * and Media3 reports processor timestamps relative to the clipped item.
         * Keep those bounds together when constructing that item's processors.
         */
        internal fun audioConfigForSource(
            config: ExportConfig,
            source: AudioExportSource
        ): ExportConfig {
            val fallbackSpeed = config.clipSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
            val sourceSpeed = source.constantSpeedOverride
                ?.takeIf { it.isFinite() && it > 0f }
                ?: fallbackSpeed
            val baseVolume = config.volume.takeIf { it.isFinite() }?.coerceIn(0f, 2f) ?: 1f
            val sourceGain = source.sourceGain.takeIf { it.isFinite() }?.coerceIn(0f, 2f) ?: 1f
            return config.copy(
                trimStartMs = source.trimStartMs,
                trimEndMs = source.trimEndMs,
                clipSpeed = sourceSpeed,
                volume = (baseVolume * sourceGain).coerceIn(0f, 2f),
                speedPoints = source.speedPoints,
                denoiseEnabled = source.denoiseEnabled,
                denoiseStrength = source.denoiseStrength
            )
        }

        /** Fresh gain processor supporting the mono and stereo source formats. */
        internal fun volumeProcessorFor(
            volume: Float
        ): androidx.media3.common.audio.ChannelMixingAudioProcessor? {
            val gain = volume.takeIf { it.isFinite() }?.coerceIn(0f, 2f) ?: 1f
            if (gain == 1f) return null
            return androidx.media3.common.audio.ChannelMixingAudioProcessor().apply {
                putChannelMixingMatrix(
                    androidx.media3.common.audio.ChannelMixingMatrix(
                        1, 1, floatArrayOf(gain)
                    )
                )
                putChannelMixingMatrix(
                    androidx.media3.common.audio.ChannelMixingMatrix(
                        2, 2, floatArrayOf(gain, 0f, 0f, gain)
                    )
                )
            }
        }
    }

    private val _exportState = MutableStateFlow(ExportProgressState())
    val exportState: StateFlow<ExportProgressState> = _exportState

    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private var postMixJob: Job? = null
    private var overlayAudioTransformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    data class ExportProgressState(
        val isExporting: Boolean = false,
        val progress: Float = 0f,
        val outputUri: String? = null,
        val error: String? = null,
        val warning: String? = null
    )

    data class ExportConfig(
        val resolution: String = "1080p",
        val fps: Int = 60,
        val quality: String = "high",
        // Pro Phase 1: aspect ratio + bitrate controls
        val aspectRatio: String = "16:9", // "16:9" | "9:16" | "1:1"
        val bitrateMbps: Int = 0, // 0 = auto (resolution-based default)
        val filterPreset: FilterPreset? = null,
        val filterIntensity: Float = 1f,
        val adjustments: com.apexstudio.app.domain.model.VideoAdjustments = com.apexstudio.app.domain.model.VideoAdjustments(),
        // Preferred per-clip crop source. The legacy editor-state cropRect
        // remains a fallback for older callers/projects.
        val videoCrop: com.apexstudio.app.domain.model.VideoCropSettings? = null,
        val faceCropConfig: com.apexstudio.app.data.effect.FaceCropRenderConfig? = null,
        val cropRect: com.apexstudio.app.presentation.state.CropRect? = null,
        val clipSpeed: Float = 1f,
        // Variable speed curve (PR-E): when non-empty, the export
        // follows the curve instead of the constant [clipSpeed].
        // Points are fractions 0..1 across the TRIMMED clip span
        // (see SpeedCurveMath). Empty = constant [clipSpeed].
        val speedPoints: List<com.apexstudio.app.domain.model.SpeedPoint> = emptyList(),
        val keyframes: com.apexstudio.app.domain.model.KeyframeTrack =
            com.apexstudio.app.domain.model.KeyframeTrack(),
        val fxPreset: FxPreset? = null,
        val fxIntensity: Float = 1f,
        val fxSpeed: Float = 1f,
        // AR Face filter (previewed via ArFaceFilterOverlay): the graded
        // look is baked by ArFaceGlEffect; festival banner text is baked
        // as a caption overlay (see buildArGreetingOverlay).
        val arFilterId: String? = null,
        val arFilterIntensity: Float = 0f,
        val arFilterCustomText: String = "",
        val transitionType: TransitionEngine.Companion.TransitionType? = null,
        /**
         * Phase 4: gl-transitions shader id (without the `glpro_` prefix).
         * Takes precedence over [transitionType] when non-null.
         */
        val transitionGlId: String? = null,
        val transitionDurationMs: Long = 500L,
        val textOverlays: List<TextOverlay> = emptyList(),
        val stickers: List<com.apexstudio.app.domain.model.StickerOverlay> = emptyList(),
        val rotationAngle: Float = 0f,
        val isFlippedHorizontal: Boolean = false,
        val isFlippedVertical: Boolean = false,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L,
        val pitchSemitones: Float = 0f,
        val volume: Float = 1f,
        // Clean Voice (per-clip voice noise cleanup): when true, the
        // export runs the same DenoiseAudioProcessor the preview uses
        // (first in the audio chain, so it sees the original audio).
        // The engine is classical spectral gating, NOT RNNoise — see
        // SpectralDenoiser's KDoc for the honest reasoning.
        val denoiseEnabled: Boolean = false,
        val denoiseStrength: DenoiseStrength = DenoiseStrength.MEDIUM,
        val reverbEnabled: Boolean = false,
        val reverbPreset: Short = 0,
        val echoEnabled: Boolean = false,
        val bassBoostEnabled: Boolean = false,
        val bassBoostStrength: Short = 0,
        // Picture-in-Picture overlays: composited in the export to match preview.
        val pipOverlays: List<PipOverlayConfig> = emptyList(),
        // Shape mask (Mask tool) on the main clip. Null = no mask.
        val mask: com.apexstudio.app.domain.model.VideoMask? = null,
        // Timeline music: unmuted extra audio tracks are decoded and mixed over
        // the exported audio after the Transformer pass (see MusicExportMixer),
        // so added music is actually audible in the exported video. Empty = no-op.
        val musicMixTracks: List<com.apexstudio.app.domain.model.AudioTrack> = emptyList(),
        // Timeline audio overlays are prepared by Media3, then mixed into the completed video.
        val overlayAudioSources: List<AudioExportSource> = emptyList()
    )

    /**
     * Configuration for one Picture-in-Picture overlay in the export.
     * The overlay video is composited over the main video at [offsetMs] on the
     * export timeline, centred at ([centerX], [centerY]) — normalised 0..1,
     * x from the left, y from the top — with width [widthFraction] of the
     * output width, a 16:9 box, and [rotationDeg] clockwise rotation.
     *
     * These mirror MediaClip's pipX/pipY/pipScale/pipRotationDeg exactly, so
     * the export renders what the preview canvas showed (WYSIWYG). The old
     * fixed bottom-end corner equals centerX = 0.85, centerY = 0.85.
     */
    data class PipOverlayConfig(
        val uri: String,
        val offsetMs: Long = 0L,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = Long.MAX_VALUE,
        val speedPoints: List<com.apexstudio.app.domain.model.SpeedPoint> = emptyList(),
        val widthFraction: Float = 0.25f,
        val centerX: Float = 0.85f,
        val centerY: Float = 0.85f,
        val rotationDeg: Float = 0f,
        val opacity: Float = 1f,
        // Shape mask (Mask tool) on this overlay clip. Null = no mask.
        val mask: com.apexstudio.app.domain.model.VideoMask? = null,
        // Overlay properties (properties panel) + intrinsic aspect +
        // keyframes. The effect resolves keyframes per frame, so the
        // exported overlay animates exactly like the preview. Opacity
        // here is the BASE opacity — the effect multiplies the
        // keyframe-interpolated opacity per frame (it must NOT be
        // pre-multiplied by the keyframe at the overlay start, which
        // would freeze the animation at its first value).
        val aspect: Float = 16f / 9f,
        val blendMode: Int = 0,
        val borderWidth: Float = 0f,
        val borderColor: Int = 0xFFFFFFFF.toInt(),
        val shadow: Float = 0f,
        val flipH: Boolean = false,
        val flipV: Boolean = false,
        val keyframes: List<com.apexstudio.app.domain.model.Keyframe> = emptyList(),
        // Per-overlay source crop; affects only the PiP texture, not its canvas geometry.
        val videoCrop: com.apexstudio.app.domain.model.VideoCropSettings = com.apexstudio.app.domain.model.VideoCropSettings()
    )

    /**
     * One audio source for [startAudioExport], including its source trim and
     * placement on the standalone export timeline (both in ms).
     */
    data class AudioExportSource(
        val uri: String,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L,
        val timelinePositionMs: Long = 0L,
        // Variable speed curve for THIS source (PR-E). Empty = use the
        // source constant override, or config.clipSpeed for legacy callers.
        val speedPoints: List<com.apexstudio.app.domain.model.SpeedPoint> = emptyList(),
        // Constant speed for this source; null preserves legacy callers'
        // config.clipSpeed fallback. A valid per-source curve takes precedence.
        val constantSpeedOverride: Float? = null,
        // Per-source gain, composed with the export's global volume.
        val sourceGain: Float = 1f,
        // Clean Voice for THIS source (per-clip). Extra audio tracks
        // (music / voiceover) leave this off — the toggle is per
        // video clip, and music must never be noise-gated.
        val denoiseEnabled: Boolean = false,
        val denoiseStrength: DenoiseStrength = DenoiseStrength.MEDIUM,
        // Standalone tracks apply absolute scalar/keyframed volume and fades
        // after pitch/speed processing; do not also put their scalar in sourceGain.
        val trackVolume: Float? = null,
        val trackFadeInMs: Long = 0L,
        val trackFadeOutMs: Long = 0L,
        val trackVolumeKeyframes: List<com.apexstudio.app.domain.model.VolumeKeyframe> = emptyList()
    )

    /**
     * Initiates hardware-accelerated video export on a background Coroutine.
     */
    fun startExport(
        inputUri: String,
        config: ExportConfig,
        outputFileName: String = "apex_studio_export.mp4"
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _exportState.value = ExportProgressState(isExporting = true, progress = 0f)

                val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(inputUri))
                if (config.trimStartMs > 0L || (config.trimEndMs > 0L && config.trimEndMs > config.trimStartMs)) {
                    val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                    if (config.trimStartMs > 0L) {
                        clippingBuilder.setStartPositionMs(config.trimStartMs)
                    }
                    if (config.trimEndMs > config.trimStartMs) {
                        clippingBuilder.setEndPositionMs(config.trimEndMs)
                    }
                    mediaItemBuilder.setClippingConfiguration(clippingBuilder.build())
                }
                val inputMediaItem = mediaItemBuilder.build()

                val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
                outputDir.mkdirs()
                val outputFile = File(outputDir, outputFileName)

                val videoEffects = mutableListOf<androidx.media3.common.Effect>()
                // Crop before speed changes, on the clipped source clock. This avoids numerical
                // inverse-curve drift and never applies a clip's speed a second time to crop lookup.
                val speedPair = buildSpeedProvider(config)?.let { Effects.createExperimentalSpeedChangingEffect(it) }
                val activeFaceCrop = config.faceCropConfig
                if (activeFaceCrop != null) {
                    videoEffects.add(FaceCropGlEffect.forExport(activeFaceCrop.exportConfig()))
                }

                // 0. Pro Phase 1: aspect-ratio conversion (16:9 / 9:16 / 1:1).
                // Runs first on raw frames: center-crops to the target aspect
                // and scales to the target output size. Skipped when the
                // aspect is the default 16:9 (zero behavior change).
                // A face track already fixes the output aspect; do not center-crop it a second time.
                if (activeFaceCrop == null && (config.aspectRatio == "9:16" || config.aspectRatio == "1:1")) {
                    val (tw, th) = AspectCropTransformation.targetSizeFor(
                        config.resolution, config.aspectRatio
                    )
                    try {
                        videoEffects.add(AspectCropTransformation(tw, th))
                    } catch (e: Exception) {
                        Log.w(TAG, "Aspect-ratio transform failed", e)
                    }
                }

                // Picture-in-Picture overlays: composite each overlay clip as a true
                // PiP in the export, matching the preview's bottom-end corner placement.
                // Each overlay gets its own GlEffect instance (chained like text overlays).
                config.pipOverlays.forEach { pip ->
                    try {
                        videoEffects.add(
                            com.apexstudio.app.data.effect.PipOverlayGlEffect(
                                context = context,
                                overlayUri = pip.uri,
                                offsetMs = pip.offsetMs,
                                trimStartMs = pip.trimStartMs,
                                trimEndMs = pip.trimEndMs,
                                speedPoints = pip.speedPoints,
                                widthFraction = pip.widthFraction,
                                centerX = pip.centerX,
                                centerY = pip.centerY,
                                rotationDeg = pip.rotationDeg,
                                opacity = pip.opacity,
                                mask = pip.mask,
                                aspect = pip.aspect,
                                blendMode = pip.blendMode,
                                borderWidth = pip.borderWidth,
                                borderColor = pip.borderColor,
                                shadow = pip.shadow,
                                flipH = pip.flipH,
                                flipV = pip.flipV,
                                keyframes = pip.keyframes,
                                videoCrop = pip.videoCrop
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "PiP overlay effect creation failed for ${pip.uri}", e)
                    }
                }

                // Mono phone recordings and stereo sources must both obey gain/mute.
                // Use the same bounded gain policy as standalone AAC export.
                val audioProcessors = buildVideoExportAudioProcessors(config, speedPair?.first)

                // 1. Crop (clip-persisted setting takes precedence; old
                // editor-state cropRect callers remain supported).
                val cropEffect = if (activeFaceCrop == null) {
                    if (config.videoCrop != null) VideoCropGlEffect.fromSettings(config.videoCrop)
                    else config.cropRect?.let { r -> VideoCropGlEffect.fromRect(r.left, r.top, r.right, r.bottom) }
                } else null // Dynamic face crop takes precedence over all static crop sources.
                cropEffect?.let { videoEffects.add(it) }

                // 1.5 Rotation and Flipping
                if (config.rotationAngle != 0f || config.isFlippedHorizontal || config.isFlippedVertical) {
                    val scaleX = if (config.isFlippedHorizontal) -1f else 1f
                    val scaleY = if (config.isFlippedVertical) -1f else 1f
                    videoEffects.add(
                        ScaleAndRotateTransformation.Builder()
                            .setRotationDegrees(config.rotationAngle)
                            .setScale(scaleX, scaleY)
                            .build()
                    )
                }

                // 1.6 Shape mask (Mask tool): applied to the source frame
                // right after geometry, before any colour work, so the
                // mask matches what MaskPreviewOverlay showed in the
                // editor. Null/enabled-off = no-op.
                com.apexstudio.app.data.effect.MaskGlEffect.fromMask(config.mask)
                    ?.let { videoEffects.add(it) }

                // 2. Color Filter (3D LUT) & Video Adjustments
                if (config.filterPreset != null && config.filterIntensity > 0f) {
                    videoEffects.add(LutFilterGlEffect(context, config.filterPreset, config.filterIntensity))
                }
                if (!config.adjustments.isDefault) {
                    val adjustMatrix = com.apexstudio.app.data.filter.FilterColorMatrix.getCombinedMatrix(
                        filterId = null,
                        intensity = 0f,
                        adjustments = config.adjustments
                    )
                    videoEffects.add(com.apexstudio.app.data.filter.ColorMatrixGlEffect(adjustMatrix))
                }
                // Video Adjustments v2 (export): bake the non-matrix
                // adjustments into the exported MP4 via the GL shader.
                // Matrix-covered knobs are zeroed out so nothing
                // double-applies (ColorMatrix above handles those).
                run {
                    val v2 = config.adjustments
                    if (v2.hasNonMatrixAdjustments) {
                        videoEffects.add(
                            com.apexstudio.app.data.adjust.AdjustmentsGlEffect(
                                adjustments = v2.copy(
                                    hdr = 0f,
                                    brilliance = 0f,
                                    brightness = 0f,
                                    contrast = 1f,
                                    saturation = 1f,
                                    exposure = 0f,
                                    temperature = 0f,
                                    tint = 0f
                                )
                            )
                        )
                    }
                }

                // 3. Dynamic Visual Effects (Glitch, RGB Split, VHS)
                if (config.fxPreset != null && config.fxIntensity > 0f) {
                    videoEffects.add(FxGlEffect(config.fxPreset, config.fxIntensity, config.fxSpeed))
                }

                // 3.5 AR Face filter: bake the graded look of the active AR
                // Face card so the export matches the AR Face preview.
                if (config.arFilterId != null && config.arFilterIntensity > 0f &&
                    ArFaceGlEffect.isKnownFilter(config.arFilterId)
                ) {
                    videoEffects.add(ArFaceGlEffect(config.arFilterId, config.arFilterIntensity))
                }

                // 4. Transitions
                if (config.transitionDurationMs > 0L) {
                    val glId = config.transitionGlId
                    if (glId != null) {
                        // Phase 4: real gl-transitions GLSL shader.
                        videoEffects.add(
                            GlTransitionAdapterEffect(
                                glId = glId,
                                durationUs = config.transitionDurationMs * 1000L,
                                startUs = 0L
                            )
                        )
                    } else if (config.transitionType != null) {
                        videoEffects.add(
                            TransitionGlEffect(
                                transitionType = config.transitionType,
                                durationUs = config.transitionDurationMs * 1000L,
                                startUs = 0L
                            )
                        )
                    }
                }

                // 5. Speed Ramping with Audio/Video synchronization
                // Variable curve (PR-E): the SpeedProvider follows the
                // curve; constant speed is the no-curve fallback.
                // Audio is time-stretched with pitch preservation by
                // Media3's experimental speed-changing effect, so a
                // ramped clip's audio stays in sync and on-pitch —
                // no mute, no chipmunk effect.
                speedPair?.let {
                    // Its paired audio processor is already in the production chain.
                    videoEffects.add(it.second)
                }

                // 6. Keyframe Animation
                if (!config.keyframes.isEmpty()) {
                    videoEffects.add(
                        com.apexstudio.app.data.animation.KeyframeAnimationEffect(
                            trackProvider = { config.keyframes }
                        ).buildEffects().first()
                    )
                }

                // 6b. Per-clip saturation keyframes (CapCut "Color
                // Change"): the keyframed factor multiplies the global
                // saturation at every frame (saturation composes
                // exactly: sat(sat(c, g), f) == sat(c, g*f), so the
                // ColorMatrix stage above + this stage = global x
                // keyframed). Base = all-default adjustments so only
                // the keyframed saturation applies here. Same frame
                // time convention as KeyframeAnimationEffect above.
                if (config.keyframes.hasSaturationPins()) {
                    videoEffects.add(
                        com.apexstudio.app.data.adjust.AdjustmentsGlEffect(
                            adjustments = com.apexstudio.app.domain.model.VideoAdjustments(),
                            saturationAt = { frameTimeMs ->
                                config.keyframes.saturationFactorAt(frameTimeMs)
                            }
                        )
                    )
                }

                // 7. Caption / Text Overlays & Stickers
                val captionOverlays = config.textOverlays.filter { it.text.isNotBlank() } +
                    listOfNotNull(buildArGreetingOverlay(config))
                if (captionOverlays.isNotEmpty() || config.stickers.isNotEmpty()) {
                    val aspect = queryAspectRatio(inputUri)
                    videoEffects.addAll(buildCaptionExportEffects(captionOverlays, aspect))
                    config.stickers.forEach { sticker ->
                        if (sticker.isPngSticker()) {
                            // Bundled PNG sticker: rasterised with crop / cutout /
                            // rotation / opacity and composited as a GL sprite so
                            // the export matches the editor preview exactly.
                            videoEffects.add(
                                com.apexstudio.app.data.effect.StickerGlEffect(
                                    context, sticker, aspect
                                )
                            )
                        } else {
                            // Legacy emoji sticker: rendered as text. The size
                            // factor converts the sticker's sizeScale into a
                            // TextOverlay sizeScale so the export font size
                            // matches the preview's emoji font size exactly
                            // (see StickerSpriteRenderer.EMOJI_TEXT_SIZE_FACTOR).
                            val overlay = TextOverlay(
                                id = sticker.id,
                                text = sticker.symbolOrUri,
                                x = sticker.x,
                                y = sticker.y,
                                sizeScale = sticker.sizeScale *
                                    com.apexstudio.app.data.stickers.StickerSpriteRenderer.EMOJI_TEXT_SIZE_FACTOR,
                                startMs = sticker.startMs,
                                endMs = sticker.endMs
                            )
                            videoEffects.add(TextOverlayGlEffect(context, overlay, aspect))
                        }
                    }
                }

                val editedMediaItem = EditedMediaItem.Builder(inputMediaItem)
                    .setEffects(Effects(audioProcessors, videoEffects))
                    .build()

                // 8. Configure Hardware Encoder Factory based on target resolution
                //    (+ Pro Phase 1 custom bitrate override when set)
                startTransformerOnApplicationThread(
                    factory = {
                        buildHardwareTransformer(
                            config.resolution, outputFile, config.musicMixTracks,
                            overlayAudioSources = config.overlayAudioSources,
                            bitrateMbpsOverride = config.bitrateMbps
                        )
                    },
                    start = { it.start(editedMediaItem, outputFile.absolutePath) }
                )
            } catch (e: Exception) {
                Log.e(TAG, "startExport failed", e)
                mainHandler.post {
                    _exportState.value = ExportProgressState(
                        isExporting = false,
                        progress = 0f,
                        error = e.message ?: "Export failed"
                    )
                }
            }
        }
    }

    /** The actual video-export chain; the caller supplies the paired A/V speed processor. */
    internal fun buildVideoExportAudioProcessors(
        config: ExportConfig,
        speedProcessor: androidx.media3.common.audio.AudioProcessor? = null
    ): MutableList<androidx.media3.common.audio.AudioProcessor> {
        val processors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
        denoiseProcessorOrNull(config)?.let(processors::add)
        if (config.pitchSemitones != 0f) {
            val pitchFactor = Math.pow(2.0, (config.pitchSemitones / 12.0).toDouble()).toFloat()
            processors.add(androidx.media3.common.audio.SonicAudioProcessor().apply {
                setPitch(pitchFactor)
            })
        }
        volumeProcessorFor(config.volume)?.let(processors::add)
        speedProcessor?.let(processors::add)
        return processors
    }

    /**
     * Builds a fresh list of audio processors (pitch, volume, speed) from the
     * export config. Processors are stateful, so callers must build a new
     * list per media item instead of sharing instances.
     */
    internal fun buildExportAudioProcessors(
        config: ExportConfig,
        source: AudioExportSource? = null
    ): MutableList<androidx.media3.common.audio.AudioProcessor> {
        val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()

        // Clean Voice first: same DenoiseAudioProcessor the preview uses,
        // so it sees the original audio before pitch / volume / speed.
        denoiseProcessorOrNull(config)?.let { audioProcessors.add(it) }

        // Audio Pitch Adjustment via SonicAudioProcessor
        if (config.pitchSemitones != 0f) {
            val pitchFactor = Math.pow(2.0, (config.pitchSemitones / 12.0).toDouble()).toFloat()
            val sonic = androidx.media3.common.audio.SonicAudioProcessor().apply {
                setPitch(pitchFactor)
            }
            audioProcessors.add(sonic)
        }

        // Standalone tracks compose global gain into their final envelope
        // processor so boosted PCM is not clipped before track attenuation.
        // Other sources retain the shared mono/stereo gain processor.
        if (source?.trackVolume == null) {
            volumeProcessorFor(config.volume)?.let { audioProcessors.add(it) }
        }

        // Speed change affects audio too (constant or curve — same
        // provider builder as the video path, so the two can never
        // disagree about the clip's rate).
        buildSpeedProvider(config)?.let { provider ->
            val speedPair = Effects.createExperimentalSpeedChangingEffect(provider)
            audioProcessors.add(speedPair.first)
        }
        // This is intentionally last: timeline keyframes use the placed track's
        // absolute timeline clock and fades follow post-speed samples.
        if (source?.trackVolume != null) {
            val retainedStartMs = source.trimStartMs.coerceAtLeast(0L)
            val retainedEndMs = source.trimEndMs.coerceAtLeast(0L)
            val durationMs = if (retainedEndMs > retainedStartMs) retainedEndMs - retainedStartMs else 0L
            audioProcessors.add(
                AudioTrackEnvelopeAudioProcessor(
                    scalarVolume = source.trackVolume,
                    volumeKeyframes = source.trackVolumeKeyframes,
                    timelinePositionMs = source.timelinePositionMs,
                    fadeInMs = source.trackFadeInMs,
                    fadeOutMs = source.trackFadeOutMs,
                    retainedDurationMs = durationMs,
                    globalGain = config.volume
                )
            )
        }
        return audioProcessors
    }

    /**
     * Fresh Clean Voice processor for an export config, or null when
     * denoise is off. One instance per media item — processors are
     * stateful and must never be shared across items. Shared by both
     * export paths (single-clip [startExport] and [startAudioExport])
     * so they can never disagree about the denoise chain.
     */
    private fun denoiseProcessorOrNull(
        config: ExportConfig
    ): androidx.media3.common.audio.AudioProcessor? {
        if (!config.denoiseEnabled) return null
        return DenoiseAudioProcessor(config.denoiseStrength, enabled = true)
    }

    /**
     * Builds the Media3 [SpeedProvider] for an export, or null when no
     * speed change applies (constant 1x, no curve).
     *
     * Timestamp base: the effect receives presentation times relative
     * to the clipped item start (0-based), the same convention
     * KeyframeAnimationEffect already relies on — so the fraction is
     * timeUs / trimmed-span. [getNextSpeedChangeTimeUs] re-queries
     * every 10 ms of source time so the effect tracks the curve
     * instead of caching one rate for the whole clip.
     */
    private fun buildSpeedProvider(
        config: ExportConfig
    ): androidx.media3.common.audio.SpeedProvider? {
        val points = com.apexstudio.app.domain.model.SpeedCurveMath.sanitize(config.speedPoints)
        val hasCurve = points.size >= 2
        val constant = com.apexstudio.app.domain.model.SpeedCurveMath.sanitizeConstantSpeed(config.clipSpeed)
        if (!hasCurve && (constant <= 0f || constant == 1f)) return null
        // Persisted trim values can be extreme (or negative in legacy data).
        // Normalize the source clock and saturate instead of wrapping the
        // speed-curve span into a negative/short interval.
        val spanUs = ExportAudioTimelineMapping.sourceSpanUs(
            config.trimStartMs, config.trimEndMs
        ) ?: -1L
        if (hasCurve && spanUs <= 0L) {
            // Curve without a usable trim span (e.g. the photo path
            // passes 0..0): fall back to the constant rate rather
            // than guessing a mapping.
            Log.w(TAG, "Speed curve ignored: no valid trim span in export config")
            if (constant <= 0f || constant == 1f) return null
            return object : androidx.media3.common.audio.SpeedProvider {
                override fun getSpeed(timeUs: Long): Float = constant
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long =
                    androidx.media3.common.C.TIME_UNSET
            }
        }
        return object : androidx.media3.common.audio.SpeedProvider {
            override fun getSpeed(timeUs: Long): Float {
                if (!hasCurve) return constant
                val fraction = (timeUs.coerceIn(0L, spanUs).toDouble() / spanUs.toDouble())
                    .toFloat()
                return com.apexstudio.app.domain.model.SpeedCurveMath.speedAt(points, fraction)
            }

            override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
                if (!hasCurve) return androidx.media3.common.C.TIME_UNSET
                // Next 10 ms boundary (source time); stop querying once the
                // clipped item reaches the end of its curve span.
                return ExportAudioTimelineMapping.nextSpeedChangeTimeUs(timeUs, spanUs)
                    ?: androidx.media3.common.C.TIME_UNSET
            }
        }
    }

    /** One timed text batch rather than one full-frame GL effect per karaoke word. */
    internal fun buildCaptionExportEffects(
        overlays: List<TextOverlay>,
        aspectRatio: Float
    ): List<TextOverlayGlEffect> {
        val visibleText = overlays.filter { it.text.isNotBlank() }
        return if (visibleText.isEmpty()) emptyList()
        else listOf(TextOverlayGlEffect(context, visibleText, aspectRatio))
    }

    /** Build fresh, independently processed Media3 items for either standalone or overlay audio. */
    internal fun buildPlacedAudioItems(
        config: ExportConfig,
        sources: List<AudioExportSource>,
        overlay: Boolean = false
    ): List<PlacedAudioItem> = sources.map { src ->
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(src.uri))
        val retainedStartMs = src.trimStartMs.coerceAtLeast(0L)
        val retainedEndMs = src.trimEndMs.coerceAtLeast(0L)
        if (retainedStartMs > 0L || retainedEndMs > retainedStartMs) {
            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
            if (retainedStartMs > 0L) clippingBuilder.setStartPositionMs(retainedStartMs)
            if (retainedEndMs > retainedStartMs) clippingBuilder.setEndPositionMs(retainedEndMs)
            mediaItemBuilder.setClippingConfiguration(clippingBuilder.build())
        }
        // Overlay source gain is authoritative. Do not inherit the main V1 clip's
        // gain/mute, pitch or speed; each source's own speed curve and denoise remain.
        val itemBase = if (overlay) config.copy(
            clipSpeed = 1f, speedPoints = emptyList(), volume = 1f, pitchSemitones = 0f
        ) else config
        val itemConfig = audioConfigForSource(itemBase, src)
        val editedItem = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setRemoveVideo(true)
            .setEffects(Effects(buildExportAudioProcessors(itemConfig, src), emptyList()))
            .build()
        PlacedAudioItem(editedItem, src.timelinePositionMs)
    }

    /**
     * Exports the project's audio as a standalone AAC (.m4a) timeline mix.
     * Each source gets an independent sequence, with a leading timeline gap
     * for its placement; Media3 mixes the overlapping audio streams while
     * retaining each item's trim, speed, gain, pitch and denoise processors.
     */
    fun startAudioExport(
        sources: List<AudioExportSource>,
        config: ExportConfig,
        outputFileName: String = "apex_studio_audio.m4a"
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _exportState.value = ExportProgressState(isExporting = true, progress = 0f)
                if (sources.isEmpty()) {
                    throw IllegalArgumentException("No audio sources to export")
                }
                // OVERLAY does not retain the original video/image kind.
                // Probe containers so stills/silent videos cannot abort a mix.
                val selectedSources = selectAudioExportSources(sources) { uri ->
                    AudioExportSourceProbe.hasAudio(context, uri)
                }
                require(selectedSources.sources.isNotEmpty()) { "No readable audio sources to export" }

                val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
                outputDir.mkdirs()
                val outputFile = File(outputDir, outputFileName)

                val placedItems = buildPlacedAudioItems(config, selectedSources.sources)
                val composition = StandaloneAudioComposition.build(placedItems)

                startTransformerOnApplicationThread(factory = { Transformer.Builder(context)
                    .setLooper(Looper.getMainLooper())
                    .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            progressJob?.cancel()
                            mainHandler.post {
                                _exportState.value = ExportProgressState(
                                    isExporting = false,
                                    progress = 1f,
                                    outputUri = Uri.fromFile(outputFile).toString(),
                                    warning = selectedSources.warning
                                )
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            progressJob?.cancel()
                            mainHandler.post {
                                _exportState.value = ExportProgressState(
                                    isExporting = false,
                                    progress = 0f,
                                    error = exportException.message ?: "Audio export failed"
                                )
                            }
                        }
                    })
                    .build()
                }, start = { it.start(composition, outputFile.absolutePath) })
            } catch (e: Exception) {
                Log.e(TAG, "startAudioExport failed", e)
                mainHandler.post {
                    _exportState.value = ExportProgressState(
                        isExporting = false,
                        progress = 0f,
                        error = e.message ?: "Audio export failed"
                    )
                }
            }
        }
    }

    /**
     * Exports a multi-clip template timeline with transitions, filters, and audio tracks.
     */
    fun startTemplateExport(
        template: TimelineTemplate,
        outputFileName: String = "apex_template_export.mp4"
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _exportState.value = ExportProgressState(isExporting = true, progress = 0f)

                val templateManager = TimelineTemplateManager(context)
                val composition = templateManager.mapTemplateToComposition(template)

                val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
                outputDir.mkdirs()
                val outputFile = File(outputDir, outputFileName)

                startTransformerOnApplicationThread(
                    factory = { buildHardwareTransformer(template.resolution, outputFile) },
                    start = { it.start(composition, outputFile.absolutePath) }
                )
            } catch (e: Exception) {
                Log.e(TAG, "startTemplateExport failed", e)
                mainHandler.post {
                    _exportState.value = ExportProgressState(
                        isExporting = false,
                        progress = 0f,
                        error = e.message ?: "Template export failed"
                    )
                }
            }
        }
    }

    /** Render one shared standalone composition so overlay items retain timeline gaps/overlaps. */
    private suspend fun renderOverlayAudio(composition: Composition, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        // Transformer expects to own its destination path; the temp-file reservation
        // prevents collisions between exports, but must be removed before start.
        if (outputFile.exists() && !outputFile.delete()) throw IllegalStateException("Cannot prepare overlay audio output")
        withContext(Dispatchers.Main.immediate) {
            awaitCancellableAudioRender { onCompleted, onFailure ->
                var active: Transformer? = null
                try {
                    active = Transformer.Builder(context)
                        .setLooper(Looper.getMainLooper())
                        .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (overlayAudioTransformer === active) overlayAudioTransformer = null
                                onCompleted()
                            }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (overlayAudioTransformer === active) overlayAudioTransformer = null
                                onFailure(exportException)
                            }
                        }).build()
                    overlayAudioTransformer = active
                    active.start(composition, outputFile.absolutePath)
                    val renderer = requireNotNull(active)
                    val cancel: () -> Unit = {
                        // Media3 cancellation is main-looper confined; clean its partial target there too.
                        mainHandler.post {
                            try { renderer.cancel() } catch (_: Exception) {}
                            if (overlayAudioTransformer === renderer) overlayAudioTransformer = null
                            if (outputFile.exists()) outputFile.delete()
                        }
                    }
                    cancel
                } catch (failure: Exception) {
                    if (overlayAudioTransformer === active) overlayAudioTransformer = null
                    try { active?.cancel() } catch (_: Exception) {}
                    throw failure
                }
            }
        }
    }

    private fun mediaDurationMs(file: File): Long {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun publishPostMixState(owner: Job, state: ExportProgressState) {
        mainHandler.post {
            if (!owner.isCancelled && postMixJob === owner) _exportState.value = state
        }
    }

    private fun buildHardwareTransformer(
        resolution: String,
        outputFile: File,
        musicMixTracks: List<com.apexstudio.app.domain.model.AudioTrack> = emptyList(),
        overlayAudioSources: List<AudioExportSource> = emptyList(),
        bitrateMbpsOverride: Int = 0
    ): Transformer {
        // Pro Phase 1: explicit bitrate from the Export screen wins;
        // otherwise fall back to the resolution-based default.
        val targetBitrate = if (bitrateMbpsOverride > 0) {
            bitrateMbpsOverride * 1_000_000
        } else when (resolution.lowercase()) {
            "4k", "2160p" -> 50_000_000
            "720p" -> 6_000_000
            else -> 18_000_000 // 1080p
        }

        val encoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(targetBitrate)
            .setEncodingProfileLevel(
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                MediaCodecInfo.CodecProfileLevel.AVCLevel51
            )
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(encoderSettings)
            .setEnableFallback(true)
            .build()

        val transformerListener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                progressJob?.cancel()
                if (musicMixTracks.isEmpty() && overlayAudioSources.isEmpty()) {
                    mainHandler.post {
                        _exportState.value = ExportProgressState(
                            isExporting = false,
                            progress = 1f,
                            outputUri = Uri.fromFile(outputFile).toString()
                        )
                    }
                    return
                }
                mainHandler.post {
                    _exportState.value = ExportProgressState(isExporting = true, progress = 0.97f)
                }
                postMixJob?.cancel()
                postMixJob = CoroutineScope(Dispatchers.IO).launch {
                    val mixContext = currentCoroutineContext()
                    val owner = mixContext.job
                    val mixedFile = File(outputFile.parentFile, outputFile.nameWithoutExtension + "_music.mp4")
                    var preparedOverlay: File? = null
                    var overlayWarning: String? = null
                    try {
                        var overlayTrack: com.apexstudio.app.domain.model.AudioTrack? = null
                        if (overlayAudioSources.isNotEmpty()) {
                            try {
                                val probed = selectAudioExportSources(overlayAudioSources) { uri ->
                                    AudioExportSourceProbe.hasAudio(context, uri)
                                }
                                // Avoid asking Transformer to materialize arbitrarily long leading
                                // gaps for sources that cannot intersect the rendered video.
                                val durationCapMs = exportResult.durationMs.takeIf { it > 0L }
                                val durationSelection = selectOverlayAudioWithinDuration(
                                    probed.sources, durationCapMs
                                )
                                val inRangeSources = durationSelection.sources
                                val outOfRange = durationSelection.omittedCount
                                val rangeWarning = if (outOfRange > 0) {
                                    "$outOfRange overlay audio source(s) starting after the rendered video were omitted."
                                } else null
                                overlayWarning = listOfNotNull(probed.warning, rangeWarning).joinToString(" ").ifBlank { null }
                                if (inRangeSources.isEmpty()) {
                                    if (overlayWarning == null) overlayWarning = "Overlay audio could not be prepared; original video audio was retained."
                                } else {
                                    val items = buildPlacedAudioItems(ExportConfig(), inRangeSources, overlay = true)
                                    val composition = StandaloneAudioComposition.build(items)
                                    val outputDir = outputFile.parentFile ?: throw IllegalStateException("Missing export directory")
                                    val overlayTemp = File.createTempFile("apex_overlay_", ".m4a", outputDir)
                                    preparedOverlay = overlayTemp
                                    renderOverlayAudio(composition, overlayTemp)
                                    val durationMs = mediaDurationMs(overlayTemp)
                                    if (durationMs <= 0L) throw IllegalStateException("Prepared overlay audio has no readable duration")
                                    overlayTrack = com.apexstudio.app.domain.model.AudioTrack(
                                        id = "export_overlay_audio",
                                        name = "Overlay audio",
                                        uri = Uri.fromFile(overlayTemp).toString(),
                                        volume = 1f,
                                        trimEndMs = durationMs
                                    )
                                    // Keep source-probe and duration-cap warnings alongside the mix result.
                                }
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                Log.w(TAG, "Overlay audio preparation failed; retaining rendered video audio", failure)
                                overlayWarning = "Overlay audio could not be prepared; original video audio was retained."
                            }
                        }
                        val tracks = musicMixTracks + listOfNotNull(overlayTrack)
                        if (tracks.isEmpty()) {
                            publishPostMixState(owner, ExportProgressState(
                                    isExporting = false, progress = 1f,
                                    outputUri = Uri.fromFile(outputFile).toString(), warning = overlayWarning
                                ))
                            return@launch
                        }
                        mixContext.ensureActive()
                        val result = MusicExportMixer.mixMusicIntoVideo(context, outputFile, tracks, mixedFile) {
                            mixContext.ensureActive()
                        }
                        mixContext.ensureActive()
                        Log.i(TAG, "Audio mix done: ${result.mixedTracks} mixed, ${result.skippedTracks} skipped")
                        if (!outputFile.delete()) Log.w(TAG, "Could not delete intermediate export file")
                        val mixWarning = AudioMixExportNotice.forSkippedTracks(result.skippedTracks)
                        publishPostMixState(owner, ExportProgressState(
                                isExporting = false, progress = 1f,
                                outputUri = Uri.fromFile(mixedFile).toString(),
                                warning = listOfNotNull(overlayWarning, mixWarning).distinct().joinToString(" ").ifBlank { null }
                            ))
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        if (mixedFile.exists()) mixedFile.delete()
                        throw cancelled
                    } catch (e: Exception) {
                        Log.w(TAG, "Audio mix failed; keeping video without postmix audio", e)
                        if (mixedFile.exists() && !mixedFile.delete()) Log.w(TAG, "Could not delete partial mixed file")
                        val warning = listOfNotNull(overlayWarning, AudioMixExportNotice.MIX_FAILED).distinct().joinToString(" ")
                        publishPostMixState(owner, ExportProgressState(
                                isExporting = false, progress = 1f,
                                outputUri = Uri.fromFile(outputFile).toString(), warning = warning
                            ))
                    } finally {
                        preparedOverlay?.let { temp ->
                            if (temp.exists() && !temp.delete()) Log.w(TAG, "Could not delete prepared overlay audio")
                        }
                    }
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                progressJob?.cancel()
                mainHandler.post {
                    _exportState.value = ExportProgressState(
                        isExporting = false,
                        progress = 0f,
                        error = exportException.message ?: "Export failed"
                    )
                }
            }
        }

        return Transformer.Builder(context)
            .setLooper(Looper.getMainLooper())
            .setEncoderFactory(encoderFactory)
            .addListener(transformerListener)
            .build()
    }

    /** Media3 start/getProgress/cancel all require its configured application looper. */
    internal suspend fun startTransformerOnApplicationThread(
        factory: () -> Transformer,
        start: (Transformer) -> Unit
    ) = withContext(Dispatchers.Main.immediate) {
        val active = factory()
        transformer = active
        try {
            start(active)
            startProgressTracking(active)
        } catch (failure: Exception) {
            active.cancel()
            if (transformer === active) transformer = null
            throw failure
        }
    }

    private fun startProgressTracking(activeTransformer: Transformer) {
        progressJob?.cancel()
        val progressHolder = ProgressHolder()
        progressJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            while (_exportState.value.isExporting && this@ExportEngine.transformer === activeTransformer) {
                val pState = activeTransformer.getProgress(progressHolder)
                if (pState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val p = (progressHolder.progress / 100f).coerceIn(0f, 0.99f)
                    _exportState.value = _exportState.value.copy(progress = p)
                }
                delay(100)
            }
        }
    }

    /**
     * The AR Face festival banner text (e.g. "Happy Ganesh Chaturthi")
     * is previewed as a bottom banner by [ArFaceFilterOverlay]; bake it
     * into the export as a caption overlay so it survives, matching the
     * preview. Non-festival AR filters return null.
     */
    private fun buildArGreetingOverlay(config: ExportConfig): TextOverlay? {
        if (config.arFilterIntensity <= 0f) return null
        val preset = ArFilterCatalog.getFilterById(config.arFilterId) ?: return null
        if (!preset.hasEditableText) return null
        val greeting = config.arFilterCustomText.ifBlank { preset.defaultGreetingText }
        if (greeting.isBlank()) return null
        return TextOverlay(
            id = "ar_greeting_${preset.id}",
            text = "✨ $greeting ✨",
            x = 0.5f,
            y = 0.88f,
            sizeScale = 1f,
            colorArgb = 0xFFFFFDFAL,
            bgArgb = 0xE6C2410CL,
            isBold = true
        )
    }

    private fun queryAspectRatio(inputUri: String): Float = try {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(inputUri))
            val w = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0
            if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 16f / 9f
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    } catch (e: Exception) {
        Log.w(TAG, "queryAspectRatio failed for $inputUri", e)
        16f / 9f
    }

    fun cancelExport() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { cancelExport() }
            return
        }
        progressJob?.cancel()
        progressJob = null
        postMixJob?.cancel()
        postMixJob = null
        try { overlayAudioTransformer?.cancel() } catch (_: Exception) {}
        overlayAudioTransformer = null
        transformer?.cancel()
        transformer = null
        _exportState.value = ExportProgressState()
    }

    fun release() {
        cancelExport()
    }
}
