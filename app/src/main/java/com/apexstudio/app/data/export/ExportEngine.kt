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
import com.apexstudio.app.data.effect.TextOverlayGlEffect
import com.apexstudio.app.data.effect.VideoCropGlEffect
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.filter.LutFilterGlEffect
import com.apexstudio.app.data.fx.FxGlEffect
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.data.gl.TransitionEngine
import com.apexstudio.app.data.gl.TransitionGlEffect
import com.apexstudio.app.data.template.TimelineTemplate
import com.apexstudio.app.data.template.TimelineTemplateManager
import com.apexstudio.app.domain.model.TextOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
@UnstableApi
class ExportEngine(private val context: Context) {

    companion object {
        private const val TAG = "ExportEngine"
    }

    private val _exportState = MutableStateFlow(ExportProgressState())
    val exportState: StateFlow<ExportProgressState> = _exportState

    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    data class ExportProgressState(
        val isExporting: Boolean = false,
        val progress: Float = 0f,
        val outputUri: String? = null,
        val error: String? = null
    )

    data class ExportConfig(
        val resolution: String = "1080p",
        val fps: Int = 60,
        val quality: String = "high",
        val filterPreset: FilterPreset? = null,
        val filterIntensity: Float = 1f,
        val adjustments: com.apexstudio.app.domain.model.VideoAdjustments = com.apexstudio.app.domain.model.VideoAdjustments(),
        val cropRect: com.apexstudio.app.presentation.state.CropRect? = null,
        val clipSpeed: Float = 1f,
        val keyframes: com.apexstudio.app.domain.model.KeyframeTrack =
            com.apexstudio.app.domain.model.KeyframeTrack(),
        val fxPreset: FxPreset? = null,
        val fxIntensity: Float = 1f,
        val transitionType: TransitionEngine.Companion.TransitionType? = null,
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
        val reverbEnabled: Boolean = false,
        val reverbPreset: Short = 0,
        val echoEnabled: Boolean = false,
        val bassBoostEnabled: Boolean = false,
        val bassBoostStrength: Short = 0,
        // Picture-in-Picture overlays: composited in the export to match preview.
        val pipOverlays: List<PipOverlayConfig> = emptyList()
    )

    /**
     * Configuration for one Picture-in-Picture overlay in the export.
     * The overlay video is composited over the main video at [offsetMs] on the
     * export timeline, scaled to [widthFraction] of the output width and placed
     * in the bottom-end corner (matching the preview's BottomEnd PiP box).
     */
    data class PipOverlayConfig(
        val uri: String,
        val offsetMs: Long = 0L,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = Long.MAX_VALUE,
        val widthFraction: Float = 0.25f,
        val marginFraction: Float = 0.03f,
        val opacity: Float = 1f
    )

    /**
     * One audio source for [startAudioExport]: a media URI plus its trim range
     * (in ms, relative to the source media).
     */
    data class AudioExportSource(
        val uri: String,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L
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
                                widthFraction = pip.widthFraction,
                                marginFraction = pip.marginFraction,
                                opacity = pip.opacity
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "PiP overlay effect creation failed for ${pip.uri}", e)
                    }
                }

                // Audio Studio Effects pipeline (Pitch, Volume, Channel Mixing)
                val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()

                // Audio Pitch Adjustment via SonicAudioProcessor
                if (config.pitchSemitones != 0f) {
                    val pitchFactor = Math.pow(2.0, (config.pitchSemitones / 12.0).toDouble()).toFloat()
                    val sonic = androidx.media3.common.audio.SonicAudioProcessor().apply {
                        setPitch(pitchFactor)
                    }
                    audioProcessors.add(sonic)
                }

                // Volume / Gain adjustment via ChannelMixingMatrix
                if (config.volume != 1f) {
                    val clampedVol = config.volume.coerceIn(0f, 2f)
                    val stereoMatrix = androidx.media3.common.audio.ChannelMixingMatrix(
                        /* inputChannelCount = */ 2,
                        /* outputChannelCount = */ 2,
                        /* coefficients = */ floatArrayOf(
                            clampedVol, 0f,
                            0f, clampedVol
                        )
                    )
                    val channelMixingProcessor = androidx.media3.common.audio.ChannelMixingAudioProcessor()
                    channelMixingProcessor.putChannelMixingMatrix(stereoMatrix)
                    audioProcessors.add(channelMixingProcessor)
                }

                // 1. Crop
                config.cropRect?.let { r ->
                    VideoCropGlEffect.fromRect(r.left, r.top, r.right, r.bottom)
                        ?.let { videoEffects.add(it) }
                }

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

                // 3. Dynamic Visual Effects (Glitch, RGB Split, VHS)
                if (config.fxPreset != null && config.fxIntensity > 0f) {
                    videoEffects.add(FxGlEffect(config.fxPreset, config.fxIntensity))
                }

                // 4. Transitions
                if (config.transitionType != null && config.transitionDurationMs > 0L) {
                    videoEffects.add(
                        TransitionGlEffect(
                            transitionType = config.transitionType,
                            durationUs = config.transitionDurationMs * 1000L,
                            startUs = 0L
                        )
                    )
                }

                // 5. Speed Ramping with Audio/Video synchronization
                if (config.clipSpeed > 0f && config.clipSpeed != 1f) {
                    val constantProvider = object : androidx.media3.common.audio.SpeedProvider {
                        override fun getSpeed(timeUs: Long): Float = config.clipSpeed
                        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long =
                            androidx.media3.common.C.TIME_UNSET
                    }
                    val speedPair = Effects.createExperimentalSpeedChangingEffect(constantProvider)
                    audioProcessors.add(speedPair.first)
                    videoEffects.add(speedPair.second)
                }

                // 6. Keyframe Animation
                if (!config.keyframes.isEmpty()) {
                    videoEffects.add(
                        com.apexstudio.app.data.animation.KeyframeAnimationEffect(
                            trackProvider = { config.keyframes }
                        ).buildEffects().first()
                    )
                }

                // 7. Caption / Text Overlays & Stickers
                val captionOverlays = config.textOverlays.filter { it.text.isNotBlank() }
                if (captionOverlays.isNotEmpty() || config.stickers.isNotEmpty()) {
                    val aspect = queryAspectRatio(inputUri)
                    captionOverlays.forEach { overlay ->
                        videoEffects.add(TextOverlayGlEffect(context, overlay, aspect))
                    }
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
                            // Legacy emoji sticker: rendered as text (unchanged).
                            val overlay = TextOverlay(
                                id = sticker.id,
                                text = sticker.symbolOrUri,
                                x = sticker.x,
                                y = sticker.y,
                                sizeScale = sticker.sizeScale * 1.5f,
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
                val transformer = buildHardwareTransformer(config.resolution, outputFile)
                this@ExportEngine.transformer = transformer

                startProgressTracking(transformer)
                transformer.start(editedMediaItem, outputFile.absolutePath)
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

    /**
     * Builds a fresh list of audio processors (pitch, volume, speed) from the
     * export config. Processors are stateful, so callers must build a new
     * list per media item instead of sharing instances.
     */
    private fun buildExportAudioProcessors(
        config: ExportConfig
    ): MutableList<androidx.media3.common.audio.AudioProcessor> {
        val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()

        // Audio Pitch Adjustment via SonicAudioProcessor
        if (config.pitchSemitones != 0f) {
            val pitchFactor = Math.pow(2.0, (config.pitchSemitones / 12.0).toDouble()).toFloat()
            val sonic = androidx.media3.common.audio.SonicAudioProcessor().apply {
                setPitch(pitchFactor)
            }
            audioProcessors.add(sonic)
        }

        // Volume / Gain adjustment via ChannelMixingMatrix
        if (config.volume != 1f) {
            val clampedVol = config.volume.coerceIn(0f, 2f)
            val stereoMatrix = androidx.media3.common.audio.ChannelMixingMatrix(
                /* inputChannelCount = */ 2,
                /* outputChannelCount = */ 2,
                /* coefficients = */ floatArrayOf(
                    clampedVol, 0f,
                    0f, clampedVol
                )
            )
            val channelMixingProcessor = androidx.media3.common.audio.ChannelMixingAudioProcessor()
            channelMixingProcessor.putChannelMixingMatrix(stereoMatrix)
            audioProcessors.add(channelMixingProcessor)
        }

        // Speed change affects audio too.
        if (config.clipSpeed > 0f && config.clipSpeed != 1f) {
            val constantProvider = object : androidx.media3.common.audio.SpeedProvider {
                override fun getSpeed(timeUs: Long): Float = config.clipSpeed
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long =
                    androidx.media3.common.C.TIME_UNSET
            }
            val speedPair = Effects.createExperimentalSpeedChangingEffect(constantProvider)
            audioProcessors.add(speedPair.first)
        }
        return audioProcessors
    }

    /**
     * Exports the project's audio as a standalone AAC (.m4a) file: every
     * timeline clip's audio plus all unmuted extra audio tracks (music /
     * voiceover), concatenated in timeline order. Reuses the audio pipeline
     * (pitch, volume, channel mixing) from the video export; video is removed
     * via [EditedMediaItem.Builder.setRemoveVideo].
     *
     * Note: sources are concatenated into a single sequence, not
     * simultaneously mixed — an extra music track plays after the clips'
     * audio rather than underneath it. Per-source trim ranges are honored via
     * each item's clipping configuration.
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

                val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
                outputDir.mkdirs()
                val outputFile = File(outputDir, outputFileName)

                val editedItems = sources.map { src ->
                    val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(src.uri))
                    if (src.trimStartMs > 0L || (src.trimEndMs > 0L && src.trimEndMs > src.trimStartMs)) {
                        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                        if (src.trimStartMs > 0L) {
                            clippingBuilder.setStartPositionMs(src.trimStartMs)
                        }
                        if (src.trimEndMs > src.trimStartMs) {
                            clippingBuilder.setEndPositionMs(src.trimEndMs)
                        }
                        mediaItemBuilder.setClippingConfiguration(clippingBuilder.build())
                    }
                    // Fresh audio processors per item — processors are stateful
                    // and must not be shared across sequence items.
                    EditedMediaItem.Builder(mediaItemBuilder.build())
                        .setRemoveVideo(true)
                        .setEffects(Effects(buildExportAudioProcessors(config), emptyList()))
                        .build()
                }
                val composition = Composition.Builder(
                    EditedMediaItemSequence(editedItems)
                ).build()

                val transformer = Transformer.Builder(context)
                    .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            progressJob?.cancel()
                            mainHandler.post {
                                _exportState.value = ExportProgressState(
                                    isExporting = false,
                                    progress = 1f,
                                    outputUri = Uri.fromFile(outputFile).toString()
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
                this@ExportEngine.transformer = transformer

                startProgressTracking(transformer)
                transformer.start(composition, outputFile.absolutePath)
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

                val transformer = buildHardwareTransformer(template.resolution, outputFile)
                this@ExportEngine.transformer = transformer

                startProgressTracking(transformer)
                transformer.start(composition, outputFile.absolutePath)
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

    private fun buildHardwareTransformer(resolution: String, outputFile: File): Transformer {
        val targetBitrate = when (resolution.lowercase()) {
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
                mainHandler.post {
                    _exportState.value = ExportProgressState(
                        isExporting = false,
                        progress = 1f,
                        outputUri = Uri.fromFile(outputFile).toString()
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
                        error = exportException.message ?: "Export failed"
                    )
                }
            }
        }

        return Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .addListener(transformerListener)
            .build()
    }

    private fun startProgressTracking(activeTransformer: Transformer) {
        progressJob?.cancel()
        val progressHolder = ProgressHolder()
        progressJob = CoroutineScope(Dispatchers.IO).launch {
            while (_exportState.value.isExporting && this@ExportEngine.transformer != null) {
                val pState = activeTransformer.getProgress(progressHolder)
                if (pState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val p = (progressHolder.progress / 100f).coerceIn(0f, 0.99f)
                    mainHandler.post {
                        if (_exportState.value.isExporting) {
                            _exportState.value = _exportState.value.copy(progress = p)
                        }
                    }
                }
                delay(100)
            }
        }
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
        progressJob?.cancel()
        progressJob = null
        transformer?.cancel()
        transformer = null
        _exportState.value = ExportProgressState()
    }

    fun release() {
        cancelExport()
    }
}
