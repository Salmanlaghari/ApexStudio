package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.data.background.BackgroundReplaceEngine
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.domain.model.BackgroundReplaceSettings
import com.apexstudio.app.domain.model.ClipType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Background replace controller (PR-G): MediaPipe person
 * segmentation on a selected VIDEO clip.
 *
 * Flow: user picks a treatment in the Background panel -> Apply ->
 * if the segmentation model pack (~244 KB) isn't downloaded, the
 * panel shows the download prompt instead of silently doing
 * nothing -> the engine pre-renders a processed video (same
 * duration / resolution, audio passed through) off the UI thread
 * with cancellable progress -> the clip's [uri] swaps to the
 * processed file and [backgroundReplace] records the settings +
 * the original [sourceUri]. Preview and export both read the
 * clip's [uri], so they show the identical pixels by construction
 * — there is no second rendering path that could drift.
 *
 * Re-applying always starts from [sourceUri] (never from an
 * already-processed file), and "Original" restores [sourceUri]
 * and deletes the processed file.
 */
data class BackgroundUiState(
    val phase: BackgroundPhase = BackgroundPhase.IDLE,
    val progress01: Float = 0f,
    val message: String = "",
    /** Picked replacement image for IMAGE mode (pending Apply). */
    val imageUri: String? = null
)

enum class BackgroundPhase { IDLE, NEED_DOWNLOAD, PROCESSING, DONE, FAILED }

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

/** Cancel in-flight processing. The partial output is deleted; the clip is untouched. */
fun EditorViewModel.cancelBackgroundReplace() {
    backgroundJob?.cancel()
    backgroundJob = null
    _backgroundUi.value = _backgroundUi.value.copy(
        phase = BackgroundPhase.IDLE, progress01 = 0f, message = "Cancelled"
    )
}

/** Start (or resume) downloading the segmentation model pack. */
fun EditorViewModel.downloadBackgroundModel() {
    val ctx = context ?: return
    PackManager.download(ctx.applicationContext, BackgroundReplaceEngine.PACK_ID)
    _backgroundUi.value = _backgroundUi.value.copy(
        phase = BackgroundPhase.NEED_DOWNLOAD,
        message = "Downloading the segmentation model (~244 KB, one-time)…"
    )
}

/**
 * Apply [settings] to [clipId]'s video. Safe to call repeatedly:
 * an identical re-apply reuses the cached output file instead of
 * re-processing; changed settings process from the original.
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
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: run {
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
    if (_backgroundUi.value.phase == BackgroundPhase.PROCESSING) return // already running
    backgroundJob?.cancel()
    // Flip the phase SYNCHRONOUSLY so a second concurrent call
    // (e.g. a double-tap on Apply) sees PROCESSING and bails
    // instead of starting a duplicate job.
    _backgroundUi.value = _backgroundUi.value.copy(
        phase = BackgroundPhase.PROCESSING, progress01 = 0f,
        message = "Checking AI model…"
    )

    backgroundJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            // 1. Model pack gating: PackManager is the single source of
            //    truth for heavy assets (PR-C). refreshSync first so the
            //    check sees the latest catalog/registry.
            PackManager.refreshSync(ctx.applicationContext)
            val ready = BackgroundReplaceEngine.isModelReady(ctx.applicationContext)
            if (!ready) {
                _backgroundUi.value = _backgroundUi.value.copy(
                    phase = BackgroundPhase.NEED_DOWNLOAD,
                    message = "The person-segmentation model (~244 KB, one-time download) is needed for background replace."
                )
                return@launch
            }
            // 2. Resolve input: always the ORIGINAL, never a
            //    previously processed file.
            val sourceUri = clip.backgroundReplace?.sourceUri?.takeIf { it.isNotBlank() }
                ?: clip.uri
            val effective = settings.sanitized().copy(
                sourceUri = sourceUri,
                // IMAGE mode reads the panel's picked image; a stale
                // imageUri from a previous apply must not win over
                // the freshly picked one.
                imageUri = _backgroundUi.value.imageUri ?: settings.imageUri
            )
            if (effective.mode == com.apexstudio.app.domain.model.BackgroundReplaceMode.IMAGE &&
                effective.imageUri.isNullOrBlank()
            ) {
                _backgroundUi.value = _backgroundUi.value.copy(
                    phase = BackgroundPhase.FAILED,
                    message = "Pick a background image first."
                )
                return@launch
            }
            // 3. Cache hit: identical settings reuse the output file.
            val outputFile = BackgroundReplaceEngine.outputFileFor(
                ctx.applicationContext, clipId, effective
            )
            if (outputFile.exists() && outputFile.length() > 0L) {
                finishBackgroundApply(clipId, clip, effective, outputFile)
                return@launch
            }
            // 4. Process (cancellable; progress per frame).
            _backgroundUi.value = _backgroundUi.value.copy(
                phase = BackgroundPhase.PROCESSING, progress01 = 0f,
                message = "Segmenting person… (on-device AI)"
            )
            val processed = BackgroundReplaceEngine.process(
                ctx.applicationContext,
                sourceUri,
                effective,
                outputFile
            ) { p ->
                _backgroundUi.value = _backgroundUi.value.copy(
                    phase = BackgroundPhase.PROCESSING, progress01 = p,
                    message = "Processing ${(p * 100).toInt()}%…"
                )
            }
            finishBackgroundApply(clipId, clip, effective, processed)
        } catch (e: CancellationException) {
            // Cancel button: back to idle, no error noise. The
            // engine already deleted its temp file.
            _backgroundUi.value = _backgroundUi.value.copy(
                phase = BackgroundPhase.IDLE, progress01 = 0f, message = "Cancelled"
            )
        } catch (e: Exception) {
            Log.e("BgReplaceCtrl", "Background replace failed", e)
            _backgroundUi.value = _backgroundUi.value.copy(
                phase = BackgroundPhase.FAILED,
                message = "Background replace failed: ${e.message?.take(100)}"
            )
        }
    }
}

/**
 * Commit a processed file to the clip: swap [uri], record the
 * settings + original, delete the previous processed file, and
 * persist. The editor's player reloads on the [uri] change, so
 * the preview shows the processed video immediately — the same
 * file the export will export.
 */
private fun EditorViewModel.finishBackgroundApply(
    clipId: String,
    oldClip: com.apexstudio.app.domain.model.MediaClip,
    effective: BackgroundReplaceSettings,
    processed: java.io.File
) {
    val previousProcessed = oldClip.uri.takeIf { oldClip.backgroundReplace != null }
    updateClip(clipId) { c ->
        c.copy(
            uri = android.net.Uri.fromFile(processed).toString(),
            backgroundReplace = effective
        )
    }
    if (previousProcessed != null && previousProcessed != android.net.Uri.fromFile(processed).toString()) {
        val oldFile = java.io.File(android.net.Uri.parse(previousProcessed).path ?: "")
        BackgroundReplaceEngine.deleteProcessedFile(oldFile)
    }
    _backgroundUi.value = _backgroundUi.value.copy(
        phase = BackgroundPhase.DONE, progress01 = 1f,
        message = "Background applied — preview and export show the same video."
    )
}

/**
 * Restore the clip's original background: [uri] back to
 * [sourceUri], settings cleared, processed file deleted.
 */
fun EditorViewModel.restoreOriginalBackground(clipId: String) {
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    val settings = clip.backgroundReplace ?: run {
        _backgroundUi.value = _backgroundUi.value.copy(
            phase = BackgroundPhase.IDLE, message = "Already showing the original background."
        )
        return
    }
    val processedUri = clip.uri
    updateClip(clipId) { c ->
        c.copy(uri = settings.sourceUri, backgroundReplace = null)
    }
    if (processedUri != settings.sourceUri) {
        val oldFile = java.io.File(android.net.Uri.parse(processedUri).path ?: "")
        BackgroundReplaceEngine.deleteProcessedFile(oldFile)
    }
    _backgroundUi.value = _backgroundUi.value.copy(
        phase = BackgroundPhase.IDLE, progress01 = 0f,
        message = "Original background restored."
    )
}

fun EditorViewModel.resetBackgroundUi() {
    backgroundJob?.cancel()
    backgroundJob = null
    _backgroundUi.value = BackgroundUiState()
}
