package com.apexstudio.app.data.audio

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * [DefaultRenderersFactory] that injects the shared [DenoiseAudioProcessor]
 * into the preview player's audio chain.
 *
 * Why a factory instead of `Player.setAudioEffects(...)`: Media3 1.5.0 has
 * no `setAudioEffects` (and `AudioProcessor` does not extend `Effect`), so
 * the only supported way to attach a custom [androidx.media3.common.audio.AudioProcessor]
 * to ExoPlayer playback is through the [AudioSink] the renderers are built
 * with. [DefaultRenderersFactory.buildAudioSink] is `protected`, so this
 * subclass overrides it to build a [DefaultAudioSink] whose processor
 * chain starts with the denoise processor.
 *
 * The processor instance is SHARED (one instance, created once by the
 * editor and handed in via [processorProvider]) so the Clean Voice
 * toggle / strength can take effect immediately: the editor just flips
 * [DenoiseAudioProcessor.denoiseEnabled] /
 * [DenoiseAudioProcessor.denoiseStrength] on the same instance the sink
 * is already running — no player rebuild, no seek. The processor stays
 * in the chain permanently; when disabled it is a cheap bit-exact
 * passthrough (no FFT).
 *
 * Lifetime: the provider must return the same instance for the whole
 * life of the player built from this factory. ExoPlayer creates its
 * renderers (and therefore the sink) once at build time, so one
 * instance serves exactly one player. Do not share one processor
 * across two live players.
 */
@OptIn(UnstableApi::class)
class DenoiseRenderersFactory(
    context: Context,
    private val processorProvider: () -> DenoiseAudioProcessor?,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? {
        val processor = processorProvider()
            ?: return super.buildAudioSink(
                context,
                enableFloatOutput,
                enableAudioTrackPlaybackParams,
            )
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(processor))
            .build()
    }
}
