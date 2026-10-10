package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import com.apexstudio.app.data.captions.VoskCaptionEngine
import com.apexstudio.app.domain.model.TextOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Auto-captions controller (Phase 4): offline speech-to-text via Vosk
 * (Apache-2.0, https://github.com/alphacep/vosk-api).
 *
 * Flow: user taps "Auto Captions" on a video clip -> model downloads once
 * (~40MB, progress shown) -> audio transcribed on-device with word
 * timestamps -> words grouped into caption segments -> each segment becomes
 * a timed [TextOverlay] (startMs/endMs) on the clip, so preview AND export
 * render them via the existing text pipeline. No dead buttons: every state
 * (downloading / transcribing / done / failed) is surfaced in the UI.
 */
class CaptionUiState(
    val phase: CaptionPhase = CaptionPhase.IDLE,
    val progress01: Float = 0f,
    val message: String = "",
    val segmentsAdded: Int = 0
)

enum class CaptionPhase { IDLE, DOWNLOADING_MODEL, TRANSCRIBING, DONE, FAILED }

private val _captionUi = MutableStateFlow(CaptionUiState())
val EditorViewModel.captionUi: StateFlow<CaptionUiState>
    get() = _captionUi.asStateFlow()

/** True once the Vosk model is on disk. */
fun EditorViewModel.isCaptionModelReady(): Boolean {
    val ctx = context ?: return false
    return VoskCaptionEngine.modelDir(ctx) != null
}

/**
 * Generates timed captions for [clipId]'s audio. Safe to call repeatedly;
 * replaces previously auto-generated captions on the same clip.
 */
fun EditorViewModel.generateAutoCaptions(clipId: String) {
    val ctx = context ?: run {
        _captionUi.value = CaptionUiState(CaptionPhase.FAILED, message = "No context")
        return
    }
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: run {
        _captionUi.value = CaptionUiState(CaptionPhase.FAILED, message = "Clip not found")
        return
    }
    if (_captionUi.value.phase == CaptionPhase.DOWNLOADING_MODEL ||
        _captionUi.value.phase == CaptionPhase.TRANSCRIBING
    ) return // already running

    viewModelScope.launch(Dispatchers.IO) {
        try {
            // 1. Model (one-time ~40MB download).
            if (VoskCaptionEngine.modelDir(ctx) == null) {
                _captionUi.value = CaptionUiState(
                    CaptionPhase.DOWNLOADING_MODEL, 0f,
                    "Downloading speech model (40MB, once)…"
                )
                val dir = VoskCaptionEngine.ensureModel(ctx) { p ->
                    _captionUi.value = CaptionUiState(
                        CaptionPhase.DOWNLOADING_MODEL, p,
                        "Downloading speech model ${(p * 100).toInt()}%…"
                    )
                }
                if (dir == null) {
                    _captionUi.value = CaptionUiState(
                        CaptionPhase.FAILED,
                        message = "Model download failed — check internet and retry."
                    )
                    return@launch
                }
            }
            // 2. Transcribe.
            _captionUi.value = CaptionUiState(
                CaptionPhase.TRANSCRIBING, 0f, "Listening… (on-device)"
            )
            val words = VoskCaptionEngine.transcribe(ctx, clip.uri) { p ->
                _captionUi.value = CaptionUiState(
                    CaptionPhase.TRANSCRIBING, p,
                    "Transcribing ${(p * 100).toInt()}%…"
                )
            }
            if (words.isEmpty()) {
                _captionUi.value = CaptionUiState(
                    CaptionPhase.FAILED, message = "No speech detected in this clip."
                )
                return@launch
            }
            // 3. Group into segments -> timed text overlays.
            val segments = VoskCaptionEngine.toSegments(words)
            val clipStartMs = clip.trimStartMs
            val overlays = segments.map { seg ->
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
            _captionUi.value = CaptionUiState(
                CaptionPhase.DONE, 1f,
                "Added ${overlays.size} captions",
                segmentsAdded = overlays.size
            )
        } catch (e: Exception) {
            Log.e("CaptionCtrl", "Auto-captions failed", e)
            _captionUi.value = CaptionUiState(
                CaptionPhase.FAILED,
                message = "Captions failed: ${e.message?.take(80)}"
            )
        }
    }
}

fun EditorViewModel.resetCaptionUi() {
    _captionUi.value = CaptionUiState()
}

/** Removes auto-generated captions from a clip (keeps manual text). */
fun EditorViewModel.clearAutoCaptions(clipId: String) {
    updateClip(clipId) { c ->
        c.copy(textOverlays = c.textOverlays.filter { it.presetId != "caption_auto" })
    }
    resetCaptionUi()
}
