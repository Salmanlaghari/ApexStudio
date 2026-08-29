package com.apexstudio.app.data.export

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.TransformerFactory
import com.google.common.util.concurrent.ListenableFuture
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
    private var player: ExoPlayer? = null
    private var transformerFuture: ListenableFuture<Uri>? = null

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

                val inputMediaItem = MediaItem.fromUri(android.net.Uri.parse(inputUri))

                player = ExoPlayer.Builder(context).build().also { it.setMediaItem(inputMediaItem); it.prepare() }

                val transformationRequest = buildTransformationRequest(config)

                val outputDir = File(context.getExternalFilesDir(null), "ApexStudio_Exports")
                outputDir.mkdirs()
                val outputFile = File(outputDir, outputFileName)
                val outputUri = Uri.fromFile(outputFile)

                transformer = TransformerFactory.getInstance(context)
                    .createTransformer(
                        player,
                        transformationRequest,
                        outputUri
                    )

                transformerFuture = transformer?.future

                transformerFuture?.addListener({
                    try {
                        val resultUri = transformerFuture?.get()
                        _exportState.value = ExportProgressState(
                            isExporting = false,
                            progress = 1f,
                            outputUri = resultUri?.toString()
                        )
                        player?.release()
                    } catch (e: Exception) {
                        _exportState.value = ExportProgressState(
                            isExporting = false,
                            progress = 0f,
                            error = e.message ?: "Export failed"
                        )
                        player?.release()
                    }
                }, Dispatchers.Main)

                transformer?.addListener({
                    val progress = transformer?.transformationProgress
                    if (progress != null) {
                        _exportState.value = ExportProgressState(
                            isExporting = true,
                            progress = progress
                        )
                    }
                }, Dispatchers.Main)

            } catch (e: Exception) {
                _exportState.value = ExportProgressState(
                    isExporting = false,
                    progress = 0f,
                    error = e.message ?: "Export failed"
                )
            }
        }
    }

    private fun buildTransformationRequest(config: ExportConfig): TransformationRequest {
        val width = when (config.resolution) {
            "4K" -> 3840
            "8K" -> 7680
            else -> 1920
        }
        val height = when (config.resolution) {
            "4K" -> 2160
            "8K" -> 4320
            else -> 1080
        }
        return TransformationRequest.Builder()
            .setVideoResolution(width, height)
            .setVideoFrameRate(config.fps)
            .setVideoBitRate(when (config.quality) {
                "high" -> 20_000_000
                "medium" -> 10_000_000
                else -> 5_000_000
            })
            .build()
    }

    fun cancelExport() {
        transformer?.cancel()
        player?.release()
        _exportState.value = ExportProgressState()
    }

    fun release() {
        cancelExport()
        transformer = null
    }
}
