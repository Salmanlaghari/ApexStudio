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
import com.apexstudio.app.data.filter.FilterPreset
import com.apexstudio.app.data.filter.LutFilterEngine
import com.apexstudio.app.data.filter.LutFilterGlEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class ExportEngine(private val context: Context) {

    private val _exportState = MutableStateFlow(ExportProgressState())
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
        // Speed ramping: when set, applied to the exported clip via
        // EditedMediaItem.setSpeed so the time-lapse / slow-mo is
        // baked into the hardware-encoded output.
        val clipSpeed: Float = 1f
    )

    fun startExport(
        inputUri: String,
        config: ExportConfig,
        outputFileName: String = "apex_studio_export.mp4"
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _exportState.value = ExportProgressState(isExporting = true, progress = 0f)

                val inputMediaItem = MediaItem.fromUri(Uri.parse(inputUri))

                val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
                outputDir.mkdirs()
                val outputFile = File(outputDir, outputFileName)

                val videoEffects = mutableListOf<androidx.media3.common.Effect>()
                val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
                if (config.filterPreset != null && config.filterIntensity > 0f) {
                    videoEffects.add(LutFilterGlEffect(context, config.filterPreset, config.filterIntensity))
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
                val editedMediaItem = EditedMediaItem.Builder(inputMediaItem)
                    .setEffects(Effects(audioProcessors, videoEffects))
                    .build()

                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
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
                            mainHandler.post {
                                _exportState.value = ExportProgressState(
                                    isExporting = false,
                                    progress = 0f,
                                    error = exportException.message ?: "Export failed"
                                )
                            }
                        }
                    })
                    .build()
                this@ExportEngine.transformer = transformer
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

    fun cancelExport() {
        transformer?.cancel()
        transformer = null
        _exportState.value = ExportProgressState()
    }

    fun release() {
        cancelExport()
    }
}
