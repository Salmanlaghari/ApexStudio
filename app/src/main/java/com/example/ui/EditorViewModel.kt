package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlaybackManager
import com.example.audio.AudioSynthesizer
import com.example.model.AdjustmentValues
import com.example.model.AudioTrackState
import com.example.model.ChromaKeyState
import com.example.model.EditorTab
import com.example.model.EffectType
import com.example.model.RoyaltyFreeTrack
import com.example.video.VideoFrameRenderer
import com.example.video.VideoPresetClip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val audioManager = AudioPlaybackManager(application)

    private val _currentTimeMs = MutableStateFlow(0L)
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(15_000L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeClip = MutableStateFlow(VideoPresetClip.GREEN_SCREEN_DANCER)
    val activeClip: StateFlow<VideoPresetClip> = _activeClip.asStateFlow()

    private val _chromaKeyState = MutableStateFlow(ChromaKeyState(enabled = true))
    val chromaKeyState: StateFlow<ChromaKeyState> = _chromaKeyState.asStateFlow()

    private val _activeEffect = MutableStateFlow(EffectType.NONE)
    val activeEffect: StateFlow<EffectType> = _activeEffect.asStateFlow()

    private val _effectIntensity = MutableStateFlow(0.85f)
    val effectIntensity: StateFlow<Float> = _effectIntensity.asStateFlow()

    private val _adjustments = MutableStateFlow(AdjustmentValues())
    val adjustments: StateFlow<AdjustmentValues> = _adjustments.asStateFlow()

    private val _isCompareMode = MutableStateFlow(false)
    val isCompareMode: StateFlow<Boolean> = _isCompareMode.asStateFlow()

    private val _textOverlay = MutableStateFlow("ApexStudio")
    val textOverlay: StateFlow<String> = _textOverlay.asStateFlow()

    private val _audioState = MutableStateFlow(AudioTrackState())
    val audioState: StateFlow<AudioTrackState> = _audioState.asStateFlow()

    private val _activeTab = MutableStateFlow(EditorTab.CHROMA_KEY)
    val activeTab: StateFlow<EditorTab> = _activeTab.asStateFlow()

    private val _previewingTrackId = MutableStateFlow<String?>(null)
    val previewingTrackId: StateFlow<String?> = _previewingTrackId.asStateFlow()

    private val _renderedFrame = MutableStateFlow<Bitmap?>(null)
    val renderedFrame: StateFlow<Bitmap?> = _renderedFrame.asStateFlow()

    private var playbackJob: Job? = null

    init {
        // Pre-load default royalty-free track so audio is ready on first launch
        viewModelScope.launch {
            val defaultTrack = AudioSynthesizer.TRACKS[0] // "Neon Horizon"
            loadRoyaltyFreeTrack(defaultTrack)
            refreshCurrentFrame()
        }
    }

    fun setTab(tab: EditorTab) {
        _activeTab.value = tab
    }

    fun setClip(clip: VideoPresetClip) {
        _activeClip.value = clip
        _totalDurationMs.value = clip.durationMs
        seekContinuously(0L)
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        _isPlaying.value = true
        audioManager.play(_currentTimeMs.value)

        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var lastTime = System.currentTimeMillis()
            while (_isPlaying.value) {
                val now = System.currentTimeMillis()
                val delta = now - lastTime
                lastTime = now

                var nextTime = _currentTimeMs.value + delta
                if (nextTime >= _totalDurationMs.value) {
                    nextTime = 0L // Loop playback
                    audioManager.seekTo(0L)
                }

                _currentTimeMs.value = nextTime
                audioManager.applyVolumeAndFade(nextTime)
                refreshCurrentFrame()

                delay(30) // ~33fps smooth animation
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        audioManager.pause()
    }

    /**
     * Continuous per-frame scrubbing handler.
     * Updates video frame, audio position, and timeline cursor synchronously.
     */
    fun seekContinuously(timeMs: Long) {
        val clampedTime = timeMs.coerceIn(0L, _totalDurationMs.value)
        _currentTimeMs.value = clampedTime
        audioManager.seekTo(clampedTime)
        refreshCurrentFrame()
    }

    fun setChromaKeyState(state: ChromaKeyState) {
        _chromaKeyState.value = state
        refreshCurrentFrame()
    }

    fun setActiveEffect(effect: EffectType) {
        _activeEffect.value = effect
        refreshCurrentFrame()
    }

    fun setEffectIntensity(intensity: Float) {
        _effectIntensity.value = intensity
        refreshCurrentFrame()
    }

    fun setAdjustments(adjustments: AdjustmentValues) {
        _adjustments.value = adjustments
        refreshCurrentFrame()
    }

    fun toggleCompareMode() {
        _isCompareMode.value = !_isCompareMode.value
        refreshCurrentFrame()
    }

    fun setTextOverlay(text: String) {
        _textOverlay.value = text
        refreshCurrentFrame()
    }

    fun setAudioState(newState: AudioTrackState) {
        _audioState.value = newState
        audioManager.updateTrackConfig(newState, _currentTimeMs.value)
    }

    fun loadRoyaltyFreeTrack(track: RoyaltyFreeTrack) {
        viewModelScope.launch {
            val audioFile = AudioSynthesizer.ensureTrackGenerated(getApplication(), track)
            val waveforms = AudioSynthesizer.extractWaveformPoints(audioFile, 64)

            val newState = AudioTrackState(
                title = track.title,
                audioFile = audioFile,
                durationMs = track.durationMs,
                trimStartMs = 0L,
                trimEndMs = track.durationMs,
                volume = 1.0f,
                waveforms = waveforms,
                isRoyaltyFree = true
            )
            _audioState.value = newState
            audioManager.loadTrack(newState)
            _previewingTrackId.value = null
        }
    }

    fun previewRoyaltyFreeTrack(track: RoyaltyFreeTrack) {
        if (_previewingTrackId.value == track.id) {
            // Stop preview
            audioManager.pause()
            _previewingTrackId.value = null
        } else {
            viewModelScope.launch {
                val file = AudioSynthesizer.ensureTrackGenerated(getApplication(), track)
                val previewState = AudioTrackState(
                    title = "Preview: ${track.title}",
                    audioFile = file,
                    durationMs = track.durationMs,
                    volume = 0.9f
                )
                audioManager.loadTrack(previewState)
                audioManager.play(0L)
                _previewingTrackId.value = track.id
            }
        }
    }

    fun importLocalAudio(uri: Uri) {
        viewModelScope.launch {
            val imported = audioManager.importLocalAudioFile(uri)
            if (imported != null) {
                _audioState.value = imported
                audioManager.loadTrack(imported)
            }
        }
    }

    private fun refreshCurrentFrame() {
        val effectiveAdjust = if (_isCompareMode.value) AdjustmentValues() else _adjustments.value
        val frame = VideoFrameRenderer.renderProcessedFrame(
            timeMs = _currentTimeMs.value,
            activeClip = _activeClip.value,
            chromaKey = _chromaKeyState.value,
            activeEffect = _activeEffect.value,
            effectIntensity = _effectIntensity.value,
            adjustments = effectiveAdjust,
            textOverlay = _textOverlay.value
        )
        _renderedFrame.value = frame
    }

    override fun onCleared() {
        super.onCleared()
        audioManager.release()
        playbackJob?.cancel()
    }
}
