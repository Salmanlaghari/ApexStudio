package com.apexstudio.app.data.export

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.apexstudio.app.data.effect.TextOverlayGlEffect
import com.apexstudio.app.data.effect.VideoCropGlEffect
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.filter.LutFilterGlEffect
import com.apexstudio.app.data.fx.FxGlEffect
import com.apexstudio.app.data.fx.FxPreset
import com.apexstudio.app.domain.model.TextOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class ExportEngine(private val context: Context) {

    private val _exportState = MutableStateFlow(ExportProgressState())

    companion object {
        private const val TAG = "ExportEngine"
    }
    val exportState: StateFlow<ExportProgressState> = _exportState

    private var transformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lutEngine = LutFilterEngine(context)

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
        // Normalized (0..1, top-left origin) crop rect applied with the
        // same VideoCropGlEffect the editor preview uses, so the baked
        // export matches the crop the user framed on screen.
        val cropRect: com.apexstudio.app.presentation.state.CropRect? = null,
        // Speed ramping: when set, applied to the exported clip via
        // EditedMediaItem.setSpeed so the time-lapse / slow-mo is
        // baked into the hardware-encoded output.
        val clipSpeed: Float = 1f,
        // Keyframe animation track from the selected clip. When
        // non-empty, the export pipeline drives a MatrixTransformation
        // for every output frame so the translate / scale / rotation
        // / opacity bakes into the MP4.
        val keyframes: com.apexstudio.app.domain.model.KeyframeTrack =
            com.apexstudio.app.domain.model.KeyframeTrack(),
        // Real-time FX (VHS / Glitch / Grain / …). Applied right after
        // the LUT filter so colour first, then spatial FX.
        val fxPreset: FxPreset? = null,
        val fxIntensity: Float = 1f,
        // Captions / titles baked into the export. Each overlay gets
        // its own TextOverlayGlEffect that composites a rasterised
        // sprite (same geometry as the editor preview) over the
        // frame after crop / grade / transform.
        val textOverlays: List<TextOverlay> = emptyList(),
        // Trimming start and end offsets in milliseconds.
        // Media3 Transformer uses ClippingConfiguration to cleanly trim the video
        // at frame-accurate boundaries without unnecessary re-encoding.
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L
    )

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
                val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
                // Crop first so the colour grade and speed/keyframe
                // passes below operate on the cropped frame.
                config.cropRect?.let { r ->
                    VideoCropGlEffect.fromRect(r.left, r.top, r.right, r.bottom)
                        ?.let { videoEffects.add(it) }
                }
                if (config.filterPreset != null && config.filterIntensity > 0f) {
                    videoEffects.add(LutFilterGlEffect(context, config.filterPreset, config.filterIntensity))
                }
                if (config.fxPreset != null && config.fxIntensity > 0f) {
                    videoEffects.add(FxGlEffect(config.fxPreset, config.fxIntensity))
                }
                if (config.clipSpeed > 0f && config.clipSpeed != 1f) {
                    // Media3 1.4's setSpeed() is mutually exclusive
                    // with custom video effects, so we use the
                    // interlinked speed-change effect instead — it
                    // lives inside the same Effects list as the LUT
                    // filter, maintaining A/V sync without
                    // sacrificing the colour grade.
                    val constantProvider = object : androidx.media3.common.audio.SpeedProvider {
                        override fun getSpeed(timeUs: Long): Float = config.clipSpeed
                        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long =
                            androidx.media3.common.C.TIME_UNSET
                    }
                    val speedPair =
                        androidx.media3.transformer.Effects.createExperimentalSpeedChangingEffect(constantProvider)
                    audioProcessors.add(speedPair.first)
                    videoEffects.add(speedPair.second)
                }
                if (!config.keyframes.isEmpty()) {
                    // Keyframe animation: same MatrixTransformation
                    // used by the live preview, so the export bakes
                    // exactly what the user saw.
                    videoEffects.add(
                        com.apexstudio.app.data.animation.KeyframeAnimationEffect(
                            trackProvider = { config.keyframes }
                        ).buildEffects().first()
                    )
                }
                // Captions last, so they sit on top of the colour
                // grade / FX / transform instead of being re-graded.
                val captionOverlays = config.textOverlays.filter { it.text.isNotBlank() }
                if (captionOverlays.isNotEmpty()) {
                    val aspect = queryAspectRatio(inputUri)
                    captionOverlays.forEach { overlay ->
                        videoEffects.add(TextOverlayGlEffect(context, overlay, aspect))
                    }
                }
                val editedMediaItem = EditedMediaItem.Builder(inputMediaItem)
                    .setEffects(Effects(audioProcessors, videoEffects))
                    .build()

                var progressJob: kotlinx.coroutines.Job? = null
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

                val transformer = Transformer.Builder(context)
                    .addListener(transformerListener)
                    .build()
                this@ExportEngine.transformer = transformer

                val progressHolder = androidx.media3.transformer.ProgressHolder()
                progressJob = launch {
                    while (_exportState.value.isExporting && this@ExportEngine.transformer != null) {
                        val pState = this@ExportEngine.transformer?.getProgress(progressHolder)
                        if (pState == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val p = (progressHolder.progress / 100f).coerceIn(0f, 0.99f)
                            mainHandler.post {
                                if (_exportState.value.isExporting) {
                                    _exportState.value = _exportState.value.copy(progress = p)
                                }
                            }
                        }
                        kotlinx.coroutines.delay(150)
                    }
                }

                transformer.start(editedMediaItem, outputFile.absolutePath)
            } catch (e: Exception) {
                Log.e("ExportEngine", "Export failed", e)
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
     * Best-effort source aspect ratio, used to size caption sprites so
     * normalised text coordinates map 1:1 onto the exported frame.
     * Falls back to 16:9 when the metadata can't be read.
     */
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
        transformer?.cancel()
        transformer = null
        _exportState.value = ExportProgressState()
    }

    fun release() {
        cancelExport()
    }
}
