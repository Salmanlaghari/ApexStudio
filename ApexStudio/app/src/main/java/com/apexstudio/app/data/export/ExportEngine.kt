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
        val filterIntensity: Float = 1f
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

                val videoEffects = buildList {
                    if (config.filterPreset != null && config.filterIntensity > 0f) {
                        add(LutFilterGlEffect(context, config.filterPreset, config.filterIntensity))
                    }
                }
                val editedMediaItem = EditedMediaItem.Builder(inputMediaItem)
                    .setEffects(Effects(emptyList(), videoEffects))
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
