package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.data.captions.WhisperCaptionEngine
import com.apexstudio.app.data.packs.download.PackManager
import com.apexstudio.app.domain.model.TextOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Auto-captions controller: on-device Whisper speech-to-text
 * (tiny, multilingual, int8 — via onnxruntime-android).
 *
 * Flow: user taps "Auto Captions" on a video clip -> if the Whisper model
 * files (~103MB in 3 file packs) aren't downloaded, the UI shows a
 * download prompt (routed through PackManager.requirePack /
 * PackManager.download) -> audio is extracted to 16 kHz mono PCM ->
 * transcribed on-device in 30 s chunks with a cancellable progress UI ->
 * each timestamped segment becomes a timed [TextOverlay] (startMs/endMs)
 * on the clip, so preview AND export render them via the existing text
 * pipeline. No dead buttons: every state (need-download / transcribing /
 * done / failed / no-speech) is surfaced in the UI.
 *
 * Language: "auto" (detect) or a whisper code ("ur", "hi", "en", …).
 * Prince's content is Urdu/Hindi songs + vlogs, so those are first-class.
 */
data class CaptionUiState(
    val phase: CaptionPhase = CaptionPhase.IDLE,
    val progress01: Float = 0f,
    val message: String = "",
    val segmentsAdded: Int = 0,
    val language: String = WhisperCaptionEngine.LANG_AUTO
)

enum class CaptionPhase { IDLE, NEED_DOWNLOAD, TRANSCRIBING, DONE, FAILED }

// CaptionUiState lives on EditorViewModel (instance-scoped).

/** Switch the caption language (resets the phase so the user can retry). */
fun EditorViewModel.setCaptionLanguage(code: String) {
    captionJob?.cancel()
    captionJob = null
    _captionUi.value = CaptionUiState(language = code)
}

/** Cancel an in-flight transcription. Never a dead button. */
fun EditorViewModel.cancelAutoCaptions() {
    captionJob?.cancel()
    captionJob = null
    _captionUi.value = _captionUi.value.copy(
        phase = CaptionPhase.IDLE, progress01 = 0f, message = "Cancelled"
    )
}

/**
 * Generates timed captions for [clipId]'s audio. Safe to call repeatedly;
 * replaces previously auto-generated captions on the same clip.
 */
fun EditorViewModel.generateAutoCaptions(clipId: String) {
    val ctx = context ?: run {
        _captionUi.value = _captionUi.value.copy(
            phase = CaptionPhase.FAILED, message = "No context"
        )
        return
    }
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: run {
        _captionUi.value = _captionUi.value.copy(
            phase = CaptionPhase.FAILED, message = "Clip not found"
        )
        return
    }
    if (_captionUi.value.phase == CaptionPhase.TRANSCRIBING) return // already running
    captionJob?.cancel()

    captionJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            val language = _captionUi.value.language
            // 1. Model pack gating: PackManager is the single source of
            //    truth for heavy assets (PR-C). refreshSync first so the
            //    check sees the latest catalog/registry.
            PackManager.refreshSync(ctx.applicationContext)
            _captionUi.value = _captionUi.value.copy(
                phase = CaptionPhase.TRANSCRIBING, progress01 = 0f,
                message = "Checking AI model…"
            )
            val ready = WhisperCaptionEngine.isModelReady(ctx.applicationContext)
            if (!ready) {
                _captionUi.value = _captionUi.value.copy(
                    phase = CaptionPhase.NEED_DOWNLOAD,
                    message = "The Whisper AI model (~103MB in 3 parts, one-time download) is needed for auto-captions."
                )
                return@launch
            }
            // 2. Transcribe (cancellable; progress per 30 s chunk).
            _captionUi.value = _captionUi.value.copy(
                phase = CaptionPhase.TRANSCRIBING, progress01 = 0f,
                message = "Listening… (on-device AI)"
            )
            val segments = WhisperCaptionEngine.transcribe(
                ctx.applicationContext, android.net.Uri.parse(clip.uri), language
            ) { p ->
                _captionUi.value = _captionUi.value.copy(
                    phase = CaptionPhase.TRANSCRIBING, progress01 = p,
                    message = "Transcribing ${(p * 100).toInt()}%…"
                )
            }
            when {
                segments == null -> {
                    _captionUi.value = _captionUi.value.copy(
                        phase = CaptionPhase.FAILED,
                        message = "Couldn't load the AI model or read this clip's audio."
                    )
                    return@launch
                }
                segments.isEmpty() -> {
                    _captionUi.value = _captionUi.value.copy(
                        phase = CaptionPhase.FAILED,
                        message = "No speech detected in this clip."
                    )
                    return@launch
                }
            }
            // 3. Segments -> timed text overlays (existing text pipeline).
            val clipStartMs = clip.trimStartMs
            val overlays = segments!!.map { seg ->
                TextOverlay.of(
                    text = seg.text,
                    x = 0.5f,
                    y = 0.82f, // lower-third, CapCut-style
                    sizeScale = 0.85f,
                    colorArgb = 0xFFFFFFFFL,
                    bgArgb = 0xB3000000L,
                    isBold = true,
                    presetId = "caption_auto"
                ).copy(
                    startMs = (clipStartMs + seg.startMs).coerceAtLeast(0L),
                    endMs = (clipStartMs + seg.endMs).coerceAtLeast(0L)
                )
            }
            // Replace previous auto-captions, keep manual overlays.
            updateClip(clipId) { c ->
                val manual = c.textOverlays.filter { it.presetId != "caption_auto" }
                c.copy(textOverlays = manual + overlays)
            }
            _captionUi.value = _captionUi.value.copy(
                phase = CaptionPhase.DONE, progress01 = 1f,
                message = "Added ${overlays.size} captions",
                segmentsAdded = overlays.size
            )
        } catch (e: CancellationException) {
            // Cancel button: back to idle, no error noise.
            _captionUi.value = _captionUi.value.copy(
                phase = CaptionPhase.IDLE, progress01 = 0f, message = "Cancelled"
            )
        } catch (e: Exception) {
            Log.e("CaptionCtrl", "Auto-captions failed", e)
            _captionUi.value = _captionUi.value.copy(
                phase = CaptionPhase.FAILED,
                message = "Captions failed: ${e.message?.take(80)}"
            )
        }
    }
}

fun EditorViewModel.resetCaptionUi() {
    captionJob?.cancel()
    captionJob = null
    _captionUi.value = CaptionUiState()
}

/** Removes auto-generated captions from a clip (keeps manual text). */
fun EditorViewModel.clearAutoCaptions(clipId: String) {
    updateClip(clipId) { c ->
        c.copy(textOverlays = c.textOverlays.filter { it.presetId != "caption_auto" })
    }
    resetCaptionUi()
}
