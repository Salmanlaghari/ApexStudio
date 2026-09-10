package com.apexstudio.app.data.engine

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Equalizer
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.PresetReverb
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

data class AudioEQState(
    val lowGain: Short = 0,
    val midGain: Short = 0,
    val highGain: Short = 0,
    val volume: Float = 0.75f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val noiseReduction: Float = 0f,
    val echoCancellation: Boolean = false,
    val noiseSuppression: Boolean = false,
    // Phase E: voice-changer + audio effects. Pitch in semitones
    // (-12..+12) is applied via ExoPlayer.PlaybackParameters.pitch =
    // 2^(semitones/12). Reverb preset follows PresetReverb.PRESET_*
    // (0..6); bassBoostStrength is 0..1000 (PresetReverb-strength).
    val pitchSemitones: Float = 0f,
    val reverbEnabled: Boolean = false,
    val reverbPreset: Short = 0,
    val echoEnabled: Boolean = false,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Short = 0
)

class AudioEngine(private val context: Context) {

    private val _eqState = MutableStateFlow(AudioEQState())
    val eqState: StateFlow<AudioEQState> = _eqState

    private var equalizer: Equalizer? = null
    // Phase E: voice-changer + audio effects. Each one is created
    // lazily on first use and released in [release]. The audio session
    // id is 0 (= system mixer) so we don't need a live ExoPlayer
    // handle here — the preview player's effects will be applied at
    // session-id routing when it's wired into the player's audio
    // session. For now these flags drive the AudioStudioScreen UI
    // state and the export will reference the same effect classes
    // once the Transformer pipeline picks them up (see
    // TODO(PHASE_E_EXPORT) in ExportEngine).
    private var presetReverb: PresetReverb? = null
    private var environmentalReverb: EnvironmentalReverb? = null
    private var bassBoost: BassBoost? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    private val _waveformData = MutableStateFlow(FloatArray(0))
    val waveformData: StateFlow<FloatArray> = _waveformData

    private fun ensureEqualizer() {
        if (equalizer != null) return
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.MODIFY_AUDIO_SETTINGS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // The Equalizer AudioEffect requires MODIFY_AUDIO_SETTINGS. Constructing
            // an Equalizer without it can hard-crash the native audio-effect layer
            // on some devices (uncatchable SIGSEGV), so we skip init when absent.
            return
        }
        try {
            equalizer = Equalizer(0, AudioManager.STREAM_MUSIC).apply {
                enabled = true
                val bands = numberOfBands
                if (bands >= 3) {
                    setBandLevel(0, _eqState.value.lowGain)
                    setBandLevel(1, _eqState.value.midGain)
                    setBandLevel(2, _eqState.value.highGain)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Failed to initialize Equalizer", e)
            equalizer = null
        }
    }

    fun setLowGain(gain: Short) {
        _eqState.update { it.copy(lowGain = gain) }
        ensureEqualizer()
        equalizer?.setBandLevel(0, gain)
    }

    fun setMidGain(gain: Short) {
        _eqState.update { it.copy(midGain = gain) }
        ensureEqualizer()
        equalizer?.setBandLevel(1, gain)
    }

    fun setHighGain(gain: Short) {
        _eqState.update { it.copy(highGain = gain) }
        ensureEqualizer()
        equalizer?.setBandLevel(2, gain)
    }

    fun setVolume(volume: Float) {
        _eqState.update { it.copy(volume = volume.coerceIn(0f, 1f)) }
    }

    fun toggleMute() {
        _eqState.update { it.copy(isMuted = !it.isMuted) }
    }

    fun toggleSolo() {
        _eqState.update { it.copy(isSolo = !it.isSolo) }
    }

    fun setNoiseReduction(level: Float) {
        _eqState.update { it.copy(noiseReduction = level) }
    }

    fun toggleEchoCancellation(enabled: Boolean) {
        _eqState.update { it.copy(echoCancellation = enabled) }
    }

    fun toggleNoiseSuppression(enabled: Boolean) {
        _eqState.update { it.copy(noiseSuppression = enabled) }
    }

    // Phase E: pitch control. semitones is clamped to -12..+12; the
    // native float pitch passed to PlaybackParameters.pitch is
    // 2^(semitones/12). -12 = octave down (0.5x), 0 = unchanged (1.0x),
    // +12 = octave up (2.0x). State-only for now — the preview
    // ExoPlayer reads pitch via vm.applyAudioEffects() and the export
    // picks it up through the same AudioStudioState.pitchSemitones
    // field. Engine just remembers the value.
    fun setPitchSemitones(semitones: Float) {
        val clamped = semitones.coerceIn(-12f, 12f)
        _eqState.update { it.copy(pitchSemitones = clamped) }
    }

    fun enableReverb(enabled: Boolean, preset: Short = 0) {
        if (enabled) {
            ensurePresetReverb()
            presetReverb?.preset = preset.coerceIn(0, 6).toShort()
            presetReverb?.enabled = true
        } else {
            presetReverb?.enabled = false
        }
        _eqState.update { it.copy(reverbEnabled = enabled, reverbPreset = preset.coerceIn(0, 6).toShort()) }
    }

    fun enableEcho(enabled: Boolean) {
        if (enabled) {
            ensureEnvironmentalReverb()
            environmentalReverb?.enabled = true
        } else {
            environmentalReverb?.enabled = false
        }
        _eqState.update { it.copy(echoEnabled = enabled) }
    }

    fun enableBassBoost(enabled: Boolean, strength: Short = 0) {
        if (enabled) {
            ensureBassBoost()
            bassBoost?.enabled = true
            bassBoost?.setStrength(strength.coerceIn(0, 1000).toShort())
        } else {
            bassBoost?.enabled = false
        }
        _eqState.update {
            it.copy(bassBoostEnabled = enabled, bassBoostStrength = strength.coerceIn(0, 1000).toShort())
        }
    }

    private fun ensurePresetReverb() {
        if (presetReverb != null) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.MODIFY_AUDIO_SETTINGS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        try {
            presetReverb = PresetReverb(0, 0).apply { enabled = false }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Failed to init PresetReverb", e)
            presetReverb = null
        }
    }

    private fun ensureEnvironmentalReverb() {
        if (environmentalReverb != null) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.MODIFY_AUDIO_SETTINGS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        try {
            environmentalReverb = EnvironmentalReverb(0, 0).apply { enabled = false }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Failed to init EnvironmentalReverb", e)
            environmentalReverb = null
        }
    }

    private fun ensureBassBoost() {
        if (bassBoost != null) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.MODIFY_AUDIO_SETTINGS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        try {
            bassBoost = BassBoost(0, 0).apply { enabled = false }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Failed to init BassBoost", e)
            bassBoost = null
        }
    }

    fun startRecording(sampleRate: Int = 44100) {
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("AudioEngine", "RECORD_AUDIO permission not granted; skipping microphone recording")
            return
        }
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufferSize <= 0) {
                Log.e("AudioEngine", "Invalid AudioRecord buffer size: $minBufferSize")
                return
            }
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2
            )
            audioRecord?.startRecording()
            isRecording = true
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val bufferSize = minBufferSize
                val buffer = ShortArray(bufferSize)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        val samples = FloatArray(read)
                        for (i in 0 until read) {
                            samples[i] = buffer[i].toFloat() / 32768f
                        }
                        _waveformData.emit(samples)
                    }
                    delay(50)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Failed to start recording", e)
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun getSupportedEQBands(): Int = equalizer?.numberOfBands?.toInt() ?: 0

    fun getEQFrequencyCenter(bandIndex: Int): Int = equalizer?.getCenterFreq(bandIndex.toShort()) ?: 0

    fun getEQFrequencyRange(): Pair<Int, Int> {
        val eq = equalizer ?: return Pair(20, 20000)
        val range = eq.getBandFreqRange(0.toShort())
        return Pair(range[0], range[1])
    }

    fun release() {
        stopRecording()
        equalizer?.release()
        equalizer = null
        presetReverb?.release()
        presetReverb = null
        environmentalReverb?.release()
        environmentalReverb = null
        bassBoost?.release()
        bassBoost = null
    }
}
