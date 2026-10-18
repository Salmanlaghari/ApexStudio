package com.apexstudio.app.presentation.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import com.apexstudio.app.data.background.BackgroundReplaceEngine
import com.apexstudio.app.domain.model.BackgroundReplaceSettings
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.util.UUID

/**
 * Background replace controller (PR-G): MediaPipe person
 * segmentation on a selected VIDEO clip.
 *
 * The engine does its expensive work on its own worker dispatcher;
 * all editor/project mutations and UI-state publication happen on Main.
 * Results are committed only while the request is still current and the
 * originating project/clip version is unchanged.
 */
data class BackgroundUiState(
    val phase: BackgroundPhase = BackgroundPhase.IDLE,
    val progress01: Float = 0f,
    val message: String = "",
    /** Picked replacement image for IMAGE mode (pending Apply). */
    val imageUri: String? = null
)

enum class BackgroundPhase { IDLE, NEED_DOWNLOAD, PROCESSING, DONE, FAILED }

/** Immutable identity of the project/clip input which an async result was built from. */
internal data class BackgroundApplyVersion(
    val projectId: String,
    val clipId: String,
    val uri: String,
    val backgroundReplace: BackgroundReplaceSettings?
)

/** Pure commit policy: a removed, replaced, restored, or otherwise changed clip is stale. */
internal fun backgroundApplyCanCommit(
    expected: BackgroundApplyVersion,
    current: BackgroundApplyVersion?
): Boolean = current == expected

// BackgroundUiState lives on EditorViewModel (instance-scoped).

/** Open the Background panel, seeding the pending image from the clip's settings. */
fun EditorViewModel.openBackgroundPanel() {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == _state.value.selectedClipId }
        ?: _state.value.project?.clips?.firstOrNull()
    _backgroundUi.value = _backgroundUi.value.copy(
        imageUri = clip?.backgroundReplace?.imageUri
    )
    _state.update { it.copy(backgroundPanelOpen = true) }
}

fun EditorViewModel.closeBackgroundPanel() {
    _state.update { it.copy(backgroundPanelOpen = false) }
}

/** Store the picked replacement image (IMAGE mode) pending Apply. */
fun EditorViewModel.setBackgroundReplaceImageUri(uri: String?) {
    _backgroundUi.value = _backgroundUi.value.copy(imageUri = uri)
}

/** Cancel in-flight processing. Its result is no longer permitted to update editor state. */
fun EditorViewModel.cancelBackgroundReplace() {
    val job = backgroundJob
    backgroundJob = null
    job?.cancel()
    _backgroundUi.value = _backgroundUi.value.copy(
        phase = BackgroundPhase.IDLE, progress01 = 0f, message = "Cancelled"
    )
}

/** Compatibility action for older UI: prepare the bundled model locally without network access. */
fun EditorViewModel.downloadBackgroundModel() {
    val ctx = context ?: return
    val preparingMessage = "Preparing the bundled segmentation model offline…"
    _backgroundUi.value = _backgroundUi.value.copy(
        phase = BackgroundPhase.PROCESSING,
        message = preparingMessage
    )
    // isModelReady switches to IO internally; stay on Main for state publication.
    viewModelScope.launch {
        val ready = BackgroundReplaceEngine.isModelReady(ctx.applicationContext)
        _backgroundUi.update { current ->
            if (current.message != preparingMessage) current else if (ready) {
                current.copy(phase = BackgroundPhase.IDLE, progress01 = 0f, message = "Offline model ready.")
            } else {
                current.copy(
                    phase = BackgroundPhase.FAILED, progress01 = 0f,
                    message = "Bundled person-segmentation model is unavailable."
                )
            }
        }
    }
}

/**
 * Apply [settings] to [clipId]'s video. An identical re-apply reuses the
 * cache; results are rejected if the originating project or clip changes
 * while processing.
 */
fun EditorViewModel.applyBackgroundReplace(
    clipId: String,
    settings: BackgroundReplaceSettings
) {
    val ctx = context ?: run {
        _backgroundUi.value = _backgroundUi.value.copy(
            phase = BackgroundPhase.FAILED, message = "No context"
        )
        return
    }
    val project = _state.value.project
    val clip = project?.clips?.firstOrNull { it.id == clipId } ?: run {
        _backgroundUi.value = _backgroundUi.value.copy(
            phase = BackgroundPhase.FAILED, message = "Clip not found"
        )
        return
    }
    if (clip.type != ClipType.VIDEO) {
        _backgroundUi.value = _backgroundUi.value.copy(
            phase = BackgroundPhase.FAILED,
            message = "Background replace works on video clips."
        )
        return
    }
    if (!GlobalModerationEnforcement.readiness.approved) {
        _backgroundUi.value = _backgroundUi.value.copy(
            phase = BackgroundPhase.FAILED,
            progress01 = 0f,
            message = GlobalModerationEnforcement.statusMessage(ProtectedAiAction.BACKGROUND_REPLACEMENT),
        )
        return
    }
    if (backgroundJob?.isActive == true) return // Ignore duplicate Apply taps.

    backgroundJob?.cancel()
    val version = BackgroundApplyVersion(
        projectId = project.id,
        clipId = clip.id,
        uri = clip.uri,
        backgroundReplace = clip.backgroundReplace
    )
    val sourceUri = clip.backgroundReplace?.sourceUri?.takeIf { it.isNotBlank() } ?: clip.uri
    // Capture pending UI input synchronously. A later panel edit cannot
    // silently change the settings of this already-started request.
    val effective = settings.sanitized().copy(
        sourceUri = sourceUri,
        imageUri = _backgroundUi.value.imageUri ?: settings.imageUri
    )
    val ctxApp = ctx.applicationContext

    _backgroundUi.value = _backgroundUi.value.copy(
        phase = BackgroundPhase.PROCESSING, progress01 = 0f,
        message = "Checking AI model…"
    )

    // LAZY ensures backgroundJob is assigned before this coroutine can run.
    val job = viewModelScope.launch(Dispatchers.Main.immediate, start = kotlinx.coroutines.CoroutineStart.LAZY) {
        val requestJob = coroutineContext[Job] ?: return@launch
        try {
            val ready = BackgroundReplaceEngine.isModelReady(ctxApp)
            if (!isCurrentBackgroundJob(requestJob)) return@launch
            if (!ready) {
                publishBackgroundState(requestJob) {
                    it.copy(phase = BackgroundPhase.FAILED,
                        message = "Bundled person-segmentation model is unavailable.")
                }
                return@launch
            }
            if (effective.mode == com.apexstudio.app.domain.model.BackgroundReplaceMode.IMAGE &&
                effective.imageUri.isNullOrBlank()
            ) {
                publishBackgroundState(requestJob) {
                    it.copy(phase = BackgroundPhase.FAILED, message = "Pick a background image first.")
                }
                return@launch
            }

            var outputFile = BackgroundReplaceEngine.outputFileFor(ctxApp, clipId, effective)
            // Never overwrite a path that may be held by undo/history. A
            // valid deterministic cache entry is reused; a damaged one is
            // left intact and this run gets its own output path.
            if (outputFile.exists() && outputFile.length() <= 0L) {
                outputFile = File(
                    outputFile.parentFile,
                    "${outputFile.nameWithoutExtension}_${UUID.randomUUID()}.mp4"
                )
            }
            if (outputFile.exists() && outputFile.length() > 0L) {
                finishBackgroundApply(requestJob, version, effective, outputFile)
                return@launch
            }

            publishBackgroundState(requestJob) {
                it.copy(phase = BackgroundPhase.PROCESSING, progress01 = 0f,
                    message = "Segmenting person… (on-device AI)")
            }
            val processed = BackgroundReplaceEngine.process(
                ctxApp,
                sourceUri,
                effective,
                outputFile
            ) { progress ->
                // Progress originates on the engine worker. Dispatch it
                // to Main and re-check request identity when it executes.
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    publishBackgroundState(requestJob) {
                        it.copy(phase = BackgroundPhase.PROCESSING,
                            progress01 = progress,
                            message = "Processing ${(progress * 100).toInt()}%…")
                    }
                }
            }
            finishBackgroundApply(requestJob, version, effective, processed)
        } catch (e: CancellationException) {
            // Cancellation is expected; the cancelling action owns the UI status.
            throw e
        } catch (e: Exception) {
            Log.e("BgReplaceCtrl", "Background replace failed", e)
            publishBackgroundState(requestJob) {
                it.copy(phase = BackgroundPhase.FAILED,
                    message = "Background replace failed: ${e.message?.take(100)}")
            }
        } finally {
            if (backgroundJob === requestJob) backgroundJob = null
        }
    }
    backgroundJob = job
    job.start()
}

private fun EditorViewModel.isCurrentBackgroundJob(job: Job): Boolean =
    backgroundJob === job && job.isActive

private fun EditorViewModel.publishBackgroundState(
    job: Job,
    transform: (BackgroundUiState) -> BackgroundUiState
) {
    if (!isCurrentBackgroundJob(job)) return
    _backgroundUi.update { current ->
        if (isCurrentBackgroundJob(job)) transform(current) else current
    }
}

/** Commit only on Main and only if the originating clip version is still live. */
private suspend fun EditorViewModel.finishBackgroundApply(
    job: Job,
    expected: BackgroundApplyVersion,
    effective: BackgroundReplaceSettings,
    processed: File
) = withContext(Dispatchers.Main.immediate) {
    if (!isCurrentBackgroundJob(job)) return@withContext
    val project = _state.value.project
    val clip = project?.clips?.firstOrNull { it.id == expected.clipId }
    val currentVersion = if (project != null && clip != null) {
        BackgroundApplyVersion(project.id, clip.id, clip.uri, clip.backgroundReplace)
    } else null
    if (!backgroundApplyCanCommit(expected, currentVersion)) {
        backgroundJob = null
        _backgroundUi.value = _backgroundUi.value.copy(
            phase = BackgroundPhase.IDLE, progress01 = 0f,
            message = "Clip or project changed; background result discarded."
        )
        return@withContext
    }

    pushUndo()
    updateClip(expected.clipId) { current ->
        current.copy(
            uri = Uri.fromFile(processed).toString(),
            backgroundReplace = effective,
            faceCropTrack = null,
        )
    }
    // Do not delete the previous processed output. Undo/history snapshots
    // store clip URIs and may still refer to it.
    backgroundJob = null
    _backgroundUi.value = _backgroundUi.value.copy(
        phase = BackgroundPhase.DONE, progress01 = 1f,
        message = "Background applied — preview and export show the same video."
    )
}

/** Restore the clip's original background without deleting history-referenced outputs. */
fun EditorViewModel.restoreOriginalBackground(clipId: String) {
    // Invalidate the active request before inspecting/restoring the clip;
    // a late cache hit or engine completion can no longer re-apply it.
    val activeJob = backgroundJob
    backgroundJob = null
    activeJob?.cancel()

    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val settings = clip.backgroundReplace ?: run {
        _backgroundUi.value = _backgroundUi.value.copy(
            phase = BackgroundPhase.IDLE, progress01 = 0f,
            message = "Already showing the original background."
        )
        return
    }
    pushUndo()
    updateClip(clipId) { current ->
        current.copy(uri = settings.sourceUri, backgroundReplace = null, faceCropTrack = null)
    }
    _backgroundUi.value = _backgroundUi.value.copy(
        phase = BackgroundPhase.IDLE, progress01 = 0f,
        message = "Original background restored."
    )
}

fun EditorViewModel.resetBackgroundUi() {
    val activeJob = backgroundJob
    backgroundJob = null
    activeJob?.cancel()
    _backgroundUi.value = BackgroundUiState()
}
