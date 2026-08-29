package com.apexstudio.app.data.export

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.TransformationResult
import androidx.media3.transformer.Transformer
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

    data class ExportProgressState(
        val isExporting: Boolean = false,
        val progress: Float = 0f,
        val outputUri: String? = null,
        val error: String? = null
    )

    data class ExportConfig(
        val resolution: String = "1080p",
        val fps: Int = 60,
        val quality: String = "high"
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

                val request = buildTransformationRequest(config)

                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onTransformationCompleted(
                            mediaItem: MediaItem,
                            transformationResult: TransformationResult
                        ) {
                            mainHandler.post {
                                _exportState.value = ExportProgressState(
                                    isExporting = false,
                                    progress = 1f,
                                    outputUri = Uri.fromFile(outputFile).toString()
                                )
                            }
                        }

                        override fun onTransformationError(
                            mediaItem: MediaItem,
                            transformationResult: TransformationResult,
                            exception: Exception
                        ) {
                            mainHandler.post {
                                _exportState.value = ExportProgressState(
                                    isExporting = false,
                                    progress = 0f,
                                    error = exception.message ?: "Export failed"
                                )
                            }
                        }
                    })
                    .build()
                this@ExportEngine.transformer = transformer
                transformer.start(inputMediaItem, outputFile.absolutePath)
            } catch (e: Exception) {
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

    private fun buildTransformationRequest(config: ExportConfig): TransformationRequest {
        val videoMime = when (config.resolution) {
            "4K", "8K" -> MimeTypes.VIDEO_H265
            else -> MimeTypes.VIDEO_H264
        }
        return TransformationRequest.Builder()
            .setVideoMimeType(videoMime)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()
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
