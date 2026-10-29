package com.apexstudio.app.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.apexstudio.app.data.video.VideoUpscaleEngine
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Progress for the existing-project-video enhancement action. */
data class VideoUpscaleUiState(
    val processing: Boolean = false,
    val progress01: Float = 0f,
    val message: String = "",
    val outputUri: String? = null
)

/** Full-snapshot commit rule: selection, project, and every clip field must still match. */
internal fun videoUpscaleCanCommit(
    expectedProjectId: String,
    expectedClip: MediaClip,
    currentProjectId: String?,
    selectedClipId: String?,
    currentClip: MediaClip?
): Boolean = currentProjectId == expectedProjectId && selectedClipId == expectedClip.id &&
    currentClip != null && currentClip == expectedClip && currentClip.type == ClipType.VIDEO && currentClip.trackIndex == 0

/**
 * Start offline learned x3 upscaling of the exact selected V1 VIDEO snapshot. The processed
 * file replaces that clip's media URI, so preview and export consume the same rendered pixels.
 * Trim values are intentionally left untouched; the engine renders the whole source and preserves
 * its timestamp duration, original audio and source frame presentation timestamps.
 */
fun EditorViewModel.applyExistingVideoUpscale(
    projectId: String,
    expectedClip: MediaClip
) {
    val context = context?.applicationContext ?: run {
            _videoUpscaleUi.value = VideoUpscaleUiState(message = "The editor is not ready. Try again.")
        return
    }
    synchronized(this) {
        val project = _state.value.project
        val current = project?.clips?.firstOrNull { it.id == expectedClip.id }
        if (!videoUpscaleCanCommit(projectId, expectedClip, project?.id, _state.value.selectedClipId, current)) {
            _videoUpscaleUi.value = VideoUpscaleUiState(message = "Choose a video on the main track first.")
            return
        }
        if (expectedClip.uri.isBlank() || expectedClip.durationMs <= 0L || expectedClip.trimStartMs < 0L ||
            expectedClip.trimEndMs <= expectedClip.trimStartMs || expectedClip.trimEndMs > expectedClip.durationMs) {
            _videoUpscaleUi.value = VideoUpscaleUiState(message = "This video cannot be opened. Choose another video.")
            return
        }
        VideoUpscaleEngine.sourceDurationBlockReason(expectedClip.durationMs)?.let { reason ->
            _videoUpscaleUi.value = VideoUpscaleUiState(message = reason)
            return
        }
        videoUpscaleJob?.cancel()
        val outputDir = context.getExternalFilesDir("video_upscale") ?: File(context.filesDir, "video_upscale")
        val output = File(outputDir, "espcn_${UUID.randomUUID()}.mp4")
        lateinit var request: Job
        request = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var rendered: File? = null
            var committed = false
            publishVideoUpscale(request) { VideoUpscaleUiState(processing = true, message = "Preparing your video…") }
            try {
                rendered = VideoUpscaleEngine.process(
                    context = context,
                    sourceUri = expectedClip.uri,
                    outputFile = output,
                    onProgress = { progress ->
                        publishVideoUpscale(request) {
                            it.copy(progress01 = progress.coerceIn(0f, 1f), message = "Enhancing video…")
                        }
                    },
                    allowPublish = {
                        synchronized(this@applyExistingVideoUpscale) {
                            val latest = _state.value
                            val projectNow = latest.project
                            val clipNow = projectNow?.clips?.firstOrNull { it.id == expectedClip.id }
                            videoUpscaleJob === request && request.isActive &&
                                videoUpscaleCanCommit(projectId, expectedClip, projectNow?.id, latest.selectedClipId, clipNow)
                        }
                    }
                )
                withContext(Dispatchers.Main.immediate) {
                    synchronized(this@applyExistingVideoUpscale) {
                        val latest = _state.value
                        val currentProject = latest.project
                        val currentClip = currentProject?.clips?.firstOrNull { it.id == expectedClip.id }
                        if (videoUpscaleJob !== request || !request.isActive) return@synchronized
                        if (!videoUpscaleCanCommit(projectId, expectedClip, currentProject?.id, latest.selectedClipId, currentClip)) {
                            _videoUpscaleUi.value = VideoUpscaleUiState(message = "The selected video changed. Nothing was changed.")
                            return@synchronized
                        }
                        pushUndo()
                        updateClip(expectedClip.id) { clip -> clip.copy(uri = rendered!!.toURI().toString()) }
                        committed = true
                        _videoUpscaleUi.value = VideoUpscaleUiState(
                            progress01 = 1f,
                            message = "Enhanced video ready.",
                            outputUri = rendered!!.toURI().toString()
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                publishVideoUpscale(request) {
                    VideoUpscaleUiState(message = videoUpscaleFailureMessage(failure))
                }
            } finally {
                if (!committed) {
                    rendered?.delete()
                    output.delete() // also covers cancellation after atomic rename but before return assignment.
                }
                if (videoUpscaleJob === request) {
                    videoUpscaleJob = null
                    if (!committed && request.isCancelled) {
                        _videoUpscaleUi.value = VideoUpscaleUiState(message = "Enhancement cancelled.")
                    }
                }
            }
        }
        videoUpscaleJob = request
        _videoUpscaleUi.value = VideoUpscaleUiState(processing = true, message = "Starting enhancement…")
        request.start()
    }
}

/** Cancel the active request; incomplete/stale output files are removed before returning. */
fun EditorViewModel.cancelExistingVideoUpscale() {
    val active = videoUpscaleJob
    videoUpscaleJob = null
    active?.cancel()
    _videoUpscaleUi.value = VideoUpscaleUiState(message = "Enhancement cancelled.")
}

internal fun videoUpscaleFailureMessage(failure: Throwable): String {
    val reason = failure.message.orEmpty().lowercase()
    return when {
        "audio" in reason || "aac" in reason -> "Choose a silent video or one with AAC audio."
        "duration" in reason || "30 seconds" in reason -> "Choose a video that is 30 seconds or shorter."
        "frame rate" in reason || "frames per second" in reason || "fps" in reason ->
            "This video is too fast. Choose a video at 30 fps or less."
        "640" in reason || "dimension" in reason || "resolution" in reason ->
            "This video's size isn't supported. Choose up to 640×360 (portrait 360×640)."
        "space" in reason || "storage" in reason -> "Free up storage space, then try again."
        else -> "Couldn't enhance this video. Try another video."
    }
}

private fun EditorViewModel.publishVideoUpscale(
    request: Job,
    transform: (VideoUpscaleUiState) -> VideoUpscaleUiState
) {
    if (videoUpscaleJob !== request || !request.isActive) return
    _videoUpscaleUi.value = transform(_videoUpscaleUi.value)
}
