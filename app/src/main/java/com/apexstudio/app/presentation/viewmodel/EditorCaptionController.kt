package com.apexstudio.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apexstudio.app.ai.safety.GlobalModerationEnforcement
import com.apexstudio.app.ai.safety.ProtectedAiAction
import com.apexstudio.app.data.captions.WhisperCaptionEngine
import com.apexstudio.app.ai.subtitles.EnglishWordAlignment
import com.apexstudio.app.ai.subtitles.TimingPrecision
import com.apexstudio.app.ai.subtitles.WordCue
import com.apexstudio.app.domain.model.ClipTimeMapping
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.TextOverlay
import com.apexstudio.app.domain.model.TextOverlayKaraoke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Whisper provides phrase times. Optional English CTC alignment provides measured word times.
 * Native inference runs on its own IO dispatcher; all editor mutations resume on Main.
 * A request identity prevents late native callbacks from resurrecting cancelled/replaced jobs.
 */
data class CaptionUiState(
    val phase: CaptionPhase = CaptionPhase.IDLE,
    val progress01: Float = 0f,
    val message: String = "",
    val segmentsAdded: Int = 0,
    val language: String = WhisperCaptionEngine.LANG_AUTO,
    val requestId: String? = null,
    val timingMode: CaptionTimingMode = CaptionTimingMode.PHRASE,
)

enum class CaptionTimingMode { PHRASE, ENGLISH_WORDS, ENGLISH_KARAOKE }
enum class CaptionPhase { IDLE, NEED_DOWNLOAD, TRANSCRIBING, DONE, FAILED }

/** Only this request may publish. Flow.update also protects native-worker progress callbacks. */
internal fun EditorViewModel.updateCaptionRequest(id: String, transform: (CaptionUiState) -> CaptionUiState) {
    _captionUi.update { if (it.requestId == id) transform(it) else it }
}

fun EditorViewModel.setCaptionLanguage(code: String) {
    captionJob?.cancel()
    captionJob = null
    _captionUi.value = CaptionUiState(language = code,
        timingMode = if (code == "en") _captionUi.value.timingMode else CaptionTimingMode.PHRASE)
}

/** Word mode requires explicit English, never an unverified auto-language guess. */
fun EditorViewModel.setCaptionTimingMode(mode: CaptionTimingMode) {
    if (mode != CaptionTimingMode.PHRASE && _captionUi.value.language != "en") return
    captionJob?.cancel()
    captionJob = null
    _captionUi.value = CaptionUiState(language = _captionUi.value.language, timingMode = mode)
}

fun EditorViewModel.cancelAutoCaptions() {
    captionJob?.cancel()
    captionJob = null
    _captionUi.value = _captionUi.value.copy(
        phase = CaptionPhase.IDLE, progress01 = 0f, message = "Cancelled", requestId = null,
    )
}

/** Snapshot of media/timing, not the entire clip: unrelated style/manual-text edits may survive. */
internal data class CaptionSourceSnapshot(val projectId: String, val clip: MediaClip) {
    fun matches(projectId: String, current: MediaClip): Boolean = this.projectId == projectId &&
        clip.id == current.id && clip.uri == current.uri && clip.type == current.type &&
        clip.durationMs == current.durationMs && clip.trimStartMs == current.trimStartMs &&
        clip.trimEndMs == current.trimEndMs && clip.speedPoints == current.speedPoints &&
        clip.speedMultiplier == current.speedMultiplier && clip.speedCurve == current.speedCurve &&
        clip.rampStartSpeed == current.rampStartSpeed && clip.rampEndSpeed == current.rampEndSpeed &&
        clip.textOverlays.filter { it.presetId == "caption_auto" } ==
        current.textOverlays.filter { it.presetId == "caption_auto" }
}

/** Engine segments are relative to the trimmed SOURCE; text overlays use clip OUTPUT time.
 * Both boundaries must pass through the original speed curve (not an independently restarted ramp).
 */
internal fun captionOverlaysFor(clip: MediaClip, segments: List<WhisperCaptionEngine.CaptionSegment>): List<TextOverlay> {
    val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
    if (span == 0L) return emptyList()
    return segments.mapNotNull { seg ->
        val sourceStart = seg.startMs.coerceIn(0L, span)
        val sourceEnd = seg.endMs.coerceIn(0L, span)
        if (sourceEnd <= sourceStart || seg.text.isBlank()) return@mapNotNull null
        val start = ClipTimeMapping.outputOffsetAtSourceTime(clip, clip.trimStartMs + sourceStart)
        val end = ClipTimeMapping.outputOffsetAtSourceTime(clip, clip.trimStartMs + sourceEnd)
        if (end <= start) return@mapNotNull null
        TextOverlay.of(
            text = seg.text, x = 0.5f, y = 0.82f, sizeScale = 0.85f,
            colorArgb = 0xFFFFFFFFL, bgArgb = 0xB3000000L, isBold = true, presetId = "caption_auto",
        ).copy(startMs = start, endMs = end)
    }
}

/** Full original phrase is repeated for every measured CTC interval; ambiguous grouping fails closed. */
internal fun karaokeCaptionOverlaysFor(
    clip: MediaClip,
    phrases: List<WhisperCaptionEngine.CaptionSegment>,
    words: List<WordCue>,
): List<TextOverlay> {
    alignedCaptionSegments(words)
    val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
    require(span > 0L && phrases.isNotEmpty()) { "No phrase timing available for karaoke" }
    require(phrases.zipWithNext().all { (a, b) -> a.endMs <= b.startMs }) {
        "Karaoke phrases must be ordered and non-overlapping"
    }
    val tokenMatches = phrases.map { phrase ->
        require(phrase.startMs >= 0L && phrase.endMs > phrase.startMs && phrase.endMs <= span && phrase.text.isNotBlank())
        Regex("[A-Za-z]+(?:'[A-Za-z]+)*").findAll(phrase.text).toList()
    }
    require(tokenMatches.all { it.isNotEmpty() }) { "A phrase has no mappable English words" }
    val assigned = phrases.indices.map { mutableListOf<WordCue>() }
    words.forEach { word ->
        val matches = phrases.indices.filter { i -> word.startMs >= phrases[i].startMs && word.endMs <= phrases[i].endMs }
        require(matches.size == 1) { "An acoustic word cannot be mapped uniquely to a Whisper phrase" }
        assigned[matches.single()] += word
    }
    val result = mutableListOf<TextOverlay>()
    phrases.forEachIndexed { phraseIndex, phrase ->
        val tokens = tokenMatches[phraseIndex]
        val group = assigned[phraseIndex]
        require(group.size == tokens.size) { "Acoustic words do not map one-to-one to the original phrase" }
        val fingerprint = TextOverlayKaraoke.fingerprint(phrase.text)
        fun emit(sourceStart: Long, sourceEnd: Long, token: MatchResult? = null) {
            if (sourceEnd <= sourceStart) return
            val start = ClipTimeMapping.outputOffsetAtSourceTime(clip, clip.trimStartMs + sourceStart)
            val exclusiveEnd = ClipTimeMapping.outputOffsetAtSourceTime(clip, clip.trimStartMs + sourceEnd)
            if (exclusiveEnd <= start) {
                require(token == null) { "Measured word collapsed after speed mapping" }
                return // A sub-millisecond silence has no output frame; never invent word timing.
            }
            result += TextOverlay.of(
                text = phrase.text, x = 0.5f, y = 0.82f, sizeScale = 0.85f,
                colorArgb = 0xFFFFFFFFL, bgArgb = 0xB3000000L, isBold = true, presetId = "caption_auto",
            ).copy(
                startMs = start, endMs = exclusiveEnd - 1L,
                highlightStartUtf16 = token?.range?.first, highlightEndUtf16 = token?.range?.last?.plus(1),
                highlightSourceFingerprint = fingerprint, highlightColorArgb = 0xFFFFFF00L,
            )
        }
        var cursor = phrase.startMs
        group.forEachIndexed { index, word ->
            val token = tokens[index]
            val cueTokens = Regex("[A-Za-z]+(?:'[A-Za-z]+)*").findAll(word.text).toList()
            require(cueTokens.size == 1 && cueTokens.single().value.equals(token.value, ignoreCase = true)) {
                "Acoustic word does not match the original phrase token"
            }
            emit(cursor, word.startMs) // Preserve phrase visibility in genuine gaps, without a highlighted word.
            emit(word.startMs, word.endMs, token)
            cursor = word.endMs
        }
        emit(cursor, phrase.endMs)
    }
    return result
}

/** Only genuine, ordered acoustic word intervals may enter the word-by-word editor path.
 * Invalid results fail atomically: no partial captions and no equal-duration fallback.
 */
internal fun alignedCaptionSegments(words: List<WordCue>): List<WhisperCaptionEngine.CaptionSegment> {
    require(words.isNotEmpty()) { "No aligned speech words" }
    require(words.all { it.precision == TimingPrecision.CTC_FORCED_WORD_ALIGNMENT &&
        it.text.isNotBlank() && it.startMs >= 0L && it.endMs > it.startMs &&
        it.confidence != null && it.confidence.isFinite() && it.confidence in 0f..1f }) {
        "Word captions require genuine acoustic intervals and confidence"
    }
    require(words.zipWithNext().all { (a, b) -> a.endMs <= b.startMs }) {
        "Word caption intervals must be ordered and non-overlapping"
    }
    return words.map { WhisperCaptionEngine.CaptionSegment(it.text, it.startMs, it.endMs) }
}

/** Called on the editor/Main dispatcher only; stale inference never mutates history or media. */
internal fun EditorViewModel.applyCaptionResult(
    snapshot: CaptionSourceSnapshot,
    segments: List<WhisperCaptionEngine.CaptionSegment>,
    karaokeWords: List<WordCue>? = null,
): Int? {
    val project = _state.value.project ?: return null
    val current = project.clips.firstOrNull { it.id == snapshot.clip.id } ?: return null
    if (!snapshot.matches(project.id, current)) return null
    val overlays = karaokeWords?.let { karaokeCaptionOverlaysFor(current, segments, it) }
        ?: captionOverlaysFor(current, segments)
    if (overlays.isEmpty()) return 0
    pushUndo()
    updateClip(current.id) { c ->
        c.copy(textOverlays = c.textOverlays.filter { it.presetId != "caption_auto" } + overlays)
    }
    return overlays.size
}

fun EditorViewModel.generateAutoCaptions(clipId: String) {
    val ctx = context ?: run {
        _captionUi.value = _captionUi.value.copy(phase = CaptionPhase.FAILED, message = "No context")
        return
    }
    val project = _state.value.project ?: return
    val clip = project.clips.firstOrNull { it.id == clipId } ?: run {
        _captionUi.value = _captionUi.value.copy(phase = CaptionPhase.FAILED, message = "Clip not found")
        return
    }
    if (_captionUi.value.phase == CaptionPhase.TRANSCRIBING) return
    captionJob?.cancel()
    captionJob = null
    if (!GlobalModerationEnforcement.readiness.approved) {
        _captionUi.value = _captionUi.value.copy(
            phase = CaptionPhase.FAILED,
            progress01 = 0f,
            segmentsAdded = 0,
            requestId = null,
            message = GlobalModerationEnforcement.statusMessage(ProtectedAiAction.WHISPER_CAPTIONS),
        )
        return
    }
    val request = UUID.randomUUID().toString()
    val language = _captionUi.value.language
    val timingMode = _captionUi.value.timingMode
    if (timingMode != CaptionTimingMode.PHRASE && language != "en") {
        _captionUi.value = _captionUi.value.copy(phase = CaptionPhase.FAILED,
            message = "Select English for acoustic word timing. Other languages keep phrase timing.")
        return
    }
    val snapshot = CaptionSourceSnapshot(project.id, clip)
    _captionUi.value = _captionUi.value.copy(
        phase = CaptionPhase.TRANSCRIBING, progress01 = 0f, segmentsAdded = 0,
        message = "Checking offline AI model…", requestId = request,
    )
    captionJob = viewModelScope.launch {
        try {
            val ready = WhisperCaptionEngine.isModelReady(ctx.applicationContext)
            ensureActive()
            if (_captionUi.value.requestId != request) return@launch
            if (!ready) {
                updateCaptionRequest(request) { it.copy(
                    phase = CaptionPhase.NEED_DOWNLOAD,
                    message = "Bundled Whisper is unavailable. No network request was made; legacy packs are optional.",
                ) }
                return@launch
            }
            updateCaptionRequest(request) { it.copy(message = "Listening… (on-device AI)") }
            val segments = WhisperCaptionEngine.transcribe(
                ctx.applicationContext, android.net.Uri.parse(clip.uri), language, clip.trimStartMs, clip.trimEndMs,
            ) { p ->
                updateCaptionRequest(request) { it.copy(
                    progress01 = p.coerceIn(0f, 1f) * if (timingMode != CaptionTimingMode.PHRASE) 0.65f else 1f,
                    message = "Transcribing ${(p * 100).toInt()}%…",
                ) }
            }
            ensureActive()
            if (_captionUi.value.requestId != request) return@launch
            if (segments == null) {
                updateCaptionRequest(request) { it.copy(phase = CaptionPhase.FAILED,
                    message = "Couldn't load the AI model or read this clip's audio.") }
                return@launch
            }
            var karaokeWords: List<WordCue>? = null
            val finalSegments = if (timingMode != CaptionTimingMode.PHRASE && segments.isNotEmpty()) {
                updateCaptionRequest(request) { it.copy(message = "Aligning English words offline…", progress01 = .65f) }
                val words = EnglishWordAlignment.align(ctx.applicationContext, android.net.Uri.parse(clip.uri),
                    segments, clip.trimStartMs, clip.trimEndMs) { progress ->
                    updateCaptionRequest(request) { it.copy(progress01 = .65f + .35f * progress.coerceIn(0f, 1f),
                        message = "Aligning English words ${(progress * 100).toInt()}%…") }
                }
                ensureActive()
                if (_captionUi.value.requestId != request) return@launch
                val measuredSegments = alignedCaptionSegments(words)
                if (timingMode == CaptionTimingMode.ENGLISH_KARAOKE) {
                    karaokeWords = words
                    // Keep the exact Whisper phrase transcript/grouping for karaoke construction.
                    segments
                } else measuredSegments
            } else segments
            val added = applyCaptionResult(snapshot, finalSegments, karaokeWords)
            updateCaptionRequest(request) {
                when (added) {
                    null -> it.copy(phase = CaptionPhase.FAILED,
                        message = "Clip, source, timing or captions changed while transcribing. Generate again; nothing was replaced.")
                    0 -> it.copy(phase = CaptionPhase.FAILED, message = "No speech detected in this clip.")
                    else -> it.copy(phase = CaptionPhase.DONE, progress01 = 1f,
                        message = when (timingMode) {
                            CaptionTimingMode.ENGLISH_WORDS -> "Added $added acoustically timed words"
                            CaptionTimingMode.ENGLISH_KARAOKE -> "Added $added full-phrase karaoke windows"
                            CaptionTimingMode.PHRASE -> "Added $added phrase captions"
                        }, segmentsAdded = added)
                }
            }
        } catch (cancelled: CancellationException) {
            updateCaptionRequest(request) { it.copy(phase = CaptionPhase.IDLE,
                progress01 = 0f, message = "Cancelled", requestId = null) }
            throw cancelled
        } catch (failure: Exception) {
            Log.e("CaptionCtrl", "Auto-captions failed", failure)
            updateCaptionRequest(request) { it.copy(phase = CaptionPhase.FAILED,
                message = "Captions failed: ${failure.message?.take(80)}") }
        }
    }
}

fun EditorViewModel.resetCaptionUi() {
    captionJob?.cancel()
    captionJob = null
    _captionUi.value = CaptionUiState()
}

/** Manual clear is undoable and invalidates in-flight results even when no caption existed yet. */
fun EditorViewModel.clearAutoCaptions(clipId: String) {
    resetCaptionUi()
    val clip = _state.value.project?.clips?.firstOrNull { it.id == clipId } ?: return
    if (clip.textOverlays.none { it.presetId == "caption_auto" }) return
    pushUndo()
    updateClip(clipId) { c -> c.copy(textOverlays = c.textOverlays.filter { it.presetId != "caption_auto" }) }
}
