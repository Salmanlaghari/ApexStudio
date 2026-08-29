package com.apexstudio.app.data.engine

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Equalizer
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AudioEQState(
    val lowGain: Short = 0,
    val midGain: Short = 0,
    val highGain: Short = 0,
    val volume: Float = 0.75f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val noiseReduction: Float = 0f,
    val echoCancellation: Boolean = false,
    val noiseSuppression: Boolean = false
)

class AudioEngine(private val context: Context) {

    private val _eqState = MutableStateFlow(AudioEQState())
    val eqState: StateFlow<AudioEQState> = _eqState

    private var equalizer: Equalizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    private val _waveformData = MutableStateFlow(FloatArray(0))
    val waveformData: StateFlow<FloatArray> = _waveformData

    init {
        setupEqualizer()
    }

    private fun setupEqualizer() {
        try {
            equalizer = Equalizer(0, AudioManager.STREAM_MUSIC).apply {
                enabled = true
                val bands = numberOfBands
                if (bands >= 3) {
                    setBandGain(0, lowGain)
                    setBandGain(1, midGain)
                    setBandGain(2, highGain)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Failed to initialize Equalizer", e)
        }
    }

    fun setLowGain(gain: Short) {
        _eqState.update { it.copy(lowGain = gain) }
        equalizer?.setBandGain(0, gain)
    }

    fun setMidGain(gain: Short) {
        _eqState.update { it.copy(midGain = gain) }
        equalizer?.setBandGain(1, gain)
    }

    fun setHighGain(gain: Short) {
        _eqState.update { it.copy(highGain = gain) }
        equalizer?.setBandGain(2, gain)
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

    fun startRecording(sampleRate: Int = 44100) {
        if (isRecording) return
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
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

    fun getSupportedEQBands(): Int = equalizer?.numberOfBands ?: 0

    fun getEQFrequencyCenter(bandIndex: Int): Int = equalizer?.getBandCenterFrequency(bandIndex)?.toInt() ?: 0

    fun getEQFrequencyRange(): Pair<Int, Int> {
        val eq = equalizer ?: return Pair(20, 20000)
        return Pair(eq.bandFreqRange.first.toInt(), eq.bandFreqRange.second.toInt())
    }

    fun release() {
        stopRecording()
        equalizer?.release()
        equalizer = null
    }
}
