package com.apexstudio.app.data.audio.sfx

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runtime-only sound-effect browser state, preview, and cache rendering. */
class SfxLibraryController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(SfxLibraryUiState())
    val state: StateFlow<SfxLibraryUiState> = _state.asStateFlow()

    private var previewPlayer: ExoPlayer? = null
    private var previewJob: Job? = null
    private var previewGeneration = 0

    fun togglePreview(effect: SfxEffect, intensity: Float = SfxLibrary.DEFAULT_INTENSITY) {
        if (_state.value.previewingId == effect.id) {
            stopPreview()
            return
        }
        stopPreview()
        val generation = ++previewGeneration
        _state.update { it.copy(renderingIds = it.renderingIds + effect.id, error = null) }
        previewJob = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    SfxWavCache.getOrCreate(appContext, effect.id, effect.defaultDurationMs, intensity)
                }
                if (generation != previewGeneration) return@launch
                val player = ExoPlayer.Builder(appContext).build().also { previewPlayer = it }
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED && generation == previewGeneration) stopPreview()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "SFX preview failed for ${effect.id}", error)
                        if (generation == previewGeneration) {
                            stopPreview()
                            _state.update { it.copy(error = "Preview failed. Try again.") }
                        }
                    }
                })
                player.setMediaItem(MediaItem.fromUri(file.absolutePath))
                player.prepare()
                player.play()
                _state.update { it.copy(previewingId = effect.id) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Could not render SFX preview ${effect.id}", error)
                if (generation == previewGeneration) {
                    _state.update { it.copy(error = "Could not render ${effect.displayName}.") }
                }
            } finally {
                if (generation == previewGeneration) {
                    _state.update { it.copy(renderingIds = it.renderingIds - effect.id) }
                }
            }
        }
    }

    fun renderAndAdd(
        effect: SfxEffect,
        intensity: Float = SfxLibrary.DEFAULT_INTENSITY,
        onAdded: (effect: SfxEffect, path: String, durationMs: Long, intensity: Float) -> Unit
    ) {
        if (effect.id in _state.value.addingIds) return
        stopPreview()
        val safeIntensity = intensity.coerceIn(0f, 1f)
        _state.update { it.copy(addingIds = it.addingIds + effect.id, error = null) }
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    SfxWavCache.getOrCreate(
                        appContext, effect.id, effect.defaultDurationMs, safeIntensity
                    )
                }
                onAdded(effect, file.absolutePath, effect.defaultDurationMs, safeIntensity)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Could not create SFX ${effect.id}", error)
                _state.update { it.copy(error = "Could not add ${effect.displayName}. Try again.") }
            } finally {
                _state.update { it.copy(addingIds = it.addingIds - effect.id) }
            }
        }
    }

    fun stopPreview() {
        previewGeneration++
        previewJob?.cancel()
        previewJob = null
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {
        }
        previewPlayer = null
        _state.update { it.copy(previewingId = null, renderingIds = emptySet()) }
    }

    fun release() {
        stopPreview()
        scope.cancel()
    }

    private companion object {
        const val TAG = "SfxLibraryController"
    }
}
