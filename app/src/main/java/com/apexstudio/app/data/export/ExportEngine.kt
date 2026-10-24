package com.apexstudio.app.data.export

import android.content.Context
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
import androidx.compose.ui.graphics.toArgb
import com.apexstudio.app.data.ar.ArFaceGlEffect
import com.apexstudio.app.data.ar.ArFilterCatalog
import com.apexstudio.app.data.audio.AudioTrackEnvelopeAudioProcessor
import com.apexstudio.app.data.audio.DenoiseAudioProcessor
import com.apexstudio.app.data.audio.SharedVideoClipAudioProcessor
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
import com.apexstudio.app.data.color.ColorStudioGrade
import com.apexstudio.app.data.color.toLutTexture
import com.apexstudio.app.domain.model.DenoiseStrength
import com.apexstudio.app.domain.model.ChromaKeySettings
import com.apexstudio.app.domain.model.hasValidParameters as hasValidChromaParameters
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.resolveMaskAt
import com.apexstudio.app.domain.model.Signature
import com.apexstudio.app.domain.model.SignatureCorner
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.VideoDrawingLayer
import com.apexstudio.app.domain.model.hasValidParameters
import com.apexstudio.app.domain.model.timeline.animationKeyframeTimes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
    /** Work before Transformer.start(), so Cancel also stops slow preflight/setup. */
    private var exportPreparationJob: Job? = null
    private var postMixJob: Job? = null
    private var overlayAudioTransformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val projectRunLock = Any()
    private var projectRun: ProjectExportRun? = null
    private var projectExportJob: Job? = null

    /** Invalidates queued callbacks/errors from a cancelled or replaced export. */
    @Volatile private var nonProjectRun: Any? = null

    private data class ProjectExportRun(
        val cancelled: AtomicBoolean = AtomicBoolean(false),
        val lastProgress: java.util.concurrent.atomic.AtomicReference<Float> =
            java.util.concurrent.atomic.AtomicReference(0f)
    )

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
        /** Explicit photo canvas is already baked into the source frames. */
        val preserveInputAspectRatio: Boolean = false,
        /** Selected main-video placement inside the fixed output composition frame. */
        val videoFitMode: com.apexstudio.app.domain.model.VideoFitMode =
            com.apexstudio.app.domain.model.VideoFitMode.COVER,
        val bitrateMbps: Int = 0, // 0 = auto (resolution-based default)
        val filterPreset: FilterPreset? = null,
        val filterIntensity: Float = 1f,
        val adjustments: com.apexstudio.app.domain.model.VideoAdjustments = com.apexstudio.app.domain.model.VideoAdjustments(),
        /** Fixed non-AI effect; resolved by the same catalog/shader as the video decoder preview. */
        val projectVideoEffectId: String? = null,
        /** Ranged, absolute-source-time visual effects for selected-clip export. */
        val workspaceEffects: List<com.apexstudio.app.domain.model.WorkspaceEffect> = emptyList(),
        // Preferred per-clip crop source. The legacy editor-state cropRect
        // remains a fallback for older callers/projects.
        val videoCrop: com.apexstudio.app.domain.model.VideoCropSettings? = null,
        /** Per-clip chroma key pass; null/disabled preserves the source. */
        val chromaKeySettings: ChromaKeySettings? = null,
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
        /** Project branding is appended after every other visual composition layer. */
        val signature: Signature? = null,
        /** Active artwork layers for selected-clip export; callers filter to global + selected clip. */
        val videoDrawings: List<VideoDrawingLayer> = emptyList(),
        /** Source clip id used by VideoDrawingRenderer to include matching clip-specific layers. */
        val videoDrawingClipId: String? = null,
        val stickers: List<com.apexstudio.app.domain.model.StickerOverlay> = emptyList(),
        val rotationAngle: Float = 0f,
        val isFlippedHorizontal: Boolean = false,
        val isFlippedVertical: Boolean = false,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L,
        val pitchSemitones: Float = 0f,
        val volume: Float = 1f,
        /** Global mixer gain, separate from clip volume when typed audio is animated. */
        val globalAudioVolume: Float? = null,
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
        val overlayAudioSources: List<AudioExportSource> = emptyList(),
        /** Project-global start of this clip; keyframes are stored in this clock. */
        val keyframeTimelineStartMs: Long = 0L,
        /** Canonical typed keyframe source for a selected clip export. */
        val sharedVideoClip: com.apexstudio.app.domain.model.timeline.VideoClip? = null,
        /** Canonical typed video clips used by whole-project export, keyed by clip id. */
        val sharedVideoClips: Map<String, com.apexstudio.app.domain.model.timeline.VideoClip> = emptyMap(),
        /** Optional Color Studio project grade. Applied to every eligible composed main-track clip. */
        val colorStudioGrade: ColorStudioGrade? = null
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
     * Exports a deliberately limited, sequential V1 video-only project.
     * Unsupported content is rejected before a Transformer is created; clips
     * are never filtered out or silently rendered without their edits.
     */
    fun startProjectExport(
        clips: List<MediaClip>,
        config: ExportConfig,
        outputFileName: String = "apex_studio_project_export.mp4"
    ) {
        val run = ProjectExportRun()
        synchronized(projectRunLock) {
            if (projectRun != null || _exportState.value.isExporting || transformer != null ||
                overlayAudioTransformer != null || postMixJob?.isActive == true
            ) {
                Log.w(TAG, "Project export refused because another export is active")
                return
            }
            val reason = projectCompositionUnsupportedReason(clips, config)
            if (reason != null) {
                _exportState.value = ExportProgressState(error = reason)
                return
            }
            projectRun = run
            _exportState.value = ExportProgressState(isExporting = true, progress = 0f)
            projectExportJob = CoroutineScope(Dispatchers.IO).launch {
                var partialFile: File? = null
                var mixedFile: File? = null
                val diagnostics = ExportDiagnosticTrace(emit = { Log.i(TAG, it) })
                try {
                    diagnostics.stage("project_preparation_started", "clips=${clips.size}")
                    publishProjectProgress(run, 0.01f)
                    val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
                    if (!outputDir.exists() && !outputDir.mkdirs()) {
                        throw IllegalStateException("Cannot create project export directory")
                    }
                    val destination = File(outputDir, outputFileName).canonicalFile
                    if (destination.parentFile != outputDir.canonicalFile) {
                        throw IllegalArgumentException("Project export filename must not contain a path")
                    }
                    val partial = File.createTempFile("apex_project_", ".mp4", outputDir)
                    partialFile = partial
                    if (!partial.delete()) throw IllegalStateException("Cannot prepare project export output")

                    val plan = ProjectCompositionTimeline.build(clips)
                    if (plan.entries.size != clips.size || plan.durationMs <= 0L) {
                        throw IllegalStateException("Project composition could not include every video clip")
                    }
                    val (outputWidth, outputHeight) =
                        com.apexstudio.app.data.effect.AspectCropTransformation.targetSizeFor(
                            config.resolution, config.aspectRatio
                        )
                    val items = plan.entries.map { entry ->
                        val clip = entry.clip
                        val itemConfig = config.copy(
                            trimStartMs = entry.sourceStartMs,
                            trimEndMs = entry.sourceEndMs,
                            keyframeTimelineStartMs = entry.timelineStartMs,
                            clipSpeed = clip.speedMultiplier,
                            speedPoints = clip.speedPoints,
                            keyframes = clip.keyframes,
                            sharedVideoClip = config.sharedVideoClips[clip.id],
                            volume = (config.volume * clip.volume).coerceIn(0f, 2f),
                            denoiseEnabled = config.denoiseEnabled || clip.denoiseEnabled,
                            denoiseStrength = if (clip.denoiseEnabled) clip.denoiseStrength else config.denoiseStrength
                        )
                        val speedPair = buildSpeedProvider(itemConfig)?.let {
                            Effects.createExperimentalSpeedChangingEffect(it)
                        }
                        val keyframeClock = keyframeClockFor(itemConfig)
                        val sharedAnimation = itemConfig.sharedVideoClip
                        val videoEffects = buildList<androidx.media3.common.Effect> {
                            clip.videoCrop.takeUnless { it.isFullFrame }
                                ?.let { com.apexstudio.app.data.effect.VideoCropGlEffect.fromSettings(it) }
                                ?.let { add(it) }
                            com.apexstudio.app.data.effect.ChromaKeyGlEffect.fromSettings(clip.chromaKeySettings)
                                ?.let { add(it) }
                            com.apexstudio.app.data.template.ProjectVideoPresets.colorEffect(clip.projectVideoEffectId)
                                ?.let { add(it) }
                            addAll(com.apexstudio.app.data.effect.WorkspaceEffectGlEffect.createEffects(
                                clip.workspaceEffects,
                                entry.sourceStartMs,
                                sharedAnimation?.takeIf { it.effectIntensityKeyframes.isNotEmpty() }?.let { typed ->
                                    { compositionTimeUs ->
                                        val projectMs = keyframeClock.projectTimeMs(
                                            com.apexstudio.app.data.animation.itemLocalPresentationTimeUs(
                                                compositionTimeUs, entry.timelineStartMs
                                            )
                                        )
                                        typed.effectIntensityAt(projectMs)
                                    }
                                }
                            ))
                            add(ProjectOutputSizeTransformation(outputWidth, outputHeight, clip.videoFitMode))
                            // Per-clip GPU filter/LUT state is intentionally resolved inside
                            // this item's effect graph. A default config contributes no stage;
                            // another clip's state can therefore never bleed into this item.
                            if (!clip.gpuFilterConfig.isDefault) {
                                clip.gpuFilterConfig.filterPresetId
                                    ?.let { com.apexstudio.app.data.filter.FilterManifest.presetById(it) }
                                    ?.let { preset ->
                                        add(LutFilterGlEffect(
                                            context,
                                            preset,
                                            clip.gpuFilterConfig.filterIntensity
                                        ))
                                    }
                                val perClipAdjustments = clip.gpuFilterConfig.toProjectExportAdjustments()
                                if (!perClipAdjustments.isDefault) {
                                    add(com.apexstudio.app.data.adjust.AdjustmentsGlEffect(perClipAdjustments))
                                }
                            }
                            if (config.filterPreset != null && (config.filterIntensity > 0f ||
                                    sharedAnimation?.filterKeyframes?.isNotEmpty() == true ||
                                    sharedAnimation?.lutKeyframes?.isNotEmpty() == true)) {
                                add(LutFilterGlEffect(
                                    context,
                                    config.filterPreset,
                                    config.filterIntensity,
                                    presentationTimeIntensityProvider = sharedAnimation?.let { typed ->
                                        { compositionTimeUs ->
                                            val projectMs = keyframeClock.projectTimeMs(
                                                com.apexstudio.app.data.animation.itemLocalPresentationTimeUs(
                                                    compositionTimeUs, entry.timelineStartMs
                                                )
                                            )
                                            config.filterIntensity * typed.filterIntensityAt(projectMs) * typed.lutIntensityAt(projectMs)
                                        }
                                    }
                                ))
                            }
                            if (!config.adjustments.isDefault) {
                                val matrix = com.apexstudio.app.data.filter.FilterColorMatrix.getCombinedMatrix(
                                    filterId = null,
                                    intensity = 0f,
                                    adjustments = config.adjustments
                                )
                                add(com.apexstudio.app.data.filter.ColorMatrixGlEffect(matrix))
                                val v2 = config.adjustments
                                if (v2.hasNonMatrixAdjustments) {
                                    add(com.apexstudio.app.data.adjust.AdjustmentsGlEffect(
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
                                    ))
                                }
                            }
                            if (config.fxPreset != null && config.fxIntensity > 0f) {
                                add(com.apexstudio.app.data.fx.FxGlEffect(
                                    config.fxPreset, config.fxIntensity, config.fxSpeed
                                ))
                            }
                            // Keyframes are in project-global time. This effect runs before
                            // speed changes PTS, so the mapper can apply this clip's source trim,
                            // speed curve, and sequential project placement exactly once.
                            val hasTypedAnimation = itemConfig.sharedVideoClip
                                ?.animationKeyframeTimes()?.isNotEmpty() == true
                            if (!clip.keyframes.isEmpty() || hasTypedAnimation) {
                                add(com.apexstudio.app.data.animation.KeyframeAnimationEffect(
                                    trackProvider = { clip.keyframes },
                                    // Composition effects see PTS with this item's sequence offset;
                                    // projectTimeMs expects an item-local, trim-relative timestamp.
                                    timeMsAtPresentationTimeUs = { compositionTimeUs ->
                                        keyframeClock.projectTimeMs(
                                            com.apexstudio.app.data.animation.itemLocalPresentationTimeUs(
                                                compositionTimeUs, entry.timelineStartMs
                                            )
                                        )
                                    },
                                    sharedVideoClipProvider = { itemConfig.sharedVideoClip }
                                ).buildEffects().first())
                            }
                            // Clip masks use the clip-output clock. Composition
                            // PTS includes the sequence offset, so localize it
                            // first, map through this item's speed clock, then
                            // subtract the entry placement before interpolation.
                            com.apexstudio.app.data.effect.MaskGlEffect.fromMask(
                                clip.mask,
                                clip.mask?.let { mask ->
                                    { compositionTimeUs ->
                                        mask.resolveMaskAt(
                                            (keyframeClock.projectTimeMs(
                                                com.apexstudio.app.data.animation.itemLocalPresentationTimeUs(
                                                    compositionTimeUs, entry.timelineStartMs
                                                )
                                            ) - entry.timelineStartMs).coerceAtLeast(0L)
                                        )
                                    }
                                }
                            )?.let(::add)
                            speedPair?.second?.let(::add)
                            // These windows are clip-output-relative: the trimmed sequence item starts at 0,
                            // and the caption/sticker effects follow speed processing on that output clock.
                            val aspect = outputWidth.toFloat() / outputHeight
                            val captions = clip.textOverlays.filter { it.text.isNotBlank() }
                            if (captions.isNotEmpty()) {
                                add(com.apexstudio.app.data.effect.TextOverlayGlEffect(context, captions, aspect))
                            }
                            // Keep sticker layering in editor order, above captions and prior stickers.
                            clip.stickers.forEach { sticker ->
                                if (sticker.isPngSticker()) {
                                    add(com.apexstudio.app.data.effect.StickerGlEffect(context, sticker, aspect))
                                } else {
                                    add(com.apexstudio.app.data.effect.TextOverlayGlEffect(
                                        context,
                                        com.apexstudio.app.data.export.projectEmojiStickerAsTextOverlay(sticker),
                                        aspect
                                    ))
                                }
                            }
                            // Apply to the completed clip image, including clip-attached foreground art,
                            // identically for each sequence item. Unsupported extra tracks are rejected
                            // by projectCompositionUnsupportedReason before this render path is reached.
                            config.colorStudioGrade?.let { grade ->
                                add(com.apexstudio.app.data.filter.LutFilterGlEffect(
                                    context, preset = null, preloaded = grade.toLutTexture()
                                ))
                            }
                            config.signature?.let { signature ->
                                addAll(buildSignatureExportEffects(signature, aspect))
                            }
                            add(androidx.media3.effect.FrameDropEffect.createDefaultFrameDropEffect(
                                effectiveExportFrameRate(config.fps).toFloat()
                            ))
                        }
                        val audioProcessors = buildVideoExportAudioProcessors(itemConfig, speedPair?.first)
                        val input = MediaItem.Builder()
                            .setUri(Uri.parse(clip.uri))
                            .setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(entry.sourceStartMs)
                                    .setEndPositionMs(entry.sourceEndMs)
                                    .build()
                            )
                            .build()
                        EditedMediaItem.Builder(input)
                            .setFrameRate(effectiveExportFrameRate(config.fps))
                            .setEffects(Effects(audioProcessors, videoEffects))
                            .build()
                    }
                    val composition = Composition.Builder(EditedMediaItemSequence(items)).build()
                    diagnostics.stage("project_preparation_completed", "durationMs=${plan.durationMs}")
                    val renderResult = renderProjectComposition(composition, config, partial, run, diagnostics)
                    currentCoroutineContext().ensureActive()
                    check(!run.cancelled.get()) { "Project export was cancelled" }

                    var finishedFile = partial
                    if (config.musicMixTracks.isNotEmpty()) {
                        publishProjectProgress(run, 0.92f)
                        val mixed = File.createTempFile("apex_project_music_", ".mp4", outputDir)
                        mixedFile = mixed
                        if (!mixed.delete()) throw IllegalStateException("Cannot prepare project music mix output")
                        val coroutineContext = currentCoroutineContext()
                        val mixResult = MusicExportMixer.mixMusicIntoVideo(
                            context, partial, config.musicMixTracks, mixed
                        ) { coroutineContext.ensureActive() }
                        coroutineContext.ensureActive()
                        if (mixResult.skippedTracks > 0) {
                            throw IllegalStateException("Project music postmix skipped ${mixResult.skippedTracks} track(s)")
                        }
                        finishedFile = mixed
                    }
                    currentCoroutineContext().ensureActive()
                    check(!run.cancelled.get()) { "Project export was cancelled" }
                    if (destination.exists() && !destination.delete()) {
                        throw IllegalStateException("Cannot replace existing project export")
                    }
                    if (!finishedFile.renameTo(destination)) {
                        throw IllegalStateException("Could not finalize project export file")
                    }
                    val validationWarning = validateEncodedOutput(
                        destination, config.fps, renderResult.videoFrameCount,
                        renderResult.durationMs, diagnostics
                    )
                    diagnostics.stage(
                        "project_export_completed",
                        "outputBytes=${destination.length()} videoFrames=${renderResult.videoFrameCount}"
                    )
                    withContext(Dispatchers.Main.immediate) {
                        if (isCurrentProjectRun(run)) {
                            _exportState.value = ExportProgressState(
                                isExporting = false,
                                progress = 1f,
                                outputUri = Uri.fromFile(destination).toString(),
                                warning = validationWarning
                            )
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    diagnostics.stage("project_export_failed", "failure=${failure.javaClass.simpleName}")
                    Log.e(TAG, "startProjectExport failed", failure)
                    withContext(Dispatchers.Main.immediate) {
                        if (isCurrentProjectRun(run) && !run.cancelled.get()) {
                            _exportState.value = ExportProgressState(
                                error = failure.message ?: "Project export failed"
                            )
                        }
                    }
                } finally {
                    partialFile?.let { if (it.exists()) it.delete() }
                    mixedFile?.let { if (it.exists()) it.delete() }
                    synchronized(projectRunLock) {
                        if (projectRun === run) {
                            projectRun = null
                            projectExportJob = null
                        }
                    }
                }
            }
        }
    }

    private fun isCurrentProjectRun(run: ProjectExportRun): Boolean = synchronized(projectRunLock) {
        projectRun === run && !run.cancelled.get()
    }

    /** Preserve monotonic visible progress even when Media3 has no early estimate. */
    private fun publishProjectProgress(run: ProjectExportRun, reported: Float) {
        var next: Float
        while (true) {
            val previous = run.lastProgress.get()
            next = monotonicProjectExportProgress(previous, reported)
            if (run.lastProgress.compareAndSet(previous, next)) break
        }
        synchronized(projectRunLock) {
            if (projectRun === run && !run.cancelled.get()) {
                _exportState.value = _exportState.value.copy(isExporting = true, progress = next)
            }
        }
    }

    /** Compose callback API is main-looper confined, like the selected-clip route. */
    private suspend fun renderProjectComposition(
        composition: Composition,
        config: ExportConfig,
        outputFile: File,
        run: ProjectExportRun,
        diagnostics: ExportDiagnosticTrace
    ): ExportResult = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine<ExportResult> { continuation ->
            val settled = AtomicBoolean(false)
            var active: Transformer? = null
            continuation.invokeOnCancellation {
                run.cancelled.set(true)
                mainHandler.post {
                    active?.let { renderer ->
                        try { renderer.cancel() } catch (_: Exception) {}
                        if (transformer === renderer) transformer = null
                    }
                    if (outputFile.exists()) outputFile.delete()
                }
            }
            if (!continuation.isActive || !isCurrentProjectRun(run)) return@suspendCancellableCoroutine
            try {
                val encoderSettings = VideoEncoderSettings.Builder()
                    .setBitrate(exportTargetBitrate(config))
                    .build()
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(encoderSettings)
                    .setEnableFallback(false) // Never silently change the owner's requested output settings.
                    .build()
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (!isCurrentProjectRun(run) || !finishTransformerRun(active)) return
                        diagnostics.stage(
                            "project_transformer_completed",
                            "durationMs=${exportResult.durationMs} frames=${exportResult.videoFrameCount}"
                        )
                        if (settled.compareAndSet(false, true) && !run.cancelled.get()) {
                            continuation.resumeWith(Result.success(exportResult))
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (!isCurrentProjectRun(run) || !finishTransformerRun(active)) return
                        diagnostics.stage(
                            "project_transformer_failed",
                            "errorCode=${exportException.errorCodeName} errorType=${exportException.cause?.javaClass?.simpleName} " +
                                "message=${exportException.message} frames=${exportResult.videoFrameCount} durationMs=${exportResult.durationMs}"
                        )
                        if (settled.compareAndSet(false, true) && !run.cancelled.get()) {
                            continuation.resumeWith(Result.failure(exportException))
                        }
                    }
                }
                active = Transformer.Builder(context)
                    .setLooper(Looper.getMainLooper())
                    .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                    .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(listener)
                    .build()
                transformer = active
                active!!.start(composition, outputFile.absolutePath)
                diagnostics.stage("project_transformer_started")
                startProgressTracking(active!!, diagnostics) { progress ->
                    publishProjectProgress(run, 0.01f + progress * 0.89f)
                }
            } catch (failure: Exception) {
                active?.let {
                    try { it.cancel() } catch (_: Exception) {}
                    if (transformer === it) transformer = null
                }
                if (settled.compareAndSet(false, true)) continuation.resumeWith(Result.failure(failure))
            }
        }
    }

    internal fun reserveNonProjectExport(): Any? = synchronized(projectRunLock) {
        if (projectRun != null || _exportState.value.isExporting || transformer != null ||
            overlayAudioTransformer != null || postMixJob?.isActive == true
        ) {
            Log.w(TAG, "Export refused because another export is active")
            null
        } else {
            _exportState.value = ExportProgressState(isExporting = true, progress = 0f)
            Any().also { nonProjectRun = it }
        }
    }

    /**
     * Initiates hardware-accelerated video export on a background Coroutine.
     */
    fun startExport(
        inputUri: String,
        config: ExportConfig,
        outputFileName: String = "apex_studio_export.mp4"
    ) {
        val run = reserveNonProjectExport() ?: return
        exportPreparationJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val diagnostics = ExportDiagnosticTrace(emit = { Log.i(TAG, it) })
                diagnostics.stage("video_preparation_started")
                try {
                    val sourceAudit = inspectSourceVideoFormat(context, inputUri)
                    diagnostics.stage("source_video_format", "${sourceAudit.diagnosticSummary()} requestedFps=${effectiveExportFrameRate(config.fps)}")
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    // A missing metadata field is not a decoded-frame failure. Transformer
                    // remains responsible for decoding and reports real codec errors below.
                    diagnostics.stage("source_video_format_unavailable", "failure=${failure.javaClass.simpleName}")
                }
                currentCoroutineContext().ensureActive()
                require(config.projectVideoEffectId == null ||
                    com.apexstudio.app.data.template.ProjectVideoPresets.effect(config.projectVideoEffectId) != null) {
                    "Unknown project-video effect; export stopped rather than dropping the edit."
                }
                require(config.workspaceEffects.size <= 16 && config.workspaceEffects.all { it.hasValidParameters() }) {
                    "Invalid ranged filter or custom grading values; export stopped rather than dropping the edit."
                }
                require(config.chromaKeySettings == null || config.chromaKeySettings.hasValidChromaParameters()) {
                    "Invalid chroma-key parameters; export stopped rather than changing the keyed result."
                }

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
                val pipOverlayEffects = mutableListOf<androidx.media3.common.Effect>()
                val foregroundEffects = mutableListOf<androidx.media3.common.Effect>()
                // Crop before speed changes, on the clipped source clock. This avoids numerical
                // inverse-curve drift and never applies a clip's speed a second time to crop lookup.
                val speedPair = buildSpeedProvider(config)?.let { Effects.createExperimentalSpeedChangingEffect(it) }
                val activeFaceCrop = config.faceCropConfig
                if (activeFaceCrop != null) {
                    videoEffects.add(FaceCropGlEffect.forExport(activeFaceCrop.exportConfig()))
                }

                // Picture-in-Picture overlays: composite each overlay clip as a true
                // PiP in the export, matching the preview's bottom-end corner placement.
                // Each overlay gets its own GlEffect instance (chained like text overlays).
                config.pipOverlays.forEach { pip ->
                    pipOverlayEffects.add(
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
                com.apexstudio.app.data.effect.ChromaKeyGlEffect.fromSettings(config.chromaKeySettings)
                    ?.let { videoEffects.add(it) }

                com.apexstudio.app.data.template.ProjectVideoPresets.colorEffect(config.projectVideoEffectId)
                    ?.let { videoEffects.add(it) }
                // Match preview: source crop → fixed color → ranged effect, before output resize/speed.
                val keyframeClock = keyframeClockFor(config)
                val sharedAnimation = config.sharedVideoClip
                videoEffects.addAll(
                    com.apexstudio.app.data.effect.WorkspaceEffectGlEffect.createEffects(
                        config.workspaceEffects,
                        config.trimStartMs,
                        sharedAnimation?.takeIf { it.effectIntensityKeyframes.isNotEmpty() }?.let { typed ->
                            { presentationTimeUs ->
                                typed.effectIntensityAt(keyframeClock.projectTimeMs(presentationTimeUs))
                            }
                        }
                    )
                )

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

                // 1.6 Shape mask (Mask tool). Mask keyframes are clip-output
                // relative: convert the pre-speed clipped-item PTS through
                // the same clock as transforms, then remove the timeline
                // placement used by project/global clocks.
                com.apexstudio.app.data.effect.MaskGlEffect.fromMask(
                    config.mask,
                    config.mask?.let { mask ->
                        { presentationTimeUs ->
                            mask.resolveMaskAt(
                                (keyframeClock.projectTimeMs(presentationTimeUs) -
                                    config.keyframeTimelineStartMs).coerceAtLeast(0L)
                            )
                        }
                    }
                )
                    ?.let { videoEffects.add(it) }

                // 2. Color Filter (3D LUT) & Video Adjustments
                if (config.filterPreset != null && (config.filterIntensity > 0f ||
                        sharedAnimation?.filterKeyframes?.isNotEmpty() == true ||
                        sharedAnimation?.lutKeyframes?.isNotEmpty() == true)) {
                    videoEffects.add(LutFilterGlEffect(
                        context,
                        config.filterPreset,
                        config.filterIntensity,
                        presentationTimeIntensityProvider = sharedAnimation?.let { typed ->
                            { presentationTimeUs ->
                                val projectMs = keyframeClock.projectTimeMs(presentationTimeUs)
                                config.filterIntensity * typed.filterIntensityAt(projectMs) * typed.lutIntensityAt(projectMs)
                            }
                        }
                    ))
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

                // Keyframes are stored against the project playhead. Evaluate them on the
                // clipped source clock before SpeedChangeEffect rewrites frame timestamps.
                val hasTypedAnimation = config.sharedVideoClip?.animationKeyframeTimes()?.isNotEmpty() == true
                if (!config.keyframes.isEmpty() || hasTypedAnimation) {
                    videoEffects.add(
                        com.apexstudio.app.data.animation.KeyframeAnimationEffect(
                            trackProvider = { config.keyframes },
                            timeMsAtPresentationTimeUs = keyframeClock::projectTimeMs,
                            sharedVideoClipProvider = { config.sharedVideoClip }
                        ).buildEffects().first()
                    )
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

                // 7. Caption / Text Overlays & Stickers
                val captionOverlays = config.textOverlays.filter { it.text.isNotBlank() } +
                    listOfNotNull(buildArGreetingOverlay(config))
                if (captionOverlays.isNotEmpty() || config.stickers.isNotEmpty() || config.signature != null) {
                    val (width, height) = AspectCropTransformation.targetSizeFor(config.resolution, config.aspectRatio)
                    val aspect = config.faceCropConfig?.track?.outputAspectRatio
                        ?: if (config.preserveInputAspectRatio) queryAspectRatio(inputUri) else width.toFloat() / height
                    foregroundEffects.addAll(buildCaptionExportEffects(captionOverlays, aspect))
                    config.stickers.forEach { sticker ->
                        if (sticker.isPngSticker()) {
                            // Bundled PNG sticker: rasterised with crop / cutout /
                            // rotation / opacity and composited as a GL sprite so
                            // the export matches the editor preview exactly.
                            foregroundEffects.add(
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
                            val overlay = projectEmojiStickerAsTextOverlay(sticker)
                            foregroundEffects.add(TextOverlayGlEffect(context, overlay, aspect))
                        }
                    }
                    config.signature?.let { signature ->
                        foregroundEffects.addAll(buildSignatureExportEffects(signature, aspect))
                    }
                }

                // Main-source geometry, grading and animation must not also transform PiP.
                // Composite PiP after the main chain and captions/stickers above the PiP layer.
                val drawingEffects = mutableListOf<androidx.media3.common.Effect>()
                if (config.videoDrawings.any { it.strokes.isNotEmpty() }) {
                    val (drawingWidth, drawingHeight) = selectedDrawingCanvasSize(
                        config,
                        if (config.preserveInputAspectRatio) queryAspectRatio(inputUri) else null
                    )
                    val drawingBitmap = com.apexstudio.app.ui.screens.editor.VideoDrawingRenderer.renderBitmap(
                        drawingWidth, drawingHeight, config.videoDrawings, config.videoDrawingClipId
                    )
                    drawingEffects.add(
                        androidx.media3.effect.OverlayEffect(
                            listOf(androidx.media3.effect.BitmapOverlay.createStaticBitmapOverlay(drawingBitmap))
                        )
                    )
                }
                val composedVideoEffects = composeVideoEffectLayers(
                    mainVideoEffects = videoEffects + selectedVideoOutputEffects(config),
                    pipOverlayEffects = pipOverlayEffects,
                    foregroundEffects = foregroundEffects,
                    mainArtworkEffects = drawingEffects
                )
                currentCoroutineContext().ensureActive()
                diagnostics.stage("video_preparation_completed")
                val editedMediaItem = EditedMediaItem.Builder(inputMediaItem)
                    .setFrameRate(effectiveExportFrameRate(config.fps))
                    .setEffects(Effects(audioProcessors, composedVideoEffects))
                    .build()

                // 8. Configure Hardware Encoder Factory based on target resolution
                //    (+ Pro Phase 1 custom bitrate override when set)
                startTransformerOnApplicationThread(
                    factory = {
                        buildHardwareTransformer(
                            config, outputFile, run, config.musicMixTracks,
                            overlayAudioSources = config.overlayAudioSources,
                            diagnostics = diagnostics
                        )
                    },
                    start = { it.start(editedMediaItem, outputFile.absolutePath) },
                    diagnostics = diagnostics
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "startExport failed", e)
                mainHandler.post {
                    if (nonProjectRun !== run) return@post
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
        val sharedAudioAnimation = config.sharedVideoClip
            ?.takeIf { it.volumeKeyframes.isNotEmpty() || it.panKeyframes.isNotEmpty() }
        if (sharedAudioAnimation == null) volumeProcessorFor(config.volume)?.let(processors::add)
        speedProcessor?.let(processors::add)
        sharedAudioAnimation?.let { clip ->
            // This runs after speed, so its frame clock is the emitted output
            // clock and does not apply the speed transform a second time.
            processors.add(
                SharedVideoClipAudioProcessor(
                    clip = clip,
                    globalGain = config.globalAudioVolume ?: config.volume,
                    timelineStartMs = config.keyframeTimelineStartMs
                )
            )
        }
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

    private fun keyframeClockFor(config: ExportConfig) =
        com.apexstudio.app.data.animation.KeyframeAnimationClock(
            trimStartMs = config.trimStartMs,
            trimEndMs = config.trimEndMs,
            timelineStartMs = config.keyframeTimelineStartMs,
            clipSpeed = config.clipSpeed,
            speedPoints = config.speedPoints
        )

    /**
     * Builds the Media3 [SpeedProvider] for an export, or null when no
     * speed change applies (constant 1x, no curve).
     *
     * Timestamp base: the effect receives presentation times relative
     * to the clipped item start (0-based), so the fraction is
     * timeUs / trimmed-span. KeyframeAnimationEffect runs before this
     * speed stage and explicitly maps the same source-relative timestamp
     * into project time. [getNextSpeedChangeTimeUs] re-queries
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

    /** Text/logo signature effects are appended last and composite over every other layer. */
    internal fun buildSignatureExportEffects(
        signature: Signature,
        aspectRatio: Float
    ): List<androidx.media3.common.Effect> = buildList<androidx.media3.common.Effect> {
        val alpha = signature.alpha.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        val fontSize = signature.fontSize.takeIf(Float::isFinite)?.coerceIn(12f, 256f) ?: 24f
        val margin = signature.marginDp.takeIf(Float::isFinite)?.coerceIn(0f, 256f) ?: 16f
        val isRight = signature.position == SignatureCorner.TOP_RIGHT ||
            signature.position == SignatureCorner.BOTTOM_RIGHT
        val isTop = signature.position == SignatureCorner.TOP_LEFT ||
            signature.position == SignatureCorner.TOP_RIGHT
        if (signature.text.isNotBlank() && alpha > 0f) {
            val marginFraction = (margin / 1080f).coerceIn(0f, 0.2f)
            val sizeScale = (fontSize / 64f).coerceIn(0.3f, 4f)
            val textHeightFraction = 0.07f * sizeScale
            val x = if (isRight) 1f - marginFraction else marginFraction
            val y = if (isTop) marginFraction + textHeightFraction / 2f
                else 1f - marginFraction - textHeightFraction / 2f
            add(
                TextOverlayGlEffect(
                    context,
                    TextOverlay(
                        id = "project-signature",
                        text = signature.text,
                        x = x,
                        y = y,
                        sizeScale = sizeScale,
                        colorArgb = signature.color.toArgb().toLong() and 0xFFFFFFFFL,
                        fontFamily = "sans",
                        isBold = false,
                        startMs = 0L,
                        endMs = Long.MAX_VALUE,
                        opacity = alpha,
                        textAlign = if (isRight) "RIGHT" else "LEFT"
                    ),
                    aspectRatio
                )
            )
        }
        signature.logoUri?.takeIf { alpha > 0f }?.let { logoUri ->
            val marginFraction = (margin / 1080f).coerceIn(0f, 0.2f)
            val backgroundX = if (isRight) 1f - marginFraction * 2f else -1f + marginFraction * 2f
            val backgroundY = if (isTop) 1f - marginFraction * 2f else -1f + marginFraction * 2f
            val overlayX = if (isRight) 1f else -1f
            val overlayY = if (isTop) 1f else -1f
            val settings = androidx.media3.effect.OverlaySettings.Builder()
                .setAlphaScale(alpha)
                .setBackgroundFrameAnchor(backgroundX, backgroundY)
                .setOverlayFrameAnchor(overlayX, overlayY)
                .setScale(0.12f, 0.12f)
                .build()
            val bitmapOverlay = androidx.media3.effect.BitmapOverlay
                .createStaticBitmapOverlay(context, logoUri, settings)
            add(androidx.media3.effect.OverlayEffect(listOf(bitmapOverlay)))
        }
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
        val run = reserveNonProjectExport() ?: return
        exportPreparationJob = CoroutineScope(Dispatchers.IO).launch {
            try {
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
                currentCoroutineContext().ensureActive()

                var active: Transformer? = null
                startTransformerOnApplicationThread(factory = { Transformer.Builder(context)
                    .setLooper(Looper.getMainLooper())
                    .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (nonProjectRun !== run || !finishTransformerRun(active)) return
                            mainHandler.post {
                                if (nonProjectRun !== run) return@post
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
                            if (nonProjectRun !== run || !finishTransformerRun(active)) return
                            mainHandler.post {
                                if (nonProjectRun !== run) return@post
                                _exportState.value = ExportProgressState(
                                    isExporting = false,
                                    progress = 0f,
                                    error = exportException.message ?: "Audio export failed"
                                )
                            }
                        }
                    })
                    .build()
                    .also { active = it }
                }, start = { it.start(composition, outputFile.absolutePath) })
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "startAudioExport failed", e)
                mainHandler.post {
                    if (nonProjectRun !== run) return@post
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
        val run = reserveNonProjectExport() ?: return
        exportPreparationJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val templateManager = TimelineTemplateManager(context)
                val composition = templateManager.mapTemplateToComposition(template)

                val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
                outputDir.mkdirs()
                val outputFile = File(outputDir, outputFileName)
                currentCoroutineContext().ensureActive()

                startTransformerOnApplicationThread(
                    factory = { buildHardwareTransformer(ExportConfig(resolution = template.resolution), outputFile, run) },
                    start = { it.start(composition, outputFile.absolutePath) }
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "startTemplateExport failed", e)
                mainHandler.post {
                    if (nonProjectRun !== run) return@post
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

    /** Demuxes the completed output on a worker and returns only evidence-backed warnings. */
    private fun validateEncodedOutput(
        outputFile: File,
        requestedFps: Int,
        transformerFrameCount: Int?,
        transformerDurationMs: Long?,
        diagnostics: ExportDiagnosticTrace?
    ): String? = try {
        val audit = inspectEncodedVideo(
            context = context,
            file = outputFile,
            requestedFps = requestedFps,
            transformerFrameCount = transformerFrameCount,
            transformerDurationMs = transformerDurationMs
        )
        diagnostics?.stage("encoded_output_validated", audit.diagnosticSummary())
        audit.warning
    } catch (failure: Exception) {
        diagnostics?.stage("encoded_output_validation_failed", "failure=${failure.javaClass.simpleName}")
        Log.w(TAG, "Could not validate encoded export output", failure)
        "Export finished, but its encoded video stream could not be validated."
    }

    internal fun buildHardwareTransformer(
        config: ExportConfig,
        outputFile: File,
        run: Any,
        musicMixTracks: List<com.apexstudio.app.domain.model.AudioTrack> = emptyList(),
        overlayAudioSources: List<AudioExportSource> = emptyList(),
        diagnostics: ExportDiagnosticTrace? = null
    ): Transformer {
        // Let Media3 negotiate a supported H.264 profile/level instead of forcing
        // AVC High Level 5.1 even for 720p or an HEVC input container.
        val encoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(exportTargetBitrate(config))
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(encoderSettings)
            .setEnableFallback(false) // Never silently change the owner's requested output settings.
            .build()

        var active: Transformer? = null
        val transformerListener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (nonProjectRun !== run || !finishTransformerRun(active)) return
                diagnostics?.stage(
                    "video_transformer_completed",
                    "durationMs=${exportResult.durationMs} frames=${exportResult.videoFrameCount} outputBytes=${outputFile.length()}"
                )
                val outputValidation = CoroutineScope(Dispatchers.IO).async {
                    validateEncodedOutput(
                        outputFile, config.fps, exportResult.videoFrameCount,
                        exportResult.durationMs, diagnostics
                    )
                }
                if (musicMixTracks.isEmpty() && overlayAudioSources.isEmpty()) {
                    diagnostics?.stage("export_completed")
                    val outputUri = Uri.fromFile(outputFile).toString()
                    mainHandler.post {
                        if (nonProjectRun !== run) return@post
                        _exportState.value = ExportProgressState(
                            isExporting = false,
                            progress = 1f,
                            outputUri = outputUri
                        )
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        val validationWarning = outputValidation.await()
                        mainHandler.post {
                            if (nonProjectRun !== run || _exportState.value.outputUri != outputUri) return@post
                            _exportState.value = _exportState.value.copy(warning = validationWarning)
                        }
                    }
                    return
                }
                diagnostics?.stage(
                    "post_mix_started",
                    "musicTracks=${musicMixTracks.size} overlaySources=${overlayAudioSources.size}"
                )
                mainHandler.post {
                    if (nonProjectRun !== run) return@post
                    _exportState.value = ExportProgressState(isExporting = true, progress = 0.97f)
                }
                postMixJob?.cancel()
                postMixJob = CoroutineScope(Dispatchers.IO).launch {
                    var videoValidationWarning = outputValidation.await()
                    val mixContext = currentCoroutineContext()
                    val owner = mixContext.job
                    val mixedFile = File(outputFile.parentFile, outputFile.nameWithoutExtension + "_music.mp4")
                    var preparedOverlay: File? = null
                    var overlayWarning: String? = null
                    try {
                        var overlayTrack: com.apexstudio.app.domain.model.AudioTrack? = null
                        if (overlayAudioSources.isNotEmpty()) {
                            try {
                                diagnostics?.stage("overlay_audio_prepare_started", "sources=${overlayAudioSources.size}")
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
                                    diagnostics?.stage("overlay_audio_prepare_completed", "durationMs=$durationMs")
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
                                    outputUri = Uri.fromFile(outputFile).toString(),
                                    warning = listOfNotNull(videoValidationWarning, overlayWarning).distinct().joinToString(" ").ifBlank { null }
                                ))
                            return@launch
                        }
                        mixContext.ensureActive()
                        diagnostics?.stage("music_audio_mix_started", "tracks=${tracks.size}")
                        val result = MusicExportMixer.mixMusicIntoVideo(context, outputFile, tracks, mixedFile) {
                            mixContext.ensureActive()
                        }
                        mixContext.ensureActive()
                        diagnostics?.stage(
                            "music_audio_mix_completed",
                            "mixedTracks=${result.mixedTracks} skippedTracks=${result.skippedTracks} outputBytes=${mixedFile.length()}"
                        )
                        Log.i(TAG, "Audio mix done: ${result.mixedTracks} mixed, ${result.skippedTracks} skipped")
                        val mixedOutputWarning = validateEncodedOutput(
                            mixedFile, config.fps, exportResult.videoFrameCount,
                            exportResult.durationMs, diagnostics
                        )
                        videoValidationWarning = listOfNotNull(videoValidationWarning, mixedOutputWarning)
                            .distinct().joinToString(" ").ifBlank { null }
                        if (!outputFile.delete()) Log.w(TAG, "Could not delete intermediate export file")
                        val mixWarning = AudioMixExportNotice.forSkippedTracks(result.skippedTracks)
                        publishPostMixState(owner, ExportProgressState(
                                isExporting = false, progress = 1f,
                                outputUri = Uri.fromFile(mixedFile).toString(),
                                warning = listOfNotNull(videoValidationWarning, overlayWarning, mixWarning).distinct().joinToString(" ").ifBlank { null }
                            ))
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        if (mixedFile.exists()) mixedFile.delete()
                        throw cancelled
                    } catch (e: Exception) {
                        diagnostics?.stage("post_mix_failed", "failure=${e.javaClass.simpleName}")
                        Log.w(TAG, "Audio mix failed; keeping video without postmix audio", e)
                        if (mixedFile.exists() && !mixedFile.delete()) Log.w(TAG, "Could not delete partial mixed file")
                        val warning = listOfNotNull(videoValidationWarning, overlayWarning, AudioMixExportNotice.MIX_FAILED).distinct().joinToString(" ")
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
                if (nonProjectRun !== run || !finishTransformerRun(active)) return
                diagnostics?.stage(
                    "video_transformer_failed",
                    "errorCode=${exportException.errorCodeName} errorType=${exportException.cause?.javaClass?.simpleName} " +
                        "message=${exportException.message}"
                )
                mainHandler.post {
                    if (nonProjectRun !== run) return@post
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
            .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
            .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(transformerListener)
            .build()
            .also { active = it }
    }

    /** Terminal callbacks must retire only their own renderer, so retry is possible. */
    internal fun finishTransformerRun(active: Transformer?): Boolean {
        if (active == null || transformer !== active) return false
        progressJob?.cancel()
        progressJob = null
        transformer = null
        return true
    }

    /** Media3 start/getProgress/cancel all require its configured application looper. */
    internal suspend fun startTransformerOnApplicationThread(
        factory: () -> Transformer,
        diagnostics: ExportDiagnosticTrace? = null,
        start: (Transformer) -> Unit
    ) = withContext(Dispatchers.Main.immediate) {
        val active = factory()
        transformer = active
        try {
            start(active)
            diagnostics?.stage("video_transformer_started")
            startProgressTracking(active, diagnostics)
        } catch (failure: Exception) {
            if (transformer === active) transformer = null
            try { active.cancel() } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    private fun startProgressTracking(
        activeTransformer: Transformer,
        diagnostics: ExportDiagnosticTrace? = null,
        onProgress: ((Float) -> Unit)? = null
    ) {
        progressJob?.cancel()
        val progressHolder = ProgressHolder()
        progressJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            while (_exportState.value.isExporting && this@ExportEngine.transformer === activeTransformer) {
                val pState = activeTransformer.getProgress(progressHolder)
                if (pState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    diagnostics?.progress(progressHolder.progress)
                    val p = (progressHolder.progress / 100f).coerceIn(0f, 0.99f)
                    if (onProgress != null) onProgress(p)
                    else _exportState.value = _exportState.value.copy(progress = p)
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
        nonProjectRun = null
        exportPreparationJob?.cancel()
        exportPreparationJob = null
        postMixJob?.cancel()
        postMixJob = null
        synchronized(projectRunLock) {
            projectRun?.cancelled?.set(true)
            projectRun = null
            projectExportJob?.cancel()
            projectExportJob = null
        }
        try { overlayAudioTransformer?.cancel() } catch (_: Exception) {}
        overlayAudioTransformer = null
        val renderer = transformer
        transformer = null
        try { renderer?.cancel() } catch (failure: Exception) { Log.w(TAG, "Export cancellation cleanup failed", failure) }
        _exportState.value = ExportProgressState()
    }

    /** Dedicated comparison engines must stop and drain their writer before deleting owned outputs. */
    internal suspend fun cancelProjectExportAndAwait() {
        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) {
            val writer = synchronized(projectRunLock) { projectExportJob }
            cancelExport()
            writer?.join()
        }
    }

    fun release() {
        cancelExport()
    }
}
